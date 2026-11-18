package com.example.boss.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * O modelo 3D do boss (2026-11-17), do FreeMinecraftModels, por reflexão como na montaria: sem o FMM (ou sem
 * o modelo), o boss fica com a cara de mob comum. O mob fica invisível e o modelo vai por cima, no tamanho
 * do boss ({@code setScaleModifier} com a escala dele: o FMM não lê o atributo de escala da entidade). As
 * animações idle, walk, attack, damaged, death e spawn o FMM toca sozinho. Os modelos saem do
 * {@code tools/pacote/bosses.py}.
 */
public class ModeloFmm {

    private final JavaPlugin plugin;
    private boolean avisouFalha;

    public ModeloFmm(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Põe o modelo no boss; devolve o objeto do FMM (pra tirar depois) ou null. */
    public Object vestir(LivingEntity boss, String modelo, double escala) {
        Plugin fmm = Bukkit.getPluginManager().getPlugin("FreeMinecraftModels");
        if (fmm == null || !fmm.isEnabled()) return null;
        try {
            Class<?> dinamica = Class.forName("com.magmaguy.freeminecraftmodels.customentity.DynamicEntity", true, fmm.getClass().getClassLoader());
            Object m = dinamica.getMethod("createWithInvisibility", String.class, LivingEntity.class).invoke(null, modelo, boss);
            if (m == null) {
                plugin.getLogger().warning("O FreeMinecraftModels não tem o modelo '" + modelo + "' (bosses.<id>.fmm-modelo).");
                return null;
            }
            dinamica.getMethod("setScaleModifier", double.class).invoke(m, escala);
            dinamica.getMethod("setDamagesOnContact", boolean.class).invoke(m, false);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            avisar("Não foi possível pôr o modelo do FreeMinecraftModels no boss (versão diferente?): " + e);
            return null;
        }
    }

    /** Tira o modelo; {@code morreu} toca a animação de morte antes. */
    public void tirar(Object modelo, boolean morreu) {
        if (modelo == null) return;
        try {
            Object removido = modelo.getClass().getMethod("isRemoved").invoke(modelo);
            if (Boolean.TRUE.equals(removido)) return;
            modelo.getClass().getMethod(morreu ? "removeWithDeathAnimation" : "remove").invoke(modelo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            avisar("Não foi possível tirar o modelo do FreeMinecraftModels do boss: " + e);
        }
    }

    /** O boss está com o modelo do FMM? (pros testes e o /boss status) */
    public boolean temModelo(LivingEntity boss) {
        Plugin fmm = Bukkit.getPluginManager().getPlugin("FreeMinecraftModels");
        if (fmm == null || !fmm.isEnabled() || boss == null) return false;
        try {
            Class<?> dinamica = Class.forName("com.magmaguy.freeminecraftmodels.customentity.DynamicEntity", true, fmm.getClass().getClassLoader());
            return Boolean.TRUE.equals(dinamica.getMethod("isDynamicEntity", org.bukkit.entity.Entity.class).invoke(null, boss));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private void avisar(String texto) {
        if (avisouFalha) return;
        avisouFalha = true;
        plugin.getLogger().warning(texto);
    }
}
