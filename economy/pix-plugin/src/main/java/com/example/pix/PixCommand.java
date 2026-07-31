package com.example.pix;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PixCommand implements CommandExecutor, TabCompleter {

    private final PixPlugin plugin;

    public PixCommand(PixPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(cor("§cSomente jogadores podem usar o Pix."));
            return true;
        }

        Player jogador = (Player) sender;

        if (plugin.getEconomy() == null) {
            jogador.sendMessage(cor("§cO sistema de economia (Vault) não está disponível no servidor."));
            return true;
        }

        if (args.length == 0) {
            enviarAjuda(jogador);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] resto = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "pagar" -> pagar(jogador, resto);
            case "receber", "cobrar" -> receber(jogador, resto);
            case "aceitar" -> aceitar(jogador, resto);
            case "recusar" -> recusar(jogador, resto);
            case "pendentes" -> pendentes(jogador);
            case "cancelar" -> cancelar(jogador, resto);
            case "historico", "extrato" -> historico(jogador, resto);
            case "qrcode" -> qrcode(jogador, resto);
            default -> enviarAjuda(jogador);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // /pix pagar <jogador> <valor> [motivo]
    // ---------------------------------------------------------------------
    private void pagar(Player pagador, String[] args) {
        if (args.length < 2) {
            pagador.sendMessage(cor("§cUso: §f/pix pagar <jogador> <valor> [motivo]"));
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[0]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            pagador.sendMessage(cor("§cJogador \"" + args[0] + "\" não encontrado."));
            return;
        }

        if (alvo.getUniqueId().equals(pagador.getUniqueId())) {
            pagador.sendMessage(cor("§cVocê não pode pagar a si mesmo."));
            return;
        }

        Double valor = parseValor(pagador, args[1]);
        if (valor == null) return;

        String motivo = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;

        Economy econ = plugin.getEconomy();
        if (!econ.has(pagador, valor)) {
            pagador.sendMessage(cor("§cSaldo insuficiente. Seu saldo atual: §f" + formatarValor(econ.getBalance(pagador))));
            return;
        }

        econ.withdrawPlayer(pagador, valor);
        econ.depositPlayer(alvo, valor);
        plugin.getChargeManager().registrarTransacao(pagador.getUniqueId(), alvo.getUniqueId(), valor, "Pagamento");

        pagador.sendMessage(cor("§aVocê pagou §f" + formatarValor(valor) + " §apara §f" + alvo.getName()
                + (motivo != null ? " §7(" + motivo + ")" : "")));

        if (alvo.isOnline()) {
            Player alvoOnline = alvo.getPlayer();
            alvoOnline.sendMessage(cor("§aVocê recebeu §f" + formatarValor(valor) + " §ade §f" + pagador.getName()
                    + (motivo != null ? " §7(" + motivo + ")" : "")));
        }
    }

    // ---------------------------------------------------------------------
    // /pix receber <jogador> <valor> [motivo]   (envia uma cobrança)
    // ---------------------------------------------------------------------
    private void receber(Player cobrador, String[] args) {
        if (args.length < 2) {
            cobrador.sendMessage(cor("§cUso: §f/pix receber <jogador> <valor> [motivo]"));
            return;
        }

        Player alvo = Bukkit.getPlayerExact(args[0]);
        if (alvo == null) {
            cobrador.sendMessage(cor("§cJogador \"" + args[0] + "\" não está online. Cobranças só podem ser enviadas para quem está no servidor."));
            return;
        }

        if (alvo.getUniqueId().equals(cobrador.getUniqueId())) {
            cobrador.sendMessage(cor("§cVocê não pode cobrar a si mesmo."));
            return;
        }

        if (plugin.getChargeManager().existeCobranca(cobrador.getUniqueId(), alvo.getUniqueId())) {
            cobrador.sendMessage(cor("§cVocê já tem uma cobrança pendente para §f" + alvo.getName()
                    + "§c. Aguarde ele responder ou ela expirar (5 min) antes de enviar outra."));
            return;
        }

        Double valor = parseValor(cobrador, args[1]);
        if (valor == null) return;

        String motivo = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;

        PixCharge charge = plugin.getChargeManager().criarCobranca(cobrador.getUniqueId(), alvo.getUniqueId(), valor, motivo);

        cobrador.sendMessage(cor("§aCobrança enviada para §f" + alvo.getName()
                + " §ano valor de §f" + formatarValor(valor) + " §a(expira em 5 min)"));

        Component aceitar = Component.text("[ACEITAR]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pix aceitar " + cobrador.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para pagar " + formatarValor(valor))));

        Component recusar = Component.text("[RECUSAR]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pix recusar " + cobrador.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para recusar")));

        Component mensagem = Component.text(alvo.getName() + " está te cobrando " + formatarValor(valor)
                        + (motivo != null ? " (" + motivo + ")" : "") + "  ")
                .color(NamedTextColor.YELLOW)
                .append(aceitar)
                .append(Component.text("  "))
                .append(recusar);

        alvo.sendMessage(mensagem);
    }

    // ---------------------------------------------------------------------
    // /pix aceitar <jogador>
    // ---------------------------------------------------------------------
    private void aceitar(Player jogador, String[] args) {
        PixCharge charge = obterCobrancaValida(jogador, args, true);
        if (charge == null) return;

        Economy econ = plugin.getEconomy();
        if (!econ.has(jogador, charge.getValor())) {
            jogador.sendMessage(cor("§cSaldo insuficiente para aceitar essa cobrança. Você precisa de §f"
                    + formatarValor(charge.getValor())));
            return;
        }

        OfflinePlayer cobrador = Bukkit.getOfflinePlayer(charge.getCobradorId());

        econ.withdrawPlayer(jogador, charge.getValor());
        econ.depositPlayer(cobrador, charge.getValor());
        plugin.getChargeManager().removerCobranca(charge.getCobradorId(), charge.getCobradoId());
        plugin.getChargeManager().registrarTransacao(jogador.getUniqueId(), cobrador.getUniqueId(), charge.getValor(), "Cobrança aceita");

        jogador.sendMessage(cor("§aVocê pagou a cobrança de §f" + cobrador.getName() + " §a(§f" + formatarValor(charge.getValor()) + "§a)"));

        if (cobrador.isOnline()) {
            cobrador.getPlayer().sendMessage(cor("§a" + jogador.getName() + " aceitou e pagou sua cobrança de §f"
                    + formatarValor(charge.getValor())));
        }
    }

    // ---------------------------------------------------------------------
    // /pix recusar <jogador>
    // ---------------------------------------------------------------------
    private void recusar(Player jogador, String[] args) {
        PixCharge charge = obterCobrancaValida(jogador, args, true);
        if (charge == null) return;

        plugin.getChargeManager().removerCobranca(charge.getCobradorId(), charge.getCobradoId());

        OfflinePlayer cobrador = Bukkit.getOfflinePlayer(charge.getCobradorId());
        jogador.sendMessage(cor("§eVocê recusou a cobrança de §f" + cobrador.getName() + "§e."));

        if (cobrador.isOnline()) {
            cobrador.getPlayer().sendMessage(cor("§c" + jogador.getName() + " recusou sua cobrança."));
        }
    }

    // ---------------------------------------------------------------------
    // /pix cancelar <jogador>   (quem enviou a cobrança desiste dela)
    // ---------------------------------------------------------------------
    private void cancelar(Player jogador, String[] args) {
        PixCharge charge = obterCobrancaValida(jogador, args, false);
        if (charge == null) return;

        plugin.getChargeManager().removerCobranca(charge.getCobradorId(), charge.getCobradoId());

        OfflinePlayer alvo = Bukkit.getOfflinePlayer(charge.getCobradoId());
        jogador.sendMessage(cor("§eCobrança para §f" + alvo.getName() + " §ecancelada."));

        if (alvo.isOnline()) {
            alvo.getPlayer().sendMessage(cor("§7A cobrança de " + jogador.getName() + " foi cancelada."));
        }
    }

    // ---------------------------------------------------------------------
    // /pix pendentes
    // ---------------------------------------------------------------------
    private void pendentes(Player jogador) {
        List<PixCharge> enviadas = plugin.getChargeManager().cobrancasEnviadasPor(jogador.getUniqueId());
        List<PixCharge> recebidas = plugin.getChargeManager().cobrancasRecebidasPor(jogador.getUniqueId());

        if (enviadas.isEmpty() && recebidas.isEmpty()) {
            jogador.sendMessage(cor("§7Você não tem cobranças pendentes."));
            return;
        }

        if (!recebidas.isEmpty()) {
            jogador.sendMessage(cor("§e§lCobranças que você recebeu:"));
            for (PixCharge c : recebidas) {
                OfflinePlayer cobrador = Bukkit.getOfflinePlayer(c.getCobradorId());
                jogador.sendMessage(cor("  §f" + cobrador.getName() + " §7- §f" + formatarValor(c.getValor())
                        + (c.getMotivo() != null ? " §7(" + c.getMotivo() + ")" : "")
                        + "  §a/pix aceitar " + cobrador.getName() + " §7| §c/pix recusar " + cobrador.getName()));
            }
        }

        if (!enviadas.isEmpty()) {
            jogador.sendMessage(cor("§e§lCobranças que você enviou (aguardando):"));
            for (PixCharge c : enviadas) {
                OfflinePlayer alvo = Bukkit.getOfflinePlayer(c.getCobradoId());
                jogador.sendMessage(cor("  §f" + alvo.getName() + " §7- §f" + formatarValor(c.getValor())
                        + (c.getMotivo() != null ? " §7(" + c.getMotivo() + ")" : "")
                        + "  §c/pix cancelar " + alvo.getName()));
            }
        }
    }

    // ---------------------------------------------------------------------
    // /pix historico [quantidade]
    // ---------------------------------------------------------------------
    private void historico(Player jogador, String[] args) {
        int quantidade = 10;
        if (args.length > 0) {
            try {
                quantidade = Math.max(1, Math.min(20, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
                // mantém o padrão de 10
            }
        }

        List<TransacaoRegistro> registros = plugin.getChargeManager().getHistorico(jogador.getUniqueId(), quantidade);

        if (registros.isEmpty()) {
            jogador.sendMessage(cor("§7Você ainda não tem transações no histórico."));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
        jogador.sendMessage(cor("§e§lÚltimas transações:"));
        for (TransacaoRegistro r : registros) {
            boolean recebeu = r.getPara().equals(jogador.getUniqueId());
            OfflinePlayer outro = Bukkit.getOfflinePlayer(recebeu ? r.getDe() : r.getPara());
            String seta = recebeu ? "§a+" : "§c-";
            String prep = recebeu ? "de" : "para";

            jogador.sendMessage(cor("  §7[" + sdf.format(new Date(r.getData())) + "] " + seta
                    + formatarValor(r.getValor()) + " §7" + prep + " §f" + outro.getName()
                    + " §7(" + r.getTipo() + ")"));
        }
    }

    // ---------------------------------------------------------------------
    // /pix qrcode <valor> [motivo]   (mensagem clicável que qualquer um pode clicar pra te pagar)
    // ---------------------------------------------------------------------
    private void qrcode(Player jogador, String[] args) {
        if (args.length < 1) {
            jogador.sendMessage(cor("§cUso: §f/pix qrcode <valor> [motivo]"));
            return;
        }

        Double valor = parseValor(jogador, args[0]);
        if (valor == null) return;

        String motivo = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;

        Component botao = Component.text("[ PAGAR " + formatarValor(valor) + " ]")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/pix pagar " + jogador.getName() + " " + valor))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para pagar " + jogador.getName())));

        Component mensagem = Component.text(jogador.getName() + " está pedindo pagamentos"
                        + (motivo != null ? " (" + motivo + ")" : "") + ":  ")
                .color(NamedTextColor.AQUA)
                .append(botao);

        Bukkit.broadcast(mensagem);
    }

    // ---------------------------------------------------------------------
    // Ajuda
    // ---------------------------------------------------------------------
    private void enviarAjuda(Player jogador) {
        jogador.sendMessage(cor("§e§l--- Comandos do Pix ---"));
        jogador.sendMessage(cor("§f/pix pagar <jogador> <valor> [motivo] §7- paga alguém direto"));
        jogador.sendMessage(cor("§f/pix receber <jogador> <valor> [motivo] §7- cobra alguém (expira em 5 min)"));
        jogador.sendMessage(cor("§f/pix aceitar <jogador> §7- aceita e paga a cobrança desse jogador"));
        jogador.sendMessage(cor("§f/pix recusar <jogador> §7- recusa a cobrança desse jogador"));
        jogador.sendMessage(cor("§f/pix cancelar <jogador> §7- cancela a cobrança que você enviou a esse jogador"));
        jogador.sendMessage(cor("§f/pix pendentes §7- lista cobranças em aberto"));
        jogador.sendMessage(cor("§f/pix historico [qtd] §7- suas últimas transações"));
        jogador.sendMessage(cor("§f/pix qrcode <valor> [motivo] §7- anuncia um pagamento pra qualquer um clicar"));
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------
    private PixCharge obterCobrancaValida(Player jogador, String[] args, boolean deveSerCobrado) {
        if (args.length < 1) {
            jogador.sendMessage(cor("§cUso: §f/pix " + (deveSerCobrado ? "aceitar|recusar" : "cancelar") + " <jogador>"));
            return null;
        }

        OfflinePlayer outro = Bukkit.getOfflinePlayer(args[0]);
        if (!outro.hasPlayedBefore() && !outro.isOnline()) {
            jogador.sendMessage(cor("§cJogador \"" + args[0] + "\" não encontrado."));
            return null;
        }

        PixCharge charge = deveSerCobrado
                ? plugin.getChargeManager().getCobranca(outro.getUniqueId(), jogador.getUniqueId())
                : plugin.getChargeManager().getCobranca(jogador.getUniqueId(), outro.getUniqueId());

        if (charge == null) {
            jogador.sendMessage(cor("§cVocê não tem nenhuma cobrança " + (deveSerCobrado ? "de" : "enviada para") + " §f"
                    + outro.getName() + "§c."));
            return null;
        }

        return charge;
    }

    private Double parseValor(Player jogador, String texto) {
        double valor;
        try {
            valor = Double.parseDouble(texto.replace(",", "."));
        } catch (NumberFormatException e) {
            jogador.sendMessage(cor("§cValor inválido. Use um número, ex: 100.50"));
            return null;
        }
        if (valor <= 0) {
            jogador.sendMessage(cor("§cO valor deve ser maior que zero."));
            return null;
        }
        return valor;
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    private Component cor(String texto) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(texto);
    }

    // ---------------------------------------------------------------------
    // Tab completion
    // ---------------------------------------------------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();

        if (args.length == 1) {
            opcoes.addAll(Arrays.asList("pagar", "receber", "aceitar", "recusar", "pendentes", "cancelar", "historico", "qrcode"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("pagar") || sub.equals("receber") || sub.equals("cobrar")
                    || sub.equals("aceitar") || sub.equals("recusar") || sub.equals("cancelar")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    opcoes.add(p.getName());
                }
            }
        }

        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
