package com.example.rankup.command;

import com.example.rankup.RankupPlugin;
import com.example.rankup.manager.PrestigioManager;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /prestigio: no último rank, volta pro primeiro e ganha um nível de prestígio (bônus de venda
 * permanente). O dinheiro fica com o jogador. Pede /prestigio confirmar, porque não tem volta.
 */
public class PrestigioCommand implements CommandExecutor, TabCompleter {

    private final RankupPlugin plugin;

    public PrestigioCommand(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores têm prestígio.");
            return true;
        }
        PrestigioManager prestigio = plugin.getPrestigioManager();
        int nivel = prestigio.nivel(jogador.getUniqueId());
        Rank atual = plugin.getRankManager().getRankDe(jogador.getUniqueId());
        boolean noTopo = plugin.getRankManager().getProximo(atual) == null;
        double porNivel = plugin.getConfig().getDouble("prestigio.bonus-venda-percentual", 10);

        if (!plugin.getConfig().getBoolean("prestigio.ativado", true)) {
            jogador.sendMessage("§cO prestígio está desligado neste servidor.");
            return true;
        }
        if (nivel >= prestigio.maximo()) {
            jogador.sendMessage("§eVocê já está no prestígio máximo (§f" + nivel + "§e).");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirmar")) {
            jogador.sendMessage("§6§l--- Prestígio ---");
            jogador.sendMessage("§7Seu prestígio: §f" + nivel + " §7(bônus de venda: §a+" + Formato.numero(porNivel * nivel) + "%§7)");
            jogador.sendMessage("§7No rank " + plugin.getRankManager().getRanks().getLast().nomeColorido()
                    + "§7, você pode voltar pro " + plugin.getRankManager().getPrimeiro().nomeColorido()
                    + " §7e ganhar §a+" + Formato.numero(porNivel) + "% §7de venda na loja pra sempre. O dinheiro fica com você.");
            jogador.sendMessage(noTopo ? "§eConfirme com §f/prestigio confirmar§e. Não tem volta."
                    : "§cVocê ainda não chegou no último rank.");
            return true;
        }
        if (!noTopo) {
            jogador.sendMessage("§cVocê precisa estar no rank " + plugin.getRankManager().getRanks().getLast().nomeColorido() + "§c.");
            return true;
        }
        prestigio.subir(jogador.getUniqueId());
        Rank primeiro = plugin.getRankManager().getPrimeiro();
        plugin.aplicarRank(jogador, atual, primeiro);
        plugin.getServer().broadcastMessage("§d§l✦ §f" + jogador.getName() + " §dalcançou o §lprestígio " + (nivel + 1)
                + "§d e voltou pro " + primeiro.nomeColorido() + "§d!");
        jogador.sendMessage("§aBônus de venda do prestígio agora: §f+" + Formato.numero(porNivel * (nivel + 1)) + "%");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("confirmar") : List.of();
    }
}
