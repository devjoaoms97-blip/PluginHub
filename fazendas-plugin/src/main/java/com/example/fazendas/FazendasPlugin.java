package com.example.fazendas;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fazendas: uma Escritura (item negociável) vira uma construção 7x7 no terreno, com um NPC parado. A
 * fazenda não dá itens: cada safra põe dinheiro no cofre, gastando Ração ou Adubo, e o dono coleta no
 * NPC. Ninguém quebra a construção; ela só sai recolhendo a Escritura (que volta com os níveis). Veja
 * o {@link FazendaManager}, a {@link Construcao} e a {@link Protecao}.
 */
public class FazendasPlugin extends JavaPlugin implements Listener {

    /** Ração ou Adubo. */
    public record Insumo(String id, String nome, double preco) {}

    /** Um tipo de fazenda. */
    public record Tipo(String id, String nome, String npc, Material icone, String insumo, double intervaloSegundos,
                       double insumoPorSafra, double rendaPorSafra, double preco, String rank) {}

    /** As três melhorias, na ordem dos botões. */
    static final int PROD = 0, COFRE = 1, DESPENSA = 2;

    private Economy economy;
    private final Map<String, Insumo> insumos = new LinkedHashMap<>();
    private final Map<String, Tipo> tipos = new LinkedHashMap<>();
    private NamespacedKey chaveEscritura, chaveInsumo, chaveNivelProd, chaveNivelCofre, chaveNivelDespensa, chaveEntidade;
    private FazendaManager fazendas;
    private LojaFazendas loja;
    private PixIntegration pix;
    private RankupIntegration rankup;
    private TerrenosIntegration terrenos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        chaveEscritura = new NamespacedKey(this, "escritura");
        chaveInsumo = new NamespacedKey(this, "insumo");
        chaveNivelProd = new NamespacedKey(this, "nivel_produtividade");
        chaveNivelCofre = new NamespacedKey(this, "nivel_cofre");
        chaveNivelDespensa = new NamespacedKey(this, "nivel_despensa");
        chaveEntidade = new NamespacedKey(this, "fazenda");
        carregarConfig();
        pix = new PixIntegration(this);
        rankup = new RankupIntegration(this);
        terrenos = new TerrenosIntegration(this);
        fazendas = new FazendaManager(this);
        loja = new LojaFazendas(this);
        getServer().getPluginManager().registerEvents(fazendas, this);
        getServer().getPluginManager().registerEvents(new Colocacao(this), this);
        getServer().getPluginManager().registerEvents(new Protecao(this), this);
        getServer().getPluginManager().registerEvents(loja, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, fazendas::tick, 100L, 400L);
        getLogger().info("FazendasPlugin ativado! " + tipos.size() + " tipo(s), " + fazendas.quantas() + " fazenda(s) colocada(s).");
    }

    @Override
    public void onDisable() {
        if (fazendas != null) fazendas.salvar();
    }

    private void carregarConfig() {
        insumos.clear();
        tipos.clear();
        ConfigurationSection is = getConfig().getConfigurationSection("insumos");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ConfigurationSection s = is.getConfigurationSection(id);
                insumos.put(id, new Insumo(id, cores(s.getString("nome", id)), s.getDouble("preco", 25)));
            }
        }
        ConfigurationSection fs = getConfig().getConfigurationSection("fazendas");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(id);
                if (!insumos.containsKey(s.getString("insumo"))) {
                    getLogger().warning("Fazenda " + id + " com insumo que não existe: " + s.getString("insumo"));
                    continue;
                }
                Material icone = Material.matchMaterial(s.getString("icone", "WHEAT"));
                tipos.put(id, new Tipo(id, cores(s.getString("nome", id)), s.getString("npc", "Fazendeiro"),
                        icone == null ? Material.WHEAT : icone, s.getString("insumo"), s.getDouble("intervalo-segundos", 60),
                        s.getDouble("insumo-por-safra", 1), s.getDouble("renda-por-safra"), s.getDouble("preco"), s.getString("rank")));
            }
        }
    }

    // ---------------------------------------------------------------------
    // Itens (papel com o visual de um item vanilla: não planta, não alimenta, não entra em receita)
    // ---------------------------------------------------------------------

    private static ItemStack item(String modelo, String nome, List<String> lore, int quantidade) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, quantidade));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.minecraft(modelo));
        meta.setDisplayName(nome);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack insumo(Insumo i, int quantidade) {
        boolean racao = i.id().equals("racao");
        ItemStack item = item(racao ? "wheat" : "bone_meal", i.nome(),
                List.of(racao ? "§7Alimenta as fazendas de animais." : "§7Aduba as fazendas de plantação.",
                        "§7Clique no fazendeiro com ele na mão", "§7(ou use §fAbastecer§7 no menu)."), quantidade);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(chaveInsumo, PersistentDataType.STRING, i.id());
        item.setItemMeta(meta);
        return item;
    }

    /** A Escritura: o tipo e os níveis das melhorias vão no item, por isso dá pra negociar. */
    public ItemStack escritura(Tipo t, int nivelProd, int nivelCofre, int nivelDespensa) {
        Insumo ins = insumos.get(t.insumo());
        List<String> lore = new ArrayList<>();
        lore.add("§7Rende §a" + dinheiro(t.rendaPorSafra()) + " §7a cada §f" + fmt(t.intervaloSegundos()) + "s §7(cofre)");
        lore.add("§7Gasta §f" + fmt(t.insumoPorSafra()) + " " + ChatColor.stripColor(ins.nome()) + " §7por safra");
        lore.add("§7Rank: §f" + t.rank());
        if (nivelProd > 0 || nivelCofre > 0 || nivelDespensa > 0) {
            lore.add("§7Produtividade §f" + nivelProd + " §7| Cofre §f" + nivelCofre + " §7| Despensa §f" + nivelDespensa);
        }
        lore.add("");
        lore.add("§eClique direito no chão do seu terreno:");
        lore.add("§e1º mostra a área (7x7), 2º constrói.");
        ItemStack item = item("filled_map", "§6📜 Escritura: " + t.nome(), lore, 1);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(chaveEscritura, PersistentDataType.STRING, t.id());
        pdc.set(chaveNivelProd, PersistentDataType.INTEGER, nivelProd);
        pdc.set(chaveNivelCofre, PersistentDataType.INTEGER, nivelCofre);
        pdc.set(chaveNivelDespensa, PersistentDataType.INTEGER, nivelDespensa);
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    public Tipo tipoDaEscritura(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(chaveEscritura, PersistentDataType.STRING);
        return id == null ? null : tipos.get(id);
    }

    public Insumo insumoDoItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(chaveInsumo, PersistentDataType.STRING);
        return id == null ? null : insumos.get(id);
    }

    /** O nível que a escritura guarda: PROD, COFRE ou DESPENSA. */
    int nivel(ItemStack item, int qual) {
        NamespacedKey chave = qual == PROD ? chaveNivelProd : qual == COFRE ? chaveNivelCofre : chaveNivelDespensa;
        Integer n = item.getItemMeta().getPersistentDataContainer().get(chave, PersistentDataType.INTEGER);
        return n == null ? 0 : n;
    }

    /** Item nosso não entra em receita nenhuma. */
    @EventHandler
    public void aoPrepararCraft(PrepareItemCraftEvent evento) {
        for (ItemStack i : evento.getInventory().getMatrix()) {
            if (i != null && i.hasItemMeta() && (tipoDaEscritura(i) != null || insumoDoItem(i) != null)) {
                evento.getInventory().setResult(null);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Bônus e limites
    // ---------------------------------------------------------------------

    /** Rank × VIP: o que multiplica a coleta desse jogador. */
    double multiplicadorColeta(Player p) {
        double vip = 0;
        for (Map<?, ?> m : getConfig().getMapList("vip-bonus-coleta")) {
            if (m.get("permissao") instanceof String perm && m.get("percentual") instanceof Number n && p.hasPermission(perm)) {
                vip = Math.max(vip, n.doubleValue());
            }
        }
        return rankup.multiplicadorVenda(p.getUniqueId()) * (1 + vip / 100.0);
    }

    int limitePorTerreno() {
        return Math.max(1, getConfig().getInt("limite-por-terreno", 4));
    }

    // ---------------------------------------------------------------------
    // /fazendas
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "loja" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("loja")) {
            if (sender instanceof Player p) loja.abrir(p);
            return true;
        }
        if (!sender.hasPermission("fazendas.admin")) {
            sender.sendMessage("§cUso: §f/fazendas loja");
            return true;
        }
        switch (sub) {
            case "dar" -> {
                Player alvo = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (alvo == null) {
                    sender.sendMessage("§cUso: §f/fazendas dar <jogador> <tipo|racao|adubo> [qtd]");
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                ItemStack item = insumos.containsKey(id) ? insumo(insumos.get(id), args.length > 3 ? numero(args[3], 16) : 16)
                        : tipos.containsKey(id) ? escritura(tipos.get(id), 0, 0, 0) : null;
                if (item == null) {
                    sender.sendMessage("§cTipos: " + String.join(", ", tipos.keySet()) + " §c| insumos: " + String.join(", ", insumos.keySet()));
                    return true;
                }
                alvo.getInventory().addItem(item).values().forEach(s -> alvo.getWorld().dropItemNaturally(alvo.getLocation(), s));
                sender.sendMessage("§aEntregue pra §f" + alvo.getName() + "§a.");
            }
            case "acelerar" -> {
                int minutos = args.length > 1 ? numero(args[1], 60) : 60;
                fazendas.acelerar(minutos);
                sender.sendMessage("§aAs fazendas andaram §f" + minutos + " §aminutos.");
            }
            case "lista" -> {
                sender.sendMessage("§e§l--- Fazendas colocadas: " + fazendas.quantas() + " ---");
                fazendas.resumo().forEach(sender::sendMessage);
            }
            case "remover" -> {
                // a única forma de tirar uma fazenda sem o dono: a staff, olhando pra ela (a escritura vai pra staff)
                if (!(sender instanceof Player p)) return true;
                if (!fazendas.removerPelaStaff(p)) sender.sendMessage("§cOlhe pra uma fazenda (a até 8 blocos).");
            }
            case "reload" -> {
                reloadConfig();
                carregarConfig();
                sender.sendMessage("§aConfig recarregado.");
            }
            default -> sender.sendMessage("§f/fazendas loja §7| §fdar <jogador> <tipo|racao|adubo> [qtd] §7| §flista §7| §facelerar <min> §7| §fremover §7| §freload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.add("loja");
            if (sender.hasPermission("fazendas.admin")) opcoes.addAll(List.of("dar", "lista", "acelerar", "remover", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            opcoes.addAll(tipos.keySet());
            opcoes.addAll(insumos.keySet());
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }

    private static int numero(String s, int padrao) {
        try {
            return Math.max(1, Math.min(64 * 36, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    // ---------------------------------------------------------------------

    public Map<String, Tipo> getTipos() {
        return tipos;
    }

    public Map<String, Insumo> getInsumos() {
        return insumos;
    }

    /** API (pra o Leiloeiro do TerrenosPlugin, por reflexão, como as máquinas): fazendas no terreno "x;z". */
    public int fazendasNoTerreno(String terreno) {
        return fazendas == null ? 0 : fazendas.noTerreno(terreno);
    }

    NamespacedKey chaveEntidade() {
        return chaveEntidade;
    }

    public Economy getEconomy() {
        return economy;
    }

    PixIntegration getPix() {
        return pix;
    }

    RankupIntegration getRankup() {
        return rankup;
    }

    TerrenosIntegration getTerrenos() {
        return terrenos;
    }

    FazendaManager getFazendas() {
        return fazendas;
    }

    static String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }

    static String fmt(double v) {
        return new java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }
}
