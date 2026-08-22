package com.example.boss.model;

public enum TipoGatilhoFase {
    /** Dispara uma única vez, ao cruzar aquele percentual de vida pela primeira vez. */
    PERCENTUAL_VIDA,
    /** Repete a cada X segundos, contados desde o spawn do boss. */
    INTERVALO_TEMPO
}
