package com.example.clas.listener;

import com.example.clas.ClasPlugin;
import com.example.clas.model.Cla;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Integração com o BossPlugin, sem dependência entre os dois: o boss é reconhecido pela chave
 * compartilhada {@code pluginhub:boss_mob} no PersistentDataContainer (a mesma que o SkillsPlugin
 * usa).
 *
 * <ul>
 *   <li>O clã de quem dá o último golpe vira o <b>clã do campeão</b> e ganha {@code boss.pontos-campeao}.</li>
 *   <li>Os outros clãs cujos membros, somados, tiraram pelo menos {@code boss.participacao-minima-percentual}
 *       da vida do boss ganham {@code boss.pontos-participacao}.</li>
 * </ul>
 * O dano é contado do mesmo jeito que o ranking do BossPlugin: MONITOR, só o dano que entrou,
 * limitado à vida que o boss ainda tinha.
 */
public class BossListener implements Listener {

    private static final NamespacedKey CHAVE_BOSS = new NamespacedKey("pluginhub", "boss_mob");

    private final ClasPlugin plugin;
    // boss (entidade) -> clã (chave) -> dano somado dos membros
    private final Map<UUID, Map<String, Double>> danoPorCla = new HashMap<>();

    public BossListener(ClasPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ehBoss(Entity entidade) {
        return entidade.getPersistentDataContainer().has(CHAVE_BOSS, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoCausarDano(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof LivingEntity boss) || !ehBoss(boss)) return;
        Player atacante = jogadorPor(evento.getDamager());
        if (atacante == null) return;
        Cla cla = plugin.getClaManager().getClaDe(atacante.getUniqueId());
        if (cla == null) return;

        double dano = Math.min(evento.getFinalDamage(), boss.getHealth());
        if (dano > 0) {
            // boss que sumiu sem morrer (/boss stop, restart) não chega no aoMorrer: limpa aqui
            danoPorCla.keySet().removeIf(id -> !id.equals(boss.getUniqueId()) && Bukkit.getEntity(id) == null);
            danoPorCla.computeIfAbsent(boss.getUniqueId(), k -> new HashMap<>()).merge(cla.chave(), dano, Double::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        LivingEntity boss = evento.getEntity();
        if (!ehBoss(boss)) return;
        Map<String, Double> danos = danoPorCla.remove(boss.getUniqueId());
        if (!plugin.getConfig().getBoolean("boss.ativado", true)) return;

        Cla campeao = null;
        Player matador = boss.getKiller();
        if (matador != null) {
            campeao = plugin.getClaManager().getClaDe(matador.getUniqueId());
        }

        List<String> mensagens = new ArrayList<>();
        if (campeao != null) {
            int pontos = Math.max(0, plugin.getConfig().getInt("boss.pontos-campeao", 10));
            campeao.setBosses(campeao.getBosses() + 1);
            campeao.setPontosBoss(campeao.getPontosBoss() + pontos);
            plugin.getClaManager().setClaCampeaoBoss(campeao);
            mensagens.add("§6§l★ §6O clã " + plugin.tagFormatada(campeao) + " §f" + campeao.getNome()
                    + " §6é o clã do campeão! §7(+" + pontos + " pontos)");
        }

        if (danos != null) {
            double vidaMaxima = vidaMaxima(boss);
            double minimo = vidaMaxima * Math.max(0, plugin.getConfig().getDouble("boss.participacao-minima-percentual", 10)) / 100.0;
            int pontos = Math.max(0, plugin.getConfig().getInt("boss.pontos-participacao", 3));
            for (Map.Entry<String, Double> entrada : danos.entrySet()) {
                Cla cla = plugin.getClaManager().get(entrada.getKey());
                if (cla == null || cla == campeao || pontos == 0 || entrada.getValue() < minimo) continue;
                cla.setPontosBoss(cla.getPontosBoss() + pontos);
                mensagens.add("§7O clã " + plugin.tagFormatada(cla) + " §7ajudou a derrubar o boss §8(+" + pontos + " pontos)");
            }
        }

        if (campeao != null || !mensagens.isEmpty()) {
            plugin.getClaManager().salvar();
        }
        mensagens.forEach(Bukkit::broadcastMessage);
    }

    private double vidaMaxima(LivingEntity boss) {
        AttributeInstance atributo = boss.getAttribute(Attribute.MAX_HEALTH);
        return atributo != null ? atributo.getValue() : 0;
    }

    private Player jogadorPor(Entity causador) {
        if (causador instanceof Player jogador) return jogador;
        if (causador instanceof Projectile projetil && projetil.getShooter() instanceof Player atirador) return atirador;
        return null;
    }
}
