package com.example.cheque;

import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
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
    // última emissão de cada jogador, pro cooldown
    private final java.util.Map<UUID, Long> ultimaEmissao = new java.util.HashMap<>();

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

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(ChatColor.RED + "Uso correto: /cheque <valor> [jogador]  (com jogador, só ele resgata)");
            return true;
        }

        double valor;
        try {
            valor = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Valor inválido. Use um número, ex: /cheque 100.50");
            return true;
        }

        if (!Double.isFinite(valor) || valor <= 0) { // parseDouble aceita NaN/Infinity, e NaN passa por qualquer comparação com zero
            player.sendMessage(ChatColor.RED + "O valor do cheque deve ser maior que zero.");
            return true;
        }

        // limites: valor máximo por cheque e intervalo entre emissões (cheque.limite.ignorar pula os dois)
        boolean semLimite = player.hasPermission("cheque.limite.ignorar");
        double maximo = plugin.getConfig().getDouble("limite.valor-maximo", 0);
        if (!semLimite && maximo > 0 && valor > maximo) {
            player.sendMessage(ChatColor.RED + "O valor máximo de um cheque é " + formatarValor(maximo) + ".");
            return true;
        }
        long cooldownMs = plugin.getConfig().getLong("limite.cooldown-segundos", 0) * 1000L;
        Long ultima = ultimaEmissao.get(player.getUniqueId());
        if (!semLimite && cooldownMs > 0 && ultima != null && System.currentTimeMillis() - ultima < cooldownMs) {
            long falta = (cooldownMs - (System.currentTimeMillis() - ultima) + 999) / 1000;
            player.sendMessage(ChatColor.RED + "Espere " + falta + "s pra emitir outro cheque.");
            return true;
        }

        OfflinePlayer nominal = null;
        if (args.length == 2) {
            nominal = Bukkit.getOfflinePlayer(args[1]);
            if (!nominal.hasPlayedBefore() && !nominal.isOnline()) {
                player.sendMessage(ChatColor.RED + "Jogador \"" + args[1] + "\" não encontrado.");
                return true;
            }
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

        ItemStack cheque = criarCheque(player, valor, nominal);
        ultimaEmissao.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getChequeLog().emitido(player.getName(), valor,
                cheque.getItemMeta().getPersistentDataContainer().get(plugin.getChequeIdKey(), PersistentDataType.STRING),
                nominal != null ? nominal.getName() : null);

        // Entrega o cheque; se o inventário estiver cheio, dropa no chão
        var sobras = player.getInventory().addItem(cheque);
        sobras.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        player.sendMessage(ChatColor.GREEN + "Cheque no valor de " + ChatColor.GOLD + formatarValor(valor)
                + ChatColor.GREEN + (nominal != null ? " nominal a " + ChatColor.WHITE + nominal.getName() + ChatColor.GREEN : "")
                + " criado com sucesso!");

        return true;
    }

    private ItemStack criarCheque(Player emissor, double valor, OfflinePlayer nominal) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "Cheque" + ChatColor.DARK_GRAY + " - " + ChatColor.GOLD + formatarValor(valor));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Emitido por: " + ChatColor.WHITE + emissor.getName());
        lore.add(ChatColor.GRAY + "Data: " + ChatColor.WHITE + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        if (nominal != null) {
            lore.add(ChatColor.GRAY + "Nominal a: " + ChatColor.WHITE + nominal.getName() + ChatColor.GRAY + " (só ele resgata)");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Clique direito para resgatar");
        meta.setLore(lore);

        // Grava dados reais no item (não confia no texto do lore, que pode ser editado
        // por outros plugins de anvil/renomear). Isso evita duplicação/falsificação.
        meta.getPersistentDataContainer().set(plugin.getChequeValueKey(), PersistentDataType.DOUBLE, valor);
        meta.getPersistentDataContainer().set(plugin.getChequeIdKey(), PersistentDataType.STRING, UUID.randomUUID().toString());
        if (nominal != null) {
            meta.getPersistentDataContainer().set(plugin.getChequeNominalKey(), PersistentDataType.STRING, nominal.getUniqueId().toString());
        }

        cheque.setItemMeta(meta);
        return cheque;
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
