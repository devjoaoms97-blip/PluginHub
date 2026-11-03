package com.example.eventos.corrida;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pista da Corrida Maluca: um pavilhão comprido e fechado, de tema de parque de diversões (listras
 * vermelhas e brancas, pisos coloridos, bandeirinhas), com a pista de {@code 13} blocos de largura sobre
 * uma piscina, de sul a norte (+z) a partir de {@code inicio}. Quem cai na água volta pro checkpoint. Quem
 * assiste fica nas passarelas dos dois lados, em cima das paredes, atrás de um vidro até o teto.
 * <p>
 * Os trechos (dz a partir do início):
 * <ol>
 *   <li>Largada (0 a 7) com o portão de vidro (8), que abre no "valendo"; duas fileiras de portas falsas
 *       (14 e 20: de cada 3, só umas abrem); o trampolim de slime (26), que joga por cima do vão (27 a 31);
 *       e a pista de gelo com buracos (35 a 46). Checkpoint 1 (47 a 50).</li>
 *   <li>As plataformas que somem e voltam (53 a 70), em dois grupos alternados; e o tapete rolante (73 a
 *       88), estreito, que empurra pra trás. Checkpoint 2 (89 a 92).</li>
 *   <li>Dois discos com barras giratórias na altura do pé (pivôs em 99 e 113: é preciso pular); a ponte
 *       estreita do vento (120 a 134), com canhões nas paredes; e a chegada (136).</li>
 * </ol>
 * {@link #construir()} faz o pavilhão inteiro (uma vez, pela staff); {@link #refazer()} só o que muda numa
 * corrida (o portão, as portas e as plataformas).
 */
public class ArenaCorrida {

    /** A metade da largura da pista (de -6 a 6). */
    static final int LARGURA = 6;
    /** O raio das barras giratórias (e dos discos). */
    static final int RAIO = 6;
    static final int PORTAO = 8;
    static final int[] PORTAS = {14, 20};
    static final int TRAMPOLIM = 26;
    static final int[] CHECKPOINTS = {0, 47, 89};
    static final int[] LINHAS_PLATAFORMAS = {53, 58, 63, 68};
    static final int[] COLUNAS_PLATAFORMAS = {-5, -1, 3};
    static final int[] PIVOS = {99, 113};
    static final int[] CANHOES = {123, 126, 129, 132};
    static final int CHEGADA = 136;
    static final int FIM = 143;

    static final Material PORTA = Material.PURPLE_CONCRETE;
    static final Material[] GRUPOS = {Material.LIME_CONCRETE, Material.PINK_CONCRETE};
    static final Material AVISO = Material.RED_CONCRETE;
    static final Material TAPETE = Material.MAGENTA_GLAZED_TERRACOTTA;
    private static final Material[] CORES = {Material.LIGHT_BLUE_CONCRETE, Material.PINK_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.ORANGE_CONCRETE};
    private static final Material[] VIDROS = {Material.RED_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE};
    /** Os buracos 2x2 da pista de gelo (dz, x do canto). */
    private static final int[][] BURACOS_GELO = {{37, -5}, {37, 1}, {40, -2}, {40, 4}, {43, -6}, {43, 0}, {45, 3}};

    /** Uma porta (3 x 3) de uma fileira: as que abrem somem quando alguém encosta. */
    record Porta(int fileira, int x0, int z) {
    }

    /** Um canhão de vento na parede: de onde sai a carga e pra onde vai. */
    record Canhao(Location origem, Vector direcao) {
    }

    private final String mundo;
    private final int z0, agua, piso, galeria, teto;

    public ArenaCorrida(ConfigurationSection cfg) {
        this.mundo = cfg.getString("mundo");
        List<Integer> inicio = cfg.getIntegerList("inicio");
        this.z0 = inicio.size() > 1 ? inicio.get(1) : 0;
        this.agua = cfg.getInt("agua-y");
        this.piso = agua + 6;
        this.galeria = piso + 10;
        this.teto = galeria + 7;
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    public int piso() {
        return piso;
    }

    private int z(int dz) {
        return z0 + dz;
    }

    // ---------------------------------------------------------------- consultas

    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= -LARGURA && l.getX() < LARGURA + 1 && l.getZ() >= z0 && l.getZ() < z(FIM)
                && l.getY() >= agua - 4 && l.getY() < galeria;
    }

    /** As passarelas dos dois lados, por cima das paredes. */
    public boolean naArquibancada(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        boolean lado = (l.getX() >= LARGURA + 2 && l.getX() < LARGURA + 7) || (l.getX() >= -LARGURA - 6 && l.getX() < -LARGURA - 1);
        return lado && l.getZ() >= z0 - 1 && l.getZ() < z(FIM) + 1 && l.getY() >= galeria && l.getY() < teto;
    }

    public Location arquibancada() {
        return new Location(mundo(), LARGURA + 4.5, galeria + 1, z(20) + 0.5, 90, 30);
    }

    public boolean caiu(Location l) {
        return l.getY() < agua + 1.2;
    }

    public int[][] caixa() {
        return new int[][]{{-LARGURA - 1, agua - 4, z0 - 1}, {LARGURA + 1, teto, z(FIM)}};
    }

    /** Começo: espalhados na largada, de frente pra pista. */
    public List<Location> pontosDeEntrada() {
        List<Location> pontos = new ArrayList<>();
        for (int x = -5; x <= 5; x += 2) {
            for (int dz = 1; dz <= 5; dz += 2) pontos.add(new Location(mundo(), x + 0.5, piso + 1, z(dz) + 0.5, 0, 0));
        }
        Collections.shuffle(pontos);
        return pontos;
    }

    /** Onde quem caiu volta: no checkpoint, num lugar sorteado da largura. */
    public Location checkpoint(int i) {
        int dz = CHECKPOINTS[Math.max(0, Math.min(i, CHECKPOINTS.length - 1))] + (i == 0 ? 3 : 1);
        int x = ThreadLocalRandom.current().nextInt(-4, 5);
        return new Location(mundo(), x + 0.5, piso + 1, z(dz) + 0.5, 0, 0);
    }

    /** O último checkpoint que esse lugar já passou (0 = a largada). */
    public int checkpointEm(Location l) {
        int dz = (int) Math.floor(l.getZ()) - z0;
        for (int i = CHECKPOINTS.length - 1; i > 0; i--) if (dz >= CHECKPOINTS[i]) return i;
        return 0;
    }

    public boolean chegou(Location l) {
        return l.getZ() >= z(CHEGADA) && l.getY() >= piso + 0.5;
    }

    public boolean ehTrampolim(Block b) {
        return b.getType() == Material.SLIME_BLOCK && b.getZ() == z(TRAMPOLIM) && b.getY() == piso;
    }

    public boolean ehTapete(Block b) {
        return b.getType() == TAPETE && b.getY() == piso;
    }

    public List<Porta> portas() {
        List<Porta> portas = new ArrayList<>();
        for (int f = 0; f < PORTAS.length; f++) {
            for (int x0 = -5; x0 <= 3; x0 += 4) portas.add(new Porta(f, x0, z(PORTAS[f])));
        }
        return portas;
    }

    /** O centro de cada pivô, no nível de cima do piso. */
    public List<Location> pivos() {
        List<Location> pivos = new ArrayList<>();
        for (int dz : PIVOS) pivos.add(new Location(mundo(), 0.5, piso + 1, z(dz) + 0.5));
        return pivos;
    }

    /** Os canhões, alternando as paredes (o primeiro na do leste), na altura do peito. */
    public List<Canhao> canhoes() {
        List<Canhao> canhoes = new ArrayList<>();
        for (int i = 0; i < CANHOES.length; i++) {
            int lado = i % 2 == 0 ? 1 : -1;
            Vector direcao = new Vector(-lado, 0, 0);
            Location origem = new Location(mundo(), 0.5 + lado * (LARGURA + 0.1), piso + 1.6, z(CANHOES[i]) + 0.5);
            origem.setDirection(direcao);
            canhoes.add(new Canhao(origem, direcao));
        }
        return canhoes;
    }

    // ---------------------------------------------------------------- o que muda numa corrida

    /** O portão fechado, todas as portas no lugar e as plataformas firmes. */
    public void refazer() {
        World w = mundo();
        if (w == null) return;
        encher(w, -LARGURA, piso + 1, z(PORTAO), LARGURA, piso + 3, z(PORTAO), Material.LIGHT_BLUE_STAINED_GLASS);
        for (Porta p : portas()) porta(p, PORTA);
        for (int g = 0; g < GRUPOS.length; g++) plataformas(g, GRUPOS[g]);
    }

    public void abrirPortao() {
        World w = mundo();
        if (w != null) encher(w, -LARGURA, piso + 1, z(PORTAO), LARGURA, piso + 3, z(PORTAO), Material.AIR);
    }

    public void porta(Porta p, Material m) {
        World w = mundo();
        if (w != null) encher(w, p.x0(), piso + 1, p.z(), p.x0() + 2, piso + 3, p.z(), m);
    }

    /** As plataformas de um grupo (os dois se alternam, em xadrez) de um material (AIR = sumidas). */
    public void plataformas(int grupo, Material m) {
        World w = mundo();
        if (w == null) return;
        for (int l = 0; l < LINHAS_PLATAFORMAS.length; l++) {
            for (int c = 0; c < COLUNAS_PLATAFORMAS.length; c++) {
                if ((l + c) % 2 != grupo) continue;
                int x0 = COLUNAS_PLATAFORMAS[c], dz0 = LINHAS_PLATAFORMAS[l];
                encher(w, x0, piso, z(dz0), x0 + 2, piso, z(dz0 + 2), m);
            }
        }
    }

    // ---------------------------------------------------------------- o pavilhão

    public void construir() {
        World w = mundo();
        if (w == null) return;
        int fora = LARGURA + 7, base = agua - 3, zi = z0 - 2, zf = z(FIM) + 1;
        encher(w, -fora - 3, agua - 4, zi - 3, fora + 3, teto + 4, zf + 3, Material.AIR);

        // corpo maciço até a passarela, com o poço da pista escavado e a piscina no fundo
        encher(w, -fora, base, zi, fora, galeria - 1, zf, Material.WHITE_CONCRETE);
        encher(w, -LARGURA, agua - 2, z0, LARGURA, galeria - 1, z(FIM) - 1, Material.AIR);
        encher(w, -LARGURA, agua - 2, z0, LARGURA, agua, z(FIM) - 1, Material.WATER);
        for (int x = -LARGURA; x <= LARGURA; x += 4) {
            for (int z = z0 + 1; z < z(FIM); z += 4) w.getBlockAt(x, base, z).setType(Material.SEA_LANTERN, false);
        }

        // paredes de dentro: listras de circo, uma faixa amarela no alto e luzes
        for (int z = z0; z < z(FIM); z++) {
            Material listra = ((z - z0) / 2) % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
            for (int y = agua + 1; y < galeria - 3; y++) {
                w.getBlockAt(-LARGURA - 1, y, z).setType(listra, false);
                w.getBlockAt(LARGURA + 1, y, z).setType(listra, false);
            }
            boolean luz = (z - z0) % 6 == 3;
            for (int y = galeria - 3; y < galeria; y++) {
                Material m = luz && y == galeria - 2 ? Material.OCHRE_FROGLIGHT : Material.YELLOW_CONCRETE;
                w.getBlockAt(-LARGURA - 1, y, z).setType(m, false);
                w.getBlockAt(LARGURA + 1, y, z).setType(m, false);
            }
            if (luz) {
                w.getBlockAt(-LARGURA - 1, piso + 4, z).setType(Material.SEA_LANTERN, false);
                w.getBlockAt(LARGURA + 1, piso + 4, z).setType(Material.SEA_LANTERN, false);
            }
        }
        for (int x = -LARGURA; x <= LARGURA; x++) {
            for (int y = agua + 1; y < galeria; y++) {
                Material listra = ((x + LARGURA) / 2) % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
                w.getBlockAt(x, y, z0 - 1).setType(listra, false);
                w.getBlockAt(x, y, z(FIM)).setType(listra, false);
            }
        }
        // por fora: faixas coloridas
        for (int y = base; y < galeria; y += 3) {
            Material faixa = CORES[((y - base) / 3) % CORES.length];
            anel(w, -fora, zi, fora, zf, y, faixa);
        }

        // passarelas: piso de quartzo, vidro até o teto do lado da pista, paredes com janelas coloridas
        encher(w, LARGURA + 2, galeria, zi + 1, fora - 1, galeria, zf - 1, Material.SMOOTH_QUARTZ);
        encher(w, -fora + 1, galeria, zi + 1, -LARGURA - 2, galeria, zf - 1, Material.SMOOTH_QUARTZ);
        for (int y = galeria; y < teto; y++) {
            encher(w, LARGURA + 1, y, z0 - 1, LARGURA + 1, y, z(FIM), Material.GLASS);
            encher(w, -LARGURA - 1, y, z0 - 1, -LARGURA - 1, y, z(FIM), Material.GLASS);
            encher(w, -LARGURA, y, z0 - 1, LARGURA, y, z0 - 1, Material.GLASS);
            encher(w, -LARGURA, y, z(FIM), LARGURA, y, z(FIM), Material.GLASS);
            anel(w, -fora, zi, fora, zf, y, Material.WHITE_CONCRETE);
        }
        int k = 0;
        for (int z = zi + 2; z < zf; z += 4) {
            Material vidro = Material.valueOf(VIDROS[k++ % VIDROS.length].name().replace("_PANE", ""));
            w.getBlockAt(fora, galeria + 2, z).setType(vidro, false);
            w.getBlockAt(-fora, galeria + 2, z).setType(vidro, false);
        }
        for (int z = zi + 3; z < zf; z += 5) {
            w.getBlockAt(fora - 1, galeria, z).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(-fora + 1, galeria, z).setType(Material.SEA_LANTERN, false);
        }

        // teto com claraboias coloridas e bandeirinhas atravessando a pista
        encher(w, -fora, teto, zi, fora, teto, zf, Material.WHITE_CONCRETE);
        k = 0;
        for (int z = z0 + 4; z < z(FIM); z += 8) {
            for (int x = -3; x <= 3; x += 3) {
                w.getBlockAt(x, teto, z).setType(Material.valueOf(VIDROS[k++ % VIDROS.length].name().replace("_PANE", "")), false);
            }
        }
        for (int z = z0 + 6; z < z(FIM); z += 10) {
            for (int x = -LARGURA; x <= LARGURA; x++) w.getBlockAt(x, galeria + 4, z).setType(VIDROS[(x + LARGURA + z) % VIDROS.length], false);
        }
        anel(w, -fora - 1, zi - 1, fora + 1, zf + 1, teto, Material.SMOOTH_QUARTZ_SLAB);

        pista(w);
        refazer();
    }

    /** O chão de cada trecho (o que não muda numa corrida). */
    private void pista(World w) {
        // largada em xadrez
        for (int dz = 0; dz < PORTAO; dz++) {
            for (int x = -LARGURA; x <= LARGURA; x++) {
                w.getBlockAt(x, piso, z(dz)).setType((x + dz) % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIME_CONCRETE, false);
            }
        }
        listras(w, PORTAO, TRAMPOLIM - 1);
        // as paredes das portas: colunas vermelhas entre as portas e uma viga branca em cima
        for (int dz : PORTAS) {
            for (int x : new int[]{-6, -2, 2, 6}) encher(w, x, piso + 1, z(dz), x, piso + 3, z(dz), Material.RED_CONCRETE);
            encher(w, -LARGURA, piso + 4, z(dz), LARGURA, piso + 4, z(dz), Material.WHITE_CONCRETE);
        }
        encher(w, -LARGURA, piso, z(TRAMPOLIM), LARGURA, piso, z(TRAMPOLIM), Material.SLIME_BLOCK);
        listras(w, TRAMPOLIM + 6, TRAMPOLIM + 8);
        // gelo com buracos
        encher(w, -LARGURA, piso, z(35), LARGURA, piso, z(46), Material.BLUE_ICE);
        for (int[] b : BURACOS_GELO) encher(w, b[1], piso, z(b[0]), b[1] + 1, piso, z(b[0] + 1), Material.AIR);
        for (int i = 1; i < CHECKPOINTS.length; i++) checkpoint(w, CHECKPOINTS[i]);
        // tapete rolante, estreito, com as setas pra trás
        for (int dz = 73; dz <= 88; dz++) {
            for (int x = -3; x <= 3; x++) {
                Block b = w.getBlockAt(x, piso, z(dz));
                b.setType(TAPETE, false);
                if (b.getBlockData() instanceof Directional d) {
                    d.setFacing(BlockFace.NORTH);
                    b.setBlockData(d, false);
                }
            }
        }
        // os discos das barras, em anéis coloridos, com o poste no meio, e a ponte entre eles
        for (int p = 0; p < PIVOS.length; p++) {
            int pz = PIVOS[p];
            for (int dz = -RAIO; dz <= RAIO; dz++) {
                for (int x = -RAIO; x <= RAIO; x++) {
                    double r = Math.sqrt(x * x + dz * dz);
                    if (r > RAIO + 0.3) continue;
                    w.getBlockAt(x, piso, z(pz + dz)).setType(CORES[((int) r / 2 + p) % CORES.length], false);
                }
            }
            w.getBlockAt(0, piso + 1, z(pz)).setType(Material.RED_CONCRETE, false);
            w.getBlockAt(0, piso + 2, z(pz)).setType(Material.WHITE_CONCRETE, false);
            w.getBlockAt(0, piso + 3, z(pz)).setType(Material.RED_CONCRETE, false);
        }
        encher(w, -1, piso, z(PIVOS[0] + RAIO), 1, piso, z(PIVOS[1] - RAIO), Material.YELLOW_CONCRETE);
        // a ponte do vento, com os canhões (enfeite) nas paredes
        for (int dz = 120; dz <= 134; dz++) {
            for (int x = -1; x <= 1; x++) w.getBlockAt(x, piso, z(dz)).setType(dz % 2 == 0 ? Material.ORANGE_CONCRETE : Material.WHITE_CONCRETE, false);
        }
        for (Canhao c : canhoes()) {
            int lado = c.direcao().getX() < 0 ? 1 : -1;
            Block b = w.getBlockAt(lado * (LARGURA + 1), piso + 1, c.origem().getBlockZ());
            b.setType(Material.DISPENSER, false);
            if (b.getBlockData() instanceof Directional d) {
                d.setFacing(lado > 0 ? BlockFace.WEST : BlockFace.EAST);
                b.setBlockData(d, false);
            }
        }
        // a chegada: faixa quadriculada e um arco
        encher(w, -LARGURA, piso, z(135), LARGURA, piso, z(FIM - 1), Material.YELLOW_CONCRETE);
        for (int dz = CHEGADA; dz <= CHEGADA + 1; dz++) {
            for (int x = -LARGURA; x <= LARGURA; x++) {
                w.getBlockAt(x, piso, z(dz)).setType((x + dz) % 2 == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE, false);
            }
        }
        for (int y = piso + 1; y <= piso + 5; y++) {
            w.getBlockAt(-LARGURA, y, z(CHEGADA)).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(LARGURA, y, z(CHEGADA)).setType(Material.GOLD_BLOCK, false);
        }
        for (int x = -LARGURA; x <= LARGURA; x++) {
            w.getBlockAt(x, piso + 5, z(CHEGADA)).setType((x + LARGURA) % 2 == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE, false);
        }
    }

    private void listras(World w, int dzDe, int dzAte) {
        for (int dz = dzDe; dz <= dzAte; dz++) {
            encher(w, -LARGURA, piso, z(dz), LARGURA, piso, z(dz), CORES[(dz / 2) % CORES.length]);
        }
    }

    private void checkpoint(World w, int dz) {
        encher(w, -LARGURA, piso, z(dz), LARGURA, piso, z(dz + 3), Material.YELLOW_CONCRETE);
        encher(w, -LARGURA, piso, z(dz), LARGURA, piso, z(dz), Material.GOLD_BLOCK);
    }

    private static void anel(World w, int ax, int az, int bx, int bz, int y, Material m) {
        encher(w, ax, y, az, bx, y, az, m);
        encher(w, ax, y, bz, bx, y, bz, m);
        encher(w, ax, y, az, ax, y, bz, m);
        encher(w, bx, y, az, bx, y, bz, m);
    }

    private static void encher(World w, int ax, int ay, int az, int bx, int by, int bz, Material m) {
        for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
            for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++) {
                for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) w.getBlockAt(x, y, z).setType(m, false);
            }
        }
    }
}
