package com.example.missoes.command;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.Missao;
import com.example.missoes.model.Periodo;
import com.example.missoes.model.ProgressoJogador;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /missoes — abre o menu com as missões de hoje e da semana. /missoes lista mostra o mesmo no
 * chat. Staff: /missoes admin &lt;resortear &lt;jogador&gt; [semanal]|reload&gt;.
 */
public class MissoesCommand implements CommandExecutor, TabCompleter {

    private static final int TAMANHO_BARRA = 20;

    private final MissoesPlugin plugin;

    public MissoesCommand(MissoesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores têm missões. Staff: §f/missoes admin <resortear|reload>");
            return true;
        }
        boolean texto = args.length >= 1 && (args[0].equalsIgnoreCase("lista") || args[0].equalsIgnoreCase("chat"));
        if (!texto && plugin.getConfig().getBoolean("gui.ativado", true)) {
            plugin.getMissoesGUI().abrir(jogador);
            return true;
        }
        listar(jogador, Periodo.DIARIA);
        if (plugin.getMissaoManager().semanaisAtivas() && !plugin.getMissaoManager().getPool(Periodo.SEMANAL).isEmpty()) {
            listar(jogador, Periodo.SEMANAL);
        }
        return true;
    }

    private void listar(Player jogador, Periodo periodo) {
        boolean diaria = periodo == Periodo.DIARIA;
        ProgressoJogador atual = plugin.getMissaoManager().getAtual(jogador.getUniqueId(), periodo);
        if (atual.getProgresso().isEmpty()) {
            jogador.sendMessage("§7Nenhuma missão " + (diaria ? "diária" : "semanal") + " disponível.");
            return;
        }

        jogador.sendMessage(diaria ? "§e§l--- Missões de hoje ---" : "§d§l--- Missões da semana ---");
        for (Map.Entry<String, Integer> entrada : atual.getProgresso().entrySet()) {
            Missao missao = plugin.getMissaoManager().getMissao(periodo, entrada.getKey());
            if (missao == null) continue; // removida do config depois de sorteada
            int feito = entrada.getValue();
            boolean concluida = feito >= missao.quantidade();
            jogador.sendMessage((concluida ? "§a✔ " : "§7• ") + "§f" + missao.descricao());
            jogador.sendMessage("   " + barra(feito, missao.quantidade()) + " §f" + feito + "/" + missao.quantidade()
                    + " §7| " + plugin.getMissoesGUI().recompensa(missao.coins(), missao.skill(), missao.xp()));
        }

        String chave = plugin.getMissaoManager().chaveBonus(periodo);
        double bonusCoins = plugin.getConfig().getDouble(chave + ".coins", 0);
        double bonusXp = plugin.getConfig().getDouble(chave + ".xp", 0);
        if (bonusCoins > 0 || bonusXp > 0) {
            jogador.sendMessage((atual.isBonusPago() ? "§a✔ " : "§6★ ") + "§6Bônus por completar todas: "
                    + plugin.getMissoesGUI().recompensa(bonusCoins, plugin.getConfig().getString(chave + ".skill"), bonusXp));
        }

        Duration falta = Duration.between(LocalDateTime.now(), periodo.proximoInicio(LocalDate.now()).atStartOfDay());
        jogador.sendMessage("§8Novas missões em " + (falta.toDays() > 0 ? falta.toDays() + "d " + falta.toHoursPart() + "h."
                : falta.toHours() + "h " + falta.toMinutesPart() + "min."));
    }

    private String barra(int atual, int total) {
        int cheios = (int) Math.round((double) atual / total * TAMANHO_BARRA);
        return "§a" + "|".repeat(cheios) + "§8" + "|".repeat(TAMANHO_BARRA - cheios);
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("missoes.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getMissaoManager().carregarPool();
            sender.sendMessage("§aConfiguração recarregada: §f" + plugin.getMissaoManager().getPool(Periodo.DIARIA).size()
                    + " §amissão(ões) diária(s) e §f" + plugin.getMissaoManager().getPool(Periodo.SEMANAL).size()
                    + " §asemanal(is) no pool. §7(Quem já sorteou mantém as missões até o período virar.)");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("resortear")) {
            OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[2]);
            if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
                sender.sendMessage("§cJogador \"" + args[2] + "\" não encontrado.");
                return;
            }
            boolean semanal = args.length >= 4 && args[3].equalsIgnoreCase("semanal");
            plugin.getMissaoManager().resortear(alvo.getUniqueId(), semanal ? Periodo.SEMANAL : Periodo.DIARIA);
            sender.sendMessage("§aMissões " + (semanal ? "da semana" : "de hoje") + " de §f" + alvo.getName()
                    + " §asorteadas de novo (progresso zerado).");
            return;
        }
        sender.sendMessage("§cUso: §f/missoes admin resortear <jogador> [semanal] §7| §f/missoes admin reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.add("lista");
            if (sender.hasPermission("missoes.admin")) opcoes.add("admin");
        } else if (!sender.hasPermission("missoes.admin")) {
            return opcoes;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            opcoes.addAll(List.of("resortear", "reload"));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("resortear")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 4 && args[1].equalsIgnoreCase("resortear")) {
            opcoes.add("semanal");
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
