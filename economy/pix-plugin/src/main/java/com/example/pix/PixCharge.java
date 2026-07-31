package com.example.pix;

import java.util.UUID;

/**
 * Representa uma cobrança pendente: alguém (cobrador) pediu dinheiro a outra pessoa (cobrado).
 * Cada par (cobrador -> cobrado) só pode ter uma cobrança ativa por vez.
 */
public class PixCharge {

    private final UUID cobradorId;   // quem vai RECEBER o dinheiro
    private final UUID cobradoId;    // quem vai PAGAR
    private final double valor;
    private final String motivo;
    private final long criadoEm;

    public PixCharge(UUID cobradorId, UUID cobradoId, double valor, String motivo, long criadoEm) {
        this.cobradorId = cobradorId;
        this.cobradoId = cobradoId;
        this.valor = valor;
        this.motivo = motivo;
        this.criadoEm = criadoEm;
    }

    public UUID getCobradorId() {
        return cobradorId;
    }

    public UUID getCobradoId() {
        return cobradoId;
    }

    public double getValor() {
        return valor;
    }

    public String getMotivo() {
        return motivo;
    }

    public long getCriadoEm() {
        return criadoEm;
    }
}
