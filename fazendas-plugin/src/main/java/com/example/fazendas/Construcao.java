package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * A construção 7x7 de cada fazenda. Antes de construir, guarda todos os blocos originais da área
 * (chão e camadas acima); ao recolher, devolve cada um como estava. Os blocos vão sem física
 * (applyPhysics false): nada cai nem dropa, e as cercas vêm com as conexões já calculadas.
 *
 * <p>O desenho de cada tipo está nos {@link Planos}, com a frente ao norte; aqui ele é girado pra
 * frente da fazenda (o lado de quem colocou), junto com os blocos que têm direção.
 */
final class Construcao {

    private Construcao() {
    }

    interface Visitante {
        void visitar(int dx, int dy, int dz);
    }

    /** Toda a área, na ordem em que os originais são guardados: do chão (dy -1) pra cima, x, depois z. */
    static void percorrer(int altura, Visitante v) {
        for (int dy = -1; dy <= altura - 1; dy++) {
            for (int dx = -Fazenda.RAIO; dx <= Fazenda.RAIO; dx++) {
                for (int dz = -Fazenda.RAIO; dz <= Fazenda.RAIO; dz++) v.visitar(dx, dy, dz);
            }
        }
    }

    /** A rotação que leva o norte (a frente do desenho) pra frente da fazenda. */
    static StructureRotation rotacao(int[] frente) {
        if (frente[0] == 1) return StructureRotation.CLOCKWISE_90;
        if (frente[0] == -1) return StructureRotation.COUNTERCLOCKWISE_90;
        return frente[1] == 1 ? StructureRotation.CLOCKWISE_180 : StructureRotation.NONE;
    }

    /** Coordenada local (lx, lz) → deslocamento no mundo {dx, dz}, com a frente {@code frente}. */
    static double[] paraMundo(int[] frente, double lx, double lz) {
        // direita = (-fz, fx); fundo = (-fx, -fz)
        return new double[]{lx * -frente[1] + lz * -frente[0], lx * frente[0] + lz * -frente[1]};
    }

    /** Quanto o yaw gira junto com o desenho (o yaw do Minecraft cresce no sentido horário). */
    static float giroYaw(int[] frente) {
        return switch (rotacao(frente)) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> -90;
            default -> 0;
        };
    }

    /** O bloco do desenho na posição (dx, dy, dz) do mundo, relativa ao centro (null = ar). */
    static BlockData plano(FazendasPlugin.Tipo tipo, int[] frente, StructureRotation rot, int dx, int dy, int dz) {
        // mundo → local: lx = d·direita, lz = d·fundo
        int lx = dx * -frente[1] + dz * frente[0];
        int lz = dx * -frente[0] + dz * -frente[1];
        BlockData d = Planos.bloco(tipo.id(), lx, dy, lz);
        if (d != null && rot != StructureRotation.NONE) d.rotate(rot);
        return d;
    }

    /** Guarda os originais da área e constrói o desenho do tipo. */
    static void construir(Fazenda f, FazendasPlugin.Tipo tipo) {
        World w = f.world();
        f.originais.clear();
        BlockData ar = Bukkit.createBlockData(Material.AIR);
        int[] frente = f.frente();
        StructureRotation rot = rotacao(frente);
        percorrer(f.altura, (dx, dy, dz) -> f.originais.add(w.getBlockAt(f.x + dx, f.y + dy, f.z + dz).getBlockData().getAsString()));
        percorrer(f.altura, (dx, dy, dz) -> {
            BlockData d = plano(tipo, frente, rot, dx, dy, dz);
            w.getBlockAt(f.x + dx, f.y + dy, f.z + dz).setBlockData(d == null ? ar : d, false);
        });
    }

    /** Devolve cada bloco da área como estava antes da fazenda. */
    static void restaurar(Fazenda f) {
        World w = f.world();
        if (w == null || f.originais.isEmpty()) return;
        int[] i = {0};
        percorrer(f.altura, (dx, dy, dz) -> {
            if (i[0] >= f.originais.size()) return;
            String original = f.originais.get(i[0]++);
            Block b = w.getBlockAt(f.x + dx, f.y + dy, f.z + dz);
            BlockData d;
            try {
                d = Bukkit.createBlockData(original);
            } catch (IllegalArgumentException e) {
                d = Bukkit.createBlockData(dy == -1 ? Material.DIRT : Material.AIR);
            }
            b.setBlockData(d, false);
        });
    }
}
