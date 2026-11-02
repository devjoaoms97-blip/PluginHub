package com.example.eventos.vietna;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A arena do Vietnã, construída pelo plugin (de novo a cada evento, pra os pilares voltarem):
 * <pre>
 *   arquibancada (norte, atrás do vidro, acima dos pilares)
 *   ┌──────────── vidro ────────────┐
 *   │ ▪▪  ▪▪  ▪▪  ▪▪  ▪▪  ▪▪        │   pilares de 2x2 (tronco da selva, topo de musgo),
 *   │                               │   "vao" blocos entre eles, sobre a água
 *   │ ▪▪  ▪▪  ▪▪  ▪▪  ▪▪  ▪▪        │
 *   └───────────────────────────────┘
 * </pre>
 * A água tem 3 de fundo, os pilares sobem {@code altura} acima dela e o vidro em volta vai bem acima
 * dos pilares (quem voa por cima com a carga de vento sai da arena e está fora).
 */
public class ArenaVietna {

    /** Um pilar: o canto de menor x/z (ocupa x..x+1, z..z+1). */
    public record Pilar(int x, int z) {
    }

    private final String mundo;
    private final int aguaY, altura;
    private final int x0, z0, x1, z1; // piscina por dentro
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

    private int topoVidro() {
        return topoPilar() + 8;
    }

    private int pisoArquibancada() {
        return topoPilar() + 2;
    }

    /** Em cima do pilar, no meio dos 4 blocos. */
    public Location topo(Pilar p, float yaw) {
        return new Location(mundo(), p.x() + 1, topoPilar() + 1, p.z() + 1, yaw, 0);
    }

    /** Onde quem caiu (ou veio assistir) aparece: o meio da arquibancada, olhando pros pilares. */
    public Location arquibancada() {
        return new Location(mundo(), (x0 + x1) / 2.0 + 0.5, pisoArquibancada() + 1, z0 - 3.5, 0, 25);
    }

    /** Dentro da piscina e acima dela até o vidro (onde se luta). */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 && l.getX() < x1 + 1 && l.getZ() >= z0 && l.getZ() < z1 + 1
                && l.getY() >= aguaY - 4 && l.getY() < topoVidro() + 1;
    }

    /** A arquibancada (atrás do vidro norte). */
    public boolean naArquibancada(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x0 - 1 && l.getX() < x1 + 2 && l.getZ() >= z0 - 7 && l.getZ() < z0 - 1
                && l.getY() >= pisoArquibancada() - 1 && l.getY() < topoVidro() + 3;
    }

    /** Caiu: os pés na altura da água (ou abaixo). */
    public boolean caiu(Location l) {
        return l.getY() < aguaY + 1.2;
    }

    /** [min, max] da caixa de PvP pro AreasPlugin (a piscina até o vidro). */
    public int[][] caixa() {
        return new int[][]{{x0, aguaY - 4, z0}, {x1, topoVidro(), z1}};
    }

    /** Constrói tudo de novo (a piscina, os pilares, o vidro e a arquibancada). */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        // limpa (o ar acima de tudo, a arquibancada inclusive)
        encher(w, x0 - 1, aguaY - 3, z0 - 8, x1 + 1, topoVidro() + 4, z1 + 1, Material.AIR);
        // fundo, água e paredes de vidro
        encher(w, x0 - 1, aguaY - 3, z0 - 1, x1 + 1, aguaY - 3, z1 + 1, Material.SAND);
        encher(w, x0, aguaY - 2, z0, x1, aguaY, z1, Material.WATER);
        for (int y = aguaY - 2; y <= topoVidro(); y++) {
            encher(w, x0 - 1, y, z0 - 1, x1 + 1, y, z0 - 1, Material.GLASS);
            encher(w, x0 - 1, y, z1 + 1, x1 + 1, y, z1 + 1, Material.GLASS);
            encher(w, x0 - 1, y, z0 - 1, x0 - 1, y, z1 + 1, Material.GLASS);
            encher(w, x1 + 1, y, z0 - 1, x1 + 1, y, z1 + 1, Material.GLASS);
        }
        for (Pilar p : pilares) erguer(p);
        // arquibancada: dois degraus de madeira atrás do vidro norte, com parapeito
        int piso = pisoArquibancada();
        encher(w, x0 - 1, aguaY - 3, z0 - 4, x1 + 1, piso - 1, z0 - 2, Material.STONE_BRICKS);
        encher(w, x0 - 1, aguaY - 3, z0 - 7, x1 + 1, piso, z0 - 5, Material.STONE_BRICKS);
        encher(w, x0 - 1, piso, z0 - 4, x1 + 1, piso, z0 - 2, Material.DARK_OAK_PLANKS);
        encher(w, x0 - 1, piso + 1, z0 - 7, x1 + 1, piso + 1, z0 - 5, Material.DARK_OAK_PLANKS);
        for (int y = piso + 1; y <= piso + 3; y++) {
            encher(w, x0 - 2, y, z0 - 8, x0 - 2, y, z0 - 2, Material.GLASS);
            encher(w, x1 + 2, y, z0 - 8, x1 + 2, y, z0 - 2, Material.GLASS);
        }
        encher(w, x0 - 2, piso + 2, z0 - 8, x1 + 2, piso + 4, z0 - 8, Material.GLASS);
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

    private static void encher(World w, int ax, int ay, int az, int bx, int by, int bz, Material m) {
        for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
            for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++) {
                for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) w.getBlockAt(x, y, z).setType(m, false);
            }
        }
    }
}
