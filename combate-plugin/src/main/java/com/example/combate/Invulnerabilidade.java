package com.example.combate;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tempo entre golpes de jogador (o "meio segundo de invulnerabilidade"), configurável pra
 * ajustar o dano por segundo. No jogo, quem apanha fica com 20 ticks de invulnerabilidade e só
 * toma um golpe cheio de novo quando faltam 10 ou menos: na prática, 10 ticks (0,5 s) entre
 * golpes, de qualquer mão.
 * <p>
 * Com {@code separar-maos} (padrão), cada mão tem o próprio tempo: quem alterna as duas armas
 * bate duas vezes mais. Pra isso o plugin conta o tempo de cada mão em cada alvo, barra o golpe
 * que vem cedo demais e zera o contador do jogo depois de cada golpe de jogador. Sem separar,
 * só ajusta o contador do jogo pro tempo configurado (as mãos dividem o tempo).
 * <p>
 * Só vale pra golpe corpo a corpo de jogador: lava, cacto, fogo, explosão e flecha continuam com
 * o tempo normal (com 0 pra tudo, a lava mataria quase na hora). O ataque em área da espada
 * segue o jogo.
 */
public class Invulnerabilidade implements Listener {

    /** O jogo aceita golpe cheio quando o contador chega na metade do máximo (20 / 2). */
    private static final int METADE_PADRAO = 10;

    private final CombatePlugin plugin;
    /** Tick do último golpe aceito de cada mão ({principal, segunda}) em cada alvo. */
    private final Map<UUID, int[]> ultimoGolpe = new HashMap<>();

    public Invulnerabilidade(CombatePlugin plugin) {
        this.plugin = plugin;
        // limpa alvos sem golpe há mais de 10 s (mortos, que saíram, mobs que sumiram)
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int agora = plugin.getServer().getCurrentTick();
            ultimoGolpe.values().removeIf(t -> agora - Math.max(t[0], t[1]) > 200);
        }, 1200L, 1200L);
    }

    public int ticksEntreGolpes() {
        return Math.max(0, plugin.getConfig().getInt("invulnerabilidade.ticks-entre-golpes", METADE_PADRAO));
    }

    public boolean separarMaos() {
        return plugin.getConfig().getBoolean("invulnerabilidade.separar-maos", true);
    }

    /** Barra o golpe que vem antes do tempo da mão que bateu. Bem cedo, pra skills e afins nem verem. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void antesDoGolpe(EntityDamageByEntityEvent evento) {
        if (!separarMaos() || evento.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(evento.getDamager() instanceof Player jogador) || !(evento.getEntity() instanceof LivingEntity alvo)) return;
        int mao = plugin.golpeandoComASegundaMao(jogador) ? 1 : 0;
        int agora = plugin.getServer().getCurrentTick();
        int[] ultimo = ultimoGolpe.computeIfAbsent(alvo.getUniqueId(), k -> new int[]{Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2});
        if (agora - ultimo[mao] < ticksEntreGolpes()) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void depoisDoGolpe(EntityDamageByEntityEvent evento) {
        if (!(evento.getDamager() instanceof Player jogador) || !(evento.getEntity() instanceof LivingEntity alvo)) return;
        if (separarMaos()) {
            if (evento.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            int[] ultimo = ultimoGolpe.get(alvo.getUniqueId());
            if (ultimo != null) ultimo[plugin.golpeandoComASegundaMao(jogador) ? 1 : 0] = plugin.getServer().getCurrentTick();
            // o tempo de cada mão é daqui; o contador do jogo (que o golpe vai encher) não pode barrar a outra mão
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (alvo.isValid() && !alvo.isDead()) alvo.setNoDamageTicks(0);
            });
            return;
        }
        int ticks = ticksEntreGolpes();
        if (ticks == METADE_PADRAO) return; // igual ao jogo: não mexe
        // o jogo põe o contador no máximo depois deste evento; o ajuste vai no tick seguinte (por isso o -1)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!alvo.isValid() || alvo.isDead()) return;
            int metade = alvo.getMaximumNoDamageTicks() / 2;
            alvo.setNoDamageTicks(Math.max(0, metade + ticks - 1));
        });
    }
}
