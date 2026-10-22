package com.example.missoes.model;

import java.util.Locale;

public enum TipoMissao {
    /** Quebrar blocos (que não foram colocados por jogador). Alvos: materiais. */
    MINERAR,
    /** Colher plantações maduras. Alvos: materiais da planta (WHEAT, CARROTS...). */
    COLHER,
    /** Matar mobs (os de spawner não contam, por padrão). Alvos: tipos de entidade. */
    MATAR,
    /** Pescar. Alvos: materiais do que veio no anzol. */
    PESCAR,
    /** Vender itens pra loja do servidor (EconomyShopPlugin). Alvos: materiais. Conta itens vendidos. */
    VENDER,
    /** Causar dano no boss mundial (BossPlugin). Alvos: tipo do mob do boss (ZOMBIE...). Conta pontos de dano. */
    DANO_BOSS;

    public static TipoMissao de(String texto) {
        try {
            return valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
