package com.example.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Pergunta ao RankupPlugin o bônus de venda do rank do jogador (1.25 = vende 25% mais caro).
 * Mesma abordagem das outras integrações: reflexão, sem dependência de compilação — sem o
 * RankupPlugin, o multiplicador é sempre 1.
 */
public class RankupIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public RankupIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double multiplicador(Player jogador) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return 1.0;
        try {
            Object valor = rankup.getClass().getMethod("multiplicadorVenda", UUID.class).invoke(rankup, jogador.getUniqueId());
            double multiplicador = valor instanceof Number n ? n.doubleValue() : 1.0;
            return Double.isFinite(multiplicador) && multiplicador > 0 ? multiplicador : 1.0;
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("RankupPlugin encontrado, mas não foi possível ler o bônus de venda (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
            return 1.0;
        }
    }

    /** " (bônus do rank: +25%)" pra mostrar na mensagem de venda, ou "" sem bônus. */
    public static String textoBonus(double multiplicador) {
        if (multiplicador <= 1.0) return "";
        return String.format(Locale.forLanguageTag("pt-BR"), " §7(bônus do rank: §a+%.0f%%§7)", (multiplicador - 1) * 100);
    }
}
