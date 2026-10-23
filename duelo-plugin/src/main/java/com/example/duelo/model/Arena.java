package com.example.duelo.model;

import org.bukkit.Location;

/** Arena de X1: um ponto de partida pra cada lutador. Só um duelo por arena de cada vez. */
public class Arena {

    private final String nome;
    private Location pos1;
    private Location pos2;
    private boolean ocupada; // só em memória

    public Arena(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

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

    public boolean pronta() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos2.getWorld() != null;
    }

    public boolean isOcupada() {
        return ocupada;
    }

    public void setOcupada(boolean ocupada) {
        this.ocupada = ocupada;
    }
}
