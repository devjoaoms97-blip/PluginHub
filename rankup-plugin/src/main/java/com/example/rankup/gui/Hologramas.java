package com.example.rankup.gui;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Mina;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;

/**
 * Holograma em cima do ponto de teleporte de cada mina: nome, rank, tempo até o próximo reset e
 * quanto já foi minerado, atualizado a cada segundo. É um TextDisplay que não fica salvo no
 * mundo: some ao desligar e volta sozinho quando o chunk carrega de novo.
 */
public class Hologramas {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final RankupPlugin plugin;
    private final Map<String, TextDisplay> hologramas = new HashMap<>();

    public Hologramas(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ativados() {
        return plugin.getConfig().getBoolean("minas.holograma.ativado", true);
    }

    /** A cada segundo. */
    public void tick() {
        if (!ativados()) {
            removerTodos();
            return;
        }
        hologramas.keySet().removeIf(id -> {
            if (plugin.getMinaManager().get(id) != null) return false;
            TextDisplay velho = hologramas.get(id);
            if (velho != null) velho.remove();
            return true;
        });
        double altura = plugin.getConfig().getDouble("minas.holograma.altura", 2.5);
        for (Mina mina : plugin.getMinaManager().getMinas()) {
            Location local = mina.getLocalSpawn();
            if (local == null || !local.getWorld().isChunkLoaded(local.getBlockX() >> 4, local.getBlockZ() >> 4)) continue;
            local = local.clone().add(0, altura, 0);
            local.setYaw(0);
            local.setPitch(0);
            TextDisplay holo = hologramas.get(mina.getId());
            if (holo == null || !holo.isValid()) {
                holo = local.getWorld().spawn(local, TextDisplay.class, t -> {
                    t.setPersistent(false);
                    t.setBillboard(Display.Billboard.CENTER);
                    t.setSeeThrough(false);
                    t.setShadowed(true);
                });
                hologramas.put(mina.getId(), holo);
            } else if (holo.getLocation().distanceSquared(local) > 0.01) {
                holo.teleport(local);
            }
            holo.text(LEGADO.deserialize(texto(mina)));
        }
    }

    private String texto(Mina mina) {
        Rank minimo = plugin.getRankManager().getRank(mina.getRankMinimo());
        String reset = mina.isResetando() ? "§eResetando..."
                : mina.getProximoReset() == Long.MAX_VALUE ? "§7sem reset por tempo"
                : "§7Reset em §f" + Formato.tempo(mina.getProximoReset() - System.currentTimeMillis());
        return "§6§lMina " + mina.getId() + (minimo != null ? "\n§7Rank: " + minimo.nomeColorido() : "")
                + "\n" + reset + "\n§7Minerado: §f" + Menus.porcentagemMinerada(mina) + "%"
                + (mina.getResetPercentual() > 0 && mina.getResetPercentual() < 100 ? " §8(reseta com " + Math.round(mina.getResetPercentual()) + "%)" : "");
    }

    public void removerTodos() {
        hologramas.values().forEach(t -> {
            if (t.isValid()) t.remove();
        });
        hologramas.clear();
    }
}
