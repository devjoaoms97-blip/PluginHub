package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

/**
 * XP em dobro (ou o multiplicador que for): nos dias fixos do config ({@code xp-dobro.dias}) ou
 * ligado na hora pela staff ({@code /skills admin xpdobro <multiplicador> <horas>}). Vale pro XP de
 * combate e pro que vem de outros plugins (missões, boss), não pro bônus de ativação do VIP.
 * Começo e fim são anunciados no chat; quem entra durante o evento é avisado.
 */
public class EventoXp {

    private final SkillsPlugin plugin;
    private double multiplicadorManual;
    private long manualAte;
    private boolean estavaAtivo;

    public EventoXp(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.manualAte = plugin.getConfig().getLong("xp-dobro.manual-ate", 0);
        this.multiplicadorManual = plugin.getConfig().getDouble("xp-dobro.manual-multiplicador", 2.0);
        this.estavaAtivo = multiplicador() > 1;
    }

    /** O multiplicador de agora (1 = sem evento). O evento manual vale mais que o dos dias fixos. */
    public double multiplicador() {
        if (System.currentTimeMillis() < manualAte) return Math.max(1, multiplicadorManual);
        if (!plugin.getConfig().getBoolean("xp-dobro.ativado", true)) return 1;
        DayOfWeek hoje = LocalDate.now().getDayOfWeek();
        for (String dia : plugin.getConfig().getStringList("xp-dobro.dias")) {
            if (hoje == dia(dia)) return Math.max(1, plugin.getConfig().getDouble("xp-dobro.multiplicador", 2.0));
        }
        return 1;
    }

    /** Liga um evento manual por algumas horas (0 horas desliga). */
    public void ligarManual(double multiplicador, double horas) {
        this.multiplicadorManual = multiplicador;
        this.manualAte = horas <= 0 ? 0 : System.currentTimeMillis() + (long) (horas * 3_600_000L);
        plugin.getConfig().set("xp-dobro.manual-ate", manualAte);
        plugin.getConfig().set("xp-dobro.manual-multiplicador", multiplicadorManual);
        plugin.saveConfig();
        conferir();
    }

    public long getManualAte() {
        return manualAte;
    }

    /** Roda a cada minuto: anuncia quando o evento começa e quando acaba. */
    public void conferir() {
        double m = multiplicador();
        boolean ativo = m > 1;
        if (ativo == estavaAtivo) return;
        estavaAtivo = ativo;
        if (ativo) {
            Bukkit.broadcastMessage("§6§l✦ XP " + texto(m) + " NAS SKILLS! §eAproveite pra treinar.");
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        } else {
            Bukkit.broadcastMessage("§7O XP extra nas skills acabou.");
        }
    }

    /** Aviso pra quem entra durante o evento. */
    public void avisar(Player jogador) {
        double m = multiplicador();
        if (m > 1) jogador.sendMessage("§6✦ §eHoje o XP das skills está §6" + texto(m) + "§e!");
    }

    private String texto(double m) {
        return m == 2 ? "EM DOBRO" : "x" + String.format(Locale.ROOT, "%.1f", m).replace(".0", "");
    }

    private static DayOfWeek dia(String texto) {
        return switch (texto.toUpperCase(Locale.ROOT).trim()) {
            case "SEGUNDA" -> DayOfWeek.MONDAY;
            case "TERCA", "TERÇA" -> DayOfWeek.TUESDAY;
            case "QUARTA" -> DayOfWeek.WEDNESDAY;
            case "QUINTA" -> DayOfWeek.THURSDAY;
            case "SEXTA" -> DayOfWeek.FRIDAY;
            case "SABADO", "SÁBADO" -> DayOfWeek.SATURDAY;
            case "DOMINGO" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
}
