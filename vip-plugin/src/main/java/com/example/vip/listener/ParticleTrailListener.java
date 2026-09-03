package com.example.vip.listener;

import com.example.vip.VipPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda a escolha de partícula de cada jogador com o perk ativo, e periodicamente spawna
 * o efeito ao redor de quem estiver online com uma escolha definida.
 */
public class ParticleTrailListener implements Listener {

    private static final long PERIODO_TICKS = 10L;

    private final Map<UUID, Particle> escolhas = new HashMap<>();
    private final BukkitTask tarefa;

    public ParticleTrailListener(VipPlugin plugin) {
        this.tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnarTodos, PERIODO_TICKS, PERIODO_TICKS);
    }

    public void definir(UUID jogadorId, Particle particula) {
        escolhas.put(jogadorId, particula);
    }

    public void limpar(UUID jogadorId) {
        escolhas.remove(jogadorId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        escolhas.remove(event.getPlayer().getUniqueId());
    }

    private void spawnarTodos() {
        for (Map.Entry<UUID, Particle> entrada : escolhas.entrySet()) {
            Player jogador = Bukkit.getPlayer(entrada.getKey());
            if (jogador == null || !jogador.isOnline()) {
                continue;
            }
            Location local = jogador.getLocation().add(0, 0.2, 0);
            jogador.getWorld().spawnParticle(entrada.getValue(), local, 6, 0.3, 0.1, 0.3, 0.01);
        }
    }

    public void desligar() {
        tarefa.cancel();
    }
}
