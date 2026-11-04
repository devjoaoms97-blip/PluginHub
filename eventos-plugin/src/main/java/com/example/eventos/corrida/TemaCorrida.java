package com.example.eventos.corrida;

import org.bukkit.Material;

/**
 * O visual de um mapa da Corrida Maluca: as listras das paredes, a faixa de cima, os pisos coloridos, as
 * bandeirinhas e as peças. As plataformas que somem (verde e rosa, com o aviso vermelho) são iguais em
 * todos os mapas, pra ninguém ter que reaprender o aviso.
 *
 * @param pisos     as cores do piso listrado, dos discos e do parkour (a primeira é a do 1º bloco do parkour)
 * @param vidros    os painéis de vidro das bandeirinhas (o bloco inteiro de cada um vai nas janelas e claraboias)
 * @param tapete    uma terracota vitrificada (tem direção: as setas apontam pra trás)
 * @param pecaA     a cor principal das barras, da cabeça dos martelos e de um raio do carrossel
 */
public record TemaCorrida(String nome, Material listraA, Material listraB, Material faixa, Material luzFaixa,
                          Material[] pisos, Material[] vidros, Material corpo, Material passarela, Material borda,
                          Material largadaA, Material largadaB, Material ponteA, Material ponteB,
                          Material porta, Material colunaPorta, Material vigaPorta, Material tapete,
                          Material pecaA, Material pecaB) {

    private static Material[] m(Material... lista) {
        return lista;
    }

    static final TemaCorrida PARQUE = new TemaCorrida("Parque de Diversões",
            Material.RED_CONCRETE, Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE, Material.OCHRE_FROGLIGHT,
            m(Material.LIGHT_BLUE_CONCRETE, Material.PINK_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.ORANGE_CONCRETE),
            m(Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
                    Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE),
            Material.WHITE_CONCRETE, Material.SMOOTH_QUARTZ, Material.SMOOTH_QUARTZ_SLAB,
            Material.WHITE_CONCRETE, Material.LIME_CONCRETE, Material.ORANGE_CONCRETE, Material.WHITE_CONCRETE,
            Material.PURPLE_CONCRETE, Material.RED_CONCRETE, Material.WHITE_CONCRETE, Material.MAGENTA_GLAZED_TERRACOTTA,
            Material.RED_CONCRETE, Material.WHITE_CONCRETE);

    static final TemaCorrida DOCES = new TemaCorrida("Fábrica de Doces",
            Material.PINK_CONCRETE, Material.WHITE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PEARLESCENT_FROGLIGHT,
            m(Material.PINK_CONCRETE, Material.WHITE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE),
            m(Material.PINK_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
                    Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE),
            Material.WHITE_CONCRETE, Material.SMOOTH_QUARTZ, Material.SMOOTH_QUARTZ_SLAB,
            Material.PINK_CONCRETE, Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.WHITE_CONCRETE,
            Material.BROWN_CONCRETE, Material.PINK_CONCRETE, Material.WHITE_CONCRETE, Material.PINK_GLAZED_TERRACOTTA,
            Material.MAGENTA_CONCRETE, Material.WHITE_CONCRETE);

    static final TemaCorrida GELO = new TemaCorrida("Polo Norte",
            Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE, Material.BLUE_CONCRETE, Material.SEA_LANTERN,
            m(Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.CYAN_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.BLUE_CONCRETE),
            m(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE,
                    Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE),
            Material.SNOW_BLOCK, Material.SMOOTH_QUARTZ, Material.SMOOTH_QUARTZ_SLAB,
            Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.CYAN_CONCRETE, Material.WHITE_CONCRETE,
            Material.BLUE_CONCRETE, Material.CYAN_CONCRETE, Material.WHITE_CONCRETE, Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
            Material.BLUE_CONCRETE, Material.WHITE_CONCRETE);

    static final TemaCorrida NEON = new TemaCorrida("Neon",
            Material.BLACK_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.VERDANT_FROGLIGHT,
            m(Material.CYAN_CONCRETE, Material.MAGENTA_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.LIGHT_BLUE_CONCRETE),
            m(Material.LIME_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE,
                    Material.YELLOW_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE),
            Material.BLACK_CONCRETE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_SLAB,
            Material.BLACK_CONCRETE, Material.CYAN_CONCRETE, Material.MAGENTA_CONCRETE, Material.BLACK_CONCRETE,
            Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.BLACK_CONCRETE, Material.PURPLE_GLAZED_TERRACOTTA,
            Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE);

    static final TemaCorrida SELVA = new TemaCorrida("Selva",
            Material.GREEN_CONCRETE, Material.JUNGLE_PLANKS, Material.BROWN_CONCRETE, Material.OCHRE_FROGLIGHT,
            m(Material.MOSS_BLOCK, Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE),
            m(Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
                    Material.ORANGE_STAINED_GLASS_PANE, Material.BROWN_STAINED_GLASS_PANE),
            Material.GREEN_TERRACOTTA, Material.JUNGLE_PLANKS, Material.JUNGLE_SLAB,
            Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE, Material.JUNGLE_PLANKS, Material.BROWN_CONCRETE,
            Material.BROWN_CONCRETE, Material.JUNGLE_LOG, Material.DARK_OAK_PLANKS, Material.GREEN_GLAZED_TERRACOTTA,
            Material.BROWN_CONCRETE, Material.YELLOW_CONCRETE);
}
