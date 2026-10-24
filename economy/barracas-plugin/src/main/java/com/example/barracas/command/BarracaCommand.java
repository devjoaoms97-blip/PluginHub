package com.example.barracas.command;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.manager.BarracaManager;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import com.example.barracas.model.Leilao;
import com.example.barracas.model.Oferta;
import com.example.barracas.util.Formato;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /barraca — jogador: alugar, renovar, autorenovar, sair, lance, info, lista, retirar, e a loja
 * (vender, comprar, loja). Staff (barracas.admin): pos1, pos2, criar, remover, liberar, preco,
 * holograma, npc, reload. Criar, npc e holograma também vão pelo console, com o lugar depois de
 * "em" (é assim que o EstruturasPlugin cadastra as barracas do spawn).
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
        int em = List.of(args).indexOf("em");
        if (em > 0 && (sub.equals("criar") || sub.equals("npc") || sub.equals("holograma"))) {
            if (exigirAdmin(sender)) porLocal(sender, sub, args, em);
            return true;
        }
        switch (sub) {
            case "lista" -> lista(sender);
            case "info" -> info(sender, args);
            case "alugar", "renovar", "autorenovar", "sair", "lance", "retirar", "vender", "comprar", "loja",
                 "pos1", "pos2", "criar", "holograma", "npc" -> {
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
                    case "vender" -> anunciar(jogador, args, Oferta.Tipo.VENDA);
                    case "comprar" -> anunciar(jogador, args, Oferta.Tipo.COMPRA);
                    case "loja" -> loja(jogador);
                    case "npc" -> npc(jogador, args);
                    case "pos1", "pos2" -> marcarPosicao(jogador, sub);
                    case "criar" -> criar(jogador, args);
                    case "holograma" -> holograma(jogador, args);
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
        jogador.sendMessage("§7Sua loja é o NPC da barraca. Com o item na mão, anuncie com §f/barraca vender <quantidade> <preço>"
                + " §7(ou §f/barraca comprar§7) e abasteça clicando no NPC ou em §f/barraca loja§7.");
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
                    + "O estoque da loja vai pra §f/barraca retirar§e. Pra confirmar: §f/barraca sair confirmar");
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

    /** /barraca vender|comprar <quantidade> <preço>, com o item na mão: cria um anúncio na loja do NPC. */
    private void anunciar(Player jogador, String[] args, Oferta.Tipo tipo) {
        String comando = tipo == Oferta.Tipo.VENDA ? "vender" : "comprar";
        Barraca barraca = barracas().getBarracaDe(jogador.getUniqueId());
        if (barraca == null || !barraca.estaAlugada(System.currentTimeMillis())) {
            jogador.sendMessage("§cVocê precisa ter uma barraca alugada. Veja as livres em §f/barraca lista§c.");
            return;
        }
        if (args.length < 3) {
            jogador.sendMessage("§cUso: §f/barraca " + comando + " <quantidade> <preço> §7(com o item na mão)");
            return;
        }
        ItemStack mao = jogador.getInventory().getItemInMainHand();
        if (mao.getType().isAir()) {
            jogador.sendMessage("§cSegure na mão o item que quer " + comando + ".");
            return;
        }
        int quantidade;
        try {
            quantidade = Integer.parseInt(args[1].replace("x", ""));
        } catch (NumberFormatException e) {
            quantidade = -1;
        }
        if (quantidade < 1 || quantidade > plugin.getEstoqueMaximo()) {
            jogador.sendMessage("§cQuantidade por " + (tipo == Oferta.Tipo.VENDA ? "venda" : "compra")
                    + " inválida (1 a " + plugin.getEstoqueMaximo() + ").");
            return;
        }
        double preco = Formato.lerPreco(args[2]);
        if (preco <= 0) {
            jogador.sendMessage("§cPreço inválido. Use um número, ex: §f150§c ou §f150,50§c.");
            return;
        }
        if (barraca.getOfertas().size() >= plugin.getMaxOfertas(barraca)) {
            jogador.sendMessage("§cSua loja já tem o máximo de §f" + plugin.getMaxOfertas(barraca)
                    + " §canúncios. Remova um (shift + clique no menu da loja) antes.");
            return;
        }
        for (Oferta existente : barraca.getOfertas()) {
            if (existente.getTipo() == tipo && existente.mesmoItem(mao)) {
                jogador.sendMessage("§cVocê já tem um anúncio pra " + comando + " esse item. Remova o antigo pra mudar o preço.");
                return;
            }
        }

        Oferta oferta = new Oferta(tipo, mao, quantidade, preco, 0);
        barraca.getOfertas().add(oferta);
        String descricao = quantidade + "x " + Formato.nomeItem(mao) + " §apor §f" + Formato.valor(preco);
        if (tipo == Oferta.Tipo.VENDA) {
            jogador.sendMessage("§aAnúncio criado: vende §f" + descricao + "§a.");
            plugin.getLoja().depositar(jogador, oferta); // já leva o que tiver no inventário
        } else {
            jogador.sendMessage("§aAnúncio criado: compra §f" + descricao
                    + "§a. O pagamento sai da sua conta; retire os itens comprados no NPC.");
        }
        barracas().salvar();
    }

    private void loja(Player jogador) {
        Barraca barraca = barracas().getBarracaDe(jogador.getUniqueId());
        if (barraca == null || !barraca.estaAlugada(System.currentTimeMillis())) {
            jogador.sendMessage("§cVocê não tem nenhuma barraca alugada.");
            return;
        }
        plugin.getLoja().abrir(jogador, barraca);
    }

    /** /barraca npc <id> [remover]: o vendedor fica onde a staff está, olhando pra onde ela olha. */
    private void npc(Player jogador, String[] args) {
        if (!exigirAdmin(jogador)) return;
        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/barraca npc <id> §7(no lugar do vendedor, olhando pro balcão) §7| §f/barraca npc <id> remover");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            jogador.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("remover")) {
            barraca.setNpc(null, 0);
            barracas().salvar(); // salvar já tira o NPC
            jogador.sendMessage("§aA barraca §f" + barraca.getId() + " §anão tem mais ponto de NPC.");
            return;
        }
        if (!jogador.getWorld().getName().equals(barraca.getMundo())) {
            jogador.sendMessage("§cO NPC precisa ficar no mesmo mundo da barraca.");
            return;
        }
        // yaw arredondado pro ponto cardeal mais próximo: o vendedor fica alinhado com o balcão
        float yaw = Math.round(jogador.getLocation().getYaw() / 90f) * 90f;
        barraca.setNpc(BlocoPos.de(jogador.getLocation().getBlock()), yaw);
        barracas().salvar(); // salvar já põe o NPC (se a barraca estiver alugada)
        jogador.sendMessage("§aPonto do NPC da barraca §f" + barraca.getId() + " §amarcado onde você está. "
                + "§7Ele aparece com a skin do dono enquanto a barraca estiver alugada.");
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
                + " §7| Máx. de anúncios: §f" + plugin.getMaxOfertas(barraca));
        sender.sendMessage("§7" + (barraca.isEspecial() ? "Lance mínimo" : "Aluguel") + ": §f"
                + Formato.valor(plugin.getPrecoAluguel(barraca)) + " §7por §f" + Formato.tempo(plugin.getDuracaoAluguelMs())
                + (barraca.getPreco() == null ? " §8(preço padrão)" : ""));
        sender.sendMessage("§7Situação: " + situacao(barraca)
                + (barraca.isRenovacaoAutomatica() ? " §8(renovação automática ligada)" : ""));
        if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§7Área: §f" + barraca.getMundo() + " " + barraca.getMin() + " → " + barraca.getMax());
            sender.sendMessage("§7NPC: " + (barraca.getNpc() == null ? "§cnão marcado §7(§f/barraca npc " + barraca.getId() + "§7)"
                    : "§f" + barraca.getNpc()));
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
        boolean especial = args.length >= 3 && args[2].equalsIgnoreCase("especial");
        if (criarArea(jogador, id, especial, mundo, a, b)) {
            pos1.remove(uuid);
            pos2.remove(uuid);
        }
    }

    private boolean criarArea(CommandSender sender, String id, boolean especial, World mundo, BlocoPos a, BlocoPos b) {
        BlocoPos min = new BlocoPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        BlocoPos max = new BlocoPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        if (barracas().sobrepoeAlguma(mundo.getName(), min, max)) {
            sender.sendMessage("§cEssa área sobrepõe outra barraca.");
            return false;
        }
        barracas().criar(id, mundo, a, b, especial);
        sender.sendMessage("§aBarraca §f" + id + " §acriada" + (especial ? " como §6especial§a (já entrou em leilão)" : "")
                + "§a: §f" + min + " → " + max + "§a.");
        return true;
    }

    /**
     * Versão com o lugar explícito, pro console:
     * criar <id> [especial] em <mundo> <x1> <y1> <z1> <x2> <y2> <z2> |
     * npc <id> em <mundo> <x> <y> <z> <yaw> | holograma <id> em <mundo> <x> <y> <z>
     */
    private void porLocal(CommandSender sender, String sub, String[] args, int em) {
        int numeros = switch (sub) {
            case "criar" -> 6;
            case "npc" -> 4;
            default -> 3;
        };
        World mundo = args.length > em + 1 ? Bukkit.getWorld(args[em + 1]) : null;
        if (args.length < em + 2 + numeros || mundo == null) {
            sender.sendMessage("§cUso: §f/barraca criar <id> [especial] em <mundo> <x1> <y1> <z1> <x2> <y2> <z2>§c, "
                    + "§f/barraca npc <id> em <mundo> <x> <y> <z> <yaw> §cou §f/barraca holograma <id> em <mundo> <x> <y> <z>");
            return;
        }
        int[] v = new int[numeros];
        try {
            for (int i = 0; i < numeros; i++) v[i] = (int) Math.floor(Double.parseDouble(args[em + 2 + i]));
        } catch (NumberFormatException e) {
            sender.sendMessage("§cCoordenadas inválidas.");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("criar")) {
            if (!id.matches("[a-z0-9_-]{1,24}")) {
                sender.sendMessage("§cId inválido: use letras minúsculas, números, - ou _ (até 24 caracteres).");
            } else if (barracas().get(id) != null) {
                sender.sendMessage("§cJá existe uma barraca §f" + id + "§c.");
            } else {
                criarArea(sender, id, args[2].equalsIgnoreCase("especial"), mundo,
                        new BlocoPos(v[0], v[1], v[2]), new BlocoPos(v[3], v[4], v[5]));
            }
            return;
        }

        Barraca barraca = barracas().get(id);
        if (barraca == null) {
            sender.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        if (!mundo.getName().equals(barraca.getMundo())) {
            sender.sendMessage("§cPrecisa ser no mesmo mundo da barraca.");
            return;
        }
        BlocoPos pos = new BlocoPos(v[0], v[1], v[2]);
        if (sub.equals("npc")) {
            barraca.setNpc(pos, v[3]);
        } else {
            barraca.setEntrada(pos);
        }
        barracas().salvar(); // salvar já põe o NPC/holograma
        sender.sendMessage("§a" + (sub.equals("npc") ? "NPC" : "Holograma") + " da barraca §f" + id + " §aem §f" + pos + "§a.");
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

    /** /barraca holograma <id> [remover]: o holograma de entrada vai pro bloco onde a staff está. */
    private void holograma(Player jogador, String[] args) {
        if (!exigirAdmin(jogador)) return;
        if (args.length < 2) {
            jogador.sendMessage("§cUso: §f/barraca holograma <id> §7(no lugar da entrada) §7| §f/barraca holograma <id> remover");
            return;
        }
        Barraca barraca = barracas().get(args[1]);
        if (barraca == null) {
            jogador.sendMessage("§cBarraca §f" + args[1] + " §cnão existe.");
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("remover")) {
            barraca.setEntrada(null);
            barracas().salvar(); // salvar já tira o holograma
            jogador.sendMessage("§aA barraca §f" + barraca.getId() + " §anão tem mais holograma de entrada.");
            return;
        }
        if (!jogador.getWorld().getName().equals(barraca.getMundo())) {
            jogador.sendMessage("§cO holograma precisa ficar no mesmo mundo da barraca.");
            return;
        }
        barraca.setEntrada(BlocoPos.de(jogador.getLocation().getBlock()));
        barracas().salvar(); // salvar já põe o holograma
        jogador.sendMessage("§aHolograma de entrada da barraca §f" + barraca.getId() + " §acolocado onde você está. Ele mostra o dono, ou "
                + "\"Aluga-se\" com o preço quando estiver livre, e se atualiza sozinho.");
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
        sender.sendMessage("§f/barraca vender <quantidade> <preço> §7- anuncia o item da mão na loja do seu NPC");
        sender.sendMessage("§f/barraca comprar <quantidade> <preço> §7- sua loja passa a comprar o item da mão");
        sender.sendMessage("§f/barraca loja §7- abre o estoque da sua loja (o mesmo que clicar no NPC)");
        sender.sendMessage("§f/barraca retirar §7- pega os itens da loja quando o aluguel acabou");
        if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§f/barraca pos1 §7| §fpos2 §7- (staff) marca os cantos da área");
            sender.sendMessage("§f/barraca criar <id> [especial] §7- (staff) cria a barraca na área marcada");
            sender.sendMessage("§f/barraca remover <id> §7- (staff) apaga a barraca");
            sender.sendMessage("§f/barraca liberar <id> §7- (staff) encerra o aluguel atual");
            sender.sendMessage("§f/barraca preco <id> <valor|padrao> §7- (staff) preço próprio da barraca");
            sender.sendMessage("§f/barraca holograma <id> [remover] §7- (staff) holograma de entrada, onde você está");
            sender.sendMessage("§f/barraca npc <id> [remover] §7- (staff) lugar do NPC vendedor, onde você está");
            sender.sendMessage("§f/barraca reload §7- (staff) recarrega o config.yml");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.addAll(List.of("lista", "info", "alugar", "renovar", "autorenovar", "lance", "sair", "retirar",
                    "vender", "comprar", "loja"));
            if (sender.hasPermission(BarracasPlugin.PERMISSAO_ADMIN)) {
                opcoes.addAll(List.of("pos1", "pos2", "criar", "remover", "liberar", "preco", "holograma", "npc", "reload"));
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "info", "remover", "preco", "holograma", "npc" -> opcoes.addAll(barracas().getBarracas().keySet());
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
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("holograma") || args[0].equalsIgnoreCase("npc"))) {
            opcoes.add("remover");
        }

        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
