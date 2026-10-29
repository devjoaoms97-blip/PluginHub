package com.example.skills.listener;

import com.example.skills.SkillsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Quem morre renasce limpo: nenhum efeito atravessa a morte.
 * <ul>
 *   <li>Sangramento e atordoamento são tarefas nossas que conferem a morte só uma vez por segundo;
 *       quem renascia antes disso seguia sangrando (ou travado) no spawn. Param na hora da morte.</li>
 *   <li>O jogo limpa as poções ao morrer, mas o que é aplicado com o jogador já morto (na tela de
 *       morte, como o Wither em área do boss) atravessava o renascimento. Um tique depois de renascer,
 *       qualquer poção que sobrou sai.</li>
 * </ul>
 */
public class MorteListener implements Listener {

    private final SkillsPlugin plugin;

    public MorteListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        Player jogador = evento.getEntity();
        plugin.getSangramentoManager().cancelar(jogador.getUniqueId());
        plugin.getAtordoamentoManager().cancelar(jogador.getUniqueId());
        jogador.sendActionBar(Component.empty());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoRenascer(PlayerRespawnEvent evento) {
        Player jogador = evento.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!jogador.isOnline() || jogador.isDead()) return;
            for (PotionEffect efeito : jogador.getActivePotionEffects()) jogador.removePotionEffect(efeito.getType());
            jogador.setFireTicks(0);
        }, 1L);
    }
}
