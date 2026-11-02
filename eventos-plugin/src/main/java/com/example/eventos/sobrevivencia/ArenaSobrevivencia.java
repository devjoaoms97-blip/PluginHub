package com.example.eventos.sobrevivencia;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A arena da Sobrevivência: o pátio de um castelo sitiado, fechado. Chão de pedra (com musgo e
 * rachaduras), muralhas de tijolo de pedra com colunas de carvalho escuro, um portão com grade no meio de
 * cada muralha (os invasores entram por ali), fardos de feno e pilhas de tronco pelo pátio pra se
 * esconder, teto de carvalho escuro com claraboias e lanternas penduradas. A galeria de quem assiste
 * fica em cima das muralhas, em volta dos 4 lados, atrás de um vidro até o teto; por fora, ameias.
 */
public class ArenaSobrevivencia {

    private static final int GALERIA = 4;
    private static final int ALTURA_MURALHA = 8;

    private final String mundo;
    private final int cx, cz, chaoY, raio;
    private final int x0, z0, x1, z1; // o pátio

    public ArenaSobrevivencia(ConfigurationSection cfg) {
        this.mundo = cfg.getString("mundo");
        List<Integer> centro = cfg.getIntegerList("centro");
        this.cx = centro.size() > 0 ? centro.get(0) : 0;
        this.cz = centro.size() > 1 ? centro.get(1) : 0;
        this.chaoY = cfg.getInt("chao-y");
        this.raio = Math.max(6, cfg.getInt("raio"));
        this.x0 = cx - raio;
        this.z0 = cz - raio;
        this.x1 = cx + raio;
        this.z1 = cz + raio;
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    private int pisoGaleria() {
        return chaoY + ALTURA_MURALHA;
    }

    private int teto() {
        return pisoGaleria() + 6;
    }

    /** Começo: em roda, no meio do pátio, olhando pra fora (pros portões). */
    public List<Location> pontosDeEntrada(int quantos) {
        List<Location> pontos = new ArrayList<>();
        int n = Math.max(1, quantos);
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n, r = n == 1 ? 0 : 3;
            double x = cx + 0.5 + r * Math.cos(ang), z = cz + 0.5 + r * Math.sin(ang);
            float yaw = (float) Math.toDegrees(Math.atan2(-(x - cx - 0.5), z - cz - 0.5));
            pontos.add(new Location(mundo(), x, chaoY + 1, z, yaw, 0));
        }
        return pontos;
    }

    /** Onde os invasores aparecem: na frente de cada portão, dentro do pátio. */
    public List<Location> portoes() {
        World w = mundo();
        return List.of(new Location(w, cx + 0.5, chaoY + 1, z0 + 1.5), new Location(w, cx + 0.5, chaoY + 1, z1 - 0.5),
                new Location(w, x0 + 1.5, chaoY + 1, cz + 0.5), new Location(w, x1 - 0.5, chaoY + 1, cz + 0.5));
    }

    public Location arquibancada() {
        return new Location(mundo(), cx + 0.5, pisoGaleria() + 1, z0 - 1 - GALERIA / 2.0, 0, 35);
    }

    /** Dentro das muralhas, do chão ao teto. */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 && l.getX() < x1 + 1 && l.getZ() >= z0 && l.getZ() < z1 + 1
                && l.getY() >= chaoY - 2 && l.getY() < teto();
    }

    public boolean naArquibancada(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        boolean noAnel = l.getX() >= x0 - 1 - GALERIA && l.getX() < x1 + 2 + GALERIA && l.getZ() >= z0 - 1 - GALERIA
                && l.getZ() < z1 + 2 + GALERIA && !(l.getX() >= x0 - 1 && l.getX() < x1 + 2 && l.getZ() >= z0 - 1 && l.getZ() < z1 + 2);
        return noAnel && l.getY() >= pisoGaleria() && l.getY() < teto();
    }

    public int[][] caixa() {
        return new int[][]{{x0, chaoY - 2, z0}, {x1, teto(), z1}};
    }

    /** O castelo inteiro. */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        int ex0 = x0 - 2 - GALERIA, ez0 = z0 - 2 - GALERIA, ex1 = x1 + 2 + GALERIA, ez1 = z1 + 2 + GALERIA;
        int base = chaoY - 4, galeria = pisoGaleria(), teto = teto();
        encher(w, ex0 - 3, base, ez0 - 3, ex1 + 3, teto + 6, ez1 + 3, Material.AIR);

        // corpo maciço até a galeria, com o pátio escavado
        encher(w, ex0, base, ez0, ex1, galeria - 1, ez1, Material.STONE_BRICKS);
        encher(w, x0, chaoY + 1, z0, x1, galeria - 1, z1, Material.AIR);
        Random r = new Random(11);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int s = r.nextInt(10);
                w.getBlockAt(x, chaoY, z).setType(s < 2 ? Material.MOSSY_STONE_BRICKS : s < 4 ? Material.CRACKED_STONE_BRICKS
                        : s < 5 ? Material.COBBLESTONE : Material.STONE_BRICKS, false);
            }
        }
        // muralhas por dentro: colunas de carvalho escuro, faixa de andesito e lanternas nas colunas
        for (int y = chaoY + 1; y < galeria; y++) {
            for (int x = x0 - 1; x <= x1 + 1; x += 5) {
                w.getBlockAt(x, y, z0 - 1).setType(Material.DARK_OAK_LOG, false);
                w.getBlockAt(x, y, z1 + 1).setType(Material.DARK_OAK_LOG, false);
            }
            for (int z = z0 - 1; z <= z1 + 1; z += 5) {
                w.getBlockAt(x0 - 1, y, z).setType(Material.DARK_OAK_LOG, false);
                w.getBlockAt(x1 + 1, y, z).setType(Material.DARK_OAK_LOG, false);
            }
        }
        anelSemColunas(w, chaoY + 4, Material.POLISHED_ANDESITE);
        // os 4 portões: um vão de 3 de largura e 4 de altura na muralha, com grade de ferro
        portao(w, cx - 1, z0 - 1, cx + 1, z0 - 1);
        portao(w, cx - 1, z1 + 1, cx + 1, z1 + 1);
        portao(w, x0 - 1, cz - 1, x0 - 1, cz + 1);
        portao(w, x1 + 1, cz - 1, x1 + 1, cz + 1);
        // cobertura pelo pátio: fardos de feno e pilhas de tronco, longe dos portões e do meio
        int[][] obstaculos = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {-10, 0}, {10, 0}, {0, -10}, {0, 10}};
        for (int i = 0; i < obstaculos.length; i++) {
            int ox = cx + obstaculos[i][0] * raio / 15, oz = cz + obstaculos[i][1] * raio / 15;
            Material m = i % 2 == 0 ? Material.HAY_BLOCK : Material.SPRUCE_LOG;
            encher(w, ox, chaoY + 1, oz, ox + 1, chaoY + 1, oz, m);
            w.getBlockAt(ox, chaoY + 2, oz).setType(m, false);
        }

        // galeria: piso de carvalho escuro, vidro até o teto do lado do pátio, parede com seteiras
        encher(w, ex0 + 1, galeria, ez0 + 1, ex1 - 1, galeria, ez1 - 1, Material.DARK_OAK_PLANKS);
        encher(w, x0, galeria, z0, x1, galeria, z1, Material.AIR);
        for (int y = galeria; y < teto; y++) anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, Material.GLASS);
        for (int y = galeria; y < teto; y++) anel(w, ex0, ez0, ex1, ez1, y, Material.STONE_BRICKS);
        for (int x = ex0 + 2; x < ex1; x += 3) {
            w.getBlockAt(x, galeria + 2, ez0).setType(Material.IRON_BARS, false);
            w.getBlockAt(x, galeria + 2, ez1).setType(Material.IRON_BARS, false);
        }
        for (int z = ez0 + 2; z < ez1; z += 3) {
            w.getBlockAt(ex0, galeria + 2, z).setType(Material.IRON_BARS, false);
            w.getBlockAt(ex1, galeria + 2, z).setType(Material.IRON_BARS, false);
        }
        anel(w, ex0 + 1, ez0 + 1, ex1 - 1, ez1 - 1, galeria + 1, Material.DARK_OAK_SLAB);
        for (int x = ex0 + 3; x < ex1; x += 6) {
            w.getBlockAt(x, galeria + 1, ez0 + 1).setType(Material.LANTERN, false);
            w.getBlockAt(x, galeria + 1, ez1 - 1).setType(Material.LANTERN, false);
        }

        // teto de carvalho escuro com claraboias e lanternas penduradas; ameias em volta por fora
        encher(w, ex0, teto, ez0, ex1, teto, ez1, Material.DARK_OAK_PLANKS);
        for (int x = x0 + 2; x <= x1 - 2; x += 6) {
            for (int z = z0 + 2; z <= z1 - 2; z += 6) {
                encher(w, x, teto, z, x + 1, teto, z + 1, Material.GLASS);
                pendurar(w, x + 3, teto - 1, z + 3);
            }
        }
        for (int x = ex0; x <= ex1; x += 2) {
            w.getBlockAt(x, teto + 1, ez0).setType(Material.STONE_BRICKS, false);
            w.getBlockAt(x, teto + 1, ez1).setType(Material.STONE_BRICKS, false);
        }
        for (int z = ez0; z <= ez1; z += 2) {
            w.getBlockAt(ex0, teto + 1, z).setType(Material.STONE_BRICKS, false);
            w.getBlockAt(ex1, teto + 1, z).setType(Material.STONE_BRICKS, false);
        }
    }

    /**
     * O portão: grade de ferro rente à muralha (3 de largura, 4 de altura), com um vão escuro atrás. Rente
     * pra ninguém entrar num vão fora da arena (sair da arena elimina).
     */
    private void portao(World w, int ax, int az, int bx, int bz) {
        boolean norteSul = az == bz;
        int fora = norteSul ? (az < cz ? -1 : 1) : (ax < cx ? -1 : 1);
        for (int y = chaoY + 1; y <= chaoY + 4; y++) {
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    w.getBlockAt(x, y, z).setType(Material.IRON_BARS, false);
                    Block atras = norteSul ? w.getBlockAt(x, y, z + fora) : w.getBlockAt(x + fora, y, z);
                    atras.setType(Material.AIR, false);
                }
            }
        }
        // o arco de cima do portão
        for (int x = ax - (norteSul ? 1 : 0); x <= bx + (norteSul ? 1 : 0); x++) {
            for (int z = az - (norteSul ? 0 : 1); z <= bz + (norteSul ? 0 : 1); z++) {
                w.getBlockAt(x, chaoY + 5, z).setType(Material.CHISELED_STONE_BRICKS, false);
            }
        }
    }

    private void anelSemColunas(World w, int y, Material m) {
        for (int x = x0; x <= x1; x++) {
            if ((x - x0 + 1) % 5 == 0) continue;
            w.getBlockAt(x, y, z0 - 1).setType(m, false);
            w.getBlockAt(x, y, z1 + 1).setType(m, false);
        }
        for (int z = z0; z <= z1; z++) {
            if ((z - z0 + 1) % 5 == 0) continue;
            w.getBlockAt(x0 - 1, y, z).setType(m, false);
            w.getBlockAt(x1 + 1, y, z).setType(m, false);
        }
    }

    private static void pendurar(World w, int x, int y, int z) {
        w.getBlockAt(x, y, z).setType(Material.IRON_CHAIN, false);
        Block b = w.getBlockAt(x, y - 1, z);
        b.setType(Material.LANTERN, false);
        if (b.getBlockData() instanceof Lantern l) {
            l.setHanging(true);
            b.setBlockData(l, false);
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
