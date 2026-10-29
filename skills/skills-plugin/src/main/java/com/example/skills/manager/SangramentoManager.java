package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aplica dano real ao longo do tempo (pode matar). Se a vítima já estiver sangrando,
 * reaplicar REINICIA a duração em vez de empilhar múltiplos sangramentos simultâneos
 * (evita virar instakill fácil combinando vários procs em sequência).
 *
 * Como não é um PotionEffect de verdade, não aparece nos ícones padrão do jogo — por
 * isso mostramos uma mensagem na action bar (pra vítima) e partículas vermelhas
 * (visíveis pra qualquer um por perto) como indicador visual do efeito ativo.
 *
 * A cada tique, quem aplicou o sangramento é marcado como o último jogador que bateu na
 * vítima (setKiller). Sem isso, quem morria só do sangramento depois de 5 s sem apanhar
 * morria "sozinho", e o abate não contava pro Bounty, pros clãs, pras missões nem pro Gladiador.
 */
public class SangramentoManager {

    private final SkillsPlugin plugin;
    private final Map<UUID, BukkitTask> tarefasAtivas = new ConcurrentHashMap<>();

    public SangramentoManager(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void aplicar(LivingEntity vitima, Player atacante, double danoPorTick, int duracaoSegundos) {
        UUID id = vitima.getUniqueId();
        UUID atacanteId = atacante.getUniqueId();

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

                if (tiquesRestantes[0] <= 0) {
                    // Tick final "silencioso": só limpa, sem mostrar "0s" nem sobrepor a última contagem
                    tarefasAtivas.remove(id);
                    if (vitima instanceof Player p) {
                        p.sendActionBar(Component.empty());
                    }
                    cancel();
                    return;
                }

                if (vitima instanceof Player p) {
                    p.sendActionBar(Component.text("❤ Sangrando (" + tiquesRestantes[0] + "s)", NamedTextColor.DARK_RED));
                }

                vitima.getWorld().spawnParticle(
                        Particle.DUST,
                        vitima.getLocation().add(0, 1, 0),
                        15, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.2f)
                );

                // O dano vai sem o atacante de propósito: com ele, o CombatListener rodaria de
                // novo a cada tique (loop de procs e XP). O crédito do abate vem pelo setKiller.
                Player atacanteOnline = plugin.getServer().getPlayer(atacanteId);
                if (atacanteOnline != null && atacanteOnline != vitima) {
                    vitima.setKiller(atacanteOnline);
                }
                vitima.damage(danoPorTick);

                tiquesRestantes[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        tarefasAtivas.put(id, tarefa);
    }

    /** Para o sangramento de quem morreu: o tique só confere a morte uma vez por segundo, e quem renascia antes seguia sangrando. */
    public void cancelar(UUID vitima) {
        BukkitTask tarefa = tarefasAtivas.remove(vitima);
        if (tarefa != null) tarefa.cancel();
    }

    public void cancelarTodas() {
        tarefasAtivas.values().forEach(BukkitTask::cancel);
        tarefasAtivas.clear();
    }
}