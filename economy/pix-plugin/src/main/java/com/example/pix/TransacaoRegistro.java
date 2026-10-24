package com.example.pix;

import java.util.UUID;

public class TransacaoRegistro {

    private final UUID de;
    private final UUID para;
    private final double valor;
    private final String tipo; // ex: "Pagamento", "Cobrança aceita"
    private final long data;
    // Nome do "outro lado" quando ele não é um jogador (ex: "Clã [ABC]"); null = jogador ou a loja
    private final String nomeEntidade;
    // Taxa descontada nessa transação (0 = sem taxa); quem recebeu ficou com valor - taxa
    private final double taxa;

    public TransacaoRegistro(UUID de, UUID para, double valor, String tipo, long data) {
        this(de, para, valor, tipo, data, null);
    }

    public TransacaoRegistro(UUID de, UUID para, double valor, String tipo, long data, String nomeEntidade) {
        this(de, para, valor, tipo, data, nomeEntidade, 0);
    }

    public TransacaoRegistro(UUID de, UUID para, double valor, String tipo, long data, String nomeEntidade, double taxa) {
        this.taxa = taxa;
        this.de = de;
        this.para = para;
        this.valor = valor;
        this.tipo = tipo;
        this.data = data;
        this.nomeEntidade = nomeEntidade;
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

    public double getTaxa() {
        return taxa;
    }

    public String getNomeEntidade() {
        return nomeEntidade;
    }
}
