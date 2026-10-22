package com.example.clas.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Registra a criação de clã e as movimentações do banco do clã no histórico do PixPlugin
 * (/pix historico). Mesma abordagem dos outros plugins: reflexão, sem dependência de compilação
 * — se o Pix não estiver instalado (ou for de uma versão sem suporte), não registra nada.
 */
public class PixIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public PixIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Pagamento entre o jogador e o servidor (custo de criar o clã) — aparece como "Loja do Servidor". */
    public void registrarComServidor(UUID jogador, double valor, boolean jogadorRecebeu, String tipo) {
        invocar("registrarTransacaoComLoja", new Class<?>[]{UUID.class, double.class, boolean.class, String.class},
                jogador, valor, jogadorRecebeu, tipo);
    }

    /** Depósito/saque no banco do clã — no extrato aparece o nome do clã (ex: "Clã [ABC]"). */
    public void registrarComCla(UUID jogador, String nomeCla, double valor, boolean jogadorRecebeu, String tipo) {
        invocar("registrarTransacaoComEntidade", new Class<?>[]{UUID.class, String.class, double.class, boolean.class, String.class},
                jogador, nomeCla, valor, jogadorRecebeu, tipo);
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
                plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível integrar o histórico (versão antiga do Pix?): " + e.getMessage());
                avisouFalha = true; // avisa só uma vez, não spamma o console
            }
        }
    }
}
