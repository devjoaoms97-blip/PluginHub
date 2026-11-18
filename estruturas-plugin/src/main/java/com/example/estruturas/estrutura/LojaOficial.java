package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Loja Oficial: salão fechado colado no portão LESTE do spawn ("LOJA"), como a avenida das minas no
 * norte e a arena do boss no sul: o túnel do portão desemboca direto na porta. Mesmo estilo do spawn:
 * tijolo de pedra misturado, faixas lisas, detalhes vinho, caule carmesim e cerejeira. O mundo "spawn"
 * é vazio fora das construções: embaixo do salão só vai a fundação.
 *
 * <p>Dentro, uma nave comprida e doze balcões, seis de cada lado, cada um com toldo da cor do tema,
 * decoração e quadros com o que vende. Sete têm o vendedor (NPC) de uma categoria do EconomyShopPlugin:
 * no norte construtor, lenhador, jardineiro e minerador; no sul fazendeiro, caçador e alquimista. Os
 * outros abrem a loja de outro plugin: spawners e fazendas (o Corretor Rural, desde 2026-11-17) no norte,
 * máquinas e PvP no sul. O último do sul fica reservado ("em breve"), sem vendedor. No fundo, no lugar da
 * fonte, fica o Banco ({@link BancoDaMina}, desde 2026-11-17), com o Bancário.
 *
 * <p>Construir com {@code /estrutura construir loja spawn 0 65 43} (a mesma origem do spawn): x é
 * ABSOLUTO, z é relativo ao centro do spawn (z 43) e y é relativo ao chão (y 0 = piso em 64). O portão
 * leste do spawn termina em x 63; a parede da frente da loja é o x 64.
 */
public class LojaOficial implements Estrutura {

    // Área limpa antes de construir (inclui o que sobrou da versão com jardim em volta).
    private static final int X_LIMPAR_INI = 64, X_LIMPAR_FIM = 161, DZ_LIMPAR = 28;
    // Salão.
    // o fundo era o x 136; em 2026-11-17 o salão cresceu 13 blocos pro par de balcões das fazendas (149)
    // e mais 10 pro Banco, que saiu do fim da Avenida das Minas e ficou no lugar da fonte
    private static final int X_FRENTE = 64, DZ_PAREDE = 20;
    static final int X_FUNDO = 159;
    private static final int ALTURA = 12;                 // teto do salão
    private static final int Y_CLERESTORIO = 17;          // telhado de vidro sobre a nave
    private static final int MEIA_NAVE = 4;

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material FAIXA = Material.SMOOTH_STONE;
    private static final Material VERMELHO = Material.RED_NETHER_BRICKS;

    /**
     * Um balcão: id do vendedor (EconomyShopPlugin), lado (-1 norte, +1 sul) e onde começa/termina em x.
     * {@code reservado}: ainda sem vendedor ("em breve").
     */
    public record Balcao(String id, int lado, int ini, int fim, Material toldo, Material toldo2, boolean reservado) {
        Balcao(String id, int lado, int ini, int fim, Material toldo, Material toldo2) {
            this(id, lado, ini, fim, toldo, toldo2, false);
        }

        int meio() {
            return (ini + fim) / 2;
        }
    }

    public static final List<Balcao> BALCOES = List.of(
            new Balcao("construtor", -1, 69, 78, Material.ORANGE_WOOL, Material.WHITE_WOOL),
            new Balcao("lenhador", -1, 82, 91, Material.BROWN_WOOL, Material.RED_WOOL),
            new Balcao("jardineiro", -1, 95, 104, Material.LIME_WOOL, Material.PINK_WOOL),
            new Balcao("fazendeiro", 1, 69, 78, Material.YELLOW_WOOL, Material.GREEN_WOOL),
            new Balcao("cacador", 1, 82, 91, Material.GRAY_WOOL, Material.BROWN_WOOL),
            new Balcao("alquimista", 1, 95, 104, Material.PURPLE_WOOL, Material.MAGENTA_WOOL),
            new Balcao("minerador", -1, 108, 117, Material.CYAN_WOOL, Material.LIGHT_BLUE_WOOL),
            new Balcao("spawners", -1, 121, 130, Material.BLACK_WOOL, Material.GRAY_WOOL),
            new Balcao("maquinas", 1, 108, 117, Material.LIGHT_GRAY_WOOL, Material.ORANGE_WOOL),
            new Balcao("pvp", 1, 121, 130, Material.RED_WOOL, Material.BLACK_WOOL),
            new Balcao("fazendas", -1, 134, 143, Material.LIME_WOOL, Material.YELLOW_WOOL),
            new Balcao("reservado", 1, 134, 143, Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, true));

    private Planta p;

    @Override
    public String getId() {
        return "loja";
    }

    @Override
    public String getNome() {
        return "Loja Oficial";
    }

    @Override
    public void gerar(Planta planta) {
        this.p = planta;
        limparEFundar();
        salao();
        nave();
        for (Balcao b : BALCOES) balcao(b);
        new BancoDaMina().gerarEm(p);
        portaDoPorto();
        telhado();
        fachada();
    }

    private void set(int x, int y, int dz, BlockData d) {
        p.set(x, y, dz, d);
    }

    private void set(int x, int y, int dz, Material m) {
        p.set(x, y, dz, m);
    }

    /** Tijolo de pedra misturado com musgo e rachado (igual ao spawn). */
    private static Material pedra(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h = Math.floorMod(h ^ (h >>> 13), 100);
        if (h < 62) return PEDRA;
        if (h < 86) return Material.MOSSY_STONE_BRICKS;
        return Material.CRACKED_STONE_BRICKS;
    }

    private static BlockData folha(Material m) {
        Leaves f = (Leaves) m.createBlockData();
        f.setPersistent(true);
        return f;
    }

    // ------------------------------------------------------------------ base

    /** Limpa a área (e a ilha de jardim da versão antiga) e assenta a fundação embaixo do salão. */
    private void limparEFundar() {
        for (int x = X_LIMPAR_INI; x <= X_LIMPAR_FIM; x++) {
            for (int dz = -DZ_LIMPAR; dz <= DZ_LIMPAR; dz++) {
                for (int y = -8; y <= Y_CLERESTORIO + 6; y++) set(x, y, dz, Material.AIR);
            }
        }
        for (int x = X_FRENTE; x <= X_FUNDO + 1; x++) {
            for (int dz = -DZ_PAREDE - 1; dz <= DZ_PAREDE + 1; dz++) {
                set(x, -1, dz, PEDRA);
                set(x, -2, dz, Material.STONE);
                set(x, -3, dz, Material.STONE);
            }
        }
    }

    // ------------------------------------------------------------------ salão

    private void salao() {
        for (int x = X_FRENTE; x <= X_FUNDO; x++) {
            for (int dz = -DZ_PAREDE; dz <= DZ_PAREDE; dz++) {
                boolean parede = x == X_FRENTE || x == X_FUNDO || Math.abs(dz) == DZ_PAREDE;
                // piso: xadrez de andesito e diorito com faixa lisa a cada 6
                Material piso = Math.floorMod(x, 6) == 0 ? FAIXA
                        : Math.floorMod(x + dz, 2) == 0 ? Material.POLISHED_ANDESITE : Material.POLISHED_DIORITE;
                set(x, 0, dz, parede ? PEDRA : piso);
                if (!parede) continue;
                for (int y = 1; y <= ALTURA; y++) {
                    Material m = (y == 1 || y == 6 || y == ALTURA) ? FAIXA : pedra(x, y, dz);
                    set(x, y, dz, m);
                }
            }
        }
        // pilastras de caule carmesim a cada 6, por dentro das paredes compridas, e janelas altas entre elas
        for (int x = X_FRENTE + 1; x < X_FUNDO; x++) {
            for (int s : new int[]{-1, 1}) {
                int dz = s * (DZ_PAREDE - 1);
                if (Math.floorMod(x - X_FRENTE, 6) == 0) {
                    for (int y = 1; y < ALTURA; y++) set(x, y, dz, eixo(Material.CRIMSON_STEM, Axis.Y));
                    set(x, ALTURA - 1, dz, VERMELHO);
                    set(x, 9, s * (DZ_PAREDE - 2), lanterna(Material.LANTERN, false));
                    set(x, 8, s * (DZ_PAREDE - 2), laje(Material.CRIMSON_SLAB, true));
                } else if (Math.floorMod(x - X_FRENTE, 6) != 1 && Math.floorMod(x - X_FRENTE, 6) != 5) {
                    for (int y = 7; y <= 10; y++) set(x, y, s * DZ_PAREDE, conectado(Material.GLASS_PANE, BlockFace.EAST, BlockFace.WEST));
                }
            }
        }
        // teto de madeira escura com vigas, aberto em cima da nave
        for (int x = X_FRENTE + 1; x < X_FUNDO; x++) {
            for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
                if (Math.abs(dz) <= MEIA_NAVE + 1) continue;
                set(x, ALTURA, dz, Math.floorMod(x - X_FRENTE, 6) == 0 ? eixo(Material.STRIPPED_DARK_OAK_LOG, Axis.Z)
                        : Material.DARK_OAK_PLANKS.createBlockData());
            }
        }
    }

    /** Nave: passadeira vinho de ponta a ponta, bancos no meio e lustres. */
    private void nave() {
        for (int x = X_FRENTE + 1; x < X_FUNDO; x++) {
            for (int dz = -MEIA_NAVE; dz <= MEIA_NAVE; dz++) {
                set(x, 0, dz, Math.abs(dz) == MEIA_NAVE ? VERMELHO : (Math.abs(dz) <= 1 ? Material.SMOOTH_STONE : Material.POLISHED_DEEPSLATE));
            }
        }
        // bancos de cerejeira entre um par de balcões e o outro, virados pra nave
        for (int x0 : new int[]{79, 92, 105, 118, 131}) {
            for (int s : new int[]{-1, 1}) {
                set(x0, 1, s * 8, escada(Material.CHERRY_STAIRS, s < 0 ? BlockFace.NORTH : BlockFace.SOUTH, false));
                set(x0 + 1, 1, s * 8, escada(Material.CHERRY_STAIRS, s < 0 ? BlockFace.NORTH : BlockFace.SOUTH, false));
            }
        }
        // lustres: corrente do clerestório e lanterna
        for (int x = X_FRENTE + 6; x < X_FUNDO - 8; x += 8) { // o último ficaria em cima do banco
            for (int y = 11; y < Y_CLERESTORIO; y++) set(x, y, 0, eixo(Material.IRON_CHAIN, Axis.Y));
            set(x, 10, 0, lanterna(Material.LANTERN, true));
        }
    }

    // ------------------------------------------------------------------ balcões

    /**
     * Coordenadas de um balcão: {@code ao} anda ao longo do balcão (ini..fim) e {@code fundo} vai do
     * balcão (0) até a vitrine (7); a parede do salão é o 8. Nos laterais é ao longo de x; no do fundo, ao longo de z.
     */
    private void bset(Balcao b, int ao, int y, int fundo, BlockData d) {
        if (b.lado() == 0) set(X_FUNDO - 8 + fundo, y, ao, d);
        else set(ao, y, b.lado() * (12 + fundo), d);
    }

    private void bset(Balcao b, int ao, int y, int fundo, Material m) {
        bset(b, ao, y, fundo, m.createBlockData());
    }

    /** Pra onde o vendedor olha (pra nave). */
    private static BlockFace frente(Balcao b) {
        return b.lado() == 0 ? BlockFace.WEST : (b.lado() < 0 ? BlockFace.SOUTH : BlockFace.NORTH);
    }

    private void balcao(Balcao b) {
        BlockFace frente = frente(b);
        Material piso = switch (b.id()) {
            case "construtor" -> Material.BRICKS;
            case "lenhador" -> Material.SPRUCE_PLANKS;
            case "jardineiro" -> Material.MOSS_BLOCK;
            case "fazendeiro" -> Material.PACKED_MUD;
            case "fazendas" -> Material.MOSS_BLOCK;
            case "cacador" -> Material.COARSE_DIRT;
            case "alquimista" -> Material.PURPUR_BLOCK;
            default -> Material.POLISHED_DEEPSLATE;
        };
        for (int ao = b.ini(); ao <= b.fim(); ao++) {
            for (int f = 0; f <= 7; f++) {
                for (int y = 1; y <= 7; y++) bset(b, ao, y, f, Material.AIR);
                bset(b, ao, 0, f, piso);
            }
            // balcão com tampo, aberto no meio pra ver o vendedor
            if (ao > b.ini() && ao < b.fim()) {
                bset(b, ao, 1, 0, Material.STRIPPED_DARK_OAK_WOOD);
                if (Math.abs(ao - b.meio()) > 1) bset(b, ao, 2, 0, laje(Material.DARK_OAK_SLAB, false));
            }
            // toldo listrado, com a beirada pra fora do balcão
            for (int f = -1; f <= 7; f++) bset(b, ao, 6, f, Math.floorMod(ao, 2) == 0 ? b.toldo() : b.toldo2());
            bset(b, ao, 5, -1, Math.floorMod(ao, 2) == 0 ? b.toldo() : b.toldo2());
        }
        // divisórias e pilares nas pontas
        for (int ao : new int[]{b.ini(), b.fim()}) {
            for (int f = 1; f <= 7; f++) for (int y = 1; y <= 5; y++) bset(b, ao, y, f, Material.STRIPPED_SPRUCE_LOG);
            for (int y = 1; y <= 5; y++) bset(b, ao, y, 0, eixo(Material.CHERRY_LOG, Axis.Y));
            for (int y = 1; y <= 5; y++) bset(b, ao, y, -1, eixo(Material.CHERRY_LOG, Axis.Y));
        }
        // luz
        bset(b, b.ini() + 1, 5, 2, lanterna(Material.LANTERN, true));
        bset(b, b.fim() - 1, 5, 2, lanterna(Material.LANTERN, true));
        decoracao(b, frente);
    }

    /** O que cada vendedor tem atrás do balcão (o fundo, f = 5..7, é a "vitrine"). */
    private void decoracao(Balcao b, BlockFace frente) {
        int a = b.ini() + 1, z = b.fim() - 1, m = b.meio();
        switch (b.id()) {
            case "construtor" -> {
                Material[] cores = {Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
                        Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.WHITE_CONCRETE};
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, cores[Math.floorMod(ao, cores.length)]);
                    bset(b, ao, 2, 7, Math.floorMod(ao, 2) == 0 ? Material.BRICKS : Material.STONE_BRICKS);
                    bset(b, ao, 3, 7, Material.SCAFFOLDING);
                }
                bset(b, a, 1, 5, Material.STONECUTTER);
                bset(b, a, 1, 4, Material.CRAFTING_TABLE);
                bset(b, z, 1, 5, Material.SAND);
                bset(b, z, 2, 5, Material.GRAVEL);
                bset(b, z, 1, 4, Material.GLASS);
            }
            case "lenhador" -> {
                Material[] toras = {Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG, Material.CHERRY_LOG};
                for (int ao = a; ao <= z; ao++) {
                    for (int y = 1; y <= 3; y++) bset(b, ao, y, 7, eixo(toras[Math.floorMod(ao + y, toras.length)], b.lado() == 0 ? Axis.X : Axis.Z));
                }
                bset(b, a, 1, 5, direcional(Material.BARREL, BlockFace.UP));
                bset(b, a, 1, 4, Material.CHERRY_SAPLING);
                bset(b, z, 1, 5, Material.STRIPPED_OAK_LOG);
                bset(b, z, 1, 4, direcional(Material.BARREL, BlockFace.UP));
            }
            case "jardineiro" -> {
                Material[] vasos = {Material.POTTED_RED_TULIP, Material.POTTED_ALLIUM, Material.POTTED_BLUE_ORCHID, Material.POTTED_AZALEA_BUSH,
                        Material.POTTED_CORNFLOWER, Material.POTTED_FLOWERING_AZALEA_BUSH, Material.POTTED_LILY_OF_THE_VALLEY};
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, folha(Material.FLOWERING_AZALEA_LEAVES));
                    bset(b, ao, 2, 7, Material.MOSS_BLOCK);
                    bset(b, ao, 3, 7, vasos[Math.floorMod(ao, vasos.length)]);
                }
                bset(b, a, 1, 5, Material.FLOWERING_AZALEA);
                bset(b, a, 1, 4, Material.MOSS_BLOCK);
                bset(b, a, 2, 4, Material.POTTED_FERN);
                bset(b, z, 1, 5, Material.AZALEA);
                bset(b, z, 1, 4, Material.COMPOSTER);
                bset(b, m, 5, 6, Material.SPORE_BLOSSOM);
            }
            case "fazendeiro" -> {
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, eixo(Material.HAY_BLOCK, Axis.Y));
                    bset(b, ao, 2, 7, Math.floorMod(ao, 3) == 0 ? Material.JACK_O_LANTERN.createBlockData()
                            : (Math.floorMod(ao, 3) == 1 ? Material.MELON.createBlockData() : eixo(Material.HAY_BLOCK, Axis.Y)));
                }
                bset(b, a, 1, 5, Material.COMPOSTER);
                bset(b, a, 1, 4, direcional(Material.BARREL, BlockFace.UP));
                bset(b, a, 2, 4, Material.PUMPKIN);
                bset(b, z, 1, 5, eixo(Material.HAY_BLOCK, Axis.Y));
                bset(b, z, 2, 5, eixo(Material.HAY_BLOCK, Axis.Y));
                bset(b, z, 1, 4, Material.WATER_CAULDRON);
            }
            case "cacador" -> {
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, eixo(Material.BONE_BLOCK, Axis.Y));
                    bset(b, ao, 2, 7, Material.BROWN_WOOL);
                    bset(b, ao, 3, 7, Math.floorMod(ao, 2) == 0 ? Material.TARGET : Material.BROWN_WOOL);
                }
                bset(b, a, 1, 5, Material.FLETCHING_TABLE);
                bset(b, a, 2, 5, Material.SKELETON_SKULL);
                bset(b, a, 1, 4, direcional(Material.BARREL, BlockFace.UP));
                bset(b, z, 1, 5, Material.SMOKER);
                bset(b, z, 2, 5, Material.ZOMBIE_HEAD);
                bset(b, z, 1, 4, Material.COBWEB);
            }
            case "alquimista" -> {
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Material.BOOKSHELF);
                    bset(b, ao, 2, 7, Math.abs(ao - m) <= 1 ? Material.PURPLE_STAINED_GLASS : Material.BOOKSHELF);
                    bset(b, ao, 3, 7, Material.AMETHYST_BLOCK);
                }
                bset(b, a, 1, 5, Material.BREWING_STAND);
                bset(b, a, 1, 4, Material.WATER_CAULDRON);
                bset(b, z, 1, 5, direcional(Material.LECTERN, frente));
                bset(b, z, 1, 4, Material.AMETHYST_BLOCK);
                bset(b, z, 2, 4, direcional(Material.AMETHYST_CLUSTER, BlockFace.UP));
                bset(b, a + 1, 2, 0, vela(Material.PURPLE_CANDLE, 3));
                bset(b, z - 1, 2, 0, vela(Material.MAGENTA_CANDLE, 2));
                bset(b, a, 5, 2, lanterna(Material.SOUL_LANTERN, true));
                bset(b, z, 5, 2, lanterna(Material.SOUL_LANTERN, true));
            }
            case "spawners" -> {
                // gaiolas: spawners vazios de enfeite (sem mob: não geram nada) e barras de ferro
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Material.POLISHED_DEEPSLATE);
                    bset(b, ao, 2, 7, Math.floorMod(ao, 2) == 0 ? Material.IRON_BARS : Material.DEEPSLATE_TILES);
                    bset(b, ao, 3, 7, Material.POLISHED_DEEPSLATE);
                }
                bset(b, a, 1, 5, Material.BARREL);
                bset(b, a, 1, 4, Material.HAY_BLOCK);
                bset(b, z, 1, 5, Material.COBWEB);
                bset(b, z, 1, 4, Material.BONE_BLOCK);
            }
            case "maquinas" -> {
                // oficina: pistões, observadores, funis, correntes e uma bigorna
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Math.floorMod(ao, 2) == 0 ? Material.WAXED_COPPER_BLOCK : Material.POLISHED_DEEPSLATE);
                    bset(b, ao, 2, 7, Math.floorMod(ao, 3) == 0 ? Material.OBSERVER : Material.PISTON);
                    bset(b, ao, 3, 7, Material.WAXED_CUT_COPPER);
                }
                bset(b, a, 1, 5, Material.CRAFTER);
                bset(b, a, 1, 4, Material.HOPPER);
                bset(b, z, 1, 5, direcional(Material.ANVIL, BlockFace.NORTH));
                bset(b, z, 1, 4, Material.BLAST_FURNACE);
            }
            case "pvp" -> {
                // arsenal: suportes de armadura nos quadros, alvo, barris e uma bigorna
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Material.POLISHED_BLACKSTONE_BRICKS);
                    bset(b, ao, 2, 7, Math.floorMod(ao, 2) == 0 ? Material.RED_NETHER_BRICKS : Material.TARGET);
                    bset(b, ao, 3, 7, Material.POLISHED_BLACKSTONE_BRICKS);
                }
                bset(b, a, 1, 5, direcional(Material.ANVIL, BlockFace.NORTH));
                bset(b, a, 1, 4, Material.GRINDSTONE);
                bset(b, z, 1, 5, Material.BREWING_STAND);
                bset(b, z, 1, 4, Material.BARREL);
            }
            case "fazendas" -> {
                // corretor rural: fardos de feno, composteiras e colmeia, e uma mesa com a escritura
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Math.floorMod(ao, 2) == 0 ? Material.HAY_BLOCK : Material.COMPOSTER);
                    bset(b, ao, 2, 7, Math.floorMod(ao, 3) == 0 ? Material.BEE_NEST : Material.OAK_PLANKS);
                    bset(b, ao, 3, 7, Material.STRIPPED_OAK_LOG);
                }
                bset(b, a, 1, 5, Material.CARTOGRAPHY_TABLE);
                bset(b, a, 1, 4, Material.HAY_BLOCK);
                bset(b, a, 2, 4, Material.CARVED_PUMPKIN);
                bset(b, z, 1, 5, Material.BARREL);
                bset(b, z, 1, 4, Material.MELON);
                bset(b, z, 2, 5, Material.FLOWER_POT);
            }
            case "reservado" -> {
                // em breve: tábuas e caixotes vazios
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Material.SPRUCE_PLANKS);
                    bset(b, ao, 2, 7, Math.floorMod(ao, 2) == 0 ? Material.BARREL : Material.SPRUCE_PLANKS);
                    bset(b, ao, 3, 7, Material.STRIPPED_SPRUCE_LOG);
                }
                bset(b, a, 1, 5, Material.BARREL);
                bset(b, z, 1, 4, Material.SCAFFOLDING);
            }
            default -> { // minerador
                Material[] minerios = {Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE,
                        Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_DIAMOND_ORE};
                for (int ao = a; ao <= z; ao++) {
                    bset(b, ao, 1, 7, Material.DEEPSLATE_BRICKS);
                    bset(b, ao, 2, 7, minerios[Math.floorMod(ao, minerios.length)]);
                    bset(b, ao, 3, 7, Material.DEEPSLATE_TILES);
                }
                bset(b, a, 1, 5, direcional(Material.ANVIL, BlockFace.NORTH));
                bset(b, a, 1, 4, Material.BLAST_FURNACE);
                bset(b, z, 1, 5, Material.IRON_BLOCK);
                bset(b, z, 1, 4, Material.GOLD_BLOCK);
                bset(b, z, 2, 5, Material.DIAMOND_BLOCK);
                bset(b, m, 5, 6, eixo(Material.IRON_CHAIN, Axis.Y));
            }
        }
    }

    private static BlockData vela(Material m, int quantas) {
        Candle v = (Candle) m.createBlockData();
        v.setCandles(quantas);
        v.setLit(true);
        return v;
    }

    // ------------------------------------------------------------------ porta do porto

    /**
     * No canto do fundo, do lado do Banco, uma porta na parede norte e um corredor coberto até o muro do
     * porto da /warp pesca (estrutura "porto", 2026-11-17), que começa no z 14 (dz -29).
     */
    private void portaDoPorto() {
        int x1 = 149, x2 = 151;
        for (int x = x1; x <= x2; x++) {
            for (int y = 1; y <= 3; y++) set(x, y, -DZ_PAREDE, Material.AIR);
            set(x, 0, -DZ_PAREDE, Material.POLISHED_ANDESITE);
            set(x, 4, -DZ_PAREDE, VERMELHO);
        }
        for (int y = 1; y <= 5; y++) {
            set(x1 - 1, y, -DZ_PAREDE + 1, eixo(Material.CRIMSON_STEM, Axis.Y));
            set(x2 + 1, y, -DZ_PAREDE + 1, eixo(Material.CRIMSON_STEM, Axis.Y));
        }
        for (int x = x1 - 1; x <= x2 + 1; x++) set(x, 6, -DZ_PAREDE + 1, VERMELHO);
        set(x1 + 1, 5, -DZ_PAREDE + 1, Material.SHROOMLIGHT);
        // o corredor: de tijolo, coberto, com lanternas e o chão de tábua do porto
        for (int dz = -DZ_PAREDE - 1; dz >= -DZ_PAREDE - 8; dz--) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                set(x, -1, dz, Material.STONE);
                boolean parede = x == x1 - 1 || x == x2 + 1;
                set(x, 0, dz, parede ? PEDRA : Material.SPRUCE_PLANKS);
                for (int y = 1; y <= 4; y++) set(x, y, dz, parede ? pedra(x, y, dz) : Material.AIR);
                set(x, 5, dz, parede ? FAIXA : Material.SPRUCE_PLANKS);
                set(x, 6, dz, laje(Material.SPRUCE_SLAB, false));
            }
            if (Math.floorMod(dz, 4) == 0) set(x1 + 1, 4, dz, lanterna(Material.LANTERN, true));
        }
    }

    // ------------------------------------------------------------------ telhado e fachada

    private void telhado() {
        // laje por cima do teto e ameias nas beiradas
        for (int x = X_FRENTE; x <= X_FUNDO; x++) {
            for (int dz = -DZ_PAREDE; dz <= DZ_PAREDE; dz++) {
                boolean beira = x == X_FRENTE || x == X_FUNDO || Math.abs(dz) == DZ_PAREDE;
                if (Math.abs(dz) <= MEIA_NAVE + 1 && !beira) continue;
                set(x, ALTURA + 1, dz, beira ? pedra(x, ALTURA + 1, dz).createBlockData() : laje(Material.STONE_BRICK_SLAB, false));
                if (beira && Math.floorMod(x + dz, 2) == 0) set(x, ALTURA + 2, dz, pedra(x, ALTURA + 2, dz));
            }
        }
        // clerestório: paredes com janelas e telhado de vidro sobre a nave
        for (int x = X_FRENTE; x <= X_FUNDO; x++) {
            for (int s : new int[]{-1, 1}) {
                int dz = s * (MEIA_NAVE + 2);
                for (int y = ALTURA; y < Y_CLERESTORIO; y++) {
                    boolean janela = y > ALTURA && Math.floorMod(x - X_FRENTE, 4) != 0 && x != X_FRENTE && x != X_FUNDO;
                    set(x, y, dz, janela ? conectado(Material.GLASS_PANE, BlockFace.EAST, BlockFace.WEST)
                            : (y == ALTURA ? FAIXA.createBlockData() : pedra(x, y, dz).createBlockData()));
                }
                set(x, Y_CLERESTORIO, dz, FAIXA);
            }
            for (int dz = -MEIA_NAVE - 1; dz <= MEIA_NAVE + 1; dz++) {
                set(x, Y_CLERESTORIO, dz, x == X_FRENTE || x == X_FUNDO ? FAIXA : Material.GLASS);
            }
        }
        // pontas do clerestório (em cima da parede da frente e da do fundo): fechadas com vidro
        for (int x : new int[]{X_FRENTE, X_FUNDO}) {
            for (int dz = -MEIA_NAVE - 1; dz <= MEIA_NAVE + 1; dz++) {
                for (int y = ALTURA + 1; y < Y_CLERESTORIO; y++) {
                    set(x, y, dz, conectado(Material.GLASS_PANE, BlockFace.NORTH, BlockFace.SOUTH));
                }
            }
        }
        // torres nos cantos
        for (int x : new int[]{X_FRENTE, X_FUNDO - 3}) {
            for (int s : new int[]{-1, 1}) {
                int z0 = s < 0 ? -DZ_PAREDE - 1 : DZ_PAREDE - 3;
                for (int dx = 0; dx < 5; dx++) {
                    for (int dz = 0; dz < 5; dz++) {
                        for (int y = 0; y <= ALTURA + 6; y++) {
                            boolean dentroDoSalao = (x + dx) > X_FRENTE && (x + dx) < X_FUNDO && Math.abs(z0 + dz) < DZ_PAREDE;
                            if (dentroDoSalao && y >= 1 && y < ALTURA) continue; // a torre não invade o salão
                            Material mm = (y == 1 || y == ALTURA + 6) ? FAIXA : (y == ALTURA + 3 ? VERMELHO : pedra(x + dx, y, z0 + dz));
                            set(x + dx, y, z0 + dz, mm);
                        }
                        boolean beira = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                        if (beira && Math.floorMod(dx + dz, 2) == 0) set(x + dx, ALTURA + 7, z0 + dz, pedra(x + dx, ALTURA + 7, z0 + dz));
                    }
                }
                set(x + 2, ALTURA + 7, z0 + 2, lanterna(Material.LANTERN, false));
            }
        }
    }

    /**
     * A porta é a boca do túnel do portão leste do spawn, com o mesmo arco (9 no meio, 8 e 7 nas
     * beiradas) e o mesmo chão; por dentro, a moldura de caule carmesim e cerejeira dos portões.
     */
    private void fachada() {
        int x = X_FRENTE;
        for (int dz = -3; dz <= 3; dz++) {
            int topo = Math.abs(dz) <= 1 ? 9 : (Math.abs(dz) == 2 ? 8 : 7);
            for (int y = 1; y <= topo; y++) set(x, y, dz, Material.AIR);
            // o calçamento do túnel continua (tijolo nas beiradas, andesito no meio)
            set(x, 0, dz, Math.abs(dz) == 3 ? PEDRA : Material.POLISHED_ANDESITE);
        }
        int dentro = x + 1;
        for (int s : new int[]{-1, 1}) {
            for (int y = 1; y <= 9; y++) {
                set(dentro, y, s * 4, eixo(Material.CRIMSON_STEM, Axis.Y));
                set(dentro, y, s * 5, eixo(Material.CHERRY_LOG, Axis.Y));
            }
            set(dentro, 1, s * 6, folha(Material.FLOWERING_AZALEA_LEAVES));
            set(dentro, 2, s * 6, lanterna(Material.LANTERN, false));
        }
        for (int dz = -5; dz <= 5; dz++) set(dentro, 10, dz, dz == 0 ? Material.SHROOMLIGHT : VERMELHO);
    }

    // ------------------------------------------------------------------ integração

    /**
     * Os vendedores (EconomyShopPlugin) e os quadros com o que cada um vende. {@code oz} é o z do centro
     * do spawn (43): os dz daqui viram absolutos. O nome de cada balcão em cima do toldo (um holograma)
     * saiu em 2026-10-31: ficava atravessado no telhado, os emojis não apareciam, e o NPC já diz de que
     * é a loja. O kill de text_display com a tag "loja" fica, pra apagar os de mapas antigos.
     */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        List<String> c = new ArrayList<>();
        String em = "execute in minecraft:" + mundo + " run ";
        int yPe = oy + 1;
        c.add(em + "minecraft:kill @e[type=text_display,tag=loja,x=100,y=" + yPe + ",z=" + oz + ",distance=..60]");
        c.add(em + "minecraft:kill @e[type=item_frame,tag=loja,x=100,y=" + yPe + ",z=" + oz + ",distance=..60]");
        Map<String, String[]> quadros = new java.util.HashMap<>(Map.of(
                "construtor", new String[]{"bricks", "white_concrete"}, "lenhador", new String[]{"oak_log", "iron_axe"},
                "jardineiro", new String[]{"poppy", "oak_sapling"}, "fazendeiro", new String[]{"wheat", "carrot"},
                "cacador", new String[]{"bone", "bow"}, "alquimista", new String[]{"brewing_stand", "redstone"},
                "minerador", new String[]{"diamond", "iron_pickaxe"}, "spawners", new String[]{"spawner", "pig_spawn_egg"},
                "maquinas", new String[]{"piston", "observer"}, "pvp", new String[]{"diamond_sword", "shield"}));
        quadros.put("fazendas", new String[]{"hay_block", "egg"}); // o Map.of vai só até 10 pares
        for (Balcao b : BALCOES) {
            double nx, nz;
            int yaw;
            if (b.lado() == 0) {
                // 2 atrás do balcão: o jogador alcança (o clique em entidade vai até ~3 blocos)
                nx = X_FUNDO - 8 + 2 + 0.5;
                nz = oz + b.meio() + 0.5;
                yaw = 90;
            } else {
                nx = b.meio() + 0.5;
                nz = oz + b.lado() * 14 + 0.5;
                yaw = b.lado() < 0 ? 0 : 180;
            }
            if (b.id().equals("pvp")) {
                // o vendedor de PvP é o Armeiro do OficinasPlugin (lista de itens com encantamento e poção)
                c.add("oficina criar pvp armeiro " + mundo + " " + nx + " " + yPe + " " + nz + " " + yaw);
            } else if (!b.reservado()) {
                c.add("shop admin npc " + b.id() + " " + mundo + " " + nx + " " + yPe + " " + nz + " " + yaw);
            }
            // quadros na vitrine (f = 7, em cima da decoração), presos na parede do salão, olhando pra nave
            String[] itens = quadros.get(b.id());
            if (itens == null) continue; // o reservado fica sem quadros
            int facing = b.lado() == 0 ? 4 : (b.lado() < 0 ? 3 : 2); // 4 oeste, 3 sul, 2 norte
            for (int i = 0; i < 2; i++) {
                int ao = i == 0 ? b.ini() + 2 : b.fim() - 2;
                double fx, fz;
                if (b.lado() == 0) {
                    fx = X_FUNDO - 8 + 7;
                    fz = oz + ao;
                } else {
                    fx = ao;
                    fz = oz + b.lado() * (12 + 7);
                }
                c.add(em + "summon minecraft:item_frame " + (int) fx + " " + (oy + 4) + " " + (int) fz + " {Facing:" + facing
                        + "b,Fixed:1b,Invulnerable:1b,Tags:[\"loja\"],Item:{id:\"minecraft:" + itens[i] + "\",count:1}}");
            }
        }
        c.add(BancoDaMina.comandoNpc(mundo, oy, oz)); // o Bancário, no banco do fundo
        return c;
    }
}
