package com.example.rankup.util;

import org.bukkit.Material;

import java.util.Locale;

public final class Formato {

    private Formato() {
    }

    public static String valor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    /** "3", "2,5": sem casas decimais quando é inteiro. */
    public static String numero(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", valor);
    }

    /** "45s", "12min 3s", "5h 20min". */
    public static String tempo(long ms) {
        long segundos = Math.max(0, ms / 1000);
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long seg = segundos % 60;
        if (horas > 0) return horas + "h " + minutos + "min";
        if (minutos > 0) return minutos + "min " + seg + "s";
        return seg + "s";
    }

    /** "+25%" pra um multiplicador de 1.25; "" pra 1.0. */
    public static String bonus(double multiplicador) {
        if (multiplicador <= 1.0) return "";
        return String.format(Locale.forLanguageTag("pt-BR"), "+%.0f%%", (multiplicador - 1) * 100);
    }

    public static String nomeMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String cores(String texto) {
        return texto == null ? "" : texto.replace('&', '§');
    }
}
