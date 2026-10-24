package com.example.missoes.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Pergunta ao RankupPlugin se o jogador já tem um rank (pra só sortear missão de mina pra quem
 * pode minerar nela). Por reflexão, sem dependência de compilação. Sem o RankupPlugin, toda missão
 * vale pra todo mundo.
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
}
