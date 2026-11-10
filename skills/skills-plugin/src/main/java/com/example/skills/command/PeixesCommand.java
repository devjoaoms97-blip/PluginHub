package com.example.skills.command;

import com.example.skills.SkillsPlugin;
import com.example.skills.profissao.PescaLoot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /peixes: os peixes raros (loot próprio da pesca) com o preço de cada um, quantos você tem e a sua
 * chance de pesca especial. /peixes vender: vende os do inventário. Staff: /peixes dar.
 */
public class PeixesCommand implements CommandExecutor, TabCompleter {

    private final SkillsPlugin plugin;

    public PeixesCommand(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PescaLoot loot = plugin.getPescaLoot();
        if (args.length >= 1 && args[0].equalsIgnoreCase("dar") && sender.hasPermission("skills.admin")) {
            return dar(sender, args);
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores. Staff: /peixes dar <jogador> <item> [qtd]");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("vender")) {
            double total = loot.venderPeixes(jogador);
            if (total <= 0) jogador.sendMessage("§cVocê não tem peixes raros no inventário.");
            else jogador.sendMessage("§b🎣 §aVendeu seus peixes raros por §f" + PescaLoot.dinheiro(total) + "§a.");
            return true;
        }

        Map<String, Integer> meus = new LinkedHashMap<>();
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            String id = loot.idPeixe(item);
            if (id != null) meus.merge(id, item.getAmount(), Integer::sum);
        }
        jogador.sendMessage("§b§l🎣 Peixes raros");
        ConfigurationSection peixes = loot.secaoPeixes();
        double total = 0;
        if (peixes != null) {
            for (String id : peixes.getKeys(false)) {
                double preco = loot.precoPeixe(id);
                int tenho = meus.getOrDefault(id, 0);
                total += preco * tenho;
                jogador.sendMessage("§8- " + ChatColor.translateAlternateColorCodes('&', peixes.getString(id + ".nome", id))
                        + " §7vale §a" + PescaLoot.dinheiro(preco) + (tenho > 0 ? " §7(você tem §f" + tenho + "§7)" : ""));
            }
        }
        jogador.sendMessage(String.format(Locale.forLanguageTag("pt-BR"),
                "§7Sua chance de pesca especial: §b%.1f%% §7por pesca (§f%.0f%%§7 disso pescando parado por mais de %.0f min).",
                loot.chanceAtiva(jogador) * 100, loot.fatorParado() * 100, loot.minutosParado()));
        if (total > 0) jogador.sendMessage("§eVocê tem §f" + PescaLoot.dinheiro(total) + " §eem peixes: §f/peixes vender");
        else jogador.sendMessage("§7Pesque pra achar peixes raros, Baús do Mar e mais. §f/peixes vender §7vende os peixes.");
        return true;
    }

    // /peixes dar <jogador> <item> [qtd]: item como no config (peixe:lambari, bau:raro, vara, adaga...)
    private boolean dar(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUso: /peixes dar <jogador> <item> [qtd] §7(peixe:<id>, bau:<raridade>, vara, adaga, fragmento, livro:mending:1, MATERIAL...)");
            return true;
        }
        Player alvo = Bukkit.getPlayerExact(args[1]);
        if (alvo == null) {
            sender.sendMessage("§cJogador offline: " + args[1]);
            return true;
        }
        int qtd = args.length >= 4 ? Math.max(1, Integer.parseInt(args[3])) : 1;
        ItemStack item = plugin.getPescaLoot().criar(args[2], qtd);
        if (item == null) {
            sender.sendMessage("§cItem desconhecido: " + args[2]);
            return true;
        }
        alvo.getInventory().addItem(item).values().forEach(i -> alvo.getWorld().dropItem(alvo.getLocation(), i));
        sender.sendMessage("§aDeu " + PescaLoot.nomeDoItem(item) + " §ax" + item.getAmount() + " §apra " + alvo.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.add("vender");
            if (sender.hasPermission("skills.admin")) opcoes.add("dar");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar") && sender.hasPermission("skills.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar") && sender.hasPermission("skills.admin")) {
            ConfigurationSection peixes = plugin.getPescaLoot().secaoPeixes();
            if (peixes != null) peixes.getKeys(false).forEach(id -> opcoes.add("peixe:" + id));
            opcoes.addAll(List.of("bau:comum", "bau:raro", "bau:lendario"));
            opcoes.add("vara");
            opcoes.add("adaga");
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
