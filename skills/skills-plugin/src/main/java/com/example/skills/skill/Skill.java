package com.example.skills.skill;

import org.bukkit.Material;

public enum Skill {

    ESPADAS("Espadas", Material.DIAMOND_SWORD, "Dano causado com espadas", "espadas"),
    MACHADO("Machado", Material.DIAMOND_AXE, "Dano causado com machados", "machado"),
    ARQUEIRO("Arqueiro", Material.BOW, "Dano causado com arco/besta", "arqueiro"),
    PORRADEIRO("Porradeiro", Material.STICK, "Dano causado com a mão vazia", "porradeiro"),
    TRIDENTE("Tridente", Material.TRIDENT, "Dano causado com tridente", "tridente"),
    ESQUIVA("Esquiva", Material.FEATHER, "Tomar dano enquanto está se movendo", "esquiva"),
    DEFESA("Defesa", Material.IRON_CHESTPLATE, "Tomar dano e sobreviver", "defesa"),
    BLOQUEIO("Bloqueio", Material.SHIELD, "Bloquear ataques com escudo levantado", "bloqueio"),
    CRITICO("Crítico", Material.NETHER_STAR, "Acertar golpes críticos", "critico"),
    REGENERACAO("Regeneração", Material.GOLDEN_APPLE, "Tempo em combate recente", "regeneracao"),
    AGILIDADE("Agilidade", Material.SUGAR, "Se mover durante o combate", "agilidade");

    private final String nomeExibicao;
    private final Material icone;
    private final String descricaoXp;
    private final String chaveConfig;

    Skill(String nomeExibicao, Material icone, String descricaoXp, String chaveConfig) {
        this.nomeExibicao = nomeExibicao;
        this.icone = icone;
        this.descricaoXp = descricaoXp;
        this.chaveConfig = chaveConfig;
    }

    public String getNomeExibicao() {
        return nomeExibicao;
    }

    public Material getIcone() {
        return icone;
    }

    public String getDescricaoXp() {
        return descricaoXp;
    }

    /** Chave usada no config.yml (bonus-por-nivel.<chave>) e no arquivo de dados do jogador. */
    public String getChaveConfig() {
        return chaveConfig;
    }
}
