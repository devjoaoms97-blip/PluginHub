package com.example.estruturas.estrutura;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.Random;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Coliseu oval de arenito pensado pro evento Gladiador. O piso de luta (elipse de 153 x 129) fica
 * num "fosso" fechado: parede de 12 blocos em volta e um teto de VIDRO com nervuras de arenito na
 * altura do topo da parede, então quem luta não sai por cima (nem de pérola) e não alcança a plateia.
 *
 * Os espectadores ficam em cima: o vidro e o topo da parede formam um deck por onde dá pra andar
 * olhando a luta lá embaixo; em volta sobem as fileiras da arquibancada, a galeria do topo com
 * toldos e estandartes, e a muralha externa alta com três ordens de arcos (as janelas da galeria
 * são de vidro, ninguém cai pra fora). Não há saída nenhuma: o público chega por warp (no deck)
 * e sai com /spawn, e não há passagem pro piso de luta.
 *
 * Tudo é feito em "anéis": o anel k de um bloco é o menor k tal que ele cabe na elipse de raios
 * (RAIO_X + k, RAIO_Z + k). k <= 0 é o piso; 1..3 a parede/deck; depois as fileiras (2 anéis cada),
 * a galeria e a muralha. Assim cada camada tem largura constante em volta da elipse inteira.
 */
public class ArenaColiseu implements Estrutura {

    // Piso de luta: elipse de 153 x 129.
    private static final int RAIO_X = 76;
    private static final int RAIO_Z = 64;

    private static final int PODIO = 12;                             // parede do piso = nível do vidro/deck
    private static final int FILEIRAS = 10;                          // degraus da arquibancada (2 anéis cada)
    private static final int ANEL_GALERIA = 4 + 2 * FILEIRAS;        // 24..26
    private static final int ANEL_MURALHA = ANEL_GALERIA + 3;        // 27..29
    private static final int ANEL_MAX = ANEL_MURALHA + 2;            // 29
    private static final int TOPO = PODIO + FILEIRAS + 1;            // 23: piso da galeria
    private static final int ALTURA_MURALHA = TOPO + 7;              // 30
    private static final int ALTURA_LIMPEZA = ALTURA_MURALHA + 1 + 5; // 36: ameias + 5 de folga

    private static final int[] NERVURAS = {-12, -24, -36, -48};     // anéis de nervura no vidro (além da borda k = 0)
    private static final int RAIOS_TETO = 32;                        // nervuras radiais do teto

    private static final int ARCOS = 96;          // arcos da fachada (múltiplo de 4: um em cada eixo)
    private static final int PAINEIS = 96;        // painéis na parede do piso
    private static final int CORREDORES = 24;     // corredores coloridos na arquibancada
    private static final int FORA = Integer.MAX_VALUE;

    private static final int LIMITE_X = RAIO_X + ANEL_MAX + 2;
    private static final int LIMITE_Z = RAIO_Z + ANEL_MAX + 2;

    private static final Material LISO = Material.SMOOTH_SANDSTONE;
    private static final Material CORTADO = Material.CUT_SANDSTONE;
    private static final Material ENTALHADO = Material.CHISELED_SANDSTONE;
    private static final Material MACICO = Material.SANDSTONE;
    private static final Material ESCADA = Material.SMOOTH_SANDSTONE_STAIRS;
    private static final Material LAJE = Material.SMOOTH_SANDSTONE_SLAB;
    private static final Material ESCADA_CORREDOR = Material.RED_SANDSTONE_STAIRS;
    private static final Material LUZ = Material.OCHRE_FROGLIGHT;
    private static final Material VIDRO = Material.GLASS;

    private static final Material[] ESTANDARTES = {
            Material.RED_WALL_BANNER, Material.YELLOW_WALL_BANNER, Material.PURPLE_WALL_BANNER, Material.ORANGE_WALL_BANNER
    };
    private static final Material[] VASOS = {
            Material.POTTED_RED_TULIP, Material.POTTED_CACTUS, Material.POTTED_ORANGE_TULIP, Material.POTTED_DEAD_BUSH
    };

    private int[][] aneis;

    @Override
    public String getId() {
        return "coliseu";
    }

    @Override
    public String getNome() {
        return "Coliseu";
    }

    @Override
    public void gerar(Planta p) {
        calcularAneis();

        for (int x = -LIMITE_X; x <= LIMITE_X; x++) {
            for (int z = -LIMITE_Z; z <= LIMITE_Z; z++) {
                int k = anel(x, z);
                if (k > ANEL_MAX + 1) continue;
                p.set(x, -1, z, Material.STONE);
                if (k <= 0) piso(p, x, z, k);
                else if (k <= 3) podio(p, x, z, k);
                else if (k < ANEL_GALERIA) fileira(p, x, z, k);
                else if (k < ANEL_MURALHA) galeria(p, x, z, k);
                else if (k <= ANEL_MAX) muralha(p, x, z, k);
                else cornijas(p, x, z);
            }
        }

        gerarLuzes(p);
        gerarDecoracao(p);
        gerarPortoes(p);
        gerarObstaculos(p);
        gerarLuzesDoPiso(p);

        // Limpeza por último e só onde nada foi gravado: cada posição entra uma vez só na planta.
        for (int x = -LIMITE_X; x <= LIMITE_X; x++) {
            for (int z = -LIMITE_Z; z <= LIMITE_Z; z++) {
                if (anel(x, z) > ANEL_MAX + 1) continue;
                for (int y = 1; y <= ALTURA_LIMPEZA; y++) {
                    if (p.get(x, y, z) == null) p.set(x, y, z, Material.AIR);
                }
            }
        }
    }

    // ------------------------------------------------------------------ geometria

    private void calcularAneis() {
        aneis = new int[2 * LIMITE_X + 1][2 * LIMITE_Z + 1];
        for (int x = -LIMITE_X; x <= LIMITE_X; x++) {
            for (int z = -LIMITE_Z; z <= LIMITE_Z; z++) {
                int resultado = FORA;
                for (int k = -(RAIO_Z - 1); k <= ANEL_MAX + 2; k++) {
                    double a = RAIO_X + k, b = RAIO_Z + k;
                    if ((x * x) / (a * a) + (z * z) / (b * b) <= 1.0) {
                        resultado = k;
                        break;
                    }
                }
                aneis[x + LIMITE_X][z + LIMITE_Z] = resultado;
            }
        }
    }

    private int anel(int x, int z) {
        if (Math.abs(x) > LIMITE_X || Math.abs(z) > LIMITE_Z) return FORA;
        return aneis[x + LIMITE_X][z + LIMITE_Z];
    }

    /** Ângulo (0..1 da volta) do bloco na elipse do anel k. */
    private double volta(int x, int z, int k) {
        double phi = Math.atan2(z / (double) (RAIO_Z + k), x / (double) (RAIO_X + k));
        double v = phi / (2 * Math.PI);
        return v - Math.floor(v);
    }

    /** Posição (0..1) do bloco dentro da "célula" de uma divisão em n partes da volta; 0.5 é o meio. */
    private double fracao(int x, int z, int k, int n, double deslocamento) {
        double f = volta(x, z, k) * n + deslocamento;
        return f - Math.floor(f);
    }

    /** Distância do bloco ao meio da sua célula, de 0 (meio) até 0.5 (divisa). */
    private double desvio(int x, int z, int k, int n) {
        return Math.abs(fracao(x, z, k, n, 0.5) - 0.5);
    }

    /** Comprimento aproximado da volta do anel k. */
    private double perimetro(int k) {
        double a = RAIO_X + k, b = RAIO_Z + k;
        return 2 * Math.PI * Math.sqrt((a * a + b * b) / 2);
    }

    /** Bloco no meio do anel k, na fração {@code v} da volta; null se o arredondamento cair fora do anel. */
    private int[] ponto(double v, int k) {
        double phi = 2 * Math.PI * v;
        int x = (int) Math.round((RAIO_X + k - 0.5) * Math.cos(phi));
        int z = (int) Math.round((RAIO_Z + k - 0.5) * Math.sin(phi));
        return anel(x, z) == k ? new int[]{x, z} : null;
    }

    private int[] centro(int n, int i, int k) {
        return ponto(i / (double) n, k);
    }

    /** Ponto do piso na fração f dos raios, no ângulo em graus. */
    private int[] noPiso(double f, double graus) {
        double phi = Math.toRadians(graus);
        return new int[]{(int) Math.round(RAIO_X * f * Math.cos(phi)), (int) Math.round(RAIO_Z * f * Math.sin(phi))};
    }

    /** Direção "pra fora" do coliseu (normal da elipse), arredondada pro eixo dominante. */
    private BlockFace paraFora(int x, int z) {
        double gx = x / (double) (RAIO_X * RAIO_X), gz = z / (double) (RAIO_Z * RAIO_Z);
        if (Math.abs(gx) >= Math.abs(gz)) return x >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return z >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static boolean livre(Material m) {
        return m == null || m == Material.AIR;
    }

    private void coluna(Planta p, int x, int z, int topo, Material material) {
        for (int y = 0; y < topo; y++) p.set(x, y, z, MACICO);
        p.set(x, topo, z, material);
    }

    // ------------------------------------------------------------------ camadas

    /** Areia com barra de arenito rente à parede, quadrado da borda final no centro e o teto de vidro. */
    private void piso(Planta p, int x, int z, int k) {
        Material material = Material.SAND;
        if (k >= -1) material = LISO;
        else if (k == -2) material = CORTADO;
        int q = Math.max(Math.abs(x), Math.abs(z));
        if (q == 4) material = Material.CUT_RED_SANDSTONE;   // contorno da borda final de 8x8
        if (q <= 1) material = ENTALHADO;
        p.set(x, 0, z, material);
        teto(p, x, z, k);
    }

    /**
     * Teto de vidro em y = PODIO sobre todo o piso: borda de arenito, quatro anéis de nervura e
     * nervuras radiais; nos cruzamentos uma luz embutida (ilumina em cima e embaixo) com lanterna
     * pendurada por baixo. Vidro não deixa mob nascer; as nervuras ficam iluminadas pelos cruzamentos.
     */
    private void teto(Planta p, int x, int z, int k) {
        boolean anelNervura = k == 0;
        for (int n : NERVURAS) anelNervura |= k == n;
        boolean raio = k >= NERVURAS[NERVURAS.length - 1]
                && desvio(x, z, k, RAIOS_TETO) * perimetro(k) / RAIOS_TETO < 0.55;

        if (anelNervura && raio) {
            p.set(x, PODIO, z, LUZ);
            p.set(x, PODIO - 1, z, lanterna(Material.LANTERN, true));
        } else if (anelNervura || raio) {
            p.set(x, PODIO, z, k == 0 ? LISO : CORTADO);
        } else {
            p.set(x, PODIO, z, VIDRO);
        }
    }

    /** Parede do piso (12 blocos) com pilastras e painéis por dentro; o topo dela é o deck do público. */
    private void podio(Planta p, int x, int z, int k) {
        coluna(p, x, z, PODIO, k == 1 ? ENTALHADO : k == 2 ? LISO : CORTADO);
        if (k != 1) return;

        double d = desvio(x, z, 1, PAINEIS);
        boolean pilastra = d >= 0.40;
        boolean miolo = d < 0.26;
        p.set(x, 1, z, LISO);
        for (int y = 2; y <= PODIO - 2; y++) {
            boolean faixa = miolo && (y == 3 || y == 8);
            p.set(x, y, z, pilastra ? LISO : faixa ? Material.TERRACOTTA : CORTADO);
        }
        p.set(x, PODIO - 1, z, pilastra ? ENTALHADO : CORTADO);
    }

    /**
     * Fileira i da arquibancada, de 2 anéis: o de dentro é o piso onde se pisa (y = PODIO + i) e o de
     * fora tem o banco (degrau de escada) no nível do piso da fileira seguinte. Corredores em vermelho.
     */
    private void fileira(Planta p, int x, int z, int k) {
        int i = (k - 4) / 2 + 1;
        boolean interno = (k - 4) % 2 == 0;
        int h = PODIO + i;
        double celula = perimetro(k) / CORREDORES;
        boolean corredor = Math.abs(fracao(x, z, k, CORREDORES, 0) - 0.5) * celula <= 1.0;
        if (interno) {
            coluna(p, x, z, h, corredor ? Material.CUT_RED_SANDSTONE : LISO);
        } else {
            coluna(p, x, z, h, LISO);
            p.set(x, h + 1, z, escada(corredor ? ESCADA_CORREDOR : ESCADA, paraFora(x, z), false));
        }
    }

    /** Galeria do topo: passeio de 3 anéis rente à muralha, com borda de arenito cortado. */
    private void galeria(Planta p, int x, int z, int k) {
        coluna(p, x, z, TOPO, k == ANEL_GALERIA ? CORTADO : LISO);
    }

    /**
     * Muralha externa com três ordens de arcos: térreo e meio em nicho; a de cima são as janelas da
     * galeria, com vidro na face de fora (o público vê a paisagem mas não cai).
     */
    private void muralha(Planta p, int x, int z, int k) {
        coluna(p, x, z, ALTURA_MURALHA, LISO);
        double d = desvio(x, z, ANEL_MAX, ARCOS);
        boolean pilar = d >= 0.28;
        boolean externo = k == ANEL_MAX;

        for (int y = 1; y < ALTURA_MURALHA; y++) {
            boolean terreo = !pilar && y <= 6 || d < 0.15 && y == 7;
            boolean meio = !pilar && y >= 10 && y <= 15 || d < 0.15 && y == 16;
            boolean janela = !pilar && y > TOPO && y <= TOPO + 3 || d < 0.15 && y == TOPO + 4;
            if (externo) {
                if (terreo || meio) p.set(x, y, z, Material.AIR);
                else if (janela) p.set(x, y, z, VIDRO);
                else p.set(x, y, z, pilar ? LISO : CORTADO);
            } else if (janela) {
                p.set(x, y, z, Material.AIR);                                  // vão da janela, do lado da galeria
            } else if (k == ANEL_MAX - 1 && terreo) {
                p.set(x, y, z, Material.TERRACOTTA);                           // fundo dos nichos
            } else if (k == ANEL_MAX - 1 && meio) {
                p.set(x, y, z, Material.ORANGE_TERRACOTTA);
            } else if (y > TOPO) {
                p.set(x, y, z, CORTADO);                                       // face interna da galeria
            }
        }
        // Ameias sobre os pilares.
        if (pilar) p.set(x, ALTURA_MURALHA + 1, z, CORTADO);
    }

    /** Faixas salientes de laje entre as ordens de arcos e a cornija do topo. */
    private void cornijas(Planta p, int x, int z) {
        for (int y : new int[]{8, 17, ALTURA_MURALHA}) p.set(x, y, z, laje(LAJE, true));
    }

    // ------------------------------------------------------------------ luz

    private void gerarLuzes(Planta p) {
        // Luz no meio dos painéis da parede do piso, nas duas faixas.
        for (int i = 0; i < PAINEIS; i++) {
            int[] c = centro(PAINEIS, i, 1);
            if (c == null) continue;
            p.set(c[0], 3, c[1], LUZ);
            p.set(c[0], 8, c[1], LUZ);
        }
        // Deck (topo da parede): luzes embutidas no anel do meio.
        for (int i = 0; i < 64; i++) {
            int[] c = centro(64, i, 2);
            if (c != null) p.set(c[0], PODIO, c[1], LUZ);
        }
        // Piso de cada fileira, alternando a posição entre fileiras.
        for (int f = 1; f <= FILEIRAS; f++) {
            int k = 4 + 2 * (f - 1);
            for (int i = 0; i < 40; i++) {
                int[] c = ponto((i + (f % 2) * 0.5) / 40, k);
                if (c != null) p.set(c[0], PODIO + f, c[1], LUZ);
            }
        }
        // Galeria: na borda descoberta e embutida nos toldos.
        for (int i = 0; i < ARCOS; i++) {
            int[] c = ponto((i + 0.5) / ARCOS, ANEL_GALERIA);
            if (c != null) p.set(c[0], TOPO, c[1], LUZ);
        }
        // Fachada: lanterna pendurada no arco térreo, de pé no nicho do meio e no topo da muralha.
        for (int i = 0; i < ARCOS; i++) {
            int[] c = centro(ARCOS, i, ANEL_MAX);
            if (c == null) continue;
            if (livre(p.get(c[0], 6, c[1]))) p.set(c[0], 6, c[1], lanterna(Material.LANTERN, true));
            if (livre(p.get(c[0], 10, c[1]))) p.set(c[0], 10, c[1], lanterna(Material.LANTERN, false));
            p.set(c[0], ALTURA_MURALHA + 1, c[1], lanterna(Material.LANTERN, false));
        }
    }

    // ------------------------------------------------------------------ decoração do público

    private void gerarDecoracao(Planta p) {
        // Toldos listrados (vermelho e branco) sobre a galeria, presos na muralha, com luz embutida.
        int yToldo = TOPO + 5;
        for (int x = -LIMITE_X; x <= LIMITE_X; x++) {
            for (int z = -LIMITE_Z; z <= LIMITE_Z; z++) {
                int k = anel(x, z);
                if (k != ANEL_GALERIA + 1 && k != ANEL_GALERIA + 2) continue;
                int listra = (int) Math.floor(volta(x, z, k) * ARCOS * 2) % 2;
                p.set(x, yToldo, z, listra == 0 ? Material.RED_WOOL : Material.WHITE_WOOL);
            }
        }
        for (int i = 0; i < ARCOS; i++) {
            int[] c = centro(ARCOS, i, ANEL_GALERIA + 1);
            if (c != null) p.set(c[0], yToldo, c[1], LUZ);
        }

        // Estandartes coloridos nos pilares da muralha (lado da galeria), embaixo dos toldos.
        for (int i = 0; i < ARCOS; i++) {
            estandarte(p, ponto((i + 0.5) / ARCOS, ANEL_MURALHA - 1), TOPO + 3, ESTANDARTES[i % ESTANDARTES.length]);
        }
        // Estandartes na parede do piso, virados pra luta (um a cada 6 painéis, nas pilastras).
        for (int i = 0; i < PAINEIS; i += 6) {
            estandarte(p, ponto((i + 0.5) / PAINEIS, 0), PODIO - 3, ESTANDARTES[(i / 6) % ESTANDARTES.length]);
        }

        // Vasos no peitoril das janelas da galeria.
        for (int i = 0; i < ARCOS; i++) {
            int[] c = centro(ARCOS, i, ANEL_MURALHA);
            if (c != null && livre(p.get(c[0], TOPO + 1, c[1]))) {
                p.set(c[0], TOPO + 1, c[1], VASOS[i % VASOS.length]);
            }
        }

        // Deck: pedestais com lanterna alternando com vasos, rente à primeira fileira.
        for (int i = 0; i < 48; i++) {
            int[] c = ponto((i + 0.25) / 48, 3);
            if (c == null) continue;
            if (i % 2 == 0) {
                p.set(c[0], PODIO + 1, c[1], ENTALHADO);
                p.set(c[0], PODIO + 2, c[1], lanterna(Material.LANTERN, false));
            } else {
                p.set(c[0], PODIO + 1, c[1], VASOS[(i / 2) % VASOS.length]);
            }
        }

        // Camarotes nos eixos leste/oeste: tablado de arenito vermelho, bancos e estandartes.
        for (int s : new int[]{-1, 1}) {
            BlockFace olhar = s > 0 ? BlockFace.EAST : BlockFace.WEST;   // parte alta do banco pra fora
            for (int z = -5; z <= 5; z++) {
                for (int xx = RAIO_X + 1; xx <= RAIO_X + 3; xx++) {
                    int x = s * xx;
                    if (anel(x, z) < 1 || anel(x, z) > 3) continue;
                    p.set(x, PODIO, z, Material.CUT_RED_SANDSTONE);
                }
                int xb = s * (RAIO_X + 3);
                if (Math.abs(z) <= 3 && anel(xb, z) == 3) p.set(xb, PODIO + 1, z, escada(Material.DARK_OAK_STAIRS, olhar, false));
            }
        }
    }

    /** Estandarte de parede no ponto c (anel do lado de dentro), encostado no bloco pra fora dele. */
    private void estandarte(Planta p, int[] c, int y, Material material) {
        if (c == null) return;
        BlockFace fora = paraFora(c[0], c[1]);
        int x = c[0], z = c[1];
        // Anda pra fora até o bloco de trás ser parede.
        for (int passo = 0; passo < 3 && livre(p.get(x + fora.getModX(), y, z + fora.getModZ())); passo++) {
            x += fora.getModX();
            z += fora.getModZ();
        }
        if (!livre(p.get(x, y, z)) || livre(p.get(x + fora.getModX(), y, z + fora.getModZ()))) return;
        p.set(x, y, z, direcional(material, fora.getOppositeFace()));
    }

    // ------------------------------------------------------------------ portões e acessos

    /** Portões decorativos leste/oeste no piso, FECHADOS: grade de ferro na frente e portas de madeira no fundo. */
    private void gerarPortoes(Planta p) {
        for (int s : new int[]{-1, 1}) {
            for (int xx = RAIO_X - 2; xx <= RAIO_X + 6; xx++) {
                int x = s * xx;
                for (int z = -5; z <= 5; z++) {
                    int k = anel(x, z);
                    if (k < 1 || k > 3) continue;
                    int az = Math.abs(z);
                    BlockFace parede = z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
                    if (k == 1) {
                        if (az == 5) {
                            for (int y = 1; y <= PODIO - 1; y++) p.set(x, y, z, Material.SMOOTH_RED_SANDSTONE);
                            p.set(x, PODIO - 1, z, Material.CHISELED_RED_SANDSTONE);
                        } else {
                            for (int y = 1; y <= 7; y++) {
                                p.set(x, y, z, conectado(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH));
                            }
                            p.set(x, 8, z, Material.CUT_RED_SANDSTONE);
                        }
                    } else if (k == 2 && az <= 4) {
                        // Vão de 1 bloco atrás da grade, com arco de escadas invertidas.
                        for (int y = 1; y <= 7; y++) p.set(x, y, z, Material.AIR);
                        if (az == 4) p.set(x, 7, z, escada(ESCADA, parede, true));
                        if (z == 0) p.set(x, 7, z, lanterna(Material.LANTERN, true));
                    } else if (k == 3 && az <= 4) {
                        for (int y = 1; y <= 7; y++) p.set(x, y, z, Material.DARK_OAK_PLANKS);
                        p.set(x, 3, z, Material.STRIPPED_DARK_OAK_LOG);
                    }
                }
            }
        }
    }


    // ------------------------------------------------------------------ piso de luta

    /**
     * Cobertura espalhada pelo piso (até 8 de altura, abaixo do vidro): colunas quebradas, arcos em
     * ruína, muretas e cantos de parede. O centro (raio ~12) fica livre pra borda final de 8x8.
     */
    private void gerarObstaculos(Planta p) {
        Random random = new Random(1937);

        // Anel interno: oito colunas quebradas.
        for (int i = 0; i < 8; i++) {
            int[] c = noPiso(0.24, 22.5 + 45 * i);
            colunaQuebrada(p, c[0], c[1], 4 + random.nextInt(3), random);
        }

        // Arcos em ruína nos quatro eixos.
        int ax = (int) Math.round(RAIO_X * 0.42), az = (int) Math.round(RAIO_Z * 0.42);
        arcoRuina(p, ax, 0, false);
        arcoRuina(p, -ax, 0, false);
        arcoRuina(p, 0, az, true);
        arcoRuina(p, 0, -az, true);

        // Muretas nas diagonais, alternando a direção.
        for (int i = 0; i < 4; i++) {
            int[] c = noPiso(0.42, 45 + 90 * i);
            if (i % 2 == 0) mureta(p, c[0] - 4, c[1], c[0] + 4, c[1]);
            else mureta(p, c[0], c[1] - 4, c[0], c[1] + 4);
        }

        // Anel do meio: colunas (com tambor caído do lado) e cantos de ruína nas diagonais.
        for (int i = 0; i < 12; i++) {
            double graus = 15 + 30 * i;
            int[] c = noPiso(0.62, graus);
            if ((int) (graus - 45) % 90 == 0) {
                ruinaL(p, c[0], c[1], c[0] >= 0 ? -1 : 1, c[1] >= 0 ? -1 : 1, random);
            } else {
                colunaQuebrada(p, c[0], c[1], 3 + random.nextInt(5), random);
                if (i % 2 == 0) {
                    int[] t = noPiso(0.62, graus + 7);
                    tamborCaido(p, t[0], t[1], random.nextBoolean());
                }
            }
        }

        // Anel externo: muretas grandes nos eixos, colunas e tambores no resto.
        for (int i = 0; i < 16; i++) {
            double graus = 22.5 * i;
            int[] c = noPiso(0.82, graus);
            if (i % 4 == 0) {
                if (i % 8 == 0) mureta(p, c[0], c[1] - 5, c[0], c[1] + 5);
                else mureta(p, c[0] - 5, c[1], c[0] + 5, c[1]);
            } else if (i % 2 == 1) {
                colunaQuebrada(p, c[0], c[1], 3 + random.nextInt(4), random);
            } else {
                tamborCaido(p, c[0], c[1], random.nextBoolean());
                colunaQuebrada(p, c[0] + 4, c[1] + 4, 2 + random.nextInt(2), random);
            }
        }

        // Barris e fardos junto aos portões.
        for (int s : new int[]{-1, 1}) {
            int x = s * (RAIO_X - 3);
            for (int z : new int[]{-8, 8}) {
                p.set(x, 1, z, Material.BARREL);
                p.set(x, 1, z + (z > 0 ? 1 : -1), Material.HAY_BLOCK);
                p.set(x, 2, z, Material.HAY_BLOCK);
                p.set(x - s, 1, z, Material.BARREL);
            }
        }

        // Manchas de areia vermelha / terra e arbustos secos, sorteados com semente fixa.
        for (int i = 0; i < 90; i++) {
            int cx = random.nextInt(2 * RAIO_X) - RAIO_X, cz = random.nextInt(2 * RAIO_Z) - RAIO_Z;
            Material mancha = i % 2 == 0 ? Material.RED_SAND : Material.COARSE_DIRT;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz > 9 || random.nextInt(4) == 0) continue;
                    if (p.get(cx + dx, 0, cz + dz) == Material.SAND) p.set(cx + dx, 0, cz + dz, mancha);
                }
            }
        }
        for (int i = 0; i < 200; i++) {
            int x = random.nextInt(2 * RAIO_X) - RAIO_X, z = random.nextInt(2 * RAIO_Z) - RAIO_Z;
            if (x * x + z * z < 196) continue;
            Material chao = p.get(x, 0, z);
            boolean solto = chao == Material.SAND || chao == Material.RED_SAND || chao == Material.COARSE_DIRT;
            if (solto && anel(x, z) <= -3 && livre(p.get(x, 1, z))) p.set(x, 1, z, Material.DEAD_BUSH);
        }
    }

    private void colunaQuebrada(Planta p, int x, int z, int altura, Random random) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                p.set(x + dx, 1, z + dz, ENTALHADO);
                for (int y = 2; y < altura; y++) p.set(x + dx, y, z + dz, LISO);
                // Topo partido: cada quarto vira bloco, laje ou some.
                switch (random.nextInt(3)) {
                    case 0 -> p.set(x + dx, altura, z + dz, LISO);
                    case 1 -> p.set(x + dx, altura, z + dz, laje(LAJE, false));
                    default -> { }
                }
            }
        }
    }

    /** Pedaço de coluna caído: três blocos deitados com a ponta entalhada. */
    private void tamborCaido(Planta p, int x, int z, boolean aoLongoDeX) {
        for (int a = 0; a < 3; a++) {
            int bx = aoLongoDeX ? x + a : x, bz = aoLongoDeX ? z : z + a;
            p.set(bx, 1, bz, a == 0 ? ENTALHADO : CORTADO);
        }
        p.set(aoLongoDeX ? x + 3 : x, 1, aoLongoDeX ? z : z + 3, laje(LAJE, false));
    }

    /** Mureta de 2 + laje, com pontas entalhadas e lanterna em cima. */
    private void mureta(Planta p, int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                boolean ponta = (x == x1 && z == z1) || (x == x2 && z == z2);
                p.set(x, 1, z, ponta ? ENTALHADO : CORTADO);
                p.set(x, 2, z, ponta ? ENTALHADO : CORTADO);
                p.set(x, 3, z, ponta ? LISO.createBlockData() : laje(LAJE, false));
                if (ponta) p.set(x, 4, z, lanterna(Material.LANTERN, false));
            }
        }
    }

    /**
     * Arco em ruína: dois pilares 2x2 de 6 com um vão de 3 entre eles e a verga em cima (um lado
     * mais alto, quebrado). {@code aoLongoDeX} = o arco se estende no eixo x.
     */
    private void arcoRuina(Planta p, int cx, int cz, boolean aoLongoDeX) {
        for (int a = -3; a <= 3; a++) {
            for (int b = 0; b <= 1; b++) {
                int x = aoLongoDeX ? cx + a : cx + b, z = aoLongoDeX ? cz + b : cz + a;
                boolean pilar = Math.abs(a) >= 2;
                if (pilar) {
                    p.set(x, 1, z, ENTALHADO);
                    for (int y = 2; y <= 6; y++) p.set(x, y, z, LISO);
                }
                p.set(x, 7, z, Math.abs(a) == 1 ? CORTADO : ENTALHADO);
                if (a >= 2) p.set(x, 8, z, a == 3 ? LISO.createBlockData() : laje(LAJE, false));
            }
            if (a == 0) p.set(cx, 6, cz, lanterna(Material.LANTERN, true));
        }
    }

    /** Canto de parede em ruína (L de 6 + 6), com a altura caindo pras pontas. */
    private void ruinaL(Planta p, int cx, int cz, int sx, int sz, Random random) {
        int alturaCanto = 5;
        for (int a = 0; a < 6; a++) {
            int altura = a == 0 ? alturaCanto : Math.max(1, 5 - a + random.nextInt(2));
            for (int y = 1; y <= altura; y++) {
                Material m = y == 1 ? ENTALHADO : (y + a) % 3 == 0 ? MACICO : CORTADO;
                p.set(cx + sx * a, y, cz, m);
                p.set(cx, y, cz + sz * a, m);
            }
        }
        p.set(cx, alturaCanto + 1, cz, lanterna(Material.LANTERN, false));
    }

    /** Grade de luzes no chão — sem isso o piso fica escuro e nasce mob. */
    private void gerarLuzesDoPiso(Planta p) {
        for (int x = -RAIO_X + 4; x <= RAIO_X - 4; x += 9) {
            for (int z = -RAIO_Z + 1; z <= RAIO_Z - 1; z += 9) {
                if (anel(x, z) > -2 || !livre(p.get(x, 1, z))) continue;
                p.set(x, 0, z, LUZ);
            }
        }
        p.set(0, 0, 0, LUZ);
    }
}
