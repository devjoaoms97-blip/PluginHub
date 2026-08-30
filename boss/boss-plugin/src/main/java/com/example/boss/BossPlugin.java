package com.example.boss;

import com.example.boss.command.BossCommand;
import com.example.boss.listener.BossDeathListener;
import com.example.boss.listener.BossExplosionListener;
import com.example.boss.manager.ArenaManager;
import com.example.boss.manager.BossManager;
import com.example.boss.manager.ChampionManager;
import com.example.boss.manager.LootManager;
import com.example.boss.manager.ScheduleManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BossPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private LootManager lootManager;
    private ChampionManager championManager;
    private BossManager bossManager;
    private ScheduleManager scheduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager = new ArenaManager(this);
        this.lootManager = new LootManager(this);
        this.championManager = new ChampionManager(this);
        this.bossManager = new BossManager(this);
        this.scheduleManager = new ScheduleManager(this);

        BossCommand comando = new BossCommand(this);
        getCommand("boss").setExecutor(comando);
        getCommand("boss").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new BossDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BossExplosionListener(this), this);

        // Checa a agenda a cada minuto (20 ticks * 60 = 1200 ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (scheduleManager.deveDispararAgora() && !bossManager.estaAtivo()) {
                bossManager.iniciarAleatorio(null);
            }
        }, 20L * 10, 20L * 60);

        getLogger().info("BossPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (bossManager != null && bossManager.estaAtivo()) {
            bossManager.finalizar(false);
        }
        getLogger().info("BossPlugin desativado!");
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public ChampionManager getChampionManager() {
        return championManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }
}
