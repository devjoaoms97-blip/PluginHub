package com.example.combate;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Tempo entre golpes de jogador (o "meio segundo de invulnerabilidade"), configurável pra
 * ajustar o dano por segundo. No jogo, quem apanha fica com 20 ticks de invulnerabilidade e só
 * toma um golpe cheio de novo quando faltam 10 ou menos: na prática, 10 ticks (0,5 s) entre
 * golpes. Aqui o contador do jogo é ajustado depois de cada golpe de jogador pro tempo do config.
 * <p>
 * Só vale pra golpe corpo a corpo de jogador: lava, cacto, fogo, explosão e flecha continuam com
 * o tempo normal (com 0 pra tudo, a lava mataria quase na hora). O ataque em área da espada
 * segue o jogo.
 */
public class Invulnerabilidade implements Listener {

    /** O jogo aceita golpe cheio quando o contador chega na metade do máximo (20 / 2). */
    private static final int METADE_PADRAO = 10;

    private final CombatePlugin plugin;

    public Invulnerabilidade(CombatePlugin plugin) {
        this.plugin = plugin;
    }

    public int ticksEntreGolpes() {
        return Math.max(0, plugin.getConfig().getInt("invulnerabilidade.ticks-entre-golpes", 2));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void depoisDoGolpe(EntityDamageByEntityEvent evento) {
        if (!(evento.getDamager() instanceof Player) || !(evento.getEntity() instanceof LivingEntity alvo)) return;
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
