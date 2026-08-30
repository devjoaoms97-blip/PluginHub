package com.example.boss.model;

import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Representa um "perfil" de boss configurado — um tipo de mob específico, com nome
 * próprio, fases (marcos fixos por % de vida ou tempo) e um arsenal de ataques que
 * disparam aleatoriamente durante a luta. Cada mob normal (zumbi, esqueleto, etc) e o
 * boss especial (Wither) são todos "perfis" independentes, carregados do config.yml.
 */
public class BossProfile {

    private final String id;
    private final EntityType mob;
    private final String nome;
    private final boolean especial;
    private final Double vidaMaximaOverride;
    private final Double danoAtaqueOverride;
    private final Double escalaOverride;
    private final List<FaseConfig> fases;
    private final List<FaseConfig> ataquesAleatorios;

    public BossProfile(String id, EntityType mob, String nome, boolean especial,
                        Double vidaMaximaOverride, Double danoAtaqueOverride, Double escalaOverride,
                        List<FaseConfig> fases, List<FaseConfig> ataquesAleatorios) {
        this.id = id;
        this.mob = mob;
        this.nome = nome;
        this.especial = especial;
        this.vidaMaximaOverride = vidaMaximaOverride;
        this.danoAtaqueOverride = danoAtaqueOverride;
        this.escalaOverride = escalaOverride;
        this.fases = fases;
        this.ataquesAleatorios = ataquesAleatorios;
    }

    public String getId() {
        return id;
    }

    public EntityType getMob() {
        return mob;
    }

    public String getNome() {
        return nome;
    }

    public boolean isEspecial() {
        return especial;
    }

    public Double getVidaMaximaOverride() {
        return vidaMaximaOverride;
    }

    public Double getDanoAtaqueOverride() {
        return danoAtaqueOverride;
    }

    public Double getEscalaOverride() {
        return escalaOverride;
    }

    public List<FaseConfig> getFases() {
        return fases;
    }

    public List<FaseConfig> getAtaquesAleatorios() {
        return ataquesAleatorios;
    }
}
