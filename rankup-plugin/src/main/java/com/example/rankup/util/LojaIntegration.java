package com.example.rankup.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Venda automática pela loja (EconomyShopPlugin#venderAutomaticamente), por reflexão como as
 * outras integrações. Sem a loja, nada é vendido e o minério vai pro inventário.
 */
public class LojaIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public LojaIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** True se a loja comprou o stack inteiro. */
    public boolean vender(Player jogador, ItemStack stack) {
        Plugin loja = Bukkit.getPluginManager().getPlugin("EconomyShopPlugin");
        if (loja == null || !loja.isEnabled()) return false;
        try {
            Object vendeu = loja.getClass().getMethod("venderAutomaticamente", Player.class, ItemStack.class)
                    .invoke(loja, jogador, stack);
            return Boolean.TRUE.equals(vendeu);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("EconomyShopPlugin encontrado, mas sem a venda automática (versão antiga?): " + e.getMessage());
                avisouFalha = true;
            }
            return false;
        }
    }
}
