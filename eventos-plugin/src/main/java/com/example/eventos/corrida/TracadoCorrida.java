package com.example.eventos.corrida;

import java.util.ArrayList;
import java.util.List;

/**
 * O traçado de um mapa da Corrida Maluca: os obstáculos em ordem, de sul a norte, com a posição (dz, a
 * partir da largada) que cada um ocupa. É montado em sequência: cada obstáculo começa onde o anterior
 * acabou ({@link #listras(int)} só deixa piso listrado entre um e outro), e leva a fase (0, 1 ou 2) que
 * escolhe a força dele nas listas do config.
 * <p>
 * Começa com a largada (0 a 7) e o portão de vidro (8); termina com {@link #chegada()}: a linha quadriculada,
 * um vidro e a arquibancada de quem já chegou, de frente pra linha.
 */
public final class TracadoCorrida {

    /** A largada (0 a 7) e o portão (8). */
    static final int PORTAO = 8;
    /** O vão que o trampolim pula. */
    static final int VAO_DO_TRAMPOLIM = 5;
    /** Da linha de chegada ao vidro da arquibancada, e da linha ao fim do pavilhão. */
    static final int ATE_O_VIDRO = 2, ATE_O_FIM = 10;

    /** Uma plataforma 3 x 3 que some: a fase e o grupo (os dois se alternam, em xadrez). */
    record Plataforma(int fase, int grupo, int x0, int dz0) {
    }

    /** Um tapete rolante, de dz0 a dz1, de -meia a meia. */
    record Tapete(int fase, int dz0, int dz1, int meia) {
    }

    /** Um martelo de pêndulo atravessando a pista em dz; a fase (radianos) desencontra os vizinhos. */
    record Martelo(int fase, int dz, double desencontro) {
    }

    /** Um bloco do pula-pula (de -2 a 2, de dz0 a dz1): quem pisa é jogado pro meio de "alvo" (o próximo, ou o chão). */
    record Quica(int dz0, int dz1, int alvo) {
    }

    /** Um bloco 2 x 2 do parkour, com o topo "altura" acima do piso. */
    record Bloco(int x0, int dz0, int altura) {
    }

    /** Uma ponte estreita sobre a água, de dz0 a dz1, de -meia a meia. */
    record Ponte(int dz0, int dz1, int meia) {
    }

    /** Um trecho de gelo, com os buracos 2 x 2 (dz, x do canto). */
    record Gelo(int dz0, int dz1, List<int[]> buracos) {
    }

    /** Um disco com barras (o centro); o segundo de um par gira pro outro lado. */
    record Pivo(int fase, int dz, boolean inverso) {
    }

    /** O centro de um carrossel. */
    record Carrossel(int fase, int dz) {
    }

    private static final int[] COLUNAS_PLATAFORMAS = {-5, -1, 3};
    /** Os cantos dos buracos do gelo, em rodízio (2 de largura cada, com piso entre eles). */
    private static final int[] BURACOS_X = {-5, 1, -2, 4, -6, 0, 3, -4};
    /** O zigue-zague do parkour (x do canto) e as alturas. */
    private static final int[] PARKOUR_X = {-1, 2, -1, -4};
    private static final int[] PARKOUR_ALTURA = {0, 1, 1, 2, 2, 1, 0, 1, 1, 0};

    final List<Integer> checkpoints = new ArrayList<>(List.of(0));
    final List<Integer> fileiras = new ArrayList<>();
    final List<Integer> trampolins = new ArrayList<>();
    final List<Gelo> gelos = new ArrayList<>();
    final List<Plataforma> plataformas = new ArrayList<>();
    final List<Tapete> tapetes = new ArrayList<>();
    final List<Quica> quicas = new ArrayList<>();
    /** Os trechos de água sem piso (dz0, dz1): o vão do trampolim, o pula-pula e o parkour. */
    final List<int[]> vaos = new ArrayList<>();
    final List<Pivo> pivos = new ArrayList<>();
    final List<Ponte> pontes = new ArrayList<>();
    final List<Martelo> martelos = new ArrayList<>();
    final List<Bloco> blocos = new ArrayList<>();
    final List<Carrossel> carrosseis = new ArrayList<>();
    final List<Integer> canhoes = new ArrayList<>();
    int chegada = -1, fim;

    private int z = PORTAO + 1;
    private int fase;
    private int gelosFeitos;

    /** Os obstáculos de agora em diante são dessa fase (0, 1 ou 2). */
    TracadoCorrida fase(int fase) {
        this.fase = fase;
        return this;
    }

    /** Piso listrado. */
    TracadoCorrida listras(int n) {
        z += n;
        return this;
    }

    /** Um checkpoint (4 de comprimento, com a faixa de ouro no começo). */
    TracadoCorrida checkpoint() {
        checkpoints.add(z);
        z += 4;
        return this;
    }

    /** Uma fileira de 3 portas (só as sorteadas abrem). */
    TracadoCorrida portas() {
        fileiras.add(z);
        z += 1;
        return this;
    }

    /** O slime que joga por cima de um vão de 5 (mirando 2 blocos chão adentro, depois dele). */
    TracadoCorrida trampolim() {
        trampolins.add(z);
        vaos.add(new int[]{z + 1, z + VAO_DO_TRAMPOLIM});
        z += VAO_DO_TRAMPOLIM + 1;
        return this;
    }

    /** Gelo com buracos 2 x 2: uma fileira de buracos a cada 3, 2 por fileira (3 a partir da fase 2). */
    TracadoCorrida gelo(int n) {
        List<int[]> buracos = new ArrayList<>();
        int porFileira = fase >= 1 ? 3 : 2, k = gelosFeitos++ * 3;
        for (int dz = z + 2; dz + 1 < z + n; dz += 3) {
            for (int i = 0; i < porFileira; i++) buracos.add(new int[]{dz, BURACOS_X[k++ % BURACOS_X.length]});
        }
        gelos.add(new Gelo(z, z + n - 1, buracos));
        z += n;
        return this;
    }

    /** Linhas de 3 plataformas 3 x 3 (com 2 de água entre as linhas), em xadrez. */
    TracadoCorrida plataformas(int linhas) {
        for (int l = 0; l < linhas; l++) {
            for (int c = 0; c < COLUNAS_PLATAFORMAS.length; c++) {
                plataformas.add(new Plataforma(fase, (l + c) % 2, COLUNAS_PLATAFORMAS[c], z + 5 * l));
            }
        }
        z += 5 * linhas - 2;
        return this;
    }

    /** Um tapete rolante, estreito, sobre a água. */
    TracadoCorrida tapete(int n, int meia) {
        tapetes.add(new Tapete(fase, z, z + n - 1, meia));
        z += n;
        return this;
    }

    /**
     * O pula-pula: blocos de slime de 4, um a cada 6, cada um mirando no meio do próximo, e o vão de água até
     * o chão depois do último (que começa 5 depois dele; o último mira 2 blocos chão adentro).
     */
    TracadoCorrida pulaPula(int n) {
        int chao = z + 6 * (n - 1) + 5;
        for (int i = 0; i < n; i++) {
            int dz = z + 6 * i;
            quicas.add(new Quica(dz, dz + 3, i + 1 < n ? dz + 6 + 2 : chao + 2));
        }
        vaos.add(new int[]{z, chao - 1});
        z = chao;
        return this;
    }

    /** Um disco com barras giratórias (raio 6, 13 de comprimento). */
    TracadoCorrida disco() {
        pivos.add(new Pivo(fase, z + ArenaCorrida.RAIO, false));
        z += 2 * ArenaCorrida.RAIO + 1;
        return this;
    }

    /** Dois discos encostados, com uma pontinha entre eles; o segundo gira pro outro lado. */
    TracadoCorrida discoDuplo() {
        pivos.add(new Pivo(fase, z + ArenaCorrida.RAIO, false));
        pivos.add(new Pivo(fase, z + 3 * ArenaCorrida.RAIO + 2, true));
        z += 4 * ArenaCorrida.RAIO + 3;
        return this;
    }

    /** Uma ponte estreita (3) com os martelos atravessando, um a cada 6. */
    TracadoCorrida martelos(int n) {
        pontes.add(new Ponte(z, z + 6 * n + 2, 1));
        for (int i = 0; i < n; i++) martelos.add(new Martelo(fase, z + 4 + 6 * i, i * 2 * Math.PI / n));
        z += 6 * n + 3;
        return this;
    }

    /** O carrossel, com uma pontinha de 3 antes e outra depois. */
    TracadoCorrida carrossel() {
        pontes.add(new Ponte(z, z + 2, 1));
        carrosseis.add(new Carrossel(fase, z + 3 + ArenaCorrida.RAIO));
        pontes.add(new Ponte(z + 4 + 2 * ArenaCorrida.RAIO, z + 6 + 2 * ArenaCorrida.RAIO, 1));
        z += 7 + 2 * ArenaCorrida.RAIO;
        return this;
    }

    /** O parkour: blocos 2 x 2 soltos sobre a água, um a cada 3, em zigue-zague e em alturas diferentes. */
    TracadoCorrida parkour(int n) {
        vaos.add(new int[]{z, z + 3 * n - 1});
        for (int i = 0; i < n; i++) blocos.add(new Bloco(PARKOUR_X[i % PARKOUR_X.length], z + 1 + 3 * i, PARKOUR_ALTURA[i % PARKOUR_ALTURA.length]));
        z += 3 * n;
        return this;
    }

    /** A ponte estreita do vento, com um canhão a cada 3, alternando as paredes. */
    TracadoCorrida vento(int n) {
        pontes.add(new Ponte(z, z + 3 * n + 2, 1));
        for (int i = 0; i < n; i++) canhoes.add(z + 3 + 3 * i);
        z += 3 * n + 3;
        return this;
    }

    /** A chegada: a linha, o vidro e a arquibancada de quem chegou. Fecha o traçado. */
    TracadoCorrida chegada() {
        chegada = z;
        fim = z + ATE_O_FIM;
        return this;
    }
}
