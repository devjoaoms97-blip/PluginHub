package com.example.painel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /ricos (pedido de 2026-11-07): os mais ricos do servidor, pra todo mundo, com a tag do rank e do clã,
 * e a posição de quem pediu. É a mesma conta do /economia top (em cache por circulacao.cache-minutos:
 * ler o saldo de toda conta offline é lento). Ops e os nicks de {@code ricos.esconder} ficam de fora.
 */
public class RicosCommand implements CommandExecutor {

    private final PainelEconomiaPlugin plugin;

    public RicosCommand(PainelEconomiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.temEconomia()) {
            sender.sendMessage("§cA economia não está disponível agora.");
            return true;
        }
        PainelEconomiaPlugin.Circulacao circ = plugin.calcularCirculacao(false);
        Set<String> esconder = plugin.getConfig().getStringList("ricos.esconder").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<PainelEconomiaPlugin.Fortuna> visiveis = circ.todas().stream().filter(f -> !escondido(f, esconder)).toList();
        int quantos = Math.max(1, plugin.getConfig().getInt("ricos.quantos", 10));

        sender.sendMessage("§6§l💰 Os mais ricos do servidor");
        if (visiveis.isEmpty()) sender.sendMessage("§7Ninguém com dinheiro ainda.");
        for (int i = 0; i < Math.min(quantos, visiveis.size()); i++) {
            PainelEconomiaPlugin.Fortuna f = visiveis.get(i);
            String medalha = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§8" + (i + 1) + "º";
            sender.sendMessage(medalha + " " + tags(f.id()) + "§f" + f.nome() + " §7- §a" + valor(f.saldo()));
        }
        if (sender instanceof Player p) {
            int pos = -1;
            for (int i = 0; i < visiveis.size(); i++) {
                if (visiveis.get(i).id().equals(p.getUniqueId())) {
                    pos = i + 1;
                    break;
                }
            }
            if (pos > quantos) sender.sendMessage("§7Você: §f" + pos + "º §7de " + visiveis.size() + ", com §a" + valor(visiveis.get(pos - 1).saldo()));
            else if (pos < 0 && !escondido(new PainelEconomiaPlugin.Fortuna(p.getUniqueId(), p.getName(), 0), esconder)) {
                sender.sendMessage("§7Você ainda não entrou no ranking.");
            }
        }
        long minutos = Math.max(1, plugin.getConfig().getInt("circulacao.cache-minutos", 5));
        sender.sendMessage("§8Atualiza a cada " + minutos + " min.");
        return true;
    }

    private boolean escondido(PainelEconomiaPlugin.Fortuna f, Set<String> esconder) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(f.id());
        return o.isOp() || esconder.contains(f.nome().toLowerCase(Locale.ROOT));
    }

    /** "[Rei] [CLÃ] " antes do nome: a tag do rank (RankupPlugin) e a do clã (ClasPlugin), se existirem. */
    static String tags(UUID id) {
        StringBuilder sb = new StringBuilder();
        Object rank = chamar("RankupPlugin", "tagDoRank", id);
        if (rank != null && !rank.toString().isBlank()) sb.append(cores(rank.toString())).append(' ');
        Object cla = chamar("ClasPlugin", "tagDoCla", id);
        if (cla != null && !cla.toString().isBlank()) sb.append("§b[").append(cla).append("] ");
        return sb.toString();
    }

    private static Object chamar(String nomePlugin, String metodo, UUID id) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nomePlugin);
        if (p == null || !p.isEnabled()) return null;
        try {
            return p.getClass().getMethod(metodo, UUID.class).invoke(p, id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    private static String valor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
