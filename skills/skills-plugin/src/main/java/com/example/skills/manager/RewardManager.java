package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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

        Location loc = jogador.getLocation().add(0, 1, 0);
        jogador.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.5, 0.8, 0.5, 0.05);
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.0f);
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
