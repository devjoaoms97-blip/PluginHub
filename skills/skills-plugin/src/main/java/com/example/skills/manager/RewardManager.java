package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;

public class RewardManager {

    private final SkillsPlugin plugin;

    public RewardManager(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void aoSubirDeNivel(Player jogador, Skill skill, int novoNivel) {
        double dinheiroPorNivel = plugin.getConfig().getDouble("recompensa.dinheiro-por-nivel", 25);
        double recompensa = novoNivel * dinheiroPorNivel;

        Economy econ = plugin.getEconomy();
        if (econ != null) {
            econ.depositPlayer(jogador, recompensa);
        }

        jogador.sendMessage("");
        jogador.sendMessage("§6§l✦ SKILL UP! §e" + skill.getNomeExibicao() + " §6agora é nível §e" + novoNivel + "§6!");
        if (econ != null) {
            jogador.sendMessage("§a+ " + formatarValor(recompensa) + " §7creditados na sua conta.");
        }
        jogador.sendMessage("");

        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.0f);
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        efeito(jogador, skill);
    }

    /** A partícula de cada skill no efeito do level up. */
    private static Particle particula(Skill skill) {
        return switch (skill) {
            case ESPADAS -> Particle.CRIT;
            case MACHADO -> Particle.ENCHANTED_HIT;
            case ARQUEIRO -> Particle.END_ROD;
            case PORRADEIRO -> Particle.POOF;
            case TRIDENTE -> Particle.BUBBLE_POP;
            case LANCEIRO -> Particle.FIREWORK;
            case MARRETEIRO -> Particle.LAVA;
            case ESQUIVA -> Particle.CLOUD;
            case DEFESA -> Particle.ENCHANT;
            case BLOQUEIO -> Particle.WAX_ON;
            case CRITICO -> Particle.DAMAGE_INDICATOR;
            case REGENERACAO -> Particle.HEART;
            case AGILIDADE -> Particle.HAPPY_VILLAGER;
        };
    }

    /**
     * Duas espirais subindo em volta do jogador com a partícula da skill (1 s), e no fim a explosão
     * de totem. Todo mundo em volta vê.
     */
    private void efeito(Player jogador, Skill skill) {
        Particle particula = particula(skill);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!jogador.isOnline() || tick > 20) {
                    if (jogador.isOnline()) {
                        Location topo = jogador.getLocation().add(0, 1, 0);
                        jogador.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, topo, 40, 0.5, 0.8, 0.5, 0.05);
                    }
                    cancel();
                    return;
                }
                Location base = jogador.getLocation();
                double altura = tick * 0.11;
                for (int braco = 0; braco < 2; braco++) {
                    double angulo = tick * 0.6 + braco * Math.PI;
                    Location ponto = base.clone().add(Math.cos(angulo) * 0.9, altura, Math.sin(angulo) * 0.9);
                    jogador.getWorld().spawnParticle(particula, ponto, 3, 0.03, 0.03, 0.03, 0);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
