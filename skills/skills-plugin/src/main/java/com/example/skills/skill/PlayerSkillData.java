package com.example.skills.skill;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSkillData {

    private final UUID jogadorId;
    private final Map<Skill, Integer> niveis = new EnumMap<>(Skill.class);
    private final Map<Skill, Double> xpAtual = new EnumMap<>(Skill.class);

    public PlayerSkillData(UUID jogadorId) {
        this.jogadorId = jogadorId;
        for (Skill skill : Skill.values()) {
            niveis.put(skill, 1);
            xpAtual.put(skill, 0.0);
        }
    }

    public UUID getJogadorId() {
        return jogadorId;
    }

    public int getNivel(Skill skill) {
        return niveis.getOrDefault(skill, 1);
    }

    public void setNivel(Skill skill, int nivel) {
        niveis.put(skill, nivel);
    }

    public double getXp(Skill skill) {
        return xpAtual.getOrDefault(skill, 0.0);
    }

    public void setXp(Skill skill, double xp) {
        xpAtual.put(skill, xp);
    }
}
