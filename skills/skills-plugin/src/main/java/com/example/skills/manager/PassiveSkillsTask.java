package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Roda periodicamente:
 * - concede XP de Regeneração pra quem está/esteve em combate recente
 * - concede XP de Agilidade pra quem está em combate E se movendo
 * - mantém o atributo de velocidade de movimento sincronizado com o nível de Agilidade
 */
public class PassiveSkillsTask extends BukkitRunnable {

    private static final UUID MODIFICADOR_AGILIDADE_ID = UUID.fromString("a1b2c3d4-e5f6-4789-a0b1-c2d3e4f5a6b7");

    private final SkillsPlugin plugin;

    public PassiveSkillsTask(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        double xpPorTick = plugin.getConfig().getDouble("passivas.xp-por-tick", 3.0);

        for (Player jogador : Bukkit.getOnlinePlayers()) {
            boolean emCombate = plugin.getCombatTagManager().emCombate(jogador.getUniqueId());

            if (emCombate) {
                plugin.getXpManager().adicionarXp(jogador, Skill.REGENERACAO, xpPorTick);

                boolean movendo = jogador.getVelocity().lengthSquared() > 0.008 || jogador.isSprinting();
                if (movendo) {
                    plugin.getXpManager().adicionarXp(jogador, Skill.AGILIDADE, xpPorTick);
                }
            }

            atualizarVelocidade(jogador);
        }
    }

    /** Aplica o bônus de velocidade de movimento como um AttributeModifier real. */
    public void atualizarVelocidade(Player jogador) {
        AttributeInstance atributo = jogador.getAttribute(Attribute.MOVEMENT_SPEED);
        if (atributo == null) return;

        atributo.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(MODIFICADOR_AGILIDADE_ID))
                .findFirst()
                .ifPresent(atributo::removeModifier);

        double bonus = plugin.getBonusCalculator().getBonus(jogador.getUniqueId(), Skill.AGILIDADE);
        if (bonus <= 0) return;

        AttributeModifier modificador = new AttributeModifier(
                MODIFICADOR_AGILIDADE_ID,
                "skills.agilidade",
                bonus,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );
        atributo.addModifier(modificador);
    }
}
