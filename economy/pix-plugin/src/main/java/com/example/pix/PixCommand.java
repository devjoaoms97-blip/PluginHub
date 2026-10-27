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
import java.util.UUID;
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
            case "limite" -> limite(jogador);
            case "agendar" -> agendar(jogador, resto);
            case "agendamentos", "agendados" -> agendamentos(jogador, resto);
            case "chave" -> chave(jogador, resto);
            case "admin" -> admin(jogador, resto);
            default -> enviarAjuda(jogador);
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // /pix pagar <jogador> <valor> [motivo]
    // ---------------------------------------------------------------------
    private void pagar(Player pagador, String[] args) {
        if (args.length < 2) {
            pagador.sendMessage(cor("§cUso: §f/pix pagar <jogador|chave> <valor> [motivo]"));
            return;
        }

        OfflinePlayer alvo = resolverAlvo(args[0]);
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

        if (!verificarLimite(pagador, valor)) return;

        double taxa = plugin.calcularTaxaTransacao(valor, pagador);
        double valorLiquido = valor - taxa;

        econ.withdrawPlayer(pagador, valor);
        econ.depositPlayer(alvo, valorLiquido);
        plugin.getChargeManager().registrarTransacao(pagador.getUniqueId(), alvo.getUniqueId(), valor, "Pagamento", taxa);
        plugin.getLimiteManager().registrarEnvio(pagador.getUniqueId(), valor);
        plugin.conferirSuspeito(pagador.getUniqueId(), pagador.getName(), alvo.getUniqueId(), alvo.getName(), valor, "Pagamento");

        pagador.sendMessage(cor("§aVocê pagou §f" + formatarValor(valor) + " §apara §f" + alvo.getName()
                + (motivo != null ? " §7(" + motivo + ")" : "")
                + (taxa > 0 ? " §7(taxa: " + formatarValor(taxa) + ")" : "")));

        if (alvo.isOnline()) {
            Player alvoOnline = alvo.getPlayer();
            alvoOnline.sendMessage(cor("§aVocê recebeu §f" + formatarValor(valorLiquido) + " §ade §f" + pagador.getName()
                    + (motivo != null ? " §7(" + motivo + ")" : "")
                    + (taxa > 0 ? " §7(taxa descontada: " + formatarValor(taxa) + ")" : "")));
            plugin.notificarRecebimento(alvoOnline, valorLiquido, pagador.getName());
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

        Player alvo = resolverAlvo(args[0]).getPlayer();
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

        // a cobrança continua pendente se bater no limite — dá pra aceitar amanhã, se não expirar antes
        if (!verificarLimite(jogador, charge.getValor())) return;

        OfflinePlayer cobrador = Bukkit.getOfflinePlayer(charge.getCobradorId());

        double taxa = plugin.calcularTaxaTransacao(charge.getValor(), jogador);
        double valorLiquido = charge.getValor() - taxa;

        econ.withdrawPlayer(jogador, charge.getValor());
        econ.depositPlayer(cobrador, valorLiquido);
        plugin.getChargeManager().removerCobranca(charge.getCobradorId(), charge.getCobradoId());
        plugin.getChargeManager().registrarTransacao(jogador.getUniqueId(), cobrador.getUniqueId(), charge.getValor(), "Cobrança aceita", taxa);
        plugin.getLimiteManager().registrarEnvio(jogador.getUniqueId(), charge.getValor());
        plugin.conferirSuspeito(jogador.getUniqueId(), jogador.getName(), cobrador.getUniqueId(), cobrador.getName(), charge.getValor(), "Cobrança aceita");

        jogador.sendMessage(cor("§aVocê pagou a cobrança de §f" + cobrador.getName() + " §a(§f" + formatarValor(charge.getValor()) + "§a)"
                + (taxa > 0 ? " §7(taxa: " + formatarValor(taxa) + ")" : "")));

        if (cobrador.isOnline()) {
            cobrador.getPlayer().sendMessage(cor("§a" + jogador.getName() + " aceitou e pagou sua cobrança. Você recebeu §f"
                    + formatarValor(valorLiquido)
                    + (taxa > 0 ? " §7(taxa descontada: " + formatarValor(taxa) + ")" : "")));
            plugin.notificarRecebimento(cobrador.getPlayer(), valorLiquido, jogador.getName());
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

        mostrarExtrato(jogador, jogador.getUniqueId(), quantidade, "§e§lÚltimas transações:", "§7Você ainda não tem transações no histórico.");
    }

    /**
     * O extrato de {@code dono} pra quem pediu ({@code leitor}): o seu no /pix historico, o de
     * qualquer um no /pix admin log. Com taxa, quem pagou vê o valor cheio e a taxa, quem recebeu
     * vê o que entrou de fato e a taxa descontada.
     */
    private void mostrarExtrato(Player leitor, UUID dono, int quantidade, String titulo, String vazio) {
        List<TransacaoRegistro> registros = plugin.getChargeManager().getHistorico(dono, quantidade);

        if (registros.isEmpty()) {
            leitor.sendMessage(cor(vazio));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
        leitor.sendMessage(cor(titulo));
        for (TransacaoRegistro r : registros) {
            boolean recebeu = r.getPara().equals(dono);
            UUID outroId = recebeu ? r.getDe() : r.getPara();
            String seta = recebeu ? "§a+" : "§c-";
            String prep = recebeu ? "de" : "para";

            String nomeOutro;
            if (r.getNomeEntidade() != null) {
                nomeOutro = r.getNomeEntidade();
            } else if (outroId.equals(ChargeManager.ENTIDADE_LOJA)) {
                nomeOutro = "Loja do Servidor";
            } else {
                nomeOutro = Bukkit.getOfflinePlayer(outroId).getName();
            }

            double valor = recebeu ? r.getValor() - r.getTaxa() : r.getValor();
            String taxa = r.getTaxa() > 0 ? " §8[taxa " + formatarValor(r.getTaxa()) + "]" : "";
            leitor.sendMessage(cor("  §7[" + sdf.format(new Date(r.getData())) + "] " + seta
                    + formatarValor(valor) + " §7" + prep + " §f" + nomeOutro
                    + " §7(" + r.getTipo() + ")" + taxa));
        }
    }

    /** O jogador dono da chave Pix, ou o jogador com esse nome. */
    private OfflinePlayer resolverAlvo(String texto) {
        UUID dono = plugin.getChaveManager().dono(texto);
        return dono != null ? Bukkit.getOfflinePlayer(dono) : Bukkit.getOfflinePlayer(texto);
    }

    // ---------------------------------------------------------------------
    // /pix chave [<apelido>|remover]
    // ---------------------------------------------------------------------
    private void chave(Player jogador, String[] args) {
        ChaveManager chaves = plugin.getChaveManager();
        if (args.length == 0) {
            String atual = chaves.chaveDe(jogador.getUniqueId());
            jogador.sendMessage(cor(atual == null
                    ? "§7Você não tem chave Pix. Crie uma com §f/pix chave <apelido>§7: aí te pagam com §f/pix pagar <apelido> <valor>§7."
                    : "§aSua chave Pix: §f" + atual + " §7(§f/pix chave remover§7 apaga)"));
            return;
        }
        if (args[0].equalsIgnoreCase("remover")) {
            jogador.sendMessage(cor(chaves.remover(jogador.getUniqueId()) ? "§aChave Pix removida." : "§cVocê não tem chave Pix."));
            return;
        }
        String erro = chaves.definir(jogador.getUniqueId(), args[0]);
        jogador.sendMessage(cor(erro != null ? "§c" + erro
                : "§aSua chave Pix agora é §f" + args[0].toLowerCase(Locale.ROOT) + "§a. Te pagam com §f/pix pagar "
                + args[0].toLowerCase(Locale.ROOT) + " <valor>§a."));
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
    // /pix limite
    // ---------------------------------------------------------------------
    private void limite(Player jogador) {
        LimiteManager limites = plugin.getLimiteManager();
        if (!limites.temLimite(jogador)) {
            jogador.sendMessage(cor("§aVocê não tem limite diário de transferência."));
            return;
        }

        UUID id = jogador.getUniqueId();
        jogador.sendMessage(cor("§e§lLimite diário do Pix:"));
        jogador.sendMessage(cor("  §7Limite: §f" + formatarValor(limites.getLimiteDiario())));
        jogador.sendMessage(cor("  §7Enviado hoje: §f" + formatarValor(limites.getGastoHoje(id))));
        jogador.sendMessage(cor("  §7Disponível: §a" + formatarValor(limites.getDisponivelHoje(id))));
        jogador.sendMessage(cor("  §8Zera todo dia à meia-noite."));
    }

    // ---------------------------------------------------------------------
    // /pix admin <reload|resetlimite>
    // ---------------------------------------------------------------------
    private void admin(Player jogador, String[] args) {
        if (!jogador.hasPermission("pix.admin")) {
            jogador.sendMessage(cor("§cVocê não tem permissão pra administrar o Pix."));
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("resetlimite")) {
            if (args.length < 2) {
                jogador.sendMessage(cor("§cUso: §f/pix admin resetlimite <jogador>"));
                return;
            }
            OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
            if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
                jogador.sendMessage(cor("§cJogador \"" + args[1] + "\" não encontrado."));
                return;
            }
            plugin.getLimiteManager().resetar(alvo.getUniqueId());
            jogador.sendMessage(cor("§aLimite diário de §f" + alvo.getName() + " §azerado."));
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("log")) {
            if (args.length < 2) {
                jogador.sendMessage(cor("§cUso: §f/pix admin log <jogador> [qtd]"));
                return;
            }
            OfflinePlayer alvo = resolverAlvo(args[1]);
            if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
                jogador.sendMessage(cor("§cJogador \"" + args[1] + "\" não encontrado."));
                return;
            }
            Integer qtd = args.length > 2 ? numero(args[2]) : null;
            mostrarExtrato(jogador, alvo.getUniqueId(), qtd == null ? 20 : Math.max(1, Math.min(20, qtd)),
                    "§e§lExtrato de " + alvo.getName() + ":", "§7" + alvo.getName() + " não tem transações no histórico.");
            return;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("vencer")) {
            AgendamentoManager.Agendamento a = numero(args[1]) == null ? null : plugin.getAgendamentoManager().get(numero(args[1]));
            if (a == null) {
                jogador.sendMessage(cor("§cAgendamento não encontrado."));
                return;
            }
            plugin.getAgendamentoManager().vencerAgora(a);
            jogador.sendMessage(cor("§aO Pix agendado #" + a.getId() + " vence agora (sai na próxima verificação, em até 20 s)."));
            return;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
            jogador.sendMessage(cor("§cUso: §f/pix admin <reload|resetlimite <jogador>|log <jogador> [qtd]|vencer <nº>>"));
            return;
        }

        plugin.reloadConfig();
        double percentual = plugin.getConfig().getDouble("taxa.percentual-transacao", 1.0);
        jogador.sendMessage(cor("§aConfiguração recarregada. §7Taxa de transação atual: §f"
                + String.format(Locale.forLanguageTag("pt-BR"), "%.2f", percentual) + "%"));
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
        jogador.sendMessage(cor("§f/pix limite §7- quanto você ainda pode enviar hoje"));
        jogador.sendMessage(cor("§f/pix agendar <jogador> <valor> <dias> [motivo] §7- paga agora e repete a cada N dias"));
        jogador.sendMessage(cor("§f/pix agendamentos [cancelar <nº>] §7- seus Pix agendados"));
        jogador.sendMessage(cor("§f/pix chave [apelido|remover] §7- sua chave Pix (te pagam pelo apelido)"));
        if (jogador.hasPermission("pix.admin")) {
            jogador.sendMessage(cor("§f/pix admin reload §7- (staff) recarrega o config.yml"));
            jogador.sendMessage(cor("§f/pix admin resetlimite <jogador> §7- (staff) zera o limite diário de alguém"));
            jogador.sendMessage(cor("§f/pix admin log <jogador> [qtd] §7- (staff) extrato de qualquer jogador"));
            jogador.sendMessage(cor("§f/pix admin vencer <nº> §7- (staff) faz um Pix agendado vencer agora"));
        }
    }

    // ---------------------------------------------------------------------
    // /pix agendar <jogador> <valor> <dias> [motivo] e /pix agendamentos
    // ---------------------------------------------------------------------
    private void agendar(Player pagador, String[] args) {
        if (args.length < 3) {
            pagador.sendMessage(cor("§cUso: §f/pix agendar <jogador> <valor> <dias> [motivo] §7(paga agora e repete a cada N dias)"));
            return;
        }
        OfflinePlayer alvo = resolverAlvo(args[0]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            pagador.sendMessage(cor("§cJogador \"" + args[0] + "\" não encontrado."));
            return;
        }
        if (alvo.getUniqueId().equals(pagador.getUniqueId())) {
            pagador.sendMessage(cor("§cVocê não pode agendar um Pix pra si mesmo."));
            return;
        }
        Double valor = parseValor(pagador, args[1]);
        if (valor == null) return;
        Integer dias = numero(args[2]);
        int maxDias = Math.max(1, plugin.getConfig().getInt("agendamento.dias-maximo", 30));
        if (dias == null || dias < 1 || dias > maxDias) {
            pagador.sendMessage(cor("§cO intervalo precisa ser de 1 a " + maxDias + " dias."));
            return;
        }
        AgendamentoManager agenda = plugin.getAgendamentoManager();
        if (agenda.quantosPaga(pagador.getUniqueId()) >= agenda.maximoPorJogador()) {
            pagador.sendMessage(cor("§cVocê já tem " + agenda.maximoPorJogador() + " Pix agendados, o máximo. Cancele um com §f/pix agendamentos cancelar <nº>§c."));
            return;
        }
        if (!plugin.getEconomy().has(pagador, valor)) {
            pagador.sendMessage(cor("§cO primeiro pagamento sai agora, e você não tem §f" + formatarValor(valor) + "§c."));
            return;
        }
        if (!verificarLimite(pagador, valor)) return;
        String motivo = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        AgendamentoManager.Agendamento a = agenda.criar(pagador, alvo, valor, dias, motivo);
        pagador.sendMessage(cor("§aPix agendado §f#" + a.getId() + "§a: §f" + formatarValor(valor) + " §apara §f" + alvo.getName()
                + " §aa cada §f" + dias + " dia(s)§a. Cancele com §f/pix agendamentos cancelar " + a.getId() + "§a."));
    }

    private void agendamentos(Player jogador, String[] args) {
        AgendamentoManager agenda = plugin.getAgendamentoManager();
        if (args.length >= 2 && args[0].equalsIgnoreCase("cancelar")) {
            Integer id = numero(args[1]);
            AgendamentoManager.Agendamento a = id == null ? null : agenda.get(id);
            boolean meu = a != null && (a.getPagador().equals(jogador.getUniqueId()) || a.getRecebedor().equals(jogador.getUniqueId()));
            if (!meu) {
                jogador.sendMessage(cor("§cVocê não tem um Pix agendado com esse número."));
                return;
            }
            agenda.cancelar(a);
            jogador.sendMessage(cor("§7Pix agendado §f#" + a.getId() + " §7cancelado."));
            UUID outro = a.getPagador().equals(jogador.getUniqueId()) ? a.getRecebedor() : a.getPagador();
            Player outroOnline = Bukkit.getPlayer(outro);
            if (outroOnline != null) {
                outroOnline.sendMessage(cor("§7" + jogador.getName() + " cancelou o Pix agendado §f#" + a.getId() + "§7."));
            }
            return;
        }
        List<AgendamentoManager.Agendamento> lista = agenda.doJogador(jogador.getUniqueId());
        jogador.sendMessage(cor("§e§l--- Pix agendados ---"));
        if (lista.isEmpty()) {
            jogador.sendMessage(cor("§7Nenhum. Crie com §f/pix agendar <jogador> <valor> <dias> [motivo]§7."));
            return;
        }
        long agora = System.currentTimeMillis();
        for (AgendamentoManager.Agendamento a : lista) {
            boolean paga = a.getPagador().equals(jogador.getUniqueId());
            long horas = Math.max(0, (a.getProximo() - agora) / 3_600_000L);
            jogador.sendMessage(cor("§f#" + a.getId() + " " + (paga ? "§c→ §f" + a.getNomeRecebedor() : "§a← §f" + a.getNomePagador())
                    + " §7" + formatarValor(a.getValor()) + " a cada " + a.getDias() + " dia(s)"
                    + (a.getMotivo() != null ? " (" + a.getMotivo() + ")" : "")
                    + " · próximo em " + (horas >= 24 ? horas / 24 + "d " + horas % 24 + "h" : horas + "h")));
        }
        jogador.sendMessage(cor("§7Cancele com §f/pix agendamentos cancelar <nº>§7."));
    }

    private static Integer numero(String texto) {
        try {
            return Integer.parseInt(texto.replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Bloqueia (com mensagem) um envio que passaria do limite diário. Retorna true se pode seguir. */
    private boolean verificarLimite(Player jogador, double valor) {
        LimiteManager limites = plugin.getLimiteManager();
        if (limites.podeEnviar(jogador, valor)) return true;

        jogador.sendMessage(cor("§cEsse envio passa do seu limite diário de §f" + formatarValor(limites.getLimiteDiario())
                + "§c. Você ainda pode enviar §f" + formatarValor(limites.getDisponivelHoje(jogador.getUniqueId()))
                + " §choje (zera à meia-noite)."));
        return false;
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
        if (!Double.isFinite(valor) || valor <= 0) { // parseDouble aceita NaN/Infinity, e NaN passa por qualquer comparação com zero
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
            opcoes.addAll(Arrays.asList("pagar", "receber", "aceitar", "recusar", "pendentes", "cancelar", "historico", "qrcode", "limite",
                    "agendar", "agendamentos", "chave"));
            if (sender.hasPermission("pix.admin")) {
                opcoes.add("admin");
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("agendamentos")) {
                opcoes.add("cancelar");
            } else if (sub.equals("chave")) {
                opcoes.add("remover");
            } else if (sub.equals("pagar") || sub.equals("agendar") || sub.equals("receber") || sub.equals("cobrar")
                    || sub.equals("aceitar") || sub.equals("recusar") || sub.equals("cancelar")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    opcoes.add(p.getName());
                }
            } else if (sub.equals("admin")) {
                opcoes.add("reload");
                opcoes.add("resetlimite");
                opcoes.add("log");
                opcoes.add("vencer");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("resetlimite") || args[1].equalsIgnoreCase("log"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                opcoes.add(p.getName());
            }
        }

        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
