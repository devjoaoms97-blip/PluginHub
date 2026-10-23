package com.example.combate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** /combate (staff): mostra e ajusta o tempo entre golpes com o servidor ligado, e recarrega o config. */
public class CombateCommand implements CommandExecutor, TabCompleter {

    private final CombatePlugin plugin;

    public CombateCommand(CombatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "intervalo" -> {
                if (args.length < 2) {
                    sender.sendMessage("§7Tempo entre golpes: §f" + descrever(plugin.getInvulnerabilidade().ticksEntreGolpes())
                            + "§7. Mude com §f/combate intervalo <ticks> §7(20 ticks = 1 s; 10 = padrão do jogo; 0 = sem espera).");
                    return true;
                }
                int ticks;
                try {
                    ticks = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    ticks = -1;
                }
                if (ticks < 0 || ticks > 100) {
                    sender.sendMessage("§cUse um número de 0 a 100 ticks.");
                    return true;
                }
                plugin.getConfig().set("invulnerabilidade.ticks-entre-golpes", ticks);
                plugin.saveConfig();
                sender.sendMessage("§aTempo entre golpes: §f" + descrever(ticks) + "§a.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aConfig recarregado. §7(ligar ou desligar uma mecânica inteira pede reinício.)");
            }
            default -> sender.sendMessage("§cUso: §f/combate intervalo [ticks] §7| §f/combate reload");
        }
        return true;
    }

    private static String descrever(int ticks) {
        if (ticks == 0) return "0 (todo clique conta)";
        return ticks + " ticks (" + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", ticks / 20.0) + " s"
                + (ticks == 10 ? ", padrão do jogo" : "") + ")";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("intervalo", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("intervalo")) return List.of("0", "5", "10");
        return List.of();
    }
}
