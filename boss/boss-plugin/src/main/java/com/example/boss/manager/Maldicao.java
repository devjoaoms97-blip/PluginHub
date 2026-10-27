package com.example.boss.manager;

import com.example.boss.BossPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;

/**
 * Boss que ninguém derrotou em {@code tempo-limite-minutos} vai embora e deixa o servidor amaldiçoado
 * por {@code maldicao.horas}: o XP de skill vale {@code maldicao.xp-skill-multiplicador} (o SkillsPlugin
 * lê {@link BossPlugin#fatorXpMaldicao()}). Acaba no prazo ou quando o próximo boss morre. Fica em
 * {@code maldicao.yml}, então sobrevive a reinício.
 */
public class Maldicao implements Listener {

    private final BossPlugin plugin;
    private final File arquivo;
    private long ate;

    public Maldicao(BossPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "maldicao.yml");
        this.ate = YamlConfiguration.loadConfiguration(arquivo).getLong("ate", 0);
    }

    public boolean ativa() {
        return ate > System.currentTimeMillis();
    }

    /** Multiplicador do XP de skill agora (1 sem maldição). */
    public double fatorXp() {
        if (!ativa()) return 1;
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("maldicao.xp-skill-multiplicador", 0.9)));
    }

    /** Horas (arredondadas pra cima) que faltam, ou 0 sem maldição. */
    public long horasRestantes() {
        long ms = ate - System.currentTimeMillis();
        return ms <= 0 ? 0 : (ms + 3_599_999) / 3_600_000;
    }

    /** O boss foi embora sem ser derrotado. */
    public void iniciar(String nomeBoss) {
        boolean ligada = plugin.getConfig().getBoolean("maldicao.ativado", true);
        String texto = plugin.getConfig().getString(ligada ? "maldicao.mensagem" : "maldicao.mensagem-sem-maldicao",
                ligada ? "&c%boss% &cnão foi derrotado e voltou pras trevas... &4O mal reina sobre o servidor pelas próximas %horas% horas!"
                        : "&7%boss% &7não foi derrotado e voltou pras trevas.");
        int horas = Math.max(1, plugin.getConfig().getInt("maldicao.horas", 24));
        String mensagem = cor(texto.replace("%boss%", nomeBoss).replace("%horas%", String.valueOf(horas)));
        Bukkit.broadcastMessage(mensagem);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.6f);
        plugin.getDiscord().enviar("boss", ChatColor.stripColor(mensagem));
        if (!ligada) return;
        ate = System.currentTimeMillis() + horas * 3_600_000L;
        salvar();
    }

    /** O boss morreu: quebra a maldição, se houver. */
    public void quebrar() {
        if (!ativa()) return;
        ate = 0;
        salvar();
        String mensagem = cor(plugin.getConfig().getString("maldicao.mensagem-quebrada",
                "&a&l✦ &aO boss caiu e a maldição foi quebrada! &7O XP de skill voltou ao normal."));
        Bukkit.broadcastMessage(mensagem);
        plugin.getDiscord().enviar("boss", ChatColor.stripColor(mensagem));
    }

    /** Roda junto com a agenda: anuncia quando a maldição acaba sozinha. */
    public void conferir() {
        if (ate == 0 || ativa()) return;
        ate = 0;
        salvar();
        Bukkit.broadcastMessage(cor(plugin.getConfig().getString("maldicao.mensagem-fim",
                "&7A maldição se dissipou. O XP de skill voltou ao normal.")));
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent event) {
        if (!ativa()) return;
        event.getPlayer().sendMessage(cor(plugin.getConfig().getString("maldicao.mensagem-entrada",
                        "&4☠ &cO mal reina sobre o servidor: XP de skill reduzido por mais %horas%h, ou até alguém derrotar o próximo boss.")
                .replace("%horas%", String.valueOf(horasRestantes()))));
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ate", ate);
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar maldicao.yml: " + e.getMessage());
        }
    }

    private static String cor(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }
}
