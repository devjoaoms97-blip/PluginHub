package com.example.boss.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gera um código de VIP pelo CodeManager do VipPlugin, por reflexão (sem dependência de
 * compilação). Sem o VipPlugin, ou com tier inválido, devolve null.
 */
public class VipIntegration {

    private final JavaPlugin plugin;

    public VipIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** O código gerado (1 uso), ou null se não deu. */
    public String gerarCodigo(String tier, int dias, int validadeDias) {
        Plugin vip = Bukkit.getPluginManager().getPlugin("VipPlugin");
        if (vip == null || !vip.isEnabled()) return null;
        try {
            ClassLoader carregador = vip.getClass().getClassLoader();
            Class<?> classeTier = carregador.loadClass("com.example.vip.model.VipTier");
            Object objTier = classeTier.getMethod("fromString", String.class).invoke(null, tier);
            if (objTier == null) {
                plugin.getLogger().warning("Tier de VIP inválido no config (recompensas.codigo-vip.tier): " + tier);
                return null;
            }
            Object codeManager = vip.getClass().getMethod("getCodeManager").invoke(vip);
            Object codigo = codeManager.getClass()
                    .getMethod("gerar", classeTier, int.class, int.class, String.class, int.class)
                    .invoke(codeManager, objTier, dias, 1, "BossPlugin", validadeDias);
            return (String) codigo.getClass().getMethod("getCodigo").invoke(codigo);
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Não foi possível gerar o código de VIP pelo VipPlugin: " + e.getMessage());
            return null;
        }
    }
}
