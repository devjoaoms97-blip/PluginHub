package com.example.estruturas.command;

import com.example.estruturas.EstruturasPlugin;
import com.example.estruturas.estrutura.Estrutura;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /estrutura lista | construir <id> [mundo x y z] | desfazer | status. Pelo console (ou pra
 * construir longe), passe o mundo e o lugar dos pés: o piso fica no bloco logo abaixo.
 */
public class EstruturaCommand implements CommandExecutor, TabCompleter {

    private final EstruturasPlugin plugin;

    public EstruturaCommand(EstruturasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "lista" -> {
                sender.sendMessage("§e§l--- Estruturas disponíveis ---");
                plugin.getConstrutor().getEstruturas().forEach((id, estrutura) ->
                        sender.sendMessage("§f" + id + " §7- " + estrutura.getNome()));
            }
            case "construir" -> construir(sender, args);
            case "desfazer" -> plugin.getConstrutor().desfazer(sender);
            case "status" -> {
                String andamento = plugin.getConstrutor().getEmAndamento();
                sender.sendMessage(andamento == null ? "Nenhuma construção em andamento." : "Construindo: " + andamento);
            }
            default -> enviarAjuda(sender);
        }
        return true;
    }

    private void construir(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/estrutura construir <id> [mundo x y z] §7(veja /estrutura lista)");
            return;
        }
        Estrutura estrutura = plugin.getConstrutor().get(args[1]);
        if (estrutura == null) {
            sender.sendMessage("§cEstrutura desconhecida: " + args[1] + ". Use /estrutura lista.");
            return;
        }

        if (args.length >= 6) {
            World mundo = Bukkit.getWorld(args[2]);
            if (mundo == null) {
                sender.sendMessage("§cMundo não encontrado: " + args[2]);
                return;
            }
            try {
                int x = (int) Math.floor(Double.parseDouble(args[3]));
                int y = (int) Math.floor(Double.parseDouble(args[4]));
                int z = (int) Math.floor(Double.parseDouble(args[5]));
                plugin.getConstrutor().construir(sender, estrutura, mundo, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cCoordenadas inválidas.");
            }
            return;
        }

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cPelo console, passe o lugar: /estrutura construir <id> <mundo> <x> <y> <z>");
            return;
        }
        Location pe = jogador.getLocation();
        plugin.getConstrutor().construir(jogador, estrutura, pe.getWorld(), pe.getBlockX(), pe.getBlockY(), pe.getBlockZ());
    }

    private void enviarAjuda(CommandSender sender) {
        sender.sendMessage("§e§l--- Estruturas ---");
        sender.sendMessage("§f/estrutura lista §7- o que dá pra construir");
        sender.sendMessage("§f/estrutura construir <id> §7- constrói centrada em você (o piso fica no nível dos seus pés)");
        sender.sendMessage("§f/estrutura construir <id> <mundo> <x> <y> <z> §7- constrói com os pés nesse ponto");
        sender.sendMessage("§f/estrutura desfazer §7- volta o terreno da última construção");
        sender.sendMessage("§f/estrutura status §7- se tem construção em andamento");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.addAll(List.of("lista", "construir", "desfazer", "status"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("construir")) {
            opcoes.addAll(plugin.getConstrutor().getEstruturas().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("construir")) {
            Bukkit.getWorlds().forEach(m -> opcoes.add(m.getName()));
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
