package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.lanterna;
import static com.example.estruturas.estrutura.Planta.laje;

/**
 * Spawn do servidor em forma de coliseu de primavera (inspirado no "Spring Colosseum" da BuiltByBit):
 * praça redonda de grama florida, toda cercada pela parede do coliseu (arcos em dois andares,
 * arquibancada e muralha externa — nada escalável), com fonte e pavilhão no centro onde o jogador
 * nasce, quatro avenidas, cerejeiras com bancos e barracas de loja (NPC atrás do balcão).
 *
 * Três saídas: o portão LESTE (letreiro "LOJA") leva à Loja Oficial (estrutura "loja"), do lado de
 * fora da muralha. E as duas de sempre: o portão ao NORTE (letreiro "MINAS" por dentro e "SPAWN" por fora), com o
 * túnel atravessando a muralha direto pra avenida das minas, e o portão ao SUL, igual e espelhado
 * (letreiro "BOSS"), que leva à arena do boss. As duas encostam na face de fora dos portões
 * (|z| = 63): o spawn não escreve nada além disso, pra não apagar as vizinhas.
 *
 * O conteúdo de cada quadrante (barracas, árvore, bancos, postes) é descrito uma vez no quadrante
 * nordeste (x > 0, z < 0) e girado 90° pros outros três.
 */
public class ArenaSpringColiseu implements Estrutura {

    // Raios (medidos do centro do bloco 0,0).
    private static final double R_PISO = 44.5;       // praça: ~89 de diâmetro
    private static final double R_FACE = 45.5;       // fachada interna (arcos)
    private static final double R_PASSARELA = 46.5;  // corredor na frente da arquibancada
    private static final double R_PASSEIO = 51.5;    // fim das fileiras
    private static final double R_MURO = 53.5;       // muralha externa
    private static final double R_EXT = 54.5;

    private static final int ALTURA = 16;            // topo da parede interna
    private static final int FILEIRAS = 5;
    private static final int Y_PASSEIO = ALTURA + FILEIRAS; // 21
    private static final int Y_MURO = Y_PASSEIO + 1;
    private static final int Y_LIMPAR = 26;

    private static final int ARCOS = 36;             // divisível por 4: o portão cai no meio de um vão
    private static final double R_ARCO_INT = 45;
    private static final double R_ARCO_EXT = 54;
    private static final double VAO_INT = 2.5;
    private static final double VAO_EXT = 3.0;

    // Praça.
    private static final double R_ILHA = 3.5;
    private static final double R_LAGO = 5.5;
    private static final double R_BORDA_LAGO = 6.5;
    private static final double R_ANEL_A = 9.5;      // anel calçado em volta da fonte
    private static final int MEIA_AVENIDA = 3;       // avenidas de 7 de largura
    private static final double R_ANEL_C_INI = 38.5; // caminho rente à parede
    private static final double R_ANEL_C_FIM = 41.5;

    // Portão norte (z negativo).
    private static final int MEIO_TUNEL = 3;
    private static final int Z_FACE_INT = -44;
    private static final int Z_FACE_EXT = -63;
    // O spawn acaba na face de fora dos portões: a avenida das minas e a arena do boss encostam direto
    // nela, sem caminho no meio. Nada da estrutura passa de |z| = 63.
    private static final int Z_FIM_CAMINHO = Z_FACE_EXT;
    private static final int TERRENO_X = 57;       // o terreno de fora vai até aqui em x (cobre a muralha inteira)

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material FAIXA = Material.SMOOTH_STONE;
    private static final Material VERMELHO = Material.RED_NETHER_BRICKS;
    private static final Material CALCADA = Material.POLISHED_ANDESITE;

    // ------------------------------------------------------------------ barracas

    /**
     * Uma barraca já posicionada. {@code pos1}/{@code pos2} são os cantos do cubo inteiro (piso,
     * balcão, paredes, toldo); {@code npc} é o bloco onde o vendedor fica em pé (y = 1) e
     * {@code olhando} pra onde ele deve olhar (o caminho).
     */
    public record Barraca(String id, boolean especial, int[] pos1, int[] pos2, int[] npc, BlockFace olhando) {}

    /** Barraca no quadrante nordeste: miolo (x1..x2, z1..z2) e lado aberto. */
    private record Modelo(int x1, int z1, int x2, int z2, BlockFace frente, boolean especial) {}

    private static final Modelo[] MODELOS = {
            // Fileira da avenida norte, abertas pro oeste (miolo 2 de fundo x 3 de frente).
            new Modelo(5, -14, 6, -12, BlockFace.WEST, false),
            new Modelo(5, -20, 6, -18, BlockFace.WEST, false),
            new Modelo(5, -26, 6, -24, BlockFace.WEST, false),
            new Modelo(5, -32, 6, -30, BlockFace.WEST, false),
            // Fileira da avenida leste, abertas pro sul.
            new Modelo(18, -6, 20, -5, BlockFace.SOUTH, false),
            new Modelo(24, -6, 26, -5, BlockFace.SOUTH, false),
            new Modelo(30, -6, 32, -5, BlockFace.SOUTH, false),
            // Especial: miolo 5 de frente x 4 de fundo, perto da fonte, aberta pra avenida leste.
            new Modelo(10, -8, 14, -5, BlockFace.SOUTH, true),
    };

    private static final Material[][] TOLDOS = {
            {Material.WHITE_WOOL, Material.PINK_WOOL}, {Material.WHITE_WOOL, Material.RED_WOOL},
            {Material.YELLOW_WOOL, Material.WHITE_WOOL}, {Material.LIGHT_BLUE_WOOL, Material.WHITE_WOOL},
            {Material.LIME_WOOL, Material.WHITE_WOOL}, {Material.MAGENTA_WOOL, Material.WHITE_WOOL},
            {Material.ORANGE_WOOL, Material.WHITE_WOOL}, {Material.CYAN_WOOL, Material.WHITE_WOOL},
    };
    private static final Material[][] TOLDOS_ESPECIAIS = {
            {Material.PINK_WOOL, Material.MAGENTA_WOOL}, {Material.YELLOW_WOOL, Material.ORANGE_WOOL},
            {Material.LIGHT_BLUE_WOOL, Material.PURPLE_WOOL}, {Material.LIME_WOOL, Material.GREEN_WOOL},
    };

    private static final int ALT_NORMAL = 3;   // altura livre por dentro
    private static final int ALT_ESPECIAL = 4;

    /** Todas as barracas (b1..b28 normais, e1..e4 especiais) em coordenadas relativas ao centro. */
    public static final List<Barraca> BARRACAS = Collections.unmodifiableList(calcularBarracas());

    @Override
    public String getId() {
        return "spring";
    }

    @Override
    public String getNome() {
        return "Spawn Primavera";
    }

    /**
     * Cadastra as barracas no BarracasPlugin: a área, o ponto do NPC (atrás do balcão, olhando pro
     * caminho) e o holograma de entrada em cima do toldo, onde não atrapalha o clique no vendedor.
     * O jogador nasce em (0, 1, 0), no pavilhão da fonte — o /setspawn fica com a staff.
     */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return comandosDasBarracas(mundo, ox, oy, oz);
    }

    /** Usado também pela "spring-barracas". Barraca que já existe não é recadastrada (o aluguel dela fica). */
    static List<String> comandosDasBarracas(String mundo, int ox, int oy, int oz) {
        List<String> comandos = new ArrayList<>();
        for (Barraca b : BARRACAS) {
            int[] a = b.pos1(), c = b.pos2(), n = b.npc();
            comandos.add("barraca criar " + b.id() + (b.especial() ? " especial" : "") + " em " + mundo
                    + " " + (ox + a[0]) + " " + (oy + a[1]) + " " + (oz + a[2])
                    + " " + (ox + c[0]) + " " + (oy + c[1]) + " " + (oz + c[2]));
            comandos.add("barraca npc " + b.id() + " em " + mundo + " " + (ox + n[0]) + " " + (oy + n[1]) + " " + (oz + n[2])
                    + " " + yaw(b.olhando()));
            comandos.add("barraca holograma " + b.id() + " em " + mundo + " " + (ox + n[0]) + " "
                    + (oy + Math.max(a[1], c[1]) + 1) + " " + (oz + n[2]));
        }
        return comandos;
    }

    private static int yaw(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            default -> -90;
        };
    }

    @Override
    public void gerar(Planta p) {
        limparEFundar(p);
        gerarAnel(p);
        gerarPiso(p);
        gerarFonte(p);
        gerarPortao(p);
        // o portão do boss é o das minas espelhado no eixo z, com os letreiros reescritos (espelhados, sairiam ao contrário)
        gerarPortao(new EspelhoZ(p));
        letreirosDoPortaoSul(p);
        // o da loja é o das minas girado 90° (norte vira leste), com o letreiro "LOJA" por dentro
        RotacaoLeste leste = new RotacaoLeste(p);
        gerarPortao(leste);
        letreirosDoPortaoLeste(leste);
        for (int k = 0; k < 4; k++) gerarQuadrante(p, k);
        gerarBarracas(p);
        gerarFimDasAvenidas(p);
        gerarFlores(p);
        gerarLiquens(p);
        gerarJardinsNoTopo(p);
    }

    // ------------------------------------------------------------------ utilidades

    private static double raio(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /** Distância (em blocos, no raio de referência) até o centro do vão de arco mais próximo. */
    private static double distVao(int x, int z, double raioRef) {
        double g = Math.atan2(z, x) / (2 * Math.PI) * ARCOS;
        double frac = g - Math.floor(g);
        return Math.min(frac, 1 - frac) * 2 * Math.PI * raioRef / ARCOS;
    }

    private static int topoArco(double d, double meio, int topo) {
        if (d < meio - 1.0) return topo;
        if (d < meio - 0.4) return topo - 1;
        return topo - 2;
    }

    /** Tijolo de pedra misturado com musgo e rachado, sempre igual na mesma posição. */
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

    private static BlockData metade(Material material, boolean cima) {
        Bisected dados = (Bisected) material.createBlockData();
        dados.setHalf(cima ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        return dados;
    }

    private static BlockData alcapaoAberto(Material material, BlockFace face) {
        TrapDoor dados = (TrapDoor) material.createBlockData();
        dados.setFacing(face);
        dados.setOpen(true);
        return dados;
    }

    /** Direção cardeal predominante de (x, z) — pra fora do centro. */
    private static BlockFace paraFora(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) return x >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return z >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Gira (x, z) 90° no sentido horário (visto de cima) {@code k} vezes: NE → SE → SO → NO. */
    private static int[] girar(int k, int x, int z) {
        for (int i = 0; i < k; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[]{x, z};
    }

    private static BlockFace girar(int k, BlockFace face) {
        for (int i = 0; i < k; i++) {
            face = switch (face) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                default -> BlockFace.NORTH;
            };
        }
        return face;
    }

    private static boolean naAvenida(int x, int z) {
        return Math.abs(x) <= MEIA_AVENIDA || Math.abs(z) <= MEIA_AVENIDA;
    }

    // ------------------------------------------------------------------ base

    /** A área de fora de um dos portões (norte e sul, espelhados, e o leste, girado). */
    private static boolean naAreaDoPortao(int x, int z) {
        boolean norteSul = Math.abs(x) <= 18 && Math.abs(z) >= 48 && Math.abs(z) <= -Z_FIM_CAMINHO;
        boolean leste = Math.abs(z) <= 18 && x >= 48 && x <= -Z_FIM_CAMINHO;
        return norteSul || leste;
    }

    /**
     * Planta girada 90° no sentido horário (visto de cima): o que é escrito "ao norte" (z negativo)
     * sai a leste (x positivo), com a orientação dos blocos girada junto.
     */
    private static final class RotacaoLeste extends Planta {
        private final Planta destino;

        RotacaoLeste(Planta destino) {
            this.destino = destino;
        }

        @Override
        public void set(int x, int y, int z, BlockData dados) {
            BlockData girado = dados.clone();
            girado.rotate(StructureRotation.CLOCKWISE_90);
            destino.set(-z, y, x, girado);
        }

        @Override
        public Material get(int x, int y, int z) {
            return destino.get(-z, y, x);
        }
    }

    /** "LOJA" lido de dentro e "SPAWN" lido de quem volta da loja (na planta girada, igual ao norte). */
    private void letreirosDoPortaoLeste(Planta p) {
        painel(p, Z_FACE_INT);
        escrever(p, "LOJA", -7, 1, 16, Z_FACE_INT);
        painel(p, Z_FACE_EXT);
        escrever(p, "SPAWN", 11, -1, 16, Z_FACE_EXT);
    }

    /** Planta que grava tudo espelhado no eixo z (norte vira sul), com a orientação dos blocos espelhada junto. */
    private static final class EspelhoZ extends Planta {
        private final Planta destino;

        EspelhoZ(Planta destino) {
            this.destino = destino;
        }

        @Override
        public void set(int x, int y, int z, BlockData dados) {
            BlockData espelhado = dados.clone();
            espelhado.mirror(Mirror.LEFT_RIGHT);
            destino.set(x, y, -z, espelhado);
        }

        @Override
        public Material get(int x, int y, int z) {
            return destino.get(x, y, -z);
        }
    }

    /** "BOSS" lido de dentro (olhando pro sul, a direita é -x) e "SPAWN" lido de quem chega da arena. */
    private void letreirosDoPortaoSul(Planta p) {
        painel(p, -Z_FACE_INT);
        escrever(p, "BOSS", 7, -1, 16, -Z_FACE_INT);
        painel(p, -Z_FACE_EXT);
        escrever(p, "SPAWN", -11, 1, 16, -Z_FACE_EXT);
    }

    /**
     * Limpa e assenta o chão. Fora da muralha vai grama com arbustos até completar o quadrado do
     * terreno (|x| <= TERRENO_X, |z| <= -Z_FIM_CAMINHO): assim não sobra vazio entre o spawn redondo e
     * o que encosta nos portões (a avenida das minas ao norte e a arena do boss ao sul).
     */
    private void limparEFundar(Planta p) {
        int lim = 72;
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                double r = raio(x, z);
                boolean terreno = Math.abs(x) <= TERRENO_X && Math.abs(z) <= -Z_FIM_CAMINHO;
                if (r >= R_EXT + 2.5 && !naAreaDoPortao(x, z) && !terreno) continue;
                for (int y = 1; y <= Y_LIMPAR; y++) p.set(x, y, z, Material.AIR);
                p.set(x, -1, z, Material.DIRT);
                if (r >= R_EXT) p.set(x, 0, z, r < R_EXT + 1.5 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
                if (r >= R_EXT + 2.5 && !naAreaDoPortao(x, z)) enfeiteDoTerreno(p, x, z);
            }
        }
    }

    /** Arbusto, grama ou flor no terreno de fora (só enfeite: ninguém chega lá). */
    private void enfeiteDoTerreno(Planta p, int x, int z) {
        int sorte = Math.floorMod((x * 73856093) ^ (z * 19349663), 100);
        if (sorte < 4) {
            p.set(x, 1, z, folha(sorte < 2 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
        } else if (sorte < 20) {
            p.set(x, 1, z, Material.SHORT_GRASS);
        } else if (sorte < 24) {
            p.set(x, 1, z, sorte < 22 ? Material.DANDELION : Material.POPPY);
        }
    }

    // ------------------------------------------------------------------ anel (paredes + arquibancada)

    private void gerarAnel(Planta p) {
        int lim = (int) Math.ceil(R_EXT);
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                double r = raio(x, z);
                if (r < R_PISO || r >= R_EXT) continue;
                if (r < R_FACE) fachadaInterna(p, x, z);
                else if (r < R_PASSARELA) fundoEPassarela(p, x, z);
                else if (r < R_PASSEIO) fileira(p, x, z, (int) Math.floor(r - R_PASSARELA));
                else if (r < R_MURO) passeio(p, x, z, r);
                else muralha(p, x, z);
            }
        }
        // Mísula lisa na beira da praça entre os dois andares de arcos.
        int lim2 = (int) Math.ceil(R_PISO);
        for (int x = -lim2; x <= lim2; x++) {
            for (int z = -lim2; z <= lim2; z++) {
                double r = raio(x, z);
                if (r >= R_PISO - 1 && r < R_PISO) p.set(x, 9, z, laje(Material.SMOOTH_STONE_SLAB, true));
            }
        }
    }

    /** Pilares e vãos de arco em dois andares, faixas lisas e parapeito com lanternas. */
    private void fachadaInterna(Planta p, int x, int z) {
        double d = distVao(x, z, R_ARCO_INT);
        boolean vao = d < VAO_INT;
        for (int y = 0; y <= ALTURA + 1; y++) {
            Material m = (y == 1 || y == 9 || y == ALTURA) ? FAIXA : pedra(x, y, z);
            if (vao && y >= 2 && y <= topoArco(d, VAO_INT, 8)) m = Material.AIR;
            if (vao && y >= 10 && y <= topoArco(d, VAO_INT, 15)) m = Material.AIR;
            p.set(x, y, z, m);
        }
        if (d > VAO_INT + 0.7) p.set(x, ALTURA + 2, z, lanterna(Material.LANTERN, false));
    }

    /** Fundo dos arcos (cerejeira embaixo, azaleia florida em cima, luz no meio) e o corredor. */
    private void fundoEPassarela(Planta p, int x, int z) {
        double d = distVao(x, z, R_ARCO_INT);
        for (int y = 0; y < ALTURA; y++) p.set(x, y, z, pedra(x, y, z));
        if (d < VAO_INT) {
            for (int y = 2; y <= 8; y++) p.set(x, y, z, Material.CHERRY_PLANKS);
            for (int y = 10; y <= 15; y++) p.set(x, y, z, folha(Material.FLOWERING_AZALEA_LEAVES));
            if (d < 0.5) {
                p.set(x, 4, z, Material.SHROOMLIGHT);
                p.set(x, 12, z, Material.SHROOMLIGHT);
            }
        }
        p.set(x, ALTURA, z, FAIXA);
    }

    /** Fileira k da arquibancada (decorativa): maciço até 16+k e assento virado pro centro. */
    private void fileira(Planta p, int x, int z, int k) {
        for (int y = 0; y <= ALTURA + k; y++) p.set(x, y, z, pedra(x, y, z));
        int setor = (int) Math.floor(Math.atan2(z, x) / (2 * Math.PI) * ARCOS + 0.5);
        Material assento = Math.floorMod(setor, 2) == 0 ? Material.CHERRY_STAIRS : Material.POLISHED_ANDESITE_STAIRS;
        p.set(x, ALTURA + k + 1, z, escada(assento, paraFora(x, z), false));
    }

    /** Passeio no alto (com luzes), e o fundo dos arcos da muralha externa. */
    private void passeio(Planta p, int x, int z, double r) {
        for (int y = 0; y <= Y_PASSEIO; y++) p.set(x, y, z, pedra(x, y, z));
        p.set(x, Y_PASSEIO, z, FAIXA);
        if (r < R_PASSEIO + 1 && distVao(x, z, R_ARCO_INT) < 0.5) p.set(x, Y_PASSEIO, z, Material.SHROOMLIGHT);
        if (r >= R_MURO - 1 && distVao(x, z, R_ARCO_EXT) < VAO_EXT) {
            for (int y = 2; y <= 7; y++) p.set(x, y, z, Material.CHERRY_PLANKS);
            for (int y = 9; y <= 14; y++) p.set(x, y, z, Material.MUD_BRICKS);
            for (int y = 16; y <= 20; y++) p.set(x, y, z, folha(Material.CHERRY_LEAVES));
        }
    }

    /** Muralha externa: três andares de arcos cegos, parapeito e ameias. */
    private void muralha(Planta p, int x, int z) {
        double d = distVao(x, z, R_ARCO_EXT);
        boolean vao = d < VAO_EXT;
        for (int y = 0; y <= Y_MURO; y++) {
            Material m = (y == 1 || y == 8 || y == 15 || y == 21) ? FAIXA : pedra(x, y, z);
            if (vao && y >= 2 && y <= topoArco(d, VAO_EXT, 7)) m = Material.AIR;
            if (vao && y >= 9 && y <= topoArco(d, VAO_EXT, 14)) m = Material.AIR;
            if (vao && y >= 16 && y <= topoArco(d, VAO_EXT, 20)) m = Material.AIR;
            p.set(x, y, z, m);
        }
        if (!vao) p.set(x, Y_MURO + 1, z, pedra(x, Y_MURO + 1, z));
    }

    // ------------------------------------------------------------------ piso

    private void gerarPiso(Planta p) {
        Random random = new Random(7);
        int[][] manchas = new int[30][4];
        for (int[] m : manchas) {
            m[0] = random.nextInt(81) - 40;
            m[1] = random.nextInt(81) - 40;
            m[2] = 1 + random.nextInt(3);
            m[3] = random.nextInt(2);
        }
        int lim = (int) Math.ceil(R_PISO);
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                double r = raio(x, z);
                if (r >= R_PISO) continue;
                Material m = Material.GRASS_BLOCK;
                for (int[] mancha : manchas) {
                    int dx = x - mancha[0], dz = z - mancha[1];
                    if (dx * dx + dz * dz <= mancha[2] * mancha[2]) {
                        m = mancha[3] == 0 ? Material.MOSS_BLOCK : Material.COARSE_DIRT;
                    }
                }
                // nas avenidas com portão (minas, boss e loja) o calçamento vai direto até a boca do
                // túnel, sem o anel de terra batida e a borda da praça cruzando no meio
                boolean avenidaComPortao = (Math.abs(x) <= MEIA_AVENIDA && Math.abs(z) > MEIA_AVENIDA)
                        || (Math.abs(z) <= MEIA_AVENIDA && x > MEIA_AVENIDA);
                if (avenidaComPortao && r >= R_ANEL_C_INI) m = calcamento(x, z);
                else if (r >= R_PISO - 1) m = pedra(x, 0, z);
                else if (r >= R_ANEL_C_INI && r < R_ANEL_C_FIM) {
                    m = Math.floorMod(x * 7 + z * 13, 9) == 0 ? Material.MOSSY_COBBLESTONE : Material.DIRT_PATH;
                } else if (r < R_ANEL_A) {
                    m = r >= R_ANEL_A - 1 ? PEDRA : CALCADA;
                } else if (naAvenida(x, z)) {
                    m = calcamento(x, z);
                }
                p.set(x, 0, z, m);
            }
        }

        // Grade de luzes no chão a cada 8 blocos: nenhum ponto da praça fica no escuro.
        for (int x = -44; x <= 44; x += 8) {
            for (int z = -44; z <= 44; z += 8) {
                double r = raio(x, z);
                if (r > R_BORDA_LAGO && r < R_PISO - 1.5) p.set(x, 0, z, Material.SHROOMLIGHT);
            }
        }
    }

    /** Calçamento das avenidas: andesito polido, bordas de tijolo e faixas lisas a cada 6. */
    /** O mesmo calçamento das avenidas, no túnel (x é a largura, z o comprimento). */
    private static Material calcamentoDoTunel(int x, int z) {
        if (Math.abs(x) == MEIO_TUNEL) return PEDRA;
        return Math.floorMod(z, 6) == 0 ? FAIXA : CALCADA;
    }

    private static Material calcamento(int x, int z) {
        int t = Math.abs(x) <= MEIA_AVENIDA && Math.abs(z) > MEIA_AVENIDA ? x : z;
        int a = Math.abs(x) <= MEIA_AVENIDA && Math.abs(z) > MEIA_AVENIDA ? z : x;
        if (Math.abs(t) == MEIA_AVENIDA) return PEDRA;
        if (Math.floorMod(a, 6) == 0) return FAIXA;
        return CALCADA;
    }

    /** Fonte central: ilha com pavilhão de cerejeira (onde o jogador nasce), lago e quatro pontes. */
    private void gerarFonte(Planta p) {
        int lim = (int) Math.ceil(R_BORDA_LAGO);
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                double r = raio(x, z);
                if (r >= R_BORDA_LAGO) continue;
                boolean ponte = Math.abs(x) <= 1 || Math.abs(z) <= 1;
                p.set(x, -1, z, Material.STONE);
                if (r < R_ILHA) {
                    p.set(x, 0, z, (x + z) % 2 == 0 ? CALCADA : Material.POLISHED_DIORITE);
                } else if (r < R_LAGO) {
                    if (ponte) {
                        p.set(x, 0, z, PEDRA);
                    } else {
                        p.set(x, -1, z, (x * x + z * z) % 3 == 0 ? Material.SEA_LANTERN : Material.MOSSY_STONE_BRICKS);
                        p.set(x, 0, z, Material.WATER);
                    }
                } else {
                    p.set(x, 0, z, FAIXA);
                    if (!ponte) p.set(x, 1, z, laje(Material.STONE_BRICK_SLAB, false));
                }
            }
        }
        p.set(0, 0, 0, Material.CHISELED_STONE_BRICKS);

        // Vasos de azaleia florida nas diagonais da borda.
        for (int sx : new int[]{-4, 4}) {
            for (int sz : new int[]{-4, 4}) {
                p.set(sx, 0, sz, Material.MOSS_BLOCK);
                p.set(sx, 1, sz, folha(Material.FLOWERING_AZALEA_LEAVES));
            }
        }

        // Pavilhão: quatro pilares de cerejeira, teto de tábuas e copa de folhas em cima.
        for (int sx : new int[]{-2, 2}) {
            for (int sz : new int[]{-2, 2}) {
                for (int y = 1; y <= 5; y++) p.set(sx, y, sz, eixo(Material.CHERRY_LOG, Axis.Y));
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double r = raio(x, z);
                if (r < 3.5) p.set(x, 6, z, Material.CHERRY_PLANKS);
                if (r < 3.5 && r >= 2.5) p.set(x, 5, z, laje(Material.CHERRY_SLAB, true));
                if (r < 2.9) p.set(x, 7, z, folha(Material.CHERRY_LEAVES));
                if (r < 1.9) p.set(x, 8, z, folha(Material.FLOWERING_AZALEA_LEAVES));
            }
        }
        for (int sx : new int[]{-2, 2}) for (int sz : new int[]{-2, 2}) p.set(sx, 5, sz, eixo(Material.CHERRY_LOG, Axis.Y));
        p.set(0, 6, 0, Material.PEARLESCENT_FROGLIGHT);
        p.set(0, 5, 0, lanterna(Material.LANTERN, true));
    }

    // ------------------------------------------------------------------ portão norte (saída pras minas)

    private void gerarPortao(Planta p) {
        // Frontispício chato por dentro, cobrindo a curva da parede.
        for (int x = -14; x <= 14; x++) {
            for (int z = Z_FACE_INT - 4; z <= Z_FACE_INT; z++) {
                for (int y = 0; y <= 20; y++) {
                    Material m = (y == 1 || y == 18 || y == 20) ? FAIXA : pedra(x, y, z);
                    p.set(x, y, z, m);
                }
                boolean beirada = z == Z_FACE_INT || z == Z_FACE_INT - 4 || Math.abs(x) == 14;
                if (beirada && Math.floorMod(x + z, 2) == 0) p.set(x, 21, z, pedra(x, 21, z));
            }
        }
        for (int x : new int[]{-12, 0, 12}) p.set(x, 21, Z_FACE_INT - 2, lanterna(Material.LANTERN, false));

        // Casa do portão do lado de fora, com duas torres.
        for (int x = -12; x <= 12; x++) {
            for (int z = Z_FACE_EXT; z <= -50; z++) {
                if (raio(x, z) < R_MURO - 1) continue;
                for (int y = 0; y <= 18; y++) {
                    Material m = (y == 1 || y == 18) ? FAIXA : (y == 10 && z == Z_FACE_EXT ? VERMELHO : pedra(x, y, z));
                    p.set(x, y, z, m);
                }
                boolean beirada = z == Z_FACE_EXT || Math.abs(x) == 12;
                if (beirada && Math.floorMod(x + z, 2) == 0) p.set(x, 19, z, pedra(x, 19, z));
            }
        }
        for (int x : new int[]{-8, 0, 8}) {
            for (int z : new int[]{-56, -60}) p.set(x, 19, z, lanterna(Material.LANTERN, false));
        }
        for (int s : new int[]{-1, 1}) {
            for (int x = 13; x <= 16; x++) {
                for (int z = Z_FACE_EXT; z <= -56; z++) {
                    int bx = s * x;
                    for (int y = 0; y <= 22; y++) {
                        Material m = (y == 1 || y == 22) ? FAIXA : pedra(bx, y, z);
                        if ((y == 8 || y == 16) && (x == 16 || z == Z_FACE_EXT)) m = VERMELHO;
                        p.set(bx, y, z, m);
                    }
                    boolean beirada = x == 13 || x == 16 || z == Z_FACE_EXT || z == -56;
                    if (beirada && Math.floorMod(x + z, 2) == 0) p.set(bx, 23, z, pedra(bx, 23, z));
                }
            }
            p.set(s * 14, 23, -59, lanterna(Material.LANTERN, false));
        }

        // Túnel 7 de largura com teto em arco.
        for (int z = Z_FACE_INT - 1; z >= Z_FACE_EXT; z--) {
            for (int x = -MEIO_TUNEL; x <= MEIO_TUNEL; x++) {
                p.set(x, -1, z, Material.DIRT);
                p.set(x, 0, z, calcamentoDoTunel(x, z));
                int topo = Math.abs(x) <= 1 ? 9 : (Math.abs(x) == 2 ? 8 : 7);
                for (int y = 1; y <= topo; y++) p.set(x, y, z, Material.AIR);
                p.set(x, topo + 1, z, PEDRA);
            }
            p.set(-3, 8, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.WEST, true));
            p.set(3, 8, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.EAST, true));
            p.set(-2, 9, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.WEST, true));
            p.set(2, 9, z, escada(Material.STONE_BRICK_STAIRS, BlockFace.EAST, true));
            for (int y = 1; y <= 7; y++) {
                Material parede = Math.floorMod(z, 2) == 0 ? VERMELHO : PEDRA;
                p.set(-4, y, z, parede);
                p.set(4, y, z, parede);
            }
            if (Math.floorMod(z, 5) == 0) p.set(0, 9, z, lanterna(Material.LANTERN, true));
        }
        // Boca do túnel nos dois lados (z = -44 por dentro, z = -63 por fora): chão e abertura.
        for (int x = -MEIO_TUNEL; x <= MEIO_TUNEL; x++) {
            for (int z : new int[]{Z_FACE_INT}) {
                p.set(x, 0, z, calcamentoDoTunel(x, z));
                int topo = Math.abs(x) <= 1 ? 9 : (Math.abs(x) == 2 ? 8 : 7);
                for (int y = 1; y <= topo; y++) p.set(x, y, z, Material.AIR);
            }
        }

        moldura(p, Z_FACE_INT + 1);
        moldura(p, Z_FACE_EXT); // na própria face: do lado de fora já é a outra estrutura

        // Letreiros: "MINAS" lido de dentro (direita = +x), "SPAWN" lido de fora (direita = -x).
        painel(p, Z_FACE_INT);
        escrever(p, "MINAS", -11, 1, 16, Z_FACE_INT);
        painel(p, Z_FACE_EXT);
        escrever(p, "SPAWN", 11, -1, 16, Z_FACE_EXT);
    }

    /** Pilares de caule carmesim e cerejeira, verga de tijolo do nether e luz na pedra-chave. */
    private void moldura(Planta p, int zFrente) {
        for (int y = 1; y <= 9; y++) {
            p.set(-4, y, zFrente, eixo(Material.CRIMSON_STEM, Axis.Y));
            p.set(4, y, zFrente, eixo(Material.CRIMSON_STEM, Axis.Y));
            p.set(-5, y, zFrente, eixo(Material.CHERRY_LOG, Axis.Y));
            p.set(5, y, zFrente, eixo(Material.CHERRY_LOG, Axis.Y));
        }
        for (int x = -5; x <= 5; x++) p.set(x, 10, zFrente, VERMELHO);
        p.set(0, 10, zFrente, Material.SHROOMLIGHT);
        for (int x = -3; x <= 3; x++) {
            if (x != 0) p.set(x, 11, zFrente, laje(Material.RED_NETHER_BRICK_SLAB, false));
        }
        for (int s : new int[]{-1, 1}) {
            p.set(s * 6, 1, zFrente, folha(Material.FLOWERING_AZALEA_LEAVES));
            p.set(s * 7, 1, zFrente, folha(Material.AZALEA_LEAVES));
            p.set(s * 6, 2, zFrente, lanterna(Material.LANTERN, false));
        }
    }

    /** Placa preta com moldura em cima do arco (y 11..17, |x| <= 12). */
    private void painel(Planta p, int z) {
        for (int x = -12; x <= 12; x++) {
            for (int y = 11; y <= 17; y++) {
                boolean borda = Math.abs(x) == 12 || y == 11 || y == 17;
                p.set(x, y, z, borda ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_BLACKSTONE);
            }
        }
    }

    private static final Map<Character, String[]> FONTE = Map.ofEntries(
            Map.entry('M', new String[]{"X...X", "XX.XX", "X.X.X", "X...X", "X...X"}),
            Map.entry('I', new String[]{"XXX", ".X.", ".X.", ".X.", "XXX"}),
            Map.entry('N', new String[]{"X..X", "XX.X", "X.XX", "X..X", "X..X"}),
            Map.entry('A', new String[]{".X.", "X.X", "XXX", "X.X", "X.X"}),
            Map.entry('S', new String[]{"XXX", "X..", "XXX", "..X", "XXX"}),
            Map.entry('P', new String[]{"XXX", "X.X", "XXX", "X..", "X.."}),
            Map.entry('W', new String[]{"X...X", "X...X", "X.X.X", "XX.XX", "X...X"}),
            Map.entry('B', new String[]{"XX.", "X.X", "XX.", "X.X", "XX."}),
            Map.entry('O', new String[]{"XXX", "X.X", "X.X", "X.X", "XXX"}),
            Map.entry('L', new String[]{"X..", "X..", "X..", "X..", "XXX"}),
            Map.entry('J', new String[]{"..X", "..X", "..X", "X.X", "XXX"})
    );

    /** Escreve em pedra luminosa; {@code passo} = +1/-1 conforme a direita de quem lê. */
    private void escrever(Planta p, String texto, int xInicio, int passo, int yTopo, int z) {
        int coluna = 0;
        for (char c : texto.toCharArray()) {
            String[] letra = FONTE.get(c);
            for (int linha = 0; linha < letra.length; linha++) {
                for (int i = 0; i < letra[linha].length(); i++) {
                    if (letra[linha].charAt(i) == 'X') {
                        p.set(xInicio + passo * (coluna + i), yTopo - linha, z, Material.GLOWSTONE);
                    }
                }
            }
            coluna += letra[0].length() + 1;
        }
    }

    // ------------------------------------------------------------------ quadrantes

    /** Árvore com bancos em volta e postes de luz; descritos no NE e girados {@code k} vezes. */
    private void gerarQuadrante(Planta p, int k) {
        Quadrante q = new Quadrante(p, k);
        q.cerejeira(22, -22, 100 + k);
        // Caminho em anel em volta da árvore, com os bancos nele.
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double r = raio(x, z);
                if (r >= 5.5 && r < 7) q.set(22 + x, 0, -22 + z, Material.DIRT_PATH);
            }
        }
        q.banco(22, -28, BlockFace.NORTH);
        q.banco(22, -16, BlockFace.SOUTH);
        q.banco(28, -22, BlockFace.EAST);
        q.banco(16, -22, BlockFace.WEST);

        int[][] postes = {{3, -10}, {3, -16}, {3, -22}, {3, -28}, {3, -34}, {16, -3}, {22, -3}, {28, -3},
                {34, -3}, {30, -14}, {14, -30}, {7, -7}};
        for (int[] pos : postes) q.poste(pos[0], pos[1]);
    }

    /** Escreve na planta girando as coordenadas e as direções do quadrante NE. */
    private final class Quadrante {
        private final Planta p;
        private final int k;

        Quadrante(Planta p, int k) {
            this.p = p;
            this.k = k;
        }

        void set(int x, int y, int z, BlockData dados) {
            int[] g = girar(k, x, z);
            p.set(g[0], y, g[1], dados);
        }

        void set(int x, int y, int z, Material material) {
            set(x, y, z, material.createBlockData());
        }

        Material get(int x, int y, int z) {
            int[] g = girar(k, x, z);
            return p.get(g[0], y, g[1]);
        }

        void poste(int x, int z) {
            int[] g = girar(k, x, z);
            ArenaSpringColiseu.this.poste(p, g[0], g[1]);
        }

        /** Banco de 3 escadas de cerejeira com braços de alçapão; {@code costas} = lado do encosto. */
        void banco(int cx, int cz, BlockFace costas) {
            BlockFace c = girar(k, costas);
            int[] g = girar(k, cx, cz);
            boolean aoLongoDeX = c == BlockFace.NORTH || c == BlockFace.SOUTH;
            for (int i = -1; i <= 1; i++) {
                int x = g[0] + (aoLongoDeX ? i : 0), z = g[1] + (aoLongoDeX ? 0 : i);
                p.set(x, 1, z, escada(Material.CHERRY_STAIRS, c, false));
            }
            for (int s : new int[]{-1, 1}) {
                int x = g[0] + (aoLongoDeX ? 2 * s : 0), z = g[1] + (aoLongoDeX ? 0 : 2 * s);
                BlockFace fora = aoLongoDeX ? (s > 0 ? BlockFace.EAST : BlockFace.WEST)
                        : (s > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
                p.set(x, 1, z, alcapaoAberto(Material.CHERRY_TRAPDOOR, fora));
            }
        }

        /** Cerejeira num monte de musgo: tronco, galhos, copa larga e pétalas no chão. */
        void cerejeira(int cx, int cz, long semente) {
            Random random = new Random(semente);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    double r = raio(x, z);
                    if (r <= 3.2) set(cx + x, 1, cz + z, r <= 2 || random.nextBoolean() ? Material.MOSS_BLOCK : Material.MOSSY_COBBLESTONE);
                    if (r <= 1.5) set(cx + x, 2, cz + z, Material.MOSS_BLOCK);
                }
            }
            for (int y = 3; y <= 9; y++) set(cx, y, cz, eixo(Material.CHERRY_LOG, Axis.Y));
            set(cx + 1, 7, cz, Material.CHERRY_WOOD);
            set(cx + 2, 8, cz, eixo(Material.CHERRY_LOG, Axis.Y));
            set(cx - 1, 8, cz, Material.CHERRY_WOOD);
            set(cx - 2, 9, cz, eixo(Material.CHERRY_LOG, Axis.Y));

            BlockData folhas = folha(Material.CHERRY_LEAVES);
            int[][] camadas = {{9, 4}, {10, 5}, {11, 4}, {12, 2}};
            for (int[] c : camadas) {
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        double r = raio(x, z);
                        if (r > c[1] + 0.3) continue;
                        if (r > c[1] - 0.8 && random.nextInt(3) == 0) continue;
                        Material atual = get(cx + x, c[0], cz + z);
                        if (atual == Material.CHERRY_LOG) continue;
                        set(cx + x, c[0], cz + z, folhas);
                    }
                }
            }
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    double r = raio(x, z);
                    if (r < 3.5 || r > 5.3 || random.nextInt(3) != 0) continue;
                    set(cx + x, 8, cz + z, folhas);
                }
            }
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    if (raio(x, z) > 5 || raio(x, z) < 3.3 || random.nextInt(3) != 0) continue;
                    if (get(cx + x, 0, cz + z) == Material.GRASS_BLOCK && get(cx + x, 1, cz + z) == Material.AIR) {
                        set(cx + x, 1, cz + z, Material.PINK_PETALS);
                    }
                }
            }
        }
    }

    /** Poste: base de pedra, cerca escura e lanterna em cima. */
    private void poste(Planta p, int x, int z) {
        p.set(x, 1, z, Material.STONE_BRICK_WALL);
        p.set(x, 2, z, Material.DARK_OAK_FENCE);
        p.set(x, 3, z, Material.DARK_OAK_FENCE);
        p.set(x, 4, z, lanterna(Material.LANTERN, false));
    }

    // ------------------------------------------------------------------ barracas

    private static List<Barraca> calcularBarracas() {
        List<Barraca> lista = new ArrayList<>();
        List<Barraca> especiais = new ArrayList<>();
        int normais = 0;
        for (int k = 0; k < 4; k++) {
            for (Modelo m : MODELOS) {
                int[] a = girar(k, m.x1(), m.z1());
                int[] b = girar(k, m.x2(), m.z2());
                int x1 = Math.min(a[0], b[0]), x2 = Math.max(a[0], b[0]);
                int z1 = Math.min(a[1], b[1]), z2 = Math.max(a[1], b[1]);
                BlockFace frente = girar(k, m.frente());
                int altura = m.especial() ? ALT_ESPECIAL : ALT_NORMAL;

                // Cubo: miolo + 1 de cada lado (paredes / balcão) + 1 de aba do toldo na frente.
                int cx1 = x1 - 1, cx2 = x2 + 1, cz1 = z1 - 1, cz2 = z2 + 1;
                switch (frente) {
                    case NORTH -> cz1--;
                    case SOUTH -> cz2++;
                    case WEST -> cx1--;
                    default -> cx2++;
                }
                int topo = m.especial() ? altura + 4 : altura + 1;

                // NPC: no meio da fileira do miolo encostada no balcão.
                int nx, nz;
                switch (frente) {
                    case NORTH -> { nx = (x1 + x2) / 2; nz = z1; }
                    case SOUTH -> { nx = (x1 + x2) / 2; nz = z2; }
                    case WEST -> { nx = x1; nz = (z1 + z2) / 2; }
                    default -> { nx = x2; nz = (z1 + z2) / 2; }
                }
                String id = m.especial() ? "e" + (especiais.size() + 1) : "b" + (++normais);
                Barraca barraca = new Barraca(id, m.especial(), new int[]{cx1, 0, cz1}, new int[]{cx2, topo, cz2},
                        new int[]{nx, 1, nz}, frente);
                if (m.especial()) especiais.add(barraca);
                else lista.add(barraca);
            }
        }
        lista.addAll(especiais);
        return lista;
    }

    /** Só as barracas (sem limpar nem mexer no resto do spawn): é o que a estrutura "spring-barracas" usa. */
    public void gerarBarracas(Planta p) {
        int normais = 0, especiais = 0;
        for (Barraca b : BARRACAS) {
            Material[] cores = b.especial() ? TOLDOS_ESPECIAIS[especiais++ % TOLDOS_ESPECIAIS.length]
                    : TOLDOS[normais++ % TOLDOS.length];
            barraca(p, b, cores);
        }
    }

    /**
     * Barraca vazia por dentro: piso de tábua, postes de tronco nos cantos, fundo fechado, laterais
     * de cerca, balcão na frente (barris nas pontas, lajes no meio) e toldo listrado com aba. O NPC
     * fica atrás do balcão; uma luz embutida no meio do teto clareia o miolo e o próprio toldo.
     */
    private void barraca(Planta p, Barraca b, Material[] cores) {
        BlockFace frente = b.olhando();
        int altura = b.especial() ? ALT_ESPECIAL : ALT_NORMAL;
        // Pegada (sem a aba).
        int x1 = b.pos1()[0], x2 = b.pos2()[0], z1 = b.pos1()[2], z2 = b.pos2()[2];
        switch (frente) {
            case NORTH -> z1++;
            case SOUTH -> z2--;
            case WEST -> x1++;
            default -> x2--;
        }
        boolean frenteEmZ = frente == BlockFace.NORTH || frente == BlockFace.SOUTH;
        Material tronco = b.especial() ? Material.CHERRY_LOG : Material.SPRUCE_LOG;
        Material tabua = b.especial() ? Material.CHERRY_PLANKS : Material.SPRUCE_PLANKS;
        Material cerca = b.especial() ? Material.CHERRY_FENCE : Material.SPRUCE_FENCE;
        Material lajeBalcao = b.especial() ? Material.CHERRY_SLAB : Material.SPRUCE_SLAB;

        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                p.set(x, 0, z, tabua);
                for (int y = 1; y <= altura; y++) p.set(x, y, z, Material.AIR);
                boolean bordaX = x == x1 || x == x2, bordaZ = z == z1 || z == z2;
                boolean canto = bordaX && bordaZ;
                boolean naFrente = switch (frente) {
                    case NORTH -> z == z1;
                    case SOUTH -> z == z2;
                    case WEST -> x == x1;
                    default -> x == x2;
                };
                boolean noFundo = switch (frente) {
                    case NORTH -> z == z2;
                    case SOUTH -> z == z1;
                    case WEST -> x == x2;
                    default -> x == x1;
                };
                if (canto) {
                    for (int y = 1; y <= altura; y++) p.set(x, y, z, eixo(tronco, Axis.Y));
                } else if (naFrente) {
                    // Balcão: barril nas pontas (com vaso de flor), laje no meio.
                    int u = frenteEmZ ? x : z, uMin = frenteEmZ ? x1 : z1, uMax = frenteEmZ ? x2 : z2;
                    boolean ponta = u == uMin + 1 || u == uMax - 1;
                    if (ponta) {
                        p.set(x, 1, z, direcional(Material.BARREL, BlockFace.UP));
                        p.set(x, 2, z, Material.POTTED_AZALEA_BUSH);
                    } else {
                        p.set(x, 1, z, laje(lajeBalcao, true));
                    }
                } else if (noFundo) {
                    for (int y = 1; y <= altura; y++) p.set(x, y, z, tabua);
                } else if (frenteEmZ ? bordaX : bordaZ) {
                    // Lateral: tábua embaixo, cerca ligada na direção do fundo. O miolo fica livre (ar).
                    p.set(x, 1, z, tabua);
                    BlockFace eixoFace = frenteEmZ ? BlockFace.NORTH : BlockFace.EAST;
                    for (int y = 2; y <= altura; y++) p.set(x, y, z, conectado(cerca, eixoFace, eixoFace.getOppositeFace()));
                }
            }
        }

        // Toldo listrado no teto + aba um bloco mais baixa na frente.
        int yTeto = altura + 1;
        for (int x = b.pos1()[0]; x <= b.pos2()[0]; x++) {
            for (int z = b.pos1()[2]; z <= b.pos2()[2]; z++) {
                int u = frenteEmZ ? x : z;
                Material cor = cores[Math.floorMod(u, 2)];
                boolean aba = x < x1 || x > x2 || z < z1 || z > z2;
                p.set(x, aba ? altura : yTeto, z, cor);
            }
        }
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        p.set(mx, yTeto, mz, Material.PEARLESCENT_FROGLIGHT);

        if (b.especial()) {
            // Telhado em pirâmide listrada, com luz no topo e lanternas nos cantos da frente.
            for (int nivel = 1; nivel <= 3; nivel++) {
                for (int x = x1 + nivel; x <= x2 - nivel; x++) {
                    for (int z = z1 + nivel; z <= z2 - nivel; z++) {
                        p.set(x, yTeto + nivel, z, cores[Math.floorMod(nivel, 2)]);
                    }
                }
            }
            int topoX = (x1 + x2) / 2, topoZ = (z1 + z2) / 2;
            int nivelTopo = Math.min(3, Math.min(x2 - x1, z2 - z1) / 2);
            p.set(topoX, yTeto + nivelTopo, topoZ, Material.PEARLESCENT_FROGLIGHT);
            p.set(topoX, yTeto + nivelTopo + 1, topoZ, Material.GOLD_BLOCK);
            for (int x = x1; x <= x2; x += x2 - x1) {
                for (int z = z1; z <= z2; z += z2 - z1) {
                    boolean naFrente = switch (frente) {
                        case NORTH -> z == z1;
                        case SOUTH -> z == z2;
                        case WEST -> x == x1;
                        default -> x == x2;
                    };
                    p.set(x, yTeto, z, eixo(tronco, Axis.Y));
                    if (naFrente) p.set(x, yTeto + 1, z, lanterna(Material.LANTERN, false));
                }
            }
        }
    }

    // ------------------------------------------------------------------ enfeites

    /** Nas avenidas sem portão (leste e oeste): canteiro de azaleia e flores encostado na parede. */
    private void gerarFimDasAvenidas(Planta p) {
        for (int k = 3; k < 4; k += 2) { // só a oeste: a norte, a sul e a leste têm portão (minas, boss e loja)
            Quadrante q = new Quadrante(p, k);
            // No referencial do NE girado: a avenida "norte" girada k vezes vira leste/sul/oeste.
            for (int a = 42; a <= 44; a++) {
                for (int t = -5; t <= 5; t++) {
                    if (raio(t, -a) >= R_PISO - 1) continue;
                    q.set(t, 0, -a, Material.GRASS_BLOCK);
                    q.set(t, 1, -a, folha(Math.floorMod(t, 2) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                }
            }
            for (int t = -4; t <= 4; t++) {
                q.set(t, 0, -41, Material.GRASS_BLOCK);
                Material flor = switch (Math.floorMod(t, 3)) {
                    case 0 -> Material.PINK_TULIP;
                    case 1 -> Material.ALLIUM;
                    default -> Material.OXEYE_DAISY;
                };
                q.set(t, 1, -41, flor);
            }
            q.banco(0, -39, BlockFace.NORTH);
            q.set(-5, 0, -39, PEDRA);
            q.poste(-5, -39);
            q.set(5, 0, -39, PEDRA);
            q.poste(5, -39);
        }
    }

    /** Grama, flores e flores altas espalhadas onde for grama (semente fixa). */
    private void gerarFlores(Planta p) {
        Material[] flores = {
                Material.POPPY, Material.DANDELION, Material.OXEYE_DAISY, Material.AZURE_BLUET,
                Material.ALLIUM, Material.CORNFLOWER, Material.PINK_TULIP, Material.WHITE_TULIP,
                Material.RED_TULIP, Material.ORANGE_TULIP, Material.LILY_OF_THE_VALLEY
        };
        Material[] altas = {Material.LILAC, Material.PEONY, Material.ROSE_BUSH};
        Random random = new Random(42);
        int lim = (int) Math.ceil(R_PISO);
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                Material chao = p.get(x, 0, z);
                if (chao != Material.GRASS_BLOCK && chao != Material.MOSS_BLOCK) continue;
                if (p.get(x, 1, z) != Material.AIR) continue;
                int sorte = random.nextInt(100);
                if (sorte < 18) {
                    p.set(x, 1, z, chao == Material.MOSS_BLOCK ? Material.MOSS_CARPET : Material.SHORT_GRASS);
                } else if (sorte < 26) {
                    p.set(x, 1, z, flores[random.nextInt(flores.length)]);
                } else if (sorte < 28 && p.get(x, 2, z) == Material.AIR) {
                    Material alta = altas[random.nextInt(altas.length)];
                    p.set(x, 1, z, metade(alta, false));
                    p.set(x, 2, z, metade(alta, true));
                } else if (sorte < 30) {
                    p.set(x, 1, z, Material.PINK_PETALS);
                }
            }
        }
    }

    /** Líquen brilhante nos pilares (enfeita e não dá pra escalar, ao contrário de cipó). */
    private void gerarLiquens(Planta p) {
        Random random = new Random(5);
        int lim = (int) Math.ceil(R_PISO);
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                double r = raio(x, z);
                if (r < R_PISO - 1 || r >= R_PISO) continue;
                if (distVao(x, z, R_ARCO_INT) < VAO_INT + 0.3 || random.nextInt(4) != 0) continue;
                BlockFace parede = paraFora(x, z);
                int topo = random.nextBoolean() ? 15 : 8;
                int comprimento = 2 + random.nextInt(4);
                for (int y = topo; y > topo - comprimento && y >= 2; y--) {
                    if (p.get(x, y, z) != Material.AIR) break;
                    Material atras = p.get(x + parede.getModX(), y, z + parede.getModZ());
                    if (atras == null || !atras.isSolid()) break;
                    p.set(x, y, z, conectado(Material.GLOW_LICHEN, parede));
                }
            }
        }
    }

    /** Moitas no passeio do alto e azaleia florida em cima do parapeito. */
    private void gerarJardinsNoTopo(Planta p) {
        Random random = new Random(9);
        int lim = (int) Math.ceil(R_EXT);
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                double r = raio(x, z);
                if (r >= R_PASSEIO + 1 && r < R_MURO && p.get(x, Y_PASSEIO + 1, z) == Material.AIR
                        && p.get(x, Y_PASSEIO, z) == FAIXA && random.nextInt(9) == 0) {
                    Material m = random.nextBoolean() ? Material.FLOWERING_AZALEA_LEAVES : Material.CHERRY_LEAVES;
                    p.set(x, Y_PASSEIO + 1, z, folha(m));
                }
                if (r >= R_PISO && r < R_FACE && p.get(x, ALTURA + 1, z) != null
                        && p.get(x, ALTURA + 2, z) == Material.AIR && random.nextInt(5) == 0) {
                    p.set(x, ALTURA + 2, z, folha(Material.FLOWERING_AZALEA_LEAVES));
                }
            }
        }
    }
}
