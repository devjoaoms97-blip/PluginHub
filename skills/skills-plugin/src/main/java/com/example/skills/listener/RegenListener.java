package com.example.skills.listener;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

public class RegenListener implements Listener {

    private final SkillsPlugin plugin;

    public RegenListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player jogador)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;

        double bonus = plugin.getBonusCalculator().getBonus(jogador.getUniqueId(), Skill.REGENERACAO);
        if (bonus <= 0) return;

        event.setAmount(event.getAmount() * (1 + bonus));
    }
}
