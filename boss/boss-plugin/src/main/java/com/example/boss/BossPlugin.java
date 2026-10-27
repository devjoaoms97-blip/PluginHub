package com.example.boss;

import com.example.boss.command.BossCommand;
import com.example.boss.listener.BossDamageListener;
import com.example.boss.listener.BossDeathListener;
import com.example.boss.listener.BossExplosionListener;
import com.example.boss.manager.ArenaManager;
import com.example.boss.manager.BossManager;
import com.example.boss.manager.ChampionManager;
import com.example.boss.manager.LootManager;
import com.example.boss.manager.Recompensas;
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
    private Recompensas recompensas;
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
        this.recompensas = new Recompensas(this);

        BossCommand comando = new BossCommand(this);
        getCommand("boss").setExecutor(comando);
        getCommand("boss").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new BossDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new BossExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new com.example.boss.listener.BossOrfaoListener(this), this);

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
            // o boss sai do mundo junto: sem isso ele ficava salvo e voltava órfão (sem barra nem ranking)
            bossManager.getBossAtivo().remove();
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

    /**
     * API pública (PlacarPlugin, por reflexão): "AGORA!" com o boss vivo, o próximo horário da
     * agenda (ex: "sáb 21:00"), ou null com a agenda vazia.
     */
    public String textoProximoBoss() {
        if (bossManager != null && bossManager.estaAtivo()) return "AGORA!";
        java.time.LocalDateTime quando = scheduleManager == null ? null : scheduleManager.proximo();
        if (quando == null) return null;
        String dia = quando.toLocalDate().equals(java.time.LocalDate.now()) ? "hoje"
                : quando.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.forLanguageTag("pt-BR")).replace(".", "");
        return dia + " " + String.format("%02d:%02d", quando.getHour(), quando.getMinute());
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public Recompensas getRecompensas() {
        return recompensas;
    }

    public DiscordIntegration getDiscord() {
        return discord;
    }
}
