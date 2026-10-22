package com.example.missoes.command;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.Missao;
import com.example.missoes.model.ProgressoJogador;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /missoes — suas missões de hoje. Staff: /missoes admin <resortear|reload>. */
public class MissoesCommand implements CommandExecutor, TabCompleter {

    private static final int TAMANHO_BARRA = 20;

    private final MissoesPlugin plugin;

    public MissoesCommand(MissoesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores têm missões. Staff: §f/missoes admin <resortear|reload>");
            return true;
        }
        listar(jogador);
        return true;
    }

    private void listar(Player jogador) {
        ProgressoJogador dia = plugin.getMissaoManager().getDoDia(jogador.getUniqueId());
        if (dia.getProgresso().isEmpty()) {
            jogador.sendMessage("§7Nenhuma missão disponível hoje.");
            return;
        }

        jogador.sendMessage("§e§l--- Missões de hoje ---");
        for (Map.Entry<String, Integer> entrada : dia.getProgresso().entrySet()) {
            Missao missao = plugin.getMissaoManager().getMissao(entrada.getKey());
            if (missao == null) continue; // removida do config depois de sorteada
            int atual = entrada.getValue();
            boolean feita = atual >= missao.quantidade();
            jogador.sendMessage((feita ? "§a✔ " : "§7• ") + "§f" + missao.descricao());
            jogador.sendMessage("   " + barra(atual, missao.quantidade()) + " §f" + atual + "/" + missao.quantidade()
                    + " §7| " + recompensa(missao.coins(), missao.skill(), missao.xp()));
        }

        double bonusCoins = plugin.getConfig().getDouble("bonus-todas.coins", 0);
        double bonusXp = plugin.getConfig().getDouble("bonus-todas.xp", 0);
        if (bonusCoins > 0 || bonusXp > 0) {
            jogador.sendMessage((dia.isBonusPago() ? "§a✔ " : "§6★ ") + "§6Bônus por completar todas: "
                    + recompensa(bonusCoins, plugin.getConfig().getString("bonus-todas.skill"), bonusXp));
        }

        Duration ateMeiaNoite = Duration.between(LocalDateTime.now(), LocalDateTime.now().toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT));
        jogador.sendMessage("§8Novas missões em " + ateMeiaNoite.toHours() + "h " + ateMeiaNoite.toMinutesPart() + "min.");
    }

    private String barra(int atual, int total) {
        int cheios = (int) Math.round((double) atual / total * TAMANHO_BARRA);
        return "§a" + "|".repeat(cheios) + "§8" + "|".repeat(TAMANHO_BARRA - cheios);
    }

    private String recompensa(double coins, String skill, double xp) {
        List<String> partes = new ArrayList<>();
        if (coins > 0) partes.add("§f" + MissoesPlugin.formatarValor(coins));
        if (skill != null && xp > 0 && plugin.getSkillsIntegration().disponivel()) {
            partes.add("§b" + (int) xp + " XP de " + skill.toLowerCase(Locale.ROOT));
        }
        return partes.isEmpty() ? "§7sem recompensa" : String.join("§7 + ", partes);
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("missoes.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getMissaoManager().carregarPool();
            sender.sendMessage("§aConfiguração recarregada: §f" + plugin.getMissaoManager().getPool().size()
                    + " §amissão(ões) no pool. §7(Quem já sorteou hoje mantém as missões até amanhã.)");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("resortear")) {
            OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[2]);
            if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
                sender.sendMessage("§cJogador \"" + args[2] + "\" não encontrado.");
                return;
            }
            plugin.getMissaoManager().resortear(alvo.getUniqueId());
            sender.sendMessage("§aMissões de hoje de §f" + alvo.getName() + " §asorteadas de novo (progresso zerado).");
            return;
        }
        sender.sendMessage("§cUso: §f/missoes admin resortear <jogador> §7| §f/missoes admin reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (!sender.hasPermission("missoes.admin")) return opcoes;
        if (args.length == 1) {
            opcoes.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            opcoes.addAll(List.of("resortear", "reload"));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("resortear")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
