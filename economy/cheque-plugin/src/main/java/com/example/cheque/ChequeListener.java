package com.example.cheque;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public class ChequeListener implements Listener {

    private final ChequePlugin plugin;

    public ChequeListener(ChequePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Evita disparar duas vezes (mão principal + mão secundária)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(plugin.getChequeValueKey(), PersistentDataType.DOUBLE)) {
            return; // é papel comum, não um cheque
        }

        // A partir daqui temos certeza que é um cheque válido
        event.setCancelled(true);

        Player player = event.getPlayer();

        if (!player.hasPermission("cheque.resgatar")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para resgatar cheques.");
            return;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Não há um plugin de economia (Vault) instalado no servidor.");
            return;
        }

        Double valor = pdc.get(plugin.getChequeValueKey(), PersistentDataType.DOUBLE);
        if (valor == null || valor <= 0) {
            player.sendMessage(ChatColor.RED + "Este cheque é inválido.");
            return;
        }

        economy.depositPlayer(player, valor);

        // Consome apenas 1 unidade do cheque
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        player.sendMessage(ChatColor.GREEN + "Você resgatou " + ChatColor.GOLD + formatarValor(valor)
                + ChatColor.GREEN + "! O valor foi depositado na sua conta.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
