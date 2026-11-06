package com.example.placar;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A vida embaixo do nome, acima da cabeça ("18 ❤"). Igual às tags ({@link NomesNaCabeca}): cada jogador
 * vê o próprio scoreboard, então o objetivo fica em todos os em uso. É um objetivo comum, atualizado
 * aqui, e não o de critério "health" do Minecraft: esse só aparece depois da primeira mudança de vida,
 * e quem acabou de ganhar um scoreboard novo (o placar) ficaria com 0 em cima da cabeça.
 * <p>
 * Muda no tick seguinte ao dano, à cura e ao renascer, e confere tudo junto com o placar.
 */
class VidaNaCabeca implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();
    private static final String OBJETIVO = "vida_cabeca";

    private final PlacarPlugin plugin;
    /** Scoreboard → jogador → a vida já mostrada (pra não mandar pacote à toa). */
    private final Map<Scoreboard, Map<String, Integer>> mostrados = new WeakHashMap<>();

    VidaNaCabeca(PlacarPlugin plugin) {
        this.plugin = plugin;
    }

    boolean ativado() {
        return plugin.getConfig().getBoolean("vida-na-cabeca.ativado", true);
    }

    /** Põe o objetivo em todos os scoreboards em uso e a vida de todos os online. */
    void atualizar(Collection<Scoreboard> placares) {
        Set<Scoreboard> todos = emUso(placares);
        if (!ativado()) {
            for (Scoreboard s : todos) {
                Objective o = s.getObjective(OBJETIVO);
                if (o != null) o.unregister();
                mostrados.remove(s);
            }
            return;
        }
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Scoreboard s : todos) {
            for (Player p : online) mostrar(s, p);
        }
    }

    /** Só a vida de um jogador, em todos os scoreboards. */
    private void atualizar(Player p) {
        if (!ativado() || !p.isOnline()) return;
        for (Scoreboard s : emUso(plugin.placaresEmUso())) mostrar(s, p);
    }

    private Set<Scoreboard> emUso(Collection<Scoreboard> placares) {
        Set<Scoreboard> todos = Collections.newSetFromMap(new IdentityHashMap<>());
        todos.addAll(placares);
        todos.add(Bukkit.getScoreboardManager().getMainScoreboard());
        return todos;
    }

    private void mostrar(Scoreboard s, Player p) {
        Objective o = s.getObjective(OBJETIVO);
        if (o == null) {
            o = s.registerNewObjective(OBJETIVO, Criteria.DUMMY,
                    LEGADO.deserialize(plugin.getConfig().getString("vida-na-cabeca.simbolo", "&c❤")));
            o.setDisplaySlot(DisplaySlot.BELOW_NAME);
            mostrados.remove(s);
        }
        int vida = (int) Math.ceil(p.getHealth() + p.getAbsorptionAmount());
        Map<String, Integer> feitos = mostrados.computeIfAbsent(s, k -> new HashMap<>());
        if (Integer.valueOf(vida).equals(feitos.get(p.getName()))) return;
        o.getScore(p.getName()).setScore(vida);
        feitos.put(p.getName(), vida);
    }

    private void depois(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> atualizar(p));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity() instanceof Player p) depois(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoCurar(EntityRegainHealthEvent evento) {
        if (evento.getEntity() instanceof Player p) depois(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoRenascer(PlayerRespawnEvent evento) {
        depois(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        // quem entra precisa ver a vida de todos, e todos a dele: o placar novo dele chega uns ticks depois
        Bukkit.getScheduler().runTaskLater(plugin, () -> atualizar(plugin.placaresEmUso()), 10L);
    }

    /** Pra quem saiu não sobrar na memória (o scoreboard dele some sozinho, o principal não). */
    void esquecer(String nome) {
        for (Map<String, Integer> m : mostrados.values()) m.remove(nome);
        for (Scoreboard s : List.copyOf(mostrados.keySet())) {
            Objective o = s.getObjective(OBJETIVO);
            if (o != null) s.resetScores(nome);
        }
    }
}
