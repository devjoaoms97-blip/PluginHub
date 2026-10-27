package com.example.rankup.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Registra o pagamento do rankup no histórico do PixPlugin (extrato do Bancário), como "Loja do
 * Servidor". Mesma abordagem dos outros plugins: reflexão, sem dependência de compilação — se o
 * Pix não estiver instalado (ou for de uma versão sem suporte), não registra nada.
 */
public class PixIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public PixIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registrarComServidor(UUID jogador, double valor, boolean jogadorRecebeu, String tipo) {
        if (!plugin.getConfig().getBoolean("integracao.pix", true)) return;

        try {
            Plugin pix = Bukkit.getPluginManager().getPlugin("PixPlugin");
            if (pix == null || !pix.isEnabled()) return;

            Object chargeManager = pix.getClass().getMethod("getChargeManager").invoke(pix);
            chargeManager.getClass()
                    .getMethod("registrarTransacaoComLoja", UUID.class, double.class, boolean.class, String.class)
                    .invoke(chargeManager, jogador, valor, jogadorRecebeu, tipo);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível integrar o histórico (versão antiga do Pix?): " + e.getMessage());
                avisouFalha = true; // avisa só uma vez, não spamma o console
            }
        }
    }
}
