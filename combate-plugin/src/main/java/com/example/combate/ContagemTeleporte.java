package com.example.combate;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contagem da espera do teleporte do Essentials ({@code teleport-delay}): o Essentials só avisa no
 * começo ("Teleporte em 3 segundos.") e no fim ("Teleportando..."). Aqui sai um aviso a cada segundo
 * que falta ("Teleporte em 2 segundos.", "Teleporte em 1 segundo."). A espera começa no
 * {@code TeleportWarmupEvent} do Essentials (vale pro /spawn, /warp, /home, /back e pro /tpa, que só
 * espera quando o outro aceita); a contagem para se o jogador teleportar ou sair.
 * <p>
 * Qualquer dano na espera cancela o teleporte. O Essentials só confere a vida de quem NÃO pode andar
 * (aqui todo mundo pode, com {@code essentials.teleport.timer.move}) e, sozinho, só cancela no ataque de
 * uma entidade: queda, fogo e lava passavam. O cancelamento chama o mesmo método que ele usa no ataque
 * ({@code AsyncTeleport.cancel}, privado, por reflexão), com a mensagem "Teleporte cancelado." dele; se
 * isso falhar (o Essentials mudou), o teleporte por comando de quem tomou dano é barrado no fim da espera.
 * <p>
 * O evento é achado por nome, então o plugin não depende do Essentials pra compilar e, sem ele, isto só
 * não faz nada.
 */
public class ContagemTeleporte implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final String EVENTO = "net.ess3.api.events.teleport.TeleportWarmupEvent";

    private final CombatePlugin plugin;
    private final Map<UUID, BukkitTask> contagens = new HashMap<>();
    /** O usuário do Essentials de quem está na espera (pra cancelar o teleporte dele). */
    private final Map<UUID, Object> esperando = new HashMap<>();
    /** Tomou dano na espera e o cancelamento pelo Essentials falhou: o próximo teleporte por comando não vale. */
    private final java.util.Set<UUID> barrar = new java.util.HashSet<>();

    public ContagemTeleporte(CombatePlugin plugin) {
        this.plugin = plugin;
    }

    /** Registra o ouvinte do evento do Essentials. Devolve false se o Essentials não está no servidor. */
    @SuppressWarnings("unchecked")
    boolean registrar() {
        Class<? extends Event> classe;
        try {
            classe = (Class<? extends Event>) Class.forName(EVENTO, true, plugin.getServer().getPluginManager()
                    .getPlugin("Essentials").getClass().getClassLoader());
        } catch (Exception | LinkageError e) {
            return false;
        }
        plugin.getServer().getPluginManager().registerEvent(classe, this, EventPriority.MONITOR, (ouvinte, evento) -> {
            if (classe.isInstance(evento)) aoComecarEspera(evento);
        }, plugin, true);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        return true;
    }

    private void aoComecarEspera(Event evento) {
        Player jogador;
        double segundos;
        Object usuario;
        try {
            usuario = evento.getClass().getMethod("getTeleportee").invoke(evento);
            jogador = (Player) usuario.getClass().getMethod("getBase").invoke(usuario);
            segundos = ((Number) evento.getClass().getMethod("getDelay").invoke(evento)).doubleValue();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return;
        }
        parar(jogador.getUniqueId());
        barrar.remove(jogador.getUniqueId());
        esperando.put(jogador.getUniqueId(), usuario);
        int[] resta = {(int) Math.round(segundos)};
        BukkitTask tarefa = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            resta[0]--;
            if (resta[0] <= 0 || !jogador.isOnline()) {
                pararContagem(jogador.getUniqueId());
                // a espera acabou: se o teleporte não saiu (destino inseguro, cancelado pelo próprio
                // Essentials), um dano depois disso não deve mandar "cancelado" de novo
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> esperando.remove(jogador.getUniqueId(), usuario), 40L);
                return;
            }
            String msg = resta[0] == 1
                    ? texto("um-segundo", "&6Teleporte em &c1 segundo&6.")
                    : texto("segundos", "&6Teleporte em &c%s segundos&6.").replace("%s", String.valueOf(resta[0]));
            jogador.sendMessage(LEGADO.deserialize(msg));
        }, 20L, 20L);
        contagens.put(jogador.getUniqueId(), tarefa);
    }

    private void pararContagem(UUID jogador) {
        BukkitTask tarefa = contagens.remove(jogador);
        if (tarefa != null) tarefa.cancel();
    }

    private void parar(UUID jogador) {
        pararContagem(jogador);
        esperando.remove(jogador);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (!(evento.getEntity() instanceof Player p) || evento.getFinalDamage() <= 0) return;
        Object usuario = esperando.get(p.getUniqueId());
        if (usuario == null) return;
        parar(p.getUniqueId());
        try {
            Object teleporte = usuario.getClass().getMethod("getAsyncTeleport").invoke(usuario);
            java.lang.reflect.Method cancelar = teleporte.getClass().getDeclaredMethod("cancel", boolean.class);
            cancelar.setAccessible(true);
            cancelar.invoke(teleporte, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            barrar.add(p.getUniqueId());
            p.sendMessage(LEGADO.deserialize(texto("cancelado", "&cTeleporte cancelado.")));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        UUID id = evento.getPlayer().getUniqueId();
        if (evento.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND && barrar.remove(id)) {
            evento.setCancelled(true);
            return;
        }
        parar(id);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        parar(evento.getPlayer().getUniqueId());
        barrar.remove(evento.getPlayer().getUniqueId());
    }

    private String texto(String chave, String padrao) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("contagem-teleporte." + chave, padrao));
    }
}
