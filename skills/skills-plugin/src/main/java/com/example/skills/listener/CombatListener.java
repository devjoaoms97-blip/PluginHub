package com.example.skills.listener;

import com.example.skills.SkillsPlugin;
import com.example.skills.manager.BonusCalculator;
import com.example.skills.manager.CombatTagManager;
import com.example.skills.manager.XpManager;
import com.example.skills.skill.Skill;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
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
import org.bukkit.util.Vector;

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
        // LADO DO ATACANTE: xp de arma + bônus de dano + atordoar
        // ------------------------------------------------------------------
        if (atacante != null) {
            combatTagManager.marcar(atacante.getUniqueId());

            Skill skillArma = detectarSkillDeArma(atacante, event.getDamager());
            if (skillArma != null) {
                double xpArma = event.getDamage() * xpPorDano * multiplicadorXp;
                xpManager.adicionarXp(atacante, skillArma, xpArma);

                // Crítico não tem "bônus por nível" genérico (é chance + multiplicador fixo),
                // então só aplica o bônus percentual pras outras skills de arma.
                if (skillArma != Skill.CRITICO) {
                    double bonusDano = bonusCalculator.getBonus(atacante.getUniqueId(), skillArma);
                    event.setDamage(event.getDamage() * (1 + bonusDano));
                }

                if (skillArma == Skill.PORRADEIRO) {
                    double chanceAtordoar = bonusCalculator.getChanceAtordoar(atacante.getUniqueId());
                    if (ThreadLocalRandom.current().nextDouble() < chanceAtordoar) {
                        int ticks = plugin.getConfig().getInt("porradeiro.atordoar-ticks", 20);
                        int amp = plugin.getConfig().getInt("porradeiro.atordoar-amplificador", 3);
                        vitimaEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amp));
                        // Sem mensagem de propósito (efeito silencioso)
                    }
                } else if (skillArma == Skill.MARRETEIRO) {
                    double chanceAtordoar = bonusCalculator.getChanceAtordoarMarreteiro(atacante.getUniqueId());
                    if (ThreadLocalRandom.current().nextDouble() < chanceAtordoar && !ehBossImuneAControle(vitimaEntity)) {
                        int duracao = plugin.getConfig().getInt("marreteiro.atordoar-duracao-segundos", 3);

                        if (vitimaEntity instanceof Player) {
                            // Stun de verdade: trava o movimento (via StunListener)
                            plugin.getAtordoamentoManager().aplicar(vitimaEntity, duracao);
                        } else {
                            // Mobs não têm PlayerMoveEvent pra travar; usa Lentidão forte como substituto
                            vitimaEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duracao * 20, 6));
                        }

                        atacante.sendMessage("§c✦ Você atordoou o inimigo!");
                        if (vitimaEntity instanceof Player vitimaJogador) {
                            vitimaJogador.sendMessage("§c✦ Você foi atordoado!");
                        }
                    }
                } else if (skillArma == Skill.LANCEIRO) {
                    double chanceSangramento = bonusCalculator.getChanceSangramento(atacante.getUniqueId());
                    if (ThreadLocalRandom.current().nextDouble() < chanceSangramento) {
                        double danoPorTick = plugin.getConfig().getDouble("lanceiro.sangramento-dano-por-tick", 2.0);
                        int duracao = plugin.getConfig().getInt("lanceiro.sangramento-duracao-segundos", 4);
                        plugin.getSangramentoManager().aplicar(vitimaEntity, danoPorTick, duracao);
                        atacante.sendMessage("§4✦ Inimigo perfurado!");
                        if (vitimaEntity instanceof Player vitimaJogador) {
                            vitimaJogador.sendMessage("§4✦ Você foi perfurado!");
                        }
                    }
                } else if (skillArma == Skill.TRIDENTE) {
                    double chanceEmpurrao = bonusCalculator.getChanceEmpurrao(atacante.getUniqueId());
                    if (ThreadLocalRandom.current().nextDouble() < chanceEmpurrao && !ehBossImuneAControle(vitimaEntity)) {
                        Vector direcao = vitimaEntity.getLocation().toVector()
                                .subtract(atacante.getLocation().toVector())
                                .normalize();
                        direcao.setY(Math.max(direcao.getY(), 0.3));
                        vitimaEntity.setVelocity(direcao.multiply(bonusCalculator.getForcaEmpurrao()));
                        atacante.sendMessage("§b✦ Você empurrou o inimigo!");
                        if (vitimaEntity instanceof Player vitimaJogador) {
                            vitimaJogador.sendMessage("§b✦ Você foi empurrado!");
                        }
                    }
                }
            }

            // Crítico: proca em QUALQUER arma (não só na Adaga), com chance baseada no nível de Crítico.
            // Aplicado por cima de tudo (dano base + bônus de arma + crítico vanilla do pulo, se houver).
            double chanceCritico = bonusCalculator.getChanceCritico(atacante.getUniqueId());
            if (chanceCritico > 0 && ThreadLocalRandom.current().nextDouble() < chanceCritico) {
                double multiplicador = bonusCalculator.getMultiplicadorCritico();
                event.setDamage(event.getDamage() * (1 + multiplicador));

                if (skillArma == Skill.ARQUEIRO) {
                    atacante.sendMessage("§6§lHEADSHOT!");
                } else {
                    atacante.sendMessage("§6✦ Golpe crítico!");
                }
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
                if (atacante != null) {
                    atacante.sendMessage("§b✦ Seu ataque foi esquivado!");
                }
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
                vitima.sendMessage("§9✦ Você bloqueou o ataque!");
                if (atacante != null) {
                    atacante.sendMessage("§9✦ Seu ataque foi bloqueado!");
                }
            }

            double reducaoDefesa = ehAtaquePelasCostas(vitima, event.getDamager())
                    ? 0.0
                    : bonusCalculator.getBonus(vitima.getUniqueId(), Skill.DEFESA);
            danoRestante *= (1 - reducaoDefesa);

            // Enquanto atordoado, o alvo fica parado e apanha muito mais rápido do que o normal.
            // Sem isso, o período de invulnerabilidade vanilla (~0.5s) acaba anulando praticamente
            // todo hit "extra" que chegar antes desse tempo passar — dando a impressão de imunidade.
            if (plugin.getAtordoamentoManager().estaAtordoado(vitima.getUniqueId())) {
                vitima.setNoDamageTicks(0);
            }

            event.setDamage(Math.max(0, danoRestante));

            xpManager.adicionarXp(vitima, Skill.DEFESA, 4.0 * multiplicadorXp);

            if (estaMovendo) {
                xpManager.adicionarXp(vitima, Skill.ESQUIVA, 4.0 * multiplicadorXp);
            }
        }
    }

    /**
     * Verifica se o ataque veio de trás da vítima (ângulo maior que 90° entre pra
     * onde ela está olhando e a direção de onde o golpe partiu). Usado pra ignorar
     * a redução da Defesa em ataques surpresa/pelas costas.
     */
    private boolean ehAtaquePelasCostas(LivingEntity vitima, Entity causador) {
        Location origemAtaque = obterOrigemDoAtaque(causador);
        if (origemAtaque == null || !origemAtaque.getWorld().equals(vitima.getWorld())) {
            return false;
        }

        org.bukkit.util.Vector direcaoParaOrigem = origemAtaque.toVector().subtract(vitima.getLocation().toVector());
        if (direcaoParaOrigem.lengthSquared() < 0.0001) {
            return false; // ataque veio de cima praticamente em cima da vítima, ignora
        }
        direcaoParaOrigem.normalize();

        double anguloGraus = Math.toDegrees(vitima.getLocation().getDirection().angle(direcaoParaOrigem));
        return anguloGraus > 90;
    }

    /** Pra projéteis, usa a posição de quem atirou (mais preciso que a posição da flecha no impacto). */
    private Location obterOrigemDoAtaque(Entity causador) {
        if (causador instanceof Projectile proj && proj.getShooter() instanceof Entity atirador) {
            return atirador.getLocation();
        }
        return causador.getLocation();
    }

    /**
     * Checa se a entidade tem a marca compartilhada "pluginhub:boss_mob" (usada pelo
     * BossPlugin pra identificar chefes mundiais). Se tiver, ela é imune ao Atordoamento
     * do Marreteiro e ao Empurrão do Tridente — sem isso, um boss gigante ficaria fácil
     * demais de travar/empurrar com essas skills.
     */
    private boolean ehBossImuneAControle(Entity entidade) {
        return entidade.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey("pluginhub", "boss_mob"),
                org.bukkit.persistence.PersistentDataType.BYTE
        );
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

            // A Adaga é uma espada marcada — treina Crítico em vez de Espadas
            if (plugin.getAdagaUtil().ehAdaga(itemNaMao)) {
                return Skill.CRITICO;
            }

            Material tipo = itemNaMao.getType();
            String nome = tipo.name();

            if (tipo == Material.TRIDENT) return Skill.TRIDENTE;
            if (tipo == Material.MACE) return Skill.MARRETEIRO;
            if (nome.endsWith("_SWORD")) return Skill.ESPADAS;
            if (nome.endsWith("_AXE")) return Skill.MACHADO;
            if (nome.endsWith("_SPEAR")) return Skill.LANCEIRO;
            if (tipo == Material.AIR) return Skill.PORRADEIRO;
        }

        return null;
    }
}
