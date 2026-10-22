package com.example.clas.listener;

import com.example.clas.ClasPlugin;
import com.example.clas.model.Cla;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.net.InetSocketAddress;

/**
 * Tag do clã no chat, fogo amigo e placar de abates.
 *
 * A tag entra como PREFIXO do formato que já existir: o VipPlugin monta o formato em LOW
 * (com a tag do VIP), e aqui, em NORMAL, a tag do clã vai na frente — os dois convivem.
 */
public class ClaListener implements Listener {

    private final ClasPlugin plugin;

    public ClaListener(ClasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        Cla cla = plugin.getClaManager().getClaDe(evento.getPlayer().getUniqueId());
        if (cla == null) return;
        evento.setFormat(plugin.tagFormatada(cla) + " " + evento.getFormat());
    }

    /** Sem fogo amigo: membro do mesmo clã não machuca o outro (golpe direto ou projétil). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAtacar(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player vitima)) return;
        Player atacante = atacante(evento);
        if (atacante == null || atacante.equals(vitima)) return;

        Cla cla = plugin.getClaManager().getClaDe(atacante.getUniqueId());
        if (cla == null || cla.isFogoAmigo()) return;
        if (plugin.getClaManager().mesmoCla(atacante.getUniqueId(), vitima.getUniqueId())) {
            evento.setCancelled(true);
        }
    }

    private Player atacante(EntityDamageByEntityEvent evento) {
        if (evento.getDamager() instanceof Player jogador) return jogador;
        if (evento.getDamager() instanceof Projectile projetil && projetil.getShooter() instanceof Player atirador) {
            return atirador;
        }
        return null;
    }

    /** Abate conta pro clã do matador (e morte pro clã da vítima) — nunca entre colegas nem entre contas do mesmo IP. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        Player vitima = evento.getEntity();
        Player matador = vitima.getKiller();
        if (matador == null || matador.equals(vitima)) return;
        if (plugin.getClaManager().mesmoCla(matador.getUniqueId(), vitima.getUniqueId())) return;
        if (mesmoIp(matador, vitima)) return;

        boolean mudou = false;
        Cla claMatador = plugin.getClaManager().getClaDe(matador.getUniqueId());
        if (claMatador != null) {
            claMatador.setAbates(claMatador.getAbates() + 1);
            mudou = true;
        }
        Cla claVitima = plugin.getClaManager().getClaDe(vitima.getUniqueId());
        if (claVitima != null) {
            claVitima.setMortes(claVitima.getMortes() + 1);
            mudou = true;
        }
        if (mudou) plugin.getClaManager().salvar();
    }

    private boolean mesmoIp(Player a, Player b) {
        InetSocketAddress ipA = a.getAddress();
        InetSocketAddress ipB = b.getAddress();
        return ipA != null && ipB != null && ipA.getAddress() != null && ipA.getAddress().equals(ipB.getAddress());
    }
}
