package com.example.skills.skill;

public class LevelFormula {

    private final double base;
    private final double expoente;
    private final int nivelMaximo;

    public LevelFormula(double base, double expoente, int nivelMaximo) {
        this.base = base;
        this.expoente = expoente;
        this.nivelMaximo = nivelMaximo;
    }

    /** XP necessário para sair do nível atual e ir para o próximo. */
    public double xpParaProximoNivel(int nivelAtual) {
        return Math.round(base * Math.pow(nivelAtual + 1, expoente));
    }

    public int getNivelMaximo() {
        return nivelMaximo;
    }
}
