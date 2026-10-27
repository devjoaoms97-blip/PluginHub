package com.example.cheque;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * O cheque não vira dinheiro com o clique direito: só depositando no Bancário ({@link Banco}).
 * Aqui só fica o aviso pra quem tenta usar do jeito antigo.
 */
public class ChequeListener implements Listener {

    private final ChequePlugin plugin;

    public ChequeListener(ChequePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Evita disparar duas vezes (mão principal + mão secundária)
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getBanco().ehCheque(event.getItem())) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "Pra receber o valor, deposite o cheque com o "
                + ChatColor.GOLD + "Bancário" + ChatColor.YELLOW + ".");
    }
}
