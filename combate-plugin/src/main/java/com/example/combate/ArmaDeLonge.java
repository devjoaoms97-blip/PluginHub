package com.example.combate;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Carregar a besta ou puxar o arco ({@code arma-de-longe.armas}) trava a espada: enquanto o jogador
 * carrega, e por um tempo depois de disparar, nenhum golpe corpo a corpo dele conta (espada,
 * machado ou soco, de qualquer mão, inclusive o golpe da segunda mão). Bater e depois carregar
 * continua livre. Sem isso, com a besta numa mão e a espada na outra, dava pra carregar, bater e
 * disparar no mesmo combo.
 */
public class ArmaDeLonge implements Listener {

    private final CombatePlugin plugin;
    /** Quando cada jogador disparou pela última vez (ms). */
    private final Map<UUID, Long> disparou = new HashMap<>();

    ArmaDeLonge(CombatePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean armaDeLonge(ItemStack item) {
        if (item == null) return false;
        String nome = item.getType().name();
        return plugin.getConfig().getStringList("arma-de-longe.armas").stream().anyMatch(s -> nome.equals(s.toUpperCase(Locale.ROOT)));
    }

    /** Está carregando a besta ou puxando o arco agora? */
    private boolean carregando(Player p) {
        return p.isHandRaised() && armaDeLonge(p.getActiveItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoDisparar(EntityShootBowEvent evento) {
        // só as armas da lista (besta e arco)
        if (evento.getEntity() instanceof Player p && armaDeLonge(evento.getBow())) disparou.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoGolpear(EntityDamageByEntityEvent evento) {
        if (!(evento.getDamager() instanceof Player p)) return;
        EntityDamageEvent.DamageCause causa = evento.getCause();
        if (causa != EntityDamageEvent.DamageCause.ENTITY_ATTACK && causa != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        long travaMs = plugin.getConfig().getLong("arma-de-longe.trava-depois-de-disparar-ms", 500);
        boolean acabouDeDisparar = System.currentTimeMillis() - disparou.getOrDefault(p.getUniqueId(), 0L) < travaMs;
        if (carregando(p) || acabouDeDisparar) evento.setCancelled(true);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        disparou.remove(evento.getPlayer().getUniqueId());
    }
}
