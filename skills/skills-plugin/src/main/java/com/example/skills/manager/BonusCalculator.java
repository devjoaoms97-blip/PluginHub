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

    /**
     * O nível da skill já com o prestígio: cada prestígio vale {@code prestigio.bonus-por-prestigio}
     * a mais em tudo o que a skill dá (bônus e chances). É o "bônus permanente" do reset no 50.
     */
    private double nivelComPrestigio(java.util.UUID jogadorId, Skill skill) {
        PlayerSkillData dados = dataManager.get(jogadorId);
        double fator = 1 + dados.getPrestigio(skill) * plugin.getConfig().getDouble("prestigio.bonus-por-prestigio", 0.10);
        return dados.getNivel(skill) * fator;
    }

    /** Retorna a fração de bônus (ex: 0.25 = +25%) de uma skill para um jogador. */
    public double getBonus(java.util.UUID jogadorId, Skill skill) {
        double taxa = plugin.getConfig().getDouble("bonus-por-nivel." + skill.getChaveConfig(), 0.005);
        return nivelComPrestigio(jogadorId, skill) * taxa;
    }

    public double getChanceAtordoar(java.util.UUID jogadorId) {
        double taxa = plugin.getConfig().getDouble("porradeiro.chance-atordoar-por-nivel", 0.002);
        return nivelComPrestigio(jogadorId, Skill.PORRADEIRO) * taxa;
    }

    public double getChanceEmpurrao(java.util.UUID jogadorId) {
        double taxa = plugin.getConfig().getDouble("tridente.chance-empurrao-por-nivel", 0.002);
        return nivelComPrestigio(jogadorId, Skill.TRIDENTE) * taxa;
    }

    public double getForcaEmpurrao() {
        return plugin.getConfig().getDouble("tridente.forca-empurrao", 1.4);
    }

    public double getChanceSangramento(java.util.UUID jogadorId) {
        double taxa = plugin.getConfig().getDouble("lanceiro.chance-sangramento-por-nivel", 0.002);
        return nivelComPrestigio(jogadorId, Skill.LANCEIRO) * taxa;
    }

    public double getChanceAtordoarMarreteiro(java.util.UUID jogadorId) {
        double taxa = plugin.getConfig().getDouble("marreteiro.chance-atordoar-por-nivel", 0.002);
        return nivelComPrestigio(jogadorId, Skill.MARRETEIRO) * taxa;
    }

    /** Chance (fração) de proc-ar o Crítico da skill, baseada no nível de Crítico do jogador. */
    public double getChanceCritico(java.util.UUID jogadorId) {
        double taxa = plugin.getConfig().getDouble("critico.chance-por-nivel", 0.002);
        return nivelComPrestigio(jogadorId, Skill.CRITICO) * taxa;
    }

    /** Multiplicador de dano fixo aplicado quando o Crítico da skill proca. */
    public double getMultiplicadorCritico() {
        return plugin.getConfig().getDouble("critico.multiplicador-dano", 0.40);
    }
}
