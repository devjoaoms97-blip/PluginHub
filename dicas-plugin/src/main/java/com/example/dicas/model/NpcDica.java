package com.example.dicas.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Um NPC de dicas: parado num lugar, com um nome em cima e uma lista de falas. Cada clique mostra a
 * próxima fala (a ordem é por jogador). {@code skin} é o nick de quem empresta a skin, ou null pra
 * skin padrão.
 */
public class NpcDica {

    private final String id;
    private String nome;
    private String mundo;
    private double x, y, z;
    private float yaw;
    private String skin;
    private final List<String> falas = new ArrayList<>();

    public NpcDica(String id, String nome, Location local) {
        this.id = id;
        this.nome = nome;
        setLocal(local);
    }

    public NpcDica(String id, String nome, String mundo, double x, double y, double z, float yaw) {
        this.id = id;
        this.nome = nome;
        this.mundo = mundo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    /** O lugar, ou null se o mundo não estiver carregado. */
    public Location getLocal() {
        World w = Bukkit.getWorld(mundo);
        return w == null ? null : new Location(w, x, y, z, yaw, 0);
    }

    public void setLocal(Location local) {
        this.mundo = local.getWorld().getName();
        this.x = local.getX();
        this.y = local.getY();
        this.z = local.getZ();
        this.yaw = local.getYaw();
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getMundo() {
        return mundo;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public String getSkin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public List<String> getFalas() {
        return falas;
    }
}
