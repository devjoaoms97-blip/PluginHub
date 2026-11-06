package com.example.tesouros;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * O teleporte das warps deste plugin pelo Essentials (o {@code AsyncTeleport} dele), pra valer a mesma
 * espera do {@code /warp loja}: os 3 s do {@code teleport-delay} (VIP com
 * {@code essentials.teleport.timer.bypass} pula), a contagem do CombatePlugin e o cancelamento por dano.
 * Antes, o {@code /warp tesouros} teleportava na hora (2026-11-06).
 * <p>
 * Por reflexão, como o CombatePlugin: o plugin não depende do Essentials pra compilar. Sem ele (ou se a
 * API mudou), o teleporte é na hora, como antes.
 */
final class EsperaTeleporte {

    private EsperaTeleporte() {
    }

    /** Leva o jogador, com a espera do Essentials. Completa com true quando ele chega. */
    static CompletableFuture<Boolean> levar(Player p, Location destino, Logger log) {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess != null && ess.isEnabled()) {
            try {
                Object usuario = ess.getClass().getMethod("getUser", Player.class).invoke(ess, p);
                Object async = usuario.getClass().getMethod("getAsyncTeleport").invoke(usuario);
                Class<?> trade = Class.forName("com.earth2me.essentials.Trade", false, ess.getClass().getClassLoader());
                Method teleport = async.getClass().getMethod("teleport", Location.class, trade,
                        PlayerTeleportEvent.TeleportCause.class, CompletableFuture.class);
                CompletableFuture<Boolean> futuro = new CompletableFuture<>();
                teleport.invoke(async, destino, null, PlayerTeleportEvent.TeleportCause.COMMAND, futuro);
                return futuro.exceptionally(e -> false);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warning("Teleporte pelo Essentials falhou (" + e + "); levando sem a espera.");
            }
        }
        return p.teleportAsync(destino, PlayerTeleportEvent.TeleportCause.COMMAND);
    }
}
