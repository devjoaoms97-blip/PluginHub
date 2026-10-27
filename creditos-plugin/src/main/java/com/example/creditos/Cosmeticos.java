package com.example.creditos;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O que cada cosmético faz: o rastro de partícula nos pés de quem está andando (a cada 2 ticks), a
 * cor do nome (lida pelo VipPlugin pra montar o chat e a tab, via {@link CreditosPlugin#corDoNome}) e o
 * efeito de abate no lugar em que o outro jogador morreu. Tudo só visual: os fogos do abate não
 * machucam ninguém, a explosão é só partícula e o raio é o efeito (sem fogo nem dano).
 */
public class Cosmeticos implements Listener {

    private final CreditosPlugin plugin;
    private final NamespacedKey fogosDeAbate;
    private final Map<UUID, Location> ultimaPosicao = new HashMap<>();

    public Cosmeticos(CreditosPlugin plugin) {
        this.plugin = plugin;
        this.fogosDeAbate = new NamespacedKey(plugin, "fogos-de-abate");
    }

    /** A seção do cosmético no config ({@code cosmeticos.<categoria>.<id>}), ou null. */
    ConfigurationSection secao(Dados.Categoria c, String id) {
        return id == null ? null : plugin.getConfig().getConfigurationSection("cosmeticos." + chave(c) + "." + id);
    }

    static String chave(Dados.Categoria c) {
        return switch (c) {
            case RASTRO -> "rastros";
            case COR -> "cores";
            case ABATE -> "abates";
        };
    }

    // ---------------------------------------------------------------------
    // Rastro
    // ---------------------------------------------------------------------

    /** A cada 2 ticks: quem tem rastro e andou desde a última vez deixa partícula nos pés. */
    public void rastros() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String id = plugin.getDados().emUso(p.getUniqueId(), Dados.Categoria.RASTRO);
            if (id == null) continue;
            Location agora = p.getLocation();
            Location antes = ultimaPosicao.put(p.getUniqueId(), agora);
            if (antes == null || !antes.getWorld().equals(agora.getWorld()) || antes.distanceSquared(agora) < 0.01) continue;
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isInvisible()) continue;
            ConfigurationSection s = secao(Dados.Categoria.RASTRO, id);
            Particle particula = particula(s == null ? null : s.getString("particula"));
            if (particula == null) continue;
            Location pes = agora.clone().add(0, 0.1, 0);
            if (particula == Particle.NOTE) {
                p.getWorld().spawnParticle(particula, pes, 1, 0.2, 0, 0.2, 1);
            } else {
                p.getWorld().spawnParticle(particula, pes, 2, 0.15, 0.05, 0.15, 0.01);
            }
        }
    }

    private static Particle particula(String nome) {
        if (nome == null) return null;
        try {
            Particle p = Particle.valueOf(nome.toUpperCase(Locale.ROOT));
            // partícula que precisa de dado extra (cor, bloco...) não serve aqui
            return p.getDataType() == Void.class ? p : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        ultimaPosicao.remove(evento.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------------
    // Efeito de abate
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMatar(PlayerDeathEvent evento) {
        Player matador = evento.getEntity().getKiller();
        if (matador == null || matador == evento.getEntity()) return;
        String id = plugin.getDados().emUso(matador.getUniqueId(), Dados.Categoria.ABATE);
        ConfigurationSection s = secao(Dados.Categoria.ABATE, id);
        if (s == null) return;
        efeito(s.getString("efeito", ""), evento.getEntity().getLocation());
    }

    /** Toca o efeito {@code nome} no lugar (também usado pra mostrar na loja). */
    void efeito(String nome, Location l) {
        Location centro = l.clone().add(0, 1, 0);
        switch (nome.toUpperCase(Locale.ROOT)) {
            case "RAIO" -> l.getWorld().strikeLightningEffect(l);
            case "FOGOS" -> {
                Firework f = l.getWorld().spawn(centro, Firework.class, fw -> {
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(Color.RED, Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).flicker(true).build());
                    meta.setPower(0);
                    fw.setFireworkMeta(meta);
                    fw.getPersistentDataContainer().set(fogosDeAbate, PersistentDataType.BYTE, (byte) 1);
                });
                Bukkit.getScheduler().runTaskLater(plugin, f::detonate, 2L);
            }
            case "CORACOES" -> {
                l.getWorld().spawnParticle(Particle.HEART, centro, 25, 0.8, 0.8, 0.8, 0);
                l.getWorld().playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
            }
            case "ALMAS" -> {
                l.getWorld().spawnParticle(Particle.SOUL, centro, 30, 0.5, 0.8, 0.5, 0.05);
                l.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, centro, 20, 0.4, 0.6, 0.4, 0.02);
                l.getWorld().playSound(l, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 1f);
            }
            case "EXPLOSAO" -> {
                l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, centro, 1);
                l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            }
            case "NOTAS" -> {
                for (int i = 0; i < 12; i++) {
                    l.getWorld().spawnParticle(Particle.NOTE, centro.clone().add(Math.cos(i) * 0.8, i * 0.1, Math.sin(i) * 0.8), 1, 0, 0, 0, i / 24.0);
                }
                l.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
            }
            default -> { }
        }
    }

    /** Os fogos do efeito de abate são só pra ver: não machucam ninguém. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoFogosMachucar(EntityDamageByEntityEvent evento) {
        if (evento.getDamager() instanceof Firework f && f.getPersistentDataContainer().has(fogosDeAbate)) evento.setCancelled(true);
    }
}
