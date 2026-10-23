package com.example.terrenos.listener;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.util.Formato;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** No login: imposto atrasado e terreno que foi a leilão enquanto o dono estava fora. */
public class JogadorListener implements Listener {

    private final TerrenosPlugin plugin;

    public JogadorListener(TerrenosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player jogador = evento.getPlayer();
        // 1 segundo depois, pra não se perder entre as mensagens de entrada
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!jogador.isOnline()) return;
            long agora = System.currentTimeMillis();
            for (Terreno t : plugin.getTerrenoManager().getDe(jogador.getUniqueId())) {
                if (plugin.getTerrenoManager().atrasado(t, agora)) plugin.getTerrenoManager().avisarAtraso(t, agora, true);
            }
            for (Terreno t : plugin.getTerrenoManager().getLeiloes()) {
                if (!jogador.getUniqueId().equals(t.getLeilao().getDonoAnterior())) continue;
                jogador.sendMessage("§c§lSeu terreno §f" + t.getId() + " §c§lfoi confiscado por imposto atrasado §ce está em leilão (termina em §f"
                        + Formato.tempo(t.getLeilao().getTerminaEm() - agora) + "§c)."
                        + (t.getLeilao().isDividaPaga() ? " §7Você já pagou a dívida e pode dar lance."
                        : " §cPra dar lance, pague antes a dívida de §f" + Formato.valor(t.getLeilao().getDivida())
                        + " §ccom §f/terreno divida pagar " + t.getId() + "§c."));
            }
        }, 20L);
    }
}
