package com.example.shop.gui;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShopGUI {

    public static final String TITULO_CATEGORIAS = "§8§lLoja do Servidor";
    private static final int ITENS_POR_PAGINA = 45; // 5 linhas de 9, última linha reservada pra navegação

    private final ShopPlugin plugin;

    public ShopGUI(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Tela de categorias
    // ------------------------------------------------------------------
    public Inventory montarCategorias() {
        ShopGUIHolder holder = new ShopGUIHolder(ShopGUIHolder.Tela.CATEGORIAS, null, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, TITULO_CATEGORIAS);
        holder.setInventory(inv);

        List<String> categorias = plugin.getShopManager().getCategorias();
        int slot = 0;
        for (String categoria : categorias) {
            if (slot >= 27) break;
            inv.setItem(slot++, criarIconeCategoria(categoria));
        }

        return inv;
    }

    private ItemStack criarIconeCategoria(String categoria) {
        List<com.example.shop.model.ShopItem> itensDaCategoria = plugin.getShopManager().getItensDaCategoria(categoria);
        Material icone = plugin.getShopManager().getIconeCategoria(categoria);
        if (icone == null) icone = itensDaCategoria.isEmpty() ? Material.CHEST : itensDaCategoria.get(0).getMaterial();

        ItemStack item = new ItemStack(icone);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l" + categoria);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + itensDaCategoria.size() + " item(ns) nessa categoria");
        var evento = plugin.getEventoMercado();
        if (evento.ativo() && itensDaCategoria.stream().anyMatch(i -> i.getMaterial() == evento.getMaterial())) {
            lore.add("§6📈 Em alta: " + evento.nome(evento.getMaterial()));
        }
        lore.add("");
        lore.add("§7Clique pra abrir");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    // Tela de itens de uma categoria (paginada)
    // ------------------------------------------------------------------
    public Inventory montarItens(String categoria, int pagina, Player jogador) {
        return montarItens(categoria, pagina, jogador, false);
    }

    /** {@code soCategoria}: aberto por um vendedor (sem o botão de voltar pras categorias). */
    public Inventory montarItens(String categoria, int pagina, Player jogador, boolean soCategoria) {
        List<ShopItem> todos = plugin.getShopManager().getItensDaCategoria(categoria);
        int totalPaginas = Math.max(1, (int) Math.ceil(todos.size() / (double) ITENS_POR_PAGINA));
        pagina = Math.max(0, Math.min(pagina, totalPaginas - 1));

        ShopGUIHolder holder = new ShopGUIHolder(ShopGUIHolder.Tela.ITENS, categoria, pagina, soCategoria);
        Inventory inv = Bukkit.createInventory(holder, 54, tituloItens(categoria, pagina));
        holder.setInventory(inv);

        int inicio = pagina * ITENS_POR_PAGINA;
        int fim = Math.min(inicio + ITENS_POR_PAGINA, todos.size());

        for (int i = inicio; i < fim; i++) {
            inv.setItem(i - inicio, criarItemVisual(todos.get(i), jogador));
        }

        if (!soCategoria) inv.setItem(45, criarBotaoNavegacao("§a◀ Categorias", Material.CHEST));
        if (pagina > 0) {
            inv.setItem(46, criarBotaoNavegacao("§ePágina anterior", Material.ARROW));
        }
        if (pagina < totalPaginas - 1) {
            inv.setItem(52, criarBotaoNavegacao("§ePróxima página ▶", Material.ARROW));
        }
        inv.setItem(49, criarBotaoFechar());

        return inv;
    }

    private String tituloItens(String categoria, int pagina) {
        return "§8§l" + categoria + (pagina > 0 ? " §7(" + (pagina + 1) + ")" : "");
    }

    private ItemStack criarItemVisual(ShopItem item, Player jogador) {
        ItemStack visual = new ItemStack(item.getMaterial());
        ItemMeta meta = visual.getItemMeta();

        meta.setDisplayName("§e" + nomeAmigavel(item.getMaterial()));

        var precos = plugin.getPrecificacao();
        int desconto = jogador == null ? 0 : plugin.calcularDescontoVip(jogador);

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (item.isCompravel()) {
            double precoCompra = jogador == null ? item.getPrecoBase() : precos.precoCompra(jogador, item);
            lore.add("§aComprar: §f" + formatar(precoCompra) + (desconto > 0 ? " §7(com " + desconto + "% de VIP)" : " §7(fixo)"));
        } else {
            lore.add("§8A loja não vende esse item");
        }
        if (item.isVendivel()) {
            double unidade = jogador == null ? item.getPrecoVendaComEvento() : precos.precoVendaUnitario(jogador, item, true);
            String alta = item.getMultiplicadorEvento() > 1 ? " §6📈 em alta" : " §7(varia)";
            lore.add("§cVender: §f" + formatar(unidade) + alta + (jogador != null ? precos.textoBonus(jogador) : ""));
        } else if (item.isVendivelNaMina()) {
            lore.add("§8Só vende na mina (venda automática)");
        } else {
            lore.add("§8A loja não compra esse item");
        }
        lore.add("");
        if (item.isVendivel()) {
            lore.add("§7Clique esquerdo: §fvender 1");
            lore.add("§7Shift + esquerdo: §fvender tudo que você tem");
        }
        if (item.isCompravel()) {
            lore.add("§7Clique direito: §fcomprar 1");
            lore.add("§7Shift + direito: §fcomprar " + item.getMaterial().getMaxStackSize());
        }

        meta.setLore(lore);
        visual.setItemMeta(meta);
        return visual;
    }

    private ItemStack criarBotaoNavegacao(String nome, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack criarBotaoFechar() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cFechar");
        item.setItemMeta(meta);
        return item;
    }

    private String nomeAmigavel(Material material) {
        String nome = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
    }

    private String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
