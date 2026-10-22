package com.example.rankup.listener;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Mina;
import com.example.rankup.model.Rank;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras das minas: só minera quem tem o rank, ninguém coloca bloco dentro, nada minera durante o
 * reset e explosão não abre buraco. Nos mundos de protecao.mundos, fora das minas ninguém quebra
 * nem coloca. Quem tem rankup.admin ignora tudo isso.
 */
public class MinaListener implements Listener {

    private static final long INTERVALO_AVISO_MS = 3000;

    private final RankupPlugin plugin;
    /** Último aviso por jogador, pra não spammar a cada bloco. */
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();

    public MinaListener(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Player jogador = evento.getPlayer();
        if (jogador.hasPermission(RankupPlugin.PERMISSAO_ADMIN)) return;

        Mina mina = plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation());
        if (mina == null) {
            if (mundoProtegido(evento.getBlock())) {
                evento.setCancelled(true);
                avisar(jogador, "§cAqui só dá pra minerar nas minas. Veja §f/mina§c.");
            }
            return;
        }
        if (mina.isResetando()) {
            evento.setCancelled(true);
            avisar(jogador, "§eA mina está sendo resetada, espere um pouco.");
            return;
        }
        if (!plugin.getRankManager().temAcesso(jogador.getUniqueId(), mina.getRankMinimo())) {
            evento.setCancelled(true);
            Rank rank = plugin.getRankManager().getRank(mina.getRankMinimo());
            avisar(jogador, "§cEssa mina é a partir do rank " + rank.nomeColorido() + "§c. Veja §f/ranks§c.");
        }
    }

    /** Conta o bloco só se ninguém cancelou a quebra (MONITOR roda por último). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoQuebrarContar(BlockBreakEvent evento) {
        Mina mina = plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation());
        if (mina != null && !mina.isResetando()) {
            plugin.getMinaManager().registrarQuebra(mina);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        Player jogador = evento.getPlayer();
        if (jogador.hasPermission(RankupPlugin.PERMISSAO_ADMIN)) return;

        if (plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation()) != null) {
            evento.setCancelled(true);
            avisar(jogador, "§cNão dá pra colocar blocos dentro da mina.");
        } else if (mundoProtegido(evento.getBlock())) {
            evento.setCancelled(true);
            avisar(jogador, "§cVocê não pode construir aqui.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        filtrarExplosao(evento.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        filtrarExplosao(evento.blockList());
    }

    private void filtrarExplosao(List<Block> blocos) {
        blocos.removeIf(bloco -> mundoProtegido(bloco) || plugin.getMinaManager().getMinaEm(bloco.getLocation()) != null);
    }

    private boolean mundoProtegido(Block bloco) {
        return plugin.getConfig().getStringList("protecao.mundos").contains(bloco.getWorld().getName());
    }

    private void avisar(Player jogador, String mensagem) {
        long agora = System.currentTimeMillis();
        Long ultimo = ultimoAviso.get(jogador.getUniqueId());
        if (ultimo != null && agora - ultimo < INTERVALO_AVISO_MS) return;
        ultimoAviso.put(jogador.getUniqueId(), agora);
        jogador.sendMessage(mensagem);
    }
}
