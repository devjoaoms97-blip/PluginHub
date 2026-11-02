package com.example.eventos.spleef;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A arena do Spleef: uma torre redonda e fechada, de tema de inverno. {@code andares} pisos de neve de
 * {@code raio} blocos, um a cada {@code distancia-andares} acima da água, dentro de uma parede de gelo
 * compactado com faixas de gelo azul e lanternas do mar (uma por andar). A galeria de quem assiste é um
 * anel em volta, 5 blocos acima do piso de cima, separada da arena por um vidro até o teto (de gelo, com
 * luzes). Por fora, a torre é de pinheiro e neve.
 * <p>
 * {@link #construir()} faz a torre inteira (uma vez, pela staff); {@link #refazerPisos()} só refaz a neve,
 * a cada evento.
 */
public class ArenaSpleef {

    private static final int GALERIA = 4; // largura do anel da galeria

    private final String mundo;
    private final int cx, cz, aguaY, raio, andares, distancia;
    /** As colunas (x, z) de neve de cada piso: dentro do círculo. */
    private final List<int[]> colunas = new ArrayList<>();

    public ArenaSpleef(ConfigurationSection cfg) {
        this.mundo = cfg.getString("mundo");
        List<Integer> centro = cfg.getIntegerList("centro");
        this.cx = centro.size() > 0 ? centro.get(0) : 0;
        this.cz = centro.size() > 1 ? centro.get(1) : 0;
        this.aguaY = cfg.getInt("agua-y");
        this.raio = Math.max(4, cfg.getInt("raio"));
        this.andares = Math.max(1, cfg.getInt("andares"));
        this.distancia = Math.max(3, cfg.getInt("distancia-andares"));
        for (int x = cx - raio; x <= cx + raio; x++) {
            for (int z = cz - raio; z <= cz + raio; z++) if (dist(x + 0.5, z + 0.5) <= raio) colunas.add(new int[]{x, z});
        }
    }

    private double dist(double x, double z) {
        double dx = x - (cx + 0.5), dz = z - (cz + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    /** A altura do piso (0 = o de baixo). */
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

    /** O raio de dentro da parede (até onde vai a água). */
    private int raioParede() {
        return raio + 1;
    }

    private int raioFora() {
        return raioParede() + 1 + GALERIA + 1;
    }

    public int andares() {
        return andares;
    }

    /** O andar em que esse y está (o piso logo abaixo dos pés), ou -1 abaixo do primeiro. */
    public int andarEm(double y) {
        for (int a = andares - 1; a >= 0; a--) if (y >= yDoPiso(a) + 0.5) return a;
        return -1;
    }

    public List<int[]> colunas() {
        return colunas;
    }

    /** Um bloco do piso de neve (qualquer andar). */
    public boolean ehPiso(Block b) {
        if (!b.getWorld().getName().equals(mundo) || dist(b.getX() + 0.5, b.getZ() + 0.5) > raio) return false;
        int dy = b.getY() - aguaY;
        return dy > 0 && dy % distancia == 0 && dy / distancia <= andares;
    }

    /** Começo: no piso de cima, em roda, olhando pro centro. */
    public List<Location> pontosDeEntrada(int quantos) {
        List<Location> pontos = new ArrayList<>();
        int n = Math.max(1, quantos);
        double r = raio * 0.6;
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n;
            double x = cx + 0.5 + r * Math.cos(ang), z = cz + 0.5 + r * Math.sin(ang);
            float yaw = (float) Math.toDegrees(Math.atan2(-(cx + 0.5 - x), cz + 0.5 - z));
            pontos.add(new Location(mundo(), Math.floor(x) + 0.5, topo() + 1, Math.floor(z) + 0.5, yaw, 0));
        }
        return pontos;
    }

    /** O lado norte da galeria, olhando pro centro. */
    public Location arquibancada() {
        return new Location(mundo(), cx + 0.5, pisoGaleria() + 1, cz + 0.5 - (raioParede() + 1 + GALERIA / 2.0), 0, 35);
    }

    /** Dentro da parede, da água ao teto. */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo) && dist(l.getX(), l.getZ()) < raioParede()
                && l.getY() >= aguaY - 4 && l.getY() < teto();
    }

    /** O anel da galeria. */
    public boolean naArquibancada(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        double d = dist(l.getX(), l.getZ());
        return d >= raioParede() && d < raioFora() && l.getY() >= pisoGaleria() && l.getY() < teto();
    }

    public boolean caiu(Location l) {
        return l.getY() < aguaY + 1.2;
    }

    /** [min, max] da caixa pro AreasPlugin (até a parede, da água ao teto). */
    public int[][] caixa() {
        return new int[][]{{cx - raioParede(), aguaY - 4, cz - raioParede()}, {cx + raioParede(), teto(), cz + raioParede()}};
    }

    /** A torre inteira (e limpa o que sobrou das versões antigas em volta). */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        int rf = raioFora(), base = aguaY - 3, galeria = pisoGaleria(), teto = teto();
        for (int x = cx - rf - 4; x <= cx + rf + 4; x++) {
            for (int z = cz - rf - 12; z <= cz + rf + 4; z++) {
                for (int y = base; y <= teto + 6; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }
        for (int x = cx - rf; x <= cx + rf; x++) {
            for (int z = cz - rf; z <= cz + rf; z++) {
                double d = dist(x + 0.5, z + 0.5);
                if (d >= rf + 0.5) continue;
                boolean fora = d >= rf - 0.5;          // a parede de fora
                boolean dentro = d < raioParede();     // a arena
                boolean parede = !dentro && d < raioParede() + 1; // a parede da arena
                int angulo = (int) Math.round(Math.toDegrees(Math.atan2(z + 0.5 - cz - 0.5, x + 0.5 - cx - 0.5)) + 360) % 360;
                w.getBlockAt(x, base, z).setType(dentro && (x + z) % 4 == 0 ? Material.SEA_LANTERN : Material.SNOW_BLOCK, false);
                for (int y = base + 1; y <= teto; y++) {
                    Material m;
                    if (y == teto) {
                        m = fora ? Material.SPRUCE_PLANKS : (dentro && (x + z) % 5 == 0 && (x - z) % 5 == 0) ? Material.OCHRE_FROGLIGHT : Material.PACKED_ICE;
                    } else if (dentro) {
                        m = y <= aguaY ? Material.WATER : Material.AIR;
                    } else if (parede) {
                        // gelo compactado, faixa de gelo azul na altura de cada piso e uma lanterna do mar por andar
                        if (y >= galeria) m = Material.GLASS;
                        else if ((y - aguaY) % distancia == 0 && y > aguaY) m = angulo % 20 < 3 ? Material.SEA_LANTERN : Material.BLUE_ICE;
                        else m = Material.PACKED_ICE;
                    } else if (fora) {
                        // por fora: troncos de pinheiro em pé, neve entre eles e janelas na galeria
                        boolean coluna = angulo % 15 < 4;
                        if (y > galeria && y < teto - 1 && !coluna && angulo % 15 >= 7 && angulo % 15 < 11) m = Material.GLASS_PANE;
                        else m = coluna ? Material.SPRUCE_LOG : y < galeria ? Material.SNOW_BLOCK : Material.STRIPPED_SPRUCE_LOG;
                    } else {
                        // o anel da galeria: maciço até o piso, de pinheiro, com um banco na borda de fora
                        if (y < galeria) m = Material.POLISHED_DIORITE;
                        else if (y == galeria) m = Material.SPRUCE_PLANKS;
                        else if (y == galeria + 1 && d >= rf - 1.5) m = Material.SPRUCE_SLAB;
                        else m = Material.AIR;
                    }
                    w.getBlockAt(x, y, z).setType(m, false);
                }
            }
        }
        // lanternas na galeria e penduradas no teto da arena
        for (int a = 0; a < 360; a += 30) {
            double r = rf - 1.5, ang = Math.toRadians(a);
            int x = (int) Math.floor(cx + 0.5 + r * Math.cos(ang)), z = (int) Math.floor(cz + 0.5 + r * Math.sin(ang));
            w.getBlockAt(x, galeria + 1, z).setType(Material.LANTERN, false);
        }
        for (int a = 0; a < 360; a += 45) {
            double r = raio * 0.55, ang = Math.toRadians(a);
            pendurar(w, (int) Math.floor(cx + 0.5 + r * Math.cos(ang)), teto - 1, (int) Math.floor(cz + 0.5 + r * Math.sin(ang)));
        }
        pendurar(w, cx, teto - 1, cz);
        refazerPisos();
    }

    /** Os pisos de neve inteiros (a cada evento). */
    public void refazerPisos() {
        World w = mundo();
        for (int a = 0; a < andares; a++) {
            int y = yDoPiso(a);
            for (int[] c : colunas) w.getBlockAt(c[0], y, c[1]).setType(Material.SNOW_BLOCK, false);
        }
    }

    /** Lanterna pendurada (com uma corrente). */
    private static void pendurar(World w, int x, int y, int z) {
        w.getBlockAt(x, y, z).setType(Material.IRON_CHAIN, false);
        Block b = w.getBlockAt(x, y - 1, z);
        b.setType(Material.LANTERN, false);
        if (b.getBlockData() instanceof Lantern l) {
            l.setHanging(true);
            b.setBlockData(l, false);
        }
    }
}
