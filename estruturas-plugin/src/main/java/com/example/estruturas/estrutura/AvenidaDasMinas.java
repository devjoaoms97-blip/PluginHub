package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.TrapDoor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.lanterna;
import static com.example.estruturas.estrutura.Planta.laje;

/**
 * Avenida das Minas: recinto retangular murado em volta das 12 minas do mundo "spawn", no mesmo
 * estilo do spawn (tijolo de pedra misturado, faixas lisas, detalhes vinho, cerejeiras e azaleias).
 * A única entrada é o portão sul, encaixado no caminho que sai do portão norte do spawn.
 *
 * <p>Feita pra ser construída com {@code /estrutura construir minas spawn 0 65 -21}: a origem da
 * planta é o bloco de chão ABSOLUTO (0, 64, -21). Por isso tudo aqui é escrito com x e z
 * ABSOLUTOS e y relativo ao chão (y = 0 é o piso em 64); o {@link #set} converte z na hora de gravar.
 *
 * <p>Os poços das minas (e a borda de tijolo em volta deles) são do plugin de minas: nada é gravado
 * nessas caixas em y &lt;= 0 (64 absoluto), e acima delas só entra ar — sem telhado, pro jogador ver e
 * minerar. Os pontos de teleporte de cada mina ficam com ar em volta e chão firme.
 */
public class AvenidaDasMinas implements Estrutura {

    /** z absoluto da origem da planta (o x da origem é 0 e o y é o chão). */
    private static final int ORIGEM_Z = -21;

    // Recinto (absoluto). Muralha de 3 de espessura; o miolo é x -37..37, z -24..-182.
    private static final int X_EXT = 40;
    private static final int X_INT = 37;
    private static final int Z_SUL = -21;
    private static final int Z_SUL_INT = -24;
    private static final int Z_NORTE = -185;
    private static final int Z_NORTE_INT = -182;

    private static final int ALTURA = 12;      // topo da muralha (y relativo)
    private static final int Y_LIMPAR = 16;    // ar até aqui (muralha + ameias + 3)

    // Arcadas cobertas nas laterais (x ±34..±37) e calçada aberta rente aos poços (x ±31..±33).
    private static final int X_ARCADA = 34;
    private static final int Z_ARCADA_SUL = -26;
    private static final int Z_ARCADA_NORTE = -180;

    private static final int MEIO_PORTAO = 4;  // abertura x -4..4, igual ao caminho do spawn

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material FAIXA = Material.SMOOTH_STONE;
    private static final Material VERMELHO = Material.RED_NETHER_BRICKS;
    private static final Material CALCADA = Material.POLISHED_ANDESITE;
    private static final Material LUZ = Material.SHROOMLIGHT;

    // ------------------------------------------------------------------ minas

    /** Uma mina (poço) em coordenadas absolutas; a borda de tijolo é a caixa 1 bloco maior. */
    public record Mina(int indice, boolean oeste, int xMin, int xMax, int zMin, int zMax) {
        int xc() {
            return (xMin + xMax) / 2;
        }

        int zc() {
            return (zMin + zMax) / 2;
        }

        /** Bloco do chão onde o jogador aparece (x -6.5 / 7.5 → bloco -7 / 7). */
        int xTeleporte() {
            return oeste ? -7 : 7;
        }

        boolean naBorda(int x, int z) {
            return x >= xMin - 1 && x <= xMax + 1 && z >= zMin - 1 && z <= zMax + 1;
        }
    }

    public static final List<Mina> MINAS = Collections.unmodifiableList(calcularMinas());

    private static List<Mina> calcularMinas() {
        List<Mina> lista = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int linha = i / 2;
            boolean oeste = i % 2 == 0;
            int zMax = -30 - 25 * linha;
            int zMin = zMax - 20;
            int xMin = oeste ? -29 : 9;
            lista.add(new Mina(i, oeste, xMin, xMin + 20, zMin, zMax));
        }
        return lista;
    }

    /** (x, z) cai dentro de algum poço ou da borda de tijolo dele. */
    static boolean naBordaDeMina(int x, int z) {
        for (Mina m : MINAS) {
            if (m.naBorda(x, z)) return true;
        }
        return false;
    }

    /**
     * Pode gravar {@code material} em (x absoluto, y relativo, z absoluto)? Fora do recinto e ao sul
     * de z = -21 nunca; na caixa de uma mina só ar, e só acima do chão.
     */
    static boolean permitido(int x, int y, int z, Material material) {
        if (z > Z_SUL || z < Z_NORTE || Math.abs(x) > X_EXT) return false;
        if (naBordaDeMina(x, z)) return y >= 1 && material == Material.AIR;
        return true;
    }

    // ------------------------------------------------------------------ Estrutura

    private Planta p;

    @Override
    public String getId() {
        return "minas";
    }

    @Override
    public String getNome() {
        return "Avenida das Minas";
    }

    @Override
    public void gerar(Planta planta) {
        this.p = planta;
        limpar();
        gerarPiso();
        gerarAvenidaCentral();
        gerarPontaNorte();
        gerarLaterais();
        gerarPracaDeEntrada();
        gerarMuralha();
        gerarTorres();
        gerarPortao();
        gerarLuzes();
        gerarLiquens();
        liberarTeleportes();
        verificar();
    }

    // ------------------------------------------------------------------ gravação filtrada

    /** Grava em (x absoluto, y relativo ao chão, z absoluto), pulando tudo que é proibido. */
    private void set(int x, int y, int z, BlockData dados) {
        if (!permitido(x, y, z, dados.getMaterial())) return;
        p.set(x, y, z - ORIGEM_Z, dados);
    }

    private void set(int x, int y, int z, Material material) {
        set(x, y, z, material.createBlockData());
    }

    private Material get(int x, int y, int z) {
        return p.get(x, y, z - ORIGEM_Z);
    }

    // ------------------------------------------------------------------ utilidades

    private static double raio(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /** Tijolo de pedra misturado com musgo e rachado, sempre igual na mesma posição (como no spawn). */
    private static Material pedra(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h = Math.floorMod(h ^ (h >>> 13), 100);
        if (h < 62) return PEDRA;
        if (h < 86) return Material.MOSSY_STONE_BRICKS;
        return Material.CRACKED_STONE_BRICKS;
    }

    /** Folhas sem física somem se não forem persistentes. */
    private static BlockData folha(Material material) {
        Leaves folhas = (Leaves) material.createBlockData();
        folhas.setPersistent(true);
        return folhas;
    }

    private static BlockData alcapaoAberto(Material material, BlockFace face) {
        TrapDoor dados = (TrapDoor) material.createBlockData();
        dados.setFacing(face);
        dados.setOpen(true);
        return dados;
    }

    /** Distância (ao longo da parede) até o centro do arco cego mais próximo; arcos a cada 8. */
    private static int distArco(int u, int fase) {
        int k = Math.floorMod(u - fase, 8);
        return Math.min(k, 8 - k);
    }

    /** O que esta estrutura grava e não serve de chão nem de apoio (evita depender do registro do servidor). */
    private static final EnumSet<Material> NAO_SOLIDOS = EnumSet.of(
            Material.AIR, Material.GLOW_LICHEN, Material.LANTERN, Material.SHORT_GRASS, Material.POPPY,
            Material.DANDELION, Material.OXEYE_DAISY, Material.AZURE_BLUET, Material.ALLIUM, Material.CORNFLOWER,
            Material.PINK_TULIP, Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY);

    private static boolean solido(Material m) {
        return m != null && !NAO_SOLIDOS.contains(m);
    }

    private static final Material[] FLORES = {
            Material.POPPY, Material.DANDELION, Material.OXEYE_DAISY, Material.AZURE_BLUET,
            Material.ALLIUM, Material.CORNFLOWER, Material.PINK_TULIP, Material.WHITE_TULIP,
            Material.LILY_OF_THE_VALLEY
    };

    // ------------------------------------------------------------------ base

    /** Ar em todo o recinto até Y_LIMPAR (por cima dos poços também: ali só entra ar). */
    private void limpar() {
        BlockData ar = Material.AIR.createBlockData();
        for (int x = -X_EXT; x <= X_EXT; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                for (int y = 1; y <= Y_LIMPAR; y++) set(x, y, z, ar);
            }
        }
    }

    private void gerarPiso() {
        for (int x = -X_INT; x <= X_INT; x++) {
            for (int z = Z_NORTE_INT; z <= Z_SUL_INT; z++) {
                set(x, 0, z, pisoBase(x, z));
            }
        }
    }

    private static Material pisoBase(int x, int z) {
        int ax = Math.abs(x);
        boolean faixaArcada = z <= Z_ARCADA_SUL && z >= Z_ARCADA_NORTE;
        if (ax >= X_ARCADA && faixaArcada) {
            // Chão das arcadas: linha de tijolo sob as colunas, tapete de diorito no meio.
            if (ax == X_ARCADA) return PEDRA;
            if (Math.floorMod(z, 8) == 0) return FAIXA;
            return ax == 36 ? Material.POLISHED_DIORITE : CALCADA;
        }
        if (z >= -28 || z <= -177) {
            // Praças das pontas (entrada e fundo): lajotas com faixas lisas a cada 8.
            if (Math.floorMod(x, 8) == 0) return FAIXA;
            return CALCADA;
        }
        if (ax <= 7) return calcadaCentral(x, z);
        if (ax >= 31) {
            // Calçada aberta entre as arcadas e os poços.
            if (ax == 33) return PEDRA;
            return Math.floorMod(z, 8) == 0 ? FAIXA : CALCADA;
        }
        // Travessas de 2 entre uma linha de minas e a outra.
        return Math.floorMod(x, 4) == 0 ? FAIXA : CALCADA;
    }

    /** Passeio da avenida central: andesito, meio-fio de tijolo junto do canteiro e faixas a cada 6. */
    private static Material calcadaCentral(int x, int z) {
        if (Math.abs(x) == 3) return PEDRA;
        if (Math.floorMod(z, 6) == 0) return FAIXA;
        return CALCADA;
    }

    // ------------------------------------------------------------------ avenida central (x -7..7)

    private void gerarAvenidaCentral() {
        for (int linha = 0; linha < 6; linha++) {
            int zc = -40 - 25 * linha;
            praca(zc);
            for (int s : new int[]{-1, 1}) {
                poste(s * 3, zc - 2);
                poste(s * 3, zc + 2);
            }
            if (linha < 5) {
                // Canteiro comprido entre uma praça e a outra, com árvore no meio.
                canteiro(zc - 22, zc - 3, 300 + linha);
                int zArvore = zc - 12;
                if (linha % 2 == 0) cerejeira(0, zArvore, 50 + linha, false);
                else azaleia(0, zArvore, 70 + linha);
                for (int s : new int[]{-1, 1}) {
                    BlockFace costas = s < 0 ? BlockFace.EAST : BlockFace.WEST;
                    banco(s * 3, zc - 7, costas);
                    banco(s * 3, zc - 17, costas);
                    poste(s * 7, zArvore);
                }
            }
        }
        // Trecho entre o portão e a primeira praça.
        canteiro(-36, -30, 299);
        for (int s : new int[]{-1, 1}) banco(s * 3, -33, s < 0 ? BlockFace.EAST : BlockFace.WEST);
    }

    /** Praça na altura de cada par de minas (é onde ficam os dois pontos de teleporte). */
    private void praca(int zc) {
        for (int x = -7; x <= 7; x++) {
            for (int z = zc - 2; z <= zc + 2; z++) {
                int dz = Math.abs(z - zc);
                Material m;
                if (dz == 2) m = PEDRA;
                else m = Math.floorMod(x + z, 2) == 0 ? CALCADA : Material.POLISHED_DIORITE;
                set(x, 0, z, m);
            }
        }
        set(0, 0, zc, Material.PEARLESCENT_FROGLIGHT);
        set(1, 0, zc, Material.CHISELED_STONE_BRICKS);
        set(-1, 0, zc, Material.CHISELED_STONE_BRICKS);
        set(0, 0, zc - 1, Material.CHISELED_STONE_BRICKS);
        set(0, 0, zc + 1, Material.CHISELED_STONE_BRICKS);
    }

    /**
     * Canteiro x -2..2 de z1 a z2: meio-fio de tijolo com laje, grama com moitas de azaleia, flores
     * e capim. A árvore (se houver) é plantada depois por cima.
     */
    private void canteiro(int z1, int z2, long semente) {
        Random random = new Random(semente);
        for (int x = -2; x <= 2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean borda = Math.abs(x) == 2 || z == z1 || z == z2;
                if (borda) {
                    set(x, 0, z, PEDRA);
                    set(x, 1, z, laje(Material.STONE_BRICK_SLAB, false));
                    continue;
                }
                set(x, -1, z, Material.DIRT);
                set(x, 0, z, Material.GRASS_BLOCK);
                int sorte = random.nextInt(100);
                if (sorte < 35) {
                    set(x, 1, z, folha(random.nextBoolean() ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                } else if (sorte < 72) {
                    set(x, 1, z, FLORES[random.nextInt(FLORES.length)]);
                } else if (sorte < 88) {
                    set(x, 1, z, Material.SHORT_GRASS);
                }
            }
        }
    }

    /** Cerejeira: tronco, galhos, copa larga e folhas pendendo. {@code grande} = a do fundo, num monte de musgo. */
    private void cerejeira(int cx, int cz, long semente, boolean grande) {
        Random random = new Random(semente);
        int base = 1;
        if (grande) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    double r = raio(x, z);
                    if (r <= 3.2) {
                        set(cx + x, 0, cz + z, Material.MOSS_BLOCK);
                        set(cx + x, 1, cz + z, r <= 2 || random.nextBoolean() ? Material.MOSS_BLOCK : Material.MOSSY_COBBLESTONE);
                    }
                    if (r <= 1.5) set(cx + x, 2, cz + z, Material.MOSS_BLOCK);
                }
            }
            base = 3;
        }
        int topo = base + (grande ? 6 : 6);
        for (int y = base; y <= topo; y++) set(cx, y, cz, eixo(Material.CHERRY_LOG, Axis.Y));
        int g = base + 4; // altura dos galhos
        set(cx + 1, g, cz, Material.CHERRY_WOOD);
        set(cx + 2, g + 1, cz, eixo(Material.CHERRY_LOG, Axis.Y));
        set(cx - 1, g + 1, cz, Material.CHERRY_WOOD);
        set(cx - 2, g + 2, cz, eixo(Material.CHERRY_LOG, Axis.Y));

        BlockData folhas = folha(Material.CHERRY_LEAVES);
        double[][] camadas = grande
                ? new double[][]{{topo, 4.0}, {topo + 1, 4.5}, {topo + 2, 4.0}, {topo + 3, 2.0}}
                : new double[][]{{topo, 3.5}, {topo + 1, 4.3}, {topo + 2, 3.4}, {topo + 3, 1.8}};
        for (double[] c : camadas) {
            int y = (int) c[0];
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    double r = raio(x, z);
                    if (r > c[1] + 0.3) continue;
                    if (r > c[1] - 0.8 && random.nextInt(3) == 0) continue;
                    if (get(cx + x, y, cz + z) == Material.CHERRY_LOG) continue;
                    set(cx + x, y, cz + z, folhas);
                }
            }
        }
        // Franja pendurada embaixo da copa.
        double franjaMax = grande ? 4.6 : 4.3;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double r = raio(x, z);
                if (r < 3.0 || r > franjaMax || random.nextInt(3) != 0) continue;
                set(cx + x, topo - 1, cz + z, folhas);
            }
        }
    }

    /** Árvore de azaleia: tronco de carvalho e copa redonda de folhas comuns e floridas. */
    private void azaleia(int cx, int cz, long semente) {
        Random random = new Random(semente);
        for (int y = 1; y <= 6; y++) set(cx, y, cz, eixo(Material.OAK_LOG, Axis.Y));
        set(cx, 0, cz, Material.ROOTED_DIRT);
        double[][] camadas = {{5, 3.0}, {6, 4.0}, {7, 3.7}, {8, 2.3}};
        for (double[] c : camadas) {
            int y = (int) c[0];
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    double r = raio(x, z);
                    if (r > c[1] + 0.3) continue;
                    if (r > c[1] - 0.8 && random.nextInt(3) == 0) continue;
                    if (x == 0 && z == 0 && y <= 6) continue;
                    Material m = random.nextInt(5) < 2 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES;
                    set(cx + x, y, cz + z, folha(m));
                }
            }
        }
    }

    /** Banco de 3 escadas de cerejeira com braços de alçapão; {@code costas} = lado do encosto. */
    private void banco(int cx, int cz, BlockFace costas) {
        boolean aoLongoDeX = costas == BlockFace.NORTH || costas == BlockFace.SOUTH;
        for (int i = -1; i <= 1; i++) {
            int x = cx + (aoLongoDeX ? i : 0), z = cz + (aoLongoDeX ? 0 : i);
            set(x, 1, z, escada(Material.CHERRY_STAIRS, costas, false));
        }
        for (int s : new int[]{-1, 1}) {
            int x = cx + (aoLongoDeX ? 2 * s : 0), z = cz + (aoLongoDeX ? 0 : 2 * s);
            BlockFace fora = aoLongoDeX ? (s > 0 ? BlockFace.EAST : BlockFace.WEST)
                    : (s > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
            set(x, 1, z, alcapaoAberto(Material.CHERRY_TRAPDOOR, fora));
        }
    }

    /** Poste igual ao do spawn: base de pedra, cerca escura e lanterna em cima. */
    private void poste(int x, int z) {
        set(x, 1, z, Material.STONE_BRICK_WALL);
        set(x, 2, z, Material.DARK_OAK_FENCE);
        set(x, 3, z, Material.DARK_OAK_FENCE);
        set(x, 4, z, lanterna(Material.LANTERN, false));
    }

    // ------------------------------------------------------------------ fundo (norte)

    /** Depois da última praça: canteiro, e a cerejeira grande no meio da praça do fundo com bancos. */
    private void gerarPontaNorte() {
        canteiro(-173, -169, 399);
        cerejeira(0, -178, 77, true);
        banco(-6, -178, BlockFace.EAST);
        banco(6, -178, BlockFace.WEST);
        banco(0, Z_NORTE_INT, BlockFace.NORTH);
        for (int s : new int[]{-1, 1}) {
            poste(s * 3, -174);
            poste(s * 4, Z_NORTE_INT);
        }
        canteiroDeParede(Z_NORTE_INT, 9, 35);
    }

    // ------------------------------------------------------------------ entrada (sul)

    private void gerarPracaDeEntrada() {
        canteiroDeParede(Z_SUL_INT, 9, 14);
        canteiroDeParede(Z_SUL_INT, 19, 35);
        for (int s : new int[]{-1, 1}) poste(s * 7, -26);
    }

    /** Faixa de grama colada na parede (z fixo, |x| entre a e b) com azaleias e flores. */
    private void canteiroDeParede(int z, int a, int b) {
        for (int s : new int[]{-1, 1}) {
            for (int ax = a; ax <= b; ax++) {
                int x = s * ax;
                set(x, 0, z, Material.GRASS_BLOCK);
                if (Math.floorMod(ax, 3) == 0) {
                    Material flor = switch (Math.floorMod(ax / 3, 3)) {
                        case 0 -> Material.PINK_TULIP;
                        case 1 -> Material.ALLIUM;
                        default -> Material.OXEYE_DAISY;
                    };
                    set(x, 1, z, flor);
                } else {
                    set(x, 1, z, folha(Math.floorMod(ax, 2) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                }
            }
        }
    }

    // ------------------------------------------------------------------ laterais (arcadas)

    /**
     * Arcadas cobertas encostadas na muralha leste e oeste (x ±34..±37): colunas de caule carmesim a
     * cada 8 (alinhadas com os pilares da muralha), teto de tijolo com laje por cima (mob não nasce
     * em laje de baixo), lanternas penduradas e bancos de frente pras minas.
     */
    private void gerarLaterais() {
        for (int s : new int[]{-1, 1}) {
            BlockFace praFora = s < 0 ? BlockFace.WEST : BlockFace.EAST;
            for (int z = Z_ARCADA_NORTE; z <= Z_ARCADA_SUL; z++) {
                boolean coluna = Math.floorMod(z, 8) == 0 || z == Z_ARCADA_SUL || z == Z_ARCADA_NORTE;
                int xc = s * X_ARCADA;
                if (coluna) {
                    set(xc, 1, z, Material.CHISELED_STONE_BRICKS);
                    for (int y = 2; y <= 4; y++) set(xc, y, z, eixo(Material.CRIMSON_STEM, Axis.Y));
                    set(xc, 5, z, VERMELHO);
                } else {
                    // Arquinho: escada invertida encostada em cada coluna.
                    if (Math.floorMod(z, 8) == 1 || z == Z_ARCADA_NORTE + 1) {
                        set(xc, 5, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.NORTH, true));
                    } else if (Math.floorMod(z, 8) == 7 || z == Z_ARCADA_SUL - 1) {
                        set(xc, 5, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH, true));
                    }
                }
                // Teto.
                for (int ax = X_ARCADA; ax <= X_INT; ax++) {
                    int x = s * ax;
                    set(x, 6, z, ax == X_ARCADA ? FAIXA : pedra(x, 6, z));
                    if (ax > X_ARCADA) set(x, 7, z, laje(Material.STONE_BRICK_SLAB, false));
                }
                // Parapeito da beira: bloco + lanterna em cima das colunas, azaleia no meio dos vãos.
                if (Math.floorMod(z, 8) == 0) {
                    set(xc, 7, z, pedra(xc, 7, z));
                    set(xc, 8, z, lanterna(Material.LANTERN, false));
                } else if (Math.floorMod(z, 8) == 4) {
                    set(xc, 7, z, folha(Material.FLOWERING_AZALEA_LEAVES));
                } else {
                    set(xc, 7, z, laje(Material.SMOOTH_STONE_SLAB, false));
                }
                if (Math.floorMod(z, 8) == 4) set(s * 36, 5, z, lanterna(Material.LANTERN, true));
                if (Math.floorMod(z, 16) == 4) banco(s * X_INT, z, praFora);
            }
        }
    }

    // ------------------------------------------------------------------ muralha

    private void gerarMuralha() {
        for (int x = -X_EXT; x <= X_EXT; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                int ax = Math.abs(x);
                if (ax > X_INT) muro(x, z, z, ax - (X_INT + 1), 4);
                else if (z > Z_SUL_INT) muro(x, z, x, (Z_SUL_INT + 1) - z, 0);
                else if (z < Z_NORTE_INT) muro(x, z, x, (Z_NORTE_INT - 1) - z, 0);
            }
        }
    }

    /**
     * Uma coluna da muralha. {@code u} = posição ao longo da parede, {@code d} = camada (0 = face de
     * dentro, 2 = face de fora). Dois andares de arcos cegos por dentro (fundo de cerejeira embaixo,
     * azaleia florida em cima, luz no meio), faixas lisas, faixa vinho por fora e ameias.
     */
    private void muro(int x, int z, int u, int d, int fase) {
        int du = distArco(u, fase);
        boolean vao = du <= 2;
        for (int y = -1; y <= ALTURA; y++) {
            BlockData dados;
            Material m = (y == 1 || y == 7 || y == ALTURA) ? FAIXA : pedra(x, y, z);
            if (d == 2 && y == 10) m = VERMELHO;
            dados = m.createBlockData();
            if (vao && d == 0) {
                boolean baixo = y >= 2 && y <= (du <= 1 ? 5 : 4);
                boolean alto = y >= 8 && y <= (du <= 1 ? 11 : 10);
                if (baixo || alto) dados = Material.AIR.createBlockData();
            } else if (vao && d == 1) {
                if (y >= 2 && y <= 5) dados = Material.CHERRY_PLANKS.createBlockData();
                if (y >= 8 && y <= 11) dados = folha(Material.FLOWERING_AZALEA_LEAVES);
                if (du == 0 && (y == 4 || y == 10)) dados = LUZ.createBlockData();
            }
            set(x, y, z, dados);
        }
        if (d != 1 && Math.floorMod(u, 2) == 0) set(x, ALTURA + 1, z, pedra(x, ALTURA + 1, z));
        if (d == 1 && distArco(u, fase + 4) == 0) set(x, ALTURA + 1, z, lanterna(Material.LANTERN, false));
    }

    /** Torres quadradas (5x5) nos quatro cantos, mais altas que a muralha. */
    private void gerarTorres() {
        for (int s : new int[]{-1, 1}) {
            for (int[] faixaZ : new int[][]{{-25, Z_SUL}, {Z_NORTE, -181}}) {
                int z1 = faixaZ[0], z2 = faixaZ[1];
                for (int ax = 36; ax <= X_EXT; ax++) {
                    int x = s * ax;
                    for (int z = z1; z <= z2; z++) {
                        boolean beirada = ax == 36 || ax == X_EXT || z == z1 || z == z2;
                        for (int y = -1; y <= 20; y++) {
                            Material m = (y == 1 || y == 7 || y == 12 || y == 20) ? FAIXA : pedra(x, y, z);
                            if (beirada && (y == 9 || y == 16)) m = VERMELHO;
                            set(x, y, z, m);
                        }
                        if (beirada && Math.floorMod(x + z, 2) == 0) set(x, 21, z, pedra(x, 21, z));
                    }
                }
                set(s * 38, 21, (z1 + z2) / 2, lanterna(Material.LANTERN, false));
            }
        }
    }

    // ------------------------------------------------------------------ portão sul

    private void gerarPortao() {
        // Frontispício: trecho da muralha mais alto em volta do arco.
        for (int x = -14; x <= 14; x++) {
            for (int z = -23; z <= Z_SUL; z++) {
                for (int y = -1; y <= 20; y++) {
                    Material m = (y == 1 || y == 18 || y == 20) ? FAIXA : pedra(x, y, z);
                    set(x, y, z, m);
                }
                boolean beirada = z == Z_SUL || z == -23 || Math.abs(x) == 14;
                if (beirada && Math.floorMod(x + z, 2) == 0) set(x, 21, z, pedra(x, 21, z));
            }
        }
        for (int x : new int[]{-10, 0, 10}) set(x, 21, -22, lanterna(Material.LANTERN, false));

        // Torres do portão.
        for (int s : new int[]{-1, 1}) {
            for (int ax = 15; ax <= 18; ax++) {
                int x = s * ax;
                for (int z = Z_SUL_INT; z <= Z_SUL; z++) {
                    boolean beirada = ax == 15 || ax == 18 || z == Z_SUL_INT || z == Z_SUL;
                    for (int y = -1; y <= 22; y++) {
                        Material m = (y == 1 || y == 22) ? FAIXA : pedra(x, y, z);
                        if (beirada && (y == 8 || y == 16)) m = VERMELHO;
                        set(x, y, z, m);
                    }
                    if (beirada && Math.floorMod(x + z, 2) == 0) set(x, 23, z, pedra(x, 23, z));
                }
            }
            set(s * 16, 23, -22, lanterna(Material.LANTERN, false));
            set(s * 17, 23, -23, lanterna(Material.LANTERN, false));
        }

        // Passagem de 9 de largura (x -4..4) com teto em arco, continuando o chão do caminho do spawn.
        for (int z = -23; z <= Z_SUL; z++) {
            for (int x = -MEIO_PORTAO; x <= MEIO_PORTAO; x++) {
                set(x, -1, z, Material.DIRT);
                set(x, 0, z, Math.abs(x) <= 2 ? Material.DIRT_PATH : Material.COARSE_DIRT);
                int topo = Math.abs(x) <= 2 ? 9 : (Math.abs(x) == 3 ? 8 : 7);
                for (int y = 1; y <= topo; y++) set(x, y, z, Material.AIR);
            }
            set(-4, 8, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.WEST, true));
            set(4, 8, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.EAST, true));
            set(-3, 9, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.WEST, true));
            set(3, 9, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.EAST, true));
            if (z != Z_SUL) {
                for (int y = 1; y <= 7; y++) {
                    Material parede = Math.floorMod(z, 2) == 0 ? VERMELHO : PEDRA;
                    set(-5, y, z, parede);
                    set(5, y, z, parede);
                }
            }
        }
        set(0, 9, -22, lanterna(Material.LANTERN, true));
        for (int x = -6; x <= 6; x++) set(x, 0, Z_SUL_INT, Math.abs(x) <= MEIO_PORTAO ? PEDRA : CALCADA);

        // Moldura por fora (embutida na face z = -21, colada nas cercas vivas do caminho do spawn).
        for (int y = 1; y <= 9; y++) {
            for (int s : new int[]{-1, 1}) {
                set(s * 5, y, Z_SUL, eixo(Material.CRIMSON_STEM, Axis.Y));
                set(s * 6, y, Z_SUL, eixo(Material.CHERRY_LOG, Axis.Y));
            }
        }
        for (int x = -6; x <= 6; x++) set(x, 10, Z_SUL, VERMELHO);
        set(0, 10, Z_SUL, LUZ);

        // Moldura por dentro (na frente da face z = -23), igual à do spawn.
        int zDentro = Z_SUL_INT;
        for (int y = 1; y <= 9; y++) {
            for (int s : new int[]{-1, 1}) {
                set(s * 5, y, zDentro, eixo(Material.CRIMSON_STEM, Axis.Y));
                set(s * 6, y, zDentro, eixo(Material.CHERRY_LOG, Axis.Y));
            }
        }
        for (int x = -6; x <= 6; x++) set(x, 10, zDentro, VERMELHO);
        set(0, 10, zDentro, LUZ);
        for (int x = -3; x <= 3; x++) {
            if (x != 0) set(x, 11, zDentro, laje(Material.RED_NETHER_BRICK_SLAB, false));
        }
        for (int s : new int[]{-1, 1}) {
            set(s * 7, 1, zDentro, folha(Material.FLOWERING_AZALEA_LEAVES));
            set(s * 8, 1, zDentro, folha(Material.AZALEA_LEAVES));
            set(s * 7, 2, zDentro, lanterna(Material.LANTERN, false));
        }

        // Letreiros: "MINAS" lido de quem chega do spawn (direita = +x), "SPAWN" lido de dentro.
        painel(Z_SUL);
        escrever("MINAS", -11, 1, 16, Z_SUL);
        painel(-23);
        escrever("SPAWN", 11, -1, 16, -23);
    }

    /** Placa preta com moldura em cima do arco (y 11..17, |x| <= 12). */
    private void painel(int z) {
        for (int x = -12; x <= 12; x++) {
            for (int y = 11; y <= 17; y++) {
                boolean borda = Math.abs(x) == 12 || y == 11 || y == 17;
                set(x, y, z, borda ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_BLACKSTONE);
            }
        }
    }

    private static final Map<Character, String[]> FONTE = Map.of(
            'M', new String[]{"X...X", "XX.XX", "X.X.X", "X...X", "X...X"},
            'I', new String[]{"XXX", ".X.", ".X.", ".X.", "XXX"},
            'N', new String[]{"X..X", "XX.X", "X.XX", "X..X", "X..X"},
            'A', new String[]{".X.", "X.X", "XXX", "X.X", "X.X"},
            'S', new String[]{"XXX", "X..", "XXX", "..X", "XXX"},
            'P', new String[]{"XXX", "X.X", "XXX", "X..", "X.."},
            'W', new String[]{"X...X", "X...X", "X.X.X", "XX.XX", "X...X"}
    );

    /** Escreve em pedra luminosa; {@code passo} = +1/-1 conforme a direita de quem lê. */
    private void escrever(String texto, int xInicio, int passo, int yTopo, int z) {
        int coluna = 0;
        for (char c : texto.toCharArray()) {
            String[] letra = FONTE.get(c);
            for (int linha = 0; linha < letra.length; linha++) {
                for (int i = 0; i < letra[linha].length(); i++) {
                    if (letra[linha].charAt(i) == 'X') {
                        set(xInicio + passo * (coluna + i), yTopo - linha, z, Material.GLOWSTONE);
                    }
                }
            }
            coluna += letra[0].length() + 1;
        }
    }

    // ------------------------------------------------------------------ luz e enfeites

    /**
     * Luzes no chão. Além da grade da avenida e das praças, cada poço ganha luzes coladas na borda
     * dos quatro lados, alinhadas com o meio dele — assim nem o miolo da superfície da mina fica com
     * luz 0 (distância ≤ 13 da luz mais próxima).
     */
    private void gerarLuzes() {
        // Avenida central.
        for (int z = -32; z >= -176; z -= 8) {
            set(-5, 0, z, LUZ);
            set(5, 0, z, LUZ);
        }
        // Praças das pontas.
        for (int x = -32; x <= 32; x += 8) {
            set(x, 0, -26, LUZ);
            if (Math.abs(x) > 4) set(x, 0, -180, LUZ);
        }
        for (Mina m : MINAS) {
            int xc = m.xc(), zc = m.zc();
            int xDentro = m.xTeleporte();
            int xFora = m.oeste() ? -31 : 31;
            for (int dz = -9; dz <= 9; dz += 3) {
                if (dz != 0) set(xDentro, 0, zc + dz, LUZ);
                set(xFora, 0, zc + dz, LUZ);
            }
            for (int dx = -9; dx <= 9; dx += 3) {
                set(xc + dx, 0, m.zMin() - 2, LUZ);
                set(xc + dx, 0, m.zMax() + 2, LUZ);
            }
        }
    }

    /** Líquen brilhante nos pilares da muralha (enfeita e não dá pra escalar). */
    private void gerarLiquens() {
        Random random = new Random(13);
        for (int s : new int[]{-1, 1}) {
            BlockFace parede = s < 0 ? BlockFace.WEST : BlockFace.EAST;
            for (int z = Z_ARCADA_NORTE; z <= Z_ARCADA_SUL; z++) {
                if (distArco(z, 4) < 3 || random.nextInt(3) != 0) continue;
                pendurarLiquen(s * X_INT, z, parede, 11, 8, 2 + random.nextInt(3));
            }
        }
        for (int s : new int[]{-1, 1}) {
            for (int ax = 19; ax <= 35; ax++) {
                if (distArco(ax, 0) < 3 || random.nextInt(2) != 0) continue;
                int topo = random.nextBoolean() ? 11 : 6;
                pendurarLiquen(s * ax, Z_SUL_INT, BlockFace.SOUTH, topo, 2, 2 + random.nextInt(4));
                pendurarLiquen(s * ax, Z_NORTE_INT, BlockFace.NORTH, topo, 2, 2 + random.nextInt(4));
            }
        }
    }

    private void pendurarLiquen(int x, int z, BlockFace parede, int topo, int yMin, int comprimento) {
        for (int y = topo; y > topo - comprimento && y >= yMin; y--) {
            if (get(x, y, z) != Material.AIR) break;
            Material atras = get(x + parede.getModX(), y, z + parede.getModZ());
            if (!solido(atras)) break;
            set(x, y, z, conectado(Material.GLOW_LICHEN, parede));
        }
    }

    // ------------------------------------------------------------------ segurança

    /** Ponto de teleporte de cada mina e um bloco em volta: ar até y+5 (holograma) e chão firme. */
    private void liberarTeleportes() {
        BlockData ar = Material.AIR.createBlockData();
        for (Mina m : MINAS) {
            int tx = m.xTeleporte(), zc = m.zc();
            for (int x = tx - 1; x <= tx + 1; x++) {
                for (int z = zc - 1; z <= zc + 1; z++) {
                    for (int y = 1; y <= 5; y++) set(x, y, z, ar);
                    if (naBordaDeMina(x, z)) continue;
                    Material chao = get(x, 0, z);
                    if (!solido(chao)) set(x, 0, z, CALCADA);
                }
            }
            set(tx, 0, zc, Material.CHISELED_STONE_BRICKS);
        }
    }

    /**
     * Confere a planta pronta: nada fora do permitido (poços e bordas em y &lt;= 0, qualquer coisa
     * não-ar acima deles, nada ao sul de z = -21) e os 12 teleportes livres. Se algo escapar, falha
     * aqui em vez de estragar uma mina.
     */
    private void verificar() {
        for (Planta.Bloco b : p.getBlocos()) {
            int z = b.z() + ORIGEM_Z;
            if (!permitido(b.x(), b.y(), z, b.dados().getMaterial())) {
                throw new IllegalStateException("Avenida das Minas: bloco proibido em " + b.x() + " "
                        + (b.y() + 64) + " " + z + " (" + b.dados().getMaterial() + ")");
            }
        }
        for (Mina m : MINAS) {
            int tx = m.xTeleporte(), zc = m.zc();
            Material chao = get(tx, 0, zc);
            if (!solido(chao)) {
                throw new IllegalStateException("Avenida das Minas: teleporte da mina " + m.indice() + " sem chão");
            }
            for (int y = 1; y <= 5; y++) {
                if (get(tx, y, zc) != Material.AIR) {
                    throw new IllegalStateException("Avenida das Minas: teleporte da mina " + m.indice() + " obstruído");
                }
            }
        }
    }
}
