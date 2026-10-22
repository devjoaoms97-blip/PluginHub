package com.example.bounty.listener;

import com.example.bounty.BountyPlugin;
import com.example.bounty.model.Recompensa;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Paga a recompensa quando o alvo morre pra outro jogador. Morte sem matador (queda, lava,
 * mob) não paga — a recompensa continua valendo.
 *
 * Anti-farm (a recompensa NÃO é paga, e continua valendo):
 * <ul>
 *   <li>matador e alvo com o mesmo IP (conta alternativa);</li>
 *   <li>matador e alvo no mesmo clã (se o ClasPlugin estiver instalado);</li>
 *   <li>o mesmo matador já cobrou esse alvo há pouco tempo ({@code anti-farm.cooldown-horas}).</li>
 * </ul>
 */
public class BountyListener implements Listener {

    private final BountyPlugin plugin;

    public BountyListener(BountyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        Player alvo = evento.getEntity();
        Player matador = alvo.getKiller();
        if (matador == null || matador.equals(alvo)) return;

        Recompensa recompensa = plugin.getBountyManager().get(alvo.getUniqueId());
        if (recompensa == null) return;

        String bloqueio = motivoBloqueio(matador, alvo);
        if (bloqueio != null) {
            matador.sendMessage("§cA recompensa pela cabeça de §f" + alvo.getName() + " §cnão foi paga: " + bloqueio + "§c.");
            return;
        }

        double total = plugin.getBountyManager().pagar(matador, recompensa);
        plugin.getPixIntegration().registrarComServidor(matador.getUniqueId(), total, true,
                "Recompensa por " + alvo.getName());

        Bukkit.broadcastMessage("§4§l☠ §c" + matador.getName() + " §ccobrou a recompensa de §f"
                + BountyPlugin.formatarValor(total) + " §cpela cabeça de §f" + alvo.getName() + "§c!");
        matador.playSound(matador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
    }

    private String motivoBloqueio(Player matador, Player alvo) {
        if (plugin.getConfig().getBoolean("anti-farm.mesmo-ip", true) && mesmoIp(matador, alvo)) {
            return "vocês estão conectados do mesmo IP";
        }
        if (plugin.getConfig().getBoolean("anti-farm.mesmo-cla", true)
                && plugin.getClasIntegration().mesmoCla(matador.getUniqueId(), alvo.getUniqueId())) {
            return "vocês são do mesmo clã";
        }
        long restante = plugin.getBountyManager().cooldownRestante(matador.getUniqueId(), alvo.getUniqueId());
        if (restante > 0) {
            return "você já cobrou esse jogador há pouco tempo (libera em " + BountyPlugin.formatarTempo(restante) + ")";
        }
        return null;
    }

    private boolean mesmoIp(Player a, Player b) {
        InetSocketAddress ipA = a.getAddress();
        InetSocketAddress ipB = b.getAddress();
        return ipA != null && ipB != null && ipA.getAddress() != null
                && ipA.getAddress().equals(ipB.getAddress());
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        UUID id = evento.getPlayer().getUniqueId();
        Recompensa recompensa = plugin.getBountyManager().get(id);
        if (recompensa == null) return;
        recompensa.setNomeAlvo(evento.getPlayer().getName());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (evento.getPlayer().isOnline()) {
                evento.getPlayer().sendMessage("§c⚠ Tem uma recompensa de §f" + BountyPlugin.formatarValor(recompensa.getTotal())
                        + " §cpela sua cabeça. Cuidado!");
            }
        }, 40L);
    }
}
