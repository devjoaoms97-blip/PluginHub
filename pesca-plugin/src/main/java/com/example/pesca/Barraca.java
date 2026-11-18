package com.example.pesca;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma barraquinha de pesca: a caixa onde o dono fica pescando, o barril (o estoque) e o aluguel.
 * O estoque é uma lista de pilhas, até {@code estoque-pilhas}.
 */
public class Barraca {

    final String id;
    final String mundo;
    final int[] min, max;      // a caixa (inclusive)
    final int[] barril;        // o bloco do barril

    UUID dono;
    String nomeDono;
    long ate;                  // fim do aluguel (ms)
    boolean autorenovar;
    final List<ItemStack> estoque = new ArrayList<>();

    Barraca(String id, String mundo, int[] min, int[] max, int[] barril) {
        this.id = id;
        this.mundo = mundo;
        this.min = min;
        this.max = max;
        this.barril = barril;
    }

    boolean alugada() {
        return dono != null;
    }

    World world() {
        return Bukkit.getWorld(mundo);
    }

    boolean contem(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
    }

    boolean ehBarril(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getBlockX() == barril[0] && l.getBlockY() == barril[1] && l.getBlockZ() == barril[2];
    }

    /** Em cima do barril, onde fica o holograma. */
    Location ondeHolograma() {
        World w = world();
        return w == null ? null : new Location(w, barril[0] + 0.5, barril[1] + 1.6, barril[2] + 0.5);
    }

    /** Na frente do barril, olhando pra ele: onde o /pesca ir deixa o dono. */
    Location entrada() {
        World w = world();
        if (w == null) return null;
        double cx = (min[0] + max[0]) / 2.0 + 0.5, cz = (min[2] + max[2]) / 2.0 + 0.5;
        Location l = new Location(w, cx, min[1], cz);
        l.setDirection(new org.bukkit.util.Vector(barril[0] + 0.5 - cx, 0, barril[2] + 0.5 - cz));
        return l;
    }

    int pilhas() {
        return estoque.size();
    }

    /** Guarda o que couber; devolve o que sobrou (null se coube tudo). */
    ItemStack guardar(ItemStack item, int limite) {
        ItemStack resto = item.clone();
        for (ItemStack pilha : estoque) {
            if (resto.getAmount() <= 0) break;
            if (!pilha.isSimilar(resto)) continue;
            int cabe = pilha.getMaxStackSize() - pilha.getAmount();
            if (cabe <= 0) continue;
            int mover = Math.min(cabe, resto.getAmount());
            pilha.setAmount(pilha.getAmount() + mover);
            resto.setAmount(resto.getAmount() - mover);
        }
        while (resto.getAmount() > 0 && estoque.size() < limite) {
            int mover = Math.min(resto.getMaxStackSize(), resto.getAmount());
            ItemStack nova = resto.clone();
            nova.setAmount(mover);
            estoque.add(nova);
            resto.setAmount(resto.getAmount() - mover);
        }
        return resto.getAmount() > 0 ? resto : null;
    }

    void liberar() {
        dono = null;
        nomeDono = null;
        ate = 0;
        autorenovar = false;
        estoque.clear();
    }
}
