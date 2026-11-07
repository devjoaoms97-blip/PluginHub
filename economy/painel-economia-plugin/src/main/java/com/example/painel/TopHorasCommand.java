package com.example.painel;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /tophoras (pedido de 2026-11-07): quem mais jogou no servidor, pela estatística do Minecraft
 * (PLAY_ONE_MINUTE, em ticks; conta o tempo AFK também). Ler a de quem está offline abre o arquivo
 * de estatísticas de cada um, então essa parte fica em cache por circulacao.cache-minutos; quem
 * está online entra sempre com o valor do momento. Ops e os nicks de {@code ricos.esconder} ficam de fora.
 */
public class TopHorasCommand implements CommandExecutor {

    private record Tempo(UUID id, String nome, long ticks) {}

    private final PainelEconomiaPlugin plugin;
    private Map<UUID, Tempo> cache = Map.of();
    private long cacheEm;

    public TopHorasCommand(PainelEconomiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Set<String> esconder = plugin.getConfig().getStringList("ricos.esconder").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<UUID, Tempo> tempos = new HashMap<>(offline());
        for (Player p : Bukkit.getOnlinePlayers()) {
            tempos.put(p.getUniqueId(), new Tempo(p.getUniqueId(), p.getName(), p.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        }
        List<Tempo> visiveis = tempos.values().stream()
                .filter(t -> t.ticks() > 0 && !escondido(t.id(), t.nome(), esconder))
                .sorted(Comparator.comparingLong(Tempo::ticks).reversed()).toList();
        int quantos = Math.max(1, plugin.getConfig().getInt("tophoras.quantos", 10));

        sender.sendMessage("§6§l⏱ Quem mais jogou no servidor");
        if (visiveis.isEmpty()) sender.sendMessage("§7Ninguém jogou ainda.");
        for (int i = 0; i < Math.min(quantos, visiveis.size()); i++) {
            Tempo t = visiveis.get(i);
            String medalha = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§8" + (i + 1) + "º";
            sender.sendMessage(medalha + " " + RicosCommand.tags(t.id()) + "§f" + t.nome() + " §7- §e" + horas(t.ticks()));
        }
        if (sender instanceof Player p) {
            int pos = -1;
            for (int i = 0; i < visiveis.size(); i++) {
                if (visiveis.get(i).id().equals(p.getUniqueId())) {
                    pos = i + 1;
                    break;
                }
            }
            if (pos > quantos) sender.sendMessage("§7Você: §f" + pos + "º §7de " + visiveis.size() + ", com §e" + horas(visiveis.get(pos - 1).ticks()));
        }
        return true;
    }

    /** O tempo de todo jogador que já entrou, em cache (o arquivo de estatísticas de cada um é lido do disco). */
    private Map<UUID, Tempo> offline() {
        long validade = Math.max(1, plugin.getConfig().getInt("circulacao.cache-minutos", 5)) * 60_000L;
        if (System.currentTimeMillis() - cacheEm < validade) return cache;
        Map<UUID, Tempo> novo = new HashMap<>();
        for (OfflinePlayer o : Bukkit.getOfflinePlayers()) {
            if (o.getName() == null) continue;
            try {
                novo.put(o.getUniqueId(), new Tempo(o.getUniqueId(), o.getName(), o.getStatistic(Statistic.PLAY_ONE_MINUTE)));
            } catch (RuntimeException e) {
                // sem arquivo de estatísticas: fica fora
            }
        }
        cache = novo;
        cacheEm = System.currentTimeMillis();
        return cache;
    }

    private static boolean escondido(UUID id, String nome, Set<String> esconder) {
        return Bukkit.getOfflinePlayer(id).isOp() || esconder.contains(nome.toLowerCase(Locale.ROOT));
    }

    /** "123h 45min" (ticks: 20 por segundo). */
    private static String horas(long ticks) {
        long minutos = ticks / 20 / 60;
        return String.format(Locale.forLanguageTag("pt-BR"), "%,dh %02dmin", minutos / 60, minutos % 60);
    }
}
