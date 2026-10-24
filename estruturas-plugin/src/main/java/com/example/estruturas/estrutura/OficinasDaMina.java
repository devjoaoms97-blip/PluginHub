package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.List;

import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Oficinas da mina: quatro barraquinhas temáticas na praça de entrada da Avenida das Minas, encostadas
 * na muralha sul, dos dois lados do portão. Cada uma tem o NPC do OficinasPlugin atrás do balcão:
 * <ul>
 *   <li>leste, x 10..18: <b>Ferreiro</b> (deepslate, bigorna, alto-forno, lava, chaminé);</li>
 *   <li>leste, x 21..29: <b>Picareteiro</b> (pinho, minérios na parede, barris, picaretas expostas);</li>
 *   <li>oeste, x -18..-10: <b>Bruxa</b> (carvalho escuro, vitral roxo, caldeirão, velas, ametista);</li>
 *   <li>oeste, x -29..-21: <b>Sábio</b> (estantes, lápis, quartzo, livros e frascos de XP).</li>
 * </ul>
 * Embaixo da barraca da bruxa fica a câmara fechada com a mesa de encantamento e as estantes (o
 * poder máximo, 30), que o NPC abre de longe.
 *
 * <p>Mesma origem da Avenida das Minas: {@code /estrutura construir oficinas spawn 0 65 -21}. x e z
 * são ABSOLUTOS e y é relativo ao chão (y = 0 é o piso em 64). Construir depois da avenida.
 */
public class OficinasDaMina implements Estrutura {

    private static final int ORIGEM_Z = -21;

    // Cada barraca: fundo encostado na muralha (z -24), NPC em z -25, balcão em z -26, toldo até z -27.
    private static final int Z_FUNDO = -24;
    private static final int Z_NPC = -25;
    private static final int Z_BALCAO = -26;
    private static final int Z_TOLDO = -27;

    /** Mesa da bruxa: centro da câmara (x, y relativo, z). */
    public static final int[] MESA = {-14, -3, -25};

    private record Tema(Material piso, Material parede, Material poste, Material teto, Material tetoEscada,
                        Material balcao, Material balcaoTopo) {}

    private Planta p;

    @Override
    public String getId() {
        return "oficinas";
    }

    @Override
    public String getNome() {
        return "Oficinas da Mina";
    }

    @Override
    public void gerar(Planta planta) {
        this.p = planta;
        ferreiro(10, 18);
        picareteiro(21, 29);
        bruxa(-18, -10);
        sabio(-29, -21);
        camaraDaBruxa();
    }

    private void set(int x, int y, int z, BlockData dados) {
        p.set(x, y, z - ORIGEM_Z, dados);
    }

    private void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    // ------------------------------------------------------------------ esqueleto comum

    /** Piso, fundo, laterais, postes, balcão, teto e toldo. Devolve o x do NPC (meio da barraca). */
    private int barraca(int a, int b, Tema t) {
        for (int x = a; x <= b; x++) {
            // limpa o espaço (a avenida tem canteiro e poste nessa faixa)
            for (int z = Z_TOLDO; z <= Z_FUNDO; z++) {
                for (int y = 1; y <= 7; y++) set(x, y, z, Material.AIR);
            }
            for (int z = Z_BALCAO; z <= Z_FUNDO; z++) set(x, 0, z, t.piso());
            for (int y = 1; y <= 4; y++) set(x, y, Z_FUNDO, t.parede());
            // teto (3 de fundo) e o toldo em escada por cima do passeio
            for (int z = Z_BALCAO; z <= Z_FUNDO; z++) set(x, 5, z, t.teto());
            set(x, 5, Z_TOLDO, escada(t.tetoEscada(), BlockFace.NORTH, false));
            set(x, 6, Z_FUNDO, laje(slabDe(t.teto()), false));
        }
        for (int x : new int[]{a, b}) {
            set(x, 1, Z_NPC, t.parede());
            for (int y = 2; y <= 4; y++) set(x, y, Z_NPC, t.parede());
            for (int y = 1; y <= 4; y++) set(x, y, Z_BALCAO, eixo(t.poste(), Axis.Y));
        }
        for (int x = a + 1; x <= b - 1; x++) set(x, 1, Z_BALCAO, t.balcao());
        if (t.balcaoTopo() != null) {
            for (int x = a + 1; x <= b - 1; x++) set(x, 2, Z_BALCAO, laje(t.balcaoTopo(), false));
        }
        // abertura no meio do balcão pra ver o NPC de corpo inteiro: só a laje some
        int meio = (a + b) / 2;
        set(meio, 2, Z_BALCAO, Material.AIR);
        // luz pendurada no teto
        set(a + 1, 4, Z_NPC, lanterna(Material.LANTERN, true));
        set(b - 1, 4, Z_NPC, lanterna(Material.LANTERN, true));
        return meio;
    }

    private static Material slabDe(Material bloco) {
        Material m = Material.matchMaterial(bloco.name().replace("_BRICKS", "_BRICK").replace("_TILES", "_TILE")
                .replace("_PLANKS", "") + "_SLAB");
        return m != null ? m : Material.STONE_BRICK_SLAB;
    }

    // ------------------------------------------------------------------ Ferreiro

    private void ferreiro(int a, int b) {
        Tema t = new Tema(Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.STRIPPED_DARK_OAK_LOG,
                Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS, Material.POLISHED_BLACKSTONE_BRICKS,
                Material.POLISHED_BLACKSTONE_SLAB);
        int m = barraca(a, b, t);
        // fundo: alto-forno aceso e grade de ferro de janela
        set(a + 1, 1, Z_FUNDO, aceso(direcional(Material.BLAST_FURNACE, BlockFace.SOUTH)));
        set(a + 2, 1, Z_FUNDO, aceso(direcional(Material.FURNACE, BlockFace.SOUTH)));
        // janela de grade atrás do NPC (os quadros ficam em m ± 2, no tijolo)
        set(m - 1, 3, Z_FUNDO, Material.IRON_BARS);
        set(m + 1, 3, Z_FUNDO, Material.IRON_BARS);
        // dentro: bigorna, rebolo e caldeirão de lava
        set(a + 1, 1, Z_NPC, direcional(Material.ANVIL, BlockFace.EAST));
        set(a + 2, 1, Z_NPC, Material.SMITHING_TABLE);
        set(b - 1, 1, Z_NPC, Material.LAVA_CAULDRON);
        set(b - 2, 1, Z_NPC, direcional(Material.GRINDSTONE, BlockFace.NORTH));
        // correntes penduradas nos cantos
        set(a + 1, 3, Z_NPC, eixo(Material.IRON_CHAIN, Axis.Y));
        set(b - 1, 3, Z_NPC, eixo(Material.IRON_CHAIN, Axis.Y));
        // no balcão: bigorna lascada
        set(a + 2, 2, Z_BALCAO, direcional(Material.CHIPPED_ANVIL, BlockFace.EAST));
        // chaminé com fumaça (fogueira lá em cima, longe de quem anda)
        for (int y = 6; y <= 7; y++) set(b - 1, y, Z_FUNDO, Material.BRICKS);
        set(b - 1, 8, Z_FUNDO, Material.CAMPFIRE);
    }

    // ------------------------------------------------------------------ Picareteiro

    private void picareteiro(int a, int b) {
        Tema t = new Tema(Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_LOG,
                Material.DARK_OAK_PLANKS, Material.DARK_OAK_STAIRS, Material.BARREL, Material.SPRUCE_SLAB);
        barraca(a, b, t);
        // mostruário de minérios na parede do fundo
        Material[] minerios = {Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE,
                Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE};
        for (int i = 0; i < minerios.length && a + 1 + i <= b - 1; i++) set(a + 1 + i, 4, Z_FUNDO, minerios[i]);
        // barris, baú e mesa de trabalho dos lados do NPC
        set(a + 1, 1, Z_NPC, direcional(Material.BARREL, BlockFace.UP));
        set(a + 1, 2, Z_NPC, direcional(Material.BARREL, BlockFace.UP));
        set(a + 2, 1, Z_NPC, Material.CRAFTING_TABLE);
        set(b - 1, 1, Z_NPC, direcional(Material.CHEST, BlockFace.SOUTH));
        set(b - 2, 1, Z_NPC, Material.FLETCHING_TABLE);
        set(a + 3, 2, Z_BALCAO, Material.TORCH);
        set(b - 3, 2, Z_BALCAO, Material.TORCH);
    }

    // ------------------------------------------------------------------ Bruxa

    private void bruxa(int a, int b) {
        Tema t = new Tema(Material.MUD_BRICKS, Material.DARK_OAK_PLANKS, Material.STRIPPED_DARK_OAK_LOG,
                Material.DARK_PRISMARINE, Material.DARK_PRISMARINE_STAIRS, Material.DARK_OAK_PLANKS, Material.DARK_OAK_SLAB);
        int m = barraca(a, b, t);
        // estantes e vitral roxo no fundo
        for (int x = a + 1; x <= b - 1; x++) {
            set(x, 1, Z_FUNDO, Material.BOOKSHELF);
            set(x, 3, Z_FUNDO, Math.abs(x - m) <= 1 ? Material.PURPLE_STAINED_GLASS : Material.BOOKSHELF);
        }
        // caldeirão, suporte de poções e cogumelos
        set(a + 1, 1, Z_NPC, Material.WATER_CAULDRON);
        set(a + 2, 1, Z_NPC, Material.BREWING_STAND);
        set(b - 1, 1, Z_NPC, Material.POTTED_CRIMSON_FUNGUS);
        set(b - 2, 1, Z_NPC, Material.AMETHYST_BLOCK);
        set(b - 2, 2, Z_NPC, direcional(Material.AMETHYST_CLUSTER, BlockFace.UP));
        // teia nos cantos do teto e velas roxas acesas no balcão
        set(a + 1, 4, Z_FUNDO + 0, Material.BOOKSHELF);
        set(b - 1, 3, Z_NPC, Material.COBWEB);
        set(a + 2, 2, Z_BALCAO, vela(Material.PURPLE_CANDLE, 3));
        set(b - 2, 2, Z_BALCAO, vela(Material.BLACK_CANDLE, 2));
        set(m + 2, 2, Z_BALCAO, Material.SKELETON_SKULL);
        // o lugar da antiga lanterna vira lanterna de alma (luz azulada)
        set(a + 1, 4, Z_NPC, lanterna(Material.SOUL_LANTERN, true));
        set(b - 1, 4, Z_NPC, lanterna(Material.SOUL_LANTERN, true));
    }

    /**
     * Câmara fechada 7x7 embaixo da barraca da bruxa: mesa no meio, ar em volta e as estantes a 2
     * blocos, em duas alturas (32 estantes: o jogo conta no máximo 15, poder 30).
     */
    private void camaraDaBruxa() {
        int cx = MESA[0], cy = MESA[1], cz = MESA[2];
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + dx, z = cz + dz;
                set(x, cy - 1, z, Material.DEEPSLATE_BRICKS);
                set(x, cy + 2, z, Material.DEEPSLATE_BRICKS);
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                for (int y = cy; y <= cy + 1; y++) {
                    Material m = d == 3 ? Material.DEEPSLATE_BRICKS : d == 2 ? Material.BOOKSHELF : Material.AIR;
                    set(x, y, z, m);
                }
            }
        }
        set(cx, cy, cz, Material.ENCHANTING_TABLE);
    }

    // ------------------------------------------------------------------ Sábio

    private void sabio(int a, int b) {
        Tema t = new Tema(Material.SMOOTH_QUARTZ, Material.CHISELED_BOOKSHELF, Material.BIRCH_LOG,
                Material.LAPIS_BLOCK, Material.PRISMARINE_BRICK_STAIRS, Material.QUARTZ_BRICKS, Material.QUARTZ_SLAB);
        int m = barraca(a, b, t);
        for (int x = a + 1; x <= b - 1; x++) {
            set(x, 1, Z_FUNDO, Material.BOOKSHELF);
            set(x, 2, Z_FUNDO, Material.BOOKSHELF);
        }
        set(m, 4, Z_FUNDO, Material.LAPIS_BLOCK);
        set(a + 1, 1, Z_NPC, direcional(Material.LECTERN, BlockFace.SOUTH));
        set(a + 2, 1, Z_NPC, Material.LAPIS_BLOCK);
        set(a + 2, 2, Z_NPC, direcional(Material.END_ROD, BlockFace.UP));
        set(b - 1, 1, Z_NPC, Material.POTTED_BLUE_ORCHID);
        set(b - 2, 1, Z_NPC, Material.LAPIS_BLOCK);
        set(b - 2, 2, Z_NPC, direcional(Material.AMETHYST_CLUSTER, BlockFace.UP));
        set(a + 2, 2, Z_BALCAO, vela(Material.LIGHT_BLUE_CANDLE, 2));
        set(b - 2, 2, Z_BALCAO, vela(Material.WHITE_CANDLE, 3));
        // o teto de lápis ganha a borda de quartzo
        for (int x = a; x <= b; x++) set(x, 6, Z_FUNDO, laje(Material.QUARTZ_SLAB, false));
    }

    // ------------------------------------------------------------------ utilidades

    private static BlockData aceso(BlockData dados) {
        if (dados instanceof Lightable l) l.setLit(true);
        return dados;
    }

    private static BlockData vela(Material material, int quantas) {
        Candle vela = (Candle) material.createBlockData();
        vela.setCandles(quantas);
        vela.setLit(true);
        return vela;
    }

    @SuppressWarnings("unused")
    private static BlockData folha(Material material) {
        Leaves folhas = (Leaves) material.createBlockData();
        folhas.setPersistent(true);
        return folhas;
    }

    // ------------------------------------------------------------------ integração

    /**
     * Os 4 NPCs (OficinasPlugin), a mesa da bruxa e os quadros com o que cada um vende, na parede
     * do fundo (por comando: quadro é entidade).
     */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        // ox/oz = origem (0, -21): x e z daqui já são absolutos, então só o y é somado
        List<String> c = new ArrayList<>();
        int yPe = oy + 1;
        // yaw 180: olhando pro norte, pro balcão e pro passeio (o fundo da barraca é a muralha sul)
        c.add("oficina criar ferreiro ferreiro " + mundo + " 14.5 " + yPe + " -24.5 180");
        c.add("oficina criar picareteiro picareteiro " + mundo + " 25.5 " + yPe + " -24.5 180");
        c.add("oficina criar bruxa bruxa " + mundo + " -13.5 " + yPe + " -24.5 180");
        c.add("oficina criar sabio sabio " + mundo + " -24.5 " + yPe + " -24.5 180");
        c.add("oficina mesa " + mundo + " " + MESA[0] + " " + (oy + MESA[1]) + " " + MESA[2]);

        String em = "execute in minecraft:" + mundo + " run ";
        int yQuadro = oy + 3;
        c.add(em + "minecraft:kill @e[type=item_frame,tag=oficina,x=0,y=" + yQuadro + ",z=-25,distance=..40]");
        String[][] quadros = {
                {"12", "iron_pickaxe"}, {"16", "anvil"},
                {"23", "diamond_pickaxe"}, {"27", "netherite_pickaxe"},
                {"-16", "potion"}, {"-12", "enchanted_book"},
                {"-27", "experience_bottle"}, {"-23", "knowledge_book"},
        };
        for (String[] q : quadros) {
            c.add(em + "summon minecraft:item_frame " + q[0] + " " + yQuadro + " -25 {Facing:3b,Fixed:1b,Invulnerable:1b,"
                    + "Tags:[\"oficina\"],Item:{id:\"minecraft:" + q[1] + "\",count:1}}");
        }
        return c;
    }
}
