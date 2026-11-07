package com.example.missoes.manager;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.Missao;
import com.example.missoes.model.Periodo;
import com.example.missoes.model.ProgressoJogador;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * As missões do dia na tela (pedido de 2026-11-07): uma barra no alto com a missão que o jogador mexeu
 * por último (ou, sem nenhuma ainda, a primeira que falta), o progresso dela e quantas do dia já foram.
 * Some quando todas do dia acabam. Atualiza a cada segundo ({@code barra.ativado} no config).
 */
public class BarraMissoes implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final MissoesPlugin plugin;
    private final Map<UUID, BossBar> barras = new HashMap<>();

    public BarraMissoes(MissoesPlugin plugin) {
        this.plugin = plugin;
    }

    public void atualizarTodos() {
        boolean ativado = plugin.getConfig().getBoolean("barra.ativado", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ativado) atualizar(p);
            else esconder(p);
        }
    }

    private void atualizar(Player p) {
        MissaoManager m = plugin.getMissaoManager();
        ProgressoJogador atual = m.getAtual(p.getUniqueId(), Periodo.DIARIA);
        Map<String, Missao> pool = m.getPool(Periodo.DIARIA);
        int total = 0, feitas = 0;
        Missao primeiraQueFalta = null;
        for (Map.Entry<String, Integer> e : atual.getProgresso().entrySet()) {
            Missao missao = pool.get(e.getKey());
            if (missao == null) continue;
            total++;
            if (e.getValue() >= missao.quantidade()) feitas++;
            else if (primeiraQueFalta == null) primeiraQueFalta = missao;
        }
        if (total == 0 || feitas >= total) {
            esconder(p);
            return;
        }
        // a que ele mexeu por último, se ainda falta; senão, a primeira que falta
        Missao foco = pool.get(m.ultimaMissao(p.getUniqueId()));
        Integer progressoFoco = foco == null ? null : atual.getProgresso().get(foco.id());
        if (foco == null || progressoFoco == null || progressoFoco >= foco.quantidade()) {
            foco = primeiraQueFalta;
            progressoFoco = atual.getProgresso().get(foco.id());
        }
        int feito = progressoFoco == null ? 0 : progressoFoco;

        BossBar barra = barras.computeIfAbsent(p.getUniqueId(), k -> {
            BossBar b = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            p.showBossBar(b);
            return b;
        });
        barra.name(LEGADO.deserialize("§b📋 " + foco.descricao() + " §f" + feito + "/" + foco.quantidade()
                + " §7· " + feitas + "/" + total + " do dia §8(/missoes)"));
        barra.progress((float) Math.max(0, Math.min(1, feito / (double) Math.max(1, foco.quantidade()))));
    }

    private void esconder(Player p) {
        BossBar b = barras.remove(p.getUniqueId());
        if (b != null) p.hideBossBar(b);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        barras.remove(evento.getPlayer().getUniqueId());
    }
}
