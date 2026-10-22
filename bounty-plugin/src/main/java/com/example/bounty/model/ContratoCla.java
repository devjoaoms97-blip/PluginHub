package com.example.bounty.model;

import java.util.UUID;

/**
 * Recompensa contra um clã inteiro: "R$ X por abate de qualquer membro do clã TAG, até N abates".
 * O total ({@code valorPorAbate * abatesRestantes}) já está RETIDO, sem a taxa. Cada abate válido
 * paga {@code valorPorAbate} pro matador; o que sobrar quando expirar (ou se o clã for desfeito)
 * volta pra quem contratou.
 */
public class ContratoCla {

    private final UUID id;
    private final String tag;
    private final UUID contratante;
    private final String nomeContratante;
    private final double valorPorAbate;
    private int abatesRestantes;
    private final long criadoEm;

    public ContratoCla(UUID id, String tag, UUID contratante, String nomeContratante, double valorPorAbate,
                       int abatesRestantes, long criadoEm) {
        this.id = id;
        this.tag = tag;
        this.contratante = contratante;
        this.nomeContratante = nomeContratante;
        this.valorPorAbate = valorPorAbate;
        this.abatesRestantes = abatesRestantes;
        this.criadoEm = criadoEm;
    }

    public double getRestante() {
        return valorPorAbate * abatesRestantes;
    }

    public UUID getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public UUID getContratante() {
        return contratante;
    }

    public String getNomeContratante() {
        return nomeContratante;
    }

    public double getValorPorAbate() {
        return valorPorAbate;
    }

    public int getAbatesRestantes() {
        return abatesRestantes;
    }

    public void setAbatesRestantes(int abatesRestantes) {
        this.abatesRestantes = abatesRestantes;
    }

    public long getCriadoEm() {
        return criadoEm;
    }
}
