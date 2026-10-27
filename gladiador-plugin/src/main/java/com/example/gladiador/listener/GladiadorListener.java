package com.example.gladiador.listener;

import com.example.gladiador.GladiadorPlugin;
import com.example.gladiador.model.Evento;
import com.example.gladiador.model.Evento.Participante;
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
 * Regras do Gladiador: dano só entre participantes e só na luta (no modo por clã, nunca entre o
 * mesmo clã), nenhum dano na preparação, comando e teleporte bloqueados, morte ou desconexão
 * eliminam, e quem morre renasce onde estava antes de entrar.
 */
public class GladiadorListener implements Listener {

    private final GladiadorPlugin plugin;

    public GladiadorListener(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    private Participante participante(Player jogador) {
        return plugin.getEventoManager().emCombate(jogador.getUniqueId());
    }

    private Evento evento() {
        return plugin.getEventoManager().getEvento();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoBater(EntityDamageByEntityEvent evento) {
        Player atacante = evento.getDamager() instanceof Player p ? p
                : evento.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
        Player vitima = evento.getEntity() instanceof Player p ? p : null;
        Participante pAtacante = atacante == null ? null : participante(atacante);
        Participante pVitima = vitima == null ? null : participante(vitima);
        if (pAtacante == null && pVitima == null) return;

        boolean valendo = pAtacante != null && pVitima != null && atacante != vitima
                && pAtacante.isVivo() && pVitima.isVivo() && evento().getFase() == Evento.Fase.LUTA;
        boolean mesmoCla = valendo && evento().getModo() == Evento.Modo.CLA && pAtacante.getCla().equals(pVitima.getCla());
        if (!valendo || mesmoCla) evento.setCancelled(true);
    }

    /**
     * Destaque Top Damage: soma a vida que o golpe tirou de verdade (no máximo a que a vítima
     * tinha, pra golpe em quem já está morrendo não inflar o número).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void contarDano(EntityDamageByEntityEvent evento) {
        if (evento() == null || evento().getFase() != Evento.Fase.LUTA || !(evento.getEntity() instanceof Player vitima)) return;
        Player atacante = evento.getDamager() instanceof Player p ? p
                : evento.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
        Participante pAtacante = atacante == null ? null : participante(atacante);
        if (pAtacante == null || participante(vitima) == null || atacante == vitima) return;
        pAtacante.somarDano(Math.min(evento.getFinalDamage(), vitima.getHealth() + vitima.getAbsorptionAmount()));
    }

    /** Destaque Fujão: blocos andados (na horizontal) durante a luta, por quem ainda está de pé. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void contarDistancia(PlayerMoveEvent evento) {
        Participante p = participante(evento.getPlayer());
        if (p == null || !p.isVivo() || evento().getFase() != Evento.Fase.LUTA) return;
        double dx = evento.getTo().getX() - evento.getFrom().getX(), dz = evento.getTo().getZ() - evento.getFrom().getZ();
        double blocos = Math.sqrt(dx * dx + dz * dz);
        if (blocos < 10) p.somarDistancia(blocos); // um salto grande é teleporte/correção do servidor, não corrida
    }

    /** Fora da luta (preparação e fim), participante não toma dano nenhum. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity() instanceof Player p && participante(p) != null && evento().getFase() != Evento.Fase.LUTA) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoUsarComando(PlayerCommandPreprocessEvent evento) {
        Participante p = participante(evento.getPlayer());
        if (p == null || !p.isVivo() || plugin.isAdmin(evento.getPlayer())) return;
        String comando = evento.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (List.of("gladiador", "glad").contains(comando)) return;
        evento.setCancelled(true);
        evento.getPlayer().sendMessage("§cDurante o Gladiador, só o §f/gladiador §cfunciona.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        Player jogador = evento.getPlayer();
        Participante p = participante(jogador);
        if (p == null || !p.isVivo() || plugin.getEventoManager().teleporteLiberado(jogador.getUniqueId())) return;
        evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        Player morto = evento.getPlayer();
        if (participante(morto) != null) {
            Player matador = morto.getKiller();
            Participante pMatador = matador == null || matador == morto ? null : participante(matador);
            if (pMatador != null) pMatador.somarAbate();
            plugin.getEventoManager().eliminar(morto, matador != null ? "foi eliminado por " + matador.getName() : "foi eliminado");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoRenascer(PlayerRespawnEvent evento) {
        Location volta = plugin.getEventoManager().tirarRenascer(evento.getPlayer().getUniqueId());
        if (volta != null && volta.getWorld() != null) evento.setRespawnLocation(volta);
    }

    /** Sair no meio do evento elimina como uma morte (os itens caem na arena). Nas inscrições, devolve. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent evento) {
        Player jogador = evento.getPlayer();
        Evento e = evento();
        if (e == null) return;
        if (e.getFase() == Evento.Fase.INSCRICAO && e.getParticipantes().containsKey(jogador.getUniqueId())) {
            plugin.getEventoManager().desinscrever(jogador);
            return;
        }
        Participante p = participante(jogador);
        if (p != null && p.isVivo()) jogador.setHealth(0);
    }
}
