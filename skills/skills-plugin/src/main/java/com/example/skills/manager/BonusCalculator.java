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

    public double getChanceEmpurrao(java.util.UUID jogadorId) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        int nivel = dados.getNivel(Skill.TRIDENTE);
        double taxa = plugin.getConfig().getDouble("tridente.chance-empurrao-por-nivel", 0.002);
        return nivel * taxa;
    }

    public double getForcaEmpurrao() {
        return plugin.getConfig().getDouble("tridente.forca-empurrao", 1.4);
    }

    public double getChanceSangramento(java.util.UUID jogadorId) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        int nivel = dados.getNivel(Skill.LANCEIRO);
        double taxa = plugin.getConfig().getDouble("lanceiro.chance-sangramento-por-nivel", 0.002);
        return nivel * taxa;
    }

    public double getChanceAtordoarMarreteiro(java.util.UUID jogadorId) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        int nivel = dados.getNivel(Skill.MARRETEIRO);
        double taxa = plugin.getConfig().getDouble("marreteiro.chance-atordoar-por-nivel", 0.002);
        return nivel * taxa;
    }

    /** Chance (fração) de proc-ar o Crítico da skill, baseada no nível de Crítico do jogador. */
    public double getChanceCritico(java.util.UUID jogadorId) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        int nivel = dados.getNivel(Skill.CRITICO);
        double taxa = plugin.getConfig().getDouble("critico.chance-por-nivel", 0.002);
        return nivel * taxa;
    }

    /** Multiplicador de dano fixo aplicado quando o Crítico da skill proca. */
    public double getMultiplicadorCritico() {
        return plugin.getConfig().getDouble("critico.multiplicador-dano", 0.40);
    }
}
