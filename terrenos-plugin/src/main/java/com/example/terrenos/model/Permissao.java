package com.example.terrenos.model;

import java.util.Locale;

/** O que um amigo pode fazer no terreno. O dono pode tudo. */
public enum Permissao {
    /** Quebrar e colocar blocos, baldes, bater em animais, molduras e suportes de armadura. */
    CONSTRUIR,
    /** Abrir baús, barris, fornalhas, funis e outros recipientes. */
    BAUS,
    /** Portas, alçapões, portões, botões, alavancas, placas de pressão, camas; montar e negociar com mobs. */
    USAR;

    public String nome() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Permissao ler(String texto) {
        return switch (texto.toLowerCase(Locale.ROOT)) {
            case "construir", "build" -> CONSTRUIR;
            case "baus", "baús", "bau", "baú" -> BAUS;
            case "usar", "portas" -> USAR;
            default -> null;
        };
    }
}
