package com.example.barracas.command;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.manager.BarracaManager;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import com.example.barracas.model.Leilao;
import com.example.barracas.util.Formato;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /barraca — jogador: alugar, renovar, autorenovar, sair, lance, info, lista, retirar.
 * Staff (barracas.admin): pos1, pos2, criar, remover, liberar, preco, placa, reload.
 */
public class BarracaCommand implements CommandExecutor, TabCompleter {

    private final BarracasPlugin plugin;
    // Seleção de área da staff: jogador -> [mundo, pos1, pos2]
    private final Map<UUID, World> mundoSelecao = new HashMap<>();
    private final Map<UUID, BlocoPos> pos1 = new HashMap<>();
    private final Map<UUID, BlocoPos> pos2 = new HashMap<>();

    public BarracaCommand(BarracasPlugin plugin) {
        this.plugin = plugin;
    }

    private BarracaManager barracas() {
        return plugin.getBarracaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            enviarAjuda(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lista" -> lista(sender);
            case "info" -> info(sender, args);
            case "alugar", "renovar", "autorenovar", "sair", "lance", "retirar", "pos1", "pos2", "criar", "placa" -> {
                if (!(sender instanceof Player jogador)) {
                    sender.sendMessage("§cSó jogadores podem usar esse comando.");
                    return true;
                }
                switch (sub) {
                    case "alugar" -> alugar(jogador, args);
                    case "renovar" -> renovar(jogador);
                    case "autorenovar" -> autorenovar(jogador);
                    case "sair" -> sair(jogador, args);
                    case "lance" -> lance(jogador, args);
                    case "retirar" -> retirar(jogador);
                    case "pos1", "pos2" -> marcarPosicao(jogador, sub);
                    case "criar" -> criar(jogador, args);
                    case "placa" -> placa(jogador, args);
                }
            }
            case "preco" -> preco(sender, args);
            case "remover" -> remover(sender, args);
            case "liberar" -> liberar(sender, args);
            case "reload" -> {
                if (!exigirAdmin(sender)) return true;
                plugin.reloadConfig();
                sender.sendMessage("§aConfiguração recarregada.");
            }
            default -> enviarAjuda(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Jogador
    // ---------------------------------------------------------------------
    private void alugar(Player jogador, String[] args) {
        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/barraca alugar <id> §7(veja as livres em §f/barraca lista§7)");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            jogador.sendMessage("§cBarraca §f" + args[1] + " §cnão existe. Veja em §f/barraca lista§c.");
            return;
        }
        Economy econ = exigirEconomia(jogador);
        if (econ == null) return;

        UUID id = jogador.getUniqueId();
        Barraca atual = barracas().getBarracaDe(id);
        if (atual != null) {
            jogador.sendMessage("§cVocê já tem a barraca §f" + atual.getId() + "§c. Só dá pra ter uma por vez"
                    + (atual == barraca ? " — pra estender, use §f/barraca renovar§c." : "."));
            return;
        }
        Barraca liderando = barracas().getLeilaoLideradoPor(id);
        if (liderando != null) {
            jogador.sendMessage("§cVocê está com o maior lance no leilão da barraca §f" + liderando.getId()
                    + "§c. Espere o leilão acabar (ou ser superado) pra alugar outra.");
            return;
        }
        if (barraca.emLeilao()) {
            jogador.sendMessage("§eA barraca §f" + barraca.getId() + " §eestá em leilão. Dê um lance com §f/barraca lance "
                    + barraca.getId() + " <valor>§e.");
            return;
        }
        if (barraca.getDono() != null) {
            jogador.sendMessage("§cA barraca §f" + barraca.getId() + " §cestá alugada por §f" + barraca.getNomeDono()
                    + " §cpor mais §f" + Formato.tempo(barraca.getAlugadaAte() - System.currentTimeMillis()) + "§c.");
            return;
        }

        int desconto = plugin.calcularDescontoVip(jogador);
        double preco = plugin.getPrecoAluguel(barraca) * (1 - desconto / 100.0);
        if (!econ.has(jogador, preco)) {
            jogador.sendMessage("§cSaldo insuficiente. O aluguel custa §f" + Formato.valor(preco)
                    + " §c| Seu saldo: §f" + Formato.valor(econ.getBalance(jogador)));
            return;
        }

        econ.withdrawPlayer(jogador, preco);
        barraca.setUltimoDesconto(desconto);
        long duracao = plugin.getDuracaoAluguelMs();
        barracas().ocupar(barraca, id, jogador.getName(), System.currentTimeMillis() + duracao);
        plugin.getPixIntegration().registrarComServidor(id, preco, false, "Aluguel de barraca");

        jogador.sendMessage("§aVocê alugou a barraca §f" + barraca.getId() + " §apor §f" + Formato.tempo(duracao)
                + " §a(§f" + Formato.valor(preco) + "§a" + (desconto > 0 ? ", desconto de VIP: " + desconto + "%" : "") + ").");
        jogador.sendMessage("§7Coloque até §f" + plugin.getMaxBaus(barraca) + " baús §7com o item e uma placa em cada: "
                + "§f[Vender] §7(ou §f[Comprar]§7) / §fquantidade §7/ §fpreço§7.");
        if (!barraca.isEspecial()) {
            jogador.sendMessage("§7Pra não perder a barraca, ligue a renovação automática: §f/barraca autorenovar§7.");
        }
    }

    private void renovar(Player jogador) {
        Barraca barraca = barracas().getBarracaDe(jogador.getUniqueId());
        if (barraca == null) {
            jogador.sendMessage("§cVocê não tem nenhuma barraca alugada.");
            return;
        }
        if (barraca.isEspecial()) {
            jogador.sendMessage("§cBarracas especiais não renovam: quando o aluguel acabar, ela volta a leilão.");
            return;
        }
        Economy econ = exigirEconomia(jogador);
        if (econ == null) return;

        long agora = System.currentTimeMillis();
        long duracao = plugin.getDuracaoAluguelMs();
        long maxAcumuladoMs = Math.max(1, plugin.getConfig().getInt("aluguel.renovacao-maxima-horas", 12)) * 3_600_000L;
        long novoFim = Math.max(barraca.getAlugadaAte(), agora) + duracao;
        if (novoFim - agora > maxAcumuladoMs) {
            jogador.sendMessage("§cDá pra acumular no máximo §f" + Formato.tempo(maxAcumuladoMs) + " §cde aluguel. Renove quando faltar menos de §f"
                    + Formato.tempo(maxAcumuladoMs - duracao) + "§c (agora faltam §f" + Formato.tempo(barraca.getAlugadaAte() - agora) + "§c).");
            return;
        }

        int desconto = plugin.calcularDescontoVip(jogador);
        double preco = plugin.getPrecoAluguel(barraca) * (1 - desconto / 100.0);
        if (!econ.has(jogador, preco)) {
            jogador.sendMessage("§cSaldo insuficiente. A renovação custa §f" + Formato.valor(preco) + "§c.");
            return;
        }

        econ.withdrawPlayer(jogador, preco);
        barraca.setUltimoDesconto(desconto);
        barracas().estender(barraca, novoFim);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), preco, false, "Renovação de barraca");
        jogador.sendMessage("§aBarraca §f" + barraca.getId() + " §arenovada por §f" + Formato.valor(preco)
                + "§a. Agora vale por mais §f" + Formato.tempo(novoFim - agora) + "§a.");
    }

    private void autorenovar(Player jogador) {
        Barraca barraca = barracas().getBarracaDe(jogador.getUniqueId());
        if (barraca == null) {
            jogador.sendMessage("§cVocê não tem nenhuma barraca alugada.");
            return;
        }
        if (barraca.isEspecial()) {
            jogador.sendMessage("§cBarracas especiais não renovam: quando o aluguel acabar, ela volta a leilão.");
            return;
        }
        barraca.setRenovacaoAutomatica(!barraca.isRenovacaoAutomatica());
        barraca.setUltimoDesconto(plugin.calcularDescontoVip(jogador));
        barracas().salvar();
        if (barraca.isRenovacaoAutomatica()) {
            jogador.sendMessage("§aRenovação automática ligada. §7No último minuto do aluguel, §f"
                    + Formato.valor(barracas().precoComDesconto(barraca)) + " §7sai da sua conta e a barraca ganha mais §f"
                    + Formato.tempo(plugin.getDuracaoAluguelMs()) + "§7, mesmo com você offline. Sem saldo, ela desliga sozinha.");
        } else {
            jogador.sendMessage("§eRenovação automática desligada. §7O aluguel vence em §f"
                    + Formato.tempo(barraca.getAlugadaAte() - System.currentTimeMillis()) + "§7.");
        }
    }

    private void sair(Player jogador, String[] args) {
        Barraca barraca = barracas().getBarracaDe(jogador.getUniqueId());
        if (barraca == null) {
            jogador.sendMessage("§cVocê não tem nenhuma barraca alugada.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            jogador.sendMessage("§eIsso devolve a barraca §f" + barraca.getId() + " §eagora, §csem reembolso§e. "
                    + "Os itens dos baús vão pra §f/barraca retirar§e. Pra confirmar: §f/barraca sair confirmar");
            return;
        }
        int pilhas = barracas().liberar(barraca, true);
        jogador.sendMessage("§eVocê devolveu a barraca §f" + barraca.getId() + "§e." + barracas().avisoRetirada(pilhas));
    }

    private void lance(Player jogador, String[] args) {
        if (args.length < 3) {
            jogador.sendMessage("§cUso: §f/barraca lance <id> <valor>");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            jogador.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        if (exigirEconomia(jogador) == null) return;

        double valor = Formato.lerPreco(args[2]);
        if (valor <= 0) {
            jogador.sendMessage("§cValor inválido. Use um número, ex: §f750§c ou §f750,50§c.");
            return;
        }
        String erro = barracas().darLance(jogador, barraca, valor);
        if (erro != null) {
            jogador.sendMessage(erro);
        } else {
            jogador.sendMessage("§aLance registrado. O valor fica retido e volta pra você se alguém cobrir.");
        }
    }

    private void retirar(Player jogador) {
        int[] resultado = plugin.getRetiradaManager().retirar(jogador);
        if (resultado[0] == 0 && resultado[1] == 0) {
            jogador.sendMessage("§7Sua caixa de retirada está vazia.");
        } else if (resultado[1] == 0) {
            jogador.sendMessage("§aVocê retirou §f" + resultado[0] + " pilha(s)§a. A caixa está vazia agora.");
        } else {
            jogador.sendMessage("§eVocê retirou §f" + resultado[0] + " pilha(s)§e, mas o inventário encheu. Ainda faltam §f"
                    + resultado[1] + "§e — libere espaço e use §f/barraca retirar §ede novo.");
        }
    }

    private void info(CommandSender sender, String[] args) {
        Barraca barraca;
        if (args.length >= 2) {
            barraca = barracas().get(args[1]);
        } else if (sender instanceof Player jogador) {
            barraca = barracas().getBarracaEm(jogador.getLocation());
            if (barraca == null) barraca = barracas().getBarracaDe(jogador.getUniqueId());
        } else {
            barraca = null;
        }
        if (barraca == null) {
            sender.sendMessage("§cUso: §f/barraca info <id> §7(ou rode de dentro de uma barraca)");
            return;
        }

        sender.sendMessage("§e§l--- Barraca " + barraca.getId() + " ---");
        sender.sendMessage("§7Tipo: §f" + (barraca.isEspecial() ? "especial (leilão, não renova)" : "normal")
                + " §7| Máx. de baús: §f" + plugin.getMaxBaus(barraca));
        sender.sendMessage("§7" + (barraca.isEspecial() ? "Lance mínimo" : "Aluguel") + ": §f"
                + Formato.valor(plugin.getPrecoAluguel(barraca)) + " §7por §f" + Formato.tempo(plugin.getDuracaoAluguelMs())
                + (barraca.getPreco() == null ? " §8(preço padrão)" : ""));
        sender.sendMessage("§7Situação: " + situacao(barraca)
                + (barraca.isRenovacaoAutomatica() ? " §8(renovação automática ligada)" : ""));
        if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§7Área: §f" + barraca.getMundo() + " " + barraca.getMin() + " → " + barraca.getMax());
        }
    }

    private void lista(CommandSender sender) {
        if (barracas().getBarracas().isEmpty()) {
            sender.sendMessage("§7Nenhuma barraca criada ainda.");
            return;
        }
        sender.sendMessage("§e§l--- Barracas do mercado ---");
        for (Barraca barraca : barracas().getBarracas().values()) {
            sender.sendMessage("§f" + barraca.getId() + (barraca.isEspecial() ? " §6★" : "") + " §7- " + situacao(barraca));
        }
        sender.sendMessage("§7Aluguel por §f" + Formato.tempo(plugin.getDuracaoAluguelMs())
                + "§7 (preço de cada uma em §f/barraca info <id>§7). §6★ §7= especial, vai a leilão.");
    }

    private String situacao(Barraca barraca) {
        long agora = System.currentTimeMillis();
        if (barraca.getDono() != null) {
            return "§calugada por §f" + barraca.getNomeDono() + " §c(" + Formato.tempo(barraca.getAlugadaAte() - agora) + ")";
        }
        if (barraca.emLeilao()) {
            Leilao leilao = barraca.getLeilao();
            return "§6em leilão §7— " + (leilao.temLance()
                    ? "maior lance §f" + Formato.valor(leilao.getLance()) + " §7(" + leilao.getNomeLicitante() + ")"
                    : "sem lances, mínimo §f" + Formato.valor(plugin.getPrecoAluguel(barraca)))
                    + " §7— termina em §f" + Formato.tempo(leilao.getTerminaEm() - agora);
        }
        return "§alivre §7(§f" + Formato.valor(plugin.getPrecoAluguel(barraca)) + "§7) — §f/barraca alugar " + barraca.getId();
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------
    private void marcarPosicao(Player jogador, String qual) {
        if (!exigirAdmin(jogador)) return;
        // Bloco pra onde está olhando (assim dá pra pegar o piso); sem alvo, o bloco dos pés
        Block alvo = jogador.getTargetBlockExact(6);
        BlocoPos pos = BlocoPos.de(alvo != null ? alvo : jogador.getLocation().getBlock());
        World mundoAnterior = mundoSelecao.put(jogador.getUniqueId(), jogador.getWorld());
        if (mundoAnterior != null && !mundoAnterior.equals(jogador.getWorld())) {
            // trocou de mundo no meio da seleção: descarta o outro canto
            (qual.equals("pos1") ? pos2 : pos1).remove(jogador.getUniqueId());
        }
        (qual.equals("pos1") ? pos1 : pos2).put(jogador.getUniqueId(), pos);
        jogador.sendMessage("§a" + qual + " marcada em §f" + pos + "§a. "
                + "§7Olhe pro bloco de um canto e marque os dois cantos opostos (do piso até o teto).");
    }

    private void criar(Player jogador, String[] args) {
        if (!exigirAdmin(jogador)) return;
        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/barraca criar <id> [especial]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,24}")) {
            jogador.sendMessage("§cId inválido: use letras minúsculas, números, - ou _ (até 24 caracteres).");
            return;
        }
        if (barracas().get(id) != null) {
            jogador.sendMessage("§cJá existe uma barraca §f" + id + "§c.");
            return;
        }
        UUID uuid = jogador.getUniqueId();
        BlocoPos a = pos1.get(uuid);
        BlocoPos b = pos2.get(uuid);
        World mundo = mundoSelecao.get(uuid);
        if (a == null || b == null || mundo == null) {
            jogador.sendMessage("§cMarque os dois cantos antes, com §f/barraca pos1 §ce §f/barraca pos2§c.");
            return;
        }
        BlocoPos min = new BlocoPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        BlocoPos max = new BlocoPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        if (barracas().sobrepoeAlguma(mundo.getName(), min, max)) {
            jogador.sendMessage("§cEssa área sobrepõe outra barraca.");
            return;
        }

        boolean especial = args.length >= 3 && args[2].equalsIgnoreCase("especial");
        barracas().criar(id, mundo, a, b, especial);
        pos1.remove(uuid);
        pos2.remove(uuid);
        jogador.sendMessage("§aBarraca §f" + id + " §acriada" + (especial ? " como §6especial§a (já entrou em leilão)" : "")
                + "§a: §f" + min + " → " + max + "§a.");
    }

    /** /barraca preco <id> <valor|padrao> — preço próprio da barraca (e lance mínimo, se especial). */
    private void preco(CommandSender sender, String[] args) {
        if (!exigirAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage("§cUso: §f/barraca preco <id> <valor|padrao>");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            sender.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        if (args[2].equalsIgnoreCase("padrao") || args[2].equalsIgnoreCase("padrão")) {
            barraca.setPreco(null);
        } else {
            double valor = Formato.lerPreco(args[2]);
            if (valor <= 0) {
                sender.sendMessage("§cValor inválido. Use um número maior que zero, ex: §f750§c, ou §fpadrao§c.");
                return;
            }
            barraca.setPreco(valor);
        }
        barracas().salvar();
        sender.sendMessage("§aPreço da barraca §f" + barraca.getId() + "§a: §f" + Formato.valor(plugin.getPrecoAluguel(barraca))
                + (barraca.getPreco() == null ? " §7(padrão do config)" : "")
                + (barraca.isEspecial() ? " §7— é o lance mínimo dos próximos leilões." : " §7— vale a partir do próximo aluguel ou renovação."));
    }

    /** /barraca placa <id> [remover] — a placa pra onde a staff está olhando vira a placa de entrada. */
    private void placa(Player jogador, String[] args) {
        if (!exigirAdmin(jogador)) return;
        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/barraca placa <id> §7(olhando pra uma placa) §7| §f/barraca placa <id> remover");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            jogador.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("remover")) {
            barraca.setPlaca(null);
            barracas().salvar();
            jogador.sendMessage("§aA barraca §f" + barraca.getId() + " §anão tem mais placa de entrada (a placa continua lá, sem atualizar).");
            return;
        }
        Block alvo = jogador.getTargetBlockExact(6);
        if (alvo == null || !org.bukkit.Tag.ALL_SIGNS.isTagged(alvo.getType())) {
            jogador.sendMessage("§cOlhe pra uma placa (até 6 blocos) e rode o comando de novo.");
            return;
        }
        if (!alvo.getWorld().getName().equals(barraca.getMundo())) {
            jogador.sendMessage("§cA placa precisa estar no mesmo mundo da barraca.");
            return;
        }
        Barraca outra = barracas().getBarracaDaPlaca(alvo);
        if (outra != null && outra != barraca) {
            jogador.sendMessage("§cEssa placa já é a placa de entrada da barraca §f" + outra.getId() + "§c.");
            return;
        }
        barraca.setPlaca(BlocoPos.de(alvo));
        barracas().salvar(); // salvar já escreve a placa
        jogador.sendMessage("§aPlaca de entrada da barraca §f" + barraca.getId() + " §adefinida. Ela mostra o dono, ou "
                + "\"Aluga-se\" com o preço quando estiver livre, e se atualiza sozinha.");
    }

    private void remover(CommandSender sender, String[] args) {
        if (!exigirAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/barraca remover <id>");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            sender.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        barracas().remover(barraca);
        sender.sendMessage("§aBarraca §f" + barraca.getId() + " §aremovida. §7Itens do inquilino foram pra retirada dele; "
                + "lance retido (se havia) foi devolvido. Aluguel não é reembolsado.");
    }

    private void liberar(CommandSender sender, String[] args) {
        if (!exigirAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/barraca liberar <id>");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null || barraca.getDono() == null) {
            sender.sendMessage("§cEssa barraca não existe ou não está alugada.");
            return;
        }
        String dono = barraca.getNomeDono();
        int pilhas = barracas().liberar(barraca, true);
        sender.sendMessage("§aAluguel de §f" + dono + " §aencerrado na barraca §f" + barraca.getId() + "§a ("
                + pilhas + " pilha(s) foram pra retirada dele)" + (barraca.isEspecial() ? "; ela voltou a leilão" : "") + ".");
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------
    private boolean exigirAdmin(CommandSender sender) {
        if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) return true;
        sender.sendMessage("§cVocê não tem permissão pra isso.");
        return false;
    }

    private Economy exigirEconomia(Player jogador) {
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            jogador.sendMessage("§cO sistema de economia (Vault) não está disponível no servidor.");
        }
        return econ;
    }

    private void enviarAjuda(CommandSender sender) {
        sender.sendMessage("§e§l--- Barracas do mercado ---");
        sender.sendMessage("§f/barraca lista §7- todas as barracas e quais estão livres");
        sender.sendMessage("§f/barraca info [id] §7- detalhes de uma barraca");
        sender.sendMessage("§f/barraca alugar <id> §7- aluga uma barraca livre por " + Formato.tempo(plugin.getDuracaoAluguelMs()));
        sender.sendMessage("§f/barraca renovar §7- estende o aluguel (só barracas normais)");
        sender.sendMessage("§f/barraca autorenovar §7- liga/desliga a renovação automática do aluguel");
        sender.sendMessage("§f/barraca lance <id> <valor> §7- dá um lance no leilão de uma barraca especial");
        sender.sendMessage("§f/barraca sair confirmar §7- devolve sua barraca antes da hora (sem reembolso)");
        sender.sendMessage("§f/barraca retirar §7- pega os itens que estavam nos baús quando o aluguel acabou");
        if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§f/barraca pos1 §7| §fpos2 §7- (staff) marca os cantos da área");
            sender.sendMessage("§f/barraca criar <id> [especial] §7- (staff) cria a barraca na área marcada");
            sender.sendMessage("§f/barraca remover <id> §7- (staff) apaga a barraca");
            sender.sendMessage("§f/barraca liberar <id> §7- (staff) encerra o aluguel atual");
            sender.sendMessage("§f/barraca preco <id> <valor|padrao> §7- (staff) preço próprio da barraca");
            sender.sendMessage("§f/barraca placa <id> [remover] §7- (staff) placa de entrada (olhando pra placa)");
            sender.sendMessage("§f/barraca reload §7- (staff) recarrega o config.yml");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.addAll(List.of("lista", "info", "alugar", "renovar", "autorenovar", "lance", "sair", "retirar"));
            if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) {
                opcoes.addAll(List.of("pos1", "pos2", "criar", "remover", "liberar", "preco", "placa", "reload"));
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "info", "remover", "preco", "placa" -> opcoes.addAll(barracas().getBarracas().keySet());
                case "alugar" -> barracas().getBarracas().values().stream()
                        .filter(b -> b.getDono() == null && !b.emLeilao()).forEach(b -> opcoes.add(b.getId()));
                case "lance" -> barracas().getBarracas().values().stream()
                        .filter(Barraca::emLeilao).forEach(b -> opcoes.add(b.getId()));
                case "liberar" -> barracas().getBarracas().values().stream()
                        .filter(b -> b.getDono() != null).forEach(b -> opcoes.add(b.getId()));
                case "sair" -> opcoes.add("confirmar");
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("criar")) {
            opcoes.add("especial");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("preco")) {
            opcoes.add("padrao");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("placa")) {
            opcoes.add("remover");
        }

        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
