package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.Random;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;

/**
 * Pátio de areia rebaixado com paredes neoclássicas, no tamanho da "AxePvP Arena" da CurseForge.
 * É arena de X1: os dois lutadores nascem nos nichos das paredes curtas (fechados, sem saída), o
 * pátio é coberto de vidro na altura do topo das paredes e, em cima, fica a galeria dos
 * espectadores, cercada por um parapeito. Depois de construir, registre com /duelo arena criar +
 * pos1/pos2 (no fundo de cada nicho, olhando pro pátio). Cada parede é uma sequência de módulos pilar (3) + painel (5),
 * começando e terminando em pilar; os painéis são janela, porta grande com óculo ou entrada (túnel).
 *
 * Nas paredes trabalhamos em coordenadas locais: u corre ao longo da parede (esquerda → direita de
 * quem olha pra ela de dentro), w é a profundidade (w = 0 é a face, w < 0 avança pra dentro da arena,
 * w > 0 é o maciço) e y é a altura acima do piso.
 */
public class ArenaAxePvP implements Estrutura {

    private static final int PILAR = 3;
    private static final int PAINEL = 5;
    private static final int MODULO = PILAR + PAINEL;
    private static final int PAINEIS_X = 5;
    private static final int PAINEIS_Z = 3;
    private static final int COMPRIMENTO_X = PILAR + MODULO * PAINEIS_X; // 43
    private static final int COMPRIMENTO_Z = PILAR + MODULO * PAINEIS_Z; // 27
    private static final int ALTURA = 15;
    private static final int ESPESSURA = 8;
    private static final int FOLGA_ACIMA = 12;
    private static final int FUNDO_NICHO = 2;    // o nicho de cada lutador vai de w = 0 a 2
    private static final int ALTURA_PARAPEITO = 4;

    // Canto do piso (bloco de menor x/z), com o centro da arena em (0, 0).
    private static final int X0 = -COMPRIMENTO_X / 2;
    private static final int Z0 = -COMPRIMENTO_Z / 2;

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material ESCADA = Material.STONE_BRICK_STAIRS;
    private static final Material LAJE = Material.STONE_BRICK_SLAB;
    private static final Material FUNDO = Material.MUD_BRICKS;
    private static final Material VERMELHO = Material.RED_NETHER_BRICKS;
    private static final Material VIDRO = Material.RED_STAINED_GLASS_PANE;

    private enum TipoPainel { JANELA, PORTA_GRANDE, ENTRADA }

    /** Os quatro lados, com a direção "pra dentro da parede" e "pra direita" de quem olha de dentro. */
    private enum Lado {
        NORTE(BlockFace.NORTH, BlockFace.EAST, COMPRIMENTO_X),
        SUL(BlockFace.SOUTH, BlockFace.WEST, COMPRIMENTO_X),
        OESTE(BlockFace.WEST, BlockFace.NORTH, COMPRIMENTO_Z),
        LESTE(BlockFace.EAST, BlockFace.SOUTH, COMPRIMENTO_Z);

        final BlockFace parede;
        final BlockFace direita;
        final int comprimento;

        Lado(BlockFace parede, BlockFace direita, int comprimento) {
            this.parede = parede;
            this.direita = direita;
            this.comprimento = comprimento;
        }

        int x(int u, int w) {
            return switch (this) {
                case NORTE -> X0 + u;
                case SUL -> X0 + COMPRIMENTO_X - 1 - u;
                case OESTE -> X0 - 1 - w;
                case LESTE -> X0 + COMPRIMENTO_X + w;
            };
        }

        int z(int u, int w) {
            return switch (this) {
                case NORTE -> Z0 - 1 - w;
                case SUL -> Z0 + COMPRIMENTO_Z + w;
                case OESTE -> Z0 + COMPRIMENTO_Z - 1 - u;
                case LESTE -> Z0 + u;
            };
        }

        BlockFace esquerda() {
            return direita.getOppositeFace();
        }

        BlockFace arena() {
            return parede.getOppositeFace();
        }
    }

    @Override
    public String getId() {
        return "axepvp";
    }

    @Override
    public String getNome() {
        return "Arena AxePvP";
    }

    @Override
    public void gerar(Planta p) {
        int borda = ESPESSURA + 1;
        int minX = X0 - borda, maxX = X0 + COMPRIMENTO_X - 1 + borda;
        int minZ = Z0 - borda, maxZ = Z0 + COMPRIMENTO_Z - 1 + borda;

        // Limpa o volume todo e assenta a fundação.
        p.preencher(minX, 1, minZ, maxX, ALTURA + FOLGA_ACIMA, maxZ, Material.AIR);
        p.preencher(minX, -1, minZ, maxX, -1, maxZ, Material.STONE);

        // Piso: areia com uma barra de pedra rente às paredes.
        p.preencher(X0, 0, Z0, X0 + COMPRIMENTO_X - 1, 0, Z0 + COMPRIMENTO_Z - 1, Material.SAND);
        p.preencher(X0, 0, Z0, X0 + COMPRIMENTO_X - 1, 0, Z0 + 1, PEDRA);
        p.preencher(X0, 0, Z0 + COMPRIMENTO_Z - 2, X0 + COMPRIMENTO_X - 1, 0, Z0 + COMPRIMENTO_Z - 1, PEDRA);
        p.preencher(X0, 0, Z0, X0 + 1, 0, Z0 + COMPRIMENTO_Z - 1, PEDRA);
        p.preencher(X0 + COMPRIMENTO_X - 2, 0, Z0, X0 + COMPRIMENTO_X - 1, 0, Z0 + COMPRIMENTO_Z - 1, PEDRA);

        for (Lado lado : Lado.values()) {
            gerarMacico(p, lado);
        }
        for (Lado lado : Lado.values()) {
            gerarFachada(p, lado);
        }

        gerarTetoDeVidro(p);
        for (Lado lado : Lado.values()) {
            gerarGaleria(p, lado);
        }

        gerarLuzesDoPiso(p);
        gerarObstaculos(p);
    }

    /**
     * Vidro cobrindo o pátio na altura do topo das paredes (ninguém sai do X1 por cima, nem de
     * pérola), com vigas de pedra ligando os pilares das paredes compridas.
     */
    private void gerarTetoDeVidro(Planta p) {
        for (int fx = 1; fx < COMPRIMENTO_X - 1; fx++) {
            boolean viga = fx % MODULO == 1; // centro de cada pilar das paredes norte e sul
            for (int fz = 1; fz < COMPRIMENTO_Z - 1; fz++) {
                p.set(X0 + fx, ALTURA, Z0 + fz, viga ? Material.POLISHED_ANDESITE : Material.GLASS);
            }
        }
    }

    /**
     * Galeria dos espectadores no topo: parapeito fechado na borda de fora (base de pedra, grade e
     * lanternas em cima dos pilares), bancos virados pro vidro, estandartes e vasos.
     */
    private void gerarGaleria(Planta p, Lado lado) {
        boolean cobreCantos = lado == Lado.NORTE || lado == Lado.SUL;
        int uMin = cobreCantos ? -(ESPESSURA + 1) : 0;
        int uMax = cobreCantos ? lado.comprimento + ESPESSURA : lado.comprimento - 1;
        int base = ALTURA + 1, topo = ALTURA + ALTURA_PARAPEITO;

        // Parapeito na face externa; norte e sul também fecham as pontas dos cantos.
        for (int u = uMin; u <= uMax; u++) {
            boolean pilarDoParapeito = Math.floorMod(u, 4) == 1 || u == uMin || u == uMax;
            parapeito(p, lado, u, ESPESSURA, pilarDoParapeito, base, topo);
        }
        if (cobreCantos) {
            for (int w = 0; w < ESPESSURA; w++) {
                boolean pilarDoParapeito = w % 4 == 0;
                parapeitoNaPonta(p, lado, uMin, w, pilarDoParapeito, base, topo);
                parapeitoNaPonta(p, lado, uMax, w, pilarDoParapeito, base, topo);
            }
        }

        // Bancos (duas fileiras) virados pro vidro, nos vãos entre os pilares das paredes.
        for (int u = 0; u < lado.comprimento; u++) {
            int posNoModulo = u % MODULO;
            if (posNoModulo < PILAR) continue;
            for (int w : new int[]{2, 4}) {
                set(p, lado, u, base, w, escada(Material.SPRUCE_STAIRS, lado.parede, false));
            }
        }

        // Estandartes no parapeito, virados pra dentro, e vasos nos pilares das paredes.
        Material[] cores = {Material.RED_WALL_BANNER, Material.YELLOW_WALL_BANNER};
        for (int j = 0; j * MODULO + 1 < lado.comprimento; j++) {
            int u = j * MODULO + 1;
            set(p, lado, u, topo - 1, ESPESSURA - 1, Planta.direcional(cores[j % 2], lado.arena()));
            set(p, lado, u, base, ESPESSURA - 1, Material.POTTED_AZALEA_BUSH);
        }
    }

    /** Uma coluna do parapeito na face externa: pilar de pedra, ou base + grade + capa. */
    private void parapeito(Planta p, Lado lado, int u, int w, boolean pilar, int base, int topo) {
        if (pilar) {
            coluna(p, lado, u, base, topo, w, PEDRA);
            set(p, lado, u, topo + 1, w, Planta.lanterna(Material.LANTERN, false));
            return;
        }
        set(p, lado, u, base, w, PEDRA);
        for (int y = base + 1; y < topo; y++) {
            set(p, lado, u, y, w, conectado(Material.IRON_BARS, lado.direita, lado.esquerda()));
        }
        set(p, lado, u, topo, w, laje(LAJE, false));
    }

    /** O mesmo parapeito, correndo ao longo de w (as pontas dos cantos, nas paredes norte e sul). */
    private void parapeitoNaPonta(Planta p, Lado lado, int u, int w, boolean pilar, int base, int topo) {
        if (pilar) {
            coluna(p, lado, u, base, topo, w, PEDRA);
            set(p, lado, u, topo + 1, w, Planta.lanterna(Material.LANTERN, false));
            return;
        }
        set(p, lado, u, base, w, PEDRA);
        for (int y = base + 1; y < topo; y++) {
            set(p, lado, u, y, w, conectado(Material.IRON_BARS, lado.parede, lado.arena()));
        }
        set(p, lado, u, topo, w, laje(LAJE, false));
    }

    // ------------------------------------------------------------------ paredes

    private void set(Planta p, Lado lado, int u, int y, int w, BlockData dados) {
        p.set(lado.x(u, w), y, lado.z(u, w), dados);
    }

    private void set(Planta p, Lado lado, int u, int y, int w, Material material) {
        set(p, lado, u, y, w, material.createBlockData());
    }

    /** Fileira horizontal de u1 até u2 (inclusivo). */
    private void linha(Planta p, Lado lado, int u1, int u2, int y, int w, BlockData dados) {
        for (int u = u1; u <= u2; u++) set(p, lado, u, y, w, dados);
    }

    private void linha(Planta p, Lado lado, int u1, int u2, int y, int w, Material material) {
        linha(p, lado, u1, u2, y, w, material.createBlockData());
    }

    private void coluna(Planta p, Lado lado, int u, int y1, int y2, int w, Material material) {
        for (int y = y1; y <= y2; y++) set(p, lado, u, y, w, material);
    }

    /**
     * Maciço da parede (w de 0 até ESPESSURA) com o topo transitável. Norte e sul se estendem
     * pelos cantos, então oeste/leste só cobrem o próprio comprimento.
     */
    private void gerarMacico(Planta p, Lado lado) {
        boolean cobreCantos = lado == Lado.NORTE || lado == Lado.SUL;
        int uMin = cobreCantos ? -(ESPESSURA + 1) : 0;
        int uMax = cobreCantos ? lado.comprimento + ESPESSURA : lado.comprimento - 1;

        for (int u = uMin; u <= uMax; u++) {
            boolean ponta = cobreCantos && (u == uMin || u == uMax);
            boolean canto = u < 0 || u >= lado.comprimento;
            for (int w = 0; w <= ESPESSURA; w++) {
                boolean faceExterna = w == ESPESSURA || ponta;
                for (int y = 0; y < ALTURA; y++) {
                    Material material = faceExterna && y > 0 && y < ALTURA - 1 ? Material.BRICKS : PEDRA;
                    set(p, lado, u, y, w, material);
                }
                set(p, lado, u, ALTURA, w, materialDoTopo(u, w, canto, faceExterna));
            }
        }
    }

    /** Topo: faixa clara na beirada de dentro, ladrilhos nos pilares, tijolo vermelho com passarelas. */
    private Material materialDoTopo(int u, int w, boolean canto, boolean faceExterna) {
        if (faceExterna) return PEDRA;
        if (canto) return Material.SMOOTH_STONE;
        int posNoModulo = u % MODULO;
        boolean centroDoPilar = posNoModulo == 1;
        if (w <= 1) return centroDoPilar && w == 1 ? Material.CHISELED_STONE_BRICKS : Material.SMOOTH_STONE;
        if (centroDoPilar && (u / MODULO) % 2 == 0) return Material.SMOOTH_STONE;
        return Material.BRICKS;
    }

    private void gerarFachada(Planta p, Lado lado) {
        int paineis = (lado.comprimento - PILAR) / MODULO;

        // Fundo amarelado dos painéis e rodapé de escada ao longo de toda a parede.
        for (int u = 0; u < lado.comprimento; u++) {
            coluna(p, lado, u, 1, ALTURA - 2, 0, FUNDO);
            set(p, lado, u, 1, -1, escada(ESCADA, lado.parede, false));
        }

        for (int i = 0; i < paineis; i++) {
            int inicio = PILAR + i * MODULO;
            boolean centro = i == paineis / 2;
            TipoPainel tipo = !centro ? TipoPainel.JANELA
                    : lado.comprimento == COMPRIMENTO_X ? TipoPainel.PORTA_GRANDE : TipoPainel.ENTRADA;
            switch (tipo) {
                case JANELA -> painelJanela(p, lado, inicio);
                case PORTA_GRANDE -> painelPortaGrande(p, lado, inicio);
                case ENTRADA -> painelEntrada(p, lado, inicio);
            }
        }

        for (int j = 0; j <= paineis; j++) {
            pilar(p, lado, j * MODULO);
        }

        // Cornija e beirada do topo, onde o vidro encosta (sem ameias: a galeria vai até o vidro).
        for (int u = 0; u < lado.comprimento; u++) {
            set(p, lado, u, ALTURA - 1, -1, escada(ESCADA, lado.parede, true));
            set(p, lado, u, ALTURA, -1, PEDRA);
        }
    }

    /** Pilar de 3 de largura com nervura central saliente, base e capitel. */
    private void pilar(Planta p, Lado lado, int u) {
        for (int du = 0; du < PILAR; du++) {
            coluna(p, lado, u + du, 1, ALTURA - 2, -1, PEDRA);
            set(p, lado, u + du, 1, -2, escada(ESCADA, lado.parede, false));
            set(p, lado, u + du, ALTURA - 3, -2, escada(ESCADA, lado.parede, true));
        }
        coluna(p, lado, u + 1, 2, ALTURA - 4, -2, PEDRA);
        // Lanternas do mar na nervura: iluminam a beira da arena (e lembram as luzes ciano do original).
        set(p, lado, u + 1, 5, -2, Material.SEA_LANTERN);
        set(p, lado, u + 1, 9, -2, Material.SEA_LANTERN);
    }

    /** Janela alta com sacada gradeada embaixo e medalhão em cima. */
    private void painelJanela(Planta p, Lado lado, int u) {
        // Sacada: mísula, piso de laje e guarda-corpo de grade.
        linha(p, lado, u, u + 4, 2, -1, escada(ESCADA, lado.parede, true));
        linha(p, lado, u, u + 4, 3, -1, laje(LAJE, false));
        linha(p, lado, u + 1, u + 3, 2, -2, escada(ESCADA, lado.parede, true));
        linha(p, lado, u + 1, u + 3, 3, -2, laje(LAJE, false));
        linha(p, lado, u + 1, u + 3, 4, -2, conectado(Material.IRON_BARS, lado.direita, lado.esquerda()));

        // Moldura e vidro (vidro vermelho na frente de tijolo do nether dá o tom vinho do original).
        coluna(p, lado, u, 4, 9, -1, PEDRA);
        coluna(p, lado, u + 4, 4, 9, -1, PEDRA);
        linha(p, lado, u, u + 4, 9, -1, PEDRA);
        for (int y = 4; y <= 8; y++) {
            linha(p, lado, u + 1, u + 3, y, 0, conectado(VIDRO, lado.direita, lado.esquerda()));
            linha(p, lado, u + 1, u + 3, y, 1, VERMELHO);
        }

        medalhao(p, lado, u);
    }

    /** Enfeite logo acima da janela: moldura de pedra com um quadrado vinho rebaixado no meio. */
    private void medalhao(Planta p, Lado lado, int u) {
        linha(p, lado, u + 1, u + 3, 10, -1, escada(ESCADA, lado.parede, true));
        for (int y = 11; y <= 12; y++) linha(p, lado, u + 1, u + 3, y, -1, PEDRA);
        set(p, lado, u + 2, 11, -1, Material.AIR);
        set(p, lado, u + 2, 11, 0, VERMELHO);
        set(p, lado, u, 11, -1, escada(ESCADA, lado.direita, false));
        set(p, lado, u + 4, 11, -1, escada(ESCADA, lado.esquerda(), false));
        linha(p, lado, u + 1, u + 3, 13, -1, laje(LAJE, false));
    }

    /** Óculo redondo (5 de diâmetro) logo acima do portão/entrada, com a moldura de baixo na altura 6. */
    private void oculo(Planta p, Lado lado, int u) {
        int[][] fileiras = {{7, 1, 3}, {8, 0, 4}, {9, 0, 4}, {10, 0, 4}, {11, 1, 3}};
        for (int[] f : fileiras) {
            for (int du = f[1]; du <= f[2]; du++) {
                set(p, lado, u + du, f[0], 0, conectado(VIDRO, lado.direita, lado.esquerda()));
                set(p, lado, u + du, f[0], 1, VERMELHO);
            }
        }
        linha(p, lado, u, u + 4, 6, -1, PEDRA);
        linha(p, lado, u, u + 4, 12, -1, PEDRA);
        for (int y : new int[]{7, 11}) {
            set(p, lado, u, y, -1, PEDRA);
            set(p, lado, u + 4, y, -1, PEDRA);
        }
        linha(p, lado, u + 1, u + 3, 13, -1, laje(LAJE, false));
    }

    /** Portão vinho decorativo (fechado) com óculo em cima — o centro das paredes compridas. */
    private void painelPortaGrande(Planta p, Lado lado, int u) {
        coluna(p, lado, u, 1, 6, -1, PEDRA);
        coluna(p, lado, u + 4, 1, 6, -1, PEDRA);
        for (int y = 1; y <= 5; y++) linha(p, lado, u + 1, u + 3, y, -1, Material.AIR);
        for (int y = 1; y <= 5; y++) linha(p, lado, u + 1, u + 3, y, 0, Material.NETHER_WART_BLOCK);
        set(p, lado, u + 1, 5, 0, escada(ESCADA, lado.esquerda(), true));
        set(p, lado, u + 3, 5, 0, escada(ESCADA, lado.direita, true));
        oculo(p, lado, u);
    }

    /**
     * Nicho do lutador: entra 3 blocos na parede (3x4) e é fechado no fundo — é onde o duelo põe
     * cada um, e não tem por onde sair a não ser pro pátio.
     */
    private void painelEntrada(Planta p, Lado lado, int u) {
        for (int w = -1; w <= FUNDO_NICHO; w++) {
            for (int y = 1; y <= 4; y++) linha(p, lado, u + 1, u + 3, y, w, Material.AIR);
            linha(p, lado, u + 1, u + 3, 0, w, PEDRA);
        }
        set(p, lado, u + 1, 4, 0, escada(ESCADA, lado.esquerda(), true));
        set(p, lado, u + 3, 4, 0, escada(ESCADA, lado.direita, true));
        coluna(p, lado, u, 1, 5, 0, PEDRA);
        coluna(p, lado, u + 4, 1, 5, 0, PEDRA);
        linha(p, lado, u, u + 4, 5, 0, PEDRA);
        set(p, lado, u + 2, 4, 1, Planta.lanterna(Material.LANTERN, true));
        // fundo do nicho: o mesmo vinho das portas, pra marcar o lugar de cada lutador
        for (int y = 1; y <= 4; y++) linha(p, lado, u + 1, u + 3, y, FUNDO_NICHO + 1, Material.NETHER_WART_BLOCK);

        coluna(p, lado, u, 1, 5, -1, PEDRA);
        coluna(p, lado, u + 4, 1, 5, -1, PEDRA);
        linha(p, lado, u, u + 4, 5, -1, PEDRA);
        oculo(p, lado, u);
    }

    // ------------------------------------------------------------------ piso

    /** Grade de lanternas do mar rente ao chão — sem isso o meio da arena fica escuro e nasce mob. */
    private void gerarLuzesDoPiso(Planta p) {
        for (int fx = 6; fx < COMPRIMENTO_X - 3; fx += 10) {
            for (int fz = 7; fz < COMPRIMENTO_Z - 3; fz += 12) {
                p.set(X0 + fx, 0, Z0 + fz, Material.SEA_LANTERN);
            }
        }
        p.set(0, 0, 0, Material.SEA_LANTERN);
    }

    /** Coberturas espalhadas como no original: fardos, cactos, carroças, pedras e arbustos secos. */
    private void gerarObstaculos(Planta p) {
        fardos(p, 8, 5);
        fardos(p, 31, 19);

        cacto(p, 6, 18, 3);
        cacto(p, 16, 5, 2);
        cacto(p, 27, 5, 2);
        cacto(p, 37, 14, 3);
        cacto(p, 14, 21, 2);

        carroca(p, 29, 10, true);
        carroca(p, 11, 14, false);

        pedras(p, 18, 19);
        pedras(p, 34, 5);

        // Arbustos secos sorteados com semente fixa: a arena sai igual toda vez.
        Random random = new Random(42);
        int centroX = COMPRIMENTO_X / 2, centroZ = COMPRIMENTO_Z / 2;
        for (int i = 0; i < 25; i++) {
            int fx = 4 + random.nextInt(COMPRIMENTO_X - 8);
            int fz = 4 + random.nextInt(COMPRIMENTO_Z - 8);
            if (Math.abs(fx - centroX) < 4 && Math.abs(fz - centroZ) < 4) continue;
            int x = X0 + fx, z = Z0 + fz;
            if (p.get(x, 0, z) == Material.SAND && p.get(x, 1, z) == Material.AIR) {
                p.set(x, 1, z, Material.DEAD_BUSH);
            }
        }
    }

    /** Monte de fardo em degraus: 5 na base, 3 no meio e 1 no topo. */
    private void fardos(Planta p, int fx, int fz) {
        int[][] posicoes = {
                {0, 1, 0}, {1, 1, 0}, {2, 1, 0}, {0, 1, 1}, {1, 1, 1},
                {0, 2, 0}, {1, 2, 0}, {0, 2, 1},
                {0, 3, 0}
        };
        for (int i = 0; i < posicoes.length; i++) {
            int[] pos = posicoes[i];
            Axis eixo = i % 2 == 0 ? Axis.X : Axis.Z;
            p.set(X0 + fx + pos[0], pos[1], Z0 + fz + pos[2], eixo(Material.HAY_BLOCK, eixo));
        }
    }

    private void cacto(Planta p, int fx, int fz, int altura) {
        for (int y = 1; y <= altura; y++) p.set(X0 + fx, y, Z0 + fz, Material.CACTUS);
    }

    /** Carroça 2x2 de madeira com dois varais (end rod) saindo pela frente. */
    private void carroca(Planta p, int fx, int fz, boolean aoLongoDeX) {
        int x = X0 + fx, z = Z0 + fz;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                int bx = x + (aoLongoDeX ? a : b), bz = z + (aoLongoDeX ? b : a);
                p.set(bx, 1, bz, Material.MANGROVE_PLANKS);
                p.set(bx, 2, bz, laje(Material.JUNGLE_SLAB, false));
            }
        }
        BlockFace frente = aoLongoDeX ? BlockFace.EAST : BlockFace.SOUTH;
        for (int b = 0; b < 2; b++) {
            int vx = aoLongoDeX ? x + 2 : x + b;
            int vz = aoLongoDeX ? z + b : z + 2;
            p.set(vx, 1, vz, direcional(Material.END_ROD, frente));
        }
    }

    private void pedras(Planta p, int fx, int fz) {
        int x = X0 + fx, z = Z0 + fz;
        p.set(x, 1, z, Material.COBBLESTONE);
        p.set(x, 2, z, Material.COBBLESTONE);
        p.set(x + 1, 1, z, Material.ANDESITE);
        p.set(x, 1, z + 1, Material.MOSSY_COBBLESTONE);
        p.set(x + 1, 1, z + 1, laje(Material.COBBLESTONE_SLAB, false));
    }
}
