package com.example.terrenos.mundo;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Gera o mundo de terrenos inteiro a partir da {@link Grade}: sem cavernas, estruturas, árvores
 * nem mobs naturais, e com um bioma só (sem isso o servidor sorteia biomas, e nos frios cai neve
 * e a grama fica azulada). Usado pelo Multiverse com {@code --generator TerrenosPlugin}.
 * A grade e o bioma vêm de Suppliers porque o mundo pode ser criado antes do config ser recarregado.
 */
public class GeradorTerrenos extends ChunkGenerator {

    private final Supplier<Grade> grade;
    private final Supplier<Biome> bioma;

    public GeradorTerrenos(Supplier<Grade> grade, Supplier<Biome> bioma) {
        this.grade = grade;
        this.bioma = bioma;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return bioma.get();
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(bioma.get());
            }
        };
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData dados) {
        Grade g = grade.get();
        int minY = info.getMinHeight();
        int topo = Math.min(info.getMaxHeight() - 1, g.getAlturaChao() + 1);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkX * 16 + dx, z = chunkZ * 16 + dz;
                for (int y = g.fundo(minY); y <= topo; y++) {
                    dados.setBlock(dx, y, dz, g.blocoEm(x, y, z, minY));
                }
            }
        }
    }

    @Override
    public Location getFixedSpawnLocation(World mundo, Random random) {
        // na rua, entre os quatro primeiros terrenos
        Grade g = grade.get();
        double meioDaRua = g.getTamanho() + (g.celula() - g.getTamanho()) / 2.0;
        return new Location(mundo, meioDaRua, g.getAlturaChao() + 1, meioDaRua);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
