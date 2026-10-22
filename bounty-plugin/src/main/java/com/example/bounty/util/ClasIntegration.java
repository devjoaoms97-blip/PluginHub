package com.example.bounty.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Consulta o ClasPlugin por reflexão, sem dependência de compilação: se dois jogadores são do
 * mesmo clã (pra um colega não "cobrar" a recompensa do outro e dividir o dinheiro) e qual é o
 * clã de alguém (pras recompensas contra um clã inteiro). Sem o ClasPlugin, ninguém tem clã.
 */
public class ClasIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public ClasIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * ClasPlugin instalado e com a API de consulta por tag? Uma versão antiga (só com
     * {@code mesmoCla}) conta como indisponível — senão todo contrato pareceria de clã desfeito.
     */
    public boolean disponivel() {
        Plugin clas = Bukkit.getPluginManager().getPlugin("ClasPlugin");
        if (clas == null || !clas.isEnabled()) return false;
        try {
            clas.getClass().getMethod("tagExistente", String.class);
            clas.getClass().getMethod("tagDoCla", UUID.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public boolean mesmoCla(UUID a, UUID b) {
        return Boolean.TRUE.equals(chamar("mesmoCla", new Class<?>[]{UUID.class, UUID.class}, a, b));
    }

    /** Tag do clã do jogador, ou null se ele não tem clã. */
    public String tagDoCla(UUID jogador) {
        return chamar("tagDoCla", new Class<?>[]{UUID.class}, jogador) instanceof String tag ? tag : null;
    }

    /** A tag com as maiúsculas de quando o clã foi criado, ou null se não existe clã com essa tag. */
    public String tagExistente(String tag) {
        return chamar("tagExistente", new Class<?>[]{String.class}, tag) instanceof String existente ? existente : null;
    }

    private Object chamar(String metodo, Class<?>[] tipos, Object... args) {
        Plugin clas = Bukkit.getPluginManager().getPlugin("ClasPlugin");
        if (clas == null || !clas.isEnabled()) return null;
        try {
            return clas.getClass().getMethod(metodo, tipos).invoke(clas, args);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("ClasPlugin encontrado, mas não foi possível consultar os clãs (versão incompatível?): " + e.getMessage());
                avisouFalha = true;
            }
            return null;
        }
    }
}
