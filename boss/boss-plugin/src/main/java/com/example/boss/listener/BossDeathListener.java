package com.example.boss.listener;

import com.example.boss.BossPlugin;
import com.example.boss.util.BossTagUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class BossDeathListener implements Listener {

    private final BossPlugin plugin;

    public BossDeathListener(BossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) {
            return; // não é o nosso boss
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        for (ItemStack item : plugin.getLootManager().sortearLootChao()) {
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), item);
        }

        Player matador = event.getEntity().getKiller();

        if (matador != null) {
            ItemStack premio = plugin.getLootManager().sortearItemCampeao();
            if (premio != null) {
                Map<Integer, ItemStack> sobras = matador.getInventory().addItem(premio);
                sobras.values().forEach(item -> matador.getWorld().dropItemNaturally(matador.getLocation(), item));
            }

            plugin.getChampionManager().coroarNovoCampeao(matador);

            String mensagem = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("mensagem-vitoria",
                            "&6★ &e%jogador% &6derrotou o chefe e agora é o novo campeão! ★")
                            .replace("%jogador%", matador.getName()));
            Bukkit.broadcastMessage(mensagem);
        } else {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&7O chefe foi derrotado."));
        }

        plugin.getBossManager().finalizar(true);
    }
}
