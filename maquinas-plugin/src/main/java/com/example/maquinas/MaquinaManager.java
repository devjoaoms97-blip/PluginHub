package com.example.maquinas;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * As máquinas colocadas.
 * <ul>
 *   <li><b>No mundo</b>: um bloco de barreira (colisão e clique, invisível) e um item display com o
 *       modelo da máquina (resource pack), virado pra quem colocou; em cima, um holograma com o
 *       estoque e o combustível.</li>
 *   <li><b>Produção</b>: pelo relógio, mesmo com o dono offline ou o terreno descarregado. A cada
 *       {@code intervalo} sai um produto, gastando {@code combustivel-por-produto} Células, até acabar
 *       o combustível ou encher o estoque (que enche em uma a duas horas: a máquina recompensa quem
 *       passa pra coletar, não quem some por dias).</li>
 *   <li><b>Menu</b> (clique direito, só o dono): coletar, abastecer, melhorar velocidade e estoque
 *       (o dinheiro sai de circulação) e recolher (volta o item com os níveis).</li>
 *   <li><b>Rank</b>: só coloca e mexe na máquina quem tem o rank dela (ou já fez prestígio). A máquina
 *       que chegou por outro caminho (tesouro, barraca) fica toda bloqueada até lá; vender, pode.</li>
 * </ul>
 */
public class MaquinaManager implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    static final class Maquina {
        final String id;
        final String mundo;
        final int x, y, z;
        final String tipo;
        final UUID dono;
        final float yaw;
        int nivelVel, nivelEst;
        double combustivel;
        int estoque;
        long parcialMs;
        long ultima;
        UUID display, holograma;

        Maquina(String id, String mundo, int x, int y, int z, String tipo, UUID dono, float yaw) {
            this.id = id;
            this.mundo = mundo;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tipo = tipo;
            this.dono = dono;
            this.yaw = yaw;
        }

        Location bloco() {
            World w = Bukkit.getWorld(mundo);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    /** O menu de uma máquina. */
    private static final class Menu implements InventoryHolder {
        final Maquina m;
        Inventory inventario;

        Menu(Maquina m) {
            this.m = m;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private static final int SLOT_INFO = 4, SLOT_COLETAR = 10, SLOT_ABASTECER = 12, SLOT_VEL = 14, SLOT_EST = 16, SLOT_RECOLHER = 22;

    private final MaquinasPlugin plugin;
    private final File arquivo;
    private final Map<String, Maquina> porLugar = new LinkedHashMap<>();
    private boolean sujo;

    MaquinaManager(MaquinasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "maquinas.yml");
        carregar();
    }

    int quantas() {
        return porLugar.size();
    }

    List<String> resumo() {
        List<String> linhas = new ArrayList<>();
        for (Maquina m : porLugar.values()) {
            atualizar(m);
            linhas.add("§f" + m.tipo + " §7em §f" + m.mundo + " " + m.x + " " + m.y + " " + m.z + " §7dono §f"
                    + Bukkit.getOfflinePlayer(m.dono).getName() + " §7estoque §f" + m.estoque + " §7combustível §f" + fmt(m.combustivel));
        }
        return linhas;
    }

    private static String chave(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    // ---------------------------------------------------------------------
    // Números de cada máquina (com os níveis)
    // ---------------------------------------------------------------------

    private MaquinasPlugin.Tipo tipo(Maquina m) {
        return plugin.getTipos().get(m.tipo);
    }

    long intervaloMs(Maquina m) {
        double reducao = plugin.getConfig().getDouble("velocidade-por-nivel", 5) / 100.0 * m.nivelVel;
        return Math.max(1000, (long) (tipo(m).intervaloSegundos() * 1000 * (1 - reducao)));
    }

    int capacidade(Maquina m) {
        return plugin.getConfig().getInt("estoque-base", 96) + plugin.getConfig().getInt("estoque-por-nivel", 64) * m.nivelEst;
    }

    private int tanque() {
        return plugin.getConfig().getInt("tanque", 64);
    }

    private int nivelMaximo() {
        return plugin.getConfig().getInt("nivel-maximo", 5);
    }

    private double custoMelhoria(Maquina m, int nivelAtual) {
        return tipo(m).preco() * plugin.getConfig().getDouble("custo-melhoria", 0.3) * (nivelAtual + 1);
    }

    /** Põe a produção em dia pelo relógio. */
    void atualizar(Maquina m) {
        MaquinasPlugin.Tipo t = tipo(m);
        long agora = System.currentTimeMillis();
        long passou = Math.max(0, agora - m.ultima);
        m.ultima = agora;
        if (t == null) return;
        int cabe = capacidade(m) - m.estoque;
        int daCombustivel = (int) Math.floor(m.combustivel / t.combustivelPorProduto() + 1e-9);
        if (cabe <= 0 || daCombustivel <= 0) {
            m.parcialMs = 0;
            return;
        }
        long total = m.parcialMs + passou;
        long intervalo = intervaloMs(m);
        int feitos = (int) Math.min(total / intervalo, Math.min(cabe, daCombustivel));
        m.estoque += feitos;
        m.combustivel -= feitos * t.combustivelPorProduto();
        boolean parou = m.estoque >= capacidade(m) || m.combustivel + 1e-9 < t.combustivelPorProduto();
        m.parcialMs = parou ? 0 : total - (long) feitos * intervalo;
        if (feitos > 0) sujo = true;
    }

    /** Teste da staff: o relógio de todas as máquinas anda {@code minutos} (a próxima atualização produz). */
    void acelerar(int minutos) {
        for (Maquina m : porLugar.values()) {
            m.ultima -= minutos * 60_000L;
            atualizar(m);
            atualizarHolograma(m);
        }
        sujo = true;
    }

    /** A cada 20 s: em dia a produção das máquinas carregadas e o holograma delas. */
    void tick() {
        for (Maquina m : porLugar.values()) {
            Location b = m.bloco();
            if (b == null || !b.getWorld().isChunkLoaded(m.x >> 4, m.z >> 4)) continue;
            atualizar(m);
            atualizarHolograma(m);
        }
        if (sujo) salvar();
    }

    // ---------------------------------------------------------------------
    // Colocar
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        MaquinasPlugin.Tipo t = plugin.tipoDoItem(evento.getItemInHand());
        if (t == null) return;
        Player jogador = evento.getPlayer();
        Block bloco = evento.getBlockPlaced();
        if (!plugin.getConfig().getStringList("mundos").contains(bloco.getWorld().getName())) {
            evento.setCancelled(true);
            jogador.sendMessage("§cMáquina só pode ser colocada no seu terreno (§f/terreno home§c).");
            return;
        }
        if (!plugin.getRankup().podeUsar(jogador.getUniqueId(), t.rank())) {
            evento.setCancelled(true);
            jogador.sendMessage("§cA máquina " + t.nome() + " §clibera no rank §f" + t.rank() + "§c. Guarde até chegar lá, ou venda na barraca.");
            return;
        }
        long minhas = porLugar.values().stream().filter(m -> m.dono.equals(jogador.getUniqueId())).count();
        if (minhas >= plugin.limiteDeMaquinas(jogador)) {
            evento.setCancelled(true);
            jogador.sendMessage("§cVocê já tem o máximo de máquinas (§f" + plugin.limiteDeMaquinas(jogador) + "§c). VIP pode ter mais.");
            return;
        }
        // a frente da máquina olha pra quem colocou: o ângulo (em torno do Y) que leva a frente do
        // modelo, que é o norte, pro lado oposto ao que o jogador olha
        float yaw = switch (jogador.getFacing()) {
            case NORTH -> 180;
            case EAST -> 90;
            case WEST -> -90;
            default -> 0;
        };
        Maquina m = new Maquina(UUID.randomUUID().toString(), bloco.getWorld().getName(), bloco.getX(), bloco.getY(), bloco.getZ(),
                t.id(), jogador.getUniqueId(), yaw);
        m.nivelVel = plugin.nivel(evento.getItemInHand(), true);
        m.nivelEst = plugin.nivel(evento.getItemInHand(), false);
        m.ultima = System.currentTimeMillis();
        porLugar.put(chave(bloco), m);
        criarDisplay(m);
        atualizarHolograma(m);
        sujo = true;
        jogador.sendMessage("§a" + t.nome() + " §acolocada! §7Clique nela pra abastecer com Células de Combustível.");
    }

    private void criarDisplay(Maquina m) {
        MaquinasPlugin.Tipo t = tipo(m);
        Location centro = m.bloco().add(0.5, 0.5, 0.5);
        ItemStack modelo = plugin.maquina(t, 0, 0);
        float angulo = (float) Math.toRadians(m.yaw);
        ItemDisplay d = centro.getWorld().spawn(centro, ItemDisplay.class, e -> {
            e.setItemStack(modelo);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angulo, 0, 1, 0),
                    new Vector3f(1.001f, 1.001f, 1.001f), new AxisAngle4f()));
            e.setPersistent(true);
        });
        m.display = d.getUniqueId();
    }

    private void atualizarHolograma(Maquina m) {
        MaquinasPlugin.Tipo t = tipo(m);
        Location b = m.bloco();
        if (t == null || b == null) return;
        boolean semCombustivel = m.combustivel + 1e-9 < t.combustivelPorProduto();
        boolean cheia = m.estoque >= capacidade(m);
        String estado = cheia ? "§c§lCHEIA" : semCombustivel ? "§c§lSEM COMBUSTÍVEL" : "§a● produzindo";
        String texto = "§e⚙ " + t.nome() + "\n§f" + m.estoque + "§7/" + capacidade(m) + " " + org.bukkit.ChatColor.stripColor(plugin.getProdutos().get(t.produto()).nome())
                + "  §6⛽ §f" + fmt(m.combustivel) + "\n" + estado;
        Entity atual = m.holograma == null ? null : Bukkit.getEntity(m.holograma);
        if (atual instanceof TextDisplay td) {
            td.text(LEGADO.deserialize(texto));
            return;
        }
        TextDisplay td = b.getWorld().spawn(b.clone().add(0.5, 1.4, 0.5), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.text(LEGADO.deserialize(texto));
            d.setPersistent(true);
        });
        m.holograma = td.getUniqueId();
        sujo = true;
    }

    // ---------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicar(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getHand() != EquipmentSlot.HAND || evento.getClickedBlock() == null) return;
        Maquina m = porLugar.get(chave(evento.getClickedBlock()));
        if (m == null) return;
        evento.setCancelled(true);
        Player p = evento.getPlayer();
        if (!p.getUniqueId().equals(m.dono) && !p.hasPermission("maquinas.admin")) {
            p.sendMessage("§cEssa máquina é de §f" + Bukkit.getOfflinePlayer(m.dono).getName() + "§c.");
            return;
        }
        if (bloqueada(p, m)) return;
        abrirMenu(p, m);
    }

    /**
     * Máquina acima do rank do jogador fica toda bloqueada pra ele (nem coletar, nem abastecer, nem
     * recolher), mesmo sendo o dono: pode existir uma colocada antes da trava, ou dada pela staff.
     */
    private boolean bloqueada(Player p, Maquina m) {
        MaquinasPlugin.Tipo t = tipo(m);
        if (t == null || p.hasPermission("maquinas.admin") || plugin.getRankup().podeUsar(p.getUniqueId(), t.rank())) return false;
        p.sendMessage("§cA máquina " + t.nome() + " §clibera no rank §f" + t.rank() + "§c: até lá, ela fica bloqueada pra você.");
        return true;
    }

    private void abrirMenu(Player p, Maquina m) {
        atualizar(m);
        MaquinasPlugin.Tipo t = tipo(m);
        MaquinasPlugin.Produto prod = plugin.getProdutos().get(t.produto());
        Menu menu = new Menu(m);
        menu.inventario = Bukkit.createInventory(menu, 27, "§8⚙ " + org.bukkit.ChatColor.stripColor(t.nome()));
        double porHora = 3_600_000.0 / intervaloMs(m);
        menu.inventario.setItem(SLOT_INFO, icone(plugin.maquina(t, m.nivelVel, m.nivelEst), "§e⚙ " + t.nome(),
                "§7Fabrica: §f" + prod.nome() + " §7(" + fmt(porHora) + " por hora)",
                "§7Estoque: §f" + m.estoque + "/" + capacidade(m),
                "§7Combustível: §f" + fmt(m.combustivel) + "/" + tanque() + " §7(" + fmt(t.combustivelPorProduto()) + " por produto)",
                "§7Velocidade nível §f" + m.nivelVel + " §7| Estoque nível §f" + m.nivelEst));
        menu.inventario.setItem(SLOT_COLETAR, icone(plugin.produto(prod, 1), "§a§lColetar",
                "§7Pega os §f" + m.estoque + " §7produtos do estoque.", "§7Venda no balcão §6Máquinas§7 da Loja Oficial."));
        menu.inventario.setItem(SLOT_ABASTECER, icone(plugin.combustivel(1), "§6§lAbastecer",
                "§7Põe as Células de Combustível do seu", "§7inventário no tanque (cabe §f" + tanque() + "§7)."));
        menu.inventario.setItem(SLOT_VEL, botaoMelhoria(m, true));
        menu.inventario.setItem(SLOT_EST, botaoMelhoria(m, false));
        menu.inventario.setItem(SLOT_RECOLHER, icone(new ItemStack(Material.HOPPER), "§c§lRecolher",
                "§7Coleta o estoque e devolve a máquina", "§7(com os níveis) e o combustível que sobrou."));
        p.openInventory(menu.inventario);
    }

    private ItemStack botaoMelhoria(Maquina m, boolean velocidade) {
        int nivel = velocidade ? m.nivelVel : m.nivelEst;
        String titulo = velocidade ? "§b§lMelhorar velocidade" : "§d§lMelhorar estoque";
        String efeito = velocidade ? "§7-" + (int) plugin.getConfig().getDouble("velocidade-por-nivel", 5) + "% de tempo por produto"
                : "§7+" + plugin.getConfig().getInt("estoque-por-nivel", 64) + " de estoque";
        if (nivel >= nivelMaximo()) return icone(new ItemStack(Material.NETHER_STAR), titulo, "§aNível máximo (" + nivel + ").");
        return icone(new ItemStack(velocidade ? Material.SUGAR : Material.CHEST), titulo, "§7Nível §f" + nivel + " §7→ §f" + (nivel + 1),
                efeito, "§7Custa §a" + MaquinasPlugin.dinheiro(custoMelhoria(m, nivel)));
    }

    @EventHandler
    public void aoClicarNoMenu(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player p) || evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        Maquina m = menu.m;
        if (!porLugar.containsValue(m)) {
            p.closeInventory();
            return;
        }
        if (bloqueada(p, m)) {
            p.closeInventory();
            return;
        }
        atualizar(m);
        MaquinasPlugin.Tipo t = tipo(m);
        switch (evento.getRawSlot()) {
            case SLOT_COLETAR -> coletar(p, m);
            case SLOT_ABASTECER -> abastecer(p, m);
            case SLOT_VEL, SLOT_EST -> {
                boolean vel = evento.getRawSlot() == SLOT_VEL;
                int nivel = vel ? m.nivelVel : m.nivelEst;
                if (nivel >= nivelMaximo()) return;
                double custo = custoMelhoria(m, nivel);
                if (plugin.getEconomy() == null || !plugin.getEconomy().has(p, custo)) {
                    p.sendMessage("§cSaldo insuficiente: a melhoria custa §f" + MaquinasPlugin.dinheiro(custo) + "§c.");
                    return;
                }
                plugin.getEconomy().withdrawPlayer(p, custo);
                plugin.getPix().registrarComServidor(p.getUniqueId(), custo, false, "Melhoria de máquina");
                if (vel) m.nivelVel++;
                else m.nivelEst++;
                sujo = true;
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.3f);
                p.sendMessage("§a" + (vel ? "Velocidade" : "Estoque") + " da " + t.nome() + " §ano nível §f" + (nivel + 1) + "§a.");
            }
            case SLOT_RECOLHER -> {
                recolher(p, m);
                p.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        atualizarHolograma(m);
        abrirMenu(p, m);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    private void coletar(Player p, Maquina m) {
        if (m.estoque <= 0) {
            p.sendMessage("§7O estoque está vazio.");
            return;
        }
        MaquinasPlugin.Produto prod = plugin.getProdutos().get(tipo(m).produto());
        int total = m.estoque;
        entregar(p, plugin.produto(prod, 1), total);
        // se o estoque estava cheio, a produção volta a contar agora
        m.estoque = 0;
        m.ultima = System.currentTimeMillis();
        sujo = true;
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        p.sendMessage("§aVocê coletou §f" + total + "x " + prod.nome() + "§a.");
    }

    private void abastecer(Player p, Maquina m) {
        int espaco = (int) Math.floor(tanque() - m.combustivel + 1e-9);
        int colocadas = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (espaco - colocadas <= 0) break;
            if (!plugin.ehCombustivel(i)) continue;
            int usar = Math.min(i.getAmount(), espaco - colocadas);
            i.setAmount(i.getAmount() - usar);
            colocadas += usar;
        }
        if (colocadas == 0) {
            p.sendMessage(espaco <= 0 ? "§7O tanque já está cheio." : "§cVocê não tem Células de Combustível (compre no balcão §6Máquinas§c).");
            return;
        }
        // se estava parada sem combustível, a produção recomeça agora
        if (m.combustivel + 1e-9 < tipo(m).combustivelPorProduto()) m.ultima = System.currentTimeMillis();
        m.combustivel += colocadas;
        sujo = true;
        p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 0.7f, 1.2f);
        p.sendMessage("§aAbasteceu com §f" + colocadas + " §aCélula(s). Tanque: §f" + fmt(m.combustivel) + "/" + tanque());
    }

    private void recolher(Player p, Maquina m) {
        if (m.estoque > 0) coletar(p, m);
        int celulas = (int) Math.floor(m.combustivel + 1e-9);
        if (celulas > 0) entregar(p, plugin.combustivel(1), celulas);
        entregar(p, plugin.maquina(tipo(m), m.nivelVel, m.nivelEst), 1);
        porLugar.values().remove(m);
        for (UUID u : new UUID[]{m.display, m.holograma}) {
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        Location b = m.bloco();
        if (b != null && b.getBlock().getType() == Material.BARRIER) b.getBlock().setType(Material.AIR);
        sujo = true;
        salvar();
        p.sendMessage("§eMáquina recolhida.");
    }

    private static void entregar(Player p, ItemStack modelo, int quantidade) {
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

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    // ---------------------------------------------------------------------
    // maquinas.yml
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("maquinas");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            try {
                Maquina m = new Maquina(id, s.getString("mundo"), s.getInt("x"), s.getInt("y"), s.getInt("z"), s.getString("tipo"),
                        UUID.fromString(s.getString("dono")), (float) s.getDouble("yaw"));
                m.nivelVel = s.getInt("nivel-velocidade");
                m.nivelEst = s.getInt("nivel-estoque");
                m.combustivel = s.getDouble("combustivel");
                m.estoque = s.getInt("estoque");
                m.parcialMs = s.getLong("parcial-ms");
                m.ultima = s.getLong("ultima", System.currentTimeMillis());
                String d = s.getString("display"), h = s.getString("holograma");
                if (d != null) m.display = UUID.fromString(d);
                if (h != null) m.holograma = UUID.fromString(h);
                porLugar.put(m.mundo + ";" + m.x + ";" + m.y + ";" + m.z, m);
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("Máquina inválida ignorada em maquinas.yml: " + id);
            }
        }
    }

    void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Maquina m : porLugar.values()) {
            String b = "maquinas." + m.id;
            yaml.set(b + ".mundo", m.mundo);
            yaml.set(b + ".x", m.x);
            yaml.set(b + ".y", m.y);
            yaml.set(b + ".z", m.z);
            yaml.set(b + ".tipo", m.tipo);
            yaml.set(b + ".dono", m.dono.toString());
            yaml.set(b + ".yaw", (double) m.yaw);
            yaml.set(b + ".nivel-velocidade", m.nivelVel);
            yaml.set(b + ".nivel-estoque", m.nivelEst);
            yaml.set(b + ".combustivel", m.combustivel);
            yaml.set(b + ".estoque", m.estoque);
            yaml.set(b + ".parcial-ms", m.parcialMs);
            yaml.set(b + ".ultima", m.ultima);
            if (m.display != null) yaml.set(b + ".display", m.display.toString());
            if (m.holograma != null) yaml.set(b + ".holograma", m.holograma.toString());
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar maquinas.yml", e);
        }
    }
}
