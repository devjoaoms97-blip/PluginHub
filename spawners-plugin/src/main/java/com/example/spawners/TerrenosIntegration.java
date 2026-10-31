package com.example.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pergunta ao TerrenosPlugin em qual terreno fica um lugar (o limite de spawners é por terreno). Por
 * reflexão, sem dependência de compilação.
 */
public class TerrenosIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public TerrenosIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** O TerrenosPlugin está instalado e ligado? */
    public boolean disponivel() {
        Plugin terrenos = Bukkit.getPluginManager().getPlugin("TerrenosPlugin");
        return terrenos != null && terrenos.isEnabled();
    }

    /** O id do terreno no lugar ("x;z"), ou null fora de qualquer terreno (ou sem o TerrenosPlugin). */
    public String terrenoEm(Location local) {
        Plugin terrenos = Bukkit.getPluginManager().getPlugin("TerrenosPlugin");
        if (terrenos == null || !terrenos.isEnabled() || local == null) return null;
        try {
            Object manager = terrenos.getClass().getMethod("getTerrenoManager").invoke(terrenos);
            Object id = manager.getClass().getMethod("idEm", Location.class).invoke(manager, local);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("Não foi possível consultar o terreno no TerrenosPlugin (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
            return null;
        }
    }
}
