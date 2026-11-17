package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fence;

/**
 * A construção 7x7 de cada fazenda. Antes de construir, guarda todos os blocos originais da área
 * (chão e camadas acima); ao recolher, devolve cada um como estava. Os blocos vão sem física
 * (applyPhysics false): nada cai nem dropa, e as cercas vêm com as conexões já calculadas.
 *
 * <p>Por enquanto (fase 1), todos os tipos usam o mesmo plano: chão de grama com borda de tábuas,
 * cerca com troncos nos cantos e lanternas em cima, e uma abertura na frente, onde fica o NPC. Os planos temáticos de cada tipo entram no
 * {@link #plano} depois.
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

    /**
     * O bloco do plano na posição relativa ao centro (null = ar). {@code frente} ({x, z}): o lado da
     * abertura da cerca, onde fica o NPC.
     */
    static BlockData plano(FazendasPlugin.Tipo tipo, int[] frente, int dx, int dy, int dz) {
        int r = Fazenda.RAIO;
        int ax = frente[0] * r, az = frente[1] * r; // a abertura
        boolean bordaX = Math.abs(dx) == r, bordaZ = Math.abs(dz) == r;
        boolean borda = bordaX || bordaZ, canto = bordaX && bordaZ;
        if (dy == -1) {
            if (borda) return Bukkit.createBlockData(Material.SPRUCE_PLANKS);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return Bukkit.createBlockData(Material.COARSE_DIRT);
            return Bukkit.createBlockData(Material.GRASS_BLOCK);
        }
        if (dy == 0 && canto) return Bukkit.createBlockData(Material.SPRUCE_LOG);
        if (dy == 0 && dx == ax && dz == az) return null;
        if (dy == 0 && borda) {
            Fence cerca = (Fence) Bukkit.createBlockData(Material.SPRUCE_FENCE);
            // a cerca liga com as vizinhas da borda (e com os troncos dos cantos), menos pro lado da abertura
            BlockFace[] lados = bordaZ ? new BlockFace[]{BlockFace.EAST, BlockFace.WEST} : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            for (BlockFace lado : lados) {
                if (dx + lado.getModX() == ax && dz + lado.getModZ() == az) continue;
                cerca.setFace(lado, true);
            }
            return cerca;
        }
        if (dy == 1 && canto) return Bukkit.createBlockData(Material.LANTERN);
        return null;
    }

    /** Guarda os originais da área e constrói o plano do tipo. */
    static void construir(Fazenda f, FazendasPlugin.Tipo tipo) {
        World w = f.world();
        f.originais.clear();
        BlockData ar = Bukkit.createBlockData(Material.AIR);
        percorrer(f.altura, (dx, dy, dz) -> f.originais.add(w.getBlockAt(f.x + dx, f.y + dy, f.z + dz).getBlockData().getAsString()));
        percorrer(f.altura, (dx, dy, dz) -> {
            BlockData d = plano(tipo, f.frente(), dx, dy, dz);
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
