package com.example.eventos.vietna;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A arena do Vietnã: um prédio fechado, de tema de selva.
 * <pre>
 *   teto (madeira escura, claraboias de vidro e lanternas penduradas)
 *   ┌─────────────────────────────────────────┐
 *   │ galeria │ vidro │                │ vidro │ galeria │   ← quem assiste, em volta e acima
 *   │█████████│       │  ▪▪  ▪▪  ▪▪    │       │█████████│
 *   │█████████│ parede│  ▪▪  ▪▪  ▪▪    │ parede│█████████│   ← pilares de 2x2 sobre a água
 *   │█████████│ ~~~~~~~~~ água ~~~~~~~~~~~~~~ │█████████│
 * </pre>
 * Pilares de tronco da selva com topo de musgo, {@code vao} blocos entre eles, {@code altura} acima da
 * água (3 de fundo, com lanternas do mar no fundo). A galeria fica 5 blocos acima do topo dos pilares,
 * em volta dos 4 lados, separada da arena por um vidro do piso dela até o teto: quem luta não chega lá
 * nem voando com a carga de vento.
 * <p>
 * {@link #construir()} faz o prédio inteiro (uma vez, pela staff); {@link #resetar()} só ergue os
 * pilares de novo, a cada evento.
 */
public class ArenaVietna {

    /** Um pilar: o canto de menor x/z (ocupa x..x+1, z..z+1). */
    public record Pilar(int x, int z) {
    }

    private static final int GALERIA = 4; // largura da galeria

    private final String mundo;
    private final int aguaY, altura;
    private final int x0, z0, x1, z1; // a piscina por dentro (onde se luta)
    private final List<Pilar> pilares = new ArrayList<>();

    public ArenaVietna(ConfigurationSection cfg) {
        this.mundo = cfg.getString("mundo");
        List<Integer> centro = cfg.getIntegerList("centro");
        int cx = centro.size() > 0 ? centro.get(0) : 0, cz = centro.size() > 1 ? centro.get(1) : 0;
        this.aguaY = cfg.getInt("agua-y");
        this.altura = Math.max(2, cfg.getInt("altura"));
        int n = Math.max(2, cfg.getInt("pilares-por-lado"));
        int vao = Math.max(1, cfg.getInt("vao"));
        int lado = n * 2 + (n - 1) * vao;
        int inicioX = cx - lado / 2, inicioZ = cz - lado / 2;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) pilares.add(new Pilar(inicioX + i * (2 + vao), inicioZ + j * (2 + vao)));
        }
        this.x0 = inicioX - 3;
        this.z0 = inicioZ - 3;
        this.x1 = inicioX + lado + 2;
        this.z1 = inicioZ + lado + 2;
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    public List<Pilar> pilares() {
        return pilares;
    }

    private int topoPilar() {
        return aguaY + altura;
    }

    private int pisoGaleria() {
        return topoPilar() + 5;
    }

    private int teto() {
        return pisoGaleria() + 6;
    }

    /** Em cima do pilar, no meio dos 4 blocos. */
    public Location topo(Pilar p, float yaw) {
        return new Location(mundo(), p.x() + 1, topoPilar() + 1, p.z() + 1, yaw, 0);
    }

    /** Onde quem caiu (ou veio assistir) aparece: o meio da galeria norte, olhando pros pilares. */
    public Location arquibancada() {
        return new Location(mundo(), (x0 + x1) / 2.0 + 0.5, pisoGaleria() + 1, z0 - 1 - GALERIA / 2.0, 0, 35);
    }

    /** Dentro das paredes, da água ao teto (onde se luta). */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 && l.getX() < x1 + 1 && l.getZ() >= z0 && l.getZ() < z1 + 1
                && l.getY() >= aguaY - 4 && l.getY() < teto();
    }

    /** A galeria em volta (os 4 lados), acima da arena. */
    public boolean naArquibancada(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        boolean noAnel = l.getX() >= x0 - 1 - GALERIA && l.getX() < x1 + 2 + GALERIA && l.getZ() >= z0 - 1 - GALERIA
                && l.getZ() < z1 + 2 + GALERIA && !(l.getX() >= x0 - 1 && l.getX() < x1 + 2 && l.getZ() >= z0 - 1 && l.getZ() < z1 + 2);
        return noAnel && l.getY() >= pisoGaleria() && l.getY() < teto();
    }

    /** Caiu: os pés na altura da água (ou abaixo). */
    public boolean caiu(Location l) {
        return l.getY() < aguaY + 1.2;
    }

    /** [min, max] da caixa de PvP pro AreasPlugin (a piscina até o teto). */
    public int[][] caixa() {
        return new int[][]{{x0, aguaY - 4, z0}, {x1, teto(), z1}};
    }

    /** O prédio inteiro (e limpa o que sobrou das versões antigas em volta). */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        int ex0 = x0 - 2 - GALERIA, ez0 = z0 - 2 - GALERIA, ex1 = x1 + 2 + GALERIA, ez1 = z1 + 2 + GALERIA; // paredes de fora
        int base = aguaY - 3, galeria = pisoGaleria(), teto = teto();
        encher(w, ex0 - 3, base, ez0 - 6, ex1 + 3, teto + 6, ez1 + 3, Material.AIR);

        // o corpo do prédio: maciço até a galeria, com a piscina escavada
        encher(w, ex0, base, ez0, ex1, galeria - 1, ez1, Material.MOSSY_STONE_BRICKS);
        misturar(w, ex0, base, ez0, ex1, galeria - 1, ez1);
        encher(w, x0, aguaY - 2, z0, x1, galeria - 1, z1, Material.AIR);
        encher(w, x0, aguaY - 2, z0, x1, aguaY, z1, Material.WATER);
        for (int x = x0 + 1; x <= x1; x += 4) {
            for (int z = z0 + 1; z <= z1; z += 4) w.getBlockAt(x, aguaY - 3, z).setType(Material.SEA_LANTERN, false);
        }
        // paredes de dentro: colunas de tronco da selva e uma faixa de lanternas do mar acima da água
        for (int y = aguaY + 1; y < galeria; y++) {
            for (int x = x0; x <= x1; x += 5) {
                w.getBlockAt(x, y, z0 - 1).setType(Material.JUNGLE_LOG, false);
                w.getBlockAt(x, y, z1 + 1).setType(Material.JUNGLE_LOG, false);
            }
            for (int z = z0; z <= z1; z += 5) {
                w.getBlockAt(x0 - 1, y, z).setType(Material.JUNGLE_LOG, false);
                w.getBlockAt(x1 + 1, y, z).setType(Material.JUNGLE_LOG, false);
            }
        }
        for (int x = x0 + 2; x <= x1; x += 5) {
            w.getBlockAt(x, aguaY + 3, z0 - 1).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(x, aguaY + 3, z1 + 1).setType(Material.SEA_LANTERN, false);
        }
        for (int z = z0 + 2; z <= z1; z += 5) {
            w.getBlockAt(x0 - 1, aguaY + 3, z).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(x1 + 1, aguaY + 3, z).setType(Material.SEA_LANTERN, false);
        }
        // borda de madeira em volta da arena, no nível da galeria
        anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, galeria - 1, Material.STRIPPED_JUNGLE_LOG);

        // galeria: piso de madeira da selva, vidro do lado da arena até o teto, parede de fora com janelas
        encher(w, ex0 + 1, galeria, ez0 + 1, ex1 - 1, galeria, ez1 - 1, Material.JUNGLE_PLANKS);
        encher(w, x0, galeria, z0, x1, galeria, z1, Material.AIR);
        for (int y = galeria; y < teto; y++) anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, Material.GLASS);
        for (int y = galeria; y < teto; y++) anel(w, ex0, ez0, ex1, ez1, y, Material.MOSSY_STONE_BRICKS);
        for (int x = ex0; x <= ex1; x += 4) {
            for (int y = galeria; y < teto; y++) {
                w.getBlockAt(x, y, ez0).setType(Material.JUNGLE_LOG, false);
                w.getBlockAt(x, y, ez1).setType(Material.JUNGLE_LOG, false);
            }
            w.getBlockAt(x + 2, galeria + 2, ez0).setType(Material.GLASS_PANE, false);
            w.getBlockAt(x + 2, galeria + 2, ez1).setType(Material.GLASS_PANE, false);
        }
        for (int z = ez0; z <= ez1; z += 4) {
            for (int y = galeria; y < teto; y++) {
                w.getBlockAt(ex0, y, z).setType(Material.JUNGLE_LOG, false);
                w.getBlockAt(ex1, y, z).setType(Material.JUNGLE_LOG, false);
            }
            w.getBlockAt(ex0, galeria + 2, z + 2).setType(Material.GLASS_PANE, false);
            w.getBlockAt(ex1, galeria + 2, z + 2).setType(Material.GLASS_PANE, false);
        }
        // bancos (degraus de madeira) encostados na parede de fora, e lanternas no chão da galeria
        anel(w, ex0 + 1, ez0 + 1, ex1 - 1, ez1 - 1, galeria + 1, Material.JUNGLE_SLAB);
        for (int x = ex0 + 3; x < ex1; x += 6) {
            w.getBlockAt(x, galeria + 1, ez0 + 1).setType(Material.LANTERN, false);
            w.getBlockAt(x, galeria + 1, ez1 - 1).setType(Material.LANTERN, false);
        }

        // teto: madeira escura com claraboias e lanternas penduradas sobre a arena
        encher(w, ex0, teto, ez0, ex1, teto, ez1, Material.DARK_OAK_PLANKS);
        for (int x = x0 + 2; x <= x1 - 2; x += 6) {
            for (int z = z0 + 2; z <= z1 - 2; z += 6) {
                encher(w, x, teto, z, x + 1, teto, z + 1, Material.GLASS);
                pendurar(w, x - 2, teto - 1, z - 2);
            }
        }
        anel(w, ex0 - 1, ez0 - 1, ex1 + 1, ez1 + 1, teto, Material.JUNGLE_SLAB);
        resetar();
    }

    /** Os pilares de novo (a cada evento). */
    public void resetar() {
        for (Pilar p : pilares) erguer(p);
    }

    /** O pilar inteiro, do fundo ao topo. */
    public void erguer(Pilar p) {
        World w = mundo();
        encher(w, p.x(), aguaY - 2, p.z(), p.x() + 1, topoPilar() - 1, p.z() + 1, Material.JUNGLE_LOG);
        encher(w, p.x(), topoPilar(), p.z(), p.x() + 1, topoPilar(), p.z() + 1, Material.MOSS_BLOCK);
    }

    /** O aviso antes de sumir: o topo fica vermelho. */
    public void avisar(Pilar p) {
        encher(mundo(), p.x(), topoPilar(), p.z(), p.x() + 1, topoPilar(), p.z() + 1, Material.RED_CONCRETE);
    }

    /** Some (a água volta no lugar, quem está em cima cai). */
    public void derrubar(Pilar p) {
        World w = mundo();
        encher(w, p.x(), aguaY + 1, p.z(), p.x() + 1, topoPilar(), p.z() + 1, Material.AIR);
        encher(w, p.x(), aguaY - 2, p.z(), p.x() + 1, aguaY, p.z() + 1, Material.WATER);
    }

    /** Lanterna pendurada (com uma corrente) no teto. */
    private static void pendurar(World w, int x, int y, int z) {
        w.getBlockAt(x, y, z).setType(Material.IRON_CHAIN, false);
        w.getBlockAt(x, y - 1, z).setType(Material.LANTERN, false);
        if (w.getBlockAt(x, y - 1, z).getBlockData() instanceof Lantern l) {
            l.setHanging(true);
            w.getBlockAt(x, y - 1, z).setBlockData(l, false);
        }
    }

    /** Pedra com musgo, rachada e comum, misturadas, pra parede não ficar lisa. */
    private static void misturar(World w, int ax, int ay, int az, int bx, int by, int bz) {
        java.util.Random r = new java.util.Random(42);
        for (int x = ax; x <= bx; x++) {
            for (int y = ay; y <= by; y++) {
                for (int z = az; z <= bz; z++) {
                    if (x != ax && x != bx && z != az && z != bz) continue; // só a casca de fora
                    int s = r.nextInt(10);
                    if (s < 3) w.getBlockAt(x, y, z).setType(Material.CRACKED_STONE_BRICKS, false);
                    else if (s < 5) w.getBlockAt(x, y, z).setType(Material.STONE_BRICKS, false);
                }
            }
        }
    }

    /** O contorno de um retângulo numa altura. */
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
