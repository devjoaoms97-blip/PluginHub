package com.example.terrenos.model;

import java.util.UUID;

/**
 * Leilão de imóvel: o terreno de quem deixou o imposto atrasar demais vai a leilão com tudo o que
 * tem dentro. O maior lance fica RETIDO (já saiu da conta) até o fim; quem é superado recebe de
 * volta na hora. O dono antigo só pode dar lance depois de pagar a dívida de imposto.
 */
public class Leilao {

    private long terminaEm;
    private double lance;            // 0 = ainda sem lances
    private UUID licitante;          // null = ainda sem lances
    private String nomeLicitante;
    private final UUID donoAnterior;
    private final String nomeDonoAnterior;
    private final double divida;
    private boolean dividaPaga;

    public Leilao(long terminaEm, UUID donoAnterior, String nomeDonoAnterior, double divida) {
        this.terminaEm = terminaEm;
        this.donoAnterior = donoAnterior;
        this.nomeDonoAnterior = nomeDonoAnterior;
        this.divida = divida;
    }

    public long getTerminaEm() {
        return terminaEm;
    }

    public void setTerminaEm(long terminaEm) {
        this.terminaEm = terminaEm;
    }

    public double getLance() {
        return lance;
    }

    public UUID getLicitante() {
        return licitante;
    }

    public String getNomeLicitante() {
        return nomeLicitante;
    }

    public boolean temLance() {
        return licitante != null;
    }

    public void registrarLance(UUID licitante, String nome, double valor) {
        this.licitante = licitante;
        this.nomeLicitante = nome;
        this.lance = valor;
    }

    public UUID getDonoAnterior() {
        return donoAnterior;
    }

    public String getNomeDonoAnterior() {
        return nomeDonoAnterior;
    }

    public double getDivida() {
        return divida;
    }

    public boolean isDividaPaga() {
        return dividaPaga;
    }

    public void setDividaPaga(boolean dividaPaga) {
        this.dividaPaga = dividaPaga;
    }
}
