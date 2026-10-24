package com.example.estruturas.estrutura;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.laje;

/**
 * Arena circular de usina abandonada (inspirada na "Toxic Arena | 600x600 | Boss Map" da BuiltByBit):
 * muralha de deepslate com faixas de perigo amarelo/preto, janelas verdes iluminadas e nervuras metálicas,
 * canos de cobre oxidado despejando lodo em poças tóxicas, torres de reator quebradas, contêineres,
 * barreiras, caixotes e tambores de resíduo como cobertura. Fechada por uma cúpula baixa de vidro verde
 * com treliça de deepslate apoiada no topo da muralha, pra nem o boss (o Wither voa) nem os jogadores
 * saírem por cima. Só a arena de luta — sem o cenário de 600x600.
 *
 * Tudo é calculado em coordenadas polares a partir do centro (0, 0): d é a distância ao centro e
 * o ângulo começa no leste (+x) e gira pro sul (+z).
 */
public class ArenaToxica implements Estrutura {

    private static final int RAIO_PISO = 48;          // piso jogável: ~97 de diâmetro
    private static final int RAIO_PAREDE = 55;        // muralha de 7 de espessura
    private static final int RAIO_LIMPEZA = 58;       // faixa externa de entulho até aqui
    private static final int ALTURA = 42;             // altura da muralha (o topo é passarela e apoio da cúpula)
    private static final int FOLGA_ACIMA = 3;         // ar limpo acima do que a estrutura usa
    private static final int SEGMENTOS = 36;          // painéis da muralha (10° cada)
    private static final int ALTURA_CANO = 18;
    private static final int RAIO_POCA = 36;          // distância das poças ao centro
    private static final int RAIO_TORRE = 26;
    private static final int ENTRADA_NORTE = 3;       // em quartos de volta (0 = leste, 1 = sul, 2 = oeste, 3 = norte)
    // A arena fica enterrada: o piso dela está PROFUNDIDADE abaixo do chão de fora (gramado em y = PROFUNDIDADE),
    // então a cúpula não aparece por cima da muralha do spawn. Da entrada norte, uma escadaria fechada sobe
    // até o chão; o patamar no topo (z = -FIM_DESCIDA) é onde ela encosta no portão sul do spawn.
    private static final int PROFUNDIDADE = 32;
    // Na chegada à arena, o túnel da muralha fica DEGRAU blocos acima do piso: quem desce pula, e os mobs
    // que o boss invoca não conseguem subir pro spawn.
    private static final int DEGRAU = 4;
    private static final int ALTURA_ESCADA = 6;      // ar livre acima de cada degrau da escadaria
    private static final int TETO_TUNEL = DEGRAU + ALTURA_ESCADA + 1; // o túnel da muralha tem a altura da escada
    private static final int FIM_DESCIDA = RAIO_PAREDE + PROFUNDIDADE + 1;

    /** Cúpula: cobre até {@link #RAIO_TETO}, nasce no topo da muralha e sobe {@link #FLECHA_TETO} no centro. */
    private static final double RAIO_TETO = RAIO_PISO + 1.5;
    private static final int FLECHA_TETO = 10;
    private static final int MODULO_TETO = 8;         // treliça a cada 8 blocos

    private static final Material BASE_MURO = Material.DEEPSLATE_TILES;
    private static final Material NERVURA = Material.POLISHED_DEEPSLATE;
    private static final Material ESCURO = Material.POLISHED_BLACKSTONE_BRICKS;
    private static final Material COBRE = Material.WAXED_OXIDIZED_CUT_COPPER;
    private static final Material LUZ = Material.VERDANT_FROGLIGHT;
    private static final Material LODO = Material.LIME_STAINED_GLASS;
    private static final Material JANELA = Material.GREEN_STAINED_GLASS;

    /** Materiais "neutros" do piso: só eles podem ser trocados por veios, luzes e entulho. */
    private static final Material[] PISO = {
            Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, Material.CRACKED_DEEPSLATE_TILES,
            Material.TUFF_BRICKS, Material.GRAY_CONCRETE, Material.POLISHED_TUFF
    };
    private static final Set<Material> PISO_BASE = EnumSet.of(PISO[0], PISO[1], PISO[2], PISO[3], PISO[4], PISO[5]);

    @Override
    public String getId() {
        return "toxica";
    }

    @Override
    public String getNome() {
        return "Arena Tóxica";
    }

    /** É a arena do boss: o local de nascimento dele vai pro centro, logo acima do piso. */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return List.of("boss setlocal " + mundo + " " + (ox + 0.5) + " " + (oy + 1) + " " + (oz + 0.5));
    }

    @Override
    public void gerar(Planta p) {
        gerarBase(p);
        gerarMuralha(p);
        gerarCentro(p);
        for (int i = 0; i < 4; i++) gerarPocaComCano(p, Math.toRadians(45 + 90 * i));
        gerarVeios(p);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(90 * i);
            gerarTorre(p, (int) Math.round(Math.cos(a) * RAIO_TORRE), (int) Math.round(Math.sin(a) * RAIO_TORRE), 16 + i % 3 * 3);
        }
        gerarContaineres(p);
        gerarBarreiras(p);
        gerarCaixotes(p);
        gerarTambores(p);
        gerarEntrada(p, ENTRADA_NORTE); // a única: fica de frente pro portão sul do spawn
        gerarDescida(p);
        gerarLuzesDoPiso(p);
        gerarEntulho(p);
        gerarTeto(p);
    }

    // ------------------------------------------------------------------ utilidades

    private static double dist(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /** Ângulo em [0, 2π). */
    private static double angulo(double x, double z) {
        double a = Math.atan2(z, x);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    /** Faixa de perigo diagonal amarela e preta. */
    private static Material perigo(int a, int b) {
        return Math.floorMod(Math.floorDiv(a + b, 2), 2) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
    }

    /** Ruído barato e determinístico por célula (mesmo resultado em toda geração). */
    private static long hash(int x, int z) {
        long h = x * 73856093L ^ z * 19349663L ^ 0x5DEECE66DL;
        h ^= h >>> 13;
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }

    /** Gira (x, z) em passos de 90° em torno do centro. */
    private static int[] girar(int x, int z, int quartos) {
        return switch (Math.floorMod(quartos, 4)) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    private boolean pisoLivre(Planta p, int x, int z) {
        return dist(x, z) <= RAIO_PISO - 1 && PISO_BASE.contains(p.get(x, 0, z)) && p.get(x, 1, z) == Material.AIR;
    }

    /** Altura do vidro da cúpula na coluna (x, z): {@link #ALTURA} na borda, {@code ALTURA + FLECHA_TETO} no centro. */
    private static int alturaTeto(int x, int z) {
        double d = dist(x, z);
        if (d > RAIO_TETO) return ALTURA; // fora da cúpula: topo maciço da muralha
        double k = d / RAIO_TETO;
        return ALTURA + (int) Math.floor(FLECHA_TETO * (1 - k * k));
    }

    // ------------------------------------------------------------------ base e muralha

    /**
     * Limpa o volume (só dentro do círculo e só até a altura que a estrutura usa + folga), assenta
     * fundação, piso manchado e a faixa de entulho do lado de fora.
     */
    private void gerarBase(Planta p) {
        int r = RAIO_LIMPEZA;
        for (int x = -r; x <= r; x++) {
            for (int z = -FIM_DESCIDA; z <= r; z++) {
                double d = dist(x, z);
                // Fora da muralha: gramado no nível do chão de fora, até o quadrado (e até o patamar ao norte).
                if (d > RAIO_PAREDE + 0.5) gramado(p, x, z, d <= r + 0.5);
                if (d > r + 0.5) continue;
                p.set(x, -1, z, Material.DEEPSLATE);
                int topo = alturaTeto(x, z) + FOLGA_ACIMA;
                for (int y = 1; y <= topo; y++) p.set(x, y, z, Material.AIR);
                if (d <= RAIO_PISO + 0.5) {
                    // Manchas de 3x3 com o mesmo material + alguns blocos soltos trincados.
                    p.set(x, 0, z, materialDoPiso(x, z));
                } else {
                    long h = Math.floorMod(hash(x, z), 4);
                    p.set(x, 0, z, h == 0 ? Material.TUFF : h == 1 ? Material.GRAVEL : Material.COBBLED_DEEPSLATE);
                }
            }
        }
    }

    /** Piso manchado: manchas de 3x3 com o mesmo material e alguns blocos soltos trincados. */
    private static Material materialDoPiso(int x, int z) {
        if (Math.floorMod(hash(x, z), 7) == 0) return Material.CRACKED_DEEPSLATE_TILES;
        return PISO[(int) Math.floorMod(hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3)), PISO.length)];
    }

    /** Gramado de fora, em y = PROFUNDIDADE ({@code limpo} = a coluna já foi limpa até acima dele). */
    private void gramado(Planta p, int x, int z, boolean limpo) {
        int sorte = (int) Math.floorMod(hash(x, z), 100);
        Material chao = sorte < 70 ? Material.GRASS_BLOCK : sorte < 85 ? Material.COARSE_DIRT
                : sorte < 95 ? Material.PODZOL : Material.MOSS_BLOCK;
        p.set(x, PROFUNDIDADE - 1, z, Material.DIRT);
        p.set(x, PROFUNDIDADE, z, chao);
        if (!limpo) {
            for (int y = PROFUNDIDADE + 1; y <= PROFUNDIDADE + 6; y++) p.set(x, y, z, Material.AIR);
        }
        if (chao == Material.GRASS_BLOCK && sorte < 15) p.set(x, PROFUNDIDADE + 1, z, Material.SHORT_GRASS);
    }

    /**
     * Muralha circular. A face interna é dividida em {@link #SEGMENTOS} painéis separados por nervuras
     * salientes; cada painel tem rodapé de perigo, janela verde com froglight atrás e friso de cobre no alto.
     */
    private void gerarMuralha(Planta p) {
        int r = RAIO_PAREDE + 1;
        double raioFace = RAIO_PISO + 1;
        double tamSegmento = 2 * Math.PI * raioFace / SEGMENTOS;
        // Desloca meio painel pra que as entradas (eixos) caiam no meio de um painel, entre duas nervuras.
        double deslocamento = tamSegmento / 2;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = dist(x, z);
                if (d <= RAIO_PISO - 0.5 || d > RAIO_PAREDE + 0.5) continue;
                double arco = angulo(x, z) * raioFace;
                double pos = ((arco + deslocamento) % tamSegmento + tamSegmento) % tamSegmento;
                int u = (int) Math.floor(arco);
                boolean nervura = pos < 1 || pos > tamSegmento - 1;
                boolean meio = Math.abs(pos - tamSegmento / 2) <= 2;

                if (d <= RAIO_PISO + 0.5) {
                    // Anel mais externo do piso: só recebe a nervura saliente (sobe até a cúpula).
                    if (nervura) {
                        for (int y = 1; y < ALTURA; y++) p.set(x, y, z, NERVURA);
                        p.set(x, 0, z, ESCURO);
                        p.set(x, 1, z, perigo(u, 1));
                        p.set(x, 2, z, perigo(u, 2));
                        p.set(x, 10, z, LUZ);
                        p.set(x, 20, z, LUZ);
                        p.set(x, 30, z, LUZ);
                        p.set(x, ALTURA, z, ESCURO);
                    }
                    continue;
                }

                boolean face = d <= RAIO_PISO + 1.5;
                boolean atrasDaFace = !face && d <= RAIO_PISO + 2.5;
                boolean externa = d > RAIO_PAREDE - 0.5;

                for (int y = 0; y < ALTURA; y++) {
                    Material m;
                    if (externa) {
                        m = y == 0 || y == ALTURA - 1 ? ESCURO
                                : Math.floorMod(hash(x * 31 + y, z), 5) == 0 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.DEEPSLATE_BRICKS;
                    } else if (face) {
                        m = materialDaFace(y, u, nervura, meio);
                    } else if (atrasDaFace && meio && y >= 9 && y <= 30) {
                        m = LUZ; // ilumina a janela verde por trás
                    } else {
                        m = Material.DEEPSLATE;
                    }
                    p.set(x, y, z, m);
                }

                // Topo: passarela com faixa de cobre e ameias na borda externa.
                p.set(x, ALTURA, z, externa ? ESCURO : face ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_TILES);
                if (!externa && !face && Math.floorMod(u, 12) == 0 && d > RAIO_PISO + 3.5 && d <= RAIO_PISO + 4.5) {
                    p.set(x, ALTURA, z, LUZ);
                }
                if (externa) {
                    boolean ameia = Math.floorMod(u, 4) < 2;
                    p.set(x, ALTURA + 1, z, ameia ? ESCURO.createBlockData() : laje(Material.POLISHED_BLACKSTONE_BRICK_SLAB, false));
                    if (ameia && Math.floorMod(u, 8) == 0) {
                        p.set(x, ALTURA + 2, z, direcional(Material.LIGHTNING_ROD, BlockFace.UP));
                    }
                }
            }
        }
    }

    private Material materialDaFace(int y, int u, boolean nervura, boolean meio) {
        if (y == 0) return ESCURO;
        if (y <= 2) return perigo(u, y);
        if (y == 3 || y == 34 || y == 38 || y == ALTURA - 1) return ESCURO;
        if (y >= 35 && y <= 37) return COBRE;
        if (nervura) return NERVURA;
        if (meio && y >= 9 && y <= 30) return JANELA;
        if (y <= 7) return Material.POLISHED_TUFF;
        return BASE_MURO;
    }

    /**
     * Entrada a pé: túnel 7x8 atravessando a muralha com moldura de perigo e uma faixa de acesso
     * escura no piso em frente (fica livre de entulho e tambores). Do lado de fora, a escadaria.
     */
    private void gerarEntrada(Planta p, int quartos) {
        // Local: u corre de lado, w é a distância ao centro ao longo do eixo. Na frente da entrada (ainda
        // no piso da arena) só abre espaço, mantendo o piso igual ao resto; dentro da muralha, o túnel.
        for (int w = RAIO_PISO - 6; w <= RAIO_PAREDE; w++) {
            for (int u = -4; u <= 4; u++) {
                boolean lado = Math.abs(u) == 4;
                boolean dentroDoMuro = dist(u, w) > RAIO_PISO + 0.5 && dist(u, w) <= RAIO_PAREDE + 0.5;
                int[] c = girar(w, u, quartos);
                if (!dentroDoMuro) {
                    if (!lado) {
                        p.set(c[0], 0, c[1], materialDoPiso(c[0], c[1]));
                        for (int y = 1; y < TETO_TUNEL; y++) p.set(c[0], y, c[1], Material.AIR);
                    }
                    continue;
                }
                for (int y = 0; y <= TETO_TUNEL; y++) {
                    boolean parede = lado || y == 0 || y == TETO_TUNEL;
                    p.set(c[0], y, c[1], parede ? ESCURO : Material.AIR);
                }
                // luz no teto
                if (u == 0 && Math.floorMod(w, 3) == 0) p.set(c[0], TETO_TUNEL, c[1], LUZ);
            }
        }
        // O degrau: dentro da muralha o piso do túnel sobe DEGRAU blocos; a frente tem o acabamento da muralha.
        for (int wm = RAIO_PISO + 1; wm <= RAIO_PAREDE; wm++) {
            for (int u = -3; u <= 3; u++) {
                if (dist(u, wm) <= RAIO_PISO + 0.5) continue;
                boolean frente = dist(u, wm - 1) <= RAIO_PISO + 0.5;
                for (int y = 1; y <= DEGRAU; y++) {
                    int[] c = girar(wm, u, quartos);
                    p.set(c[0], y, c[1], frente ? (y == DEGRAU ? NERVURA : Material.POLISHED_TUFF) : ESCURO);
                }
            }
        }
        // Moldura saliente na face de dentro: batentes e verga com listras, sinal luminoso em cima.
        int w = RAIO_PISO;
        int verga = TETO_TUNEL;
        for (int u = -5; u <= 5; u++) {
            for (int y = 1; y <= verga + 1; y++) {
                boolean batente = Math.abs(u) >= 4;
                if (!batente && y < verga) continue;
                int[] c = girar(w, u, quartos);
                p.set(c[0], y, c[1], perigo(u, y));
            }
        }
        for (int u = -2; u <= 2; u++) {
            int[] c = girar(w, u, quartos);
            p.set(c[0], verga + 2, c[1], u == 0 ? LUZ : ESCURO);
            p.set(c[0], verga + 3, c[1], laje(Material.POLISHED_BLACKSTONE_BRICK_SLAB, false));
        }
    }

    /**
     * Escadaria fechada da entrada norte até o chão de fora: 7 de largura e 6 de altura livre, sobe um
     * bloco por passo a partir do piso elevado do túnel e termina num patamar no nível do gramado, com o
     * mesmo arco do túnel do portão do spawn. Por baixo do gramado ela é um túnel; perto do topo, o
     * teto aparece um pouco acima do chão.
     */
    private void gerarDescida(Planta p) {
        for (int w = RAIO_PAREDE + 1; w <= FIM_DESCIDA; w++) {
            int degrau = Math.min(PROFUNDIDADE, DEGRAU + w - RAIO_PAREDE); // continua do piso elevado do túnel
            boolean patamar = DEGRAU + w - RAIO_PAREDE > PROFUNDIDADE;
            for (int u = -4; u <= 4; u++) {
                // No patamar, o mesmo arco do túnel do portão do spawn (9, 8 e 7 de altura), pra os dois casarem.
                int livre = !patamar ? ALTURA_ESCADA : Math.abs(u) <= 1 ? 9 : Math.abs(u) == 2 ? 8 : 7;
                int[] c = girar(w, u, ENTRADA_NORTE);
                // paredes e teto vão até a altura do teto do patamar: na troca da escada pro patamar (que
                // é mais alto) não sobra vão aberto pro céu
                if (Math.abs(u) == 4) {
                    for (int y = degrau - 1; y <= degrau + 10; y++) p.set(c[0], y, c[1], ESCURO);
                    continue;
                }
                p.set(c[0], degrau - 1, c[1], ESCURO);
                p.set(c[0], degrau, c[1], patamar ? ESCURO.createBlockData()
                        : Planta.escada(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH, false));
                for (int y = degrau + 1; y <= degrau + livre; y++) p.set(c[0], y, c[1], Material.AIR);
                for (int y = degrau + livre + 1; y <= degrau + 10; y++) p.set(c[0], y, c[1], ESCURO);
                p.set(c[0], degrau + livre + 1, c[1], u == 0 && w % 3 == 0 ? LUZ : ESCURO);
            }
        }
    }

    // ------------------------------------------------------------------ teto

    /**
     * Cúpula baixa de vidro sobre o piso inteiro, apoiada no topo da muralha: painéis de vidro lima
     * (alguns verdes e alguns claros) numa treliça quadrada de deepslate polido a cada
     * {@link #MODULO_TETO} blocos, com froglight em cada nó e um óculo escuro no centro. Onde a cúpula
     * sobe em degrau, a coluna desce até encostar no vizinho mais baixo, pra não sobrar fresta.
     */
    private void gerarTeto(Planta p) {
        int r = (int) Math.ceil(RAIO_TETO);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = dist(x, z);
                if (d > RAIO_TETO) continue;
                int topo = alturaTeto(x, z);
                int vizinho = Math.min(Math.min(alturaTeto(x + 1, z), alturaTeto(x - 1, z)),
                        Math.min(alturaTeto(x, z + 1), alturaTeto(x, z - 1)));
                int base = Math.min(topo, vizinho + 1);

                boolean linhaX = Math.floorMod(x, MODULO_TETO) == 0;
                boolean linhaZ = Math.floorMod(z, MODULO_TETO) == 0;
                Material m;
                if (d <= 1.5) {
                    m = LUZ;
                } else if (d > 4.5 && d <= 6.5) {
                    m = d <= 5.5 ? ESCURO : NERVURA; // óculo central
                } else if (d > RAIO_TETO - 1) {
                    m = ESCURO; // anel de apoio sobre a muralha
                } else if (linhaX && linhaZ) {
                    m = LUZ;
                } else if (linhaX || linhaZ) {
                    m = NERVURA;
                } else {
                    long h = Math.floorMod(hash(Math.floorDiv(x, MODULO_TETO) * 5 + 1, Math.floorDiv(z, MODULO_TETO) * 3 - 7), 10);
                    m = h == 0 ? Material.GREEN_STAINED_GLASS : h == 1 ? Material.GLASS : Material.LIME_STAINED_GLASS;
                }
                for (int y = base; y <= topo; y++) p.set(x, y, z, m);
            }
        }
    }

    // ------------------------------------------------------------------ piso

    /** Centro onde o boss nasce: ralo de grade brilhando, disco escuro, aro de ferro e anel de perigo. */
    private void gerarCentro(Planta p) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = dist(x, z);
                if (d > 11.5) continue;
                Material m;
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                    m = Material.WAXED_COPPER_GRATE;
                    p.set(x, -1, z, LUZ);
                } else if (d <= 6.5) {
                    m = (x == 0 || z == 0) ? Material.POLISHED_BLACKSTONE : ESCURO;
                } else if (d <= 8.5) {
                    m = Material.IRON_BLOCK;
                } else {
                    int fatia = (int) (angulo(x, z) / (2 * Math.PI) * 30);
                    m = fatia % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
                }
                p.set(x, 0, z, m);
            }
        }
    }

    /**
     * Poça de lodo tóxico (vidro lima sobre froglight, rente ao piso — sem líquido de verdade) com um cano
     * de cobre saindo da muralha e despejando um filete em cima dela.
     */
    private void gerarPocaComCano(Planta p, double a) {
        int cx = (int) Math.round(Math.cos(a) * RAIO_POCA);
        int cz = (int) Math.round(Math.sin(a) * RAIO_POCA);
        Random random = new Random(cx * 31L + cz);

        // Poça com borda irregular.
        for (int x = cx - 9; x <= cx + 9; x++) {
            for (int z = cz - 9; z <= cz + 9; z++) {
                double d = dist(x - cx, z - cz) + Math.floorMod(hash(x, z), 10) / 10.0;
                if (d > 7.8 || dist(x, z) > RAIO_PISO - 1) continue;
                if (d <= 5.7) {
                    p.set(x, 0, z, LODO);
                    p.set(x, -1, z, LUZ);
                } else {
                    p.set(x, 0, z, Math.floorMod(hash(z, x), 3) == 0 ? Material.LIME_TERRACOTTA : Material.GREEN_CONCRETE);
                }
            }
        }
        // Bolhas de slime saltando da poça.
        for (int i = 0; i < 5; i++) {
            int x = cx + random.nextInt(7) - 3, z = cz + random.nextInt(7) - 3;
            if (p.get(x, 0, z) == LODO) p.set(x, 0, z, Material.SLIME_BLOCK);
        }

        // Cano: cilindro de cobre oxidado do meio da muralha até em cima da poça, com flanges de ferro.
        double cos = Math.cos(a), sin = Math.sin(a);
        for (double t = RAIO_POCA; t <= RAIO_PAREDE - 3; t += 0.25) {
            boolean flange = Math.floorMod((int) Math.round(t * 4), 18) == 0 && t > RAIO_POCA + 1 && t < RAIO_PISO;
            double raio = flange ? 2.8 : 2.1;
            double px = cos * t, pz = sin * t;
            for (int x = (int) Math.floor(px - 3); x <= (int) Math.ceil(px + 3); x++) {
                for (int y = ALTURA_CANO - 3; y <= ALTURA_CANO + 3; y++) {
                    for (int z = (int) Math.floor(pz - 3); z <= (int) Math.ceil(pz + 3); z++) {
                        double dx = x - px, dy = y - ALTURA_CANO, dz = z - pz;
                        if (dx * dx + dy * dy + dz * dz <= raio * raio) {
                            p.set(x, y, z, flange ? Material.IRON_BLOCK : Material.WAXED_OXIDIZED_COPPER);
                        }
                    }
                }
            }
        }
        // Boca aberta do cano e o filete caindo na poça.
        for (double t = RAIO_POCA - 2; t <= RAIO_POCA + 0.5; t += 0.25) {
            p.set((int) Math.round(cos * t), ALTURA_CANO, (int) Math.round(sin * t), LODO);
        }
        for (int y = 1; y < ALTURA_CANO; y++) p.set(cx, y, cz, LODO);
        // Luzes no dorso do cano: lá em cima fica longe das luzes do chão e nasceria mob.
        for (int t = RAIO_POCA + 1; t < RAIO_PISO; t += 5) {
            p.set((int) Math.round(cos * t), ALTURA_CANO + 2, (int) Math.round(sin * t), LUZ);
        }

        // Suporte do cano: pilar escuro do chão até a barriga do cano.
        int sx = (int) Math.round(cos * (RAIO_PISO - 6)), sz = (int) Math.round(sin * (RAIO_PISO - 6));
        for (int y = 1; y <= ALTURA_CANO - 3; y++) p.set(sx, y, sz, ESCURO);
        p.set(sx, 1, sz, perigo(sx, 1));
        p.set(sx, 2, sz, perigo(sx, 2));
    }

    /** Rachaduras com lodo saindo do centro: veios lima com froglight de vez em quando. */
    private void gerarVeios(Planta p) {
        Random random = new Random(1337);
        for (int i = 0; i < 12; i++) {
            double a = 2 * Math.PI * i / 12 + random.nextDouble() * 0.3;
            double x = Math.cos(a) * 12, z = Math.sin(a) * 12;
            int passo = 0;
            while (dist(x, z) < RAIO_PISO - 2) {
                a += (random.nextDouble() - 0.5) * 0.6;
                x += Math.cos(a);
                z += Math.sin(a);
                passo++;
                int bx = (int) Math.round(x), bz = (int) Math.round(z);
                if (PISO_BASE.contains(p.get(bx, 0, bz))) {
                    p.set(bx, 0, bz, passo % 7 == 0 ? LUZ : Material.LIME_CONCRETE);
                }
                // Às vezes o veio engrossa pro lado.
                if (random.nextInt(3) == 0) {
                    int lx = bx + (random.nextBoolean() ? 1 : -1);
                    if (PISO_BASE.contains(p.get(lx, 0, bz))) p.set(lx, 0, bz, Material.GREEN_CONCRETE);
                }
            }
        }
    }

    /** Grade de froglights no chão — sem isso o meio da arena escurece e nasce mob. */
    private void gerarLuzesDoPiso(Planta p) {
        for (int x = -RAIO_PISO; x <= RAIO_PISO; x += 7) {
            for (int z = -RAIO_PISO + 3; z <= RAIO_PISO; z += 7) {
                if (pisoLivre(p, x, z)) p.set(x, 0, z, LUZ);
            }
        }
    }

    // ------------------------------------------------------------------ coberturas

    /** Torre de reator quebrada: cilindro de raio ~3,9 com anel de perigo, faixa de vidro verde e topo irregular. */
    private void gerarTorre(Planta p, int cx, int cz, int altura) {
        Random random = new Random(cx * 7L + cz * 13L);
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int z = cz - 4; z <= cz + 4; z++) {
                double d = dist(x - cx, z - cz);
                if (d > 3.9) continue;
                boolean nucleo = d <= 2.0;
                int topo = nucleo ? altura : altura - random.nextInt(5);
                int fatia = (int) (angulo(x - cx, z - cz) / (2 * Math.PI) * 12);
                p.set(x, 0, z, ESCURO);
                for (int y = 1; y <= topo; y++) {
                    Material m;
                    if (y <= 3) m = fatia % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
                    else if (nucleo) m = y >= 7 && y <= 9 ? LUZ : NERVURA;
                    else if (y >= 7 && y <= 9) m = JANELA;
                    else if (y == 5 || y == 11) m = COBRE;
                    else m = random.nextInt(5) == 0 ? Material.CRACKED_DEEPSLATE_TILES : NERVURA;
                    p.set(x, y, z, m);
                }
                if (nucleo && Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) p.set(x, topo, z, LUZ);
            }
        }
        // Entulho caído ao pé da torre.
        for (int i = 0; i < 8; i++) {
            int x = cx + random.nextInt(13) - 6, z = cz + random.nextInt(13) - 6;
            if (pisoLivre(p, x, z)) {
                p.set(x, 1, z, random.nextBoolean() ? Material.COBBLED_DEEPSLATE.createBlockData() : laje(Material.COBBLED_DEEPSLATE_SLAB, false));
            }
        }
    }

    /** Quatro contêineres 4x4x10 (girados 90° entre si); dois têm outro menor empilhado em cima. */
    private void gerarContaineres(Planta p) {
        Material[] cores = {Material.GREEN_TERRACOTTA, Material.WAXED_EXPOSED_CUT_COPPER, Material.GRAY_TERRACOTTA, Material.LIME_TERRACOTTA};
        for (int q = 0; q < 4; q++) {
            // Base no primeiro quadrante: x 30..39, z 9..12 (comprido em x).
            conteiner(p, 30, 39, 9, 12, 1, 4, q, cores[q]);
        }
        conteiner(p, 31, 38, 9, 12, 5, 8, 1, Material.WAXED_EXPOSED_CUT_COPPER);
        conteiner(p, 32, 37, 9, 12, 5, 8, 3, Material.GREEN_TERRACOTTA);
    }

    private void conteiner(Planta p, int x1, int x2, int z1, int z2, int y1, int y2, int quartos, Material cor) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean ponta = x == x1 || x == x2;
                boolean lateral = z == z1 || z == z2;
                for (int y = y1; y <= y2; y++) {
                    // Nervuras verticais nas laterais lembram chapa corrugada.
                    Material m = ponta ? Material.IRON_BLOCK : (x % 2 == 0 && lateral) ? Material.POLISHED_BLACKSTONE : cor;
                    int[] c = girar(x, z, quartos);
                    p.set(c[0], y, c[1], m);
                }
            }
        }
        // Luzes no teto pra não nascer mob em cima.
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            int[] c = girar(x, (z1 + z2) / 2, quartos);
            p.set(c[0], y2, c[1], LUZ);
        }
    }

    /**
     * Barreiras em L de 3 de altura viradas pro centro (uma por quadrante) e uma barreira reta no anel
     * do meio de cada quadrante, todas com listra de perigo no topo.
     */
    private void gerarBarreiras(Planta p) {
        for (int q = 0; q < 4; q++) {
            for (int i = 9; i <= 15; i++) {
                barreira(p, girar(i, 15, q));
                barreira(p, girar(15, i, q));
            }
            // Reta de 7, perpendicular ao raio, a 70° do eixo e 31 do centro.
            double a = Math.toRadians(70 + 90 * q);
            double cx = Math.cos(a) * 31, cz = Math.sin(a) * 31;
            for (double t = -3; t <= 3; t += 0.5) {
                barreira(p, new int[]{(int) Math.round(cx - Math.sin(a) * t), (int) Math.round(cz + Math.cos(a) * t)});
            }
        }
    }

    private void barreira(Planta p, int[] c) {
        p.set(c[0], 1, c[1], Material.SMOOTH_STONE);
        p.set(c[0], 2, c[1], Material.SMOOTH_STONE);
        p.set(c[0], 3, c[1], perigo(c[0] + c[1], 0));
    }

    /** Pilhas de caixotes (barris e pedra lisa) no anel do meio, uma por quadrante. */
    private void gerarCaixotes(Planta p) {
        BlockData barril = direcional(Material.BARREL, BlockFace.UP);
        for (int q = 0; q < 4; q++) {
            for (int x = 20; x <= 22; x++) {
                for (int z = -14; z <= -13; z++) {
                    int[] c = girar(x, z, q);
                    boolean alto = x == 20;
                    for (int y = 1; y <= (alto ? 3 : 2); y++) {
                        p.set(c[0], y, c[1], (x + z + y) % 2 == 0 ? barril : Material.SMOOTH_STONE.createBlockData());
                    }
                }
            }
        }
    }

    /** Grupos de tambores de resíduo (amarelos, verdes e de cobre), um tombado com lodo vazando. */
    private void gerarTambores(Planta p) {
        Random random = new Random(7);
        int[][] grupos = {{-8, -38}, {20, -41}, {36, -22}};
        Material[] cores = {Material.YELLOW_TERRACOTTA, Material.LIME_CONCRETE, Material.WAXED_COPPER_BLOCK, Material.YELLOW_CONCRETE};
        for (int q = 0; q < 4; q++) {
            for (int[] g : grupos) {
                int[] c = girar(g[0], g[1], q);
                for (int i = 0; i < 6; i++) {
                    int x = c[0] + random.nextInt(3) - 1, z = c[1] + random.nextInt(3) - 1;
                    if (!pisoLivre(p, x, z)) continue;
                    Material cor = cores[random.nextInt(cores.length)];
                    p.set(x, 1, z, cor);
                    p.set(x, 2, z, cor);
                    p.set(x, 3, z, Material.HEAVY_WEIGHTED_PRESSURE_PLATE); // tampa
                }
                // Tambor tombado e mancha de lodo no chão.
                int tx = c[0] + 2, tz = c[1];
                if (pisoLivre(p, tx, tz) && pisoLivre(p, tx + 1, tz)) {
                    p.set(tx, 1, tz, Material.YELLOW_TERRACOTTA);
                    p.set(tx + 1, 1, tz, Material.YELLOW_TERRACOTTA);
                    for (int dz = -1; dz <= 1; dz++) {
                        if (pisoLivre(p, tx + 2, tz + dz)) p.set(tx + 2, 1, tz + dz, Material.LIME_CARPET);
                    }
                }
            }
        }
    }

    /** Pedaços de entulho espalhados pelo piso (semente fixa: sai igual toda vez). */
    private void gerarEntulho(Planta p) {
        Random random = new Random(99);
        BlockData[] pedacos = {
                Material.COBBLED_DEEPSLATE.createBlockData(),
                laje(Material.COBBLED_DEEPSLATE_SLAB, false),
                laje(Material.TUFF_BRICK_SLAB, false),
                Material.TUFF.createBlockData(),
                laje(Material.POLISHED_BLACKSTONE_BRICK_SLAB, false)
        };
        for (int i = 0; i < 160; i++) {
            int x = random.nextInt(RAIO_PISO * 2) - RAIO_PISO;
            int z = random.nextInt(RAIO_PISO * 2) - RAIO_PISO;
            if (dist(x, z) < 13 || !pisoLivre(p, x, z)) continue;
            p.set(x, 1, z, pedacos[random.nextInt(pedacos.length)]);
        }
    }
}
