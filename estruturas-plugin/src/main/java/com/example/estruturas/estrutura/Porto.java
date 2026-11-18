package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.List;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Porto da /warp pesca (2026-11-17): um recinto murado com um lago no meio, um píer de madeira atravessando e
 * 16 barraquinhas de pesca na beira (8 em cada margem), cada uma com o toldo listrado, o barril (o estoque,
 * do PescaPlugin) e o lugar de pescar virado pro lago. Fica longe de tudo, solto no vazio do mundo spawn,
 * sem estrada nem porta: só se chega pela /warp pesca e só se sai por comando (2026-11-18; antes ficava ao
 * norte da Loja Oficial, com uma porta no fundo dela).
 *
 * <p>Construir com {@code /estrutura construir porto spawn 400 65 0}: x e z são relativos à origem e y ao
 * chão (y = 0 é o piso em 64). Com essa origem, ocupa x 480..560, z -40..14.
 */
public class Porto implements Estrutura {

    static final int X_OESTE = 80, X_LESTE = 160, Z_NORTE = -40, Z_SUL = 14;
    static final int LAGO_X1 = 88, LAGO_X2 = 152, LAGO_Z1 = -30, LAGO_Z2 = 4;
    static final int PIER_X1 = 119, PIER_X2 = 121;
    private static final int ALTURA = 7;

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material FAIXA = Material.SMOOTH_STONE;
    private static final Material TABUA = Material.SPRUCE_PLANKS;
    private static final Material[][] TOLDOS = {
            {Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL}, {Material.WHITE_WOOL, Material.RED_WOOL},
            {Material.WHITE_WOOL, Material.YELLOW_WOOL}, {Material.WHITE_WOOL, Material.LIME_WOOL},
            {Material.WHITE_WOOL, Material.ORANGE_WOOL}, {Material.WHITE_WOOL, Material.CYAN_WOOL},
            {Material.WHITE_WOOL, Material.PINK_WOOL}, {Material.WHITE_WOOL, Material.BLUE_WOOL}};

    private Planta p;

    @Override
    public String getId() {
        return "porto";
    }

    @Override
    public String getNome() {
        return "Porto (warp pesca)";
    }

    /** As 16 barraquinhas: x inicial, se é da margem sul, e o número. */
    record Barraquinha(String id, int x, boolean sul) {
        int zFrente() {
            return sul ? 5 : -31;
        }

        int zFundo() {
            return sul ? 8 : -34;
        }

        int[] barril() {
            return new int[]{x + 1, 1, zFundo()};
        }
    }

    static List<Barraquinha> barraquinhas() {
        List<Barraquinha> l = new ArrayList<>();
        for (int i = 0; i < 8; i++) l.add(new Barraquinha(String.valueOf(i + 1), 90 + 8 * i, true));
        for (int i = 0; i < 8; i++) l.add(new Barraquinha(String.valueOf(i + 9), 90 + 8 * i, false));
        return l;
    }

    private void set(int x, int y, int z, BlockData d) {
        p.set(x, y, z, d);
    }

    private void set(int x, int y, int z, Material m) {
        p.set(x, y, z, m);
    }

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

    @Override
    public void gerar(Planta planta) {
        this.p = planta;
        limparEFundar();
        lago();
        caminhos();
        muralha();
        for (Barraquinha b : barraquinhas()) barraquinha(b);
        pier();
        margens();
    }

    // ------------------------------------------------------------------ base

    private void limparEFundar() {
        for (int x = X_OESTE; x <= X_LESTE; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                for (int y = 1; y <= ALTURA + 8; y++) set(x, y, z, Material.AIR);
                for (int y = -6; y <= -1; y++) set(x, y, z, Material.STONE);
                set(x, 0, z, Material.GRASS_BLOCK);
            }
        }
    }

    /** Lago de 3 de fundo: leito de areia e cascalho com lanternas do mar, água até 1 abaixo do chão. */
    private void lago() {
        for (int x = LAGO_X1; x <= LAGO_X2; x++) {
            for (int z = LAGO_Z1; z <= LAGO_Z2; z++) {
                int h = Math.floorMod(x * 31 + z * 17, 13);
                Material leito = h == 0 ? Material.SEA_LANTERN : h < 4 ? Material.GRAVEL : h < 6 ? Material.CLAY : Material.SAND;
                set(x, -4, z, leito);
                for (int y = -3; y <= -1; y++) set(x, y, z, Material.WATER);
                set(x, 0, z, Material.AIR);
                if (h == 7 && (z - LAGO_Z1) > 4 && (LAGO_Z2 - z) > 4 && Math.abs(x - (PIER_X1 + 1)) > 4) {
                    set(x, -3, z, Material.SEAGRASS);
                }
            }
        }
        // a beira do lago: tijolo de pedra no nível do chão (onde não tem barraquinha nem píer, fica o meio-fio)
        for (int x = LAGO_X1 - 1; x <= LAGO_X2 + 1; x++) {
            for (int z = LAGO_Z1 - 1; z <= LAGO_Z2 + 1; z++) {
                boolean borda = x == LAGO_X1 - 1 || x == LAGO_X2 + 1 || z == LAGO_Z1 - 1 || z == LAGO_Z2 + 1;
                if (borda) set(x, 0, z, Material.MOSSY_STONE_BRICKS);
            }
        }
    }

    /** Calçadões de tábua atrás das barraquinhas (sul e norte) e nas pontas. */
    private void caminhos() {
        for (int x = X_OESTE + 3; x <= X_LESTE - 3; x++) {
            for (int z : new int[]{9, 10, 11, -35, -36, -37}) set(x, 0, z, (x % 8 == 0) ? Material.STRIPPED_SPRUCE_LOG.createBlockData() : TABUA.createBlockData());
            if (x % 8 == 4) {
                set(x, 0, 10, Material.SHROOMLIGHT);
                set(x, 0, -36, Material.SHROOMLIGHT);
            }
        }
        // as pontas, ligando os dois calçadões pela beira do lago
        for (int z = -37; z <= 11; z++) {
            for (int x : new int[]{84, 85, 155, 156}) set(x, 0, z, TABUA);
        }
    }

    // ------------------------------------------------------------------ muralha

    private void muralha() {
        for (int x = X_OESTE; x <= X_LESTE; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                boolean oeste = x <= X_OESTE + 2, leste = x >= X_LESTE - 2, norte = z <= Z_NORTE + 2, sul = z >= Z_SUL - 2;
                if (!oeste && !leste && !norte && !sul) continue;
                int d = oeste ? x - X_OESTE : leste ? X_LESTE - x : norte ? z - Z_NORTE : Z_SUL - z; // 0 = face de fora
                int u = (oeste || leste) ? z : x;
                for (int y = -1; y <= ALTURA; y++) {
                    Material m = (y == 1 || y == ALTURA) ? FAIXA : pedra(x, y, z);
                    if (d == 0 && y == 5) m = Material.RED_NETHER_BRICKS;
                    if (d == 2 && Math.floorMod(u, 8) == 0 && y >= 2 && y <= 6) m = (y == 4) ? Material.SHROOMLIGHT : Material.STRIPPED_SPRUCE_LOG;
                    set(x, y, z, m);
                }
                if (d != 1 && Math.floorMod(u, 2) == 0) set(x, ALTURA + 1, z, pedra(x, ALTURA + 1, z));
                if (d == 1 && Math.floorMod(u, 8) == 4) set(x, ALTURA + 1, z, lanterna(Material.LANTERN, false));
            }
        }
    }

    // ------------------------------------------------------------------ barraquinhas

    /**
     * Barraquinha de 5x4: piso de tábua, quatro postes, toldo listrado, grade dos lados, o barril (o
     * estoque) e um caldeirão no fundo, entrada pelo meio do fundo e a frente aberta pro lago.
     */
    private void barraquinha(Barraquinha b) {
        int a = b.x(), zf = b.zFrente(), zb = b.zFundo(), passo = b.sul() ? 1 : -1;
        Material[] toldo = TOLDOS[Math.floorMod(Integer.parseInt(b.id()) - 1, TOLDOS.length)];
        for (int x = a; x <= a + 4; x++) {
            for (int z = Math.min(zf, zb); z <= Math.max(zf, zb); z++) {
                set(x, 0, z, Material.STRIPPED_SPRUCE_WOOD);
                for (int y = 1; y <= 3; y++) set(x, y, z, Material.AIR);
                set(x, 4, z, toldo[(x - a) % 2]);
            }
            // aba do toldo por cima da beira do lago
            set(x, 4, zf - passo, laje(Material.SPRUCE_SLAB, true));
        }
        for (int x : new int[]{a, a + 4}) {
            for (int z : new int[]{zf, zb}) {
                for (int y = 1; y <= 3; y++) set(x, y, z, eixo(Material.SPRUCE_LOG, Axis.Y));
            }
            for (int z = zf + passo; z != zb; z += passo) set(x, 1, z, conectado(Material.SPRUCE_FENCE, BlockFace.NORTH, BlockFace.SOUTH));
        }
        int[] barril = b.barril();
        set(barril[0], 1, barril[2], direcional(Material.BARREL, BlockFace.UP));
        set(a + 3, 1, zb, Material.WATER_CAULDRON);
        set(a + 2, 3, (zf + zb) / 2, lanterna(Material.LANTERN, true));
        // o lugar de pescar, na frente, marcado com tábua mais escura
        set(a + 2, 0, zf, Material.DARK_OAK_PLANKS);
        // o calçadão chega na entrada do fundo
        set(a + 2, 0, zb, Material.STRIPPED_SPRUCE_WOOD);
    }

    // ------------------------------------------------------------------ píer e margens

    /** Píer de tábua cruzando o lago, entre a 4ª e a 5ª barraquinha de cada margem, com grade e lanternas. */
    private void pier() {
        for (int z = LAGO_Z1 - 4; z <= LAGO_Z2 + 4; z++) {
            for (int x = PIER_X1; x <= PIER_X2; x++) set(x, 0, z, TABUA);
            if (z >= LAGO_Z1 && z <= LAGO_Z2) {
                for (int x : new int[]{PIER_X1 - 1, PIER_X2 + 1}) {
                    boolean poste = Math.floorMod(z, 6) == 0;
                    set(x, 0, z, poste ? eixo(Material.SPRUCE_LOG, Axis.Y) : TABUA.createBlockData());
                    set(x, 1, z, conectado(Material.SPRUCE_FENCE, BlockFace.NORTH, BlockFace.SOUTH));
                    if (poste) {
                        for (int y = -4; y <= -1; y++) set(x, y, z, eixo(Material.SPRUCE_LOG, Axis.Y));
                        set(x, 2, z, lanterna(Material.LANTERN, false));
                    }
                }
            }
        }
        // no meio do píer, um mirante com bancos
        int zm = (LAGO_Z1 + LAGO_Z2) / 2;
        for (int x = PIER_X1 - 3; x <= PIER_X2 + 3; x++) {
            for (int z = zm - 3; z <= zm + 3; z++) {
                boolean borda = x == PIER_X1 - 3 || x == PIER_X2 + 3 || z == zm - 3 || z == zm + 3;
                set(x, 0, z, TABUA);
                set(x, 1, z, borda ? conectado(Material.SPRUCE_FENCE, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
                        : Material.AIR.createBlockData());
            }
        }
        for (int x = PIER_X1; x <= PIER_X2; x++) {
            set(x, 1, zm - 3, Material.AIR);
            set(x, 1, zm + 3, Material.AIR);
        }
        set(PIER_X1 - 2, 1, zm, escada(Material.SPRUCE_STAIRS, BlockFace.WEST, false));
        set(PIER_X2 + 2, 1, zm, escada(Material.SPRUCE_STAIRS, BlockFace.EAST, false));
        for (int[] c : new int[][]{{PIER_X1 - 2, zm - 2}, {PIER_X2 + 2, zm - 2}, {PIER_X1 - 2, zm + 2}, {PIER_X2 + 2, zm + 2}}) {
            set(c[0], 1, c[1], Material.SPRUCE_FENCE);
            set(c[0], 2, c[1], lanterna(Material.LANTERN, false));
        }
    }

    /** As pontas do porto (fora do lago): grama com moitas, flores, barris e redes de pesca (teia). */
    private void margens() {
        for (int x = X_OESTE + 3; x <= X_LESTE - 3; x++) {
            for (int z = Z_NORTE + 3; z <= Z_SUL - 3; z++) {
                boolean ponta = x < LAGO_X1 - 1 && (x < 84 || x > 85) || x > LAGO_X2 + 1 && (x < 155 || x > 156);
                if (!ponta || (z >= 9 && z <= 11) || (z >= -37 && z <= -35)) continue;
                int h = Math.floorMod(x * 7 + z * 13, 11);
                if (h == 0) set(x, 1, z, folha(Material.AZALEA_LEAVES));
                else if (h == 1) set(x, 1, z, Material.CORNFLOWER);
                else if (h == 2) set(x, 1, z, Material.SHORT_GRASS);
                else if (h == 3 && Math.floorMod(z, 3) == 0) set(x, 1, z, direcional(Material.BARREL, BlockFace.UP));
            }
        }
    }

    /** Cadastra as 16 barraquinhas no PescaPlugin (a caixa de quem pesca e o barril). */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        List<String> c = new ArrayList<>();
        int chao = oy; // o piso (y 0 da planta): o oy já é o y do chão
        for (Barraquinha b : barraquinhas()) {
            int z1 = Math.min(b.zFrente(), b.zFundo()), z2 = Math.max(b.zFrente(), b.zFundo());
            int[] br = b.barril();
            c.add("pesca admin barraca " + b.id() + " " + mundo + " " + (ox + b.x()) + " " + chao + " " + (oz + z1) + " "
                    + (ox + b.x() + 4) + " " + (chao + 4) + " " + (oz + z2) + " " + (ox + br[0]) + " " + (chao + br[1]) + " " + (oz + br[2]));
        }
        return c;
    }
}
