package com.example.pix;

import java.util.UUID;

public class TransacaoRegistro {

    private final UUID de;
    private final UUID para;
    private final double valor;
    private final String tipo; // ex: "Pagamento", "Cobrança aceita"
    private final long data;

    public TransacaoRegistro(UUID de, UUID para, double valor, String tipo, long data) {
        this.de = de;
        this.para = para;
        this.valor = valor;
        this.tipo = tipo;
        this.data = data;
    }

    public UUID getDe() {
        return de;
    }

    public UUID getPara() {
        return para;
    }

    public double getValor() {
        return valor;
    }

    public String getTipo() {
        return tipo;
    }

    public long getData() {
        return data;
    }
}
