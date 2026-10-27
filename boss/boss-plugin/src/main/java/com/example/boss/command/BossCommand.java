package com.example.boss.command;

import com.example.boss.BossPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
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
        if (args.length > 0 && args[0].equalsIgnoreCase("proximo")) {
            proximo(sender);
            return true;
        }
        if (!sender.hasPermission("boss.admin")) {
            sender.sendMessage("§cVocê não tem permissão pra usar esse comando.");
            return true;
        }

        if (args.length == 0) {
            enviarAjuda(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                // /boss start [id] [elite]: "elite" força o boss elite (sem ele, o aleatório tem a chance do config)
                boolean elite = args.length > 1 && args[args.length - 1].equalsIgnoreCase("elite");
                String id = args.length > 1 && !args[1].equalsIgnoreCase("elite") ? args[1] : null;
                if (id != null) {
                    plugin.getBossManager().iniciarPorId(id, sender, elite);
                } else if (elite) {
                    plugin.getBossManager().iniciarAleatorio(sender, true);
                } else {
                    plugin.getBossManager().iniciarAleatorio(sender);
                }
            }

            case "list" -> {
                sender.sendMessage("§e§l--- Perfis de Boss ---");
                plugin.getBossManager().getPerfis().forEach((id, perfil) -> sender.sendMessage(
                        "§f" + id + " §7- " + perfil.getNome() + (perfil.isEspecial() ? " §c(especial)" : "")));
            }

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
                if (args.length >= 5) {
                    World mundo = Bukkit.getWorld(args[1]);
                    double x = parseDouble(args[2], sender), y = parseDouble(args[3], sender), z = parseDouble(args[4], sender);
                    if (mundo == null) {
                        sender.sendMessage("§cMundo não encontrado: " + args[1]);
                    } else if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
                        plugin.getArenaManager().setLocalizacao(new Location(mundo, x, y, z));
                        sender.sendMessage("§aLocal da arena definido em " + mundo.getName() + " " + x + ", " + y + ", " + z + ".");
                    }
                    return true;
                }
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
                plugin.getBossManager().recarregarPerfis();
                plugin.getScheduleManager().recarregar();
                sender.sendMessage("§aConfiguração recarregada.");
            }

            default -> enviarAjuda(sender);
        }

        return true;
    }

    /** /boss proximo (pra todos): quando o boss aparece de novo, pela agenda. */
    private void proximo(CommandSender sender) {
        if (plugin.getMaldicao().ativa()) {
            sender.sendMessage("§4☠ §cO mal reina: XP de skill reduzido por mais §f" + plugin.getMaldicao().horasRestantes()
                    + "h§c, ou até alguém derrotar o próximo boss.");
        }
        if (plugin.getBossManager().estaAtivo()) {
            sender.sendMessage("§4☠ §cTem um boss vivo agora! §7Corra pro portão do boss, ao sul do spawn (§f/warp boss§7).");
            return;
        }
        LocalDateTime quando = plugin.getScheduleManager().proximo();
        if (quando == null) {
            sender.sendMessage("§7Não tem boss marcado na agenda. Fique de olho no chat.");
            return;
        }
        String dia = quando.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
        long minutos = Math.max(1, Duration.between(LocalDateTime.now(), quando).toMinutes());
        String falta = minutos >= 1440 ? (minutos / 1440) + "d " + (minutos % 1440 / 60) + "h"
                : minutos >= 60 ? (minutos / 60) + "h " + (minutos % 60) + "min" : minutos + "min";
        String bossId = plugin.getScheduleManager().proximoBossId();
        var perfil = bossId == null ? null : plugin.getBossManager().getPerfis().get(bossId);
        String qual = perfil == null ? "" : " §7— " + org.bukkit.ChatColor.translateAlternateColorCodes('&', perfil.getNome());
        sender.sendMessage("§e☠ Próximo boss: §f" + dia + " às " + quando.toLocalTime() + " §7(daqui a " + falta + ")" + qual);
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
        sender.sendMessage("§f/boss proximo §7- quando o próximo boss aparece (pra todos)");
        sender.sendMessage("§f/boss start [id] [elite] §7- inicia um boss aleatório (ou um específico pelo id); \"elite\" força o elite");
        sender.sendMessage("§f/boss list §7- lista os perfis de boss configurados");
        sender.sendMessage("§f/boss stop §7- remove o boss ativo");
        sender.sendMessage("§f/boss setlocal [mundo x y z] §7- define a arena na sua posição atual (ou no lugar informado)");
        sender.sendMessage("§f/boss additem <chance%> §7- adiciona item ao loot do chão (segure na mão)");
        sender.sendMessage("§f/boss addchampionitem <peso%> §7- adiciona item ao sorteio do campeão (segure na mão)");
        sender.sendMessage("§f/boss reload §7- recarrega o config.yml");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("boss.admin")) {
            return args.length == 1 && "proximo".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("proximo") : List.of();
        }
        if (args.length == 1) {
            List<String> opcoes = new ArrayList<>(List.of(
                    "proximo", "start", "list", "stop", "setlocal", "additem", "addchampionitem", "reload"));
            String digitado = args[0].toLowerCase(Locale.ROOT);
            opcoes.removeIf(o -> !o.startsWith(digitado));
            return opcoes;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return "elite".startsWith(args[2].toLowerCase(Locale.ROOT)) ? List.of("elite") : List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> opcoes = new ArrayList<>(plugin.getBossManager().getPerfis().keySet());
            opcoes.add("elite");
            String digitado = args[1].toLowerCase(Locale.ROOT);
            opcoes.removeIf(o -> !o.startsWith(digitado));
            return opcoes;
        }
        return List.of();
    }
}
