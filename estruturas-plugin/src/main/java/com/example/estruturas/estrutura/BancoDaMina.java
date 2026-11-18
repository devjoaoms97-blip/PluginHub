package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.structure.StructureRotation;

import java.util.List;

import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * O Banco, no fundo da Loja Oficial (até 2026-11-17 ficava no fim da Avenida das Minas, que virou o
 * portão da mina PvP). Fachada de banco clássico: plataforma e escadaria de quartzo, tapete vermelho,
 * colunas com capitel de ouro, frontão com medalhão de esmeralda, balcão de pedra-negra com tampo de
 * quartzo e, no fundo, o cofre (porta de ferro e o bloco de cofre) com ouro em volta. O NPC é o Bancário
 * do OficinasPlugin (folha de cheque, depósito, extrato e Mine-sena; o balcão é do ChequePlugin).
 *
 * <p>O desenho continua escrito como era na avenida (x de -8 a 8 de um lado ao outro, z do fundo -185 à
 * escadaria -179, frente pro +z) e o {@link #set} gira 90° pra o fundo ficar colado na parede do fundo
 * da loja ({@link LojaOficial#X_FUNDO}) e a frente olhar pra porta (oeste). Mesma origem da loja:
 * {@code /estrutura construir banco spawn 0 65 43}; a loja já constrói o banco junto, e esta estrutura
 * serve pra refazer só ele. Ocupa x {@code X_FUNDO - 6} a {@code X_FUNDO}, dz -8 a 8.
 */
public class BancoDaMina implements Estrutura {

    private static final int X = 8;            // largura: x -8..8
    private static final int Z_FUNDO = -185;   // parede do fundo (a da loja)
    private static final int Z_NPC = -183;
    private static final int Z_BALCAO = -182;
    private static final int Z_FRENTE = -180;  // linha das colunas, beira da plataforma
    private static final int Z_DEGRAU = -179;
    private static final int Y_TETO = 8;

    private Planta p;

    @Override
    public String getId() {
        return "banco";
    }

    @Override
    public String getNome() {
        return "Banco (fundo da Loja Oficial)";
    }

    /** x da loja (absoluto) de um ponto do desenho: a parede do fundo e, a cada bloco pra frente, um pro oeste. */
    static int xNaLoja(int z) {
        return LojaOficial.X_FUNDO - (z - Z_FUNDO);
    }

    /** (x, y, z) do desenho → (x absoluto, y, dz) da loja, com a direção dos blocos girada junto. */
    private void set(int x, int y, int z, BlockData dados) {
        BlockData girado = dados.clone();
        girado.rotate(StructureRotation.CLOCKWISE_90);
        p.set(xNaLoja(z), y, x, girado);
    }

    private void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    @Override
    public void gerar(Planta planta) {
        gerarEm(planta);
    }

    /** Desenha o banco numa planta com a origem da loja (a LojaOficial chama no fim da dela). */
    void gerarEm(Planta planta) {
        this.p = planta;
        limpar();
        plataforma();
        paredes();
        cofre();
        balcao();
        interior();
        fachada();
        frontao();
    }

    /** Ar no lugar do banco (dentro do salão), até o alto do frontão. */
    private void limpar() {
        for (int x = -X; x <= X; x++) {
            for (int z = Z_FUNDO + 1; z <= Z_DEGRAU; z++) {
                for (int y = 1; y <= Y_TETO + 8; y++) set(x, y, z, Material.AIR);
            }
        }
    }

    /** Plataforma de quartzo (y 1) com piso xadrez por dentro, borda lavrada e a escadaria na frente. */
    private void plataforma() {
        for (int x = -X; x <= X; x++) {
            for (int z = Z_FUNDO; z <= Z_FRENTE; z++) {
                Material m;
                if (Math.abs(x) == X || z == Z_FRENTE) m = Material.CHISELED_QUARTZ_BLOCK;
                else if (z <= Z_NPC) m = Math.floorMod(x + z, 2) == 0 ? Material.SMOOTH_QUARTZ : Material.POLISHED_BLACKSTONE;
                else m = Material.SMOOTH_QUARTZ;
                set(x, 1, z, m);
            }
        }
        set(0, 1, Z_NPC, Material.GOLD_BLOCK);
        for (int x = -X + 1; x <= X - 1; x++) set(x, 1, Z_DEGRAU, escada(Material.QUARTZ_STAIRS, BlockFace.NORTH, false));
        // tapete vermelho do degrau até o balcão
        for (int x = -1; x <= 1; x++) {
            for (int z = Z_BALCAO + 1; z <= Z_FRENTE; z++) set(x, 2, z, Material.RED_CARPET);
        }
        // pedestais com lanterna dos dois lados da escadaria
        for (int s : new int[]{-1, 1}) {
            set(s * X, 1, Z_DEGRAU, Material.CHISELED_QUARTZ_BLOCK);
            set(s * X, 2, Z_DEGRAU, Material.GOLD_BLOCK);
            set(s * X, 3, Z_DEGRAU, lanterna(Material.LANTERN, false));
        }
    }

    /** Fundo e laterais de tijolo de quartzo, pilastras, janelas de grade e o teto com luzes. */
    private void paredes() {
        for (int y = 2; y < Y_TETO; y++) {
            for (int x = -X; x <= X; x++) set(x, y, Z_FUNDO, Material.QUARTZ_BRICKS);
            for (int z = Z_FUNDO + 1; z < Z_FRENTE; z++) {
                for (int s : new int[]{-1, 1}) {
                    boolean janela = (y == 4 || y == 5) && z == Z_BALCAO + 1; // a que dá pra fora da muralha
                    set(s * X, y, z, janela ? Material.IRON_BARS : Material.QUARTZ_BRICKS);
                }
            }
        }
        // pilastras no fundo, com faixa de ouro em cima
        for (int x : new int[]{-X, -4, 4, X}) {
            for (int y = 2; y < Y_TETO - 1; y++) set(x, y, Z_FUNDO, eixo(Material.QUARTZ_PILLAR, Axis.Y));
            set(x, Y_TETO - 1, Z_FUNDO, Material.CHISELED_QUARTZ_BLOCK);
        }
        // teto de quartzo liso com luzes embutidas
        for (int x = -X; x <= X; x++) {
            for (int z = Z_FUNDO; z <= Z_FRENTE; z++) set(x, Y_TETO, z, Material.SMOOTH_QUARTZ);
        }
        for (int x : new int[]{-5, 0, 5}) set(x, Y_TETO, Z_BALCAO, Material.PEARLESCENT_FROGLIGHT);
    }

    /** O cofre atrás do NPC: moldura de pedra-negra e ouro, porta de ferro, o bloco de cofre e esmeralda em cima. */
    private void cofre() {
        for (int x = -2; x <= 2; x++) {
            for (int y = 2; y <= 6; y++) {
                boolean borda = Math.abs(x) == 2 || y == 6;
                set(x, y, Z_FUNDO, borda ? Material.POLISHED_BLACKSTONE_BRICKS : Material.GILDED_BLACKSTONE);
            }
        }
        set(0, 2, Z_FUNDO, porta(Bisected.Half.BOTTOM));
        set(0, 3, Z_FUNDO, porta(Bisected.Half.TOP));
        set(0, 4, Z_FUNDO, direcional(Material.VAULT, BlockFace.SOUTH));
        set(0, 5, Z_FUNDO, Material.EMERALD_BLOCK);
        set(-1, 5, Z_FUNDO, Material.GOLD_BLOCK);
        set(1, 5, Z_FUNDO, Material.GOLD_BLOCK);
        set(0, 6, Z_FUNDO, Material.GOLD_BLOCK);
        set(0, 7, Z_FUNDO, Material.CHISELED_QUARTZ_BLOCK);
    }

    /** Balcão de pedra-negra com tampo de quartzo, aberto no meio pra ver o NPC; moedas, livro e velas. */
    private void balcao() {
        for (int x = -X + 1; x <= X - 1; x++) {
            boolean ponta = Math.abs(x) == X - 1;
            set(x, 2, Z_BALCAO, ponta ? Material.GOLD_BLOCK : Math.abs(x) % 3 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE
                    : Material.POLISHED_BLACKSTONE_BRICKS);
            set(x, 3, Z_BALCAO, laje(Material.SMOOTH_QUARTZ_SLAB, false));
        }
        set(0, 3, Z_BALCAO, Material.AIR);
        set(-3, 3, Z_BALCAO, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);   // moedas de ouro
        set(3, 3, Z_BALCAO, direcional(Material.LECTERN, BlockFace.SOUTH)); // livro-caixa
        set(-5, 3, Z_BALCAO, vela(Material.YELLOW_CANDLE, 3));
        set(5, 3, Z_BALCAO, vela(Material.YELLOW_CANDLE, 3));
        set(-X + 1, 3, Z_BALCAO, lanterna(Material.LANTERN, false));
        set(X - 1, 3, Z_BALCAO, lanterna(Material.LANTERN, false));
    }

    /** Atrás do balcão: barris e pilhas de ouro, baús, o cofre de esmeralda e os lustres. */
    private void interior() {
        for (int s : new int[]{-1, 1}) {
            int x7 = s * (X - 1), x6 = s * (X - 2), x5 = s * (X - 3);
            set(x7, 2, Z_FUNDO + 1, direcional(Material.BARREL, BlockFace.UP));
            set(x7, 3, Z_FUNDO + 1, Material.RAW_GOLD_BLOCK);
            set(x7, 4, Z_FUNDO + 1, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
            set(x6, 2, Z_FUNDO + 1, Material.GOLD_BLOCK);
            set(x6, 3, Z_FUNDO + 1, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
            set(x5, 2, Z_FUNDO + 1, direcional(Material.CHEST, BlockFace.SOUTH));
            set(x7, 2, Z_NPC, Material.EMERALD_BLOCK);
            set(x7, 3, Z_NPC, Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            set(s * 3, 2, Z_FUNDO + 1, Material.POTTED_AZALEA_BUSH);
            // lustres: corrente e lanterna penduradas no teto
            set(s * 3, 7, Z_NPC, eixo(Material.IRON_CHAIN, Axis.Y));
            set(s * 3, 6, Z_NPC, lanterna(Material.LANTERN, true));
        }
    }

    /** Pórtico: colunas na beira da plataforma (as do meio com capitel de ouro), viga e friso de ouro. */
    private void fachada() {
        for (int x : new int[]{-X, -4, 4, X}) {
            set(x, 2, Z_FRENTE, Material.CHISELED_QUARTZ_BLOCK);
            for (int y = 3; y <= 6; y++) set(x, y, Z_FRENTE, eixo(Material.QUARTZ_PILLAR, Axis.Y));
            set(x, 7, Z_FRENTE, Math.abs(x) == 4 ? Material.GOLD_BLOCK : Material.CHISELED_QUARTZ_BLOCK);
        }
        for (int x = -X; x <= X; x++) {
            set(x, Y_TETO, Z_FRENTE, Material.SMOOTH_QUARTZ);
            set(x, Y_TETO + 1, Z_FRENTE, Math.floorMod(x, 2) == 0 ? Material.GOLD_BLOCK : Material.CHISELED_QUARTZ_BLOCK);
            // cornija: escada de quartzo de cabeça pra baixo, por cima da escadaria
            set(x, Y_TETO + 1, Z_DEGRAU, escada(Material.QUARTZ_STAIRS, BlockFace.NORTH, true));
            for (int z = Z_FUNDO; z < Z_FRENTE; z++) set(x, Y_TETO + 1, z, Material.QUARTZ_BLOCK);
        }
    }

    /** Frontão triangular (o telhado inteiro, do fundo à frente) com o medalhão de esmeralda e ouro e um pináculo. */
    private void frontao() {
        int base = Y_TETO + 2;
        for (int linha = 0; linha <= 4; linha++) {
            int y = base + linha, meia = X - 2 * linha;
            if (meia < 0) break;
            for (int z = Z_FUNDO; z <= Z_FRENTE; z++) {
                for (int x = -meia; x <= meia; x++) {
                    boolean beira = Math.abs(x) == meia && meia > 0;
                    if (beira) {
                        set(x, y, z, escada(Material.QUARTZ_STAIRS, x < 0 ? BlockFace.EAST : BlockFace.WEST, false));
                    } else {
                        set(x, y, z, z == Z_FRENTE ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BLOCK);
                    }
                }
            }
        }
        // medalhão na face do frontão
        set(0, base + 1, Z_FRENTE, Material.EMERALD_BLOCK);
        set(-1, base + 1, Z_FRENTE, Material.GOLD_BLOCK);
        set(1, base + 1, Z_FRENTE, Material.GOLD_BLOCK);
        set(0, base, Z_FRENTE, Material.GOLD_BLOCK);
        set(0, base + 2, Z_FRENTE, Material.GOLD_BLOCK);
        // pináculo de ouro no topo, na frente
        set(0, base + 4, Z_FRENTE, Material.GOLD_BLOCK);
        set(0, base + 5, Z_FRENTE, direcional(Material.LIGHTNING_ROD, BlockFace.UP));
    }

    private static BlockData porta(Bisected.Half metade) {
        Door porta = (Door) Material.IRON_DOOR.createBlockData();
        porta.setFacing(BlockFace.SOUTH);
        porta.setHalf(metade);
        return porta;
    }

    private static BlockData vela(Material material, int quantas) {
        Candle vela = (Candle) material.createBlockData();
        vela.setCandles(quantas);
        vela.setLit(true);
        return vela;
    }

    /** O Bancário (OficinasPlugin) atrás do balcão, em cima da plataforma, olhando pro oeste (pra porta da loja). */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return List.of(comandoNpc(mundo, oy, oz));
    }

    /** {@code oz} é o z do centro do spawn (a origem da loja). */
    static String comandoNpc(String mundo, int oy, int oz) {
        return "oficina criar bancario bancario " + mundo + " " + (xNaLoja(Z_NPC) + 0.5) + " " + (oy + 2) + " " + (oz + 0.5) + " 90";
    }
}
