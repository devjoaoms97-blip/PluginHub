package com.example.gladiador.command;

import com.example.gladiador.GladiadorPlugin;
import com.example.gladiador.manager.AgendaManager;
import com.example.gladiador.manager.EventoManager;
import com.example.gladiador.model.Arena;
import com.example.gladiador.model.Evento;
import com.example.gladiador.util.Formato;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /gladiador entrar|sair|info|top. Staff ({@code gladiador.admin}): /gladiador abrir
 * &lt;individual|cla&gt; [inscrição], iniciar, cancelar, arena pos1|pos2|spawn add|spawn limpar|info, reload.
 */
public class GladiadorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("entrar", "sair", "info", "top");
    private static final List<String> SUBS_ADMIN = List.of("abrir", "iniciar", "cancelar", "arena", "reload");

    private final GladiadorPlugin plugin;

    public GladiadorCommand(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    private EventoManager eventos() {
        return plugin.getEventoManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (SUBS_ADMIN.contains(sub)) {
            if (!sender.hasPermission(GladiadorPlugin.PERMISSAO_ADMIN)) {
                sender.sendMessage("§cVocê não tem permissão.");
                return true;
            }
            admin(sender, sub, args);
            return true;
        }
        switch (sub) {
            case "entrar" -> entrar(sender);
            case "sair" -> sair(sender);
            case "top" -> top(sender);
            default -> info(sender);
        }
        return true;
    }

    private void info(CommandSender sender) {
        Evento e = eventos().getEvento();
        sender.sendMessage("§6§l--- Gladiador ---");
        if (e == null) {
            sender.sendMessage("§7Nenhum evento aberto agora.");
        } else {
            sender.sendMessage("§eModo: §f" + (e.getModo() == Evento.Modo.CLA ? "por clã" : "individual") + " §7· §eInscrição: §f"
                    + (e.getInscricao() > 0 ? Formato.valor(e.getInscricao()) : "grátis") + " §7· §ePote: §f" + Formato.valor(eventos().pote()));
            sender.sendMessage(switch (e.getFase()) {
                case INSCRICAO -> "§eInscrições abertas por mais §f" + Formato.tempo(e.getFimInscricao() - System.currentTimeMillis())
                        + "§e (§f" + e.getParticipantes().size() + " §einscrito(s)). Entre com §f/gladiador entrar§e.";
                case PREPARACAO, LUTA -> "§cLuta em andamento: §f"
                        + e.getParticipantes().values().stream().filter(Evento.Participante::isVivo).count() + " §cde pé.";
                case FIM -> "§7Encerrando.";
            });
        }
        sender.sendMessage("§7Cada um luta com o próprio equipamento, e §cquem morre perde os itens§7. Ranking: §f/gladiador top");
    }

    private void entrar(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores entram no Gladiador.");
            return;
        }
        Evento e = eventos().getEvento();
        if (e == null || e.getFase() != Evento.Fase.INSCRICAO) {
            jogador.sendMessage("§cNão há inscrições abertas agora.");
            return;
        }
        if (e.getParticipantes().containsKey(jogador.getUniqueId())) {
            jogador.sendMessage("§cVocê já está inscrito. Pra sair: §f/gladiador sair");
            return;
        }
        if (plugin.naArena(jogador)) {
            jogador.sendMessage("§cTermine o que está fazendo no mundo das arenas (um duelo?) antes de entrar.");
            return;
        }
        String cla = null;
        if (e.getModo() == Evento.Modo.CLA) {
            cla = plugin.getClasIntegration().tagDoCla(jogador.getUniqueId());
            if (cla == null) {
                jogador.sendMessage("§cEsse Gladiador é por clã, e você não tem clã.");
                return;
            }
        }
        if (e.getInscricao() > 0 && (plugin.getEconomy() == null || !plugin.getEconomy().has(jogador, e.getInscricao()))) {
            jogador.sendMessage("§cA inscrição é §f" + Formato.valor(e.getInscricao()) + "§c, e você não tem esse valor.");
            return;
        }
        eventos().inscrever(jogador, cla);
        jogador.sendMessage("§aInscrito! §7Quando as inscrições fecharem, você vai pra arena com o que estiver carregando."
                + " §cQuem morre perde os itens.");
    }

    private void sair(CommandSender sender) {
        if (!(sender instanceof Player jogador)) return;
        Evento e = eventos().getEvento();
        if (e == null || !e.getParticipantes().containsKey(jogador.getUniqueId())) {
            jogador.sendMessage("§cVocê não está inscrito.");
            return;
        }
        if (e.getFase() != Evento.Fase.INSCRICAO) {
            jogador.sendMessage("§cA luta já começou: não dá mais pra sair.");
            return;
        }
        eventos().desinscrever(jogador);
        jogador.sendMessage("§7Inscrição cancelada" + (e.getInscricao() > 0 ? " e devolvida" : "") + ".");
    }

    private void top(CommandSender sender) {
        sender.sendMessage("§6§l--- Top Gladiador ---");
        List<Map.Entry<UUID, Integer>> jogadores = eventos().ranking(10);
        if (jogadores.isEmpty()) sender.sendMessage("§7Ninguém venceu ainda.");
        for (int i = 0; i < jogadores.size(); i++) {
            sender.sendMessage("§e" + (i + 1) + ". §f" + eventos().nomeDe(jogadores.get(i).getKey()) + " §7- §a" + jogadores.get(i).getValue() + " vitória(s)");
        }
        sender.sendMessage("§6Destaques do último Gladiador:");
        plugin.getDestaques().resumo().forEach(l -> sender.sendMessage("  " + l));
        List<Map.Entry<String, Integer>> clas = eventos().rankingClas(5);
        if (!clas.isEmpty()) {
            sender.sendMessage("§6Clãs:");
            for (int i = 0; i < clas.size(); i++) {
                sender.sendMessage("§e" + (i + 1) + ". §f[" + clas.get(i).getKey() + "] §7- §a" + clas.get(i).getValue() + " vitória(s)");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------
    private void admin(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "abrir" -> {
                Evento.Modo modo = args.length >= 2 ? AgendaManager.modo(args[1]) : null;
                if (modo == null) {
                    sender.sendMessage("§cUso: §f/gladiador abrir <individual|cla> [inscrição]");
                    return;
                }
                if (eventos().getEvento() != null) {
                    sender.sendMessage("§cJá há um Gladiador aberto. §f/gladiador cancelar §cencerra.");
                    return;
                }
                if (!eventos().getArena().pronta()) {
                    sender.sendMessage("§cA arena não está pronta: marque §f/gladiador arena pos1§c, §fpos2 §ce pelo menos um §fspawn add§c.");
                    return;
                }
                if (modo == Evento.Modo.CLA && !plugin.getClasIntegration().disponivel()) {
                    sender.sendMessage("§cO modo por clã precisa do ClasPlugin.");
                    return;
                }
                if (plugin.getEconomy() == null) {
                    sender.sendMessage("§cA economia está indisponível.");
                    return;
                }
                double inscricao = args.length >= 3 ? Formato.lerPreco(args[2]) : plugin.inscricaoPadrao();
                if (inscricao < 0) {
                    sender.sendMessage("§cValor de inscrição inválido: §f" + args[2]);
                    return;
                }
                eventos().abrir(modo, inscricao);
            }
            case "iniciar" -> {
                Evento e = eventos().getEvento();
                if (e == null || e.getFase() != Evento.Fase.INSCRICAO) {
                    sender.sendMessage("§cNão há inscrições abertas.");
                    return;
                }
                eventos().iniciar();
            }
            case "cancelar" -> {
                if (eventos().getEvento() == null) {
                    sender.sendMessage("§cNão há Gladiador aberto.");
                    return;
                }
                eventos().cancelar("pela staff");
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getAgendaManager().carregar();
                sender.sendMessage("§aConfig e agenda recarregados.");
            }
            case "arena" -> arena(sender, args);
            default -> {
            }
        }
    }

    private void arena(CommandSender sender, String[] args) {
        Arena arena = eventos().getArena();
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "info";
        if (sub.equals("info")) {
            sender.sendMessage("§eArena do Gladiador: " + (arena.pronta() ? "§apronta" : "§cincompleta") + " §7(pos1 "
                    + (arena.getPos1() != null ? "✔" : "✘") + ", pos2 " + (arena.getPos2() != null ? "✔" : "✘") + ", "
                    + arena.getSpawns().size() + " ponto(s) de entrada)");
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cUse no jogo, no lugar a marcar.");
            return;
        }
        if (eventos().getEvento() != null && eventos().getEvento().emCombate()) {
            sender.sendMessage("§cNão mexa na arena durante a luta.");
            return;
        }
        switch (sub) {
            case "pos1" -> arena.setPos1(p.getLocation());
            case "pos2" -> arena.setPos2(p.getLocation());
            case "spawn" -> {
                String acao = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (acao.equals("add")) {
                    arena.getSpawns().add(p.getLocation());
                } else if (acao.equals("limpar")) {
                    arena.getSpawns().clear();
                } else {
                    sender.sendMessage("§cUso: §f/gladiador arena spawn <add|limpar>");
                    return;
                }
            }
            default -> {
                sender.sendMessage("§cUso: §f/gladiador arena <pos1|pos2|spawn add|spawn limpar|info>");
                return;
            }
        }
        eventos().salvarArena();
        sender.sendMessage("§aArena atualizada. " + (arena.pronta() ? "§aPronta." : "§7Ainda falta: "
                + (arena.getPos1() == null ? "pos1 " : "") + (arena.getPos2() == null ? "pos2 " : "")
                + (arena.getSpawns().isEmpty() ? "ponto de entrada (spawn add)" : "")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        boolean admin = sender.hasPermission(GladiadorPlugin.PERMISSAO_ADMIN);
        if (args.length == 1) {
            opcoes.addAll(SUBS);
            if (admin) opcoes.addAll(SUBS_ADMIN);
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("abrir")) {
            opcoes.addAll(List.of("individual", "cla"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            opcoes.addAll(List.of("pos1", "pos2", "spawn", "info"));
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("spawn")) {
            opcoes.addAll(List.of("add", "limpar"));
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefixo)).toList();
    }
}
