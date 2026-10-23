package com.example.terrenos.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Preço do rank atual do jogador (RankupPlugin#precoRankAtual), por reflexão como as outras
 * integrações. O imposto do terreno é uma % desse preço; sem o RankupPlugin, retorna -1 e o
 * imposto usa o valor fixo do config.
 */
public class RankupIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public RankupIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double precoRankAtual(UUID jogador) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return -1;
        try {
            Object valor = rankup.getClass().getMethod("precoRankAtual", UUID.class).invoke(rankup, jogador);
            return valor instanceof Number n && Double.isFinite(n.doubleValue()) ? Math.max(0, n.doubleValue()) : -1;
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("RankupPlugin encontrado, mas sem o preço do rank atual (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
            return -1;
        }
    }
}
