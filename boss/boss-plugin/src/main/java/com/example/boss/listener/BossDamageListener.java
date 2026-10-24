package com.example.boss.listener;

import com.example.boss.BossPlugin;
import com.example.boss.util.BossTagUtil;
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
import org.bukkit.persistence.PersistentDataType;

/**
 * Soma o dano que cada jogador causa no boss, pro ranking anunciado quando ele morre.
 *
 * Roda em MONITOR (depois de todos os outros plugins, incluindo os bônus do SkillsPlugin)
 * e ignora eventos cancelados, então conta o dano final que realmente entrou. Projéteis
 * (flecha, tridente arremessado etc.) contam pra quem atirou. O dano é limitado à vida
 * que o boss ainda tinha, pra o golpe final "exagerado" não inflar o ranking. O dano também é
 * separado pela skill da arma (espada, machado, arco...), pro XP de participação ir pra skill certa.
 */
public class BossDamageListener implements Listener {

    private final BossPlugin plugin;

    public BossDamageListener(BossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoCausarDano(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof LivingEntity boss)) return;
        if (!boss.getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) return;

        Player atacante = resolverJogador(evento.getDamager());
        if (atacante == null) return;

        double dano = Math.min(evento.getFinalDamage(), boss.getHealth());
        if (dano > 0) {
            plugin.getBossManager().registrarDano(atacante.getUniqueId(), dano, skillDoGolpe(evento.getDamager(), atacante));
        }
    }

    /** A skill do SkillsPlugin que treina com esse golpe, ou null. */
    private String skillDoGolpe(Entity causador, Player atacante) {
        if (causador instanceof Trident) return "TRIDENTE";
        if (causador instanceof AbstractArrow) return "ARQUEIRO";
        if (causador instanceof Projectile) return null;
        // o golpe da segunda mão (CombatePlugin) troca as mãos durante o golpe: a arma certa está na principal
        ItemStack arma = atacante.getInventory().getItemInMainHand();
        String nome = arma.getType().name();
        if (arma.getType().isAir()) return "PORRADEIRO";
        if (nome.endsWith("_SWORD")) return "ESPADAS";
        if (nome.endsWith("_AXE")) return "MACHADO";
        if (nome.endsWith("_SPEAR")) return "LANCEIRO";
        if (nome.equals("MACE")) return "MARRETEIRO";
        if (nome.equals("TRIDENT")) return "TRIDENTE";
        return null;
    }

    private Player resolverJogador(Entity causador) {
        if (causador instanceof Player jogador) {
            return jogador;
        }
        if (causador instanceof Projectile projetil && projetil.getShooter() instanceof Player atirador) {
            return atirador;
        }
        return null;
    }
}
