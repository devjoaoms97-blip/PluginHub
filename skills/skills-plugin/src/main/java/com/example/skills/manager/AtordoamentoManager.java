package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atordoamento de verdade: trava o MOVIMENTO do jogador (ele ainda consegue olhar em
 * volta, mas não anda, não pula, não é afetado pela gravidade durante o efeito — isso
 * é uma simplificação; ele "flutua" no lugar em vez de cair, já que o bloqueio funciona
 * cancelando qualquer mudança de X/Y/Z no PlayerMoveEvent).
 *
 * Reaplicar reinicia a duração em vez de empilhar (mesmo padrão do Sangramento).
 */
public class AtordoamentoManager {

    private final SkillsPlugin plugin;
    private final Map<UUID, BukkitTask> tarefasAtivas = new ConcurrentHashMap<>();

    public AtordoamentoManager(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean estaAtordoado(UUID jogadorId) {
        return tarefasAtivas.containsKey(jogadorId);
    }

    public void aplicar(LivingEntity vitima, int duracaoSegundos) {
        UUID id = vitima.getUniqueId();

        BukkitTask existente = tarefasAtivas.remove(id);
        if (existente != null) {
            existente.cancel();
        }

        int[] tiquesRestantes = {duracaoSegundos};

        BukkitTask tarefa = new BukkitRunnable() {
            @Override
            public void run() {
                if (!vitima.isValid() || vitima.isDead()) {
                    tarefasAtivas.remove(id);
                    cancel();
                    return;
                }

                if (vitima instanceof Player p) {
                    p.sendActionBar(Component.text("★ Atordoado (" + tiquesRestantes[0] + "s)", NamedTextColor.YELLOW));
                }

                vitima.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, vitima.getLocation().add(0, 2.2, 0), 3, 0.2, 0.1, 0.2, 0);

                tiquesRestantes[0]--;
                if (tiquesRestantes[0] <= 0) {
                    tarefasAtivas.remove(id);
                    if (vitima instanceof Player p) {
                        p.sendActionBar(Component.empty());
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        tarefasAtivas.put(id, tarefa);
    }

    public void cancelarTodas() {
        tarefasAtivas.values().forEach(BukkitTask::cancel);
        tarefasAtivas.clear();
    }
}
