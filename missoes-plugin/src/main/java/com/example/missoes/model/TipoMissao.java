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
    DANO_BOSS,
    /**
     * Vender nas barracas (BarracasPlugin): conta os itens que o jogador vendeu pra loja de uma
     * barraca e os que a loja dele vendeu (se ele estiver online). Alvos: materiais.
     */
    VENDER_BARRACA,
    /** Subir de rank (RankupPlugin). Alvos: o id do rank novo. Conta rankups. */
    SUBIR_RANK,
    /** Minerar dentro de uma mina (RankupPlugin). Alvos: o id da mina. Conta blocos. */
    MINERAR_MINA,
    /** Pagar o imposto do terreno (TerrenosPlugin), na mão ou no automático. Conta pagamentos. */
    PAGAR_IMPOSTO,
    /** Reparar itens no Ferreiro (OficinasPlugin). Alvos: materiais do item. Conta itens reparados. */
    REPARAR,
    /** Encantar itens na mesa da Bruxa ou no Cambista (OficinasPlugin). Alvos: materiais do item. Conta itens. */
    ENCANTAR;

    public static TipoMissao de(String texto) {
        try {
            return valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
