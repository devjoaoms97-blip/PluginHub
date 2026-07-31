package com.example.skills.listener;

import com.example.skills.SkillsPlugin;
import com.example.skills.manager.BonusCalculator;
import com.example.skills.manager.CombatTagManager;
import com.example.skills.manager.XpManager;
import com.example.skills.skill.Skill;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class CombatListener implements Listener {

    private final SkillsPlugin plugin;
    private final XpManager xpManager;
    private final BonusCalculator bonusCalculator;
    private final CombatTagManager combatTagManager;

    public CombatListener(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.xpManager = plugin.getXpManager();
        this.bonusCalculator = plugin.getBonusCalculator();
        this.combatTagManager = plugin.getCombatTagManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity vitimaEntity)) return;

        Player atacante = resolverAtacante(event.getDamager());
        boolean ehPvP = (atacante != null) && (vitimaEntity instanceof Player);
        double multiplicadorXp = ehPvP
                ? 1.0
                : plugin.getConfig().getDouble("xp.multiplicador-pve", 0.5);
        double xpPorDano = plugin.getConfig().getDouble("xp.xp-por-dano", 2.0);

        // ------------------------------------------------------------------
        // LADO DO ATACANTE: xp de arma + bônus de dano + crítico + atordoar
        // ------------------------------------------------------------------
        if (atacante != null) {
            combatTagManager.marcar(atacante.getUniqueId());

            Skill skillArma = detectarSkillDeArma(atacante, event.getDamager());
            if (skillArma != null) {
                double xpArma = event.getDamage() * xpPorDano * multiplicadorXp;
                xpManager.adicionarXp(atacante, skillArma, xpArma);

                double bonusDano = bonusCalculator.getBonus(atacante.getUniqueId(), skillArma);
                event.setDamage(event.getDamage() * (1 + bonusDano));

                if (skillArma == Skill.PORRADEIRO) {
                    double chanceAtordoar = bonusCalculator.getChanceAtordoar(atacante.getUniqueId());
                    if (ThreadLocalRandom.current().nextDouble() < chanceAtordoar) {
                        int ticks = plugin.getConfig().getInt("porradeiro.atordoar-ticks", 20);
                        int amp = plugin.getConfig().getInt("porradeiro.atordoar-amplificador", 3);
                        vitimaEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amp));
                        if (vitimaEntity instanceof Player vitimaJogador) {
                            vitimaJogador.sendMessage("§c✦ Você foi atordoado!");
                        }
                    }
                }
            }

            if (ehCriticoAproximado(atacante)) {
                xpManager.adicionarXp(atacante, Skill.CRITICO, 5.0 * multiplicadorXp);
                double bonusCritico = bonusCalculator.getBonus(atacante.getUniqueId(), Skill.CRITICO);
                event.setDamage(event.getDamage() * (1 + bonusCritico));
            }
        }

        // ------------------------------------------------------------------
        // LADO DA VÍTIMA: esquiva, bloqueio, defesa
        // ------------------------------------------------------------------
        if (vitimaEntity instanceof Player vitima) {
            combatTagManager.marcar(vitima.getUniqueId());

            boolean estaMovendo = vitima.getVelocity().lengthSquared() > 0.008
                    || vitima.isSprinting()
                    || vitima.isSneaking();

            double chanceEsquiva = bonusCalculator.getBonus(vitima.getUniqueId(), Skill.ESQUIVA);

            if (ThreadLocalRandom.current().nextDouble() < chanceEsquiva) {
                event.setCancelled(true);
                vitima.sendMessage("§b✦ Você esquivou do ataque!");
                vitima.playSound(vitima.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.4f);
                if (estaMovendo) {
                    xpManager.adicionarXp(vitima, Skill.ESQUIVA, 8.0 * multiplicadorXp);
                }
                return;
            }

            double danoRestante = event.getDamage();

            if (vitima.isBlocking()) {
                double reducaoBloqueio = bonusCalculator.getBonus(vitima.getUniqueId(), Skill.BLOQUEIO);
                danoRestante *= (1 - reducaoBloqueio);
                xpManager.adicionarXp(vitima, Skill.BLOQUEIO, 6.0 * multiplicadorXp);
            }

            double reducaoDefesa = bonusCalculator.getBonus(vitima.getUniqueId(), Skill.DEFESA);
            danoRestante *= (1 - reducaoDefesa);

            event.setDamage(Math.max(0, danoRestante));

            xpManager.adicionarXp(vitima, Skill.DEFESA, 4.0 * multiplicadorXp);

            if (estaMovendo) {
                xpManager.adicionarXp(vitima, Skill.ESQUIVA, 4.0 * multiplicadorXp);
            }
        }
    }

    private Player resolverAtacante(org.bukkit.entity.Entity causador) {
        if (causador instanceof Player p) {
            return p;
        }
        if (causador instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private Skill detectarSkillDeArma(Player atacante, org.bukkit.entity.Entity causador) {
        // Projéteis (flecha/besta ou tridente arremessado)
        if (causador instanceof Trident) {
            return Skill.TRIDENTE;
        }
        if (causador instanceof AbstractArrow) {
            return Skill.ARQUEIRO;
        }

        // Corpo a corpo: olha o item na mão do atacante
        if (causador == atacante) {
            ItemStack itemNaMao = atacante.getInventory().getItemInMainHand();
            Material tipo = itemNaMao.getType();
            String nome = tipo.name();

            if (tipo == Material.TRIDENT) return Skill.TRIDENTE;
            if (nome.endsWith("_SWORD")) return Skill.ESPADAS;
            if (nome.endsWith("_AXE")) return Skill.MACHADO;
            if (tipo == Material.AIR) return Skill.PORRADEIRO;
        }

        return null;
    }

    /**
     * Aproximação do "critical hit" vanilla, já que o Bukkit não expõe essa flag diretamente
     * no evento. Baseado nas condições clássicas: caindo, fora do chão, sem sprint.
     */
    private boolean ehCriticoAproximado(Player atacante) {
        return atacante.getFallDistance() > 0f
                && !atacante.isOnGround()
                && !atacante.isSprinting()
                && !atacante.isInsideVehicle()
                && !atacante.hasPotionEffect(PotionEffectType.BLINDNESS);
    }
}
