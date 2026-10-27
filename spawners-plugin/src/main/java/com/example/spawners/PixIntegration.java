package com.example.spawners;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Registra a compra de spawners no histórico do PixPlugin (extrato do Bancário).
 * Mesma abordagem do EconomyShopPlugin: reflexão, sem dependência de compilação — se o Pix não
 * estiver instalado (ou for de uma versão muito diferente), simplesmente não registra nada.
 */
public class PixIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public PixIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Pagamento entre o jogador e o servidor (recompensa): aparece como "Loja do Servidor". */
    public void registrarComServidor(UUID jogador, double valor, boolean jogadorRecebeu, String tipo) {
        invocar("registrarTransacaoComLoja", new Class<?>[]{UUID.class, double.class, boolean.class, String.class},
                jogador, valor, jogadorRecebeu, tipo);
    }

    private void invocar(String metodo, Class<?>[] tipos, Object... args) {
        if (!plugin.getConfig().getBoolean("integracao.pix", true)) return;

        try {
            Plugin pix = Bukkit.getPluginManager().getPlugin("PixPlugin");
            if (pix == null || !pix.isEnabled()) return;

            Object chargeManager = pix.getClass().getMethod("getChargeManager").invoke(pix);
            chargeManager.getClass().getMethod(metodo, tipos).invoke(chargeManager, args);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível integrar o histórico (versão incompatível?): " + e.getMessage());
                avisouFalha = true; // avisa só uma vez, não spamma o console
            }
        }
    }
}
