package com.example.shop.gui;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public class ShopGUIListener implements Listener {

    private final ShopPlugin plugin;

    public ShopGUIListener(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGUIHolder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player jogador)) return;
        ItemStack clicado = event.getCurrentItem();
        if (clicado == null || clicado.getType().isAir()) return;

        if (holder.getTela() == ShopGUIHolder.Tela.CATEGORIAS) {
            String categoria = ChatColor.stripColor(clicado.getItemMeta().getDisplayName());
            jogador.openInventory(plugin.getShopGUI().montarItens(categoria, 0));
            return;
        }

        // Tela de itens
        if (clicado.getType() == Material.BARRIER) {
            jogador.closeInventory();
            return;
        }
        if (clicado.getType() == Material.CHEST && event.getSlot() == 45) {
            jogador.openInventory(plugin.getShopGUI().montarCategorias());
            return;
        }
        if (clicado.getType() == Material.ARROW && event.getSlot() == 46) {
            jogador.openInventory(plugin.getShopGUI().montarItens(holder.getCategoria(), holder.getPagina() - 1));
            return;
        }
        if (clicado.getType() == Material.ARROW && event.getSlot() == 52) {
            jogador.openInventory(plugin.getShopGUI().montarItens(holder.getCategoria(), holder.getPagina() + 1));
            return;
        }

        ShopItem item = plugin.getShopManager().getItem(clicado.getType());
        if (item == null) return;

        ClickType tipo = event.getClick();
        if (tipo == ClickType.LEFT) {
            venderQuantidade(jogador, item, 1);
        } else if (tipo == ClickType.SHIFT_LEFT) {
            venderTudoDoTipo(jogador, item);
        } else if (tipo == ClickType.RIGHT) {
            comprarQuantidade(jogador, item, 1);
        } else if (tipo == ClickType.SHIFT_RIGHT) {
            comprarQuantidade(jogador, item, 64);
        }

        // Reabre a GUI atualizada com o preço já ajustado
        jogador.openInventory(plugin.getShopGUI().montarItens(holder.getCategoria(), holder.getPagina()));
    }

    private void venderQuantidade(Player jogador, ShopItem item, int quantidade) {
        int disponivel = contarNoInventario(jogador, item.getMaterial());
        int aVender = Math.min(quantidade, disponivel);
        if (aVender <= 0) {
            jogador.sendMessage("§cVocê não tem esse item pra vender.");
            return;
        }

        double totalBruto = 0;
        for (int i = 0; i < aVender; i++) {
            totalBruto += item.venderUnidade();
        }

        removerDoInventario(jogador, item.getMaterial(), aVender);

        double imposto = plugin.getConfig().getDouble("imposto.percentual-venda", 2.0);
        double totalLiquido = totalBruto * (1 - imposto / 100.0);

        plugin.getEconomy().depositPlayer(jogador, totalLiquido);
        plugin.getShopManager().salvar();
        plugin.getPixIntegration().registrarTransacao(jogador, totalLiquido, true, "Venda na loja");

        jogador.sendMessage("§aVendeu §f" + aVender + "x " + item.getMaterial().name()
                + " §apor §f" + formatar(totalLiquido) + " §7(imposto: " + formatar(totalBruto - totalLiquido) + ")");
    }

    private void venderTudoDoTipo(Player jogador, ShopItem item) {
        int disponivel = contarNoInventario(jogador, item.getMaterial());
        if (disponivel <= 0) {
            jogador.sendMessage("§cVocê não tem esse item pra vender.");
            return;
        }
        venderQuantidade(jogador, item, disponivel);
    }

    private void comprarQuantidade(Player jogador, ShopItem item, int quantidade) {
        Economy econ = plugin.getEconomy();
        double custoTotal = item.getPrecoBase() * quantidade;

        if (!econ.has(jogador, custoTotal)) {
            jogador.sendMessage("§cSaldo insuficiente. Custo: §f" + formatar(custoTotal));
            return;
        }

        econ.withdrawPlayer(jogador, custoTotal);

        ItemStack pilha = new ItemStack(item.getMaterial(), quantidade);
        Map<Integer, ItemStack> sobras = jogador.getInventory().addItem(pilha);
        sobras.values().forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));

        plugin.getPixIntegration().registrarTransacao(jogador, custoTotal, false, "Compra na loja");

        jogador.sendMessage("§aComprou §f" + quantidade + "x " + item.getMaterial().name()
                + " §apor §f" + formatar(custoTotal) + "§a.");
    }

    private int contarNoInventario(Player jogador, Material material) {
        int total = 0;
        for (ItemStack stack : jogador.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removerDoInventario(Player jogador, Material material, int quantidade) {
        int restante = quantidade;
        ItemStack[] conteudo = jogador.getInventory().getContents();
        for (int i = 0; i < conteudo.length && restante > 0; i++) {
            ItemStack stack = conteudo[i];
            if (stack == null || stack.getType() != material) continue;

            int remover = Math.min(restante, stack.getAmount());
            stack.setAmount(stack.getAmount() - remover);
            restante -= remover;
        }
    }

    private String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
