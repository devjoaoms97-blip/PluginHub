package com.example.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Avisa o MissoesPlugin do que foi vendido pra loja (missão de vender) e do que foi comprado
 * dela (pra revenda do que acabou de comprar não contar na missão). Mesma abordagem da
 * integração com o Pix: reflexão, sem dependência de compilação — sem o MissoesPlugin, não faz nada.
 */
public class MissoesIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public MissoesIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void vendeu(Player jogador, Material material, int quantidade) {
        invocar("registrarProgresso", new Class<?>[]{Player.class, String.class, String.class, int.class},
                jogador, "VENDER", material.name(), quantidade);
    }

    public void comprou(Player jogador, Material material, int quantidade) {
        invocar("registrarCompraNaLoja", new Class<?>[]{Player.class, String.class, int.class},
                jogador, material.name(), quantidade);
    }

    private void invocar(String metodo, Class<?>[] tipos, Object... args) {
        Plugin missoes = Bukkit.getPluginManager().getPlugin("MissoesPlugin");
        if (missoes == null || !missoes.isEnabled()) return;
        try {
            missoes.getClass().getMethod(metodo, tipos).invoke(missoes, args);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("MissoesPlugin encontrado, mas não foi possível avisar das vendas (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
        }
    }
}
