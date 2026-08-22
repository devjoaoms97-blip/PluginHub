package com.example.shop.command;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BuyCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;

    public BuyCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem comprar na loja.");
            return true;
        }

        if (plugin.getEconomy() == null) {
            jogador.sendMessage("§cA economia (Vault) não está disponível no servidor.");
            return true;
        }

        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/buy <item> <quantidade>");
            return true;
        }

        Material material;
        try {
            material = Material.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            jogador.sendMessage("§cItem \"" + args[0] + "\" não reconhecido.");
            return true;
        }

        ShopItem item = plugin.getShopManager().getItem(material);
        if (item == null) {
            jogador.sendMessage("§cEsse item não está à venda na loja.");
            return true;
        }

        int quantidade;
        try {
            quantidade = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            jogador.sendMessage("§cQuantidade inválida: " + args[1]);
            return true;
        }

        int limite = plugin.getConfig().getInt("limite-por-transacao", 6400);
        if (quantidade <= 0 || quantidade > limite) {
            jogador.sendMessage("§cQuantidade deve ser entre 1 e " + limite + ".");
            return true;
        }

        double custoTotal = item.getPrecoBase() * quantidade;
        Economy econ = plugin.getEconomy();

        if (!econ.has(jogador, custoTotal)) {
            jogador.sendMessage("§cSaldo insuficiente. Custo total: §f" + formatar(custoTotal)
                    + " §c| Seu saldo: §f" + formatar(econ.getBalance(jogador)));
            return true;
        }

        econ.withdrawPlayer(jogador, custoTotal);

        ItemStack pilha = new ItemStack(material, quantidade);
        Map<Integer, ItemStack> sobras = jogador.getInventory().addItem(pilha);
        sobras.values().forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));

        plugin.getPixIntegration().registrarTransacao(jogador, custoTotal, false, "Compra na loja");

        jogador.sendMessage("§aVocê comprou §f" + quantidade + "x " + nomeAmigavel(material)
                + " §apor §f" + formatar(custoTotal) + "§a.");

        return true;
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
            List<String> opcoes = new ArrayList<>();
            String digitado = args[0].toLowerCase(Locale.ROOT);
            for (Material material : plugin.getShopManager().getTodosItens().keySet()) {
                String nome = material.name().toLowerCase(Locale.ROOT);
                if (nome.startsWith(digitado)) opcoes.add(nome);
            }
            return opcoes;
        }
        if (args.length == 2) {
            return List.of("1", "16", "32", "64");
        }
        return List.of();
    }
}
