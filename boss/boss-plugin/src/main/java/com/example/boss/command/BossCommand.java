package com.example.boss.command;

import com.example.boss.BossPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BossCommand implements CommandExecutor, TabCompleter {

    private final BossPlugin plugin;

    public BossCommand(BossPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boss.admin")) {
            sender.sendMessage("§cVocê não tem permissão pra usar esse comando.");
            return true;
        }

        if (args.length == 0) {
            enviarAjuda(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> plugin.getBossManager().iniciar(sender);

            case "stop" -> {
                if (plugin.getBossManager().estaAtivo()) {
                    plugin.getBossManager().getBossAtivo().remove();
                    plugin.getBossManager().finalizar(false);
                    sender.sendMessage("§aBoss removido.");
                } else {
                    sender.sendMessage("§cNenhum boss ativo no momento.");
                }
            }

            case "setlocal" -> {
                Player p = exigirJogador(sender);
                if (p == null) return true;
                plugin.getArenaManager().setLocalizacao(p.getLocation());
                sender.sendMessage("§aLocal da arena definido na sua posição atual.");
            }

            case "additem" -> {
                Player p = exigirJogador(sender);
                if (p == null) return true;
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /boss additem <chance%>");
                    return true;
                }
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    sender.sendMessage("§cSegure o item que quer adicionar na mão.");
                    return true;
                }
                double chance = parseDouble(args[1], sender);
                if (Double.isNaN(chance)) return true;
                plugin.getLootManager().adicionarLootChao(item, chance);
                sender.sendMessage("§aItem adicionado ao loot do chão com " + chance + "% de chance.");
            }

            case "addchampionitem" -> {
                Player p = exigirJogador(sender);
                if (p == null) return true;
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /boss addchampionitem <peso%>");
                    return true;
                }
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    sender.sendMessage("§cSegure o item que quer adicionar na mão.");
                    return true;
                }
                double peso = parseDouble(args[1], sender);
                if (Double.isNaN(peso)) return true;
                plugin.getLootManager().adicionarLootCampeao(item, peso);
                sender.sendMessage("§aItem adicionado à lista de prêmios do campeão com peso " + peso + ".");
            }

            case "reload" -> {
                plugin.reloadConfig();
                plugin.getBossManager().recarregarFases();
                plugin.getScheduleManager().recarregar();
                sender.sendMessage("§aConfiguração recarregada.");
            }

            default -> enviarAjuda(sender);
        }

        return true;
    }

    private Player exigirJogador(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cSomente jogadores podem usar esse comando.");
        return null;
    }

    private double parseDouble(String texto, CommandSender sender) {
        try {
            return Double.parseDouble(texto.replace(",", "."));
        } catch (NumberFormatException e) {
            sender.sendMessage("§cValor inválido: " + texto);
            return Double.NaN;
        }
    }

    private void enviarAjuda(CommandSender sender) {
        sender.sendMessage("§e§l--- Comandos do Boss ---");
        sender.sendMessage("§f/boss start §7- inicia o boss manualmente");
        sender.sendMessage("§f/boss stop §7- remove o boss ativo");
        sender.sendMessage("§f/boss setlocal §7- define a arena na sua posição atual");
        sender.sendMessage("§f/boss additem <chance%> §7- adiciona item ao loot do chão (segure na mão)");
        sender.sendMessage("§f/boss addchampionitem <peso%> §7- adiciona item ao sorteio do campeão (segure na mão)");
        sender.sendMessage("§f/boss reload §7- recarrega o config.yml");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opcoes = new ArrayList<>(List.of(
                    "start", "stop", "setlocal", "additem", "addchampionitem", "reload"));
            String digitado = args[0].toLowerCase(Locale.ROOT);
            opcoes.removeIf(o -> !o.startsWith(digitado));
            return opcoes;
        }
        return List.of();
    }
}
