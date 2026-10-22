package com.example.clas.command;

import com.example.clas.ClasPlugin;
import com.example.clas.manager.ClaManager;
import com.example.clas.model.Cargo;
import com.example.clas.model.Cla;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /cla — criar, convidar, aceitar, recusar, sair, expulsar, promover, rebaixar, transferir,
 * desfazer, info, membros, top, banco, depositar, sacar, fogoamigo, c (chat).
 * /cc &lt;mensagem&gt; é atalho pro chat do clã. Staff: /cla admin &lt;desfazer|reload&gt;.
 */
public class ClaCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ClasPlugin plugin;

    public ClaCommand(ClasPlugin plugin) {
        this.plugin = plugin;
    }

    private ClaManager clas() {
        return plugin.getClaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cc")) {
            if (sender instanceof Player jogador) chat(jogador, args);
            return true;
        }
        if (args.length == 0) {
            ajuda(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] resto = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "info" -> info(sender, resto);
            case "top", "lista" -> top(sender);
            case "admin" -> admin(sender, resto);
            default -> {
                if (!(sender instanceof Player jogador)) {
                    sender.sendMessage("§cSó jogadores podem usar esse comando.");
                    return true;
                }
                switch (sub) {
                    case "criar" -> criar(jogador, resto);
                    case "convidar" -> convidar(jogador, resto);
                    case "aceitar" -> aceitar(jogador, resto);
                    case "recusar" -> recusar(jogador, resto);
                    case "sair" -> sair(jogador);
                    case "expulsar" -> expulsar(jogador, resto);
                    case "promover" -> mudarCargo(jogador, resto, true);
                    case "rebaixar" -> mudarCargo(jogador, resto, false);
                    case "transferir" -> transferir(jogador, resto);
                    case "desfazer" -> desfazer(jogador, resto);
                    case "membros" -> membros(jogador);
                    case "banco" -> banco(jogador);
                    case "depositar" -> depositar(jogador, resto);
                    case "sacar" -> sacar(jogador, resto);
                    case "fogoamigo" -> fogoAmigo(jogador);
                    case "c", "chat" -> chat(jogador, resto);
                    default -> ajuda(sender);
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Criar / entrar / sair
    // ---------------------------------------------------------------------
    private void criar(Player jogador, String[] args) {
        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/cla criar <tag> <nome>");
            return;
        }
        if (clas().getClaDe(jogador.getUniqueId()) != null) {
            jogador.sendMessage("§cVocê já está num clã. Saia dele antes de criar outro.");
            return;
        }
        String tag = args[0];
        if (!tag.matches("[A-Za-z0-9]{2,5}")) {
            jogador.sendMessage("§cA tag precisa ter de 2 a 5 letras ou números (sem acento nem espaço).");
            return;
        }
        if (clas().get(tag) != null) {
            jogador.sendMessage("§cJá existe um clã com a tag §f" + tag + "§c.");
            return;
        }
        String nome = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (nome.length() < 3 || nome.length() > 24) {
            jogador.sendMessage("§cO nome do clã precisa ter de 3 a 24 caracteres.");
            return;
        }

        double custo = Math.max(0, plugin.getConfig().getDouble("criacao.custo", 5000));
        if (custo > 0) {
            Economy econ = exigirEconomia(jogador);
            if (econ == null) return;
            if (!econ.has(jogador, custo)) {
                jogador.sendMessage("§cCriar um clã custa §f" + ClasPlugin.formatarValor(custo) + "§c. Seu saldo: §f"
                        + ClasPlugin.formatarValor(econ.getBalance(jogador)));
                return;
            }
            econ.withdrawPlayer(jogador, custo);
            plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), custo, false, "Criação do clã " + tag);
        }

        Cla cla = clas().criar(tag, ChatColorStrip.limpar(nome), jogador.getUniqueId(), jogador.getName());
        Bukkit.broadcastMessage("§a" + jogador.getName() + " fundou o clã " + plugin.tagFormatada(cla) + " §f" + cla.getNome() + "§a!");
        jogador.sendMessage("§7Convide membros com §f/cla convidar <jogador>§7.");
    }

    private void convidar(Player jogador, String[] args) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        if (!cla.getCargo(jogador.getUniqueId()).podeGerenciar()) {
            jogador.sendMessage("§cSó o líder e os oficiais podem convidar.");
            return;
        }
        if (args.length < 1) {
            jogador.sendMessage("§cUso: §f/cla convidar <jogador>");
            return;
        }
        Player alvo = Bukkit.getPlayerExact(args[0]);
        if (alvo == null) {
            jogador.sendMessage("§cO jogador precisa estar online pra receber o convite.");
            return;
        }
        if (clas().getClaDe(alvo.getUniqueId()) != null) {
            jogador.sendMessage("§c" + alvo.getName() + " já está num clã.");
            return;
        }
        int maximo = plugin.getConfig().getInt("membros-maximo", 10);
        if (cla.getMembros().size() >= maximo) {
            jogador.sendMessage("§cO clã já tem o máximo de §f" + maximo + " §cmembros.");
            return;
        }

        clas().convidar(cla, alvo.getUniqueId());
        int minutos = plugin.getConfig().getInt("convite-expira-minutos", 5);
        jogador.sendMessage("§aConvite enviado pra §f" + alvo.getName() + "§a (vale por " + minutos + " min).");

        Component aceitar = Component.text("[ACEITAR]").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/cla aceitar " + cla.getTag()))
                .hoverEvent(HoverEvent.showText(Component.text("Entrar no clã " + cla.getTag())));
        Component recusar = Component.text("[RECUSAR]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/cla recusar " + cla.getTag()))
                .hoverEvent(HoverEvent.showText(Component.text("Recusar o convite")));
        alvo.sendMessage(ClasPlugin.legado("§e" + jogador.getName() + " te convidou pro clã " + plugin.tagFormatada(cla)
                + " §f" + cla.getNome() + "§e.  ").append(aceitar).append(Component.text("  ")).append(recusar));
    }

    private void aceitar(Player jogador, String[] args) {
        if (clas().getClaDe(jogador.getUniqueId()) != null) {
            jogador.sendMessage("§cVocê já está num clã.");
            return;
        }
        Cla cla = args.length >= 1 ? clas().get(args[0]) : unicoConvite(jogador);
        if (cla == null) {
            jogador.sendMessage("§cUso: §f/cla aceitar <tag> §7(convites: " + listaConvites(jogador) + "§7)");
            return;
        }
        if (!clas().consumirConvite(jogador.getUniqueId(), cla)) {
            jogador.sendMessage("§cVocê não tem convite válido do clã §f" + cla.getTag() + "§c (expirou ou não existe).");
            return;
        }
        int maximo = plugin.getConfig().getInt("membros-maximo", 10);
        if (cla.getMembros().size() >= maximo) {
            jogador.sendMessage("§cO clã §f" + cla.getTag() + " §cficou cheio enquanto o convite esperava.");
            return;
        }
        clas().adicionarMembro(cla, jogador.getUniqueId(), jogador.getName());
        avisarCla(cla, "§a" + jogador.getName() + " entrou no clã!");
    }

    private void recusar(Player jogador, String[] args) {
        Cla cla = args.length >= 1 ? clas().get(args[0]) : unicoConvite(jogador);
        if (cla == null || !clas().consumirConvite(jogador.getUniqueId(), cla)) {
            jogador.sendMessage("§cVocê não tem esse convite.");
            return;
        }
        jogador.sendMessage("§7Convite do clã §f" + cla.getTag() + " §7recusado.");
    }

    private void sair(Player jogador) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        if (cla.getCargo(jogador.getUniqueId()) == Cargo.LIDER) {
            jogador.sendMessage("§cO líder não pode sair. Passe a liderança com §f/cla transferir <jogador> §cou use §f/cla desfazer§c.");
            return;
        }
        clas().removerMembro(cla, jogador.getUniqueId());
        jogador.sendMessage("§eVocê saiu do clã §f" + cla.getTag() + "§e.");
        avisarCla(cla, "§e" + jogador.getName() + " saiu do clã.");
    }

    private void expulsar(Player jogador, String[] args) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        if (args.length < 1) {
            jogador.sendMessage("§cUso: §f/cla expulsar <jogador>");
            return;
        }
        UUID alvo = membroPorNome(cla, args[0]);
        if (alvo == null) {
            jogador.sendMessage("§c" + args[0] + " não é do seu clã.");
            return;
        }
        Cargo meu = cla.getCargo(jogador.getUniqueId());
        Cargo dele = cla.getCargo(alvo);
        // líder expulsa qualquer um; oficial só expulsa membro comum
        boolean pode = !alvo.equals(jogador.getUniqueId()) && (meu == Cargo.LIDER || (meu == Cargo.OFICIAL && dele == Cargo.MEMBRO));
        if (!pode) {
            jogador.sendMessage("§cVocê não pode expulsar esse jogador.");
            return;
        }
        String nome = cla.getNomes().get(alvo);
        clas().removerMembro(cla, alvo);
        avisarCla(cla, "§c" + nome + " foi expulso do clã por " + jogador.getName() + ".");
        Player online = Bukkit.getPlayer(alvo);
        if (online != null) online.sendMessage("§cVocê foi expulso do clã §f" + cla.getTag() + "§c.");
    }

    private void mudarCargo(Player jogador, String[] args, boolean promover) {
        Cla cla = exigirLider(jogador);
        if (cla == null) return;
        if (args.length < 1) {
            jogador.sendMessage("§cUso: §f/cla " + (promover ? "promover" : "rebaixar") + " <jogador>");
            return;
        }
        UUID alvo = membroPorNome(cla, args[0]);
        if (alvo == null || alvo.equals(jogador.getUniqueId())) {
            jogador.sendMessage("§c" + args[0] + " não é um membro do seu clã (além de você).");
            return;
        }
        Cargo atual = cla.getCargo(alvo);
        Cargo novo = promover ? Cargo.OFICIAL : Cargo.MEMBRO;
        if (atual == novo) {
            jogador.sendMessage("§c" + cla.getNomes().get(alvo) + " já é " + novo.getExibicao().substring(2).toLowerCase(Locale.ROOT) + ".");
            return;
        }
        clas().definirCargo(cla, alvo, novo);
        avisarCla(cla, "§e" + cla.getNomes().get(alvo) + " agora é " + novo.getExibicao() + "§e.");
    }

    private void transferir(Player jogador, String[] args) {
        Cla cla = exigirLider(jogador);
        if (cla == null) return;
        if (args.length < 1) {
            jogador.sendMessage("§cUso: §f/cla transferir <jogador>");
            return;
        }
        UUID alvo = membroPorNome(cla, args[0]);
        if (alvo == null || alvo.equals(jogador.getUniqueId())) {
            jogador.sendMessage("§c" + args[0] + " não é um membro do seu clã (além de você).");
            return;
        }
        clas().definirCargo(cla, alvo, Cargo.LIDER);
        clas().definirCargo(cla, jogador.getUniqueId(), Cargo.OFICIAL);
        avisarCla(cla, "§6" + cla.getNomes().get(alvo) + " é o novo líder do clã!");
    }

    private void desfazer(Player jogador, String[] args) {
        Cla cla = exigirLider(jogador);
        if (cla == null) return;
        if (args.length < 1 || !args[0].equalsIgnoreCase("confirmar")) {
            jogador.sendMessage("§eIsso apaga o clã §f" + cla.getTag() + " §epra sempre. O banco ("
                    + ClasPlugin.formatarValor(cla.getBanco()) + ") volta pra você. Pra confirmar: §f/cla desfazer confirmar");
            return;
        }
        double banco = cla.getBanco();
        avisarCla(cla, "§cO clã " + cla.getTag() + " foi desfeito pelo líder.");
        clas().desfazer(cla);
        devolverBanco(cla, jogador.getUniqueId(), banco);
        Bukkit.broadcastMessage("§7O clã §f" + cla.getTag() + " §7foi desfeito.");
    }

    // ---------------------------------------------------------------------
    // Informações
    // ---------------------------------------------------------------------
    private void info(CommandSender sender, String[] args) {
        Cla cla;
        if (args.length >= 1) {
            cla = clas().get(args[0]);
        } else if (sender instanceof Player jogador) {
            cla = clas().getClaDe(jogador.getUniqueId());
        } else {
            cla = null;
        }
        if (cla == null) {
            sender.sendMessage("§cUso: §f/cla info <tag> §7(sem tag: o seu clã)");
            return;
        }
        UUID lider = cla.getLider();
        sender.sendMessage("§e§l--- " + cla.getTag() + " · " + cla.getNome() + " ---");
        sender.sendMessage("§7Líder: §f" + cla.getNomes().get(lider) + " §7| Membros: §f" + cla.getMembros().size()
                + "/" + plugin.getConfig().getInt("membros-maximo", 10));
        sender.sendMessage("§7Pontos: §f" + cla.getPontos() + " §7| Abates: §f" + cla.getAbates() + " §7| Mortes: §f" + cla.getMortes()
                + " §7| Bosses: §f" + cla.getBosses() + " §7| Banco: §f" + ClasPlugin.formatarValor(cla.getBanco()));
        if (cla == clas().getClaCampeaoBoss()) {
            sender.sendMessage("§6★ Clã do campeão do último boss");
        }
        sender.sendMessage("§7Fogo amigo: " + (cla.isFogoAmigo() ? "§cligado" : "§adesligado")
                + " §7| Fundado em §f" + FORMATO_DATA.format(Instant.ofEpochMilli(cla.getCriadoEm()).atZone(ZoneId.systemDefault())));
    }

    private void membros(Player jogador) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        jogador.sendMessage("§e§l--- Membros de " + cla.getTag() + " ---");
        for (Cargo cargo : Cargo.values()) {
            for (Map.Entry<UUID, Cargo> m : cla.getMembros().entrySet()) {
                if (m.getValue() != cargo) continue;
                boolean online = Bukkit.getPlayer(m.getKey()) != null;
                jogador.sendMessage((online ? "§a● " : "§8● ") + cargo.getExibicao() + " §f" + cla.getNomes().get(m.getKey()));
            }
        }
    }

    private void top(CommandSender sender) {
        List<Cla> ranking = clas().ranking();
        if (ranking.isEmpty()) {
            sender.sendMessage("§7Nenhum clã ainda. Crie um com §f/cla criar <tag> <nome>§7.");
            return;
        }
        sender.sendMessage("§e§l--- Ranking de clãs ---");
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Cla cla = ranking.get(i);
            sender.sendMessage("§f" + (i + 1) + "º " + plugin.tagFormatada(cla) + " §f" + cla.getNome()
                    + (cla == clas().getClaCampeaoBoss() ? " §6★" : "")
                    + " §7- §f" + cla.getPontos() + " §7pontos §8(" + cla.getAbates() + " abates + " + cla.getPontosBoss()
                    + " de boss)§7, §f" + cla.getMembros().size() + " §7membros");
        }
        sender.sendMessage("§8Cada abate vale 1 ponto; derrotar o boss vale " + plugin.getConfig().getInt("boss.pontos-campeao", 10)
                + ", e ajudar bastante vale " + plugin.getConfig().getInt("boss.pontos-participacao", 3) + ". §6★ §8= clã do campeão do boss.");
    }

    // ---------------------------------------------------------------------
    // Banco
    // ---------------------------------------------------------------------
    private void banco(Player jogador) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        jogador.sendMessage("§e§lBanco do clã " + cla.getTag() + ": §f" + ClasPlugin.formatarValor(cla.getBanco()));
        if (cla.getExtrato().isEmpty()) {
            jogador.sendMessage("§7Nenhuma movimentação ainda. §f/cla depositar <valor>");
            return;
        }
        for (Cla.Lancamento l : cla.getExtrato().subList(0, Math.min(10, cla.getExtrato().size()))) {
            jogador.sendMessage("  §7[" + FORMATO_DATA.format(Instant.ofEpochMilli(l.data()).atZone(ZoneId.systemDefault())) + "] "
                    + (l.valor() >= 0 ? "§a+" : "§c-") + ClasPlugin.formatarValor(Math.abs(l.valor()))
                    + " §7" + l.tipo() + " por §f" + l.jogador());
        }
    }

    private void depositar(Player jogador, String[] args) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        Economy econ = exigirEconomia(jogador);
        if (econ == null) return;
        Double valor = lerValor(jogador, args, "depositar");
        if (valor == null) return;
        if (!econ.has(jogador, valor)) {
            jogador.sendMessage("§cSaldo insuficiente. Seu saldo: §f" + ClasPlugin.formatarValor(econ.getBalance(jogador)));
            return;
        }
        econ.withdrawPlayer(jogador, valor);
        cla.setBanco(cla.getBanco() + valor);
        cla.lancar(jogador.getName(), "depósito", valor);
        clas().salvar();
        plugin.getPixIntegration().registrarComCla(jogador.getUniqueId(), ClasPlugin.nomeNoExtrato(cla), valor, false, "Depósito no banco do clã");
        avisarCla(cla, "§a" + jogador.getName() + " depositou §f" + ClasPlugin.formatarValor(valor)
                + " §ano banco do clã. Saldo: §f" + ClasPlugin.formatarValor(cla.getBanco()));
    }

    private void sacar(Player jogador, String[] args) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        Cargo cargo = cla.getCargo(jogador.getUniqueId());
        boolean oficialPode = plugin.getConfig().getBoolean("banco.oficial-pode-sacar", false);
        if (cargo != Cargo.LIDER && !(cargo == Cargo.OFICIAL && oficialPode)) {
            jogador.sendMessage("§cSó o líder" + (oficialPode ? " e os oficiais podem" : " pode") + " sacar do banco do clã.");
            return;
        }
        Economy econ = exigirEconomia(jogador);
        if (econ == null) return;
        Double valor = lerValor(jogador, args, "sacar");
        if (valor == null) return;
        if (valor > cla.getBanco() + 0.000001) {
            jogador.sendMessage("§cO banco do clã só tem §f" + ClasPlugin.formatarValor(cla.getBanco()) + "§c.");
            return;
        }
        cla.setBanco(cla.getBanco() - valor);
        cla.lancar(jogador.getName(), "saque", -valor);
        clas().salvar();
        econ.depositPlayer(jogador, valor);
        plugin.getPixIntegration().registrarComCla(jogador.getUniqueId(), ClasPlugin.nomeNoExtrato(cla), valor, true, "Saque do banco do clã");
        avisarCla(cla, "§e" + jogador.getName() + " sacou §f" + ClasPlugin.formatarValor(valor)
                + " §edo banco do clã. Saldo: §f" + ClasPlugin.formatarValor(cla.getBanco()));
    }

    private void fogoAmigo(Player jogador) {
        Cla cla = exigirLider(jogador);
        if (cla == null) return;
        cla.setFogoAmigo(!cla.isFogoAmigo());
        clas().salvar();
        avisarCla(cla, "§eFogo amigo " + (cla.isFogoAmigo() ? "§cligado §e— membros podem se machucar." : "§adesligado§e."));
    }

    private void chat(Player jogador, String[] args) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return;
        if (args.length == 0) {
            jogador.sendMessage("§cUso: §f/cc <mensagem>");
            return;
        }
        String mensagem = String.join(" ", args);
        Cargo cargo = cla.getCargo(jogador.getUniqueId());
        avisarCla(cla, "§b[Clã] " + cargo.getExibicao() + " §f" + jogador.getName() + "§7: §b" + ChatColorStrip.limpar(mensagem));
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clas.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("desfazer")) {
            Cla cla = clas().get(args[1]);
            if (cla == null) {
                sender.sendMessage("§cClã §f" + args[1] + " §cnão existe.");
                return;
            }
            UUID lider = cla.getLider();
            double banco = cla.getBanco();
            avisarCla(cla, "§cO clã " + cla.getTag() + " foi desfeito pela staff.");
            clas().desfazer(cla);
            devolverBanco(cla, lider, banco);
            sender.sendMessage("§aClã §f" + cla.getTag() + " §adesfeito. Banco (" + ClasPlugin.formatarValor(banco) + ") devolvido ao líder.");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aConfiguração recarregada.");
            return;
        }
        sender.sendMessage("§cUso: §f/cla admin desfazer <tag> §7| §f/cla admin reload");
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------
    private void devolverBanco(Cla cla, UUID lider, double valor) {
        if (valor <= 0 || lider == null) return;
        Economy econ = plugin.getEconomy();
        if (econ != null) {
            econ.depositPlayer(Bukkit.getOfflinePlayer(lider), valor);
            plugin.getPixIntegration().registrarComCla(lider, ClasPlugin.nomeNoExtrato(cla), valor, true, "Clã desfeito");
        } else {
            plugin.getLogger().severe("Economia indisponível: não deu pra devolver " + valor + " do banco do clã pro líder " + lider + ".");
        }
    }

    private Cla exigirCla(Player jogador) {
        Cla cla = clas().getClaDe(jogador.getUniqueId());
        if (cla == null) {
            jogador.sendMessage("§cVocê não está em nenhum clã. Crie um com §f/cla criar <tag> <nome> §cou aceite um convite.");
        }
        return cla;
    }

    private Cla exigirLider(Player jogador) {
        Cla cla = exigirCla(jogador);
        if (cla == null) return null;
        if (cla.getCargo(jogador.getUniqueId()) != Cargo.LIDER) {
            jogador.sendMessage("§cSó o líder do clã pode fazer isso.");
            return null;
        }
        return cla;
    }

    private Economy exigirEconomia(Player jogador) {
        Economy econ = plugin.getEconomy();
        if (econ == null) jogador.sendMessage("§cO sistema de economia (Vault) não está disponível no servidor.");
        return econ;
    }

    private Double lerValor(Player jogador, String[] args, String acao) {
        if (args.length < 1) {
            jogador.sendMessage("§cUso: §f/cla " + acao + " <valor>");
            return null;
        }
        double valor;
        try {
            valor = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            valor = -1;
        }
        if (!Double.isFinite(valor) || valor <= 0) { // parseDouble aceita NaN/Infinity, e NaN passa por qualquer comparação com zero
            jogador.sendMessage("§cValor inválido. Use um número maior que zero, ex: §f500§c.");
            return null;
        }
        return valor;
    }

    private UUID membroPorNome(Cla cla, String nome) {
        for (Map.Entry<UUID, String> e : cla.getNomes().entrySet()) {
            if (e.getValue().equalsIgnoreCase(nome)) return e.getKey();
        }
        OfflinePlayer jogador = Bukkit.getOfflinePlayer(nome);
        return cla.getMembros().containsKey(jogador.getUniqueId()) ? jogador.getUniqueId() : null;
    }

    private Cla unicoConvite(Player jogador) {
        List<String> tags = clas().convitesValidos(jogador.getUniqueId());
        return tags.size() == 1 ? clas().get(tags.get(0)) : null;
    }

    private String listaConvites(Player jogador) {
        List<String> tags = clas().convitesValidos(jogador.getUniqueId());
        return tags.isEmpty() ? "nenhum" : "§f" + String.join("§7, §f", tags);
    }

    private void avisarCla(Cla cla, String mensagem) {
        for (UUID membro : cla.getMembros().keySet()) {
            Player online = Bukkit.getPlayer(membro);
            if (online != null) online.sendMessage(mensagem);
        }
    }

    private void ajuda(CommandSender sender) {
        sender.sendMessage("§e§l--- Clãs ---");
        sender.sendMessage("§f/cla criar <tag> <nome> §7- funda um clã (custa " + ClasPlugin.formatarValor(
                plugin.getConfig().getDouble("criacao.custo", 5000)) + ")");
        sender.sendMessage("§f/cla convidar <jogador> §7| §f/cla aceitar [tag] §7| §f/cla recusar [tag]");
        sender.sendMessage("§f/cla info [tag] §7| §f/cla membros §7| §f/cla top");
        sender.sendMessage("§f/cla banco §7| §f/cla depositar <valor> §7| §f/cla sacar <valor>");
        sender.sendMessage("§f/cc <mensagem> §7- chat do clã");
        sender.sendMessage("§f/cla sair §7| §f/cla expulsar <jogador>");
        sender.sendMessage("§7Líder: §f/cla promover|rebaixar|transferir <jogador> §7| §f/cla fogoamigo §7| §f/cla desfazer");
        if (sender.hasPermission("clas.admin")) {
            sender.sendMessage("§f/cla admin desfazer <tag> §7| §f/cla admin reload");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("cc")) return opcoes;

        if (args.length == 1) {
            opcoes.addAll(List.of("criar", "convidar", "aceitar", "recusar", "sair", "expulsar", "promover", "rebaixar",
                    "transferir", "desfazer", "info", "membros", "top", "banco", "depositar", "sacar", "fogoamigo", "c"));
            if (sender.hasPermission("clas.admin")) opcoes.add("admin");
        } else if (args.length == 2 && sender instanceof Player jogador) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            Cla meu = clas().getClaDe(jogador.getUniqueId());
            switch (sub) {
                case "convidar" -> Bukkit.getOnlinePlayers().stream()
                        .filter(p -> clas().getClaDe(p.getUniqueId()) == null).forEach(p -> opcoes.add(p.getName()));
                case "expulsar", "promover", "rebaixar", "transferir" -> {
                    if (meu != null) {
                        meu.getNomes().forEach((id, nome) -> {
                            if (!id.equals(jogador.getUniqueId())) opcoes.add(nome);
                        });
                    }
                }
                case "aceitar", "recusar" -> opcoes.addAll(clas().convitesValidos(jogador.getUniqueId()));
                case "info" -> clas().ranking().forEach(c -> opcoes.add(c.getTag()));
                case "desfazer" -> opcoes.add("confirmar");
                case "admin" -> {
                    if (sender.hasPermission("clas.admin")) opcoes.addAll(List.of("desfazer", "reload"));
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("desfazer")) {
            clas().ranking().forEach(c -> opcoes.add(c.getTag()));
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }

    /** Tira códigos de cor (§ e &) de texto digitado por jogador, pra ninguém colorir nome de clã ou chat. */
    private static final class ChatColorStrip {
        static String limpar(String texto) {
            return texto.replaceAll("(?i)[§&][0-9A-FK-ORX]", "");
        }
    }
}
