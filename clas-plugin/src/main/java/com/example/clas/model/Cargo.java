package com.example.clas.model;

public enum Cargo {
    LIDER("§6Líder"),
    OFICIAL("§eOficial"),
    MEMBRO("§7Membro");

    private final String exibicao;

    Cargo(String exibicao) {
        this.exibicao = exibicao;
    }

    public String getExibicao() {
        return exibicao;
    }

    /** Pode convidar e expulsar membros comuns. */
    public boolean podeGerenciar() {
        return this == LIDER || this == OFICIAL;
    }
}
