package com.example.shop.command;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public class SellCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;

    public SellCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem vender na loja.");
            return true;
        }

        if (plugin.getEconomy() == null) {
            jogador.sendMessage("§cA economia (Vault) não está disponível no servidor.");
            return true;
        }

        boolean venderTudo = args.length > 0 && args[0].equalsIgnoreCase("all");

        if (venderTudo) {
            venderTudoDoInventario(jogador);
        } else {
            venderItemNaMao(jogador);
        }

        return true;
    }

    private void venderItemNaMao(Player jogador) {
        ItemStack itemNaMao = jogador.getInventory().getItemInMainHand();
        if (itemNaMao.getType().isAir()) {
            jogador.sendMessage("§cSegure o item que quer vender na mão (ou use /sell all pra vender tudo que a loja aceita).");
            return;
        }

        ShopItem item = plugin.getShopManager().getItem(itemNaMao.getType());
        if (item == null) {
            jogador.sendMessage("§cA loja não compra esse item.");
            return;
        }

        int quantidade = itemNaMao.getAmount();
        double totalBruto = venderUnidades(item, quantidade);
        itemNaMao.setAmount(0);

        creditarEAvisar(jogador, item.getMaterial(), quantidade, totalBruto);
    }

    private void venderTudoDoInventario(Player jogador) {
        double totalBrutoGeral = 0;
        int itensGerais = 0;

        for (ItemStack stack : jogador.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;

            ShopItem item = plugin.getShopManager().getItem(stack.getType());
            if (item == null) continue;

            int quantidade = stack.getAmount();
            double total = venderUnidades(item, quantidade);

            totalBrutoGeral += total;
            itensGerais += quantidade;
            stack.setAmount(0);
        }

        if (itensGerais == 0) {
            jogador.sendMessage("§cVocê não tem nenhum item que a loja compra.");
            return;
        }

        double imposto = plugin.getConfig().getDouble("imposto.percentual-venda", 2.0);
        double totalLiquido = totalBrutoGeral * (1 - imposto / 100.0);

        plugin.getEconomy().depositPlayer(jogador, totalLiquido);
        plugin.getShopManager().salvar();
        plugin.getPixIntegration().registrarTransacao(jogador, totalLiquido, true, "Venda na loja (lote)");

        jogador.sendMessage("§aVocê vendeu §f" + itensGerais + " itens §apor um total de §f" + formatar(totalLiquido)
                + " §7(imposto: " + formatar(totalBrutoGeral - totalLiquido) + ")");
    }

    /** Vende N unidades de um item, empurrando o preço pra baixo unidade por unidade, e retorna o total bruto. */
    private double venderUnidades(ShopItem item, int quantidade) {
        double total = 0;
        for (int i = 0; i < quantidade; i++) {
            total += item.venderUnidade();
        }
        return total;
    }

    private void creditarEAvisar(Player jogador, Material material, int quantidade, double totalBruto) {
        double imposto = plugin.getConfig().getDouble("imposto.percentual-venda", 2.0);
        double totalLiquido = totalBruto * (1 - imposto / 100.0);

        plugin.getEconomy().depositPlayer(jogador, totalLiquido);
        plugin.getShopManager().salvar();
        plugin.getPixIntegration().registrarTransacao(jogador, totalLiquido, true, "Venda na loja");

        jogador.sendMessage("§aVocê vendeu §f" + quantidade + "x " + nomeAmigavel(material)
                + " §apor §f" + formatar(totalLiquido) + " §7(imposto: " + formatar(totalBruto - totalLiquido) + ")");
    }

    private String nomeAmigavel(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("all");
        }
        return List.of();
    }
}
