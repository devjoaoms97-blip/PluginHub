package com.example.shop.command;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import com.example.shop.util.RankupIntegration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        if (!item.isVendivel()) {
            jogador.sendMessage("§cA loja não compra esse item.");
            return;
        }
        var venda = plugin.getPrecificacao().vender(jogador, item, itemNaMao.getAmount(), true);
        if (venda.quantidade() <= 0) {
            jogador.sendMessage("§cLimite de hoje desse item atingido.");
            return;
        }
        itemNaMao.setAmount(itemNaMao.getAmount() - venda.quantidade());
        avisar(jogador, venda.quantidade(), venda.liquido(), venda.imposto());
    }

    private void venderTudoDoInventario(Player jogador) {
        double liquido = 0;
        double imposto = 0;
        int itensGerais = 0;
        for (ItemStack stack : jogador.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            ShopItem item = plugin.getShopManager().getItem(stack.getType());
            if (item == null || !item.isVendivel()) continue;
            var venda = plugin.getPrecificacao().vender(jogador, item, stack.getAmount(), true);
            stack.setAmount(stack.getAmount() - venda.quantidade());
            liquido += venda.liquido();
            imposto += venda.imposto();
            itensGerais += venda.quantidade();
        }

        if (itensGerais == 0) {
            jogador.sendMessage("§cVocê não tem nenhum item que a loja compra (ou já bateu o limite de hoje).");
            return;
        }
        avisar(jogador, itensGerais, liquido, imposto);
    }

    private void avisar(Player jogador, int quantidade, double liquido, double imposto) {
        plugin.getShopManager().salvar();
        plugin.getPixIntegration().registrarTransacao(jogador, liquido, true, "Venda na loja");
        jogador.sendMessage("§aVocê vendeu §f" + quantidade + " itens §apor um total de §f" + formatar(liquido)
                + " §7(imposto: " + formatar(imposto) + ")" + plugin.getPrecificacao().textoBonus(jogador));
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
