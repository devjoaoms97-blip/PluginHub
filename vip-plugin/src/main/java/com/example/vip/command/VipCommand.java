package com.example.vip.command;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipAtivo;
import com.example.vip.model.VipCode;
import com.example.vip.model.VipTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ponto de entrada único do sistema: {@code /vip <ação>}. Cobre tanto os comandos de
 * jogador (check, tiers, resgatar, fly, heal, feed, kit, particula, warp) quanto os de
 * staff (add, remove, list, reload, gerarcodigo, codigos, revogarcodigo).
 */
public class VipCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Set<String> SUBCOMANDOS_PERK = Set.of("fly", "heal", "feed", "kit", "particula", "warp");

    private final VipPlugin plugin;
    private final PerkCommand perkCommand;

    public VipCommand(VipPlugin plugin) {
        this.plugin = plugin;
        this.perkCommand = new PerkCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            tratarCheckProprio(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (SUBCOMANDOS_PERK.contains(sub)) {
            tratarPerk(sender, sub, args);
            return true;
        }

        switch (sub) {
            case "add" -> tratarAdd(sender, args);
            case "remove" -> tratarRemove(sender, args);
            case "check" -> {
                if (args.length > 1) {
                    tratarCheckOutro(sender, args[1]);
                } else {
                    tratarCheckProprio(sender);
                }
            }
            case "list" -> tratarList(sender);
            case "tiers" -> tratarTiers(sender);
            case "reload" -> tratarReload(sender);
            case "resgatar" -> tratarResgatar(sender, args);
            case "vender" -> tratarVender(sender, args);
            case "cancelarvenda" -> tratarCancelarVenda(sender);
            case "gerarcodigo" -> tratarGerarCodigo(sender, args);
            case "codigos" -> tratarCodigos(sender);
            case "revogarcodigo" -> tratarRevogarCodigo(sender, args);
            default -> enviarAjuda(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Perks (delegado pro PerkCommand)
    // ---------------------------------------------------------------------

    private void tratarPerk(CommandSender sender, String sub, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSó jogadores podem usar esse comando.");
            return;
        }
        String[] restante = Arrays.copyOfRange(args, 1, args.length);
        perkCommand.executar(player, sub, restante);
    }

    // ---------------------------------------------------------------------
    // Gestão de VIP (staff)
    // ---------------------------------------------------------------------

    private void tratarAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão pra gerenciar VIPs.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUso: §f/vip add <jogador> <bronze|prata|ouro|diamante> <dias>");
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
        VipTier tier = VipTier.fromString(args[2]);
        if (tier == null) {
            sender.sendMessage("§cTier inválido. Use: bronze, prata, ouro ou diamante.");
            return;
        }

        Integer dias = lerInteiroPositivo(sender, args[3], "Dias");
        if (dias == null) {
            return;
        }

        String dataFormatada = plugin.getVipManager().adicionar(alvo.getUniqueId(), tier, dias);
        String nomeAlvo = alvo.getName() != null ? alvo.getName() : args[1];

        sender.sendMessage("§a" + nomeAlvo + " agora tem VIP " + plugin.getNomeExibicao(tier)
                + " §aaté §f" + dataFormatada + "§a.");

        avisarConcessao(alvo, tier, dataFormatada);
    }

    private void tratarRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão pra gerenciar VIPs.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/vip remove <jogador>");
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
        boolean removeu = plugin.getVipManager().remover(alvo.getUniqueId());
        String nomeAlvo = alvo.getName() != null ? alvo.getName() : args[1];

        if (removeu) {
            sender.sendMessage("§aVIP removido de §f" + nomeAlvo + "§a.");
            if (alvo.isOnline()) {
                plugin.refrescarTagJogador(alvo.getPlayer());
            }
        } else {
            sender.sendMessage("§7" + nomeAlvo + " não tem VIP ativo.");
        }
    }

    private void tratarList(CommandSender sender) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão pra ver a lista de VIPs.");
            return;
        }
        var ativos = plugin.getVipManager().getTodosAtivos();
        if (ativos.isEmpty()) {
            sender.sendMessage("§7Nenhum VIP ativo no momento.");
            return;
        }
        sender.sendMessage("§e§l--- VIPs ativos (" + ativos.size() + ") ---");
        for (VipAtivo vip : ativos.values()) {
            OfflinePlayer alvo = Bukkit.getOfflinePlayer(vip.jogadorId());
            String nome = alvo.getName() != null ? alvo.getName() : vip.jogadorId().toString();
            String data = FORMATO_DATA.format(Instant.ofEpochMilli(vip.expiraEm()).atZone(ZoneId.systemDefault()));
            String status = vip.expirado() ? " §c(expirado, aguardando varredura)" : "";
            sender.sendMessage("§f" + nome + " §7- " + plugin.getNomeExibicao(vip.tier()) + " §7até §f" + data + status);
        }
    }

    private void tratarReload(CommandSender sender) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage("§aConfiguração do VipPlugin recarregada.");
    }

    // ---------------------------------------------------------------------
    // Códigos de resgate (lojinha)
    // ---------------------------------------------------------------------

    private void tratarResgatar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSó jogadores podem resgatar código, direto no jogo.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUso: §f/vip resgatar <codigo>");
            return;
        }

        VipCode code = plugin.getCodeManager().buscar(args[1]);
        if (code == null) {
            player.sendMessage("§cCódigo inválido, expirado ou já usado.");
            return;
        }

        if (player.getUniqueId().equals(code.getVendedorId())) {
            player.sendMessage("§cVocê não pode comprar o próprio VIP à venda. Use §f/vip cancelarvenda§c pra desfazer.");
            return;
        }

        // Confere ANTES de consumir o código — se o pagamento não rolar, o jogador não pode
        // perder o código numa tentativa frustrada.
        if (code.getPreco() > 0) {
            Economy econ = plugin.getEconomy();
            if (econ == null) {
                player.sendMessage("§cSistema de economia indisponível no momento. Fale com a staff.");
                return;
            }
            if (!econ.has(player, code.getPreco())) {
                player.sendMessage("§cVocê não tem coins suficientes. Preço: §f" + formatarCoins(code.getPreco())
                        + " §c— seu saldo: §f" + formatarCoins(econ.getBalance(player)));
                return;
            }
        }

        plugin.getCodeManager().consumir(code);

        if (code.getPreco() > 0) {
            processarPagamentoRevenda(player, code);
        }

        String dataFormatada = plugin.getVipManager().adicionar(player.getUniqueId(), code.getTier(), code.getDias());
        player.sendMessage("§aCódigo resgatado! Você recebeu VIP " + plugin.getNomeExibicao(code.getTier())
                + " §apor §f" + code.getDias() + " dias§a, válido até §f" + dataFormatada + "§a.");
        plugin.refrescarTagJogador(player);
    }

    /** Cobra o comprador e repassa (menos a taxa de revenda) pro vendedor, se houver um. */
    private void processarPagamentoRevenda(Player comprador, VipCode code) {
        Economy econ = plugin.getEconomy();
        econ.withdrawPlayer(comprador, code.getPreco());

        if (code.getVendedorId() == null) {
            return; // código pago sem vendedor associado — o valor simplesmente não vai pra ninguém
        }

        double taxaPercentual = plugin.getConfig().getDouble("revenda.taxa-percentual", 10.0);
        double valorLiquido = code.getPreco() * (1 - taxaPercentual / 100.0);

        OfflinePlayer vendedor = Bukkit.getOfflinePlayer(code.getVendedorId());
        econ.depositPlayer(vendedor, valorLiquido);

        if (vendedor.isOnline()) {
            vendedor.getPlayer().sendMessage("§aSeu VIP " + plugin.getNomeExibicao(code.getTier())
                    + " §afoi comprado por §f" + comprador.getName() + "§a! Você recebeu §f" + formatarCoins(valorLiquido)
                    + (taxaPercentual > 0 ? " §7(taxa de revenda: " + taxaPercentual + "%)" : "") + "§a.");
        }
    }

    private void tratarVender(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSó jogadores podem revender um código.");
            return;
        }
        if (!plugin.getConfig().getBoolean("revenda.ativado", true)) {
            player.sendMessage("§cA revenda de VIP está desativada no momento.");
            return;
        }
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cSistema de economia indisponível no momento. Fale com a staff.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUso: §f/vip vender <preco> <codigo>");
            player.sendMessage("§7Só dá pra revender um código que você ainda não resgatou — não um VIP já ativo.");
            return;
        }

        double preco;
        try {
            preco = Double.parseDouble(args[1].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage("§cPreço inválido: " + args[1]);
            return;
        }
        if (preco < 0) {
            player.sendMessage("§cPreço não pode ser negativo.");
            return;
        }

        if (plugin.getCodeManager().buscarCodigoDeVenda(player.getUniqueId()) != null) {
            player.sendMessage("§cVocê já tem um código à venda. Cancele com §f/vip cancelarvenda§c antes de colocar outro.");
            return;
        }

        VipCode code = plugin.getCodeManager().colocarAVenda(args[2], preco, player.getUniqueId());
        if (code == null) {
            player.sendMessage("§cCódigo inválido, já resgatado, ou já está à venda.");
            return;
        }

        player.sendMessage("§aCódigo §fVIP-" + code.getCodigo() + " §a(" + plugin.getNomeExibicao(code.getTier())
                + " §a, " + code.getDias() + " dias) colocado à venda por §f" + formatarCoins(preco) + "§a.");
        player.sendMessage("§7Mudou de ideia? §f/vip cancelarvenda");

        anunciarVenda(player, code);
    }

    /** Anuncia publicamente um botão clicável que roda `/vip resgatar <codigo>` — igual o `/pix qrcode`. */
    private void anunciarVenda(Player vendedor, VipCode code) {
        if (!plugin.getConfig().getBoolean("revenda.anunciar-publicamente", true)) {
            avisarApenasVendedor(vendedor, code);
            return;
        }

        Component tagTier = LegacyComponentSerializer.legacySection().deserialize(plugin.getNomeExibicao(code.getTier()));

        Component botao = Component.text("[ COMPRAR ]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/vip resgatar " + code.getCodigo()))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para comprar por " + formatarCoins(code.getPreco()))));

        Component mensagem = Component.text(vendedor.getName() + " está vendendo VIP ")
                .color(NamedTextColor.AQUA)
                .append(tagTier)
                .append(Component.text(" (" + code.getDias() + " dias) por " + formatarCoins(code.getPreco()) + ":  ")
                        .color(NamedTextColor.AQUA))
                .append(botao);

        Bukkit.broadcast(mensagem);
    }

    private void avisarApenasVendedor(Player vendedor, VipCode code) {
        vendedor.sendMessage("§7Repasse o código pra quem for comprar — a pessoa resgata com §f/vip resgatar VIP-" + code.getCodigo());
    }

    private void tratarCancelarVenda(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSó jogadores podem cancelar a própria venda.");
            return;
        }

        VipCode code = plugin.getCodeManager().cancelarVenda(player.getUniqueId());
        if (code == null) {
            player.sendMessage("§7Você não tem nenhum código à venda no momento.");
            return;
        }

        player.sendMessage("§aVenda cancelada. O código §fVIP-" + code.getCodigo()
                + " §avoltou a ser um código normal — ainda dá pra resgatar de graça com §f/vip resgatar VIP-" + code.getCodigo());
    }

    private void tratarGerarCodigo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUso: §f/vip gerarcodigo <tier> <dias> [usos]");
            return;
        }

        VipTier tier = VipTier.fromString(args[1]);
        if (tier == null) {
            sender.sendMessage("§cTier inválido. Use: bronze, prata, ouro ou diamante.");
            return;
        }

        Integer dias = lerInteiroPositivo(sender, args[2], "Dias");
        if (dias == null) {
            return;
        }

        int usos = 1;
        if (args.length >= 4) {
            Integer usosLidos = lerInteiroPositivo(sender, args[3], "Usos");
            if (usosLidos == null) {
                return;
            }
            usos = usosLidos;
        }

        String criadoPor = sender instanceof Player p ? p.getName() : "console";
        VipCode code = plugin.getCodeManager().gerar(tier, dias, usos, criadoPor);

        sender.sendMessage("§aCódigo gerado: §f§lVIP-" + code.getCodigo());
        sender.sendMessage("§7" + plugin.getNomeExibicao(tier) + " §7por §f" + dias + " dias"
                + (usos > 1 ? " §7(" + usos + " usos)" : "")
                + " §7— peça pro jogador rodar §f/vip resgatar VIP-" + code.getCodigo());
    }

    private void tratarCodigos(CommandSender sender) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        var codigos = plugin.getCodeManager().getTodos();
        if (codigos.isEmpty()) {
            sender.sendMessage("§7Nenhum código ativo no momento.");
            return;
        }
        sender.sendMessage("§e§l--- Códigos ativos (" + codigos.size() + ") ---");
        for (VipCode code : codigos.values()) {
            String origem = code.getVendedorId() != null
                    ? "§7venda de §f" + code.getCriadoPor() + " §7por §f" + formatarCoins(code.getPreco())
                    : "§7gerado por §f" + code.getCriadoPor();
            sender.sendMessage("§fVIP-" + code.getCodigo() + " §7- " + plugin.getNomeExibicao(code.getTier())
                    + " §7por §f" + code.getDias() + "d §7(" + origem + "§7, " + code.getUsosRestantes() + " usos restantes)");
        }
    }

    private void tratarRevogarCodigo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/vip revogarcodigo <codigo>");
            return;
        }
        boolean removeu = plugin.getCodeManager().revogar(args[1]);
        sender.sendMessage(removeu ? "§aCódigo revogado." : "§7Código não encontrado (talvez já tenha sido usado).");
    }

    // ---------------------------------------------------------------------
    // Consulta de status
    // ---------------------------------------------------------------------

    private void tratarCheckProprio(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cUso no console: §f/vip check <jogador>");
            return;
        }
        exibirStatus(sender, p.getUniqueId(), p.getName());
    }

    private void tratarCheckOutro(CommandSender sender, String nomeAlvo) {
        if (!sender.hasPermission("vip.admin")) {
            sender.sendMessage("§cVocê só pode ver o próprio VIP. Use §f/vip check§c sem argumento.");
            return;
        }
        OfflinePlayer alvo = Bukkit.getOfflinePlayer(nomeAlvo);
        exibirStatus(sender, alvo.getUniqueId(), alvo.getName() != null ? alvo.getName() : nomeAlvo);
    }

    private void exibirStatus(CommandSender sender, UUID uuid, String nome) {
        VipAtivo vip = plugin.getVipManager().getVipAtivo(uuid);
        if (vip == null) {
            sender.sendMessage("§7" + nome + " não tem VIP ativo no momento.");
            return;
        }
        long restanteMs = vip.expiraEm() - System.currentTimeMillis();
        long dias = restanteMs / (24L * 60 * 60 * 1000);
        long horas = (restanteMs / (60L * 60 * 1000)) % 24;
        String data = FORMATO_DATA.format(Instant.ofEpochMilli(vip.expiraEm()).atZone(ZoneId.systemDefault()));
        sender.sendMessage("§f" + nome + " §7tem VIP " + plugin.getNomeExibicao(vip.tier())
                + " §7— expira em §f" + dias + "d " + horas + "h §7(§f" + data + "§7)");
    }

    private void tratarTiers(CommandSender sender) {
        sender.sendMessage("§e§l--- Tiers de VIP ---");
        for (VipTier tier : VipTier.values()) {
            sender.sendMessage(plugin.getNomeExibicao(tier) + " §7- desconto de §f"
                    + plugin.getDescontoPercentual(tier) + "% §7na loja, kit próprio, "
                    + "§f/vip fly§7, §f/vip heal§7, §f/vip feed§7 e partículas exclusivas.");
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void avisarConcessao(OfflinePlayer alvo, VipTier tier, String dataFormatada) {
        if (!alvo.isOnline()) {
            return;
        }
        Player online = alvo.getPlayer();
        String msg = plugin.getConfig()
                .getString("mensagens.vip-adicionado", "&aVocê recebeu o VIP &f{tier}&a! Válido até &f{data}&a.")
                .replace("{tier}", plugin.getNomeExibicao(tier))
                .replace("{data}", dataFormatada);
        online.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        plugin.refrescarTagJogador(online);
    }

    /** Usa o formatador da economia do Vault quando disponível (respeita o símbolo/moeda configurados nela). */
    private String formatarCoins(double valor) {
        Economy econ = plugin.getEconomy();
        return econ != null ? econ.format(valor) : String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valor);
    }

    /** Lê um inteiro > 0 de {@code texto}, avisando {@code sender} e retornando null se inválido. */
    private Integer lerInteiroPositivo(CommandSender sender, String texto, String nomeCampo) {
        int valor;
        try {
            valor = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c" + nomeCampo + " inválido: " + texto);
            return null;
        }
        if (valor <= 0) {
            sender.sendMessage("§c" + nomeCampo + " precisa ser maior que zero.");
            return null;
        }
        return valor;
    }

    private void enviarAjuda(CommandSender sender) {
        sender.sendMessage("§e§l--- Comandos do VIP ---");
        sender.sendMessage("§f/vip check §7- mostra seu VIP atual");
        sender.sendMessage("§f/vip tiers §7- lista os tiers disponíveis e seus benefícios");
        sender.sendMessage("§f/vip resgatar <codigo> §7- resgata um código comprado na lojinha ou de outro jogador");
        sender.sendMessage("§f/vip vender <preco> <codigo> §7- revende um código ainda não resgatado por coins");
        sender.sendMessage("§f/vip cancelarvenda §7- cancela sua venda em aberto e recupera o VIP");
        sender.sendMessage("§f/vip fly|heal|feed|kit|warp §7- perks de quem tem VIP ativo");
        sender.sendMessage("§f/vip particula <tipo|off> §7- escolhe a partícula que te segue");
        if (sender.hasPermission("vip.admin")) {
            sender.sendMessage("§7--- Staff ---");
            sender.sendMessage("§f/vip add <jogador> <tier> <dias> §7- concede/renova VIP direto");
            sender.sendMessage("§f/vip remove <jogador> §7- remove o VIP");
            sender.sendMessage("§f/vip check <jogador> §7- vê o VIP de outro jogador");
            sender.sendMessage("§f/vip list §7- lista todos os VIPs ativos");
            sender.sendMessage("§f/vip gerarcodigo <tier> <dias> [usos] §7- gera um código pra lojinha entregar");
            sender.sendMessage("§f/vip codigos §7- lista códigos ativos ainda não resgatados");
            sender.sendMessage("§f/vip revogarcodigo <codigo> §7- invalida um código antes de ser usado");
            sender.sendMessage("§f/vip reload §7- recarrega o config.yml");
        }
    }

    // ---------------------------------------------------------------------
    // Tab-complete
    // ---------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opcoes = new ArrayList<>(List.of("check", "tiers", "resgatar", "vender", "cancelarvenda",
                    "fly", "heal", "feed", "kit", "particula", "warp"));
            if (sender.hasPermission("vip.admin")) {
                opcoes.addAll(List.of("add", "remove", "list", "reload", "gerarcodigo", "codigos", "revogarcodigo"));
            }
            return opcoes;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && (sub.equals("add") || sub.equals("remove") || sub.equals("check"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 3 && sub.equals("add")) {
            return nomesTiers();
        }
        if (args.length == 4 && sub.equals("add")) {
            return List.of("7", "15", "30", "60");
        }
        if (args.length == 2 && sub.equals("gerarcodigo")) {
            return nomesTiers();
        }
        if (args.length == 2 && sub.equals("vender")) {
            return List.of("100", "500", "1000");
        }
        if (args.length == 3 && sub.equals("gerarcodigo")) {
            return List.of("7", "15", "30", "60");
        }
        if (args.length == 4 && sub.equals("gerarcodigo")) {
            return List.of("1", "5", "10");
        }
        if (args.length == 2 && sub.equals("revogarcodigo") && sender.hasPermission("vip.admin")) {
            return new ArrayList<>(plugin.getCodeManager().getTodos().keySet());
        }
        if (args.length == 2 && sub.equals("particula") && sender instanceof Player player) {
            return perkCommand.tabCompleteParticula(player);
        }

        return List.of();
    }

    private List<String> nomesTiers() {
        return Arrays.stream(VipTier.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
    }
}
