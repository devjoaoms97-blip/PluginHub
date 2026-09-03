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
        Material icone = itensDaCategoria.isEmpty() ? Material.CHEST : itensDaCategoria.get(0).getMaterial();

        ItemStack item = new ItemStack(icone);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l" + categoria);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + itensDaCategoria.size() + " item(ns) nessa categoria");
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
        List<ShopItem> todos = plugin.getShopManager().getItensDaCategoria(categoria);
        int totalPaginas = Math.max(1, (int) Math.ceil(todos.size() / (double) ITENS_POR_PAGINA));
        pagina = Math.max(0, Math.min(pagina, totalPaginas - 1));

        ShopGUIHolder holder = new ShopGUIHolder(ShopGUIHolder.Tela.ITENS, categoria, pagina);
        Inventory inv = Bukkit.createInventory(holder, 54, tituloItens(categoria, pagina));
        holder.setInventory(inv);

        int inicio = pagina * ITENS_POR_PAGINA;
        int fim = Math.min(inicio + ITENS_POR_PAGINA, todos.size());

        for (int i = inicio; i < fim; i++) {
            inv.setItem(i - inicio, criarItemVisual(todos.get(i), jogador));
        }

        inv.setItem(45, criarBotaoNavegacao("§a◀ Categorias", Material.CHEST));
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
        return TITULO_CATEGORIAS + " §7- " + categoria;
    }

    private ItemStack criarItemVisual(ShopItem item, Player jogador) {
        ItemStack visual = new ItemStack(item.getMaterial());
        ItemMeta meta = visual.getItemMeta();

        meta.setDisplayName("§e" + nomeAmigavel(item.getMaterial()));

        int desconto = jogador == null ? 0 : plugin.calcularDescontoVip(jogador);
        double precoCompra = item.getPrecoBase();
        if (desconto > 0) {
            precoCompra = precoCompra * (1 - desconto / 100.0);
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§aComprar: §f" + formatar(precoCompra) + (desconto > 0 ? " §7(com " + desconto + "% de VIP)" : " §7(fixo)"));
        lore.add("§cVender: §f" + formatar(item.getPrecoVendaAtual()) + " §7(varia)");
        lore.add("§7  min " + formatar(item.getPrecoMinimo()) + " / max " + formatar(item.getPrecoMaximo()));
        lore.add("");
        lore.add("§7Clique esquerdo: §fvender 1");
        lore.add("§7Shift + esquerdo: §fvender tudo que você tem");
        lore.add("§7Clique direito: §fcomprar 1");
        lore.add("§7Shift + direito: §fcomprar 64");
        lore.add("§7/shop historico " + item.getMaterial().name().toLowerCase(Locale.ROOT) + " §7pra ver tendência");

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
