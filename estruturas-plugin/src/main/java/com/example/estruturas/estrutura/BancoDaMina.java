package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;

import java.util.List;

import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * O Banco do fim da Avenida das Minas, entre o Compactador (x -18..-10) e o Cambista (x 10..18), no
 * lugar da cerejeira grande da praça do fundo. Fachada de banco clássico: plataforma e escadaria de
 * quartzo, tapete vermelho, colunas com capitel de ouro, frontão com medalhão de esmeralda, balcão de
 * pedra-negra com tampo de quartzo e, no fundo, o cofre (porta de ferro e o bloco de cofre) com ouro em
 * volta. O NPC é o Bancário do OficinasPlugin (folha de cheque, depósito, extrato e Mine-sena; o balcão
 * é do ChequePlugin).
 *
 * <p>Mesma origem da Avenida das Minas: {@code /estrutura construir banco spawn 0 65 -21}. x e z são
 * ABSOLUTOS e y é relativo ao chão (y = 0 é o piso em 64). Ocupa x -9..9, z -182..-172 (limpa a copa da
 * cerejeira antiga) e refaz o piso da praça em volta. Construir depois da avenida.
 */
public class BancoDaMina implements Estrutura {

    private static final int ORIGEM_Z = -21;

    private static final int X = 8;            // largura: x -8..8
    private static final int Z_FUNDO = -182;   // parede do fundo (encostada na muralha norte)
    private static final int Z_NPC = -180;
    private static final int Z_BALCAO = -179;
    private static final int Z_FRENTE = -177;  // linha das colunas, beira da plataforma
    private static final int Z_DEGRAU = -176;
    private static final int Y_TETO = 8;

    private Planta p;

    @Override
    public String getId() {
        return "banco";
    }

    @Override
    public String getNome() {
        return "Banco da Avenida das Minas";
    }

    private void set(int x, int y, int z, BlockData dados) {
        p.set(x, y, z - ORIGEM_Z, dados);
    }

    private void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    @Override
    public void gerar(Planta planta) {
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

    /** Tira a cerejeira antiga (monte de musgo, tronco e copa), os bancos e os postes, e refaz o piso da praça. */
    private void limpar() {
        for (int x = -X - 1; x <= X + 1; x++) {
            for (int z = Z_FUNDO; z <= -172; z++) {
                // perto do canteiro (z -173 em diante) só a copa sai: o canteiro e os postes ficam
                int yMin = z >= -174 ? 5 : 1;
                for (int y = yMin; y <= 16; y++) set(x, y, z, Material.AIR);
                if (z <= -175) set(x, 0, z, Math.floorMod(x, 8) == 0 ? Material.SMOOTH_STONE : Material.POLISHED_ANDESITE);
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
                    boolean janela = (y == 4 || y == 5) && z == Z_NPC;
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
        for (int x : new int[]{-5, 0, 5}) set(x, Y_TETO, -179, Material.PEARLESCENT_FROGLIGHT);
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
            set(x7, 2, -181, direcional(Material.BARREL, BlockFace.UP));
            set(x7, 3, -181, Material.RAW_GOLD_BLOCK);
            set(x7, 4, -181, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
            set(x6, 2, -181, Material.GOLD_BLOCK);
            set(x6, 3, -181, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
            set(x5, 2, -181, direcional(Material.CHEST, BlockFace.SOUTH));
            set(x7, 2, Z_NPC, Material.EMERALD_BLOCK);
            set(x7, 3, Z_NPC, Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            set(s * 3, 2, -181, Material.POTTED_AZALEA_BUSH);
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

    /** O Bancário (OficinasPlugin) atrás do balcão, em cima da plataforma, olhando pro sul (pra avenida). */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return List.of("oficina criar bancario bancario " + mundo + " 0.5 " + (oy + 2) + " " + (Z_NPC + 0.5) + " 0");
    }
}
