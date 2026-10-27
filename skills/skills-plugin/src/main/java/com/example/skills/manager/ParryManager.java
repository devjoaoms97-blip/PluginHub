package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Escudo Perfeito (parry): quem levanta o escudo pouco antes de um golpe corpo a corpo anula o
 * dano todo e atordoa quem bateu. "Pouco antes" é a janela {@code parry.janela-ticks} contada de
 * quando o escudo subiu, que cresce com o nível de Bloqueio. Vale antes do atraso de 0,25 s do
 * escudo do jogo: é justamente o golpe que o escudo ainda não pegaria.
 * <p>
 * O atordoado não anda (AtordoamentoManager, nos jogadores; Lentidão forte nos mobs) e os golpes
 * corpo a corpo dele não contam enquanto durar. A recarga evita que subir e descer o escudo sem
 * parar vire imunidade.
 */
public class ParryManager {

    private final SkillsPlugin plugin;
    private final Map<UUID, Long> recargaAte = new ConcurrentHashMap<>();
    private final Map<UUID, Long> desarmadoAte = new ConcurrentHashMap<>();

    public ParryManager(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Se o golpe de {@code agressor} cai dentro da janela do parry de {@code vitima}. */
    public boolean tentar(Player vitima, LivingEntity agressor, boolean bossImune) {
        if (!plugin.getConfig().getBoolean("parry.ativado", true)) return false;
        if (!vitima.hasActiveItem() || vitima.getActiveItem().getType() != Material.SHIELD) return false;

        long agora = System.currentTimeMillis();
        if (recargaAte.getOrDefault(vitima.getUniqueId(), 0L) > agora) return false;

        int nivelBloqueio = plugin.getSkillDataManager().get(vitima.getUniqueId()).getNivel(Skill.BLOQUEIO);
        int janela = plugin.getConfig().getInt("parry.janela-ticks", 6)
                + nivelBloqueio / 10 * plugin.getConfig().getInt("parry.janela-ticks-por-10-niveis", 1);
        if (vitima.getActiveItemUsedTime() > janela) return false;

        recargaAte.put(vitima.getUniqueId(), agora + (long) (plugin.getConfig().getDouble("parry.recarga-segundos", 3) * 1000));

        vitima.getWorld().playSound(vitima.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.6f);
        vitima.getWorld().playSound(vitima.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 2f);
        vitima.getWorld().spawnParticle(Particle.CRIT, vitima.getEyeLocation().add(vitima.getLocation().getDirection().multiply(0.8)),
                25, 0.3, 0.3, 0.3, 0.2);
        vitima.sendMessage("§9§l✦ Escudo Perfeito!");
        plugin.getXpManager().adicionarXp(vitima, Skill.BLOQUEIO, plugin.getConfig().getDouble("parry.xp-bloqueio", 15));

        if (!bossImune) {
            int segundos = Math.max(1, plugin.getConfig().getInt("parry.atordoar-segundos", 1));
            desarmadoAte.put(agressor.getUniqueId(), agora + segundos * 1000L);
            if (agressor instanceof Player jogador) {
                plugin.getAtordoamentoManager().aplicar(jogador, segundos);
                jogador.sendMessage("§9✦ Seu golpe foi aparado! Você está atordoado.");
            } else {
                agressor.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, segundos * 20, 6));
            }
        }
        return true;
    }

    /** Se o agressor ainda está atordoado por um parry (os golpes corpo a corpo dele não contam). */
    public boolean desarmado(UUID agressor) {
        Long ate = desarmadoAte.get(agressor);
        if (ate == null) return false;
        if (ate > System.currentTimeMillis()) return true;
        desarmadoAte.remove(agressor);
        return false;
    }
}
