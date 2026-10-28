package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Warp mobs (inspirada na /warp mobs da Craftlandia): salão enorme colado no portão OESTE do spawn
 * ("MOBS"), fechado, com teto de vidro e nenhuma saída além do túnel do spawn. Em volta, estátuas
 * gigantes de mobs direto na grama, e na frente de cada uma um spawner no chão: o SpawnersPlugin faz os
 * mobs daquela estátua nascerem em volta dele, pra quem quiser farmar drops, XP e skills.
 *
 * <p>Cinco estátuas de cada lado, viradas pra avenida do meio, e o Enderman no fundo, de frente pra
 * entrada. Em volta de cada par, o cenário do tema (granja, lama, pasto, cemitério, ossada, florestas,
 * pântano, Nether e o Fim), e um rio com cascatas atravessa o salão por baixo de uma ponte. Em cima da
 * entrada, o painel do PvP em ciclos do AreasPlugin (da linha vermelha pra dentro). Logo depois da porta fica o vestíbulo (onde a /warp mobs chega): os mobs não passam da
 * linha vermelha do chão, então quem chega ou sai não apanha.
 *
 * <p>O salão fica meio enterrado (igual à arena do boss): o teto de vidro fica em y 84, abaixo do topo
 * da muralha do spawn, então ele não aparece de dentro do spawn, só de bem alto. Do portão do spawn, uma escadaria
 * fechada desce até a porta do salão.
 *
 * <p>Construir com {@code /estrutura construir mobs spawn 0 65 43} (a mesma origem do spawn): x é
 * ABSOLUTO, z é relativo ao centro do spawn (z 43) e y é relativo ao chão (y 0 = chão em 64; o piso do
 * salão fica {@link #PROFUNDIDADE} abaixo). O portão oeste do spawn termina em x -63; a escadaria começa
 * no x -64 e a parede da frente do salão é o x -83.
 */
public class ArenaMobs implements Estrutura {

    // Salão (y relativo ao piso dele; o set soma a base).
    private static final int X_FRENTE = -83, X_FUNDO = -209, DZ_PAREDE = 40;
    private static final int ALTURA = 36;                 // teto de vidro
    private static final int PROFUNDIDADE = 16;           // piso em y 48, teto de vidro em y 84
    private static final int X_VESTIBULO = -90;           // mureta entre o vestíbulo e o salão
    private static final int X_LIMITE = -92;              // os mobs ficam em x <= -93 (a linha vermelha)
    private static final int MEIA_AVENIDA = 3;

    // Escadaria (y relativo ao chão de fora): patamar no nível do portão, um degrau por bloco até o
    // piso do salão e outro patamar na porta. O primeiro degrau fica na altura do patamar (o alto dele
    // rente ao chão do patamar), senão sobra um degrau inteiro pra pular na chegada lá em cima.
    private static final int X_TOPO = -64;                // começo, colado no túnel do portão do spawn
    private static final int PATAMAR = 2;                 // blocos planos antes do primeiro degrau
    private static final int ALTURA_ESCADA = 7;           // ar livre acima de cada degrau (o patamar de cima tem 9: 3 de degrau no teto)
    private static final int DZ_SPAWNER = 13;             // spawner das estátuas dos lados

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material FAIXA = Material.SMOOTH_STONE;
    private static final Material VERMELHO = Material.RED_NETHER_BRICKS;
    private static final Material CALCADA = Material.POLISHED_ANDESITE;

    /** Uma estátua: o tipo do SpawnersPlugin, o lado (-1 norte, +1 sul, 0 fundo) e o x do centro. */
    private record Lugar(String tipo, int lado, int x) {}

    private static final List<Lugar> LUGARES = List.of(
            new Lugar("galinha", -1, -103), new Lugar("porco", 1, -103),
            new Lugar("vaca", -1, -125), new Lugar("ovelha", 1, -125),
            new Lugar("zumbi", -1, -147), new Lugar("esqueleto", 1, -147),
            new Lugar("aranha", -1, -169), new Lugar("creeper", 1, -169),
            new Lugar("slime", -1, -191), new Lugar("blaze", 1, -191),
            new Lugar("enderman", 0, 0));

    private Planta p;
    private int base;                                     // y do piso do salão em relação ao chão de fora

    @Override
    public String getId() {
        return "mobs";
    }

    @Override
    public String getNome() {
        return "Warp Mobs";
    }

    @Override
    public void gerar(Planta planta) {
        this.p = planta;
        base = -PROFUNDIDADE;
        limparEFundar();
        piso();
        paredes();
        teto();
        vestibulo();
        painelPvp();
        cenario();
        rio();
        for (Lugar l : LUGARES) estatua(l);
        base = 0;
        escadaria();
    }

    /** Cadastra no SpawnersPlugin a área onde os mobs ficam e o spawner de cada estátua. */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        List<String> comandos = new ArrayList<>();
        comandos.add("spawners mobs limpar");
        int piso = oy - PROFUNDIDADE;
        comandos.add("spawners mobs area " + mundo + " " + (ox + X_FUNDO + 1) + " " + (piso + 1) + " " + (oz - DZ_PAREDE + 1)
                + " " + (ox + X_LIMITE) + " " + (piso + ALTURA) + " " + (oz + DZ_PAREDE));
        for (Lugar l : LUGARES) {
            int[] s = spawner(l);
            comandos.add("spawners mobs ponto " + l.tipo() + " " + l.tipo() + " " + mundo + " " + (ox + s[0]) + " " + piso + " " + (oz + s[1]));
        }
        return comandos;
    }

    /** Onde fica o spawner da estátua (x, dz), no chão. */
    private static int[] spawner(Lugar l) {
        if (l.lado() == 0) return new int[]{X_FUNDO + 14, 0};
        return new int[]{l.x(), l.lado() * DZ_SPAWNER};
    }

    private void set(int x, int y, int dz, BlockData d) {
        p.set(x, base + y, dz, d);
    }

    private void set(int x, int y, int dz, Material m) {
        p.set(x, base + y, dz, m);
    }

    /** Tijolo de pedra misturado com musgo e rachado (igual ao spawn). */
    private static Material pedra(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h = Math.floorMod(h ^ (h >>> 13), 100);
        if (h < 62) return PEDRA;
        if (h < 86) return Material.MOSSY_STONE_BRICKS;
        return Material.CRACKED_STONE_BRICKS;
    }

    private static int sorte(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ 0x5bd1e995;
        return Math.floorMod(h ^ (h >>> 15), 100);
    }

    // ------------------------------------------------------------------ base

    /**
     * Limpa de 40 abaixo do chão de fora até 40 acima (também as versões antigas do salão: uma no nível
     * do spawn e outra mais funda, que ia até o x -231) e funda o piso. Em volta do teto, uma borda de grama.
     */
    private void limparEFundar() {
        for (int x = -233; x <= X_TOPO; x++) {
            for (int dz = -DZ_PAREDE - 2; dz <= DZ_PAREDE + 2; dz++) {
                for (int y = PROFUNDIDADE - 40; y <= PROFUNDIDADE + 40; y++) set(x, y, dz, Material.AIR);
                if (x < X_FUNDO - 2 || x > X_FRENTE + 2) continue;
                boolean dentro = x >= X_FUNDO && x <= X_FRENTE && Math.abs(dz) <= DZ_PAREDE;
                if (!dentro) {
                    set(x, ALTURA, dz, Material.GRASS_BLOCK);
                    set(x, ALTURA - 1, dz, Material.DIRT);
                    continue;
                }
                set(x, -1, dz, Material.DIRT);
                set(x, -2, dz, Material.STONE);
                set(x, -3, dz, Material.STONE);
            }
        }
    }

    /** Grama com manchas, a avenida de calçada no meio, luz no chão e a almofada de cada spawner. */
    private void piso() {
        Random random = new Random(11);
        int[][] manchas = new int[40][4];
        for (int[] m : manchas) {
            m[0] = X_FUNDO + 2 + random.nextInt(X_FRENTE - X_FUNDO - 10);
            m[1] = random.nextInt(2 * DZ_PAREDE - 3) - DZ_PAREDE + 2;
            m[2] = 1 + random.nextInt(3);
            m[3] = random.nextInt(3);
        }
        for (int x = X_FUNDO + 1; x < X_FRENTE; x++) {
            for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
                Material m = Material.GRASS_BLOCK;
                for (int[] mancha : manchas) {
                    int a = x - mancha[0], b = dz - mancha[1];
                    if (a * a + b * b <= mancha[2] * mancha[2]) {
                        m = switch (mancha[3]) {
                            case 0 -> Material.MOSS_BLOCK;
                            case 1 -> Material.COARSE_DIRT;
                            default -> Material.PODZOL;
                        };
                    }
                }
                if (Math.abs(dz) <= MEIA_AVENIDA && x > X_FUNDO + 20) {
                    m = Math.abs(dz) == MEIA_AVENIDA ? PEDRA : (Math.floorMod(x, 6) == 0 ? FAIXA : CALCADA);
                }
                set(x, 0, dz, m);
            }
        }
        // luz no chão a cada 8 (fora da avenida): de noite o vidro não ilumina nada
        for (int x = X_FUNDO + 5; x < X_FRENTE - 8; x += 8) {
            for (int dz = -DZ_PAREDE + 4; dz < DZ_PAREDE - 3; dz += 8) {
                if (Math.abs(dz) > MEIA_AVENIDA + 1) set(x, 0, dz, Material.SHROOMLIGHT);
            }
        }
        // a almofada do spawner: 5 x 5 de tijolo de pedra-negra, o spawner no meio
        for (Lugar l : LUGARES) {
            int[] s = spawner(l);
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    boolean borda = Math.abs(a) == 2 || Math.abs(b) == 2;
                    set(s[0] + a, 0, s[1] + b, borda ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            set(s[0], 0, s[1], Material.SPAWNER);
        }
    }

    /** Paredes altas de tijolo com faixas, pilastras de pedra-negra e lanternas entre as estátuas. */
    private void paredes() {
        for (int x = X_FUNDO; x <= X_FRENTE; x++) {
            for (int dz = -DZ_PAREDE; dz <= DZ_PAREDE; dz++) {
                boolean parede = x == X_FUNDO || x == X_FRENTE || Math.abs(dz) == DZ_PAREDE;
                if (!parede) continue;
                set(x, 0, dz, PEDRA);
                for (int y = 1; y <= ALTURA; y++) {
                    Material m = (y == 1 || y == 12 || y == 24 || y == ALTURA) ? FAIXA : (y == 13 || y == 25 ? VERMELHO : pedra(x, y, dz));
                    set(x, y, dz, m);
                }
            }
        }
        // porta: a mesma boca do túnel do spawn (7 de largura, arco até 9)
        for (int dz = -MEIA_AVENIDA; dz <= MEIA_AVENIDA; dz++) {
            int topo = Math.abs(dz) <= 1 ? 9 : (Math.abs(dz) == 2 ? 8 : 7);
            for (int y = 1; y <= topo; y++) set(X_FRENTE, y, dz, Material.AIR);
            set(X_FRENTE, 0, dz, Math.abs(dz) == MEIA_AVENIDA ? PEDRA : CALCADA);
        }
        // pilastras por dentro, entre uma estátua e outra (e nos cantos), com lanterna
        for (int x : new int[]{-92, -114, -136, -158, -180, -202}) {
            for (int s : new int[]{-1, 1}) {
                int dz = s * (DZ_PAREDE - 1);
                for (int y = 1; y < ALTURA; y++) {
                    set(x, y, dz, (y % 12 == 0) ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                for (int y : new int[]{8, 20}) {
                    set(x, y, dz - s, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
                    set(x, y + 1, dz - s, lanterna(Material.LANTERN, false));
                }
            }
        }
        // no fundo, pilastras dos dois lados do Enderman
        for (int dz : new int[]{-12, 12, -26, 26}) {
            for (int y = 1; y < ALTURA; y++) {
                set(X_FUNDO + 1, y, dz, (y % 12 == 0) ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
            }
            for (int y : new int[]{8, 20}) {
                set(X_FUNDO + 2, y, dz, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
                set(X_FUNDO + 2, y + 1, dz, lanterna(Material.LANTERN, false));
            }
        }
    }

    /** Teto inteiro de vidro, com nervuras de pedra lisa a cada 8 e lanternas penduradas nos cruzamentos. */
    private void teto() {
        for (int x = X_FUNDO + 1; x < X_FRENTE; x++) {
            for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
                boolean nervura = Math.floorMod(x - X_FUNDO, 8) == 0 || Math.floorMod(dz, 8) == 0;
                set(x, ALTURA, dz, nervura ? FAIXA : Material.GLASS);
                if (Math.floorMod(x - X_FUNDO, 8) == 0 && Math.floorMod(dz, 8) == 0) {
                    for (int y = ALTURA - 3; y < ALTURA; y++) set(x, y, dz, eixo(Material.IRON_CHAIN, Axis.Y));
                    set(x, ALTURA - 4, dz, lanterna(Material.LANTERN, true));
                }
            }
        }
    }

    /**
     * Vestíbulo logo depois da porta (onde a warp chega): calçada, bancos de pedra e a mureta com a
     * passagem do meio. A linha vermelha no chão é até onde os mobs vêm.
     */
    private void vestibulo() {
        for (int x = X_LIMITE + 1; x < X_FRENTE; x++) {
            for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
                Material m = Math.floorMod(x, 6) == 0 ? FAIXA : CALCADA;
                if (Math.abs(dz) == MEIA_AVENIDA + 1 && x > X_VESTIBULO) m = PEDRA;
                set(x, 0, dz, m);
            }
        }
        for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
            set(X_LIMITE + 1, 0, dz, VERMELHO);
            set(X_LIMITE, 0, dz, VERMELHO);
            if (Math.abs(dz) <= MEIA_AVENIDA) continue;
            set(X_VESTIBULO, 1, dz, Math.floorMod(dz, 4) == 0 ? Material.CHISELED_STONE_BRICKS : PEDRA);
            set(X_VESTIBULO, 2, dz, Material.STONE_BRICK_SLAB);
        }
        // a passagem: dois pilares com lanterna
        for (int s : new int[]{-1, 1}) {
            int dz = s * (MEIA_AVENIDA + 1);
            for (int y = 1; y <= 4; y++) set(X_VESTIBULO, y, dz, eixo(Material.CRIMSON_STEM, Axis.Y));
            set(X_VESTIBULO, 5, dz, lanterna(Material.LANTERN, false));
        }
        // bancos encostados na parede da frente
        for (int s : new int[]{-1, 1}) {
            for (int dz = 10; dz <= 30; dz += 10) {
                for (int i = -1; i <= 1; i++) set(X_FRENTE - 1, 1, s * (dz + i), Planta.escada(Material.STONE_BRICK_STAIRS, org.bukkit.block.BlockFace.EAST, false));
            }
        }
    }

    /**
     * Escadaria fechada do portão do spawn até a porta do salão: 7 de largura, patamar no nível do
     * túnel do portão (com o mesmo arco, 9, 8 e 7 de altura), um degrau por bloco descendo pro oeste e
     * outro patamar com o arco da porta lá embaixo. Cada coluna tem parede e teto até 11 acima do degrau,
     * então na troca de altura não sobra fresta. Em cima dos degraus, o teto é de escada de cabeça pra
     * baixo e desce junto com eles, sem os dentes de um bloco; a luz fica embutida nele.
     */
    private void escadaria() {
        for (int x = X_TOPO; x > X_FRENTE; x--) {
            int passo = X_TOPO - x - PATAMAR;             // 0 no primeiro degrau
            boolean patamar = passo < 0 || passo >= PROFUNDIDADE;
            int degrau = passo < 0 ? 0 : -Math.min(passo, PROFUNDIDADE);
            for (int dz = -MEIA_AVENIDA - 1; dz <= MEIA_AVENIDA + 1; dz++) {
                if (Math.abs(dz) == MEIA_AVENIDA + 1) {
                    for (int y = degrau - 1; y <= degrau + 11; y++) set(x, y, dz, pedra(x, y, dz));
                    set(x, degrau + 1, dz, FAIXA);
                    continue;
                }
                int livre = !patamar ? ALTURA_ESCADA : Math.abs(dz) <= 1 ? 9 : Math.abs(dz) == 2 ? 8 : 7;
                set(x, degrau - 1, dz, PEDRA);
                if (patamar) set(x, degrau, dz, Math.abs(dz) == MEIA_AVENIDA ? PEDRA : CALCADA);
                else set(x, degrau, dz, Planta.escada(Material.STONE_BRICK_STAIRS, org.bukkit.block.BlockFace.EAST, false));
                for (int y = degrau + 1; y <= degrau + livre; y++) set(x, y, dz, Material.AIR);
                for (int y = degrau + livre + 1; y <= degrau + 11; y++) set(x, y, dz, pedra(x, y, dz));
                boolean luz = dz == 0 && Math.floorMod(x, 4) == 0;
                if (luz) set(x, degrau + livre + 1, dz, Material.SHROOMLIGHT);
                else if (!patamar) set(x, degrau + livre, dz, Planta.escada(Material.STONE_BRICK_STAIRS, org.bukkit.block.BlockFace.WEST, true));
            }
        }
    }

    // ------------------------------------------------------------------ cenário

    /**
     * Cada par de estátuas ganha um cantinho com o tema delas (granja, lama, pasto, cemitério, ossada,
     * floresta sombria, floresta, pântano, Nether e o Fim no fundo). Nada é posto na avenida nem a até
     * 4 blocos de um spawner (onde os mobs nascem), e as luzes do chão ficam. As estátuas vêm depois e
     * passam por cima do que encostar nelas.
     */
    private void cenario() {
        for (Lugar l : LUGARES) {
            Random r = new Random(l.tipo().hashCode());
            int c = l.x(), s = l.lado();
            switch (l.tipo()) {
                case "galinha" -> granja(c, s, r);
                case "porco" -> lama(c, s, r);
                case "vaca" -> pasto(c, s, r, Material.OAK_LOG, Material.OAK_LEAVES,
                        Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY);
                case "ovelha" -> pasto(c, s, r, Material.BIRCH_LOG, Material.BIRCH_LEAVES,
                        Material.ALLIUM, Material.PINK_TULIP, Material.AZURE_BLUET, Material.WHITE_TULIP);
                case "zumbi" -> cemiterio(c, s, r);
                case "esqueleto" -> ossada(c, s, r);
                case "aranha" -> florestaSombria(c, s, r);
                case "creeper" -> floresta(c, s, r);
                case "slime" -> pantano(c, s, r);
                case "blaze" -> nether(c, s, r);
                default -> fim(r);
            }
        }
    }

    /** Lugar onde o cenário não entra: avenida, perto de spawner, vestíbulo e paredes. */
    private boolean reservado(int x, int dz) {
        if (x >= X_LIMITE - 1 || x <= X_FUNDO || Math.abs(dz) >= DZ_PAREDE) return true;
        if (Math.abs(dz) <= MEIA_AVENIDA + 1 && x > X_FUNDO + 20) return true;
        for (Lugar l : LUGARES) {
            int[] sp = spawner(l);
            if (Math.abs(x - sp[0]) <= 4 && Math.abs(dz - sp[1]) <= 4) return true;
        }
        return false;
    }

    /** Troca o chão (sem apagar as luzes). */
    private void chao(int x, int dz, Material m) {
        if (reservado(x, dz) || p.get(x, base, dz) == Material.SHROOMLIGHT) return;
        set(x, 0, dz, m);
    }

    private void enfeite(int x, int y, int dz, BlockData d) {
        if (!reservado(x, dz)) set(x, y, dz, d);
    }

    private void enfeite(int x, int y, int dz, Material m) {
        enfeite(x, y, dz, m.createBlockData());
    }

    /** Mancha redonda de chão. */
    private void mancha(int cx, int cdz, double raio, Random r, Material... materiais) {
        int ri = (int) Math.ceil(raio);
        for (int a = -ri; a <= ri; a++) {
            for (int b = -ri; b <= ri; b++) {
                if (a * a + b * b > raio * raio || r.nextInt(6) == 0) continue;
                chao(cx + a, cdz + b, materiais[r.nextInt(materiais.length)]);
            }
        }
    }

    /** Planta em cima da grama em {@code vezes} lugares sorteados do retângulo. */
    private void espalhar(int x1, int x2, int dz1, int dz2, int vezes, Random r, Material... plantas) {
        for (int i = 0; i < vezes; i++) {
            int x = x1 + r.nextInt(x2 - x1 + 1), dz = Math.min(dz1, dz2) + r.nextInt(Math.abs(dz2 - dz1) + 1);
            Material chao = p.get(x, base, dz);
            if (chao != Material.GRASS_BLOCK && chao != Material.MOSS_BLOCK && chao != Material.PODZOL
                    && chao != Material.COARSE_DIRT) continue;
            if (p.get(x, base + 1, dz) != null && p.get(x, base + 1, dz) != Material.AIR) continue;
            enfeite(x, 1, dz, plantas[r.nextInt(plantas.length)]);
        }
    }

    private static BlockData folha(Material m) {
        org.bukkit.block.data.type.Leaves f = (org.bukkit.block.data.type.Leaves) m.createBlockData();
        f.setPersistent(true);
        return f;
    }

    /** Árvore de copa redonda; {@code grosso} = tronco 2x2 (carvalho escuro). */
    private void arvore(int x, int dz, Material tronco, Material folhas, int altura, double raio, boolean grosso, Random r) {
        int g = grosso ? 1 : 0;
        for (int y = 1; y <= altura; y++) {
            for (int a = 0; a <= g; a++) for (int b = 0; b <= g; b++) set(x + a, y, dz + b, eixo(tronco, Axis.Y));
        }
        BlockData f = folha(folhas);
        double[][] camadas = {{altura - 2, raio - 0.6}, {altura - 1, raio}, {altura, raio}, {altura + 1, raio - 1.2}, {altura + 2, 1.2}};
        double meio = g / 2.0;
        for (double[] cam : camadas) {
            int y = (int) cam[0], ri = (int) Math.ceil(cam[1]) + g;
            for (int a = -ri; a <= ri + g; a++) {
                for (int b = -ri; b <= ri + g; b++) {
                    double d = Math.hypot(a - meio, b - meio);
                    if (d > cam[1] + 0.3 || (d > cam[1] - 0.7 && r.nextInt(3) == 0)) continue;
                    Material ja = p.get(x + a, base + y, dz + b);
                    if (ja != null && ja != Material.AIR) continue;
                    set(x + a, y, dz + b, f);
                }
            }
        }
    }

    /** Árvore seca: tronco e uns galhos, sem folha. */
    private void arvoreSeca(int x, int dz, Material tronco, int altura) {
        for (int y = 1; y <= altura; y++) set(x, y, dz, eixo(tronco, Axis.Y));
        set(x + 1, altura - 1, dz, eixo(tronco, Axis.X));
        set(x + 2, altura, dz, eixo(tronco, Axis.Y));
        set(x - 1, altura - 2, dz, eixo(tronco, Axis.X));
        set(x - 2, altura - 1, dz, eixo(tronco, Axis.Y));
        set(x, altura - 1, dz + 1, eixo(tronco, Axis.Z));
        set(x, altura + 1, dz, eixo(tronco, Axis.Y));
    }

    /** Poste de cerca com lanterna (comum ou de alma) em cima. */
    private void poste(int x, int dz, Material cerca, Material luz) {
        enfeite(x, 1, dz, cerca);
        enfeite(x, 2, dz, cerca);
        enfeite(x, 3, dz, lanterna(luz, false));
    }

    /** Cerca/vidro conectado aos vizinhos que também são dela (lista de [x, dz]). */
    private void cercar(List<int[]> pontos, int y, Material cerca) {
        java.util.Set<Long> tem = new java.util.HashSet<>();
        for (int[] pt : pontos) tem.add(((long) pt[0] << 32) ^ (pt[1] & 0xffffffffL));
        for (int[] pt : pontos) {
            List<org.bukkit.block.BlockFace> faces = new ArrayList<>();
            if (tem.contains(((long) (pt[0] + 1) << 32) ^ (pt[1] & 0xffffffffL))) faces.add(org.bukkit.block.BlockFace.EAST);
            if (tem.contains(((long) (pt[0] - 1) << 32) ^ (pt[1] & 0xffffffffL))) faces.add(org.bukkit.block.BlockFace.WEST);
            if (tem.contains(((long) pt[0] << 32) ^ ((pt[1] + 1) & 0xffffffffL))) faces.add(org.bukkit.block.BlockFace.SOUTH);
            if (tem.contains(((long) pt[0] << 32) ^ ((pt[1] - 1) & 0xffffffffL))) faces.add(org.bukkit.block.BlockFace.NORTH);
            set(pt[0], y, pt[1], Planta.conectado(cerca, faces.toArray(new org.bukkit.block.BlockFace[0])));
        }
    }

    // --- os temas: c = x da estátua, s = lado (-1 norte, +1 sul); "s * n" é a distância n da avenida

    /** Galinha: galinheiro de tábua, cercadinho, feno e dente-de-leão. */
    private void granja(int c, int s, Random r) {
        for (int x = c + 6; x <= c + 9; x++) {
            for (int a = 22; a <= 26; a++) {
                boolean borda = x == c + 6 || x == c + 9 || a == 22 || a == 26;
                chao(x, s * a, Material.OAK_PLANKS);
                for (int y = 1; y <= 2; y++) {
                    boolean porta = a == 22 && (x == c + 7 || x == c + 8) && y <= 2;
                    if (borda && !porta) set(x, y, s * a, Material.OAK_PLANKS);
                }
                set(x, 3, s * a, laje(Material.SPRUCE_SLAB, false));
            }
        }
        set(c + 8, 1, s * 25, eixo(Material.HAY_BLOCK, Axis.Y));
        set(c + 7, 1, s * 25, eixo(Material.HAY_BLOCK, Axis.Y));
        List<int[]> cerca = new ArrayList<>();
        for (int x = c + 5; x <= c + 10; x++) cerca.add(new int[]{x, s * 19});
        cercar(cerca, 1, Material.OAK_FENCE);
        int[][] feno = {{-8, 24, 1}, {-8, 25, 1}, {-9, 24, 1}, {-8, 24, 2}, {-9, 27, 1}};
        for (int[] f : feno) enfeite(c + f[0], f[2], s * f[1], eixo(Material.HAY_BLOCK, Axis.Y));
        enfeite(c - 7, 1, s * 20, Material.COMPOSTER);
        espalhar(c - 10, c + 10, s * 5, s * 34, 30, r, Material.DANDELION, Material.SHORT_GRASS);
    }

    /** Porco: lamaçal, cocho com água, feno e um carvalho na beira do rio. */
    private void lama(int c, int s, Random r) {
        mancha(c - 7, s * 22, 3.2, r, Material.MUD, Material.MUD, Material.PACKED_MUD);
        mancha(c + 7, s * 25, 3.0, r, Material.MUD, Material.COARSE_DIRT);
        enfeite(c + 8, 1, s * 18, Material.WATER_CAULDRON);
        enfeite(c + 9, 1, s * 18, Material.WATER_CAULDRON);
        enfeite(c + 7, 1, s * 18, eixo(Material.HAY_BLOCK, Axis.Y));
        arvore(c - 9, s * 31, Material.OAK_LOG, Material.OAK_LEAVES, 6, 2.6, false, r);
        espalhar(c - 10, c + 10, s * 5, s * 34, 18, r, Material.SHORT_GRASS, Material.FERN);
    }

    /** Vaca e ovelha: pasto florido com duas árvores. */
    private void pasto(int c, int s, Random r, Material tronco, Material folhas, Material... flores) {
        arvore(c - 9, s * 30, tronco, folhas, 6, 2.8, false, r);
        arvore(c + 9, s * 28, tronco, folhas, 5, 2.4, false, r);
        for (int i = 0; i < 4; i++) enfeite(c - 8 + r.nextInt(17), 1, s * (17 + r.nextInt(4)), folha(Material.FLOWERING_AZALEA_LEAVES));
        espalhar(c - 10, c + 10, s * 5, s * 36, 55, r, flores);
        espalhar(c - 10, c + 10, s * 5, s * 36, 15, r, Material.SHORT_GRASS);
    }

    /** Zumbi: cemitério de terra batida, lápides, árvores secas, teia e lanternas de alma. */
    private void cemiterio(int c, int s, Random r) {
        for (int i = 0; i < 6; i++) mancha(c - 9 + r.nextInt(19), s * (8 + r.nextInt(28)), 2.5, r, Material.PODZOL, Material.COARSE_DIRT, Material.ROOTED_DIRT);
        for (int gx : new int[]{c - 10, c - 8, c + 8, c + 10}) {
            for (int a : new int[]{17, 21, 25}) {
                enfeite(gx, 1, s * a, r.nextBoolean() ? Material.MOSSY_COBBLESTONE_WALL : Material.COBBLESTONE_WALL);
                chao(gx, s * (a - 1), Material.COARSE_DIRT);
                chao(gx, s * (a - 2), Material.COARSE_DIRT);
                if (r.nextInt(3) == 0) enfeite(gx, 1, s * (a - 1), Material.COBWEB);
            }
        }
        arvoreSeca(c - 9, s * 32, Material.SPRUCE_LOG, 6);
        arvoreSeca(c + 9, s * 31, Material.DARK_OAK_LOG, 5);
        poste(c - 7, s * 12, Material.SPRUCE_FENCE, Material.SOUL_LANTERN);
        poste(c + 7, s * 12, Material.SPRUCE_FENCE, Material.SOUL_LANTERN);
        espalhar(c - 10, c + 10, s * 5, s * 36, 14, r, Material.DEAD_BUSH);
    }

    /** Esqueleto: costelas gigantes de osso, terra de alma, crânios e fogueira de alma. */
    private void ossada(int c, int s, Random r) {
        for (int i = 0; i < 5; i++) mancha(c - 9 + r.nextInt(19), s * (8 + r.nextInt(28)), 2.3, r, Material.SOUL_SOIL, Material.COARSE_DIRT);
        for (int base0 : new int[]{c + 6, c - 10}) {
            for (int x = base0; x <= base0 + 4; x++) set(x, 4, s * 22, eixo(Material.BONE_BLOCK, Axis.X)); // espinha
            for (int x = base0; x <= base0 + 4; x += 2) {
                for (int t : new int[]{-1, 1}) {
                    set(x, 3, s * 22 + t * 2, eixo(Material.BONE_BLOCK, Axis.Y));
                    set(x, 2, s * 22 + t * 3, eixo(Material.BONE_BLOCK, Axis.Y));
                    set(x, 1, s * 22 + t * 3, eixo(Material.BONE_BLOCK, Axis.Y));
                }
            }
        }
        enfeite(c + 8, 1, s * 17, Material.SKELETON_SKULL);
        enfeite(c - 6, 1, s * 30, Material.SKELETON_SKULL);
        enfeite(c - 9, 1, s * 16, Material.SOUL_CAMPFIRE);
        espalhar(c - 10, c + 10, s * 5, s * 36, 12, r, Material.DEAD_BUSH);
    }

    /** Aranha: carvalhos escuros grossos cheios de teia, cogumelos e musgo. */
    private void florestaSombria(int c, int s, Random r) {
        for (int i = 0; i < 6; i++) mancha(c - 9 + r.nextInt(19), s * (8 + r.nextInt(28)), 2.5, r, Material.PODZOL, Material.MOSS_BLOCK);
        arvore(c - 11, s * 30, Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, 7, 3.6, true, r);
        arvore(c + 10, s * 31, Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, 6, 3.2, true, r);
        for (int i = 0; i < 18; i++) {
            int x = c - 10 + r.nextInt(21), a = 16 + r.nextInt(20), y = 1 + r.nextInt(4);
            Material ja = p.get(x, base + y, s * a);
            if (ja == null || ja == Material.AIR) enfeite(x, y, s * a, Material.COBWEB);
        }
        espalhar(c - 10, c + 10, s * 5, s * 36, 16, r, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.FERN);
    }

    /** Creeper: floresta de carvalho e pinheiro com samambaia e a cratera de uma explosão. */
    private void floresta(int c, int s, Random r) {
        arvore(c - 8, s * 31, Material.OAK_LOG, Material.OAK_LEAVES, 6, 2.8, false, r);
        arvore(c + 8, s * 32, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 7, 2.4, false, r);
        arvore(c - 9, s * 21, Material.OAK_LOG, Material.OAK_LEAVES, 5, 2.2, false, r);
        // a cratera: chão revirado, cascalho e pedra solta
        mancha(c + 8, s * 20, 2.8, r, Material.COARSE_DIRT, Material.GRAVEL, Material.ROOTED_DIRT);
        enfeite(c + 9, 1, s * 21, Material.COBBLESTONE);
        enfeite(c + 7, 1, s * 18, Material.MOSSY_COBBLESTONE);
        espalhar(c - 10, c + 10, s * 5, s * 36, 30, r, Material.FERN, Material.SHORT_GRASS, Material.SHORT_GRASS);
    }

    /** Slime: pântano com laguinhos, vitória-régia, argila, lama e mangues. */
    private void pantano(int c, int s, Random r) {
        mancha(c, s * 22, 5, r, Material.MUD, Material.MUD, Material.CLAY);
        lago(c - 8, s * 24, 3.2, r);
        lago(c + 7, s * 21, 2.6, r);
        for (int x : new int[]{c - 9, c + 8}) {
            int dz = s * 33;
            for (int[] raiz : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) enfeite(x + raiz[0], 1, dz + raiz[1], Material.MANGROVE_ROOTS);
            arvore(x, dz, Material.MANGROVE_LOG, Material.MANGROVE_LEAVES, 6, 2.8, false, r);
        }
        espalhar(c - 10, c + 10, s * 5, s * 36, 14, r, Material.SHORT_GRASS, Material.FERN);
    }

    /** Laguinho raso (2 de fundo) com argila embaixo e vitória-régia. */
    private void lago(int cx, int cdz, double raio, Random r) {
        int ri = (int) Math.ceil(raio);
        for (int a = -ri; a <= ri; a++) {
            for (int b = -ri; b <= ri; b++) {
                if (a * a + b * b > raio * raio || reservado(cx + a, cdz + b)) continue;
                set(cx + a, -2, cdz + b, Material.CLAY);
                set(cx + a, -1, cdz + b, Material.WATER);
                set(cx + a, 0, cdz + b, Material.WATER);
                if (r.nextInt(5) == 0) set(cx + a, 1, cdz + b, Material.LILY_PAD);
            }
        }
    }

    /** Blaze: chão do Nether, poços de lava cercados, braseiros eternos e um portal em ruínas. */
    private void nether(int c, int s, Random r) {
        for (int x = c - 10; x <= c + 10; x++) {
            for (int a = 5; a < DZ_PAREDE; a++) {
                int t = r.nextInt(10);
                chao(x, s * a, t < 6 ? Material.NETHERRACK : t < 8 ? Material.BLACKSTONE : t < 9 ? Material.SOUL_SAND : Material.NETHER_WART_BLOCK);
            }
        }
        for (int x : new int[]{c - 8, c + 8}) {
            int dz = s * 21;
            List<int[]> cerca = new ArrayList<>();
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) == 2) {
                        set(x + a, 0, dz + b, Material.MAGMA_BLOCK);
                        cerca.add(new int[]{x + a, dz + b});
                    } else {
                        set(x + a, -1, dz + b, Material.NETHERRACK);
                        set(x + a, 0, dz + b, Material.LAVA);
                    }
                }
            }
            cercar(cerca, 1, Material.NETHER_BRICK_FENCE);
        }
        // braseiros: coluna de basalto com fogo eterno em cima do netherrack
        for (int[] b : new int[][]{{-10, 34}, {10, 34}, {-10, 13}, {10, 13}}) {
            int x = c + b[0], dz = s * b[1];
            for (int y = 1; y <= 2; y++) set(x, y, dz, eixo(Material.POLISHED_BASALT, Axis.Y));
            set(x, 3, dz, Material.NETHERRACK);
            set(x, 4, dz, Material.FIRE);
        }
        // portal em ruínas encostado na ponta da área, com uns blocos faltando
        int px = c + 10;
        for (int a = 28; a <= 31; a++) {
            for (int y = 1; y <= 5; y++) {
                boolean moldura = a == 28 || a == 31 || y == 1 || y == 5;
                if (!moldura || (a == 31 && y >= 4) || (a == 29 && y == 5)) continue;
                set(px, y, s * a, r.nextInt(3) == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
            }
        }
    }

    /** Enderman (fundo): chão de pedra do Fim, pilares de obsidiana com vara do fim e coros. */
    private void fim(Random r) {
        for (int x = X_FUNDO + 1; x <= X_FUNDO + 23; x++) {
            for (int dz = -9; dz <= 9; dz++) {
                if (r.nextInt(8) == 0) continue;
                chao(x, dz, r.nextInt(4) == 0 ? Material.END_STONE_BRICKS : Material.END_STONE);
            }
        }
        int[][] pilares = {{X_FUNDO + 8, -8, 6}, {X_FUNDO + 8, 8, 6}, {X_FUNDO + 18, -8, 5}, {X_FUNDO + 18, 8, 5}};
        for (int[] pl : pilares) {
            for (int y = 1; y <= pl[2]; y++) set(pl[0], y, pl[1], r.nextInt(4) == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
            set(pl[0], pl[2] + 1, pl[1], direcionalCima(Material.END_ROD));
        }
        for (int[] co : new int[][]{{X_FUNDO + 13, -7, 3}, {X_FUNDO + 13, 7, 4}, {X_FUNDO + 21, -6, 2}, {X_FUNDO + 21, 6, 3}}) {
            for (int y = 1; y <= co[2]; y++) set(co[0], y, co[1], Planta.conectado(Material.CHORUS_PLANT, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN));
            set(co[0], 0, co[1], Material.END_STONE);
            set(co[0], co[2] + 1, co[1], Material.CHORUS_FLOWER);
        }
    }

    private static BlockData direcionalCima(Material m) {
        return Planta.direcional(m, org.bukkit.block.BlockFace.UP);
    }

    // ------------------------------------------------------------------ rio

    /**
     * O rio que atravessa o salão de parede a parede entre a granja e o pasto (x -115..-113), raso (2 de
     * fundo), com uma cascata saindo de cada parede dos dois lados do pilar e a ponte de pedra na avenida.
     */
    private void rio() {
        int x1 = -115, x2 = -113;
        Random r = new Random(7);
        for (int x = x1; x <= x2; x++) {
            for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
                set(x, -2, dz, r.nextInt(3) == 0 ? Material.SAND : Material.GRAVEL);
                set(x, -1, dz, Material.WATER);
                if (Math.abs(dz) <= MEIA_AVENIDA + 1) {
                    set(x, 0, dz, Math.abs(dz) == MEIA_AVENIDA + 1 ? PEDRA : Math.abs(dz) == MEIA_AVENIDA ? PEDRA : CALCADA);
                    continue;
                }
                set(x, 0, dz, Material.WATER);
                set(x, 1, dz, Math.abs(dz) > 8 && r.nextInt(7) == 0 ? Material.LILY_PAD.createBlockData() : Material.AIR.createBlockData());
            }
        }
        // margens: areia e cana-de-açúcar aqui e ali
        for (int x : new int[]{x1 - 1, x2 + 1}) {
            for (int dz = -DZ_PAREDE + 1; dz < DZ_PAREDE; dz++) {
                if (Math.abs(dz) <= MEIA_AVENIDA + 1 || p.get(x, base, dz) == Material.SHROOMLIGHT) continue;
                int t = r.nextInt(8);
                if (t < 2) set(x, 0, dz, Material.SAND);
                if (t == 0 && Math.abs(dz) > 6) {
                    set(x, 1, dz, Material.SUGAR_CANE);
                    if (r.nextBoolean()) set(x, 2, dz, Material.SUGAR_CANE);
                }
            }
        }
        // a ponte: mureta dos dois lados e poste com lanterna nas pontas
        for (int s : new int[]{-1, 1}) {
            int dz = s * (MEIA_AVENIDA + 1);
            List<int[]> mureta = new ArrayList<>();
            for (int x = x1; x <= x2; x++) mureta.add(new int[]{x, dz});
            for (int[] m : mureta) set(m[0], 1, m[1], Material.STONE_BRICK_WALL);
            for (int x : new int[]{x1 - 1, x2 + 1}) {
                set(x, 0, dz, PEDRA);
                set(x, 1, dz, Material.CHISELED_STONE_BRICKS);
                set(x, 2, dz, lanterna(Material.LANTERN, false));
            }
        }
        // cascatas: a água sai da parede no alto e cai no rio, dos dois lados do pilar
        int y0 = 9;
        for (int s : new int[]{-1, 1}) {
            for (int x : new int[]{x1, x2}) {
                set(x, y0, s * (DZ_PAREDE + 1), PEDRA);          // tampa por fora
                set(x, y0, s * DZ_PAREDE, Material.WATER);
                set(x, y0, s * (DZ_PAREDE - 1), agua(1));
                for (int y = 1; y < y0; y++) set(x, y, s * (DZ_PAREDE - 1), agua(8));
            }
        }
    }

    /** Água corrente: 1..7 escorrendo pro lado, 8 caindo. */
    private static BlockData agua(int nivel) {
        org.bukkit.block.data.Levelled a = (org.bukkit.block.data.Levelled) Material.WATER.createBlockData();
        a.setLevel(nivel);
        return a;
    }

    // ------------------------------------------------------------------ painel do PvP

    /**
     * Na parede da frente, em cima da entrada: o painel do PvP em ciclos (AreasPlugin). A moldura é de
     * concreto verde, que o plugin pinta de vermelho quando o PvP liga, e o holograma do tempo fica na
     * frente do miolo preto.
     */
    private void painelPvp() {
        for (int dz = -12; dz <= 12; dz++) {
            for (int y = 14; y <= 22; y++) {
                boolean borda = Math.abs(dz) == 12 || y == 14 || y == 22;
                set(X_FRENTE, y, dz, borda ? Material.LIME_CONCRETE : Material.POLISHED_BLACKSTONE);
            }
        }
        // luz em cima e embaixo, pra ler de longe
        for (int dz : new int[]{-8, 0, 8}) {
            set(X_FRENTE - 1, 23, dz, lanterna(Material.LANTERN, false));
            set(X_FRENTE - 1, 22, dz, Material.POLISHED_BLACKSTONE_BRICKS);
        }
    }

    // ------------------------------------------------------------------ estátuas

    /**
     * Uma estátua em coordenadas próprias: lx da esquerda pra direita de quem olha de frente, ly pra
     * cima (0 = em cima da grama) e lz pra trás (a cara fica no lz menor).
     */
    private static final class Modelo {
        final Map<Long, BlockData> blocos = new HashMap<>();
        final List<int[]> posicoes = new ArrayList<>();

        void set(int x, int y, int z, Material m) {
            long k = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
            if (!blocos.containsKey(k)) posicoes.add(new int[]{x, y, z});
            blocos.put(k, m.createBlockData());
        }

        BlockData get(int[] pos) {
            return blocos.get(((long) (pos[0] + 512) << 40) | ((long) (pos[1] + 512) << 20) | (pos[2] + 512));
        }

        void caixa(Material m, int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
                }
            }
        }

        /** Caixa com o material sorteado por posição (pele de creeper, couro de vaca...). */
        void caixa(Material[] mistura, int[] pesos, int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        int s = sorte(x, y, z), acumulado = 0;
                        for (int i = 0; i < mistura.length; i++) {
                            acumulado += pesos[i];
                            if (s < acumulado || i == mistura.length - 1) {
                                set(x, y, z, mistura[i]);
                                break;
                            }
                        }
                    }
                }
            }
        }

        /** Pinta a cara: as linhas de cima pra baixo, a partir de (xIni, yTopo), no plano z. '.' não mexe. */
        void rosto(int xIni, int yTopo, int z, Map<Character, Material> cores, String... linhas) {
            for (int i = 0; i < linhas.length; i++) {
                for (int j = 0; j < linhas[i].length(); j++) {
                    Material m = cores.get(linhas[i].charAt(j));
                    if (m != null) set(xIni + j, yTopo - i, z, m);
                }
            }
        }
    }

    private void estatua(Lugar l) {
        Modelo m = switch (l.tipo()) {
            case "zumbi" -> zumbi();
            case "esqueleto" -> esqueleto();
            case "creeper" -> creeper();
            case "aranha" -> aranha();
            case "slime" -> slime();
            case "enderman" -> enderman();
            case "blaze" -> blaze();
            case "vaca" -> vaca();
            case "porco" -> porco();
            case "ovelha" -> ovelha();
            default -> galinha();
        };
        int maxZ = Integer.MIN_VALUE;
        for (int[] pos : m.posicoes) maxZ = Math.max(maxZ, pos[2]);
        for (int[] pos : m.posicoes) {
            int[] w = mundo(l, pos[0], pos[2], maxZ);
            set(w[0], 1 + pos[1], w[1], m.get(pos)); // direto na grama, sem pedestal
        }
    }

    /**
     * Coordenada da estátua → (x, dz) do mundo. A de trás da estátua (lz máximo) fica a 3 blocos da
     * parede; a cara vira pra avenida (as dos lados) ou pra entrada (a do fundo).
     */
    private static int[] mundo(Lugar l, int lx, int lz, int maxZ) {
        int recuo = maxZ - lz; // 0 nas costas, cresce pra frente
        return switch (l.lado()) {
            case -1 -> new int[]{l.x() + lx, -DZ_PAREDE + 3 + recuo};   // norte, olhando pro sul
            case 1 -> new int[]{l.x() - lx, DZ_PAREDE - 3 - recuo};     // sul, olhando pro norte
            default -> new int[]{X_FUNDO + 3 + recuo, -lx};             // fundo, olhando pro leste
        };
    }

    private static Map<Character, Material> cores(Object... pares) {
        Map<Character, Material> mapa = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) mapa.put((Character) pares[i], (Material) pares[i + 1]);
        return mapa;
    }

    // Os modelos, na escala de 3 blocos pra 4 pixels do jogo (a cabeça de 8 pixels vira 6 blocos).

    private static Modelo zumbi() {
        Modelo m = new Modelo();
        Material pele = Material.LIME_TERRACOTTA, cabelo = Material.GREEN_TERRACOTTA;
        m.caixa(Material.BLUE_CONCRETE, -3, 0, 0, -1, 8, 2);          // pernas
        m.caixa(Material.BLUE_CONCRETE, 0, 0, 0, 2, 8, 2);
        m.caixa(Material.GRAY_CONCRETE, -3, 0, 0, 2, 0, 2);           // sapatos
        m.caixa(Material.CYAN_CONCRETE, -3, 9, 0, 2, 17, 2);          // camisa
        for (int x : new int[]{-6, 3}) {                              // braços esticados pra frente
            m.caixa(Material.CYAN_CONCRETE, x, 15, 0, x + 2, 17, 2);
            m.caixa(pele, x, 15, -7, x + 2, 17, -1);
        }
        m.caixa(pele, -3, 18, -2, 2, 23, 3);                          // cabeça
        m.caixa(cabelo, -3, 23, -2, 2, 23, 3);
        m.caixa(cabelo, -3, 20, 3, 2, 23, 3);
        m.rosto(-3, 23, -2, cores('H', cabelo, 'S', pele, 'K', Material.BLACK_CONCRETE, 'D', Material.GREEN_CONCRETE),
                "HHHHHH", "SSSSSS", "SKSSKS", "SSDDSS", "SDDDDS", "SSSSSS");
        return m;
    }

    private static Modelo esqueleto() {
        Modelo m = new Modelo();
        Material osso = Material.CALCITE, sombra = Material.LIGHT_GRAY_CONCRETE;
        m.caixa(osso, -2, 0, 1, -2, 8, 1);                            // pernas finas
        m.caixa(osso, 1, 0, 1, 1, 8, 1);
        m.caixa(osso, -3, 9, 0, 2, 9, 2);                             // bacia
        for (int y = 10; y <= 17; y++) {                              // costelas e espinha
            if (y % 2 == 0 && y < 16) m.caixa(sombra, -1, y, 1, 0, y, 1);
            else m.caixa(osso, -3, y, 0, 2, y, 2);
        }
        m.caixa(osso, -4, 9, 1, -4, 17, 1);                           // braço solto
        m.caixa(osso, 3, 17, -6, 3, 17, 1);                           // braço com o arco, pra frente
        m.caixa(Material.SPRUCE_PLANKS, 3, 15, -7, 3, 19, -7);        // arco
        m.set(3, 14, -6, Material.SPRUCE_PLANKS);
        m.set(3, 20, -6, Material.SPRUCE_PLANKS);
        m.caixa(Material.COBWEB, 3, 15, -5, 3, 16, -5);
        m.caixa(Material.COBWEB, 3, 18, -5, 3, 19, -5);
        m.caixa(osso, -3, 18, -2, 2, 23, 3);                          // crânio
        m.rosto(-3, 23, -2, cores('C', osso, 'K', Material.BLACK_CONCRETE, 'G', Material.GRAY_CONCRETE, 'L', sombra),
                "CCCCCC", "CCCCCC", "CKCCKC", "CKLLKC", "CCGGCC", "LGLGLG");
        return m;
    }

    private static Modelo creeper() {
        Modelo m = new Modelo();
        Material[] pele = {Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.LIME_TERRACOTTA, Material.WHITE_CONCRETE};
        int[] pesos = {58, 24, 14, 4};
        for (int x : new int[]{-3, 0}) {                              // quatro patas
            m.caixa(pele, pesos, x, 0, -3, x + 2, 3, -1);
            m.caixa(pele, pesos, x, 0, 3, x + 2, 3, 5);
        }
        m.caixa(pele, pesos, -3, 4, 0, 2, 11, 2);                     // corpo
        m.caixa(pele, pesos, -3, 12, -2, 2, 17, 3);                   // cabeça
        m.rosto(-3, 17, -2, cores('K', Material.BLACK_CONCRETE),
                "......", ".K..K.", ".K..K.", "..KK..", ".KKKK.", ".K..K.");
        return m;
    }

    private static Modelo aranha() {
        Modelo m = new Modelo();
        Material corpo = Material.BLACK_TERRACOTTA, cabeca = Material.GRAY_TERRACOTTA, pata = Material.BLACK_CONCRETE;
        m.caixa(cabeca, -3, 3, -6, 2, 8, -1);                         // cabeça
        m.caixa(corpo, -2, 4, 0, 1, 7, 3);                            // tórax
        m.caixa(new Material[]{corpo, cabeca}, new int[]{80, 20}, -4, 3, 4, 3, 8, 12); // abdômen
        m.rosto(-3, 8, -6, cores('D', cabeca, 'R', Material.RED_CONCRETE),
                "DDDDDD", "DDDDDD", "RDRRDR", "DRDDRD", "DDDDDD", "DDDDDD");
        // oito patas, quatro de cada lado: sobem até o joelho e descem até o chão
        int[][] perna = {{1, 6}, {2, 7}, {3, 8}, {4, 8}, {5, 7}, {6, 6}, {6, 5}, {7, 4}, {7, 3}, {7, 2}, {7, 1}, {7, 0}};
        for (int lz : new int[]{-1, 1, 3, 5}) {
            for (int[] pt : perna) {
                m.set(-2 - pt[0], pt[1], lz, pata);
                m.set(1 + pt[0], pt[1], lz, pata);
            }
        }
        return m;
    }

    private static Modelo slime() {
        Modelo m = new Modelo();
        m.caixa(Material.SLIME_BLOCK, -4, 0, 0, 3, 7, 7);
        m.caixa(Material.LIME_TERRACOTTA, -2, 2, 2, 1, 5, 5);         // o miolo, que aparece através
        m.rosto(-4, 7, 0, cores('K', Material.GREEN_CONCRETE),
                "........", "........", ".KK..KK.", ".KK..KK.", "........", "....K...", "........", "........");
        return m;
    }

    private static Modelo enderman() {
        Modelo m = new Modelo();
        Material preto = Material.BLACK_CONCRETE;
        m.caixa(preto, -2, 0, 1, -2, 14, 1);                          // pernas compridas
        m.caixa(preto, 1, 0, 1, 1, 14, 1);
        m.caixa(preto, -3, 15, 0, 2, 23, 2);                          // corpo
        m.caixa(preto, -4, 7, 1, -4, 23, 1);                          // braços até o joelho
        m.caixa(preto, 3, 7, 1, 3, 23, 1);
        m.caixa(preto, -3, 24, -2, 2, 29, 3);                         // cabeça
        m.rosto(-3, 29, -2, cores('M', Material.MAGENTA_CONCRETE, 'P', Material.PURPLE_CONCRETE),
                "......", "......", "MPMMPM", "......", "......", "......");
        return m;
    }

    private static Modelo blaze() {
        Modelo m = new Modelo();
        Material[] pele = {Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_TERRACOTTA};
        int[] pesos = {60, 28, 12};
        m.caixa(pele, pesos, -3, 12, -2, 2, 17, 3);                   // cabeça flutuando
        m.rosto(-3, 17, -2, cores('K', Material.BLACK_CONCRETE, 'O', Material.ORANGE_CONCRETE, 'B', Material.BROWN_CONCRETE),
                "......", "......", ".K..K.", "OOOOOO", ".OBBO.", "......");
        // três anéis de varas (brilhando) em volta, cada um mais fechado
        int[][][] aneis = {
                {{-6, 1}, {5, 0}, {-1, -5}, {0, 6}},
                {{-5, -3}, {4, -3}, {-5, 4}, {4, 4}},
                {{-3, 1}, {2, 0}, {-1, -2}, {0, 3}}};
        int[] alturas = {8, 4, 0};
        for (int i = 0; i < 3; i++) {
            for (int[] v : aneis[i]) m.caixa(Material.SHROOMLIGHT, v[0], alturas[i], v[1], v[0], alturas[i] + 3, v[1]);
        }
        return m;
    }

    private static Modelo vaca() {
        Modelo m = new Modelo();
        Material[] couro = {Material.BROWN_TERRACOTTA, Material.WHITE_CONCRETE};
        int[] pesos = {72, 28};
        for (int x : new int[]{-4, 1}) {                              // pernas
            m.caixa(couro, pesos, x, 0, 0, x + 2, 8, 2);
            m.caixa(couro, pesos, x, 0, 10, x + 2, 8, 12);
            m.caixa(Material.GRAY_CONCRETE, x, 0, 0, x + 2, 0, 2);
            m.caixa(Material.GRAY_CONCRETE, x, 0, 10, x + 2, 0, 12);
        }
        m.caixa(couro, pesos, -4, 9, 0, 3, 16, 12);                   // corpo
        m.caixa(Material.PINK_CONCRETE, -1, 8, 7, 0, 8, 8);           // úbere
        m.caixa(Material.BROWN_TERRACOTTA, -3, 12, -4, 2, 17, -1);    // cabeça
        m.rosto(-3, 17, -4, cores('B', Material.BROWN_TERRACOTTA, 'W', Material.WHITE_CONCRETE, 'K', Material.BLACK_CONCRETE),
                "BBBBBB", "BBWWBB", "KWWWWK", "BWWWWB", "BWWWWB", "BBBBBB");
        m.caixa(Material.WHITE_TERRACOTTA, -2, 12, -5, 1, 13, -5);    // focinho
        m.set(-2, 12, -5, Material.GRAY_TERRACOTTA);
        m.set(1, 12, -5, Material.GRAY_TERRACOTTA);
        m.caixa(Material.WHITE_TERRACOTTA, -4, 17, -3, -4, 18, -3);   // chifres
        m.caixa(Material.WHITE_TERRACOTTA, 3, 17, -3, 3, 18, -3);
        return m;
    }

    private static Modelo porco() {
        Modelo m = new Modelo();
        Material rosa = Material.PINK_WOOL;
        for (int x : new int[]{-4, 1}) {
            m.caixa(rosa, x, 0, 0, x + 2, 4, 2);
            m.caixa(rosa, x, 0, 9, x + 2, 4, 11);
        }
        m.caixa(rosa, -4, 5, 0, 3, 10, 11);                           // corpo
        m.caixa(rosa, -3, 6, -6, 2, 11, -1);                          // cabeça
        m.rosto(-3, 11, -6, cores('W', Material.WHITE_CONCRETE, 'K', Material.BLACK_CONCRETE),
                "......", "WK..KW");
        m.rosto(-2, 8, -7, cores('P', Material.PINK_CONCRETE, 'N', Material.PINK_TERRACOTTA),
                "PPPP", "NPPN");                                      // focinho
        return m;
    }

    private static Modelo ovelha() {
        Modelo m = new Modelo();
        Material la = Material.WHITE_WOOL, pele = Material.WHITE_TERRACOTTA;
        for (int x : new int[]{-4, 1}) {
            for (int z : new int[]{0, 9}) {
                m.caixa(pele, x, 0, z, x + 2, 2, z + 2);
                m.caixa(la, x, 3, z, x + 2, 5, z + 2);
            }
        }
        m.caixa(la, -5, 6, -1, 4, 14, 12);                            // corpo fofo
        m.caixa(pele, -3, 10, -6, 2, 15, -2);                         // cabeça
        m.caixa(la, -3, 15, -6, 2, 15, -2);                           // topete de lã
        m.rosto(-3, 15, -6, cores('W', la, 'S', pele, 'K', Material.BLACK_CONCRETE, 'B', Material.WHITE_CONCRETE, 'P', Material.PINK_TERRACOTTA),
                "WWWWWW", "SSSSSS", "BKSSKB", "SSSSSS", "SSPPSS", "SSSSSS");
        return m;
    }

    private static Modelo galinha() {
        Modelo m = new Modelo();
        Material pena = Material.WHITE_WOOL, pe = Material.YELLOW_TERRACOTTA;
        for (int x : new int[]{-2, 1}) {                              // pernas e pés
            m.caixa(pe, x, 1, 3, x, 4, 3);
            m.caixa(pe, x, 0, 1, x, 0, 4);
        }
        m.caixa(pena, -3, 5, 0, 2, 10, 7);                            // corpo
        m.caixa(pena, -4, 6, 1, -4, 9, 6);                            // asas
        m.caixa(pena, 3, 6, 1, 3, 9, 6);
        m.caixa(pena, -2, 9, -3, 1, 14, -1);                          // cabeça
        m.set(-2, 13, -3, Material.BLACK_CONCRETE);                   // olhos
        m.set(1, 13, -3, Material.BLACK_CONCRETE);
        m.caixa(Material.ORANGE_CONCRETE, -2, 11, -5, 1, 12, -4);     // bico
        m.caixa(Material.RED_CONCRETE, -1, 9, -4, 0, 10, -4);         // barbela
        return m;
    }
}
