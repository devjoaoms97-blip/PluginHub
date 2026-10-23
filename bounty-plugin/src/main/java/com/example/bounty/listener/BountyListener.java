package com.example.bounty.listener;

import com.example.bounty.BountyPlugin;
import com.example.bounty.model.ContratoCla;
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
import java.util.List;
import java.util.UUID;

/**
 * Paga a recompensa quando o alvo morre pra outro jogador, e um abate do contrato mais antigo
 * contra o clã dele (se houver). Morte sem matador (queda, lava, mob) não paga — a recompensa
 * continua valendo.
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

        cobrarRecompensa(matador, alvo);
        cobrarContratoCla(matador, alvo);
    }

    private void cobrarRecompensa(Player matador, Player alvo) {
        Recompensa recompensa = plugin.getBountyManager().get(alvo.getUniqueId());
        if (recompensa == null) return;

        String bloqueio = motivoBloqueio(matador, alvo,
                plugin.getBountyManager().cooldownRestante(matador.getUniqueId(), alvo.getUniqueId()));
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
        plugin.getDiscord().anunciar(":moneybag: **" + matador.getName() + "** cobrou **" + BountyPlugin.formatarValor(total)
                + "** pela cabeça de **" + alvo.getName() + "**!", total);
    }

    private void cobrarContratoCla(Player matador, Player alvo) {
        String tag = plugin.getClasIntegration().tagDoCla(alvo.getUniqueId());
        if (tag == null || plugin.getBountyManager().contratosContra(tag).isEmpty()) return;
        // colega do mesmo clã nunca cobra (mesmo com anti-farm.mesmo-cla desligado): seria só matar o amigo e dividir
        if (tag.equals(plugin.getClasIntegration().tagDoCla(matador.getUniqueId()))) return;

        String bloqueio = motivoBloqueio(matador, alvo,
                plugin.getBountyManager().cooldownRestanteCla(matador.getUniqueId(), alvo.getUniqueId()));
        if (bloqueio != null) {
            matador.sendMessage("§cO contrato contra o clã §f" + tag + " §cnão pagou esse abate: " + bloqueio + "§c.");
            return;
        }

        ContratoCla contrato = plugin.getBountyManager().pagarAbateCla(matador, alvo.getUniqueId(), tag);
        if (contrato == null) return;
        double valor = contrato.getValorPorAbate();
        plugin.getPixIntegration().registrarComServidor(matador.getUniqueId(), valor, true, "Bounty no clã " + tag);

        matador.sendMessage("§4☠ §cVocê recebeu §f" + BountyPlugin.formatarValor(valor) + " §cpelo contrato contra o clã §f"
                + tag + "§c. §7(" + plugin.getBountyManager().contratosContra(tag).stream()
                .mapToInt(ContratoCla::getAbatesRestantes).sum() + " abate(s) restante(s) contra eles)");
        matador.playSound(matador.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        alvo.sendMessage("§cSeu clã está sendo caçado: §f" + matador.getName() + " §ccobrou um abate do contrato contra o §f" + tag + "§c.");
        plugin.getDiscord().anunciar(":crossed_swords: **" + matador.getName() + "** cobrou **" + BountyPlugin.formatarValor(valor)
                + "** por matar **" + alvo.getName() + "** do clã **" + tag + "**.", valor);
    }

    private String motivoBloqueio(Player matador, Player alvo, long cooldownRestante) {
        if (mundoIgnorado(alvo)) {
            return "abates em duelo e em evento de arena não contam";
        }
        if (plugin.getConfig().getBoolean("anti-farm.mesmo-ip", true) && mesmoIp(matador, alvo)) {
            return "vocês estão conectados do mesmo IP";
        }
        if (plugin.getConfig().getBoolean("anti-farm.mesmo-cla", true)
                && plugin.getClasIntegration().mesmoCla(matador.getUniqueId(), alvo.getUniqueId())) {
            return "vocês são do mesmo clã";
        }
        if (cooldownRestante > 0) {
            return "você já cobrou esse jogador há pouco tempo (libera em " + BountyPlugin.formatarTempo(cooldownRestante) + ")";
        }
        return null;
    }

    /** Mundo em {@code anti-farm.mundos-ignorados} (as arenas de X1 e do Gladiador): lá dá pra combinar a luta. */
    private boolean mundoIgnorado(Player alvo) {
        String mundo = alvo.getWorld().getName();
        // config.yml antigo (sem a chave) cai no padrão; lista vazia explícita desativa
        List<String> ignorados = plugin.getConfig().contains("anti-farm.mundos-ignorados")
                ? plugin.getConfig().getStringList("anti-farm.mundos-ignorados") : List.of("arenas");
        return ignorados.stream().anyMatch(mundo::equalsIgnoreCase);
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
