package com.example.combate;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Em combate: quem bate ou apanha de outro jogador fica {@code em-combate.segundos} (5) marcado, VIP ou
 * não. Só conta o dano que valeu (no spawn sem PvP o golpe é cancelado e não marca ninguém).
 * <ul>
 *   <li>Os comandos de {@code em-combate.comandos} (teleporte: /spawn, /warp, /home, /tpa, /back...) não
 *       funcionam, e nenhum teleporte por comando leva quem está marcado (o /tpaccept de outro, o fim
 *       do tempo de espera do Essentials).</li>
 *   <li>Quem sai do servidor marcado morre na hora, com os itens no chão e aviso no chat. Só vale pra
 *       quem sai por conta própria ou cai (expulsão e desligamento do servidor não matam), e não nos
 *       mundos de {@code em-combate.mundos-ignorados} (as arenas: o Gladiador e o duelo já tratam a
 *       desconexão do jeito deles).</li>
 * </ul>
 */
public class EmCombate implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final CombatePlugin plugin;
    /** Até quando cada jogador está em combate (System.currentTimeMillis). */
    private final Map<UUID, Long> ate = new HashMap<>();

    public EmCombate(CombatePlugin plugin) {
        this.plugin = plugin;
    }

    /** Segundos de combate que faltam (0 se não está em combate). */
    public long restante(Player jogador) {
        Long fim = ate.get(jogador.getUniqueId());
        if (fim == null) return 0;
        long ms = fim - System.currentTimeMillis();
        if (ms <= 0) {
            ate.remove(jogador.getUniqueId());
            return 0;
        }
        return (ms + 999) / 1000;
    }

    private void marcar(Player jogador) {
        boolean novo = restante(jogador) == 0;
        ate.put(jogador.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("em-combate.segundos", 5) * 1000);
        if (novo) jogador.sendActionBar(LEGADO.deserialize(msg("entrou", "&c⚔ Em combate! &7Sem teleporte, e quem desloga morre.")));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoApanhar(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player vitima) || evento.getFinalDamage() <= 0) return;
        Player atacante = evento.getDamager() instanceof Player p ? p
                : evento.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2 ? p2 : null;
        if (atacante == null || atacante.equals(vitima)) return;
        marcar(vitima);
        marcar(atacante);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoUsarComando(PlayerCommandPreprocessEvent evento) {
        Player jogador = evento.getPlayer();
        long resta = restante(jogador);
        if (resta == 0 || !bloqueado(evento.getMessage())) return;
        evento.setCancelled(true);
        jogador.sendMessage(msg("comando", "&c⚔ Você está em combate! Espere &f%s s &cpra teleportar.").replace("%s", String.valueOf(resta)));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        if (evento.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;
        long resta = restante(evento.getPlayer());
        if (resta == 0) return;
        evento.setCancelled(true);
        evento.getPlayer().sendMessage(msg("comando", "&c⚔ Você está em combate! Espere &f%s s &cpra teleportar.").replace("%s", String.valueOf(resta)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSair(PlayerQuitEvent evento) {
        Player jogador = evento.getPlayer();
        if (restante(jogador) == 0) return;
        ate.remove(jogador.getUniqueId());
        if (!plugin.getConfig().getBoolean("em-combate.morrer-ao-deslogar", true)) return;
        PlayerQuitEvent.QuitReason motivo = evento.getReason();
        if (motivo != PlayerQuitEvent.QuitReason.DISCONNECTED && motivo != PlayerQuitEvent.QuitReason.TIMED_OUT) return;
        if (plugin.getConfig().getStringList("em-combate.mundos-ignorados").contains(jogador.getWorld().getName())) return;
        jogador.setHealth(0);
        Bukkit.broadcast(LEGADO.deserialize(msg("deslogou", "&c☠ &f%jogador% &cdeslogou em combate e morreu.")
                .replace("%jogador%", jogador.getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        ate.remove(evento.getEntity().getUniqueId());
    }

    /**
     * O comando está na lista? Compara sem a barra, sem o "plugin:" e sem maiúsculas. Uma entrada com
     * duas palavras ("vip warp") barra só aquele subcomando.
     */
    private boolean bloqueado(String mensagem) {
        String[] partes = mensagem.substring(1).trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) return false;
        String rotulo = partes[0].contains(":") ? partes[0].substring(partes[0].indexOf(':') + 1) : partes[0];
        List<String> lista = plugin.getConfig().getStringList("em-combate.comandos");
        for (String entrada : lista) {
            String[] e = entrada.toLowerCase(Locale.ROOT).trim().split("\\s+");
            if (!e[0].equals(rotulo)) continue;
            if (e.length == 1 || (partes.length > 1 && e[1].equals(partes[1]))) return true;
        }
        return false;
    }

    private String msg(String chave, String padrao) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("em-combate.mensagens." + chave, padrao));
    }
}
