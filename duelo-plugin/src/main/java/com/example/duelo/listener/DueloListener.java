package com.example.duelo.listener;

import com.example.duelo.DueloPlugin;
import com.example.duelo.model.Duelo;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

/**
 * Regras durante o duelo: ninguém de fora bate nos lutadores (nem eles em outros), ninguém se
 * mexe nem toma dano na contagem, comando e teleporte ficam bloqueados (só o /duelo), desconectar
 * conta como morte e quem morre renasce onde estava antes.
 */
public class DueloListener implements Listener {

    private final DueloPlugin plugin;

    public DueloListener(DueloPlugin plugin) {
        this.plugin = plugin;
    }

    private Duelo duelo(Player jogador) {
        return plugin.getDueloManager().getDuelo(jogador.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoBater(EntityDamageByEntityEvent evento) {
        Player atacante = evento.getDamager() instanceof Player p ? p
                : evento.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
        Player vitima = evento.getEntity() instanceof Player p ? p : null;
        Duelo dAtacante = atacante == null ? null : duelo(atacante);
        Duelo dVitima = vitima == null ? null : duelo(vitima);
        if (dAtacante == null && dVitima == null) return;

        boolean entreOsDois = dAtacante != null && dAtacante == dVitima && atacante != vitima;
        if (!entreOsDois || dAtacante.getFase() != Duelo.Fase.LUTA) evento.setCancelled(true);
    }

    /** Na contagem, nenhum dano (queda, fogo...). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity() instanceof Player p) {
            Duelo d = duelo(p);
            if (d != null && d.getFase() != Duelo.Fase.LUTA) evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMexer(PlayerMoveEvent evento) {
        Duelo d = duelo(evento.getPlayer());
        if (d == null || d.getFase() != Duelo.Fase.CONTAGEM) return;
        Location de = evento.getFrom(), para = evento.getTo();
        if (de.getX() != para.getX() || de.getZ() != para.getZ()) {
            evento.setTo(de.clone().setDirection(para.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoUsarComando(PlayerCommandPreprocessEvent evento) {
        if (duelo(evento.getPlayer()) == null || plugin.isAdmin(evento.getPlayer())) return;
        String comando = evento.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (List.of("duelo", "x1", "duel").contains(comando)) return;
        evento.setCancelled(true);
        evento.getPlayer().sendMessage("§cDurante o duelo, só o §f/duelo §cfunciona.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        Player p = evento.getPlayer();
        if (duelo(p) == null || plugin.getDueloManager().teleporteLiberado(p.getUniqueId())) return;
        evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        if (duelo(evento.getPlayer()) != null) plugin.getDueloManager().aoMorrer(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoRenascer(PlayerRespawnEvent evento) {
        Location volta = plugin.getDueloManager().tirarRenascer(evento.getPlayer().getUniqueId());
        if (volta != null && volta.getWorld() != null) evento.setRespawnLocation(volta);
    }

    /** Sair no meio do duelo conta como morte: os itens caem na arena e o outro vence. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent evento) {
        Player p = evento.getPlayer();
        Duelo d = duelo(p);
        if (d != null && d.getFase() != Duelo.Fase.FIM) p.setHealth(0);
    }
}
