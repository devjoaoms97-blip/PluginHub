package com.example.bounty.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Pergunta ao ClasPlugin (por reflexão, sem dependência de compilação) se dois jogadores são
 * do mesmo clã — pra um colega de clã não "cobrar" a recompensa do outro e dividir o dinheiro.
 * Sem o ClasPlugin instalado, sempre responde que não.
 */
public class ClasIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public ClasIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean mesmoCla(UUID a, UUID b) {
        Plugin clas = Bukkit.getPluginManager().getPlugin("ClasPlugin");
        if (clas == null || !clas.isEnabled()) return false;
        try {
            Object resultado = clas.getClass().getMethod("mesmoCla", UUID.class, UUID.class).invoke(clas, a, b);
            return Boolean.TRUE.equals(resultado);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("ClasPlugin encontrado, mas não foi possível consultar os clãs (versão incompatível?): " + e.getMessage());
                avisouFalha = true;
            }
            return false;
        }
    }
}
