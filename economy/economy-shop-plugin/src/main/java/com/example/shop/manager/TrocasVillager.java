package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Sem troca com villager nem com o vendedor ambulante: esmeralda por item (e item por esmeralda)
 * passaria por fora da loja, dos preços dinâmicos e do painel da economia. O clique no villager
 * é barrado, e a tela de troca dele também, pra qualquer outro caminho que a abra.
 * {@code bloquear-trocas-villager} no config liga e desliga.
 */
public class TrocasVillager implements Listener {

    private final ShopPlugin plugin;

    public TrocasVillager(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ativado() {
        return plugin.getConfig().getBoolean("bloquear-trocas-villager", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoClicar(PlayerInteractEntityEvent evento) {
        if (!ativado() || !(evento.getRightClicked() instanceof AbstractVillager)) return;
        // o ovo de spawn e a etiqueta de nome continuam funcionando; só a troca é barrada
        String item = evento.getPlayer().getInventory().getItem(evento.getHand()).getType().name();
        if (item.equals("NAME_TAG") || item.endsWith("_SPAWN_EGG")) return;
        evento.setCancelled(true);
        if (evento.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) avisar(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAbrir(InventoryOpenEvent evento) {
        if (!ativado() || evento.getInventory().getType() != InventoryType.MERCHANT) return;
        if (!(evento.getInventory().getHolder() instanceof AbstractVillager)) return;
        evento.setCancelled(true);
        if (evento.getPlayer() instanceof Player p) avisar(p);
    }

    private void avisar(Player jogador) {
        jogador.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("mensagens.trocas-villager",
                "&cAs trocas com villager estão desligadas: compre e venda na &6Loja Oficial &c(&f/warp loja&c).")));
    }
}
