package com.example.spawners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
 * Os spawners colocados e as pilhas de mobs deles.
 * <ul>
 *   <li><b>Spawner</b>: bloco de spawner no mundo (o mob gira dentro, só enfeite: o spawn do vanilla é
 *       cancelado). Guardado em spawners.yml com o tipo, quantos estão empilhados e o dono.</li>
 *   <li><b>Pilha</b>: a cada ciclo, cada spawner com jogador por perto soma {@code quantidade} mobs na
 *       pilha dele, uma entidade só ("x37 Zumbi"), até {@code limite-pilha}. Nasce num lugar livre em
 *       cima ou em volta do spawner e é um mob normal: anda, a água arrasta, ataca, queima e toma
 *       dano de tudo. Só não some de longe, não pega item e o creeper não explode (levaria a pilha
 *       inteira sem drop nenhum).</li>
 *   <li><b>Abate</b>: a morte (do jogador ou não) derruba os drops e o XP de todos os mobs da pilha de
 *       uma vez, e o spawner começa uma pilha nova no ciclo seguinte. O mob nasce com o motivo
 *       "spawner": as missões não contam, e as skills dão XP só pelo dano do golpe (com o multiplicador
 *       de pilha).</li>
 * </ul>
 */
public class SpawnerManager implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    /** Um spawner colocado. */
    static final class Colocado {
        final String id;
        final String mundo;
        final int x, y, z;
        final String tipo;
        int quantidade;
        final UUID dono;
        UUID pilha;      // a entidade da pilha de mobs (pode ter sumido)
        UUID holograma;  // o texto em cima do bloco

        Colocado(String id, String mundo, int x, int y, int z, String tipo, int quantidade, UUID dono) {
            this.id = id;
            this.mundo = mundo;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tipo = tipo;
            this.quantidade = quantidade;
            this.dono = dono;
        }

        Location bloco() {
            World w = Bukkit.getWorld(mundo);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final SpawnersPlugin plugin;
    private final File arquivo;
    /** "mundo;x;y;z" -> spawner. */
    private final Map<String, Colocado> porLugar = new LinkedHashMap<>();
    private final Map<UUID, Colocado> porPilha = new HashMap<>();
    private final NamespacedKey chavePilha;
    private final NamespacedKey chaveSpawner;
    private boolean sujo;

    SpawnerManager(SpawnersPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "spawners.yml");
        // a mesma chave que o SkillsPlugin procura pra dar menos XP ("spawnersplugin:pilha")
        this.chavePilha = new NamespacedKey(plugin, "pilha");
        this.chaveSpawner = new NamespacedKey(plugin, "spawner");
        carregar();
        Bukkit.getScheduler().runTaskTimer(plugin, this::salvarSeMudou, 20L * 60, 20L * 60);
    }

    int quantos() {
        return porLugar.size();
    }

    List<String> resumo() {
        List<String> linhas = new ArrayList<>();
        for (Colocado c : porLugar.values()) {
            SpawnersPlugin.Tipo t = plugin.tipo(c.tipo);
            linhas.add("§f" + (t != null ? t.nome() : c.tipo) + " §7x" + c.quantidade + " em §f" + c.mundo + " " + c.x + " " + c.y + " " + c.z
                    + " §7(dono " + Bukkit.getOfflinePlayer(c.dono).getName() + ")");
        }
        return linhas;
    }

    private static String chave(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private int limitePorBloco() {
        return Math.max(1, plugin.getConfig().getInt("limite-por-bloco", 64));
    }

    // ---------------------------------------------------------------------
    // Colocar, empilhar e quebrar
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        SpawnersPlugin.Tipo tipo = plugin.tipoDoItem(evento.getItemInHand());
        if (tipo == null) return;
        Player jogador = evento.getPlayer();
        Block bloco = evento.getBlockPlaced();
        if (!plugin.getConfig().getStringList("mundos").contains(bloco.getWorld().getName())) {
            evento.setCancelled(true);
            jogador.sendMessage("§cSpawner só pode ser colocado no seu terreno (§f/terreno home§c).");
            return;
        }
        // o bloco: mostra o mob girando, mas o spawn do vanilla fica desligado (quem gera é o ciclo)
        if (bloco.getState() instanceof CreatureSpawner estado) {
            estado.setSpawnedType(tipo.entidade());
            estado.setRequiredPlayerRange(0);
            estado.update();
        }
        Colocado c = new Colocado(UUID.randomUUID().toString(), bloco.getWorld().getName(), bloco.getX(), bloco.getY(), bloco.getZ(),
                tipo.id(), 1, jogador.getUniqueId());
        porLugar.put(chave(bloco), c);
        sujo = true;
        atualizarHolograma(c);
        jogador.sendMessage("§aSpawner de " + tipo.nome() + " §acolocado. §7Pra empilhar, clique nele com outro igual na mão.");
    }

    /** Clique direito num spawner nosso: com outro igual na mão, empilha (shift: a mão inteira); vazio, mostra. */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getHand() != EquipmentSlot.HAND || evento.getClickedBlock() == null) return;
        Colocado c = porLugar.get(chave(evento.getClickedBlock()));
        if (c == null) return;
        Player jogador = evento.getPlayer();
        ItemStack mao = jogador.getInventory().getItemInMainHand();
        SpawnersPlugin.Tipo tipoMao = plugin.tipoDoItem(mao);
        SpawnersPlugin.Tipo tipo = plugin.tipo(c.tipo);
        if (tipoMao == null && mao.getType().name().endsWith("_SPAWN_EGG")) {
            evento.setCancelled(true); // ovo trocaria o mob do spawner
            return;
        }
        if (tipoMao == null) {
            if (tipo != null && !jogador.isSneaking()) {
                jogador.sendActionBar(LEGADO.deserialize("§eSpawner de " + tipo.nome() + " §7x" + c.quantidade + "/" + limitePorBloco()
                        + " §7| dono: §f" + Bukkit.getOfflinePlayer(c.dono).getName()));
            }
            return;
        }
        evento.setCancelled(true);
        if (!tipoMao.id().equals(c.tipo)) {
            jogador.sendMessage("§cSó dá pra empilhar spawner do mesmo tipo.");
            return;
        }
        if (!podeMexer(jogador, c)) {
            jogador.sendMessage("§cEsse spawner não é seu.");
            return;
        }
        int cabe = limitePorBloco() - c.quantidade;
        if (cabe <= 0) {
            jogador.sendMessage("§cEsse bloco já tem o máximo (§f" + limitePorBloco() + "§c). Coloque outro ao lado.");
            return;
        }
        int adicionar = Math.min(cabe, jogador.isSneaking() ? mao.getAmount() : 1);
        mao.setAmount(mao.getAmount() - adicionar);
        c.quantidade += adicionar;
        sujo = true;
        atualizarHolograma(c);
        jogador.playSound(evento.getClickedBlock().getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.6f);
        jogador.sendActionBar(LEGADO.deserialize("§aSpawner de " + tipo.nome() + " §ax" + c.quantidade));
    }

    /** Só o dono (ou a staff) empilha: quem só visita o terreno não mexe no spawner dos outros. */
    private boolean podeMexer(Player jogador, Colocado c) {
        return jogador.getUniqueId().equals(c.dono) || jogador.hasPermission("spawners.admin");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Colocado c = porLugar.get(chave(evento.getBlock()));
        if (c == null) return;
        // a proteção dos terrenos já cancelou se o jogador não pode mexer aqui
        evento.setDropItems(false);
        evento.setExpToDrop(0);
        remover(c);
        SpawnersPlugin.Tipo tipo = plugin.tipo(c.tipo);
        if (tipo != null && evento.getPlayer().getGameMode() != GameMode.CREATIVE) {
            int resta = c.quantidade;
            while (resta > 0) {
                int q = Math.min(64, resta);
                evento.getBlock().getWorld().dropItemNaturally(evento.getBlock().getLocation().add(0.5, 0.5, 0.5), plugin.item(tipo, q));
                resta -= q;
            }
        }
        evento.getPlayer().sendMessage("§eSpawner retirado §7(x" + c.quantidade + ")§e.");
    }

    private void remover(Colocado c) {
        porLugar.values().remove(c);
        if (c.pilha != null) {
            porPilha.remove(c.pilha);
            Entity e = Bukkit.getEntity(c.pilha);
            if (e != null) e.remove();
        }
        if (c.holograma != null) {
            Entity h = Bukkit.getEntity(c.holograma);
            if (h != null) h.remove();
        }
        sujo = true;
    }

    /** Explosão não quebra spawner nosso. */
    @EventHandler(ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        evento.blockList().removeIf(b -> porLugar.containsKey(chave(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        evento.blockList().removeIf(b -> porLugar.containsKey(chave(b)));
    }

    /** O spawner nosso nunca gera pelo vanilla (o setRequiredPlayerRange já impede; isso é a garantia). */
    @EventHandler(ignoreCancelled = true)
    public void aoSpawnVanilla(SpawnerSpawnEvent evento) {
        if (evento.getSpawner() != null && porLugar.containsKey(chave(evento.getSpawner().getBlock()))) evento.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // Ciclo: soma mobs nas pilhas
    // ---------------------------------------------------------------------

    void ciclo() {
        double raio = plugin.getConfig().getDouble("raio-ativacao", 16);
        int limite = Math.max(1, plugin.getConfig().getInt("limite-pilha", 300));
        for (Colocado c : porLugar.values()) {
            Location bloco = c.bloco();
            if (bloco == null || !bloco.getWorld().isChunkLoaded(c.x >> 4, c.z >> 4)) continue;
            Location centro = bloco.clone().add(0.5, 0.5, 0.5);
            boolean alguemPerto = bloco.getWorld().getPlayers().stream()
                    .anyMatch(p -> p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(centro) <= raio * raio);
            if (!alguemPerto) continue;
            SpawnersPlugin.Tipo tipo = plugin.tipo(c.tipo);
            if (tipo == null) continue;
            LivingEntity pilha = pilhaDe(c);
            if (pilha == null) {
                criarPilha(c, tipo, Math.min(limite, c.quantidade));
            } else {
                acordar(pilha);
                int atual = contar(pilha);
                if (atual >= limite) continue;
                definirContagem(pilha, tipo, Math.min(limite, atual + c.quantidade));
            }
        }
    }

    private LivingEntity pilhaDe(Colocado c) {
        if (c.pilha == null) return null;
        Entity e = Bukkit.getEntity(c.pilha);
        return e instanceof LivingEntity vivo && vivo.isValid() && !vivo.isDead() ? vivo : null;
    }

    private void criarPilha(Colocado c, SpawnersPlugin.Tipo tipo, int contagem) {
        Location onde = lugarParaNascer(c.bloco());
        Class<? extends Entity> classe = tipo.entidade().getEntityClass();
        if (classe == null) return;
        // motivo "spawner": as missões de matar não contam mob de spawner
        Entity e = onde.getWorld().spawn(onde, classe, CreatureSpawnEvent.SpawnReason.SPAWNER, nova -> prepararMob(nova, c, tipo, contagem));
        if (c.pilha != null) porPilha.remove(c.pilha);
        c.pilha = e.getUniqueId();
        porPilha.put(c.pilha, c);
    }

    /**
     * Onde a pilha nasce: em cima do spawner, se couber o mob ali, senão o lugar livre mais perto em
     * volta dele (chão firme e dois blocos de espaço), pra não nascer dentro de bloco.
     */
    private static Location lugarParaNascer(Location spawner) {
        Location melhor = null;
        double menor = Double.MAX_VALUE;
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0 && dy != 1) continue; // o próprio spawner
                    Block pe = spawner.getBlock().getRelative(dx, dy, dz);
                    if (!livre(pe) || !livre(pe.getRelative(0, 1, 0)) || !pe.getRelative(0, -1, 0).getType().isSolid()) continue;
                    double distancia = dx * dx + dz * dz + (dy == 1 ? 0 : 0.5);
                    if (distancia < menor) {
                        menor = distancia;
                        melhor = pe.getLocation().add(0.5, 0, 0.5);
                    }
                }
            }
        }
        return melhor != null ? melhor : spawner.clone().add(0.5, 1, 0.5);
    }

    /** As pilhas das versões antigas nasciam sem IA (paradas, sem gravidade): passam a ser mob normal. */
    private static void acordar(LivingEntity pilha) {
        if (pilha instanceof Mob mob && !mob.hasAI()) mob.setAI(true);
    }

    private static boolean livre(Block b) {
        return b.isPassable() && !b.isLiquid();
    }

    private void prepararMob(Entity e, Colocado c, SpawnersPlugin.Tipo tipo, int contagem) {
        if (e instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            EntityEquipment equipamento = mob.getEquipment();
            if (equipamento != null) {
                // o equipamento fica (o esqueleto atira), mas não cai: na pilha toda viraria uma pilha de arcos
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (mob.canUseEquipmentSlot(slot)) equipamento.setDropChance(slot, 0f);
                }
            }
        }
        if (e instanceof Zombie z) z.setAdult();
        if (e instanceof Slime s) s.setSize(1); // pequeno: não se divide ao morrer
        e.setPersistent(true);
        e.setSilent(true);
        e.getPersistentDataContainer().set(chaveSpawner, PersistentDataType.STRING, c.id);
        definirContagem(e, tipo, contagem);
    }

    private int contar(Entity e) {
        Integer n = e.getPersistentDataContainer().get(chavePilha, PersistentDataType.INTEGER);
        return n == null ? 1 : n;
    }

    private void definirContagem(Entity e, SpawnersPlugin.Tipo tipo, int contagem) {
        e.getPersistentDataContainer().set(chavePilha, PersistentDataType.INTEGER, contagem);
        e.customName(LEGADO.deserialize("§e§lx" + contagem + " §f" + tipo.nome()));
        e.setCustomNameVisible(true);
    }

    // ---------------------------------------------------------------------
    // A pilha: um mob normal, e a morte vale por todos
    // ---------------------------------------------------------------------

    private Colocado colocadoDaPilha(Entity e) {
        if (!e.getPersistentDataContainer().has(chavePilha, PersistentDataType.INTEGER)) return null;
        Colocado c = porPilha.get(e.getUniqueId());
        if (c != null) return c;
        // a pilha que voltou com o chunk (depois de um reinício): acha o spawner pelo id
        String id = e.getPersistentDataContainer().get(chaveSpawner, PersistentDataType.STRING);
        for (Colocado x : porLugar.values()) {
            if (x.id.equals(id)) {
                x.pilha = e.getUniqueId();
                porPilha.put(x.pilha, x);
                return x;
            }
        }
        return null;
    }

    /** O creeper da pilha chia, mas não explode: a explosão some com a pilha inteira, sem drop nenhum. */
    @EventHandler(ignoreCancelled = true)
    public void aoArmarExplosao(ExplosionPrimeEvent evento) {
        if (evento.getEntity().getPersistentDataContainer().has(chavePilha)) evento.setCancelled(true);
    }

    /** A morte vale pela pilha toda: os drops (já com Pilhagem) e o XP de um mob, vezes a contagem. */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoMorrer(EntityDeathEvent evento) {
        LivingEntity morto = evento.getEntity();
        if (!morto.getPersistentDataContainer().has(chavePilha, PersistentDataType.INTEGER)) return;
        int contagem = contar(morto);
        Colocado c = colocadoDaPilha(morto);
        if (c != null) {
            porPilha.remove(morto.getUniqueId());
            c.pilha = null; // o próximo ciclo começa uma pilha nova
        }
        if (contagem <= 1) return;
        List<ItemStack> todos = new ArrayList<>();
        for (ItemStack drop : evento.getDrops()) {
            if (drop == null || drop.getType().isAir()) continue;
            long total = (long) drop.getAmount() * contagem;
            while (total > 0) {
                ItemStack parte = drop.clone();
                parte.setAmount((int) Math.min(total, drop.getMaxStackSize()));
                total -= parte.getAmount();
                todos.add(parte);
            }
        }
        evento.getDrops().clear();
        evento.getDrops().addAll(todos);
        evento.setDroppedExp((int) Math.min(Integer.MAX_VALUE, (long) evento.getDroppedExp() * contagem));
    }

    /**
     * A pilha volta com o chunk (depois de um reinício, ou de andar pra um chunk que descarregou). Se
     * o spawner já começou outra nesse meio tempo, as duas viram uma só.
     */
    @EventHandler
    public void aoCarregarEntidades(EntitiesLoadEvent evento) {
        int limite = Math.max(1, plugin.getConfig().getInt("limite-pilha", 300));
        for (Entity e : evento.getEntities()) {
            if (!(e instanceof LivingEntity vivo) || !e.getPersistentDataContainer().has(chavePilha, PersistentDataType.INTEGER)) continue;
            acordar(vivo);
            Colocado c = porPilha.get(e.getUniqueId());
            if (c != null) continue;
            String id = e.getPersistentDataContainer().get(chaveSpawner, PersistentDataType.STRING);
            for (Colocado x : porLugar.values()) {
                if (!x.id.equals(id)) continue;
                LivingEntity atual = pilhaDe(x);
                SpawnersPlugin.Tipo tipo = plugin.tipo(x.tipo);
                if (atual != null && !atual.getUniqueId().equals(e.getUniqueId()) && tipo != null) {
                    definirContagem(atual, tipo, Math.min(limite, contar(atual) + contar(e)));
                    e.remove();
                } else {
                    if (x.pilha != null) porPilha.remove(x.pilha);
                    x.pilha = e.getUniqueId();
                    porPilha.put(x.pilha, x);
                }
                break;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Holograma em cima do spawner
    // ---------------------------------------------------------------------

    private void atualizarHolograma(Colocado c) {
        SpawnersPlugin.Tipo tipo = plugin.tipo(c.tipo);
        Location bloco = c.bloco();
        if (tipo == null || bloco == null) return;
        Entity atual = c.holograma == null ? null : Bukkit.getEntity(c.holograma);
        String texto = "§eSpawner de " + tipo.nome() + " §7x" + c.quantidade;
        if (atual instanceof TextDisplay td) {
            td.text(LEGADO.deserialize(texto));
            return;
        }
        TextDisplay td = bloco.getWorld().spawn(bloco.clone().add(0.5, 3.2, 0.5), TextDisplay.class, d -> {
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.setShadowed(true);
            d.text(LEGADO.deserialize(texto));
            d.setPersistent(true);
        });
        c.holograma = td.getUniqueId();
        sujo = true;
    }

    // ---------------------------------------------------------------------
    // spawners.yml
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("spawners");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            try {
                Colocado c = new Colocado(id, s.getString("mundo"), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getString("tipo"),
                        s.getInt("quantidade", 1), UUID.fromString(s.getString("dono")));
                String pilha = s.getString("pilha"), holo = s.getString("holograma");
                if (pilha != null) c.pilha = UUID.fromString(pilha);
                if (holo != null) c.holograma = UUID.fromString(holo);
                porLugar.put(c.mundo + ";" + c.x + ";" + c.y + ";" + c.z, c);
                if (c.pilha != null) porPilha.put(c.pilha, c);
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("Spawner inválido ignorado em spawners.yml: " + id);
            }
        }
    }

    private void salvarSeMudou() {
        if (sujo) salvar();
    }

    void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Colocado c : porLugar.values()) {
            String base = "spawners." + c.id;
            yaml.set(base + ".mundo", c.mundo);
            yaml.set(base + ".x", c.x);
            yaml.set(base + ".y", c.y);
            yaml.set(base + ".z", c.z);
            yaml.set(base + ".tipo", c.tipo);
            yaml.set(base + ".quantidade", c.quantidade);
            yaml.set(base + ".dono", c.dono.toString());
            if (c.pilha != null) yaml.set(base + ".pilha", c.pilha.toString());
            if (c.holograma != null) yaml.set(base + ".holograma", c.holograma.toString());
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar spawners.yml", e);
        }
    }
}
