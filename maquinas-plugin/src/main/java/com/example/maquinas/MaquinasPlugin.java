package com.example.maquinas;

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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Máquinas: blocos com textura própria (resource pack do PacotePlugin) colocados nos terrenos, que com
 * Células de Combustível fabricam produtos que não existem no vanilla. Os produtos vendem no balcão
 * Máquinas da Loja Oficial ({@code /maquinas loja}), com preço dinâmico. Veja o {@link MaquinaManager}
 * e o {@link Mercado}.
 */
public class MaquinasPlugin extends JavaPlugin implements Listener {

    /** Um produto: o que a máquina fabrica e a loja compra. */
    public record Produto(String id, String nome, double ancora, double minimo, double passo) {}

    /** Um tipo de máquina: o produto, quanto tempo leva cada um, quanto combustível gasta, preço e rank. */
    public record Tipo(String id, String nome, String produto, double intervaloSegundos, double combustivelPorProduto,
                       double preco, String rank) {}

    private Economy economy;
    private final Map<String, Produto> produtos = new LinkedHashMap<>();
    private final Map<String, Tipo> tipos = new LinkedHashMap<>();
    private NamespacedKey chaveProduto, chaveMaquina, chaveCombustivel, chaveNivelVel, chaveNivelEst;
    private MaquinaManager maquinas;
    private Mercado mercado;
    private LojaMaquinas loja;
    private PixIntegration pix;
    private RankupIntegration rankup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        chaveProduto = new NamespacedKey(this, "produto");
        chaveMaquina = new NamespacedKey(this, "maquina");
        chaveCombustivel = new NamespacedKey(this, "combustivel");
        chaveNivelVel = new NamespacedKey(this, "nivel_velocidade");
        chaveNivelEst = new NamespacedKey(this, "nivel_estoque");
        carregarConfig();
        pix = new PixIntegration(this);
        rankup = new RankupIntegration(this);
        mercado = new Mercado(this);
        maquinas = new MaquinaManager(this);
        loja = new LojaMaquinas(this);
        getServer().getPluginManager().registerEvents(maquinas, this);
        getServer().getPluginManager().registerEvents(loja, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, maquinas::tick, 100L, 400L);
        long regen = Math.max(1, getConfig().getLong("regeneracao.intervalo-segundos", 60)) * 20L;
        getServer().getScheduler().runTaskTimer(this, mercado::regenerar, regen, regen);
        getLogger().info("MaquinasPlugin ativado! " + tipos.size() + " máquina(s), " + produtos.size() + " produto(s), "
                + maquinas.quantas() + " colocada(s).");
    }

    @Override
    public void onDisable() {
        if (maquinas != null) maquinas.salvar();
        if (mercado != null) mercado.salvar();
    }

    private void carregarConfig() {
        produtos.clear();
        tipos.clear();
        ConfigurationSection ps = getConfig().getConfigurationSection("produtos");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(id);
                produtos.put(id, new Produto(id, cores(s.getString("nome", id)), s.getDouble("ancora"), s.getDouble("minimo"),
                        s.getDouble("passo", 0.2)));
            }
        }
        ConfigurationSection ms = getConfig().getConfigurationSection("maquinas");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (!produtos.containsKey(s.getString("produto"))) {
                    getLogger().warning("Máquina " + id + " com produto que não existe: " + s.getString("produto"));
                    continue;
                }
                tipos.put(id, new Tipo(id, cores(s.getString("nome", id)), s.getString("produto"), s.getDouble("intervalo-segundos", 60),
                        s.getDouble("combustivel-por-produto", 1), s.getDouble("preco"), s.getString("rank")));
            }
        }
    }

    // ---------------------------------------------------------------------
    // Itens (com o modelo do resource pack: "servidor:<id>")
    // ---------------------------------------------------------------------

    private ItemStack itemComModelo(Material base, String modelo, String nome, List<String> lore, int quantidade) {
        ItemStack item = new ItemStack(base, Math.max(1, quantidade));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("servidor", modelo));
        meta.setDisplayName(nome);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack produto(Produto p, int quantidade) {
        ItemStack item = itemComModelo(Material.PAPER, p.id(), p.nome(), List.of("§7Produto de máquina.", "§7Vende no balcão §6Máquinas§7 da Loja Oficial."),
                quantidade);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(chaveProduto, PersistentDataType.STRING, p.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack combustivel(int quantidade) {
        ItemStack item = itemComModelo(Material.PAPER, "celula_combustivel", cores(getConfig().getString("combustivel.nome", "&6Célula de Combustível")),
                List.of("§7Abastece as máquinas.", "§7Clique na máquina e use §fAbastecer§7."), quantidade);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(chaveCombustivel, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** O item da máquina (bloco de barreira com o modelo dela): os níveis vão junto quando recolhe. */
    public ItemStack maquina(Tipo t, int nivelVel, int nivelEst) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Fabrica: §f" + produtos.get(t.produto()).nome());
        lore.add("§7Coloque no seu terreno e abasteça com Células de Combustível.");
        if (nivelVel > 0 || nivelEst > 0) lore.add("§7Velocidade nível §f" + nivelVel + " §7| Estoque nível §f" + nivelEst);
        ItemStack item = itemComModelo(Material.BARRIER, "maquina_" + t.id(), "§e⚙ " + t.nome(), lore, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(chaveMaquina, PersistentDataType.STRING, t.id());
        meta.getPersistentDataContainer().set(chaveNivelVel, PersistentDataType.INTEGER, nivelVel);
        meta.getPersistentDataContainer().set(chaveNivelEst, PersistentDataType.INTEGER, nivelEst);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    public Produto produtoDoItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(chaveProduto, PersistentDataType.STRING);
        return id == null ? null : produtos.get(id);
    }

    public boolean ehCombustivel(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(chaveCombustivel);
    }

    public Tipo tipoDoItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(chaveMaquina, PersistentDataType.STRING);
        return id == null ? null : tipos.get(id);
    }

    int nivel(ItemStack item, boolean velocidade) {
        Integer n = item.getItemMeta().getPersistentDataContainer().get(velocidade ? chaveNivelVel : chaveNivelEst, PersistentDataType.INTEGER);
        return n == null ? 0 : n;
    }

    /** Item nosso (produto, célula ou máquina) não entra em receita nenhuma. */
    @EventHandler
    public void aoPrepararCraft(PrepareItemCraftEvent evento) {
        for (ItemStack i : evento.getInventory().getMatrix()) {
            if (i != null && i.hasItemMeta() && (produtoDoItem(i) != null || ehCombustivel(i) || tipoDoItem(i) != null)) {
                evento.getInventory().setResult(null);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Bônus de VIP e limite de máquinas
    // ---------------------------------------------------------------------

    double bonusVendaVip(Player p) {
        return maiorDaLista("vip-bonus-venda", "percentual", p, 0);
    }

    int limiteDeMaquinas(Player p) {
        return (int) maiorDaLista("limite-vip", "limite", p, getConfig().getInt("limite-por-jogador", 4));
    }

    private double maiorDaLista(String caminho, String campo, Player p, double base) {
        double maior = base;
        for (Map<?, ?> m : getConfig().getMapList(caminho)) {
            if (m.get("permissao") instanceof String perm && m.get(campo) instanceof Number n && p.hasPermission(perm)) {
                maior = Math.max(maior, n.doubleValue());
            }
        }
        return maior;
    }

    // ---------------------------------------------------------------------
    // /maquinas
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "loja" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("loja")) {
            if (sender instanceof Player p) loja.abrir(p);
            return true;
        }
        if (!sender.hasPermission("maquinas.admin")) {
            sender.sendMessage("§cUso: §f/maquinas loja §7(ou o vendedor no balcão Máquinas da Loja Oficial)");
            return true;
        }
        switch (sub) {
            case "dar" -> {
                Player alvo = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (alvo == null) {
                    sender.sendMessage("§cUso: §f/maquinas dar <jogador> <maquina|combustivel|produto> [id] [qtd]");
                    return true;
                }
                ItemStack item = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "combustivel" -> combustivel(args.length > 3 ? numero(args[3], 16) : 16);
                    case "produto" -> args.length > 3 && produtos.containsKey(args[3])
                            ? produto(produtos.get(args[3]), args.length > 4 ? numero(args[4], 1) : 1) : null;
                    default -> tipos.containsKey(args[2]) ? maquina(tipos.get(args[2]), 0, 0) : null;
                };
                if (item == null) {
                    sender.sendMessage("§cMáquinas: " + String.join(", ", tipos.keySet()) + " §c| produtos: " + String.join(", ", produtos.keySet()));
                    return true;
                }
                alvo.getInventory().addItem(item).values().forEach(s -> alvo.getWorld().dropItemNaturally(alvo.getLocation(), s));
                sender.sendMessage("§aEntregue pra §f" + alvo.getName() + "§a.");
            }
            case "acelerar" -> {
                // teste da staff: faz o relógio de todas as máquinas andar N minutos
                int minutos = args.length > 1 ? numero(args[1], 60) : 60;
                maquinas.acelerar(minutos);
                sender.sendMessage("§aAs máquinas andaram §f" + minutos + " §aminutos.");
            }
            case "lista" -> {
                sender.sendMessage("§e§l--- Máquinas colocadas: " + maquinas.quantas() + " ---");
                maquinas.resumo().forEach(sender::sendMessage);
            }
            case "reload" -> {
                reloadConfig();
                carregarConfig();
                sender.sendMessage("§aConfig recarregado.");
            }
            default -> sender.sendMessage("§f/maquinas loja §7| §f/maquinas dar <jogador> <maquina|combustivel|produto> ... §7| §flista §7| §facelerar <min> §7| §freload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.add("loja");
            if (sender.hasPermission("maquinas.admin")) opcoes.addAll(List.of("dar", "lista", "acelerar", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            opcoes.addAll(tipos.keySet());
            opcoes.add("combustivel");
            opcoes.add("produto");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("dar") && args[2].equalsIgnoreCase("produto")) {
            opcoes.addAll(produtos.keySet());
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }

    private static int numero(String s, int padrao) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    // ---------------------------------------------------------------------

    public Map<String, Produto> getProdutos() {
        return produtos;
    }

    public Map<String, Tipo> getTipos() {
        return tipos;
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

    Mercado getMercado() {
        return mercado;
    }

    MaquinaManager getMaquinas() {
        return maquinas;
    }

    static String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }
}
