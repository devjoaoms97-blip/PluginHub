package com.example.eventos.vietna;

import com.example.eventos.EventosPlugin;
import com.example.eventos.util.Formato;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /vietna entrar|sair|info|assistir|top e, pra staff, abrir|iniciar|cancelar|construir|reload. */
public class VietnaCommand implements TabExecutor {

    private final EventosPlugin plugin;

    public VietnaCommand(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Vietna v = plugin.getVietna();
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("eventos.admin");
        switch (sub) {
            case "entrar" -> {
                if (!(sender instanceof Player p)) return semConsole(sender);
                String erro = v.entrar(p);
                p.sendMessage(erro == null ? "§a[Vietnã] Inscrito! Fique com o inventário vazio até começar." : "§c" + erro);
            }
            case "sair" -> {
                if (!(sender instanceof Player p)) return semConsole(sender);
                p.sendMessage(v.sair(p) ? "§7[Vietnã] Inscrição cancelada." : "§cVocê não está inscrito (ou o evento já começou).");
            }
            case "assistir" -> {
                if (!(sender instanceof Player p)) return semConsole(sender);
                if (v.lutando(p.getUniqueId()) != null) {
                    p.sendMessage("§cVocê está lutando!");
                } else {
                    v.assistir(p);
                    p.sendMessage("§7[Vietnã] Na arquibancada. Pra sair: §f/spawn§7.");
                }
            }
            case "top" -> {
                sender.sendMessage("§2§l--- Vietnã: vitórias ---");
                int i = 1;
                for (Map.Entry<UUID, Integer> e : v.ranking(10)) {
                    sender.sendMessage("§f" + i++ + "º §a" + v.nomeDe(e.getKey()) + " §7- §f" + e.getValue());
                }
                if (i == 1) sender.sendMessage("§7Ninguém venceu ainda.");
            }
            case "info" -> info(sender, v);
            case "abrir" -> {
                if (!admin) return semPermissao(sender);
                if (v.getFase() != null) sender.sendMessage("§cJá há um Vietnã aberto.");
                else v.abrir();
            }
            case "iniciar" -> {
                if (!admin) return semPermissao(sender);
                if (v.getFase() != Vietna.Fase.INSCRICAO) sender.sendMessage("§cNão há inscrições abertas.");
                else v.iniciar();
            }
            case "cancelar" -> {
                if (!admin) return semPermissao(sender);
                if (v.getFase() == null) sender.sendMessage("§cNão há Vietnã aberto.");
                else v.cancelar("pela staff");
            }
            case "construir" -> {
                if (!admin) return semPermissao(sender);
                if (v.getFase() == Vietna.Fase.PREPARACAO || v.getFase() == Vietna.Fase.LUTA) {
                    sender.sendMessage("§cNão dá pra reconstruir com a luta rolando.");
                } else if (v.getArena().mundo() == null) {
                    sender.sendMessage("§cO mundo da arena (vietna.arena.mundo) não existe.");
                } else {
                    v.getArena().construir();
                    int[][] caixa = v.getArena().caixa();
                    sender.sendMessage("§aArena do Vietnã construída. Caixa (AreasPlugin): §f[" + caixa[0][0] + ", " + caixa[0][1] + ", "
                            + caixa[0][2] + "] a [" + caixa[1][0] + ", " + caixa[1][1] + ", " + caixa[1][2] + "]");
                }
            }
            case "reload" -> {
                if (!admin) return semPermissao(sender);
                if (v.getFase() == Vietna.Fase.PREPARACAO || v.getFase() == Vietna.Fase.LUTA) {
                    sender.sendMessage("§cEspere a luta acabar pra recarregar.");
                } else {
                    plugin.reloadConfig();
                    v.recarregar();
                    sender.sendMessage("§aConfig do EventosPlugin recarregado.");
                }
            }
            default -> sender.sendMessage("§cUso: §f/vietna <entrar|sair|info|assistir|top>"
                    + (admin ? " §7(staff: abrir, iniciar, cancelar, construir, reload)" : ""));
        }
        return true;
    }

    private void info(CommandSender sender, Vietna v) {
        sender.sendMessage("§2§l☘ Vietnã ☘");
        sender.sendMessage("§7Pilares sobre a água, todos com o mesmo kit (arco, bola de neve, carga de vento e vara). Os golpes só"
                + " empurram; caiu na água, está fora. O último de pé leva um cheque de §f" + Formato.valor(v.premio()) + "§7.");
        sender.sendMessage("§7Grátis, com o §finventário totalmente vazio §7(nem armadura), uma inscrição por IP.");
        String agenda = String.join(" e ", plugin.getConfig().getStringList("vietna.agenda"));
        sender.sendMessage("§7Todo dia" + (agenda.isEmpty() ? " (sem agenda)" : " às §f" + agenda) + "§7.");
        if (v.getFase() == null) {
            sender.sendMessage("§7Nenhum aberto agora.");
        } else {
            long vivos = v.getParticipantes().values().stream().filter(Vietna.Participante::isVivo).count();
            sender.sendMessage("§aAgora: §f" + switch (v.getFase()) {
                case INSCRICAO -> "inscrições abertas (" + v.getParticipantes().size() + " inscrito(s)): /vietna entrar";
                case PREPARACAO -> "começando";
                case LUTA -> "luta rolando, " + vivos + " de pé: /vietna assistir";
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
