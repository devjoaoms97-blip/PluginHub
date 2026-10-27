package com.example.vip.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Registra o que mexe no dinheiro no extrato (o histórico do PixPlugin, que o Bancário entrega). Por
 * reflexão, sem dependência de compilação: sem o PixPlugin, não registra nada.
 */
public class PixIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha;

    public PixIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Entre dois jogadores; {@code taxa} é a parte do valor que sumiu (aparece no extrato). */
    public void entreJogadores(UUID de, UUID para, double valor, String tipo, double taxa) {
        invocar("registrarTransacao", new Class<?>[]{UUID.class, UUID.class, double.class, String.class, double.class}, de, para, valor, tipo, taxa);
    }

    /** Entre o jogador e algo que não é jogador (o boss, o VIP, os achados e perdidos). */
    public void comEntidade(UUID jogador, String entidade, double valor, boolean jogadorRecebeu, String tipo) {
        invocar("registrarTransacaoComEntidade", new Class<?>[]{UUID.class, String.class, double.class, boolean.class, String.class},
                jogador, entidade, valor, jogadorRecebeu, tipo);
    }

    private void invocar(String metodo, Class<?>[] tipos, Object... args) {
        Plugin pix = Bukkit.getPluginManager().getPlugin("PixPlugin");
        if (pix == null || !pix.isEnabled()) return;
        try {
            Object charges = pix.getClass().getMethod("getChargeManager").invoke(pix);
            charges.getClass().getMethod(metodo, tipos).invoke(charges, args);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível registrar no extrato (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
        }
    }
}
