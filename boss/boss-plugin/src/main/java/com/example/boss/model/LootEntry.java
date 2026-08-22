package com.example.boss.model;

import org.bukkit.inventory.ItemStack;

public class LootEntry {

    private final ItemStack item;
    private final double chance;

    public LootEntry(ItemStack item, double chance) {
        this.item = item;
        this.chance = chance;
    }

    public ItemStack getItem() {
        return item;
    }

    /** Loot do chão: fração 0-1 (chance individual). Prêmio do campeão: peso relativo. */
    public double getChance() {
        return chance;
    }
}
