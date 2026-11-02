package com.example.eventos.quiz;

import com.example.eventos.EventosPlugin;
import com.example.eventos.util.Formato;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /quiz &lt;resposta&gt; (com pergunta aberta), /quiz (como funciona e o que está acontecendo) e /quiz top.
 * Staff (eventos.admin): /quiz abrir, /quiz cancelar e /quiz resposta (a resposta da pergunta aberta).
 */
public class QuizCommand implements TabExecutor {

    private final EventosPlugin plugin;

    public QuizCommand(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Quiz quiz = plugin.getQuiz();
        boolean admin = sender.hasPermission("eventos.admin");
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        if (admin && List.of("abrir", "cancelar", "resposta").contains(sub)) {
            switch (sub) {
                case "abrir" -> {
                    if (quiz.isAtivo()) sender.sendMessage("§cO Quiz já está rolando.");
                    else quiz.abrir();
                }
                case "cancelar" -> {
                    if (!quiz.isAtivo()) sender.sendMessage("§cNão tem Quiz rolando.");
                    else quiz.cancelar("pela staff");
                }
                default -> sender.sendMessage(quiz.respostaAtual() == null ? "§7Nenhuma pergunta aberta."
                        : "§7Resposta: §f" + quiz.respostaAtual());
            }
            return true;
        }

        // com pergunta aberta, tudo é resposta
        if (quiz.perguntaAberta() && args.length > 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cSó jogadores respondem.");
                return true;
            }
            String erro = quiz.responder(p, String.join(" ", args));
            if (erro != null) p.sendMessage(erro);
            return true;
        }

        if (sub.equals("top")) {
            sender.sendMessage("§e§l--- Quiz: vitórias ---");
            int i = 1;
            for (Map.Entry<UUID, Integer> e : quiz.ranking(10)) sender.sendMessage("§f" + i++ + "º §e" + quiz.nomeDe(e.getKey()) + " §7- §f" + e.getValue());
            if (i == 1) sender.sendMessage("§7Ninguém venceu ainda.");
            return true;
        }
        if (args.length > 0 && !quiz.perguntaAberta()) {
            sender.sendMessage("§7Não tem pergunta aberta agora.");
        }
        sender.sendMessage("§e§l✎ Quiz ✎");
        sender.sendMessage("§7" + quiz.total() + " perguntas no chat (sobre o servidor, o Minecraft, contas e palavras embaralhadas). "
                + "Responda com §f/quiz <resposta>§7: quem acertar primeiro marca 1 ponto. Quem fizer mais pontos leva §f"
                + Formato.valor(quiz.premio()) + "§7 (dividido no empate).");
        String agenda = String.join(" e ", plugin.getConfig().getStringList("quiz.agenda"));
        sender.sendMessage("§7Todo dia" + (agenda.isEmpty() ? " (sem agenda)" : " às §f" + agenda) + "§7.");
        sender.sendMessage(quiz.isAtivo() ? "§eAgora: §frolando, pergunta " + quiz.getNumero() + "/" + quiz.total() : "§7Nenhum rolando agora.");
        if (admin) sender.sendMessage("§7Staff: /quiz abrir, cancelar, resposta");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || plugin.getQuiz().perguntaAberta()) return List.of();
        List<String> opcoes = sender.hasPermission("eventos.admin") ? List.of("top", "abrir", "cancelar", "resposta") : List.of("top");
        return opcoes.stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
