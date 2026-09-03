package com.example.vip.model;

import java.util.Locale;

/**
 * Os 4 tiers de VIP. A ordem aqui (ordinal) representa a hierarquia, do mais fraco pro mais
 * forte — útil se algum dia quisermos comparar "tier A é melhor que tier B".
 */
public enum VipTier {
    BRONZE,
    PRATA,
    OURO,
    DIAMANTE;

    public static VipTier fromString(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return VipTier.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
