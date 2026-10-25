package com.example.terrenos.mundo;

import com.example.terrenos.model.TerrenoId;
import org.bukkit.Material;

/**
 * Geometria do mundo de terrenos: uma grade infinita de células de {@code tamanho + rua} blocos.
 * Em cada eixo, os primeiros {@code tamanho} blocos da célula são do terreno e o resto é rua. A
 * faixa de rua encostada em cada terreno leva a borda de laje, e o miolo é o calçamento.
 * <p>
 * O terreno (0;0) vai de x=0..tamanho-1 e z=0..tamanho-1; o (1;0) começa em x=tamanho+rua.
 * Tanto o gerador quanto a limpeza de terreno usam {@link #blocoEm}, então o terreno limpo
 * volta exatamente a como foi gerado.
 */
public final class Grade {

    private final int tamanho;
    private final int rua;
    private final int alturaChao;
    private final int fundo;
    private final Material chao;
    private final Material terra;
    private final Material calcamento;
    private final Material borda;

    public Grade(int tamanho, int rua, int alturaChao, int fundo, Material chao, Material terra, Material calcamento, Material borda) {
        this.tamanho = Math.max(4, tamanho);
        this.rua = Math.max(3, rua);
        this.alturaChao = alturaChao;
        // sempre abaixo da pedra que fica embaixo da terra
        this.fundo = Math.min(fundo, alturaChao - 6);
        this.chao = chao;
        this.terra = terra;
        this.calcamento = calcamento;
        this.borda = borda;
    }

    public int celula() {
        return tamanho + rua;
    }

    public int getTamanho() {
        return tamanho;
    }

    public int getAlturaChao() {
        return alturaChao;
    }

    /** Altura da camada de bedrock do fundo (nunca abaixo do fundo do mundo). Abaixo dela é só ar. */
    public int fundo(int minY) {
        return Math.max(minY, fundo);
    }

    /** Posição dentro da célula (0..celula-1), também pra coordenada negativa. */
    private int local(int coord) {
        return Math.floorMod(coord, celula());
    }

    private boolean dentroDoTerreno(int coord) {
        return local(coord) < tamanho;
    }

    /** O terreno que contém esse x/z, ou null se for rua. */
    public TerrenoId terrenoEm(int x, int z) {
        if (!dentroDoTerreno(x) || !dentroDoTerreno(z)) return null;
        return new TerrenoId(Math.floorDiv(x, celula()), Math.floorDiv(z, celula()));
    }

    public int minX(TerrenoId id) {
        return id.x() * celula();
    }

    public int minZ(TerrenoId id) {
        return id.z() * celula();
    }

    /** Faixa de rua encostada num terreno (onde vai a laje), em um eixo. */
    private boolean faixaDaBorda(int coord) {
        int l = local(coord);
        return l == tamanho || l == celula() - 1;
    }

    /** Bloco da borda: a faixa encostada no terreno, sem fechar as esquinas das ruas. */
    private boolean ehBorda(int x, int z) {
        boolean bx = faixaDaBorda(x), bz = faixaDaBorda(z);
        if (bx && bz) return true; // canto do terreno
        return (bx && dentroDoTerreno(z)) || (bz && dentroDoTerreno(x));
    }

    /**
     * Bloco gerado nessa posição. O fundo ({@link #fundo}) é bedrock, com ar embaixo, pedra até 5
     * abaixo do chão, terra até o chão, e no chão a grama (terreno) ou o calçamento (rua). Um bloco
     * acima do chão vai a laje da borda.
     */
    public Material blocoEm(int x, int y, int z, int minY) {
        int base = fundo(minY);
        if (y < base) return Material.AIR;
        if (y == base) return Material.BEDROCK;
        if (y > alturaChao + 1) return Material.AIR;
        boolean terreno = dentroDoTerreno(x) && dentroDoTerreno(z);
        if (y == alturaChao + 1) return !terreno && ehBorda(x, z) ? borda : Material.AIR;
        if (y == alturaChao) return terreno ? chao : calcamento;
        if (y >= alturaChao - 4) return terreno ? terra : Material.STONE;
        return Material.STONE;
    }

    /** Ponto no meio da rua ao lado do canto do terreno, onde o jogador chega pelo /terreno home. */
    public double[] entrada(TerrenoId id) {
        double x = minX(id) + tamanho / 2.0;
        double z = minZ(id) - rua / 2.0;
        return new double[]{x, alturaChao + 1, z};
    }
}
