package com.example.rankup.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uma mina: um cubo (min..max, inclusive) que é preenchido de novo com a {@link #getComposicao()
 * composição} de tempos em tempos ou quando boa parte dela foi minerada. Só entra quem tem pelo
 * menos o {@link #getRankMinimo() rank mínimo}.
 */
public class Mina {

    private final String id;
    private final String mundo;
    private final BlocoPos min;
    private final BlocoPos max;
    private String rankMinimo;
    /** Material -> peso (não precisa somar 100; é proporcional). Ordem de inserção mantida pro /mina admin info. */
    private final Map<Material, Double> composicao = new LinkedHashMap<>();
    /** Ponto de teleporte (x, y, z, yaw, pitch) no mundo da mina; null = em cima do centro. */
    private double[] spawn;
    private int intervaloMinutos;
    private double resetPercentual;

    // Estado em memória (não é salvo)
    private long proximoReset;
    private long quebrados;
    private boolean resetando;

    public Mina(String id, String mundo, BlocoPos a, BlocoPos b, String rankMinimo, int intervaloMinutos, double resetPercentual) {
        this.id = id;
        this.mundo = mundo;
        this.min = new BlocoPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        this.max = new BlocoPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        this.rankMinimo = rankMinimo;
        this.intervaloMinutos = intervaloMinutos;
        this.resetPercentual = resetPercentual;
    }

    public boolean contem(Location local) {
        if (local.getWorld() == null || !local.getWorld().getName().equals(mundo)) return false;
        return contem(local.getBlockX(), local.getBlockY(), local.getBlockZ());
    }

    public boolean contem(int x, int y, int z) {
        return x >= min.x() && x <= max.x() && y >= min.y() && y <= max.y() && z >= min.z() && z <= max.z();
    }

    /** Dentro da mina ou logo acima dela (até 3 blocos) — quem está aqui é tirado antes do reset. */
    public boolean contemOuEmCima(Location local) {
        if (local.getWorld() == null || !local.getWorld().getName().equals(mundo)) return false;
        int x = local.getBlockX();
        int y = local.getBlockY();
        int z = local.getBlockZ();
        return x >= min.x() && x <= max.x() && z >= min.z() && z <= max.z() && y >= min.y() && y <= max.y() + 3;
    }

    public boolean sobrepoe(String outroMundo, BlocoPos outroMin, BlocoPos outroMax) {
        return mundo.equals(outroMundo)
                && min.x() <= outroMax.x() && max.x() >= outroMin.x()
                && min.y() <= outroMax.y() && max.y() >= outroMin.y()
                && min.z() <= outroMax.z() && max.z() >= outroMin.z();
    }

    public long getVolume() {
        return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }

    public World getWorld() {
        return Bukkit.getWorld(mundo);
    }

    /** Onde o /mina teleporta (e pra onde vai quem estava dentro no reset). */
    public Location getLocalSpawn() {
        World world = getWorld();
        if (world == null) return null;
        if (spawn != null) {
            return new Location(world, spawn[0], spawn[1], spawn[2], (float) spawn[3], (float) spawn[4]);
        }
        return new Location(world, (min.x() + max.x()) / 2.0 + 0.5, max.y() + 1, (min.z() + max.z()) / 2.0 + 0.5);
    }

    public void setSpawn(Location local) {
        this.spawn = local == null ? null
                : new double[]{local.getX(), local.getY(), local.getZ(), local.getYaw(), local.getPitch()};
    }

    public double[] getSpawn() {
        return spawn;
    }

    public void setSpawn(double[] spawn) {
        this.spawn = spawn;
    }

    public String getId() {
        return id;
    }

    public String getMundo() {
        return mundo;
    }

    public BlocoPos getMin() {
        return min;
    }

    public BlocoPos getMax() {
        return max;
    }

    public String getRankMinimo() {
        return rankMinimo;
    }

    public void setRankMinimo(String rankMinimo) {
        this.rankMinimo = rankMinimo;
    }

    public Map<Material, Double> getComposicao() {
        return composicao;
    }

    public int getIntervaloMinutos() {
        return intervaloMinutos;
    }

    public void setIntervaloMinutos(int intervaloMinutos) {
        this.intervaloMinutos = intervaloMinutos;
    }

    public double getResetPercentual() {
        return resetPercentual;
    }

    public void setResetPercentual(double resetPercentual) {
        this.resetPercentual = resetPercentual;
    }

    public long getProximoReset() {
        return proximoReset;
    }

    public void setProximoReset(long proximoReset) {
        this.proximoReset = proximoReset;
    }

    public long getQuebrados() {
        return quebrados;
    }

    public void setQuebrados(long quebrados) {
        this.quebrados = quebrados;
    }

    public boolean isResetando() {
        return resetando;
    }

    public void setResetando(boolean resetando) {
        this.resetando = resetando;
    }
}
