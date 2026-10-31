package com.example.ajuda;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /comandos}: o índice dos temas (clicáveis) e, com {@code /comandos <tema>}, a página do tema,
 * com uma explicação e os comandos. Clicar num comando já o digita no chat, pra completar e mandar.
 * As páginas ficam no config ({@code comandos.paginas}).
 */
class Comandos implements CommandExecutor, TabCompleter {

    private final AjudaPlugin plugin;

    Comandos(AjudaPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection paginas() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("comandos.paginas");
        return s != null ? s : plugin.getConfig().createSection("comandos.paginas");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("ajuda.admin")) {
            plugin.reloadConfig();
            sender.sendMessage("§aConfig do AjudaPlugin recarregado.");
            return true;
        }
        if (args.length == 0) {
            indice(sender);
            return true;
        }
        ConfigurationSection pagina = paginas().getConfigurationSection(args[0].toLowerCase(Locale.ROOT));
        if (pagina == null) {
            sender.sendMessage("§cNão tem página \"" + args[0] + "\". Escolha um dos temas:");
            indice(sender);
            return true;
        }
        pagina(sender, pagina);
        return true;
    }

    private void indice(CommandSender sender) {
        sender.sendMessage(AjudaPlugin.texto(plugin.getConfig().getString("comandos.titulo", "&6&l✦ Comandos")));
        sender.sendMessage(AjudaPlugin.texto("&7Clique num tema (ou use &f/comandos <tema>&7):"));
        Component linha = Component.empty();
        int naLinha = 0;
        for (String id : paginas().getKeys(false)) {
            ConfigurationSection p = paginas().getConfigurationSection(id);
            if (p == null) continue;
            if (naLinha == 4) {
                sender.sendMessage(linha);
                linha = Component.empty();
                naLinha = 0;
            }
            linha = linha.append(Component.text(naLinha == 0 ? " " : "  "))
                    .append(AjudaPlugin.botao("&e[" + p.getString("titulo", id) + "]", "/comandos " + id,
                            "&7" + p.getString("resumo", "Abre a página") + "\n&8/comandos " + id, true));
            naLinha++;
        }
        if (naLinha > 0) sender.sendMessage(linha);
    }

    private void pagina(CommandSender sender, ConfigurationSection p) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(AjudaPlugin.texto("&6&l✦ " + p.getString("titulo", p.getName())));
        for (String t : p.getStringList("texto")) sender.sendMessage(AjudaPlugin.texto("&7" + t));
        for (String c : p.getStringList("comandos")) {
            // "comando | explicação": clicar digita o comando (até o primeiro <), pra completar
            String[] partes = c.split("\\|", 2);
            String cmd = partes[0].trim();
            String explicacao = partes.length > 1 ? partes[1].trim() : "";
            int argumento = cmd.indexOf('<');
            String digitar = (argumento > 0 ? cmd.substring(0, argumento) : cmd).replaceAll("\\[.*", "").trim();
            if (argumento > 0 || cmd.contains("[")) digitar += " ";
            sender.sendMessage(Component.text(" ")
                    .append(AjudaPlugin.botao("&f" + cmd, digitar, "&7Clique pra digitar &f" + digitar.trim(), false))
                    .append(AjudaPlugin.texto(explicacao.isEmpty() ? "" : " &8- &7" + explicacao)));
        }
        sender.sendMessage(AjudaPlugin.botao("&8« voltar aos temas", "/comandos", "&7Lista de temas", true));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> saida = new ArrayList<>();
        for (String id : paginas().getKeys(false)) if (id.startsWith(args[0].toLowerCase(Locale.ROOT))) saida.add(id);
        return saida;
    }
}
