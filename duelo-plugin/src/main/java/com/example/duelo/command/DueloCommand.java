package com.example.duelo.command;

import com.example.duelo.DueloPlugin;
import com.example.duelo.manager.DueloManager;
import com.example.duelo.model.Arena;
import com.example.duelo.model.Convite;
import com.example.duelo.model.Duelo;
import com.example.duelo.util.Formato;
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
import java.util.Map;
import java.util.UUID;

/**
 * /duelo &lt;jogador&gt; [valor], /duelo aceitar|recusar|stats|top. Staff ({@code duelo.admin}):
 * /duelo arena criar|pos1|pos2|remover|lista e /duelo cancelar &lt;jogador&gt;.
 */
public class DueloCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("aceitar", "recusar", "stats", "top", "assistir", "sair");
    private static final List<String> SUBS_ARENA = List.of("criar", "pos1", "pos2", "remover", "lista");

    private final DueloPlugin plugin;

    public DueloCommand(DueloPlugin plugin) {
        this.plugin = plugin;
    }

    private DueloManager duelos() {
        return plugin.getDueloManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "ajuda" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("arena") || sub.equals("cancelar")) {
            if (!sender.hasPermission(DueloPlugin.PERMISSAO_ADMIN)) {
                sender.sendMessage("§cVocê não tem permissão.");
            } else if (sub.equals("arena")) {
                arena(sender, args);
            } else {
                cancelar(sender, args);
            }
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores duelam. Staff: §f/duelo arena ... §cou §f/duelo cancelar <jogador>");
            return true;
        }
        switch (sub) {
            case "ajuda" -> ajuda(jogador);
            case "aceitar" -> aceitar(jogador);
            case "recusar" -> recusar(jogador);
            case "stats" -> stats(jogador, args);
            case "top" -> top(jogador);
            case "assistir" -> assistir(jogador, args);
            case "sair" -> sair(jogador);
            default -> desafiar(jogador, args);
        }
        return true;
    }

    private void ajuda(Player jogador) {
        jogador.sendMessage("§6§l--- X1 ---");
        jogador.sendMessage("§f/duelo <jogador> [valor] §7- desafia, apostando o valor (sem valor: amistoso)");
        jogador.sendMessage("§f/duelo aceitar §7| §f/duelo recusar §7- responde ao desafio");
        jogador.sendMessage("§f/duelo stats [jogador] §7| §f/duelo top §7- vitórias e derrotas");
        jogador.sendMessage("§f/duelo assistir <jogador> §7| §f/duelo sair §7- assiste um duelo em andamento");
        jogador.sendMessage("§7Cada um luta com o próprio equipamento, e §cquem morre perde os itens§7. O vencedor leva o pote"
                + " menos " + Formato.numero(plugin.getConfig().getDouble("duelo.taxa-percentual", 5)) + "%.");
    }

    // ---------------------------------------------------------------------
    // Desafio
    // ---------------------------------------------------------------------
    private void desafiar(Player jogador, String[] args) {
        Player alvo = Bukkit.getPlayerExact(args[0]);
        if (alvo == null) {
            jogador.sendMessage("§cJogador \"" + args[0] + "\" não está online. Uso: §f/duelo <jogador> [valor]");
            return;
        }
        if (alvo.equals(jogador)) {
            jogador.sendMessage("§cVocê não pode se desafiar.");
            return;
        }
        double valor = 0;
        if (args.length >= 2) {
            valor = Formato.lerPreco(args[1]);
            if (valor < 0) {
                jogador.sendMessage("§cValor inválido: §f" + args[1]);
                return;
            }
        }
        double minimo = Math.max(0, plugin.getConfig().getDouble("aposta.minima", 0));
        double maximo = plugin.getConfig().getDouble("aposta.maxima", 0);
        if (valor < minimo || (maximo > 0 && valor > maximo)) {
            jogador.sendMessage("§cA aposta precisa ser de §f" + Formato.valor(minimo) + "§c"
                    + (maximo > 0 ? " a §f" + Formato.valor(maximo) : " pra cima") + "§c.");
            return;
        }
        String ocupado = ocupado(jogador, alvo);
        if (ocupado != null) {
            jogador.sendMessage(ocupado);
            return;
        }
        if (valor > 0 && (plugin.getEconomy() == null || !plugin.getEconomy().has(jogador, valor))) {
            jogador.sendMessage("§cVocê não tem " + Formato.valor(valor) + " pra apostar.");
            return;
        }
        duelos().convidar(jogador, alvo, valor);
        int segundos = plugin.getConfig().getInt("convite.expira-segundos", 60);
        String aposta = valor > 0 ? "§e apostando §f" + Formato.valor(valor) : "§e (amistoso, sem aposta)";
        jogador.sendMessage("§eDesafio enviado pra §f" + alvo.getName() + aposta + "§e. Vale por §f" + segundos + "s§e.");
        alvo.sendMessage("§6§l[X1] §f" + jogador.getName() + " §edesafiou você" + aposta + "§e. §cQuem morre perde os itens.");
        alvo.sendMessage("§eResponda com §a/duelo aceitar §eou §c/duelo recusar §e(§f" + segundos + "s§e).");
    }

    /** Motivo pra não poder duelar agora (já em duelo, na arena, convite pendente), ou null. */
    private String ocupado(Player jogador, Player alvo) {
        if (duelos().emDuelo(jogador.getUniqueId())) return "§cVocê já está num duelo.";
        if (duelos().emDuelo(alvo.getUniqueId())) return "§c" + alvo.getName() + " já está num duelo.";
        if (plugin.naArena(jogador) || plugin.naArena(alvo)) return "§cNinguém pode estar no mundo das arenas (outro evento) pra começar um duelo.";
        if (duelos().getConvite(alvo.getUniqueId()) != null) return "§c" + alvo.getName() + " já tem um desafio pendente.";
        if (duelos().desafiouAlguem(jogador.getUniqueId())) return "§cVocê já tem um desafio pendente. Espere a resposta.";
        return null;
    }

    private void aceitar(Player jogador) {
        Convite convite = duelos().getConvite(jogador.getUniqueId());
        if (convite == null) {
            jogador.sendMessage("§cVocê não tem desafio pendente.");
            return;
        }
        Player desafiante = Bukkit.getPlayer(convite.desafiante());
        if (desafiante == null) {
            duelos().recusar(jogador.getUniqueId());
            jogador.sendMessage("§c" + convite.nomeDesafiante() + " saiu do servidor.");
            return;
        }
        if (duelos().emDuelo(desafiante.getUniqueId()) || plugin.naArena(desafiante) || plugin.naArena(jogador)) {
            jogador.sendMessage("§cAlguém de vocês está num evento agora. Tente de novo depois.");
            return;
        }
        double aposta = convite.aposta();
        if (aposta > 0) {
            if (plugin.getEconomy() == null) {
                jogador.sendMessage("§cA economia está indisponível no momento.");
                return;
            }
            if (!plugin.getEconomy().has(jogador, aposta)) {
                jogador.sendMessage("§cVocê não tem " + Formato.valor(aposta) + " pra cobrir a aposta.");
                return;
            }
            if (!plugin.getEconomy().has(desafiante, aposta)) {
                duelos().recusar(jogador.getUniqueId());
                jogador.sendMessage("§c" + desafiante.getName() + " não tem mais o valor da aposta. Desafio cancelado.");
                desafiante.sendMessage("§cSeu desafio foi cancelado: você não tem mais " + Formato.valor(aposta) + ".");
                return;
            }
        }
        Arena arena = duelos().arenaLivre();
        if (arena == null) {
            jogador.sendMessage("§cTodas as arenas estão ocupadas. Tente de novo em instantes (o desafio continua valendo).");
            return;
        }
        duelos().iniciar(convite, desafiante, jogador, arena);
    }

    private void recusar(Player jogador) {
        Convite convite = duelos().getConvite(jogador.getUniqueId());
        if (convite == null) {
            jogador.sendMessage("§cVocê não tem desafio pendente.");
            return;
        }
        duelos().recusar(jogador.getUniqueId());
        jogador.sendMessage("§7Desafio recusado.");
        Player desafiante = Bukkit.getPlayer(convite.desafiante());
        if (desafiante != null) desafiante.sendMessage("§c" + jogador.getName() + " recusou o seu desafio.");
    }

    // ---------------------------------------------------------------------
    // Espectador
    // ---------------------------------------------------------------------
    private void assistir(Player jogador, String[] args) {
        Player alvo = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        Duelo duelo = alvo == null ? null : duelos().getDuelo(alvo.getUniqueId());
        if (duelo == null || duelo.getFase() == Duelo.Fase.FIM) {
            jogador.sendMessage("§cUso: §f/duelo assistir <jogador que está duelando agora>");
            return;
        }
        if (duelo.participa(jogador.getUniqueId()) || duelos().emDuelo(jogador.getUniqueId())) {
            jogador.sendMessage("§cVocê está num duelo.");
            return;
        }
        if (duelos().assistindo(jogador.getUniqueId())) {
            jogador.sendMessage("§cVocê já está assistindo um duelo. §f/duelo sair §cprimeiro.");
            return;
        }
        if (plugin.naArena(jogador)) {
            jogador.sendMessage("§cVocê está num evento no mundo das arenas.");
            return;
        }
        duelos().assistir(jogador, duelo);
    }

    private void sair(Player jogador) {
        if (!duelos().assistindo(jogador.getUniqueId())) {
            jogador.sendMessage("§cVocê não está assistindo um duelo.");
            return;
        }
        duelos().pararDeAssistir(jogador);
        jogador.sendMessage("§7Você parou de assistir.");
    }

    // ---------------------------------------------------------------------
    // Ranking
    // ---------------------------------------------------------------------
    private void stats(Player jogador, String[] args) {
        OfflinePlayer alvo = args.length >= 2 ? Bukkit.getOfflinePlayer(args[1]) : jogador;
        int[] e = duelos().getEstatisticas(alvo.getUniqueId());
        jogador.sendMessage("§6X1 de §f" + alvo.getName() + "§6: §a" + e[0] + " vitória(s) §7· §c" + e[1] + " derrota(s)");
    }

    private void top(Player jogador) {
        List<Map.Entry<UUID, int[]>> ranking = duelos().ranking(10);
        jogador.sendMessage("§6§l--- Top X1 ---");
        if (ranking.isEmpty()) jogador.sendMessage("§7Ninguém duelou ainda.");
        for (int i = 0; i < ranking.size(); i++) {
            Map.Entry<UUID, int[]> e = ranking.get(i);
            jogador.sendMessage("§e" + (i + 1) + ". §f" + duelos().nomeDe(e.getKey()) + " §7- §a" + e.getValue()[0] + "V §c" + e.getValue()[1] + "D");
        }
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------
    private void arena(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("lista")) {
            if (duelos().getArenas().isEmpty()) sender.sendMessage("§7Nenhuma arena. Crie com §f/duelo arena criar <nome>§7.");
            duelos().getArenas().values().forEach(a -> sender.sendMessage("§e" + a.getNome() + " §7- "
                    + (!a.pronta() ? "§cfalta " + (a.getPos1() == null ? "pos1 " : "") + (a.getPos2() == null ? "pos2" : "")
                    : a.isOcupada() ? "§6em uso" : "§alivre")));
            return;
        }
        if (!SUBS_ARENA.contains(sub) || args.length < 3) {
            sender.sendMessage("§cUso: §f/duelo arena criar|pos1|pos2|remover <nome> §7| §f/duelo arena lista");
            return;
        }
        String nome = args[2];
        Arena arena = duelos().getArena(nome);
        switch (sub) {
            case "criar" -> {
                if (arena != null) {
                    sender.sendMessage("§cA arena §f" + nome + " §cjá existe.");
                    return;
                }
                duelos().criarArena(nome);
                sender.sendMessage("§aArena §f" + nome + " §acriada. Marque os dois pontos de partida com §f/duelo arena pos1 " + nome
                        + " §ae §f/duelo arena pos2 " + nome + "§a (onde você estiver, olhando pra onde o lutador deve olhar).");
            }
            case "pos1", "pos2" -> {
                if (arena == null) {
                    sender.sendMessage("§cA arena §f" + nome + " §cnão existe.");
                    return;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cFique no lugar do ponto de partida e use o comando no jogo.");
                    return;
                }
                if (sub.equals("pos1")) arena.setPos1(p.getLocation()); else arena.setPos2(p.getLocation());
                duelos().salvarArenas();
                sender.sendMessage("§a" + sub + " da arena §f" + nome + " §amarcado." + (arena.pronta() ? " §aA arena está pronta." : ""));
            }
            case "remover" -> {
                if (arena == null || arena.isOcupada()) {
                    sender.sendMessage(arena == null ? "§cA arena §f" + nome + " §cnão existe." : "§cA arena está em uso agora.");
                    return;
                }
                duelos().removerArena(arena);
                sender.sendMessage("§aArena §f" + nome + " §aremovida.");
            }
            default -> {
            }
        }
    }

    private void cancelar(CommandSender sender, String[] args) {
        Player alvo = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        Duelo duelo = alvo == null ? null : duelos().getDuelo(alvo.getUniqueId());
        if (duelo == null) {
            sender.sendMessage("§cUso: §f/duelo cancelar <jogador em duelo>");
            return;
        }
        duelos().empatar(duelo, "cancelado pela staff");
        sender.sendMessage("§aDuelo cancelado, apostas devolvidas.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        boolean admin = sender.hasPermission(DueloPlugin.PERMISSAO_ADMIN);
        if (args.length == 1) {
            opcoes.addAll(SUBS);
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (!p.equals(sender)) opcoes.add(p.getName());
            });
            if (admin) opcoes.addAll(List.of("arena", "cancelar"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("arena") && admin) {
            opcoes.addAll(SUBS_ARENA);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("arena") && admin) {
            opcoes.addAll(duelos().getArenas().keySet());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("cancelar") || args[0].equalsIgnoreCase("assistir"))) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefixo)).toList();
    }
}
