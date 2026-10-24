package com.example.rankup.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Avisa o MissoesPlugin do progresso de rankup e de mineração nas minas (tipo de missão subir_rank e minerar_mina). Por reflexão, sem
 * dependência de compilação: sem o MissoesPlugin, não faz nada.
 */
public class MissoesIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public MissoesIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void progresso(Player jogador, String tipo, String alvo, int quantidade) {
        Plugin missoes = Bukkit.getPluginManager().getPlugin("MissoesPlugin");
        if (missoes == null || !missoes.isEnabled() || jogador == null || quantidade <= 0) return;
        try {
            missoes.getClass().getMethod("registrarProgresso", Player.class, String.class, String.class, int.class)
                    .invoke(missoes, jogador, tipo, alvo, quantidade);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("MissoesPlugin encontrado, mas não foi possível avisar do progresso (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
        }
    }
}
