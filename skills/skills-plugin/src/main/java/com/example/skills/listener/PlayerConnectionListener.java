package com.example.skills.listener;

import com.example.skills.SkillsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final SkillsPlugin plugin;

    public PlayerConnectionListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Carrega os dados (get() já lê do disco se ainda não estiver em cache)
        plugin.getSkillDataManager().get(event.getPlayer().getUniqueId());
        plugin.getPassiveSkillsTask().atualizarVelocidade(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSkillDataManager().descarregar(event.getPlayer().getUniqueId());
        plugin.getCombatTagManager().remover(event.getPlayer().getUniqueId());
    }
}
