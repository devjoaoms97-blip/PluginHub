package com.example.eventos.labirinto;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A arena do Labirinto do Fauno: um quadrado de células (corredores de "corredor" blocos, com paredes de 1),
 * fechado por cima com vidro escurecido, sem luz nenhuma lá dentro: só o brilho das paredes de sculk. O chão,
 * as bordas e o teto são fixos ({@link #construir()}); o labirinto de dentro é sorteado de novo a cada evento
 * ({@link #gerar(Random)}): um caminho aleatório que passa por todas as células (busca em profundidade),
 * alguns atalhos abertos a mais (pra ter mais de um caminho e ninguém ficar encurralado à toa) e uma clareira
 * de 3 x 3 células no meio, onde o Fauno acorda. Os baús ficam nos becos sem saída.
 * <p>
 * Quem assiste fica em cima do teto de vidro escurecido, numa sala fechada (parede de vidro e teto de ardósia
 * com lanternas), vendo os corredores lá embaixo.
 */
public class ArenaLabirinto {

    /** A altura das paredes; o teto fica logo acima. */
    static final int ALTURA = 4;

    private final String mundo;
    private final int chao, celulas, corredor, lado, x0, z0;
    private final double atalhos;
    /** [x][z] relativo ao canto: parede? (do labirinto sorteado agora) */
    private boolean[][] parede;
    private final List<Block> baus = new ArrayList<>();
    private int quantosBaus = 20;

    public ArenaLabirinto(ConfigurationSection cfg) {
        this.mundo = cfg.getString("mundo");
        List<Integer> centro = cfg.getIntegerList("centro");
        int cx = centro.size() > 0 ? centro.get(0) : 0, cz = centro.size() > 1 ? centro.get(1) : 0;
        this.chao = cfg.getInt("chao-y");
        // ímpar, pra ter uma célula no meio
        int n = Math.max(7, cfg.getInt("celulas"));
        this.celulas = n % 2 == 0 ? n + 1 : n;
        this.corredor = Math.max(1, cfg.getInt("corredor"));
        this.atalhos = Math.max(0, Math.min(1, cfg.getDouble("atalhos")));
        this.lado = celulas * (corredor + 1) + 1;
        this.x0 = cx - lado / 2;
        this.z0 = cz - lado / 2;
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    private int teto() {
        return chao + ALTURA + 1;
    }

    /** O primeiro bloco (relativo ao canto) da célula i, num eixo. */
    private int inicio(int i) {
        return 1 + i * (corredor + 1);
    }

    private int meio() {
        return celulas / 2;
    }

    /** Na clareira do meio (3 x 3 células)? */
    private boolean naClareira(int i, int j) {
        return Math.abs(i - meio()) <= 1 && Math.abs(j - meio()) <= 1;
    }

    /** Dentro das paredes de fora, do chão ao teto. */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 && l.getX() < x0 + lado && l.getZ() >= z0 && l.getZ() < z0 + lado
                && l.getY() >= chao - 1 && l.getY() < teto();
    }

    /** Em cima do teto, dentro do cercado de vidro. */
    public boolean naArquibancada(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 - 1 && l.getX() < x0 + lado + 1 && l.getZ() >= z0 - 1 && l.getZ() < z0 + lado + 1
                && l.getY() >= teto() && l.getY() < teto() + 6;
    }

    /** Em cima do teto, na borda sul, olhando pro labirinto. */
    public Location arquibancada() {
        return new Location(mundo(), x0 + lado / 2.0, teto() + 1, z0 + 1.5, 0, 45);
    }

    /** O meio da clareira, onde o Fauno acorda. */
    public Location clareira() {
        double c = inicio(meio()) + corredor / 2.0;
        return new Location(mundo(), x0 + c, chao + 1, z0 + c);
    }

    public int[][] caixa() {
        return new int[][]{{x0, chao - 1, z0}, {x0 + lado - 1, teto(), z0 + lado - 1}};
    }

    public List<Block> baus() {
        return baus;
    }

    /** Quantos baús o próximo {@link #gerar} põe. */
    public void setQuantosBaus(int quantos) {
        this.quantosBaus = Math.max(0, quantos);
    }

    /**
     * Um lugar de começo por jogador, nas células da borda, espalhados por igual em volta (a partir de um
     * ponto sorteado), olhando pro meio.
     */
    public List<Location> pontosDeEntrada(int quantos, Random r) {
        List<int[]> borda = new ArrayList<>();
        int n = celulas - 1;
        for (int i = 0; i < n; i++) borda.add(new int[]{i, 0});
        for (int j = 0; j < n; j++) borda.add(new int[]{n, j});
        for (int i = n; i > 0; i--) borda.add(new int[]{i, n});
        for (int j = n; j > 0; j--) borda.add(new int[]{0, j});
        int q = Math.max(1, Math.min(quantos, borda.size()));
        int desde = r.nextInt(borda.size());
        List<Location> pontos = new ArrayList<>();
        Location meio = clareira();
        for (int k = 0; k < q; k++) {
            int[] c = borda.get((desde + k * borda.size() / q) % borda.size());
            double x = x0 + inicio(c[0]) + corredor / 2.0, z = z0 + inicio(c[1]) + corredor / 2.0;
            float yaw = (float) Math.toDegrees(Math.atan2(-(meio.getX() - x), meio.getZ() - z));
            pontos.add(new Location(mundo(), x, chao + 1, z, yaw, 0));
        }
        return pontos;
    }

    // ---------------------------------------------------------------- construção

    /** O que não muda: o chão, o teto de vidro escurecido, a sala de quem assiste. E um labirinto. */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        int teto = teto();
        encher(w, x0 - 3, chao - 2, z0 - 3, x0 + lado + 2, teto + 6, z0 + lado + 2, Material.AIR);
        encher(w, x0 - 1, chao - 2, z0 - 1, x0 + lado, chao - 1, z0 + lado, Material.DEEPSLATE);
        Random r = new Random(7);
        for (int x = 0; x < lado; x++) {
            for (int z = 0; z < lado; z++) {
                int s = r.nextInt(20);
                w.getBlockAt(x0 + x, chao, z0 + z).setType(s < 9 ? Material.DEEPSLATE_TILES : s < 13 ? Material.CRACKED_DEEPSLATE_TILES
                        : s < 17 ? Material.COBBLED_DEEPSLATE : Material.MOSS_BLOCK, false);
            }
        }
        // a clareira do meio: pedra musgosa, com o centro talhado
        int a = inicio(meio() - 1), b = inicio(meio() + 1) + corredor - 1;
        encher(w, x0 + a, chao, z0 + a, x0 + b, chao, z0 + b, Material.MOSSY_STONE_BRICKS);
        int c = inicio(meio());
        encher(w, x0 + c, chao, z0 + c, x0 + c + corredor - 1, chao, z0 + c + corredor - 1, Material.CHISELED_STONE_BRICKS);
        // o teto: vidro escurecido (não deixa passar luz nenhuma), com a borda de cima das paredes de fora lisa
        encher(w, x0, teto, z0, x0 + lado - 1, teto, z0 + lado - 1, Material.TINTED_GLASS);
        anel(w, x0, z0, x0 + lado - 1, z0 + lado - 1, teto, Material.POLISHED_DEEPSLATE);
        // a sala de quem assiste, fechada: parede de vidro e teto de ardósia com lanternas do mar (a luz delas
        // não atravessa o vidro escurecido: o labirinto continua no escuro)
        for (int y = teto; y <= teto + 3; y++) anel(w, x0 - 1, z0 - 1, x0 + lado, z0 + lado, y, Material.GLASS);
        anel(w, x0 - 1, z0 - 1, x0 + lado, z0 + lado, teto - 1, Material.POLISHED_DEEPSLATE);
        encher(w, x0 - 1, teto + 4, z0 - 1, x0 + lado, teto + 4, z0 + lado, Material.DEEPSLATE_TILES);
        for (int x = x0 + 3; x < x0 + lado; x += 8) {
            for (int z = z0 + 3; z < z0 + lado; z += 8) w.getBlockAt(x, teto + 4, z).setType(Material.SEA_LANTERN, false);
        }
        gerar(new Random());
    }

    /** Sorteia um labirinto novo e o constrói (paredes, baús vazios). */
    public void gerar(Random r) {
        World w = mundo();
        if (w == null) return;
        // os baús do labirinto anterior: esvazia antes (pra nada cair quando viram ar)
        for (Block b : baus) if (b.getState() instanceof Chest bau) bau.getBlockInventory().clear();
        baus.clear();

        parede = new boolean[lado][lado];
        for (boolean[] linha : parede) java.util.Arrays.fill(linha, true);
        for (int i = 0; i < celulas; i++) for (int j = 0; j < celulas; j++) abrir(inicio(i), inicio(j), corredor, corredor);

        // o caminho que passa por todas as células
        boolean[][] visto = new boolean[celulas][celulas];
        Deque<int[]> pilha = new ArrayDeque<>();
        pilha.push(new int[]{meio(), meio()});
        visto[meio()][meio()] = true;
        int[][] passos = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!pilha.isEmpty()) {
            int[] atual = pilha.peek();
            List<int[]> vizinhos = new ArrayList<>();
            for (int[] p : passos) {
                int ni = atual[0] + p[0], nj = atual[1] + p[1];
                if (ni >= 0 && nj >= 0 && ni < celulas && nj < celulas && !visto[ni][nj]) vizinhos.add(new int[]{ni, nj});
            }
            if (vizinhos.isEmpty()) {
                pilha.pop();
                continue;
            }
            int[] prox = vizinhos.get(r.nextInt(vizinhos.size()));
            derrubar(atual[0], atual[1], prox[0], prox[1]);
            visto[prox[0]][prox[1]] = true;
            pilha.push(prox);
        }
        // atalhos: algumas paredes a mais caem
        for (int i = 0; i < celulas; i++) {
            for (int j = 0; j < celulas; j++) {
                if (i + 1 < celulas && r.nextDouble() < atalhos) derrubar(i, j, i + 1, j);
                if (j + 1 < celulas && r.nextDouble() < atalhos) derrubar(i, j, i, j + 1);
            }
        }
        // a clareira
        int a = inicio(meio() - 1), b = inicio(meio() + 1) + corredor - 1;
        abrir(a, a, b - a + 1, b - a + 1);

        for (int x = 0; x < lado; x++) {
            for (int z = 0; z < lado; z++) {
                Material m = parede[x][z] ? Material.SCULK : Material.AIR;
                for (int y = chao + 1; y <= chao + ALTURA; y++) w.getBlockAt(x0 + x, y, z0 + z).setType(m, false);
            }
        }
        colocarBaus(w, r);
    }

    private void abrir(int x, int z, int largura, int comprimento) {
        for (int dx = 0; dx < largura; dx++) for (int dz = 0; dz < comprimento; dz++) parede[x + dx][z + dz] = false;
    }

    /** A parede entre duas células vizinhas. */
    private void derrubar(int i, int j, int ni, int nj) {
        if (ni != i) abrir(inicio(Math.max(i, ni)) - 1, inicio(j), 1, corredor);
        else abrir(inicio(i), inicio(Math.max(j, nj)) - 1, corredor, 1);
    }

    /** As saídas de uma célula: +x, -x, +z, -z. */
    private boolean[] saidas(int i, int j) {
        int x = inicio(i), z = inicio(j);
        return new boolean[]{!parede[x + corredor][z], !parede[x - 1][z], !parede[x][z + corredor], !parede[x][z - 1]};
    }

    /**
     * Um baú em cada beco sem saída sorteado (fora da clareira), encostado no fundo, de frente pra saída.
     * Se faltar beco, vai em células quaisquer.
     */
    private void colocarBaus(World w, Random r) {
        List<int[]> becos = new ArrayList<>(), outras = new ArrayList<>();
        for (int i = 0; i < celulas; i++) {
            for (int j = 0; j < celulas; j++) {
                if (naClareira(i, j)) continue;
                int abertas = 0;
                for (boolean s : saidas(i, j)) if (s) abertas++;
                (abertas == 1 ? becos : outras).add(new int[]{i, j});
            }
        }
        Collections.shuffle(becos, r);
        Collections.shuffle(outras, r);
        becos.addAll(outras);
        int quantos = Math.min(quantosBaus, becos.size());
        for (int k = 0; k < quantos; k++) {
            int i = becos.get(k)[0], j = becos.get(k)[1];
            boolean[] s = saidas(i, j);
            int x = inicio(i), z = inicio(j), lateral = r.nextInt(corredor);
            // encostado do lado oposto à (primeira) saída, virado pra ela
            int bx, bz;
            BlockFace frente;
            if (s[0]) { bx = x; bz = z + lateral; frente = BlockFace.EAST; }
            else if (s[1]) { bx = x + corredor - 1; bz = z + lateral; frente = BlockFace.WEST; }
            else if (s[2]) { bx = x + lateral; bz = z; frente = BlockFace.SOUTH; }
            else { bx = x + lateral; bz = z + corredor - 1; frente = BlockFace.NORTH; }
            Block b = w.getBlockAt(x0 + bx, chao + 1, z0 + bz);
            b.setType(Material.CHEST, false);
            if (b.getBlockData() instanceof Directional d) {
                d.setFacing(frente);
                b.setBlockData(d, false);
            }
            baus.add(b);
        }
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
