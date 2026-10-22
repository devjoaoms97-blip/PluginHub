package com.example.barracas.model;

import java.util.UUID;

/**
 * Leilão de uma barraca especial. O maior lance fica RETIDO (já saiu da conta do licitante)
 * até o leilão acabar — quem é superado recebe o valor de volta na hora.
 */
public class Leilao {

    private long terminaEm;
    private double lance;          // 0 = ainda sem lances
    private UUID licitante;        // null = ainda sem lances
    private String nomeLicitante;

    public Leilao(long terminaEm) {
        this.terminaEm = terminaEm;
    }

    public Leilao(long terminaEm, double lance, UUID licitante, String nomeLicitante) {
        this.terminaEm = terminaEm;
        this.lance = lance;
        this.licitante = licitante;
        this.nomeLicitante = nomeLicitante;
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
}
