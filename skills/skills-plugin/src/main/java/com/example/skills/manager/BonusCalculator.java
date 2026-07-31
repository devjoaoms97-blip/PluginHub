package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;

public class BonusCalculator {

    private final SkillsPlugin plugin;
    private final SkillDataManager dataManager;

    public BonusCalculator(SkillsPlugin plugin, SkillDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    /** Retorna a fração de bônus (ex: 0.25 = +25%) de uma skill para um jogador. */
    public double getBonus(java.util.UUID jogadorId, Skill skill) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        int nivel = dados.getNivel(skill);
        double taxa = plugin.getConfig().getDouble("bonus-por-nivel." + skill.getChaveConfig(), 0.0);
        return nivel * taxa;
    }

    public double getChanceAtordoar(java.util.UUID jogadorId) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        int nivel = dados.getNivel(Skill.PORRADEIRO);
        double taxa = plugin.getConfig().getDouble("porradeiro.chance-atordoar-por-nivel", 0.002);
        return nivel * taxa;
    }
}
