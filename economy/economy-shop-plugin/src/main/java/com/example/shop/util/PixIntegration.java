package com.example.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Registra as transações da loja no histórico do PixPlugin (/pix historico), pra unificar
 * o extrato do jogador num só lugar. Usa reflexão de propósito — assim o EconomyShopPlugin
 * continua funcionando normalmente mesmo se o PixPlugin não estiver instalado, sem precisar
 * de uma dependência de compilação entre os dois projetos.
 */
public class PixIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public PixIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registrarTransacao(Player jogador, double valor, boolean jogadorRecebeu, String tipo) {
        if (!plugin.getConfig().getBoolean("integracao.pix", true)) return;

        try {
            Plugin pix = Bukkit.getPluginManager().getPlugin("PixPlugin");
            if (pix == null || !pix.isEnabled()) return;

            Object chargeManager = pix.getClass().getMethod("getChargeManager").invoke(pix);
            chargeManager.getClass()
                    .getMethod("registrarTransacaoComLoja", UUID.class, double.class, boolean.class, String.class)
                    .invoke(chargeManager, jogador.getUniqueId(), valor, jogadorRecebeu, tipo);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível integrar o histórico (versão incompatível?): " + e.getMessage());
                avisouFalha = true; // avisa só uma vez, não spamma o console a cada venda
            }
        }
    }
}
