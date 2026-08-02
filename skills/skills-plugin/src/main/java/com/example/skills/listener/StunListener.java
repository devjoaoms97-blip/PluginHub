package com.example.skills.listener;

import com.example.skills.SkillsPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class StunListener implements Listener {

    private final SkillsPlugin plugin;

    public StunListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getAtordoamentoManager().estaAtordoado(event.getPlayer().getUniqueId())) {
            return;
        }

        Location de = event.getFrom();
        Location para = event.getTo();
        if (para == null) return;

        boolean tentouSeMover = de.getX() != para.getX() || de.getY() != para.getY() || de.getZ() != para.getZ();
        if (!tentouSeMover) return;

        // Mantém a posição travada, mas deixa o jogador olhar em volta livremente
        Location travada = de.clone();
        travada.setYaw(para.getYaw());
        travada.setPitch(para.getPitch());
        event.setTo(travada);
    }
}
