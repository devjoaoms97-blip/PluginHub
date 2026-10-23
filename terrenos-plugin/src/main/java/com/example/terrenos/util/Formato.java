package com.example.terrenos.util;

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

    /** "45s", "12min 3s", "5h 20min", "3d 4h". */
    public static String tempo(long ms) {
        long segundos = Math.max(0, ms / 1000);
        long dias = segundos / 86400;
        if (dias > 0) return dias + "d " + (segundos % 86400) / 3600 + "h";
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long seg = segundos % 60;
        if (horas > 0) return horas + "h " + minutos + "min";
        if (minutos > 0) return minutos + "min " + seg + "s";
        return seg + "s";
    }

    /**
     * Lê um valor digitado: "150", "R$ 150", "150,50", "1.500,50". Com vírgula, ela é
     * o separador decimal (formato brasileiro, pontos viram separador de milhar); sem vírgula,
     * o ponto é decimal. Retorna -1 se não der pra ler (inclusive NaN/Infinity, que o
     * parseDouble aceitaria e passariam por qualquer checagem de maior que zero).
     */
    public static double lerPreco(String texto) {
        if (texto == null) return -1;
        String limpo = texto.replace("R$", "").replace(" ", "").trim();
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        try {
            double valor = Double.parseDouble(limpo);
            return Double.isFinite(valor) ? valor : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
