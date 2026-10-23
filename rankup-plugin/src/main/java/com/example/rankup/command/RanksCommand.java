package com.example.rankup.command;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Mina;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ranks — lista os ranks. /ranks &lt;jogador&gt; — rank de alguém.
 * Staff (rankup.admin): /ranks admin set &lt;jogador&gt; &lt;rank&gt;, /ranks admin reload.
 */
public class RanksCommand implements CommandExecutor, TabCompleter {

    private final RankupPlugin plugin;

    public RanksCommand(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
            return true;
        }
        if (args.length == 0 && sender instanceof Player p && plugin.getMenus().ativados()) {
            plugin.getMenus().abrirRanks(p);
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("lista")) {
            verJogador(sender, args[0]);
            return true;
        }
        listar(sender);
        return true;
    }

    private void listar(CommandSender sender) {
        Rank meu = sender instanceof Player p ? plugin.getRankManager().getRankDe(p.getUniqueId()) : null;
        int meuIndice = meu != null ? plugin.getRankManager().indice(meu) : -1;

        sender.sendMessage("§6§l--- Ranks ---");
        List<Rank> ranks = plugin.getRankManager().getRanks();
        for (int i = 0; i < ranks.size(); i++) {
            Rank rank = ranks.get(i);
            String marca = i < meuIndice ? "§a✔ " : i == meuIndice ? "§e➤ " : "§8• ";
            StringBuilder linha = new StringBuilder(marca).append(rank.nomeColorido());
            if (i > 0) linha.append(" §7- §f").append(Formato.valor(rank.preco()));
            String bonus = Formato.bonus(rank.multiplicadorVenda());
            if (!bonus.isEmpty()) linha.append(" §7| venda §a").append(bonus);
            List<String> minas = minasDoRank(rank);
            if (!minas.isEmpty()) linha.append(" §7| minas: §f").append(String.join(", ", minas));
            sender.sendMessage(linha.toString());
        }
        if (meu != null) {
            Rank proximo = plugin.getRankManager().getProximo(meu);
            sender.sendMessage(proximo != null
                    ? "§7Próximo: " + proximo.nomeColorido() + " §7por §f" + Formato.valor(proximo.preco()) + " §7(/rankup)"
                    : "§eVocê está no rank máximo.");
        }
    }

    private List<String> minasDoRank(Rank rank) {
        List<String> ids = new ArrayList<>();
        for (Mina mina : plugin.getMinaManager().getMinas()) {
            if (rank.id().equalsIgnoreCase(mina.getRankMinimo())) ids.add(mina.getId());
        }
        return ids;
    }

    private void verJogador(CommandSender sender, String nome) {
        OfflinePlayer alvo = Bukkit.getOfflinePlayerIfCached(nome);
        if (alvo == null) {
            sender.sendMessage("§cJogador §f" + nome + " §cnunca entrou no servidor.");
            return;
        }
        Rank rank = plugin.getRankManager().getRankDe(alvo.getUniqueId());
        sender.sendMessage("§f" + alvo.getName() + " §7está no rank " + rank.nomeColorido()
                + " §7(" + (plugin.getRankManager().indice(rank) + 1) + "/" + plugin.getRankManager().getRanks().size() + ")");
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(RankupPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUso: §f/ranks admin set <jogador> <rank>");
                    return;
                }
                OfflinePlayer alvo = Bukkit.getOfflinePlayerIfCached(args[2]);
                if (alvo == null) {
                    sender.sendMessage("§cJogador §f" + args[2] + " §cnunca entrou no servidor.");
                    return;
                }
                Rank novo = plugin.getRankManager().getRank(args[3]);
                if (novo == null) {
                    sender.sendMessage("§cRank §f" + args[3] + " §cnão existe. Veja /ranks.");
                    return;
                }
                Rank anterior = plugin.getRankManager().getRankDe(alvo.getUniqueId());
                plugin.aplicarRank(alvo, anterior, novo);
                sender.sendMessage("§a" + alvo.getName() + " agora está no rank " + novo.nomeColorido() + "§a.");
                if (alvo.getPlayer() != null && alvo.getPlayer() != sender) {
                    alvo.getPlayer().sendMessage("§eA staff mudou seu rank pra " + novo.nomeColorido() + "§e.");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getRankManager().carregarRanks();
                sender.sendMessage("§aConfiguração recarregada: " + plugin.getRankManager().getRanks().size() + " rank(s).");
            }
            default -> sender.sendMessage("§cUso: §f/ranks admin <set|reload>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(RankupPlugin.PERMISSAO_ADMIN);
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            if (admin) opcoes.add("admin");
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                opcoes.addAll(List.of("set", "reload"));
            } else if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
                Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                plugin.getRankManager().getRanks().forEach(r -> opcoes.add(r.id()));
            }
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefixo)).toList();
    }
}
