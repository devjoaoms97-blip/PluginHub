package com.example.gladiador.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Tag do clã do jogador (ClasPlugin#tagDoCla), por reflexão como o BountyPlugin faz. Sem o
 * ClasPlugin, ninguém tem clã e o modo por clã não abre.
 */
public class ClasIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public ClasIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean disponivel() {
        Plugin clas = Bukkit.getPluginManager().getPlugin("ClasPlugin");
        return clas != null && clas.isEnabled();
    }

    /** Tag do clã, ou null se não tem clã (ou não há ClasPlugin). */
    public String tagDoCla(UUID jogador) {
        Plugin clas = Bukkit.getPluginManager().getPlugin("ClasPlugin");
        if (clas == null || !clas.isEnabled()) return null;
        try {
            Object tag = clas.getClass().getMethod("tagDoCla", UUID.class).invoke(clas, jogador);
            return tag instanceof String s ? s : null;
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("ClasPlugin encontrado, mas sem a tag do clã (versão incompatível?): " + e.getMessage());
                avisouFalha = true;
            }
            return null;
        }
    }
}
