package com.example.cheque;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChequeCommand implements CommandExecutor {

    private final ChequePlugin plugin;

    public ChequeCommand(ChequePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Somente jogadores podem usar este comando.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("cheque.criar")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para criar cheques.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Uso correto: /cheque <valor>");
            return true;
        }

        double valor;
        try {
            valor = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Valor inválido. Use um número, ex: /cheque 100.50");
            return true;
        }

        if (valor <= 0) {
            player.sendMessage(ChatColor.RED + "O valor do cheque deve ser maior que zero.");
            return true;
        }

        ItemStack itemNaMao = player.getInventory().getItemInMainHand();

        if (itemNaMao.getType() != Material.PAPER) {
            player.sendMessage(ChatColor.RED + "Você precisa estar segurando papel na mão principal.");
            return true;
        }

        // Consome 1 papel da mão
        if (itemNaMao.getAmount() > 1) {
            itemNaMao.setAmount(itemNaMao.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        ItemStack cheque = criarCheque(player, valor);

        // Entrega o cheque; se o inventário estiver cheio, dropa no chão
        var sobras = player.getInventory().addItem(cheque);
        sobras.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        player.sendMessage(ChatColor.GREEN + "Cheque no valor de " + ChatColor.GOLD + formatarValor(valor)
                + ChatColor.GREEN + " criado com sucesso!");

        return true;
    }

    private ItemStack criarCheque(Player emissor, double valor) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "Cheque" + ChatColor.DARK_GRAY + " - " + ChatColor.GOLD + formatarValor(valor));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Emitido por: " + ChatColor.WHITE + emissor.getName());
        lore.add(ChatColor.GRAY + "Data: " + ChatColor.WHITE + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Clique direito para resgatar");
        meta.setLore(lore);

        // Grava dados reais no item (não confia no texto do lore, que pode ser editado
        // por outros plugins de anvil/renomear). Isso evita duplicação/falsificação.
        meta.getPersistentDataContainer().set(plugin.getChequeValueKey(), PersistentDataType.DOUBLE, valor);
        meta.getPersistentDataContainer().set(plugin.getChequeIdKey(), PersistentDataType.STRING, UUID.randomUUID().toString());

        cheque.setItemMeta(meta);
        return cheque;
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
