package com.example.boss.listener;

import com.example.boss.BossPlugin;
import com.example.boss.util.BossTagUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Comportamento da explosão do boss Creeper.
 *
 * No vanilla, creeper explode → morre na hora (o próprio jogo dá discard na entidade)
 * e a explosão acerta QUALQUER entidade no raio (mobs inclusos) e destrói blocos.
 * Pra um boss isso é bug:
 *   1) ele não pode morrer ao explodir;
 *   2) a explosão só pode dar dano em players (nada de mobs);
 *   3) ele não pode perder vida.
 *
 * Solução: cancelamos o "prime" vanilla (ExplosionPrimeEvent) — o creeper nunca chega
 * a explodir de verdade, então nunca morre e não destrói nada — e disparamos a nossa
 * própria explosão: dano SÓ em players dentro do raio (com queda por distância),
 * knockback pra fora do centro e efeitos visuais/sonoros. Um cooldown evita que ele
 * "exploda" a cada prime (senão vira metralhadora de dano em área).
 */
public class BossExplosionListener implements Listener {

    private final BossPlugin plugin;
    private long ultimaExplosao;

    public BossExplosionListener(BossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void aoAcenderExplosao(ExplosionPrimeEvent evento) {
        Entity entidade = evento.getEntity();
        if (!(entidade instanceof Creeper)) return;
        if (!entidade.getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) return;

        // Cancela a explosão vanilla: o boss não explode de verdade, então não morre,
        // não acerta mobs e não destrói blocos.
        evento.setCancelled(true);

        long agora = System.currentTimeMillis();
        long cooldownMs = (long) (plugin.getConfig().getDouble("explosao.cooldown-segundos", 6) * 1000);
        if (agora - ultimaExplosao < cooldownMs) return;

        ultimaExplosao = agora;
        explodir((Creeper) entidade);
    }

    private void explodir(Creeper boss) {
        double raio = plugin.getConfig().getDouble("explosao.raio", 8);
        double danoMaximo = plugin.getConfig().getDouble("explosao.dano-maximo", 25);
        double forcaKnockback = plugin.getConfig().getDouble("explosao.knockback", 1.0);

        Location centro = boss.getLocation();
        boss.getWorld().playSound(centro, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, centro, 1);
        boss.getWorld().spawnParticle(Particle.EXPLOSION, centro, 1);

        for (Player jogador : boss.getWorld().getPlayers()) {
            double distancia = jogador.getLocation().distance(centro);
            if (distancia > raio) continue;

            // Dano com queda por distância: cheio no centro, zero na borda do raio.
            double dano = danoMaximo * (1.0 - (distancia / raio));
            if (dano > 0) {
                jogador.damage(dano, boss);
            }

            // Knockback pra fora do centro, como uma explosão de verdade.
            if (forcaKnockback > 0 && distancia > 0.1) {
                Vector direcao = jogador.getLocation().toVector().subtract(centro.toVector()).normalize();
                jogador.setVelocity(jogador.getVelocity().add(direcao.multiply(forcaKnockback)));
            }
        }
    }

    // Rede de segurança: se qualquer explosão (de quem quer que seja) atingir o boss,
    // ele não perde vida. Garante "boss não morre ao explodir" mesmo em cenários
    // inesperados (ex: outra explosão por perto).
    @EventHandler
    public void aoTomarDanoDeExplosao(EntityDamageEvent evento) {
        if (!evento.getEntity().getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) return;
        if (evento.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || evento.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            evento.setCancelled(true);
        }
    }

    // Rede de segurança: nunca deixar a explosão vanilla do boss destruir blocos ou
    // acertar entidades (mobs). Com o prime cancelado isso não deveria acontecer,
    // mas se acontecer, cancelamos tudo.
    @EventHandler
    public void aoExplodir(EntityExplodeEvent evento) {
        if (!evento.getEntity().getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) return;
        evento.setCancelled(true);
    }
}
