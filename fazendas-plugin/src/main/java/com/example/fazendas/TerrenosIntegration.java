package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Pergunta ao TerrenosPlugin em qual terreno fica um lugar e se o jogador pode construir nele (a
 * fazenda é colocada por clique, sem BlockPlaceEvent, então a proteção dos terrenos não passa por ela).
 * Por reflexão, sem dependência de compilação.
 */
public class TerrenosIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public TerrenosIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private Plugin terrenos() {
        Plugin terrenos = Bukkit.getPluginManager().getPlugin("TerrenosPlugin");
        return terrenos != null && terrenos.isEnabled() ? terrenos : null;
    }

    /** O TerrenosPlugin está instalado e ligado? */
    public boolean disponivel() {
        return terrenos() != null;
    }

    /** O id do terreno no lugar ("x;z"), ou null fora de qualquer terreno (ou sem o TerrenosPlugin). */
    public String terrenoEm(Location local) {
        Plugin terrenos = terrenos();
        if (terrenos == null || local == null) return null;
        try {
            Object manager = terrenos.getClass().getMethod("getTerrenoManager").invoke(terrenos);
            Object id = manager.getClass().getMethod("idEm", Location.class).invoke(manager, local);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException e) {
            avisar(e);
            return null;
        }
    }

    /**
     * O jogador pode construir nesse lugar? Dono do terreno, amigo com a permissão "construir" ou staff
     * dos terrenos. Sem o TerrenosPlugin, pode.
     */
    public boolean podeConstruir(Player jogador, Location local) {
        Plugin terrenos = terrenos();
        if (terrenos == null) return true;
        try {
            Object admin = terrenos.getClass().getMethod("isAdmin", Player.class).invoke(terrenos, jogador);
            if (Boolean.TRUE.equals(admin)) return true;
            Object manager = terrenos.getClass().getMethod("getTerrenoManager").invoke(terrenos);
            Object t = manager.getClass().getMethod("getEm", Location.class).invoke(manager, local);
            if (t == null || !Boolean.TRUE.equals(t.getClass().getMethod("temDono").invoke(t))) return false;
            Class<?> classe = t.getClass().getClassLoader().loadClass("com.example.terrenos.model.Permissao");
            Object construir = classe.getMethod("valueOf", String.class).invoke(null, "CONSTRUIR");
            return Boolean.TRUE.equals(t.getClass().getMethod("pode", UUID.class, classe).invoke(t, jogador.getUniqueId(), construir));
        } catch (ReflectiveOperationException e) {
            avisar(e);
            return false;
        }
    }

    private void avisar(Exception e) {
        if (!avisouFalha) {
            plugin.getLogger().warning("Não foi possível consultar o TerrenosPlugin (versão antiga?): " + e.getMessage());
            avisouFalha = true;
        }
    }
}
