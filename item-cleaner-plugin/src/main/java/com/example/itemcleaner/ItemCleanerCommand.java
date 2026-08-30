package com.example.itemcleaner;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public class ItemCleanerCommand implements CommandExecutor, TabCompleter {

    private static final int AVISO_MANUAL_SEGUNDOS = 10;

    private final ItemCleanerPlugin plugin;

    public ItemCleanerCommand(ItemCleanerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.limparComAviso(AVISO_MANUAL_SEGUNDOS);
            sender.sendMessage("§aLimpeza manual agendada — aviso enviado no chat, itens serão removidos em "
                    + AVISO_MANUAL_SEGUNDOS + "s.");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "agora" -> {
                int removidos = plugin.limparItens();
                sender.sendMessage("§aLimpeza executada agora, sem aviso. §f" + removidos + " §aitens removidos.");
            }
            case "status" -> sender.sendMessage("§7Próxima limpeza automática em §f"
                    + plugin.getSegundosRestantes() + "s§7.");
            case "reload" -> {
                plugin.reloadConfig();
                plugin.iniciarTarefa();
                sender.sendMessage("§aConfiguração recarregada e temporizador reiniciado.");
            }
            default -> sender.sendMessage("§cUso: §f/limparchao [agora|status|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("agora", "status", "reload");
        }
        return List.of();
    }
}
