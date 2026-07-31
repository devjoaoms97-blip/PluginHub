package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.LevelFormula;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import org.bukkit.entity.Player;

public class XpManager {

    private final SkillsPlugin plugin;
    private final SkillDataManager dataManager;
    private final RewardManager rewardManager;
    private final LevelFormula formula;

    public XpManager(SkillsPlugin plugin, SkillDataManager dataManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;

        double base = plugin.getConfig().getDouble("xp.base", 50);
        double expoente = plugin.getConfig().getDouble("xp.expoente", 1.6);
        int nivelMaximo = plugin.getConfig().getInt("nivel-maximo", 50);
        this.formula = new LevelFormula(base, expoente, nivelMaximo);
    }

    public void adicionarXp(Player jogador, Skill skill, double quantidade) {
        if (quantidade <= 0) return;

        PlayerSkillData dados = dataManager.get(jogador.getUniqueId());
        int nivelAtual = dados.getNivel(skill);

        if (nivelAtual >= formula.getNivelMaximo()) {
            return; // já no nível máximo, não acumula mais XP
        }

        double xpAtual = dados.getXp(skill) + quantidade;

        // loop, pra caso um único golpe/tick dê XP suficiente pra subir mais de um nível de uma vez
        while (nivelAtual < formula.getNivelMaximo()) {
            double xpNecessario = formula.xpParaProximoNivel(nivelAtual);
            if (xpAtual < xpNecessario) break;

            xpAtual -= xpNecessario;
            nivelAtual++;
            dados.setNivel(skill, nivelAtual);
            rewardManager.aoSubirDeNivel(jogador, skill, nivelAtual);
        }

        if (nivelAtual >= formula.getNivelMaximo()) {
            xpAtual = 0;
        }

        dados.setXp(skill, xpAtual);
    }

    public LevelFormula getFormula() {
        return formula;
    }
}
