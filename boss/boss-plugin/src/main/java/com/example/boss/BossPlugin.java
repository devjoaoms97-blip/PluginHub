package com.example.boss;

import com.example.boss.command.BossCommand;
import com.example.boss.listener.BossDamageListener;
import com.example.boss.listener.BossDeathListener;
import com.example.boss.listener.BossExplosionListener;
import com.example.boss.manager.ArenaManager;
import com.example.boss.manager.BossManager;
import com.example.boss.manager.ChampionManager;
import com.example.boss.manager.LootManager;
import com.example.boss.manager.ScheduleManager;
import com.example.boss.util.DiscordIntegration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BossPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private LootManager lootManager;
    private ChampionManager championManager;
    private BossManager bossManager;
    private ScheduleManager scheduleManager;
    private DiscordIntegration discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discord = new DiscordIntegration(this);

        this.arenaManager = new ArenaManager(this);
        this.lootManager = new LootManager(this);
        this.championManager = new ChampionManager(this);
        this.bossManager = new BossManager(this);
        this.scheduleManager = new ScheduleManager(this);

        BossCommand comando = new BossCommand(this);
        getCommand("boss").setExecutor(comando);
        getCommand("boss").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new BossDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new BossExplosionListener(this), this);

        // Checa a agenda a cada 20s — menos que 1 min de propósito, pra lag não fazer pular um
        // minuto inteiro. Spawn e avisos têm trava própria contra disparo duplicado no mesmo minuto.
        getServer().getScheduler().runTaskTimer(this, () -> {
            int minutosParaSpawn = scheduleManager.avisoParaDispararAgora();
            if (minutosParaSpawn > 0 && !bossManager.estaAtivo()) {
                anunciarContagemRegressiva(minutosParaSpawn);
            }
            if (scheduleManager.deveDispararAgora() && !bossManager.estaAtivo()) {
                bossManager.iniciarAleatorio(null);
            }
        }, 20L * 10, 20L * 20);

        getLogger().info("BossPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (bossManager != null && bossManager.estaAtivo()) {
            bossManager.finalizar(false);
        }
        getLogger().info("BossPlugin desativado!");
    }

    private void anunciarContagemRegressiva(int minutos) {
        String mensagem = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("aviso-agenda.mensagem", "&e⚠ Um boss vai aparecer em &f%minutos% min&e! Preparem-se.")
                        .replace("%minutos%", String.valueOf(minutos)));
        Bukkit.broadcastMessage(mensagem);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f);
        }
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

    public DiscordIntegration getDiscord() {
        return discord;
    }
}
