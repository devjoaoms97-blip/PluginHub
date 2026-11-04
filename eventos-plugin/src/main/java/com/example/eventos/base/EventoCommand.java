package com.example.eventos.base;

import com.example.eventos.EventosPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /<evento> entrar|sair|info|assistir|top e, pra staff, abrir|iniciar|cancelar|construir|reload. */
public class EventoCommand implements TabExecutor {

    private final EventosPlugin plugin;
    private final Evento ev;

    public EventoCommand(EventosPlugin plugin, Evento evento) {
        this.plugin = plugin;
        this.ev = evento;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("eventos.admin");
        String pre = ev.prefixo() + " ";
        switch (sub) {
            case "entrar" -> {
                if (!(sender instanceof Player p)) return semConsole(sender);
                String erro = ev.entrar(p);
                p.sendMessage(erro == null ? pre + ev.cor() + "Inscrito! Espere aqui no saguão dos eventos: sair dele cancela a inscrição." : "§c" + erro);
            }
            case "sair" -> {
                if (!(sender instanceof Player p)) return semConsole(sender);
                p.sendMessage(ev.sair(p) ? pre + "§7Inscrição cancelada." : "§cVocê não está inscrito (ou o evento já começou).");
            }
            case "assistir" -> {
                if (!(sender instanceof Player p)) return semConsole(sender);
                if (plugin.eventoLutando(p.getUniqueId()) != null) {
                    p.sendMessage("§cVocê está lutando!");
                } else if (ev.assistir(p)) {
                    p.sendMessage(pre + "§7Na arquibancada. Pra sair: §f/spawn§7. No fim, você volta pra onde estava.");
                } else {
                    p.sendMessage("§c" + ev.nome() + " não está rolando agora: dá pra assistir só durante o evento. Agenda: §f/eventos");
                }
            }
            case "top" -> {
                sender.sendMessage(ev.cor() + "§l--- " + ev.nome() + ": vitórias ---");
                int i = 1;
                for (Map.Entry<UUID, Integer> e : ev.ranking(10)) {
                    sender.sendMessage("§f" + i++ + "º " + ev.cor() + ev.nomeDe(e.getKey()) + " §7- §f" + e.getValue());
                }
                if (i == 1) sender.sendMessage("§7Ninguém venceu ainda.");
            }
            case "info" -> info(sender);
            case "abrir" -> {
                if (!admin) return semPermissao(sender);
                Evento aberto = plugin.eventoAberto();
                if (aberto != null) sender.sendMessage("§cJá há um evento aberto: " + aberto.nome() + ".");
                else ev.abrir();
            }
            case "iniciar" -> {
                if (!admin) return semPermissao(sender);
                if (ev.getFase() != Evento.Fase.INSCRICAO) sender.sendMessage("§cNão há inscrições abertas.");
                else ev.iniciar();
            }
            case "cancelar" -> {
                if (!admin) return semPermissao(sender);
                if (ev.getFase() == null) sender.sendMessage("§cNão há " + ev.nome() + (ev.feminino() ? " aberta." : " aberto."));
                else ev.cancelar("pela staff");
            }
            case "construir" -> {
                if (!admin) return semPermissao(sender);
                if (ev.getFase() == Evento.Fase.PREPARACAO || ev.getFase() == Evento.Fase.LUTA) {
                    sender.sendMessage("§cNão dá pra reconstruir com a luta rolando.");
                } else if (ev.mundo() == null) {
                    sender.sendMessage("§cO mundo da arena (" + ev.id() + ".arena.mundo) não existe.");
                } else {
                    ev.construir();
                    int[][] c = ev.caixa();
                    sender.sendMessage("§aArena " + ev.doNome() + " construída. Caixa (AreasPlugin): §f[" + c[0][0] + ", " + c[0][1] + ", "
                            + c[0][2] + "] a [" + c[1][0] + ", " + c[1][1] + ", " + c[1][2] + "]");
                }
            }
            case "reload" -> {
                if (!admin) return semPermissao(sender);
                if (plugin.eventoAberto() != null && plugin.eventoAberto().getFase() != Evento.Fase.INSCRICAO) {
                    sender.sendMessage("§cEspere a luta acabar pra recarregar.");
                } else {
                    plugin.reloadConfig();
                    plugin.getEventos().forEach(Evento::recarregar);
                    sender.sendMessage("§aConfig do EventosPlugin recarregado.");
                }
            }
            default -> sender.sendMessage("§cUso: §f/" + ev.id() + " <entrar|sair|info|assistir|top>"
                    + (admin ? " §7(staff: abrir, iniciar, cancelar, construir, reload)" : ""));
        }
        return true;
    }

    private void info(CommandSender sender) {
        sender.sendMessage(ev.titulo());
        sender.sendMessage("§7" + ev.descricao() + " " + ev.textoPremio());
        sender.sendMessage("§7Grátis, com o §finventário totalmente vazio §7(nem armadura), uma inscrição por IP. A espera é no saguão dos eventos (§f/warp eventos§7).");
        String agenda = String.join(" e ", ev.cfg().getStringList("agenda"));
        sender.sendMessage("§7Todo dia" + (agenda.isEmpty() ? " (sem agenda)" : " às §f" + agenda) + "§7.");
        if (ev.getFase() == null) {
            sender.sendMessage("§7Nenhum aberto agora.");
        } else {
            long vivos = ev.getParticipantes().values().stream().filter(Evento.Participante::isVivo).count();
            sender.sendMessage(ev.cor() + "Agora: §f" + switch (ev.getFase()) {
                case INSCRICAO -> "inscrições abertas (" + ev.getParticipantes().size() + " inscrito(s)): /" + ev.id() + " entrar";
                case PREPARACAO -> "começando";
                case LUTA -> "luta rolando, " + vivos + " de pé" + (ev.detalheLuta() != null ? ", " + ev.detalheLuta() : "")
                        + ": /" + ev.id() + " assistir";
                case FIM -> "acabou de terminar";
            });
        }
    }

    private static boolean semConsole(CommandSender sender) {
        sender.sendMessage("§cSó jogadores.");
        return true;
    }

    private static boolean semPermissao(CommandSender sender) {
        sender.sendMessage("§cSó a staff.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> opcoes = new ArrayList<>(List.of("entrar", "sair", "info", "assistir", "top"));
        if (sender.hasPermission("eventos.admin")) opcoes.addAll(List.of("abrir", "iniciar", "cancelar", "construir", "reload"));
        return opcoes.stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
