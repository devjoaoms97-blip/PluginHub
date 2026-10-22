package com.example.barracas.listener;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.util.Formato;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Ao entrar: lembra o jogador da barraca dele e de itens esperando na caixa de retirada. */
public class JogadorListener implements Listener {

    private final BarracasPlugin plugin;

    public JogadorListener(BarracasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player jogador = evento.getPlayer();
        // um pouco depois, pra não se perder no meio das mensagens de entrada
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!jogador.isOnline()) return;

            Barraca barraca = plugin.getBarracaManager().getBarracaDe(jogador.getUniqueId());
            if (barraca != null) {
                jogador.sendMessage("§eSua barraca §f" + barraca.getId() + " §eestá alugada por mais §f"
                        + Formato.tempo(barraca.getAlugadaAte() - System.currentTimeMillis()) + "§e.");
            }

            int pilhas = plugin.getRetiradaManager().contar(jogador.getUniqueId());
            if (pilhas > 0) {
                jogador.sendMessage("§eVocê tem §f" + pilhas + " pilha(s) §ede itens da sua barraca esperando em §f/barraca retirar§e.");
            }
        }, 40L);
    }
}
