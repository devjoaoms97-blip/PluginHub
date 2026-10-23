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
 * Teleporte pra um ponto aleatório seguro do mundo de recursos: dentro da borda e do raio do
 * config, em cima de um bloco sólido, sem água, lava, cacto, fogo, magma ou neve fofa. Os chunks
 * são carregados sem travar o servidor (getChunkAtAsync), um ponto de cada vez.
 */
public class Teleporte {

    private static final Set<Material> PERIGOSOS = Set.of(Material.WATER, Material.LAVA, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE);

    private final RecursosPlugin plugin;

    public Teleporte(RecursosPlugin plugin) {
        this.plugin = plugin;
    }

    /** Procura um lugar seguro e leva o jogador. Completa com true se conseguiu. */
    public CompletableFuture<Boolean> levar(Player jogador) {
        World mundo = plugin.getMundo();
        if (mundo == null) return CompletableFuture.completedFuture(false);
        return tentar(jogador, mundo, Math.max(1, plugin.getConfig().getInt("teleporte.tentativas", 20)));
    }

    private CompletableFuture<Boolean> tentar(Player jogador, World mundo, int restantes) {
        if (restantes <= 0 || !jogador.isOnline()) return CompletableFuture.completedFuture(false);
        int raio = (int) Math.min(plugin.getConfig().getInt("teleporte.raio", 2500), raioDaBorda(mundo));
        WorldBorder borda = mundo.getWorldBorder();
        int x = (int) borda.getCenter().getX() + ThreadLocalRandom.current().nextInt(-raio, raio + 1);
        int z = (int) borda.getCenter().getZ() + ThreadLocalRandom.current().nextInt(-raio, raio + 1);
        return mundo.getChunkAtAsync(x >> 4, z >> 4).thenCompose(chunk -> {
            Location seguro = lugarSeguro(mundo, x, z);
            if (seguro == null) return tentar(jogador, mundo, restantes - 1);
            return jogador.teleportAsync(seguro);
        });
    }

    private double raioDaBorda(World mundo) {
        double tamanho = mundo.getWorldBorder().getSize();
        return tamanho > 1_000_000 ? Integer.MAX_VALUE : tamanho / 2 - 16; // folga pra não cair na borda
    }

    /** Em cima do bloco mais alto em (x, z), se for chão firme e o espaço de cima estiver livre. */
    public Location lugarSeguro(World mundo, int x, int z) {
        Block chao = mundo.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (chao.getY() <= mundo.getMinHeight() + 1) return null;
        if (!chao.getType().isSolid() || PERIGOSOS.contains(chao.getType())) return null;
        Block pe = chao.getRelative(0, 1, 0), cabeca = chao.getRelative(0, 2, 0);
        if (!pe.isPassable() || !cabeca.isPassable() || PERIGOSOS.contains(pe.getType()) || pe.isLiquid()) return null;
        return new Location(mundo, x + 0.5, chao.getY() + 1, z + 0.5);
    }
}
