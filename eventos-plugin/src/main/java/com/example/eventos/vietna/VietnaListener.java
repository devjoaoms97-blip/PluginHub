package com.example.eventos.vietna;

import com.example.eventos.EventosPlugin;
import com.example.eventos.vietna.Vietna.Participante;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WindCharge;
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
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Regras do Vietnã:
 * <ul>
 *   <li>Ninguém perde vida: todo dano em quem está lutando é cancelado. Flecha, bola de neve, carga de
 *       vento e vara de outro participante empurram (com a força do config); soco não vale.</li>
 *   <li>Caiu na água: eliminado (vai pra arquibancada). Desconectou: eliminado (volta pra onde estava).</li>
 *   <li>Na arena e na arquibancada só valem os comandos de {@code comandos-liberados}; nenhum teleporte
 *       de fora leva ninguém pra lá (só o do evento). Quem está na arquibancada não atira.</li>
 *   <li>Lutando, não se joga item fora, não se pega item, não se quebra nem coloca bloco e não tem fome.</li>
 * </ul>
 */
public class VietnaListener implements Listener {

    private final EventosPlugin plugin;

    public VietnaListener(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    private Vietna vietna() {
        return plugin.getVietna();
    }

    private Participante lutando(Entity e) {
        return e instanceof Player p ? vietna().lutando(p.getUniqueId()) : null;
    }

    private static Player atirador(Entity damager) {
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoBater(EntityDamageByEntityEvent evento) {
        Player atirador = atirador(evento.getDamager());
        boolean vitimaLutando = lutando(evento.getEntity()) != null;
        // quem está na arquibancada (ou fora) não acerta quem luta, e quem luta não acerta ninguém de fora
        if (!vitimaLutando && (atirador == null || lutando(atirador) == null)) return;
        evento.setCancelled(true);
        if (!vitimaLutando || atirador == null || atirador == evento.getEntity() || lutando(atirador) == null) return;
        if (vietna().getFase() != Vietna.Fase.LUTA) return;
        empurrar((Player) evento.getEntity(), (Projectile) evento.getDamager());
    }

    /** Empurrão na direção do projétil (a flecha conforme a força do tiro). */
    private void empurrar(Player vitima, Projectile proj) {
        String tipo = proj instanceof AbstractArrow ? "flecha" : proj instanceof Snowball ? "bola-de-neve"
                : proj instanceof WindCharge ? "carga-de-vento" : proj instanceof FishHook ? "vara-de-pesca" : null;
        if (tipo == null) return;
        double forca = plugin.getConfig().getDouble("vietna.empurrao." + tipo);
        if (proj instanceof AbstractArrow) forca *= Math.min(1, proj.getVelocity().length() / 3.0);
        Vector direcao = proj.getVelocity().clone().setY(0);
        if (direcao.lengthSquared() < 1e-6) direcao = vitima.getLocation().toVector().subtract(proj.getLocation().toVector()).setY(0);
        if (direcao.lengthSquared() < 1e-6) return;
        Vector empurrao = direcao.normalize().multiply(forca).setY(plugin.getConfig().getDouble("vietna.empurrao.vertical"));
        // no tick seguinte: o cancelamento do dano já passou e a velocidade fica
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (vitima.isOnline()) vitima.setVelocity(vitima.getVelocity().add(empurrao));
        });
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
        if (lutando(p) == null) return;
        if (p.isInWater() || vietna().getArena().caiu(evento.getTo())) {
            vietna().eliminar(p, "caiu na água", false);
        }
    }

    /** Da arquibancada (ou de fora) não se atira na arena; nem na preparação. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoLancar(ProjectileLaunchEvent evento) {
        if (!(evento.getEntity().getShooter() instanceof Player p) || plugin.isAdmin(p)) return;
        Participante part = lutando(p);
        boolean naArquibancada = vietna().getArena().naArquibancada(p.getLocation());
        if ((part == null && naArquibancada) || (part != null && vietna().getFase() != Vietna.Fase.LUTA)) {
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
        if (!plugin.isAdmin(evento.getPlayer()) && vietna().getArena().naArena(evento.getBlock().getLocation())) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (!plugin.isAdmin(evento.getPlayer()) && vietna().getArena().naArena(evento.getBlock().getLocation())) evento.setCancelled(true);
    }

    /** Na arena e na arquibancada, só os comandos liberados (o /vietna e o /spawn de quem assiste). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoUsarComando(PlayerCommandPreprocessEvent evento) {
        Player p = evento.getPlayer();
        if (plugin.isAdmin(p)) return;
        ArenaVietna arena = vietna().getArena();
        boolean lutando = lutando(p) != null;
        if (!lutando && !arena.naArena(p.getLocation()) && !arena.naArquibancada(p.getLocation())) return;
        String comando = evento.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (comando.contains(":")) comando = comando.substring(comando.indexOf(':') + 1);
        boolean liberado = plugin.getConfig().getStringList("vietna.comandos-liberados").contains(comando);
        // lutando, nem o /spawn: só o /vietna
        if (liberado && (!lutando || comando.startsWith("vietna"))) return;
        evento.setCancelled(true);
        p.sendMessage(lutando ? "§cNo Vietnã, só o §f/vietna §cfunciona. Caiu na água, sai."
                : "§cNa arena do Vietnã, só §f/vietna §ce §f/spawn §cfuncionam.");
    }

    /**
     * Lutando, ninguém sai por teleporte (só os do evento). E nenhum teleporte de fora (/tpa, /home,
     * /back, warp...) leva alguém pra dentro da arena ou da arquibancada: só o do evento (/vietna assistir).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        Player p = evento.getPlayer();
        if (vietna().teleporteLiberado(p.getUniqueId()) || plugin.isAdmin(p)) return;
        if (lutando(p) != null) {
            evento.setCancelled(true);
            return;
        }
        ArenaVietna arena = vietna().getArena();
        boolean jaDentro = arena.naArena(evento.getFrom()) || arena.naArquibancada(evento.getFrom());
        if (!jaDentro && (arena.naArena(evento.getTo()) || arena.naArquibancada(evento.getTo()))) {
            evento.setCancelled(true);
            p.sendMessage("§cPra ver o Vietnã, use §f/vietna assistir§c.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent evento) {
        Player p = evento.getPlayer();
        if (vietna().getFase() == Vietna.Fase.INSCRICAO) {
            vietna().sair(p);
            return;
        }
        if (lutando(p) != null) vietna().eliminar(p, "desconectou", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        vietna().aoEntrar(evento.getPlayer());
    }
}
