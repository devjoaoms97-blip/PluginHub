package com.example.missoes.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Diárias zeram à meia-noite; semanais, na meia-noite de domingo pra segunda (horário do servidor). */
public enum Periodo {
    DIARIA,
    SEMANAL;

    /** Primeiro dia do período que contém {@code dia} — é o que identifica "as missões de agora". */
    public LocalDate inicio(LocalDate dia) {
        return this == DIARIA ? dia : dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Primeiro dia do próximo período (quando as missões trocam). */
    public LocalDate proximoInicio(LocalDate dia) {
        return this == DIARIA ? dia.plusDays(1) : inicio(dia).plusWeeks(1);
    }
}
