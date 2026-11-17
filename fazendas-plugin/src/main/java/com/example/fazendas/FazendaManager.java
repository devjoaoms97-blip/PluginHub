package com.example.fazendas;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * As fazendas colocadas.
 * <ul>
 *   <li><b>No mundo</b>: a {@link Construcao} 7x7, um NPC parado na abertura da cerca e um holograma com o cofre e
 *       o insumo. O NPC e o holograma não são salvos no mundo: aparecem quando o chunk carrega e somem
 *       quando ele descarrega (assim nunca ficam duplicados).</li>
 *   <li><b>Produção</b>: pelo relógio, mesmo com o dono offline ou o terreno descarregado (offline, na
 *       velocidade {@code producao-offline}). A cada safra o cofre ganha a renda do tipo, gastando
 *       {@code insumo-por-safra}, até acabar o insumo ou encher o cofre.</li>
 *   <li><b>Menu</b> (clique no NPC, só o dono): coletar o dinheiro, abastecer, as três melhorias e
 *       recolher (com confirmação: a construção some e a Escritura volta com os níveis).</li>
 * </ul>
 */
public class FazendaManager implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private static final class Menu implements InventoryHolder {
        final Fazenda f;
        boolean confirmarRecolher;
        Inventory inventario;

        Menu(Fazenda f) {
            this.f = f;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private static final int SLOT_INFO = 4, SLOT_COLETAR = 10, SLOT_ABASTECER = 12, SLOT_PROD = 14, SLOT_COFRE = 15,
            SLOT_DESPENSA = 16, SLOT_RECOLHER = 22;

    private final FazendasPlugin plugin;
    private final File arquivo;
    private final Map<String, Fazenda> porId = new LinkedHashMap<>();
    /** "mundo;chunkX;chunkZ" → as fazendas que encostam nesse chunk (a área 7x7 pode pegar até 4). */
    private final Map<String, List<Fazenda>> porChunk = new HashMap<>();
    private final Map<UUID, Long> ultimoClique = new HashMap<>();
    private boolean sujo;

    FazendaManager(FazendasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "fazendas.yml");
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------

    int quantas() {
        return porId.size();
    }

    List<String> resumo() {
        List<String> linhas = new ArrayList<>();
        for (Fazenda f : porId.values()) {
            atualizar(f);
            linhas.add("§f" + f.tipo + " §7em §f" + f.mundo + " " + f.x + " " + f.y + " " + f.z + " §7dono §f"
                    + Bukkit.getOfflinePlayer(f.dono).getName() + " §7cofre §a" + FazendasPlugin.dinheiro(valorCofre(f))
                    + " §7insumo §f" + FazendasPlugin.fmt(f.insumo));
        }
        return linhas;
    }

    private static String chaveChunk(String mundo, int cx, int cz) {
        return mundo + ";" + cx + ";" + cz;
    }

    private void indexar(Fazenda f) {
        for (int cx = (f.x - Fazenda.RAIO) >> 4; cx <= (f.x + Fazenda.RAIO) >> 4; cx++) {
            for (int cz = (f.z - Fazenda.RAIO) >> 4; cz <= (f.z + Fazenda.RAIO) >> 4; cz++) {
                porChunk.computeIfAbsent(chaveChunk(f.mundo, cx, cz), k -> new ArrayList<>()).add(f);
            }
        }
    }

    private void desindexar(Fazenda f) {
        porChunk.values().forEach(l -> l.remove(f));
        porChunk.values().removeIf(List::isEmpty);
    }

    /** A fazenda cuja área contém esse bloco, ou null. É chamado em todo evento de bloco: só olha o chunk. */
    Fazenda em(Block b) {
        List<Fazenda> l = porChunk.get(chaveChunk(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4));
        if (l == null) return null;
        for (Fazenda f : l) if (f.contem(b.getWorld().getName(), b.getX(), b.getY(), b.getZ())) return f;
        return null;
    }

    /** Uma área 7x7 nesse centro encostaria em alguma fazenda? */
    boolean sobrepoe(String mundo, int x, int z) {
        for (Fazenda f : porId.values()) if (f.mundo.equals(mundo) && Fazenda.sobrepoe(f.x, f.z, x, z)) return true;
        return false;
    }

    /** Quantas fazendas tem no terreno (de qualquer tipo e dono). */
    int noTerreno(String terreno) {
        return (int) porId.values().stream().filter(f -> terreno.equals(plugin.getTerrenos().terrenoEm(f.centro()))).count();
    }

    /** Quantas fazendas desse dono (sem o TerrenosPlugin, o limite vale por jogador). */
    int doDono(UUID dono) {
        return (int) porId.values().stream().filter(f -> f.dono.equals(dono)).count();
    }

    Fazenda daEntidade(Entity e) {
        String id = e.getPersistentDataContainer().get(plugin.chaveEntidade(), PersistentDataType.STRING);
        return id == null ? null : porId.get(id);
    }

    // ---------------------------------------------------------------------
    // Números de cada fazenda (com os níveis)
    // ---------------------------------------------------------------------

    private FazendasPlugin.Tipo tipo(Fazenda f) {
        return plugin.getTipos().get(f.tipo);
    }

    private FazendasPlugin.Insumo insumo(Fazenda f) {
        FazendasPlugin.Tipo t = tipo(f);
        return t == null ? null : plugin.getInsumos().get(t.insumo());
    }

    long intervaloMs(Fazenda f) {
        double reducao = plugin.getConfig().getDouble("produtividade-por-nivel", 5) / 100.0 * f.nivelProd;
        return Math.max(1000, (long) (tipo(f).intervaloSegundos() * 1000 * (1 - reducao)));
    }

    /** Quantas safras cabem no cofre. */
    int cofre(Fazenda f) {
        return plugin.getConfig().getInt("cofre-base", 96) + plugin.getConfig().getInt("cofre-por-nivel", 64) * f.nivelCofre;
    }

    double valorCofre(Fazenda f) {
        FazendasPlugin.Tipo t = tipo(f);
        return t == null ? 0 : f.safras * t.rendaPorSafra();
    }

    /** Quanto insumo cabe: o de {@code despensa.safras-base} safras (mais por nível), no mínimo {@code despensa.minimo}. */
    int despensa(Fazenda f) {
        int safras = plugin.getConfig().getInt("despensa.safras-base", 96) + plugin.getConfig().getInt("despensa.safras-por-nivel", 64) * f.nivelDespensa;
        int unidades = (int) Math.ceil(safras * tipo(f).insumoPorSafra() - 1e-9);
        return Math.max(plugin.getConfig().getInt("despensa.minimo", 64), unidades);
    }

    private int nivelDe(Fazenda f, int qual) {
        return qual == FazendasPlugin.PROD ? f.nivelProd : qual == FazendasPlugin.COFRE ? f.nivelCofre : f.nivelDespensa;
    }

    private int nivelMaximo() {
        return plugin.getConfig().getInt("nivel-maximo", 5);
    }

    private double custoMelhoria(Fazenda f, int nivelAtual) {
        return tipo(f).preco() * plugin.getConfig().getDouble("custo-melhoria", 0.3) * (nivelAtual + 1);
    }

    double fatorOffline() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("producao-offline", 0.5)));
    }

    void atualizar(Fazenda f) {
        atualizar(f, Bukkit.getPlayer(f.dono) != null);
    }

    /** Põe a produção em dia pelo relógio (como as máquinas: offline, o relógio anda mais devagar). */
    private void atualizar(Fazenda f, boolean donoOnline) {
        FazendasPlugin.Tipo t = tipo(f);
        long agora = System.currentTimeMillis();
        long passou = Math.max(0, agora - f.ultima);
        if (!donoOnline) passou = (long) (passou * fatorOffline());
        f.ultima = agora;
        if (t == null) return;
        int cabe = cofre(f) - f.safras;
        int daInsumo = (int) Math.floor(f.insumo / t.insumoPorSafra() + 1e-9);
        if (cabe <= 0 || daInsumo <= 0) {
            f.parcialMs = 0;
            return;
        }
        long total = f.parcialMs + passou;
        long intervalo = intervaloMs(f);
        int feitas = (int) Math.min(total / intervalo, Math.min(cabe, daInsumo));
        f.safras += feitas;
        f.insumo -= feitas * t.insumoPorSafra();
        boolean parou = f.safras >= cofre(f) || f.insumo + 1e-9 < t.insumoPorSafra();
        f.parcialMs = parou ? 0 : total - (long) feitas * intervalo;
        if (feitas > 0) sujo = true;
    }

    /** Teste da staff: o relógio de todas as fazendas anda {@code minutos}. */
    void acelerar(int minutos) {
        for (Fazenda f : porId.values()) {
            f.ultima -= minutos * 60_000L;
            atualizar(f);
            atualizarHolograma(f);
        }
        sujo = true;
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        atualizarDoDono(evento.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        atualizarDoDono(evento.getPlayer().getUniqueId(), true);
        ultimoClique.remove(evento.getPlayer().getUniqueId());
    }

    private void atualizarDoDono(UUID dono, boolean estavaOnline) {
        for (Fazenda f : porId.values()) if (f.dono.equals(dono)) atualizar(f, estavaOnline);
    }

    /** O chunk do NPC está carregado? */
    private static boolean carregado(Fazenda f) {
        Location npc = f.lugarDoNpc();
        return npc != null && npc.getWorld().isChunkLoaded(npc.getBlockX() >> 4, npc.getBlockZ() >> 4);
    }

    /** A cada 20 s: produção em dia e NPC e holograma no lugar, nas fazendas com o chunk carregado. */
    void tick() {
        for (Fazenda f : porId.values()) {
            World w = f.world();
            if (w == null || !carregado(f)) continue;
            atualizar(f);
            garantirEntidades(f);
            atualizarHolograma(f);
        }
        if (sujo) salvar();
    }

    @EventHandler
    public void aoCarregarChunk(ChunkLoadEvent evento) {
        List<Fazenda> l = porChunk.get(chaveChunk(evento.getWorld().getName(), evento.getChunk().getX(), evento.getChunk().getZ()));
        if (l == null) return;
        // o NPC fica no chunk da abertura da cerca
        for (Fazenda f : List.copyOf(l)) {
            Location npc = f.lugarDoNpc();
            if (npc != null && npc.getBlockX() >> 4 == evento.getChunk().getX() && npc.getBlockZ() >> 4 == evento.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!porId.containsValue(f)) return;
                    garantirEntidades(f);
                    atualizarHolograma(f);
                });
            }
        }
    }

    // ---------------------------------------------------------------------
    // Colocar e tirar
    // ---------------------------------------------------------------------

    /** Registra uma fazenda já construída (a {@link Colocacao} construiu). */
    void adicionar(Fazenda f) {
        porId.put(f.id, f);
        indexar(f);
        garantirEntidades(f);
        atualizarHolograma(f);
        sujo = true;
        salvar();
    }

    /** Tira a fazenda do mundo: NPC, holograma e a construção (o chão volta como era). */
    private void desfazer(Fazenda f) {
        porId.remove(f.id);
        desindexar(f);
        removerEntidades(f);
        Construcao.restaurar(f);
        sujo = true;
        salvar();
    }

    /** /fazendas remover: a staff tira a fazenda que está olhando (a escritura vai pra ela). */
    boolean removerPelaStaff(Player p) {
        RayTraceResult r = p.rayTraceBlocks(8);
        Fazenda f = r == null || r.getHitBlock() == null ? null : em(r.getHitBlock());
        if (f == null) {
            Entity alvo = p.getTargetEntity(8);
            f = alvo == null ? null : daEntidade(alvo);
        }
        if (f == null) return false;
        atualizar(f);
        String dono = Bukkit.getOfflinePlayer(f.dono).getName();
        entregar(p, plugin.escritura(tipo(f), f.nivelProd, f.nivelCofre, f.nivelDespensa), 1);
        desfazer(f);
        p.sendMessage("§eFazenda de §f" + dono + " §eremovida (cofre de §f" + FazendasPlugin.dinheiro(valorCofre(f))
                + " §edescartado). A Escritura foi pro seu inventário.");
        return true;
    }

    // ---------------------------------------------------------------------
    // NPC e holograma (não persistentes: refeitos quando o chunk carrega)
    // ---------------------------------------------------------------------

    private void garantirEntidades(Fazenda f) {
        FazendasPlugin.Tipo t = tipo(f);
        Location centro = f.lugarDoNpc();
        if (t == null || centro == null || !carregado(f)) return;
        Entity npc = f.npc == null ? null : Bukkit.getEntity(f.npc);
        if (npc == null || !npc.isValid()) {
            Mannequin m = centro.getWorld().spawn(centro, Mannequin.class, e -> {
                e.setPersistent(false);
                e.setInvulnerable(true);
                e.setImmovable(true);
                e.setGravity(false);
                e.setSilent(true);
                e.setCustomNameVisible(true);
                e.customName(LEGADO.deserialize("§e" + t.npc()));
                e.setDescription(LEGADO.deserialize("§7Clique pra abrir a fazenda"));
                // o que ele segura e a skin vêm do config de cada tipo ("mao" e "skin")
                Material mao = Material.matchMaterial(plugin.getConfig().getString("fazendas." + t.id() + ".mao", ""));
                if (mao != null) e.getEquipment().setItemInMainHand(new ItemStack(mao));
                String skin = plugin.getConfig().getString("fazendas." + t.id() + ".skin");
                if (skin != null && !skin.isBlank()) e.setProfile(perfil(skin));
                e.getPersistentDataContainer().set(plugin.chaveEntidade(), PersistentDataType.STRING, f.id);
            });
            f.npc = m.getUniqueId();
        }
        garantirEnfeites(f);
    }

    /** Os bichos e enfeites parados de cada tipo (Planos.enfeites), girados pra frente da fazenda. */
    private void garantirEnfeites(Fazenda f) {
        List<Planos.Enfeite> lista = Planos.enfeites(f.tipo);
        while (f.enfeites.size() < lista.size()) f.enfeites.add(null);
        World w = f.world();
        int[] frente = f.frente();
        for (int i = 0; i < lista.size(); i++) {
            UUID u = f.enfeites.get(i);
            Entity atual = u == null ? null : Bukkit.getEntity(u);
            if (atual != null && atual.isValid()) continue;
            Planos.Enfeite en = lista.get(i);
            double[] d = Construcao.paraMundo(frente, en.lx(), en.lz());
            Location l = new Location(w, f.x + 0.5 + d[0], f.y + en.ly(), f.z + 0.5 + d[1], en.yawLocal() + Construcao.giroYaw(frente), 0);
            if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            Entity novo;
            if (en.tipo() == EntityType.ITEM_DISPLAY) {
                novo = w.spawn(l, ItemDisplay.class, e -> {
                    e.setItemStack(new ItemStack(en.item()));
                    e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.45f, 0.45f, 0.45f), new AxisAngle4f()));
                    e.setPersistent(false);
                    e.getPersistentDataContainer().set(plugin.chaveEntidade(), PersistentDataType.STRING, f.id);
                });
            } else {
                novo = w.spawnEntity(l, en.tipo(), CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
                    if (e instanceof LivingEntity v) {
                        v.setAI(false);
                        v.setInvulnerable(true);
                        v.setSilent(true);
                        v.setCollidable(false);
                        v.setRemoveWhenFarAway(false);
                        v.setCanPickupItems(false);
                    }
                    if (e instanceof Ageable a) a.setAdult();
                    if (e instanceof Sheep s && en.cor() != null) s.setColor(en.cor());
                    e.setPersistent(false);
                    e.getPersistentDataContainer().set(plugin.chaveEntidade(), PersistentDataType.STRING, f.id);
                });
            }
            f.enfeites.set(i, novo.getUniqueId());
        }
    }

    private static ResolvableProfile perfil(String skin) {
        if (skin.startsWith("textura:")) {
            String hash = skin.substring("textura:".length()).trim();
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String valor = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return ResolvableProfile.resolvableProfile()
                    .uuid(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)))
                    .name("npc" + hash.substring(0, Math.min(10, hash.length())))
                    .addProperty(new ProfileProperty("textures", valor)).build();
        }
        return ResolvableProfile.resolvableProfile().name(skin).build();
    }

    private void removerEntidades(Fazenda f) {
        List<UUID> todas = new ArrayList<>(f.enfeites);
        todas.add(f.npc);
        todas.add(f.holograma);
        for (UUID u : todas) {
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        f.npc = null;
        f.holograma = null;
        f.enfeites.clear();
    }

    /** A galinha da fazenda não bota ovo (nem bicho nenhum dela solta item). */
    @EventHandler
    public void aoSoltarItem(EntityDropItemEvent evento) {
        if (evento.getEntity().getPersistentDataContainer().has(plugin.chaveEntidade())) evento.setCancelled(true);
    }

    private void atualizarHolograma(Fazenda f) {
        FazendasPlugin.Tipo t = tipo(f);
        Location centro = f.lugarDoNpc();
        if (t == null || centro == null || !carregado(f)) return;
        FazendasPlugin.Insumo ins = insumo(f);
        boolean semInsumo = f.insumo + 1e-9 < t.insumoPorSafra();
        boolean cheio = f.safras >= cofre(f);
        String estado = cheio ? "§c§lCOFRE CHEIO" : semInsumo ? "§c§lSEM " + ChatColor.stripColor(ins.nome()).toUpperCase() : "§a● produzindo";
        String texto = t.nome() + " §7de §f" + Bukkit.getOfflinePlayer(f.dono).getName()
                + "\n§a" + FazendasPlugin.dinheiro(valorCofre(f)) + " §7/ " + FazendasPlugin.dinheiro(cofre(f) * t.rendaPorSafra())
                + "\n§7" + ChatColor.stripColor(ins.nome()) + ": §f" + FazendasPlugin.fmt(f.insumo) + "§7/" + despensa(f)
                + "\n" + estado;
        Entity atual = f.holograma == null ? null : Bukkit.getEntity(f.holograma);
        if (atual instanceof TextDisplay td && td.isValid()) {
            td.text(LEGADO.deserialize(texto));
            return;
        }
        TextDisplay td = centro.getWorld().spawn(centro.clone().add(0, 2.9, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.text(LEGADO.deserialize(texto));
            d.getPersistentDataContainer().set(plugin.chaveEntidade(), PersistentDataType.STRING, f.id);
        });
        f.holograma = td.getUniqueId();
    }

    @EventHandler
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity().getPersistentDataContainer().has(plugin.chaveEntidade())) evento.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // Menu (clique no NPC)
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoClicarNoNpc(PlayerInteractEntityEvent evento) {
        Fazenda f = daEntidade(evento.getRightClicked());
        if (f == null) return;
        evento.setCancelled(true);
        if (evento.getHand() != EquipmentSlot.HAND) return;
        Player p = evento.getPlayer();
        // um clique pode chegar como dois eventos ("interact" e "interact at"): só o primeiro conta
        long agora = System.currentTimeMillis();
        Long anterior = ultimoClique.put(p.getUniqueId(), agora);
        if (anterior != null && agora - anterior < 200) return;
        if (!p.getUniqueId().equals(f.dono) && !p.hasPermission("fazendas.admin")) {
            p.sendMessage("§cEssa fazenda é de §f" + Bukkit.getOfflinePlayer(f.dono).getName() + "§c.");
            return;
        }
        if (bloqueada(p, f)) return;
        atualizar(f);
        // atalho: com Ração ou Adubo na mão, abastece direto
        if (plugin.insumoDoItem(p.getInventory().getItemInMainHand()) != null) {
            abastecer(p, f);
            atualizarHolograma(f);
            return;
        }
        abrirMenu(p, f, false);
    }

    /** Fazenda acima do rank do jogador fica toda bloqueada pra ele (como as máquinas). */
    private boolean bloqueada(Player p, Fazenda f) {
        FazendasPlugin.Tipo t = tipo(f);
        if (t == null || p.hasPermission("fazendas.admin") || plugin.getRankup().podeUsar(p.getUniqueId(), t.rank())) return false;
        p.sendMessage("§cA fazenda " + t.nome() + " §clibera no rank §f" + t.rank() + "§c: até lá, ela fica bloqueada pra você.");
        return true;
    }

    private void abrirMenu(Player p, Fazenda f, boolean confirmarRecolher) {
        atualizar(f);
        FazendasPlugin.Tipo t = tipo(f);
        FazendasPlugin.Insumo ins = insumo(f);
        Menu menu = new Menu(f);
        menu.confirmarRecolher = confirmarRecolher;
        menu.inventario = Bukkit.createInventory(menu, 27, "§8" + ChatColor.stripColor(t.nome()));
        double safrasHora = 3_600_000.0 / intervaloMs(f);
        double porHora = safrasHora * t.rendaPorSafra();
        double mult = plugin.multiplicadorColeta(p) * (1 - plugin.getConfig().getDouble("imposto-coleta", 2) / 100.0);
        menu.inventario.setItem(SLOT_INFO, icone(new ItemStack(t.icone()), t.nome(),
                "§7Rende §a" + FazendasPlugin.dinheiro(porHora) + " §7por hora"
                        + (fatorOffline() < 1 ? " §8(offline: " + FazendasPlugin.dinheiro(porHora * fatorOffline()) + ")" : ""),
                "§7Cofre: §a" + FazendasPlugin.dinheiro(valorCofre(f)) + " §7/ " + FazendasPlugin.dinheiro(cofre(f) * t.rendaPorSafra()),
                "§7" + ChatColor.stripColor(ins.nome()) + ": §f" + FazendasPlugin.fmt(f.insumo) + "/" + despensa(f)
                        + " §7(" + FazendasPlugin.fmt(t.insumoPorSafra()) + " por safra, " + FazendasPlugin.fmt(safrasHora * t.insumoPorSafra()) + "/h)",
                "§7Produtividade §f" + f.nivelProd + " §7| Cofre §f" + f.nivelCofre + " §7| Despensa §f" + f.nivelDespensa));
        menu.inventario.setItem(SLOT_COLETAR, icone(new ItemStack(Material.GOLD_INGOT), "§a§lColetar",
                "§7Cofre: §a" + FazendasPlugin.dinheiro(valorCofre(f)),
                "§7Você recebe §a" + FazendasPlugin.dinheiro(valorCofre(f) * mult),
                "§8(bônus do rank e do VIP, menos o imposto)"));
        menu.inventario.setItem(SLOT_ABASTECER, icone(plugin.insumo(ins, 1), "§6§lAbastecer",
                "§7Põe " + ChatColor.stripColor(ins.nome()) + " do seu inventário", "§7na despensa (cabe §f" + despensa(f) + "§7).",
                "§8Atalho: clique no fazendeiro com ela na mão."));
        menu.inventario.setItem(SLOT_PROD, botaoMelhoria(f, FazendasPlugin.PROD));
        menu.inventario.setItem(SLOT_COFRE, botaoMelhoria(f, FazendasPlugin.COFRE));
        menu.inventario.setItem(SLOT_DESPENSA, botaoMelhoria(f, FazendasPlugin.DESPENSA));
        menu.inventario.setItem(SLOT_RECOLHER, confirmarRecolher
                ? icone(new ItemStack(Material.RED_CONCRETE), "§c§lConfirmar: recolher",
                        "§7A construção some e o chão volta como era.", "§7Você recebe o cofre, o insumo",
                        "§7que sobrou e a Escritura (com os níveis).", "", "§cClique de novo pra confirmar.")
                : icone(new ItemStack(Material.MAP), "§c§lRecolher a Escritura",
                        "§7Desmonta a fazenda e devolve a Escritura", "§7com os níveis (pra mudar de lugar ou negociar)."));
        p.openInventory(menu.inventario);
    }

    private ItemStack botaoMelhoria(Fazenda f, int qual) {
        int nivel = nivelDe(f, qual);
        FazendasPlugin.Tipo t = tipo(f);
        String titulo = qual == FazendasPlugin.PROD ? "§b§lMelhorar produtividade" : qual == FazendasPlugin.COFRE ? "§e§lMelhorar cofre" : "§6§lMelhorar despensa";
        String efeito;
        if (qual == FazendasPlugin.PROD) {
            efeito = "§7-" + (int) plugin.getConfig().getDouble("produtividade-por-nivel", 5) + "% de tempo por safra";
        } else if (qual == FazendasPlugin.COFRE) {
            efeito = "§7+" + FazendasPlugin.dinheiro(plugin.getConfig().getInt("cofre-por-nivel", 64) * t.rendaPorSafra()) + " de cofre";
        } else {
            int antes = despensa(f);
            f.nivelDespensa++;
            int depois = despensa(f);
            f.nivelDespensa--;
            efeito = "§7Despensa: §f" + antes + " §7→ §f" + depois;
        }
        if (nivel >= nivelMaximo()) return icone(new ItemStack(Material.NETHER_STAR), titulo, "§aNível máximo (" + nivel + ").");
        Material icone = qual == FazendasPlugin.PROD ? Material.SUGAR : qual == FazendasPlugin.COFRE ? Material.CHEST : Material.BARREL;
        return icone(new ItemStack(icone), titulo, "§7Nível §f" + nivel + " §7→ §f" + (nivel + 1),
                efeito, "§7Custa §a" + FazendasPlugin.dinheiro(custoMelhoria(f, nivel)));
    }

    @EventHandler
    public void aoClicarNoMenu(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player p) || evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        Fazenda f = menu.f;
        if (!porId.containsValue(f) || bloqueada(p, f)) {
            p.closeInventory();
            return;
        }
        atualizar(f);
        FazendasPlugin.Tipo t = tipo(f);
        switch (evento.getRawSlot()) {
            case SLOT_COLETAR -> coletar(p, f);
            case SLOT_ABASTECER -> abastecer(p, f);
            case SLOT_PROD, SLOT_COFRE, SLOT_DESPENSA -> {
                int qual = evento.getRawSlot() == SLOT_PROD ? FazendasPlugin.PROD : evento.getRawSlot() == SLOT_COFRE ? FazendasPlugin.COFRE : FazendasPlugin.DESPENSA;
                int nivel = nivelDe(f, qual);
                if (nivel >= nivelMaximo()) return;
                double custo = custoMelhoria(f, nivel);
                if (plugin.getEconomy() == null || !plugin.getEconomy().has(p, custo)) {
                    p.sendMessage("§cSaldo insuficiente: a melhoria custa §f" + FazendasPlugin.dinheiro(custo) + "§c.");
                    return;
                }
                plugin.getEconomy().withdrawPlayer(p, custo);
                plugin.getPix().registrarComServidor(p.getUniqueId(), custo, false, "Melhoria de fazenda");
                if (qual == FazendasPlugin.PROD) f.nivelProd++;
                else if (qual == FazendasPlugin.COFRE) f.nivelCofre++;
                else f.nivelDespensa++;
                sujo = true;
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.3f);
                String nome = qual == FazendasPlugin.PROD ? "Produtividade" : qual == FazendasPlugin.COFRE ? "Cofre" : "Despensa";
                p.sendMessage("§a" + nome + " (" + t.nome() + "§a) no nível §f" + (nivel + 1) + " §apor §f" + FazendasPlugin.dinheiro(custo) + "§a.");
            }
            case SLOT_RECOLHER -> {
                if (!menu.confirmarRecolher) {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
                    abrirMenu(p, f, true);
                    return;
                }
                p.closeInventory();
                recolher(p, f);
                return;
            }
            default -> {
                return;
            }
        }
        atualizarHolograma(f);
        abrirMenu(p, f, false);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    private void coletar(Player p, Fazenda f) {
        if (f.safras <= 0) {
            p.sendMessage("§7O cofre está vazio.");
            return;
        }
        if (plugin.getEconomy() == null) {
            p.sendMessage("§cA economia está fora do ar: tente de novo daqui a pouco.");
            return;
        }
        double bruto = valorCofre(f);
        double liquido = Math.floor(bruto * plugin.multiplicadorColeta(p) * (1 - plugin.getConfig().getDouble("imposto-coleta", 2) / 100.0) * 100) / 100;
        plugin.getEconomy().depositPlayer(p, liquido);
        plugin.getPix().registrarComServidor(p.getUniqueId(), liquido, true, "Fazenda: " + ChatColor.stripColor(tipo(f).nome()));
        // se o cofre estava cheio, a produção volta a contar agora
        f.safras = 0;
        f.ultima = System.currentTimeMillis();
        sujo = true;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        p.sendMessage("§aVocê coletou §f" + FazendasPlugin.dinheiro(liquido) + " §ado cofre (" + tipo(f).nome() + "§a).");
    }

    private void abastecer(Player p, Fazenda f) {
        FazendasPlugin.Insumo ins = insumo(f);
        int espaco = (int) Math.floor(despensa(f) - f.insumo + 1e-9);
        int colocados = 0;
        boolean temOutro = false;
        for (ItemStack i : p.getInventory().getContents()) {
            FazendasPlugin.Insumo deste = plugin.insumoDoItem(i);
            if (deste == null) continue;
            if (deste != ins) {
                temOutro = true;
                continue;
            }
            if (espaco - colocados <= 0) break;
            int usar = Math.min(i.getAmount(), espaco - colocados);
            i.setAmount(i.getAmount() - usar);
            colocados += usar;
        }
        if (colocados == 0) {
            p.sendMessage(espaco <= 0 ? "§7A despensa já está cheia."
                    : "§cEssa fazenda usa " + ins.nome() + "§c" + (temOutro ? " (não a que você tem)" : "") + ". Compre em §f/fazendas loja§c.");
            return;
        }
        // se estava parada sem insumo, a produção recomeça agora
        if (f.insumo + 1e-9 < tipo(f).insumoPorSafra()) f.ultima = System.currentTimeMillis();
        f.insumo += colocados;
        sujo = true;
        p.playSound(p.getLocation(), Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 0.8f, 1f);
        p.sendMessage("§aAbasteceu com §f" + colocados + " " + ins.nome() + "§a. Despensa: §f" + FazendasPlugin.fmt(f.insumo) + "/" + despensa(f));
    }

    private void recolher(Player p, Fazenda f) {
        if (f.safras > 0) coletar(p, f);
        int sobra = (int) Math.floor(f.insumo + 1e-9);
        if (sobra > 0) entregar(p, plugin.insumo(insumo(f), 1), sobra);
        entregar(p, plugin.escritura(tipo(f), f.nivelProd, f.nivelCofre, f.nivelDespensa), 1);
        desfazer(f);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        p.sendMessage("§eFazenda recolhida: a Escritura voltou pro seu inventário.");
    }

    static void entregar(Player p, ItemStack modelo, int quantidade) {
        int resta = quantidade;
        while (resta > 0) {
            ItemStack pilha = modelo.clone();
            pilha.setAmount(Math.min(resta, modelo.getMaxStackSize()));
            resta -= pilha.getAmount();
            p.getInventory().addItem(pilha).values().forEach(s -> p.getWorld().dropItemNaturally(p.getLocation(), s));
        }
    }

    private static ItemStack icone(ItemStack base, String nome, String... lore) {
        ItemStack item = base.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------------
    // fazendas.yml
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("fazendas");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            try {
                Fazenda f = new Fazenda(id, s.getString("mundo"), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getString("tipo"),
                        UUID.fromString(s.getString("dono")), (float) s.getDouble("yaw"), s.getInt("altura", 3));
                f.nivelProd = s.getInt("nivel-produtividade");
                f.nivelCofre = s.getInt("nivel-cofre");
                f.nivelDespensa = s.getInt("nivel-despensa");
                f.insumo = s.getDouble("insumo");
                f.safras = s.getInt("safras");
                f.parcialMs = s.getLong("parcial-ms");
                f.ultima = s.getLong("ultima", System.currentTimeMillis());
                f.originais = new ArrayList<>(s.getStringList("originais"));
                porId.put(id, f);
                indexar(f);
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("Fazenda inválida ignorada em fazendas.yml: " + id);
            }
        }
    }

    void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Fazenda f : porId.values()) {
            String b = "fazendas." + f.id;
            yaml.set(b + ".mundo", f.mundo);
            yaml.set(b + ".x", f.x);
            yaml.set(b + ".y", f.y);
            yaml.set(b + ".z", f.z);
            yaml.set(b + ".tipo", f.tipo);
            yaml.set(b + ".dono", f.dono.toString());
            yaml.set(b + ".yaw", (double) f.yaw);
            yaml.set(b + ".altura", f.altura);
            yaml.set(b + ".nivel-produtividade", f.nivelProd);
            yaml.set(b + ".nivel-cofre", f.nivelCofre);
            yaml.set(b + ".nivel-despensa", f.nivelDespensa);
            yaml.set(b + ".insumo", f.insumo);
            yaml.set(b + ".safras", f.safras);
            yaml.set(b + ".parcial-ms", f.parcialMs);
            yaml.set(b + ".ultima", f.ultima);
            yaml.set(b + ".originais", f.originais);
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar fazendas.yml", e);
        }
    }
}
