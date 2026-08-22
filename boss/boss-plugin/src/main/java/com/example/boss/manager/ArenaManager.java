package com.example.boss.manager;

import com.example.boss.BossPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class ArenaManager {

    private final BossPlugin plugin;

    public ArenaManager(BossPlugin plugin) {
        this.plugin = plugin;
    }

    public Location getLocalizacao() {
        if (!plugin.getConfig().contains("arena.mundo")) return null;

        String mundoNome = plugin.getConfig().getString("arena.mundo");
        if (mundoNome == null || Bukkit.getWorld(mundoNome) == null) return null;

        double x = plugin.getConfig().getDouble("arena.x");
        double y = plugin.getConfig().getDouble("arena.y");
        double z = plugin.getConfig().getDouble("arena.z");

        return new Location(Bukkit.getWorld(mundoNome), x, y, z);
    }

    public void setLocalizacao(Location local) {
        plugin.getConfig().set("arena.mundo", local.getWorld().getName());
        plugin.getConfig().set("arena.x", local.getX());
        plugin.getConfig().set("arena.y", local.getY());
        plugin.getConfig().set("arena.z", local.getZ());
        plugin.saveConfig();
    }
}
