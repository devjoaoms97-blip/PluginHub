package com.example.recursos;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Teleporte pros mundos de recursos. No normal e no Nether, um ponto aleatório seguro dentro da borda
 * e do raio do mundo: em cima de um bloco sólido, sem água, lava, cacto, fogo, magma ou neve fofa (no
 * Nether, procurando o chão de baixo pra cima, abaixo do teto de bedrock). Os chunks são carregados
 * sem travar o servidor (getChunkAtAsync), um ponto de cada vez. No End, sempre o quadrado verde do
 * centro ({@link CentroDoEnd}).
 */
public class Teleporte {

    private static final Set<Material> PERIGOSOS = Set.of(Material.WATER, Material.LAVA, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE, Material.BEDROCK);

    /** No Nether, o chão é procurado entre essas alturas (o teto de bedrock fica em 127). */
    private static final int NETHER_MIN = 32, NETHER_MAX = 115;

    private final RecursosPlugin plugin;

    public Teleporte(RecursosPlugin plugin) {
        this.plugin = plugin;
    }

    /** Procura um lugar seguro no mundo e leva o jogador. Completa com true se conseguiu. */
    public CompletableFuture<Boolean> levar(Player jogador, ResetManager m) {
        World mundo = m.getWorld();
        if (mundo == null) return CompletableFuture.completedFuture(false);
        if (mundo.getEnvironment() == World.Environment.THE_END) {
            Location centro = plugin.getCentroDoEnd().chegada(mundo);
            return centro == null ? CompletableFuture.completedFuture(false) : jogador.teleportAsync(centro);
        }
        return tentar(jogador, mundo, m.getRaio(), Math.max(1, plugin.getConfig().getInt("teleporte.tentativas", 20)));
    }

    private CompletableFuture<Boolean> tentar(Player jogador, World mundo, int raioConfig, int restantes) {
        if (restantes <= 0 || !jogador.isOnline()) return CompletableFuture.completedFuture(false);
        int raio = (int) Math.min(raioConfig, raioDaBorda(mundo));
        WorldBorder borda = mundo.getWorldBorder();
        int x = (int) borda.getCenter().getX() + ThreadLocalRandom.current().nextInt(-raio, raio + 1);
        int z = (int) borda.getCenter().getZ() + ThreadLocalRandom.current().nextInt(-raio, raio + 1);
        return mundo.getChunkAtAsync(x >> 4, z >> 4).thenCompose(chunk -> {
            Location seguro = lugarSeguro(mundo, x, z);
            if (seguro == null) return tentar(jogador, mundo, raioConfig, restantes - 1);
            return jogador.teleportAsync(seguro);
        });
    }

    private double raioDaBorda(World mundo) {
        double tamanho = mundo.getWorldBorder().getSize();
        return tamanho > 1_000_000 ? Integer.MAX_VALUE : tamanho / 2 - 16; // folga pra não cair na borda
    }

    /** Um lugar seguro em (x, z), ou null. */
    public Location lugarSeguro(World mundo, int x, int z) {
        if (mundo.getEnvironment() == World.Environment.NETHER) {
            // de baixo pra cima, o primeiro chão firme com espaço livre em cima
            for (int y = NETHER_MIN; y <= NETHER_MAX; y++) {
                Location l = emCima(mundo.getBlockAt(x, y, z));
                if (l != null) return l;
            }
            return null;
        }
        Block chao = mundo.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (chao.getY() <= mundo.getMinHeight() + 1) return null;
        return emCima(chao);
    }

    /** Em cima do bloco, se ele for chão firme e os dois de cima estiverem livres. */
    private static Location emCima(Block chao) {
        if (!chao.getType().isSolid() || PERIGOSOS.contains(chao.getType())) return null;
        Block pe = chao.getRelative(0, 1, 0), cabeca = chao.getRelative(0, 2, 0);
        if (!pe.isPassable() || !cabeca.isPassable() || PERIGOSOS.contains(pe.getType()) || PERIGOSOS.contains(cabeca.getType())
                || pe.isLiquid() || cabeca.isLiquid()) return null;
        return new Location(chao.getWorld(), chao.getX() + 0.5, chao.getY() + 1, chao.getZ() + 0.5);
    }
}
