package com.example.missoes.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * As missões de um período (dia ou semana) de um jogador: id da missão -> progresso, e se o
 * bônus de "todas" já foi pago. {@code dia} é o primeiro dia do período.
 */
public class ProgressoJogador {

    private final LocalDate dia;
    private final Map<String, Integer> progresso = new LinkedHashMap<>();
    private boolean bonusPago;

    public ProgressoJogador(LocalDate dia) {
        this.dia = dia;
    }

    public LocalDate getDia() {
        return dia;
    }

    public Map<String, Integer> getProgresso() {
        return progresso;
    }

    public boolean isBonusPago() {
        return bonusPago;
    }

    public void setBonusPago(boolean bonusPago) {
        this.bonusPago = bonusPago;
    }
}
