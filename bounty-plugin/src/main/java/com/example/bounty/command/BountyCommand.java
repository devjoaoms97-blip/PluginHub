package com.example.bounty.command;

import com.example.bounty.BountyPlugin;
import com.example.bounty.model.ContratoCla;
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

/** /bounty colocar|cla|lista|ver — staff: /bounty admin remover <jogador> | removercla <tag> | reload. */
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
            case "cla", "clan" -> cla(sender, args);
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

        double valor = lerValor(args[2]);
        double minimo = plugin.getConfig().getDouble("valor-minimo", 100);
        if (valor < minimo) {
            jogador.sendMessage("§cO valor mínimo de uma recompensa é §f" + BountyPlugin.formatarValor(minimo) + "§c.");
            return;
        }
        if (!econ.has(jogador, valor)) {
            jogador.sendMessage("§cSaldo insuficiente. Seu saldo: §f" + BountyPlugin.formatarValor(econ.getBalance(jogador)));
            return;
        }

        double taxa = valor * plugin.getTaxaPercentual() / 100.0;
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
        plugin.getDiscord().anunciar(":skull: Recompensa de **" + BountyPlugin.formatarValor(recompensa.getTotal())
                + "** pela cabeça de **" + nomeAlvo + "**!", recompensa.getTotal());
    }

    /** /bounty cla <tag> <valor-por-abate> <abates> — ou só /bounty cla <tag> pra ver os contratos. */
    private void cla(CommandSender sender, String[] args) {
        if (!plugin.getClasIntegration().disponivel()) {
            sender.sendMessage("§cRecompensa contra clã precisa do ClasPlugin (atualizado) instalado.");
            return;
        }
        if (args.length == 2) {
            verCla(sender, args[1]);
            return;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores podem contratar recompensa.");
            return;
        }
        if (args.length < 4) {
            jogador.sendMessage("§cUso: §f/bounty cla <tag> <valor por abate> <abates> §7(ex: §f/bounty cla ABC 500 10§7 = R$ 500 "
                    + "por membro do ABC morto, até 10 abates) §7| §f/bounty cla <tag> §7pra ver os contratos");
            return;
        }
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            jogador.sendMessage("§cO sistema de economia (Vault) não está disponível no servidor.");
            return;
        }

        String tag = plugin.getClasIntegration().tagExistente(args[1]);
        if (tag == null) {
            jogador.sendMessage("§cNão existe clã com a tag §f" + args[1] + "§c.");
            return;
        }
        if (tag.equalsIgnoreCase(plugin.getClasIntegration().tagDoCla(jogador.getUniqueId()))) {
            jogador.sendMessage("§cVocê não pode contratar recompensa contra o seu próprio clã.");
            return;
        }

        double valorPorAbate = lerValor(args[2]);
        double minimo = plugin.getConfig().getDouble("cla.valor-minimo-por-abate", 100);
        if (valorPorAbate < minimo) {
            jogador.sendMessage("§cO valor mínimo por abate é §f" + BountyPlugin.formatarValor(minimo) + "§c.");
            return;
        }
        int maxAbates = Math.max(1, plugin.getConfig().getInt("cla.abates-maximo", 20));
        int abates;
        try {
            abates = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            abates = 0;
        }
        if (abates < 1 || abates > maxAbates) {
            jogador.sendMessage("§cO número de abates precisa ser de 1 a " + maxAbates + ".");
            return;
        }

        double total = valorPorAbate * abates;
        if (!econ.has(jogador, total)) {
            jogador.sendMessage("§cSaldo insuficiente: o contrato custa §f" + BountyPlugin.formatarValor(total)
                    + " §c(" + abates + " × " + BountyPlugin.formatarValor(valorPorAbate) + "). Seu saldo: §f"
                    + BountyPlugin.formatarValor(econ.getBalance(jogador)));
            return;
        }

        double fator = 1 - plugin.getTaxaPercentual() / 100.0;
        econ.withdrawPlayer(jogador, total);
        ContratoCla contrato = plugin.getBountyManager().criarContrato(tag, jogador.getUniqueId(), jogador.getName(),
                valorPorAbate * fator, abates);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), total, false, "Bounty no clã " + tag);

        jogador.sendMessage("§aContrato criado: §f" + BountyPlugin.formatarValor(contrato.getValorPorAbate())
                + " §apor abate de membro do clã §f" + tag + "§a, até §f" + abates + " §aabates"
                + (fator < 1 ? " §7(taxa: " + BountyPlugin.formatarValor(total * (1 - fator)) + ")" : "") + "§a.");

        double anunciar = plugin.getConfig().getDouble("anunciar-a-partir-de", 1000);
        if (anunciar > 0 && contrato.getRestante() >= anunciar) {
            Bukkit.broadcastMessage("§4§l☠ §cO clã §f" + tag + " §cestá sendo caçado! §f"
                    + BountyPlugin.formatarValor(contrato.getValorPorAbate()) + " §cpor membro morto, até §f" + abates
                    + " §cabates. §7(/bounty cla " + tag + ")");
        }
        plugin.getDiscord().anunciar(":crossed_swords: O clã **" + tag + "** está sendo caçado: **"
                + BountyPlugin.formatarValor(contrato.getValorPorAbate()) + "** por membro morto, até " + abates + " abates.",
                contrato.getRestante());
    }

    private void verCla(CommandSender sender, String tagDigitada) {
        String tag = plugin.getClasIntegration().tagExistente(tagDigitada);
        List<ContratoCla> contratos = plugin.getBountyManager().contratosContra(tag != null ? tag : tagDigitada);
        if (contratos.isEmpty()) {
            sender.sendMessage("§7Não há contrato contra o clã §f" + tagDigitada + "§7.");
            return;
        }
        sender.sendMessage("§4§l--- Contratos contra o clã " + contratos.get(0).getTag() + " ---");
        int dias = plugin.getConfig().getInt("expira-dias", 7);
        for (ContratoCla c : contratos) {
            String expira = dias > 0
                    ? " §8(vence em " + BountyPlugin.formatarTempo(c.getCriadoEm() + dias * 86_400_000L - System.currentTimeMillis()) + ")"
                    : "";
            sender.sendMessage("  §f" + BountyPlugin.formatarValor(c.getValorPorAbate()) + " §7por abate, §f"
                    + c.getAbatesRestantes() + " §7restante(s)"
                    + (sender.hasPermission("bounty.admin") ? " §7— de §f" + c.getNomeContratante() : "") + expira);
        }
        sender.sendMessage("§8O contrato mais antigo paga primeiro. Mate um membro do clã pra cobrar.");
    }

    /** Lê um valor digitado; -1 se inválido (parseDouble aceita NaN/Infinity, e NaN passa por qualquer comparação). */
    private double lerValor(String texto) {
        try {
            double valor = Double.parseDouble(texto.replace(",", "."));
            return Double.isFinite(valor) ? valor : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void lista(CommandSender sender) {
        List<Recompensa> ranking = plugin.getBountyManager().ranking();
        List<Map.Entry<String, Double>> clas = plugin.getBountyManager().rankingClas();
        if (ranking.isEmpty() && clas.isEmpty()) {
            sender.sendMessage("§7Nenhuma recompensa ativa. Ofereça uma com §f/bounty colocar <jogador> <valor>§7.");
            return;
        }
        if (!ranking.isEmpty()) {
            sender.sendMessage("§4§l--- Recompensas ---");
            for (int i = 0; i < Math.min(10, ranking.size()); i++) {
                Recompensa r = ranking.get(i);
                boolean online = Bukkit.getPlayer(r.getAlvo()) != null;
                sender.sendMessage("§f" + (i + 1) + "º " + r.getNomeAlvo() + (online ? " §a●" : " §8●") + " §7- §f"
                        + BountyPlugin.formatarValor(r.getTotal()) + " §8(" + r.getContribuicoes().size() + " contribuinte(s))");
            }
        }
        if (!clas.isEmpty()) {
            sender.sendMessage("§4§l--- Clãs caçados ---");
            for (int i = 0; i < Math.min(5, clas.size()); i++) {
                Map.Entry<String, Double> e = clas.get(i);
                sender.sendMessage("§f" + e.getKey() + " §7- §f" + BountyPlugin.formatarValor(e.getValue())
                        + " §7retidos §8(/bounty cla " + e.getKey() + ")");
            }
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
        if (args.length >= 3 && args[1].equalsIgnoreCase("removercla")) {
            List<ContratoCla> contratos = plugin.getBountyManager().contratosContra(args[2]);
            if (contratos.isEmpty()) {
                sender.sendMessage("§cNão há contrato contra o clã §f" + args[2] + "§c.");
                return;
            }
            contratos.forEach(c -> plugin.getBountyManager().devolverContrato(c, "removido pela staff"));
            sender.sendMessage("§a" + contratos.size() + " contrato(s) contra o clã §f" + args[2] + " §aremovido(s) e devolvido(s).");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aConfiguração recarregada.");
            return;
        }
        sender.sendMessage("§cUso: §f/bounty admin remover <jogador> §7| §f/bounty admin removercla <tag> §7| §f/bounty admin reload");
    }

    private void ajuda(CommandSender sender) {
        sender.sendMessage("§4§l--- Bounty ---");
        sender.sendMessage("§f/bounty colocar <jogador> <valor> §7- oferece recompensa pela cabeça de alguém");
        sender.sendMessage("§f/bounty cla <tag> <valor por abate> <abates> §7- recompensa por cada membro de um clã morto");
        sender.sendMessage("§f/bounty cla <tag> §7- contratos ativos contra um clã");
        sender.sendMessage("§f/bounty lista §7- maiores recompensas ativas e clãs caçados");
        sender.sendMessage("§f/bounty ver <jogador> §7- recompensa de um jogador");
        if (sender.hasPermission("bounty.admin")) {
            sender.sendMessage("§f/bounty admin remover <jogador> §7- (staff) devolve a recompensa aos contribuintes");
            sender.sendMessage("§f/bounty admin removercla <tag> §7- (staff) devolve os contratos contra um clã");
            sender.sendMessage("§f/bounty admin reload §7- (staff) recarrega o config.yml");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.addAll(List.of("colocar", "cla", "lista", "ver"));
            if (sender.hasPermission("bounty.admin")) opcoes.add("admin");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("colocar") || args[0].equalsIgnoreCase("ver"))) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("cla")) {
            plugin.getBountyManager().rankingClas().forEach(e -> opcoes.add(e.getKey()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            opcoes.addAll(List.of("remover", "removercla", "reload"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remover")) {
            plugin.getBountyManager().ranking().forEach(r -> opcoes.add(r.getNomeAlvo()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("removercla")) {
            plugin.getBountyManager().rankingClas().forEach(e -> opcoes.add(e.getKey()));
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
