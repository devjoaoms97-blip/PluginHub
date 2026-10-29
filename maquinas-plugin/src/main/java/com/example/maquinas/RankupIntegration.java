package com.example.maquinas;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Pergunta ao RankupPlugin se o jogador já chegou num rank (cada máquina libera num rank).
 * Por reflexão, sem dependência de compilação. Sem o RankupPlugin, toda máquina vale pra todo mundo.
 */
public class RankupIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public RankupIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean temAcesso(UUID jogador, String rankMinimo) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return true;
        try {
            Object ranks = rankup.getClass().getMethod("getRankManager").invoke(rankup);
            return (boolean) ranks.getClass().getMethod("temAcesso", UUID.class, String.class).invoke(ranks, jogador, rankMinimo);
        } catch (ReflectiveOperationException | ClassCastException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("Não foi possível consultar o rank no RankupPlugin (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
            return true;
        }
    }

    /**
     * Pode usar (colocar e mexer) uma máquina desse rank? Tem o rank, ou já fez prestígio (já passou
     * pelo Rei, e o prestígio volta o rank pro começo). Sem o RankupPlugin, pode.
     */
    public boolean podeUsar(UUID jogador, String rankMinimo) {
        if (rankMinimo == null || temAcesso(jogador, rankMinimo)) return true;
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return true;
        try {
            Object nivel = rankup.getClass().getMethod("prestigioDe", UUID.class).invoke(rankup, jogador);
            return nivel instanceof Number n && n.intValue() > 0;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** O bônus de venda do rank (com prestígio), como na loja: 1.25 = vende 25% mais caro. Sem o RankupPlugin, 1. */
    public double multiplicadorVenda(UUID jogador) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return 1;
        try {
            Object v = rankup.getClass().getMethod("multiplicadorVenda", UUID.class).invoke(rankup, jogador);
            return v instanceof Number n && n.doubleValue() > 0 ? n.doubleValue() : 1;
        } catch (ReflectiveOperationException e) {
            return 1;
        }
    }
}
