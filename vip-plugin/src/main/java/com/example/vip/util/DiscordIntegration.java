package com.example.vip.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manda anúncios pro Discord pelo DiscordPlugin (DiscordPlugin#enviar), por reflexão como as
 * outras integrações. Sem o DiscordPlugin, nada é enviado.
 */
public class DiscordIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public DiscordIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enviar(String categoria, String mensagem) {
        Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordPlugin");
        if (discord == null || !discord.isEnabled()) return;
        try {
            discord.getClass().getMethod("enviar", String.class, String.class).invoke(discord, categoria, mensagem);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("DiscordPlugin encontrado, mas não foi possível anunciar (versão incompatível?): " + e.getMessage());
                avisouFalha = true;
            }
        }
    }
}
