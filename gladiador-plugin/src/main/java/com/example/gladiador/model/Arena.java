package com.example.gladiador.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * A arena do Gladiador: uma caixa (pos1/pos2) de onde ninguém pode sair durante a luta e os
 * pontos onde os participantes aparecem, distribuídos em rodízio.
 */
public class Arena {

    private Location pos1;
    private Location pos2;
    private final List<Location> spawns = new ArrayList<>();

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public List<Location> getSpawns() {
        return spawns;
    }

    public boolean pronta() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld()) && !spawns.isEmpty();
    }

    /** Dentro da caixa (com 1 bloco de folga na altura, pra pulo e queda não contarem). */
    public boolean contem(Location local) {
        if (!pronta() || local.getWorld() == null || !local.getWorld().equals(pos1.getWorld())) return false;
        return entre(local.getX(), pos1.getX(), pos2.getX(), 0)
                && entre(local.getY(), pos1.getY(), pos2.getY(), 1)
                && entre(local.getZ(), pos1.getZ(), pos2.getZ(), 0);
    }

    private static boolean entre(double v, double a, double b, double folga) {
        return v >= Math.floor(Math.min(a, b)) - folga && v < Math.floor(Math.max(a, b)) + 1 + folga;
    }
}
