package com.example.boss.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Dá XP de skill a quem participou do boss, chamando o XpManager do SkillsPlugin por reflexão
 * (mesma abordagem da integração com o Pix nos outros plugins): sem dependência de compilação,
 * e se o SkillsPlugin não estiver instalado o XP é só ignorado.
 */
public class SkillsIntegration {

    private final JavaPlugin plugin;
    private boolean avisouFalha = false;

    public SkillsIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean disponivel() {
        Plugin skills = Bukkit.getPluginManager().getPlugin("SkillsPlugin");
        return skills != null && skills.isEnabled();
    }

    /** Retorna true se o XP foi entregue. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean darXp(Player jogador, String nomeSkill, double quantidade) {
        if (nomeSkill == null || quantidade <= 0) return false;
        Plugin skills = Bukkit.getPluginManager().getPlugin("SkillsPlugin");
        if (skills == null || !skills.isEnabled()) return false;

        try {
            Class<? extends Enum> classeSkill = (Class<? extends Enum>) skills.getClass().getClassLoader()
                    .loadClass("com.example.skills.skill.Skill");
            Object skill = Enum.valueOf(classeSkill, nomeSkill.toUpperCase(Locale.ROOT));

            Object xpManager = skills.getClass().getMethod("getXpManager").invoke(skills);
            Method adicionar = xpManager.getClass().getMethod("adicionarXp", Player.class, classeSkill, double.class);
            adicionar.invoke(xpManager, jogador, skill, quantidade);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            if (!avisouFalha) {
                plugin.getLogger().warning("Não foi possível dar XP de skill pelo SkillsPlugin (skill \"" + nomeSkill
                        + "\" inválida ou versão incompatível?): " + e.getMessage());
                avisouFalha = true;
            }
            return false;
        }
    }
}
