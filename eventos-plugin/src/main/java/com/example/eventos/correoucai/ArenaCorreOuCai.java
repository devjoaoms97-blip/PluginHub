package com.example.eventos.correoucai;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A arena do Corre ou Cai: um prédio quadrado e fechado, de tema de festa colorida. {@code andares}
 * pisos quadrados de concreto (cada andar de uma cor), um a cada {@code distancia-andares} acima da
 * água, de parede a parede. As paredes são de quartzo com faixas de vidro colorido e luzes em cada
 * andar; o teto, de quartzo com claraboias coloridas e lanternas penduradas. A galeria de quem assiste
 * fica em volta dos 4 lados, 5 blocos acima do piso de cima, atrás de um vidro até o teto.
 * <p>
 * {@link #construir()} faz o prédio inteiro (uma vez, pela staff); {@link #refazerPisos()} só refaz os
 * pisos, a cada evento.
 */
public class ArenaCorreOuCai {

    private static final int GALERIA = 4;
    /** As cores dos andares, de baixo pra cima (o vermelho é o aviso de que o bloco vai sumir). */
    private static final Material[] CORES = {Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.MAGENTA_CONCRETE,
            Material.YELLOW_CONCRETE, Material.CYAN_CONCRETE};
    private static final Material[] VIDROS = {Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.PINK_STAINED_GLASS, Material.CYAN_STAINED_GLASS};

    private final String mundo;
    private final int cx, cz, aguaY, lado, andares, distancia;
    private final int x0, z0, x1, z1; // o piso (e a água), de parede a parede

    public ArenaCorreOuCai(ConfigurationSection cfg) {
        this.mundo = cfg.getString("mundo");
        List<Integer> centro = cfg.getIntegerList("centro");
        this.cx = centro.size() > 0 ? centro.get(0) : 0;
        this.cz = centro.size() > 1 ? centro.get(1) : 0;
        this.aguaY = cfg.getInt("agua-y");
        this.lado = Math.max(4, cfg.getInt("raio"));
        this.andares = Math.max(1, cfg.getInt("andares"));
        this.distancia = Math.max(3, cfg.getInt("distancia-andares"));
        this.x0 = cx - lado;
        this.z0 = cz - lado;
        this.x1 = cx + lado;
        this.z1 = cz + lado;
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    public int yDoPiso(int andar) {
        return aguaY + distancia * (andar + 1);
    }

    private int topo() {
        return yDoPiso(andares - 1);
    }

    private int pisoGaleria() {
        return topo() + 5;
    }

    private int teto() {
        return pisoGaleria() + 6;
    }

    /** A cor do piso desse andar. */
    public Material cor(int andar) {
        return CORES[andar % CORES.length];
    }

    /** Um bloco de piso (qualquer andar), seja qual for a cor agora. */
    public boolean ehPiso(Block b) {
        if (!b.getWorld().getName().equals(mundo) || b.getX() < x0 || b.getX() > x1 || b.getZ() < z0 || b.getZ() > z1) return false;
        int dy = b.getY() - aguaY;
        return dy > 0 && dy % distancia == 0 && dy / distancia <= andares;
    }

    /** Começo: no piso de cima, espalhados (sorteados numa grade, longe da parede). */
    public List<Location> pontosDeEntrada(int quantos) {
        List<Location> pontos = new ArrayList<>();
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                float yaw = (float) Math.toDegrees(Math.atan2(-(cx - x), cz - z));
                pontos.add(new Location(mundo(), x + 0.5, topo() + 1, z + 0.5, yaw, 0));
            }
        }
        Collections.shuffle(pontos);
        return pontos;
    }

    public Location arquibancada() {
        return new Location(mundo(), cx + 0.5, pisoGaleria() + 1, z0 - 1 - GALERIA / 2.0, 0, 35);
    }

    /** Dentro das paredes, da água ao teto. */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 && l.getX() < x1 + 1 && l.getZ() >= z0 && l.getZ() < z1 + 1
                && l.getY() >= aguaY - 4 && l.getY() < teto();
    }

    public boolean naArquibancada(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        boolean noAnel = l.getX() >= x0 - 1 - GALERIA && l.getX() < x1 + 2 + GALERIA && l.getZ() >= z0 - 1 - GALERIA
                && l.getZ() < z1 + 2 + GALERIA && !(l.getX() >= x0 - 1 && l.getX() < x1 + 2 && l.getZ() >= z0 - 1 && l.getZ() < z1 + 2);
        return noAnel && l.getY() >= pisoGaleria() && l.getY() < teto();
    }

    public boolean caiu(Location l) {
        return l.getY() < aguaY + 1.2;
    }

    public int[][] caixa() {
        return new int[][]{{x0, aguaY - 4, z0}, {x1, teto(), z1}};
    }

    /** O prédio inteiro. */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        int ex0 = x0 - 2 - GALERIA, ez0 = z0 - 2 - GALERIA, ex1 = x1 + 2 + GALERIA, ez1 = z1 + 2 + GALERIA;
        int base = aguaY - 3, galeria = pisoGaleria(), teto = teto();
        encher(w, ex0 - 3, base, ez0 - 3, ex1 + 3, teto + 6, ez1 + 3, Material.AIR);

        // corpo maciço de quartzo até a galeria, com o poço escavado
        encher(w, ex0, base, ez0, ex1, galeria - 1, ez1, Material.QUARTZ_BLOCK);
        encher(w, x0, aguaY - 2, z0, x1, galeria - 1, z1, Material.AIR);
        encher(w, x0, aguaY - 2, z0, x1, aguaY, z1, Material.WATER);
        for (int x = x0; x <= x1; x += 3) {
            for (int z = z0; z <= z1; z += 3) w.getBlockAt(x, base, z).setType(Material.SEA_LANTERN, false);
        }
        // paredes de dentro: quartzo em colunas, uma faixa de vidro colorido e luzes no meio de cada andar
        for (int a = 0; a < andares; a++) {
            int y = yDoPiso(a) + distancia / 2;
            int i = a;
            for (int x = x0; x <= x1; x++) {
                Material m = (x - x0) % 4 == 0 ? Material.PEARLESCENT_FROGLIGHT : VIDROS[(i + (x - x0) / 4) % VIDROS.length];
                w.getBlockAt(x, y, z0 - 1).setType(m, false);
                w.getBlockAt(x, y, z1 + 1).setType(m, false);
            }
            for (int z = z0; z <= z1; z++) {
                Material m = (z - z0) % 4 == 0 ? Material.PEARLESCENT_FROGLIGHT : VIDROS[(i + 2 + (z - z0) / 4) % VIDROS.length];
                w.getBlockAt(x0 - 1, y, z).setType(m, false);
                w.getBlockAt(x1 + 1, y, z).setType(m, false);
            }
        }
        for (int y = aguaY + 1; y < galeria; y++) {
            for (int x = x0 - 1; x <= x1 + 1; x += 6) {
                w.getBlockAt(x, y, z0 - 1).setType(Material.QUARTZ_PILLAR, false);
                w.getBlockAt(x, y, z1 + 1).setType(Material.QUARTZ_PILLAR, false);
            }
            for (int z = z0 - 1; z <= z1 + 1; z += 6) {
                w.getBlockAt(x0 - 1, y, z).setType(Material.QUARTZ_PILLAR, false);
                w.getBlockAt(x1 + 1, y, z).setType(Material.QUARTZ_PILLAR, false);
            }
        }
        // por fora: faixas coloridas de concreto
        Random r = new Random(7);
        for (int y = base; y < galeria; y += 4) {
            Material faixa = CORES[r.nextInt(CORES.length)];
            anel(w, ex0, ez0, ex1, ez1, y, faixa);
        }

        // galeria: piso de quartzo liso, vidro até o teto do lado da arena, parede com janelas coloridas
        encher(w, ex0 + 1, galeria, ez0 + 1, ex1 - 1, galeria, ez1 - 1, Material.SMOOTH_QUARTZ);
        encher(w, x0, galeria, z0, x1, galeria, z1, Material.AIR);
        for (int y = galeria; y < teto; y++) anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, Material.GLASS);
        for (int y = galeria; y < teto; y++) anel(w, ex0, ez0, ex1, ez1, y, Material.QUARTZ_BLOCK);
        int k = 0;
        for (int x = ex0 + 2; x < ex1; x += 3) {
            w.getBlockAt(x, galeria + 2, ez0).setType(VIDROS[k % VIDROS.length], false);
            w.getBlockAt(x, galeria + 2, ez1).setType(VIDROS[(k + 3) % VIDROS.length], false);
            k++;
        }
        for (int z = ez0 + 2; z < ez1; z += 3) {
            w.getBlockAt(ex0, galeria + 2, z).setType(VIDROS[k % VIDROS.length], false);
            w.getBlockAt(ex1, galeria + 2, z).setType(VIDROS[(k + 3) % VIDROS.length], false);
            k++;
        }
        anel(w, ex0 + 1, ez0 + 1, ex1 - 1, ez1 - 1, galeria + 1, Material.QUARTZ_SLAB);
        for (int x = ex0 + 3; x < ex1; x += 5) {
            w.getBlockAt(x, galeria + 1, ez0 + 1).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(x, galeria + 1, ez1 - 1).setType(Material.SEA_LANTERN, false);
        }

        // teto de quartzo com claraboias coloridas e lanternas penduradas sobre a arena
        encher(w, ex0, teto, ez0, ex1, teto, ez1, Material.QUARTZ_BLOCK);
        k = 0;
        for (int x = x0 + 2; x <= x1 - 2; x += 5) {
            for (int z = z0 + 2; z <= z1 - 2; z += 5) {
                w.getBlockAt(x, teto, z).setType(VIDROS[k++ % VIDROS.length], false);
                pendurar(w, x + 2, teto - 1, z + 2);
            }
        }
        anel(w, ex0 - 1, ez0 - 1, ex1 + 1, ez1 + 1, teto, Material.QUARTZ_SLAB);
        refazerPisos();
    }

    /** Os pisos inteiros, cada andar da sua cor (a cada evento). */
    public void refazerPisos() {
        World w = mundo();
        for (int a = 0; a < andares; a++) encher(w, x0, yDoPiso(a), z0, x1, yDoPiso(a), z1, cor(a));
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
