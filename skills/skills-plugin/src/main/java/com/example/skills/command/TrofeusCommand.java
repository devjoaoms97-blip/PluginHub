package com.example.skills.command;

import com.example.skills.SkillsPlugin;
import com.example.skills.profissao.PescaLoot;
import com.example.skills.profissao.Trofeus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /trofeus: quanto valem as cabeças (Caçador) e as relíquias (Escavador) que você tem. /trofeus vender: vende
 * os do inventário. Staff: /trofeus dar.
 */
public class TrofeusCommand implements CommandExecutor, TabCompleter {

    private final SkillsPlugin plugin;

    public TrofeusCommand(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Trofeus trofeus = plugin.getTrofeus();
        if (args.length >= 1 && args[0].equalsIgnoreCase("dar") && sender.hasPermission("skills.admin")) {
            if (args.length < 3) {
                sender.sendMessage("§cUso: /trofeus dar <jogador> <cabeca:<mob>|reliquia:<id>> [qtd]");
                return true;
            }
            Player alvo = Bukkit.getPlayerExact(args[1]);
            ItemStack item = trofeus.criar(args[2], args.length >= 4 ? Integer.parseInt(args[3]) : 1);
            if (alvo == null || item == null) {
                sender.sendMessage("§cJogador offline ou troféu desconhecido.");
                return true;
            }
            alvo.getInventory().addItem(item).values().forEach(i -> alvo.getWorld().dropItem(alvo.getLocation(), i));
            sender.sendMessage("§aDeu " + item.getItemMeta().getDisplayName() + " §ax" + item.getAmount() + " pra " + alvo.getName() + ".");
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores. Staff: /trofeus dar <jogador> <troféu> [qtd]");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("vender")) {
            double total = trofeus.vender(jogador);
            if (total <= 0) jogador.sendMessage("§cVocê não tem cabeças nem relíquias no inventário.");
            else jogador.sendMessage("§6☠ §aVendeu seus troféus por §f" + PescaLoot.dinheiro(total) + "§a.");
            return true;
        }
        double total = 0;
        int n = 0;
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            double preco = trofeus.preco(trofeus.marca(item));
            if (preco > 0) {
                total += preco * item.getAmount();
                n += item.getAmount();
            }
        }
        jogador.sendMessage("§6§l☠ Troféus");
        jogador.sendMessage("§7Cabeças de mob caem pro §fCaçador§7; relíquias, cavando, pro §fEscavador§7 (§f/skills§7).");
        jogador.sendMessage(n > 0 ? "§eVocê tem §f" + n + " §etroféu(s), valendo §f" + PescaLoot.dinheiro(total) + "§e: §f/trofeus vender"
                : "§7Você não tem troféus no inventário.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> op = new ArrayList<>();
        if (args.length == 1) {
            op.add("vender");
            if (sender.hasPermission("skills.admin")) op.add("dar");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar") && sender.hasPermission("skills.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> op.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar") && sender.hasPermission("skills.admin")) {
            op.addAll(plugin.getTrofeus().ids());
        }
        String d = args[args.length - 1].toLowerCase(Locale.ROOT);
        op.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(d));
        return op;
    }
}
