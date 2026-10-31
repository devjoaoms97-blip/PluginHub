package com.example.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Avisa o AjudaPlugin de uma venda do inventário pra loja (a etapa "vender-loja" do /tutorial; a venda
 * automática das minas não conta). Reflexão, sem dependência de compilação: sem o AjudaPlugin, não faz
 * nada.
 */
public class AjudaIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public AjudaIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void vendeu(Player jogador) {
        Plugin ajuda = Bukkit.getPluginManager().getPlugin("AjudaPlugin");
        if (ajuda == null || !ajuda.isEnabled()) return;
        try {
            ajuda.getClass().getMethod("registrarAcao", Player.class, String.class).invoke(ajuda, jogador, "vender-loja");
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("AjudaPlugin encontrado, mas não foi possível avisar da venda (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
        }
    }
}
