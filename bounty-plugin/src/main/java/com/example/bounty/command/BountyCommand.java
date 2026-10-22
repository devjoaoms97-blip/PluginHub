package com.example.bounty.command;

import com.example.bounty.BountyPlugin;
import com.example.bounty.model.Recompensa;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /bounty colocar|lista|ver — staff: /bounty admin remover <jogador>. */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyPlugin plugin;

    public BountyCommand(BountyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "lista" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "colocar", "add", "oferecer" -> colocar(sender, args);
            case "lista", "top" -> lista(sender);
            case "ver" -> ver(sender, args);
            case "admin" -> admin(sender, args);
            default -> ajuda(sender);
        }
        return true;
    }

    private void colocar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores podem oferecer recompensa.");
            return;
        }
        if (args.length < 3) {
            jogador.sendMessage("§cUso: §f/bounty colocar <jogador> <valor>");
            return;
        }
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            jogador.sendMessage("§cO sistema de economia (Vault) não está disponível no servidor.");
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            jogador.sendMessage("§cJogador \"" + args[1] + "\" não encontrado.");
            return;
        }
        if (alvo.getUniqueId().equals(jogador.getUniqueId())) {
            jogador.sendMessage("§cVocê não pode colocar recompensa na sua própria cabeça.");
            return;
        }
        if (plugin.getConfig().getBoolean("anti-farm.mesmo-cla", true)
                && plugin.getClasIntegration().mesmoCla(jogador.getUniqueId(), alvo.getUniqueId())) {
            jogador.sendMessage("§cVocê não pode colocar recompensa num colega de clã.");
            return;
        }

        double valor;
        try {
            valor = Double.parseDouble(args[2].replace(",", "."));
        } catch (NumberFormatException e) {
            jogador.sendMessage("§cValor inválido. Use um número, ex: §f500§c.");
            return;
        }
        double minimo = plugin.getConfig().getDouble("valor-minimo", 100);
        if (!Double.isFinite(valor) || valor < minimo) { // parseDouble aceita NaN/Infinity, e NaN passa por qualquer comparação com zero
            jogador.sendMessage("§cO valor mínimo de uma recompensa é §f" + BountyPlugin.formatarValor(minimo) + "§c.");
            return;
        }
        if (!econ.has(jogador, valor)) {
            jogador.sendMessage("§cSaldo insuficiente. Seu saldo: §f" + BountyPlugin.formatarValor(econ.getBalance(jogador)));
            return;
        }

        double taxa = valor * Math.max(0, Math.min(100, plugin.getConfig().getDouble("taxa-percentual", 10))) / 100.0;
        double liquido = valor - taxa;

        econ.withdrawPlayer(jogador, valor);
        String nomeAlvo = alvo.getName() != null ? alvo.getName() : args[1];
        Recompensa recompensa = plugin.getBountyManager().adicionar(alvo.getUniqueId(), nomeAlvo,
                jogador.getUniqueId(), jogador.getName(), liquido);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), valor, false, "Bounty em " + nomeAlvo);

        jogador.sendMessage("§aVocê ofereceu §f" + BountyPlugin.formatarValor(liquido) + " §apela cabeça de §f" + nomeAlvo
                + (taxa > 0 ? " §7(taxa: " + BountyPlugin.formatarValor(taxa) + ")" : "")
                + "§a. Recompensa total agora: §f" + BountyPlugin.formatarValor(recompensa.getTotal()) + "§a.");

        Player alvoOnline = alvo.getPlayer();
        if (alvoOnline != null) {
            alvoOnline.sendMessage("§c⚠ Alguém colocou uma recompensa pela sua cabeça! Total: §f"
                    + BountyPlugin.formatarValor(recompensa.getTotal()));
        }

        double anunciar = plugin.getConfig().getDouble("anunciar-a-partir-de", 1000);
        if (anunciar > 0 && recompensa.getTotal() >= anunciar) {
            Bukkit.broadcastMessage("§4§l☠ §cRecompensa de §f" + BountyPlugin.formatarValor(recompensa.getTotal())
                    + " §cpela cabeça de §f" + nomeAlvo + "§c! §7(/bounty lista)");
        }
    }

    private void lista(CommandSender sender) {
        List<Recompensa> ranking = plugin.getBountyManager().ranking();
        if (ranking.isEmpty()) {
            sender.sendMessage("§7Nenhuma recompensa ativa. Ofereça uma com §f/bounty colocar <jogador> <valor>§7.");
            return;
        }
        sender.sendMessage("§4§l--- Recompensas ---");
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Recompensa r = ranking.get(i);
            boolean online = Bukkit.getPlayer(r.getAlvo()) != null;
            sender.sendMessage("§f" + (i + 1) + "º " + r.getNomeAlvo() + (online ? " §a●" : " §8●") + " §7- §f"
                    + BountyPlugin.formatarValor(r.getTotal()) + " §8(" + r.getContribuicoes().size() + " contribuinte(s))");
        }
    }

    private void ver(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/bounty ver <jogador>");
            return;
        }
        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
        Recompensa r = plugin.getBountyManager().get(alvo.getUniqueId());
        if (r == null) {
            sender.sendMessage("§7Não há recompensa pela cabeça de §f" + args[1] + "§7.");
            return;
        }
        sender.sendMessage("§cRecompensa pela cabeça de §f" + r.getNomeAlvo() + "§c: §f" + BountyPlugin.formatarValor(r.getTotal()));
        if (sender.hasPermission("bounty.admin")) {
            for (Map.Entry<UUID, Double> e : r.getContribuicoes().entrySet()) {
                sender.sendMessage("  §7" + r.getNomesContribuintes().get(e.getKey()) + ": §f" + BountyPlugin.formatarValor(e.getValue()));
            }
        }
        int dias = plugin.getConfig().getInt("expira-dias", 7);
        if (dias > 0) {
            long expiraEm = r.getAtualizadaEm() + dias * 86_400_000L - System.currentTimeMillis();
            sender.sendMessage("§8Se ninguém cobrar, é devolvida em " + BountyPlugin.formatarTempo(expiraEm) + ".");
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bounty.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("remover")) {
            OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[2]);
            Recompensa r = plugin.getBountyManager().get(alvo.getUniqueId());
            if (r == null) {
                sender.sendMessage("§cNão há recompensa pela cabeça de §f" + args[2] + "§c.");
                return;
            }
            plugin.getBountyManager().devolver(r, "removida pela staff");
            sender.sendMessage("§aRecompensa removida e devolvida aos contribuintes.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aConfiguração recarregada.");
            return;
        }
        sender.sendMessage("§cUso: §f/bounty admin remover <jogador> §7| §f/bounty admin reload");
    }

    private void ajuda(CommandSender sender) {
        sender.sendMessage("§4§l--- Bounty ---");
        sender.sendMessage("§f/bounty colocar <jogador> <valor> §7- oferece recompensa pela cabeça de alguém");
        sender.sendMessage("§f/bounty lista §7- maiores recompensas ativas");
        sender.sendMessage("§f/bounty ver <jogador> §7- recompensa de um jogador");
        if (sender.hasPermission("bounty.admin")) {
            sender.sendMessage("§f/bounty admin remover <jogador> §7- (staff) devolve a recompensa aos contribuintes");
            sender.sendMessage("§f/bounty admin reload §7- (staff) recarrega o config.yml");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.addAll(List.of("colocar", "lista", "ver"));
            if (sender.hasPermission("bounty.admin")) opcoes.add("admin");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("colocar") || args[0].equalsIgnoreCase("ver"))) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            opcoes.addAll(List.of("remover", "reload"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remover")) {
            plugin.getBountyManager().ranking().forEach(r -> opcoes.add(r.getNomeAlvo()));
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
