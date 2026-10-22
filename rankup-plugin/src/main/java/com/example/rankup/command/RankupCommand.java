package com.example.rankup.command;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Mina;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /rankup sobe um rank; /rankup max sobe quantos o saldo pagar. */
public class RankupCommand implements CommandExecutor, TabCompleter {

    private final RankupPlugin plugin;

    public RankupCommand(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem subir de rank.");
            return true;
        }
        if (plugin.getEconomy() == null) {
            jogador.sendMessage("§cA economia (Vault) não está disponível no servidor.");
            return true;
        }

        boolean max = args.length > 0 && args[0].equalsIgnoreCase("max");
        Rank inicial = plugin.getRankManager().getRankDe(jogador.getUniqueId());
        Rank proximo = plugin.getRankManager().getProximo(inicial);
        if (proximo == null) {
            jogador.sendMessage("§eVocê já está no rank máximo: " + inicial.nomeColorido() + "§e.");
            return true;
        }
        if (!plugin.getEconomy().has(jogador, proximo.preco())) {
            double falta = proximo.preco() - plugin.getEconomy().getBalance(jogador);
            jogador.sendMessage("§cVocê precisa de §f" + Formato.valor(proximo.preco()) + " §cpra subir pra "
                    + proximo.nomeColorido() + "§c. Faltam §f" + Formato.valor(falta) + "§c.");
            return true;
        }

        Rank atual = inicial;
        double gasto = 0;
        do {
            if (!subir(jogador, atual, proximo)) break;
            gasto += proximo.preco();
            atual = proximo;
            proximo = plugin.getRankManager().getProximo(atual);
        } while (max && proximo != null && plugin.getEconomy().has(jogador, proximo.preco()));
        if (atual == inicial) {
            jogador.sendMessage("§cNão foi possível cobrar o rankup. Tente de novo.");
            return true;
        }

        if (plugin.getConfig().getBoolean("anunciar-rankup", true)) {
            plugin.getServer().broadcastMessage("§6§l⬆ §f" + jogador.getName() + " §esubiu pro rank " + atual.nomeColorido() + "§e!");
        } else {
            jogador.sendMessage("§aVocê subiu pro rank " + atual.nomeColorido() + "§a!");
        }
        if (atual != plugin.getRankManager().getProximo(inicial)) {
            jogador.sendMessage("§7Total gasto: §f" + Formato.valor(gasto));
        }

        List<String> liberadas = minasLiberadas(inicial, atual);
        if (!liberadas.isEmpty()) {
            jogador.sendMessage("§aMinas liberadas: §f" + String.join(", ", liberadas) + " §7(use /mina <id>)");
        }
        String bonus = Formato.bonus(atual.multiplicadorVenda());
        if (!bonus.isEmpty()) {
            jogador.sendMessage("§aBônus de venda na loja agora: §f" + bonus);
        }
        if (proximo != null) {
            jogador.sendMessage("§7Próximo rank: " + proximo.nomeColorido() + " §7por §f" + Formato.valor(proximo.preco()));
        }
        return true;
    }

    /** Cobra e aplica um rank. Separado pro painel da economia mostrar como "Rankup" (RankupCommand#subir). */
    private boolean subir(Player jogador, Rank atual, Rank proximo) {
        Economy economia = plugin.getEconomy();
        if (!economia.withdrawPlayer(jogador, proximo.preco()).transactionSuccess()) return false;
        plugin.aplicarRank(jogador, atual, proximo);
        plugin.getRegistroRankups().registrar(jogador, atual, proximo);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), proximo.preco(), false,
                "Rankup: " + proximo.nome());
        return true;
    }

    /** Minas com rank mínimo entre (de, ate]. */
    private List<String> minasLiberadas(Rank de, Rank ate) {
        int inicio = plugin.getRankManager().indice(de);
        int fim = plugin.getRankManager().indice(ate);
        List<String> ids = new ArrayList<>();
        for (Mina mina : plugin.getMinaManager().getMinas()) {
            Rank minimo = plugin.getRankManager().getRank(mina.getRankMinimo());
            if (minimo == null) continue;
            int i = plugin.getRankManager().indice(minimo);
            if (i > inicio && i <= fim) ids.add(mina.getId());
        }
        return ids;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("max") : List.of();
    }
}
