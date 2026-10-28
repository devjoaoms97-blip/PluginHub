package com.example.dicas.command;

import com.example.dicas.DicasPlugin;
import com.example.dicas.model.NpcDica;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /dica (staff): criar, fala, falas, tirarfala, nome, skin, mover, remover, lista, reload. Criar e
 * mover pegam o lugar de quem roda; pelo console, criar aceita o lugar depois de "em".
 * Os textos aceitam cores com &.
 */
public class DicaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("criar", "fala", "falas", "tirarfala", "nome", "skin", "mover", "remover", "lista", "reload");

    private final DicasPlugin plugin;

    public DicaCommand(DicasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(DicasPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§cVocê não tem permissão pra isso.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "criar" -> criar(sender, args);
            case "lista" -> {
                if (plugin.getNpcs().getNpcs().isEmpty()) sender.sendMessage("§7Nenhum NPC de dicas ainda.");
                plugin.getNpcs().getNpcs().values().forEach(n -> sender.sendMessage("§f" + n.getId() + " §7- "
                        + n.getNome().replace('&', '§') + " §7(" + n.getFalas().size() + " fala(s), " + n.getMundo()
                        + " " + (int) n.getX() + " " + (int) n.getY() + " " + (int) n.getZ() + ")"));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getNpcs().carregarTodos();
                sender.sendMessage("§aConfig recarregado.");
            }
            case "fala", "falas", "tirarfala", "nome", "skin", "mover", "remover" -> {
                if (args.length < 2) {
                    enviarAjuda(sender);
                    return true;
                }
                NpcDica npc = plugin.getNpcs().get(args[1]);
                if (npc == null) {
                    sender.sendMessage("§cNPC §f" + args[1] + " §cnão existe. Veja §f/dica lista§c.");
                    return true;
                }
                editar(sender, sub, npc, args);
            }
            default -> enviarAjuda(sender);
        }
        return true;
    }

    private void criar(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUso: §f/dica criar <id> <nome> §7(onde você está, olhando pra onde ele deve olhar)");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,24}")) {
            sender.sendMessage("§cId inválido: use letras minúsculas, números, - ou _.");
            return;
        }
        if (plugin.getNpcs().get(id) != null) {
            sender.sendMessage("§cJá existe o NPC §f" + id + "§c.");
            return;
        }
        Location local;
        int inicioNome = 2;
        if (args[2].equalsIgnoreCase("em")) {
            // criar <id> em <mundo> <x> <y> <z> <yaw> <nome...>
            World mundo = args.length > 3 ? Bukkit.getWorld(args[3]) : null;
            if (mundo == null || args.length < 9) {
                sender.sendMessage("§cUso: §f/dica criar <id> em <mundo> <x> <y> <z> <yaw> <nome>");
                return;
            }
            try {
                local = new Location(mundo, Double.parseDouble(args[4]), Double.parseDouble(args[5]),
                        Double.parseDouble(args[6]), Float.parseFloat(args[7]), 0);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cCoordenadas inválidas.");
                return;
            }
            inicioNome = 8;
        } else if (sender instanceof Player p) {
            local = centralizar(p.getLocation());
        } else {
            sender.sendMessage("§cPelo console: §f/dica criar <id> em <mundo> <x> <y> <z> <yaw> <nome>");
            return;
        }
        NpcDica npc = plugin.getNpcs().criar(id, juntar(args, inicioNome), local);
        plugin.getNpcs().atualizar(npc);
        sender.sendMessage("§aNPC §f" + id + " §acriado. Adicione falas com §f/dica fala " + id + " <texto>§a.");
    }

    private void editar(CommandSender sender, String sub, NpcDica npc, String[] args) {
        switch (sub) {
            case "fala" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso: §f/dica fala <id> <texto> §7(aceita cores com &)");
                    return;
                }
                npc.getFalas().add(juntar(args, 2));
                plugin.getNpcs().salvar();
                sender.sendMessage("§aFala " + npc.getFalas().size() + " adicionada a §f" + npc.getId() + "§a.");
            }
            case "falas" -> {
                sender.sendMessage("§e§l--- Falas de " + npc.getId() + " ---");
                for (int i = 0; i < npc.getFalas().size(); i++) {
                    sender.sendMessage("§f" + (i + 1) + ". §7" + npc.getFalas().get(i).replace('&', '§'));
                }
            }
            case "tirarfala" -> {
                int n;
                try {
                    n = Integer.parseInt(args.length > 2 ? args[2] : "") - 1;
                } catch (NumberFormatException e) {
                    n = -1;
                }
                if (n < 0 || n >= npc.getFalas().size()) {
                    sender.sendMessage("§cUso: §f/dica tirarfala <id> <número> §7(veja §f/dica falas " + npc.getId() + "§7)");
                    return;
                }
                npc.getFalas().remove(n);
                plugin.getNpcs().salvar();
                sender.sendMessage("§aFala removida.");
            }
            case "nome" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso: §f/dica nome <id> <nome>");
                    return;
                }
                npc.setNome(juntar(args, 2));
                plugin.getNpcs().salvar();
                plugin.getNpcs().atualizar(npc);
                sender.sendMessage("§aNome trocado.");
            }
            case "skin" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso: §f/dica skin <id> <nick|textura:<hash>|padrao>");
                    return;
                }
                npc.setSkin(args[2].equalsIgnoreCase("padrao") ? null : args[2]);
                plugin.getNpcs().salvar();
                plugin.getNpcs().atualizar(npc);
                sender.sendMessage("§aSkin trocada.");
            }
            case "mover" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cSó jogadores podem usar esse comando.");
                    return;
                }
                npc.setLocal(centralizar(p.getLocation()));
                plugin.getNpcs().salvar();
                plugin.getNpcs().atualizar(npc);
                sender.sendMessage("§aNPC movido pra onde você está.");
            }
            case "remover" -> {
                plugin.getNpcs().remover(npc);
                sender.sendMessage("§aNPC §f" + npc.getId() + " §aremovido.");
            }
            default -> enviarAjuda(sender);
        }
    }

    /** No meio do bloco, olhando pro ponto cardeal mais próximo de pra onde a pessoa olha. */
    private Location centralizar(Location l) {
        return new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5,
                Math.round(l.getYaw() / 45f) * 45f, 0);
    }

    private String juntar(String[] args, int inicio) {
        return String.join(" ", Arrays.copyOfRange(args, inicio, args.length));
    }

    private void enviarAjuda(CommandSender sender) {
        sender.sendMessage("§e§l--- NPCs de dicas ---");
        sender.sendMessage("§f/dica criar <id> <nome> §7- cria onde você está (cores com &)");
        sender.sendMessage("§f/dica fala <id> <texto> §7- adiciona uma fala (cores com &)");
        sender.sendMessage("§f/dica falas <id> §7| §f/dica tirarfala <id> <número>");
        sender.sendMessage("§f/dica nome <id> <nome> §7| §f/dica skin <id> <nick|textura:<hash>|padrao>");
        sender.sendMessage("§f/dica mover <id> §7| §f/dica remover <id> §7| §f/dica lista §7| §f/dica reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (!sender.hasPermission(DicasPlugin.PERMISSAO_ADMIN)) return opcoes;
        if (args.length == 1) {
            opcoes.addAll(SUBS);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("criar") && !args[0].equalsIgnoreCase("lista")) {
            opcoes.addAll(plugin.getNpcs().getNpcs().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("skin")) {
            opcoes.add("padrao");
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
