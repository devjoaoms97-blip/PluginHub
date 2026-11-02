package com.example.eventos.base;

import com.example.eventos.EventosPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;

/**
 * As regras de todos os eventos de arena:
 * <ul>
 *   <li>Ninguém perde vida: todo dano em quem está lutando é cancelado. Um projétil de outro participante
 *       chama {@link Evento#aoAcertar} (o Vietnã empurra); soco não faz nada.</li>
 *   <li>Caiu (na água): eliminado e vai pra arquibancada. Desconectou: eliminado e volta pra onde estava.</li>
 *   <li>Na arena e na arquibancada só valem os comandos de {@code comandos-liberados} do evento (lutando,
 *       só o do evento); nenhum teleporte de fora leva alguém pra lá, só os do evento. Da arquibancada
 *       ninguém atira.</li>
 *   <li>Lutando, não se joga item fora, não se pega item e não tem fome. Na arena ninguém coloca bloco
 *       e só quebra o que o evento deixa ({@link Evento#podeQuebrar}, o piso do Spleef), sem drop.</li>
 * </ul>
 */
public class EventosListener implements Listener {

    private final EventosPlugin plugin;

    public EventosListener(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    private Evento lutando(Entity e) {
        return e instanceof Player p ? plugin.eventoLutando(p.getUniqueId()) : null;
    }

    private Evento arenaEm(Location l) {
        for (Evento e : plugin.getEventos()) if (e.naArena(l)) return e;
        return null;
    }

    private Evento arquibancadaEm(Location l) {
        for (Evento e : plugin.getEventos()) if (e.naArquibancada(l)) return e;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoBater(EntityDamageByEntityEvent evento) {
        Player atirador = evento.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
        Player batedor = evento.getDamager() instanceof Player p ? p : atirador;
        Evento daVitima = lutando(evento.getEntity());
        // quem está fora não acerta quem luta, e quem luta não acerta ninguém de fora
        if (daVitima == null && (batedor == null || lutando(batedor) == null)) return;
        evento.setCancelled(true);
        if (daVitima == null || atirador == null || atirador == evento.getEntity() || lutando(atirador) != daVitima) return;
        if (daVitima.getFase() != Evento.Fase.LUTA) return;
        daVitima.aoAcertar((Player) evento.getEntity(), (Projectile) evento.getDamager());
    }

    /** Queda, afogamento, qualquer dano: ninguém lutando perde vida. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (!(evento instanceof EntityDamageByEntityEvent) && lutando(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoFome(FoodLevelChangeEvent evento) {
        if (lutando(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMover(PlayerMoveEvent evento) {
        Player p = evento.getPlayer();
        Evento e = lutando(p);
        if (e != null && (p.isInWater() || e.caiu(evento.getTo()))) e.eliminar(p, "caiu na água", false);
    }

    /** Da arquibancada (ou de fora) não se atira na arena; nem na preparação. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoLancar(ProjectileLaunchEvent evento) {
        if (!(evento.getEntity().getShooter() instanceof Player p) || plugin.isAdmin(p)) return;
        Evento e = lutando(p);
        if ((e == null && arquibancadaEm(p.getLocation()) != null) || (e != null && e.getFase() != Evento.Fase.LUTA)) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoLargar(PlayerDropItemEvent evento) {
        if (lutando(evento.getPlayer()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPegar(EntityPickupItemEvent evento) {
        if (lutando(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Player p = evento.getPlayer();
        Evento daArena = arenaEm(evento.getBlock().getLocation());
        if (daArena == null || plugin.isAdmin(p)) return;
        if (lutando(p) == daArena && daArena.getFase() == Evento.Fase.LUTA && daArena.podeQuebrar(p, evento.getBlock())) {
            evento.setDropItems(false);
            evento.setExpToDrop(0);
        } else {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (!plugin.isAdmin(evento.getPlayer()) && arenaEm(evento.getBlock().getLocation()) != null) evento.setCancelled(true);
    }

    /** Na arena e na arquibancada, só os comandos liberados do evento (lutando, só o do próprio evento). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoUsarComando(PlayerCommandPreprocessEvent evento) {
        Player p = evento.getPlayer();
        if (plugin.isAdmin(p)) return;
        Evento e = lutando(p);
        boolean lutando = e != null;
        if (e == null) e = arenaEm(p.getLocation());
        if (e == null) e = arquibancadaEm(p.getLocation());
        if (e == null) return;
        String comando = evento.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (comando.contains(":")) comando = comando.substring(comando.indexOf(':') + 1);
        boolean liberado = e.cfg().getStringList("comandos-liberados").contains(comando);
        if (liberado && (!lutando || comando.equals(e.id()))) return;
        evento.setCancelled(true);
        p.sendMessage(lutando ? "§cNo " + e.nome() + ", só o §f/" + e.id() + " §cfunciona. Caiu, sai."
                : "§cNa arena do " + e.nome() + ", só §f/" + e.id() + " §ce §f/spawn §cfuncionam.");
    }

    /**
     * Lutando, ninguém sai por teleporte (só os do evento). E nenhum teleporte de fora (/tpa, /home,
     * /back, warp...) leva alguém pra dentro de uma arena ou arquibancada: só o do evento (/<evento> assistir).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        Player p = evento.getPlayer();
        if (plugin.isAdmin(p) || plugin.getEventos().stream().anyMatch(e -> e.teleporteLiberado(p.getUniqueId()))) return;
        if (lutando(p) != null) {
            evento.setCancelled(true);
            return;
        }
        Evento destino = arenaEm(evento.getTo());
        if (destino == null) destino = arquibancadaEm(evento.getTo());
        if (destino == null) return;
        boolean jaDentro = destino.naArena(evento.getFrom()) || destino.naArquibancada(evento.getFrom());
        if (!jaDentro) {
            evento.setCancelled(true);
            p.sendMessage("§cPra ver o " + destino.nome() + ", use §f/" + destino.id() + " assistir§c.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent evento) {
        Player p = evento.getPlayer();
        for (Evento e : plugin.getEventos()) {
            if (e.getFase() == Evento.Fase.INSCRICAO) e.sair(p);
            else if (e.lutando(p.getUniqueId()) != null) e.eliminar(p, "desconectou", true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        for (Evento e : plugin.getEventos()) e.aoEntrar(evento.getPlayer());
    }
}
