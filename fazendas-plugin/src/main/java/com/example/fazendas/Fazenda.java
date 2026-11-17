package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma fazenda colocada. (x, y, z) é o centro, na altura dos pés do NPC: o chão da construção fica em
 * y - 1, e a área protegida vai de x-3..x+3, z-3..z+3 e y-1 até y + altura - 1.
 */
final class Fazenda {

    static final int RAIO = 3;

    final String id;
    final String mundo;
    final int x, y, z;
    final String tipo;
    final UUID dono;
    final float yaw;
    /** Quantas camadas acima do chão a construção ocupa (a altura livre no momento em que foi colocada). */
    final int altura;
    int nivelProd, nivelCofre, nivelDespensa;
    double insumo;
    /** Safras guardadas no cofre (o dinheiro é safras × renda do tipo). */
    int safras;
    long parcialMs;
    long ultima;
    UUID npc, holograma;
    /** Os blocos originais da área (pra devolver ao recolher), na ordem de {@link Construcao#percorrer}. */
    List<String> originais = new ArrayList<>();

    Fazenda(String id, String mundo, int x, int y, int z, String tipo, UUID dono, float yaw, int altura) {
        this.id = id;
        this.mundo = mundo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tipo = tipo;
        this.dono = dono;
        this.yaw = yaw;
        this.altura = altura;
    }

    World world() {
        return Bukkit.getWorld(mundo);
    }

    Location centro() {
        World w = world();
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5, yaw, 0);
    }

    /** O lado da frente (pra onde o NPC olha): {x, z}, um de norte, sul, leste ou oeste. */
    int[] frente() {
        return frente(yaw);
    }

    static int[] frente(float yaw) {
        double rad = Math.toRadians(yaw);
        return new int[]{(int) Math.round(-Math.sin(rad)), (int) Math.round(Math.cos(rad))};
    }

    /**
     * O NPC fica na abertura da cerca, na frente, virado pra fora: no centro, ficava a mais de 3 blocos
     * de quem está do lado de fora (o alcance pra clicar numa entidade).
     */
    Location lugarDoNpc() {
        World w = world();
        int[] f = frente();
        return w == null ? null : new Location(w, x + f[0] * RAIO + 0.5, y, z + f[1] * RAIO + 0.5, yaw, 0);
    }

    /** O bloco (bx, by, bz) desse mundo está na área da construção? */
    boolean contem(String mundo, int bx, int by, int bz) {
        return this.mundo.equals(mundo) && Math.abs(bx - x) <= RAIO && Math.abs(bz - z) <= RAIO && by >= y - 1 && by <= y + altura - 1;
    }

    /** As duas áreas se encostam ou se sobrepõem (na horizontal, sem olhar a altura)? */
    static boolean sobrepoe(int ax, int az, int bx, int bz) {
        return Math.abs(ax - bx) <= 2 * RAIO && Math.abs(az - bz) <= 2 * RAIO;
    }
}
