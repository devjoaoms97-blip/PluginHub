package com.example.shop.command;

import com.example.shop.ShopPlugin;
import com.example.shop.manager.PriceHistoryManager;
import com.example.shop.model.ShopItem;
import com.example.shop.util.GraficoUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;

    public ShopCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return tratarAdmin(sender, args);
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("historico") || args[0].equalsIgnoreCase("grafico"))) {
            return tratarHistorico(sender, args);
        }

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem abrir a loja.");
            return true;
        }
        jogador.openInventory(plugin.getShopGUI().montarCategorias());
        return true;
    }

    // ---------------------------------------------------------------------
    // /shop historico <item>
    // ---------------------------------------------------------------------
    private boolean tratarHistorico(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/shop historico <item>");
            return true;
        }

        Material material;
        try {
            material = Material.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cItem \"" + args[1] + "\" não reconhecido.");
            return true;
        }

        ShopItem item = plugin.getShopManager().getItem(material);
        if (item == null) {
            sender.sendMessage("§cEsse item não está cadastrado na loja.");
            return true;
        }

        List<PriceHistoryManager.Ponto> pontos = plugin.getPriceHistoryManager().getHistorico(material);

        sender.sendMessage("§e§l--- Histórico de " + material.name() + " (últimas 24h) ---");
        sender.sendMessage(GraficoUtil.gerarSparkline(pontos));

        if (pontos.size() >= 2) {
            double variacao = GraficoUtil.variacaoPercentual(pontos);
            String corVariacao = variacao >= 0 ? "§a+" : "§c";
            sender.sendMessage("§7Atual: §f" + formatar(item.getPrecoVendaAtual())
                    + " §7| Mín: §f" + formatar(GraficoUtil.minimo(pontos))
                    + " §7| Máx: §f" + formatar(GraficoUtil.maximo(pontos))
                    + " §7| Variação: " + corVariacao + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", variacao) + "%");
        } else {
            sender.sendMessage("§7Preço atual: §f" + formatar(item.getPrecoVendaAtual()));
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // /shop admin ...
    // ---------------------------------------------------------------------
    private boolean tratarAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage("§cVocê não tem permissão pra gerenciar a loja.");
            return true;
        }

        if (args.length < 2) {
            enviarAjudaAdmin(sender);
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "additem" -> tratarAdditem(sender, args);
            case "removeitem" -> tratarRemoveitem(sender);
            case "resetprice" -> tratarResetprice(sender);
            case "list" -> tratarList(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getShopManager().recarregar();
                sender.sendMessage("§aConfiguração e itens recarregados (itens.yml lido do disco).");
            }
            default -> enviarAjudaAdmin(sender);
        }
        return true;
    }

    private void tratarAdditem(CommandSender sender, String[] args) {
        Player p = exigirJogador(sender);
        if (p == null) return;

        if (args.length < 8) {
            sender.sendMessage("§cUso: §f/shop admin additem <categoria> <preco-base> <margem%> <minimo> <maximo> <passo%>");
            sender.sendMessage("§7Dica: use aspas ou sem espaço na categoria, ex: Minerios");
            return;
        }

        ItemStack itemNaMao = p.getInventory().getItemInMainHand();
        if (itemNaMao.getType().isAir()) {
            sender.sendMessage("§cSegure o item que quer cadastrar na mão.");
            return;
        }

        String categoria = args[2];
        Double precoBase = parseDouble(args[3], sender);
        Double margem = parseDouble(args[4], sender);
        Double minimo = parseDouble(args[5], sender);
        Double maximo = parseDouble(args[6], sender);
        Double passo = parseDouble(args[7], sender);
        if (precoBase == null || margem == null || minimo == null || maximo == null || passo == null) return;

        if (minimo > maximo) {
            sender.sendMessage("§cO mínimo não pode ser maior que o máximo.");
            return;
        }
        if (maximo >= precoBase) {
            sender.sendMessage("§e⚠ Aviso: o máximo de venda (" + maximo + ") é maior ou igual ao preço de compra ("
                    + precoBase + "). Isso permite comprar e vender sem perda — considere baixar o máximo.");
        }

        Material material = itemNaMao.getType();
        plugin.getShopManager().cadastrarItem(material, categoria, precoBase, margem, minimo, maximo, passo);

        sender.sendMessage("§aItem §f" + material.name() + " §acadastrado na categoria §f" + categoria
                + "§a. Compra: §f" + formatar(precoBase) + " §a| Venda começa em: §f"
                + formatar(plugin.getShopManager().getItem(material).getAncora()));
    }

    private void tratarRemoveitem(CommandSender sender) {
        Player p = exigirJogador(sender);
        if (p == null) return;

        Material material = p.getInventory().getItemInMainHand().getType();
        if (plugin.getShopManager().removerItem(material)) {
            sender.sendMessage("§aItem §f" + material.name() + " §aremovido da loja.");
        } else {
            sender.sendMessage("§cEsse item não estava cadastrado.");
        }
    }

    private void tratarResetprice(CommandSender sender) {
        Player p = exigirJogador(sender);
        if (p == null) return;

        Material material = p.getInventory().getItemInMainHand().getType();
        if (plugin.getShopManager().resetarPreco(material)) {
            sender.sendMessage("§aPreço de venda de §f" + material.name() + " §aresetado pra âncora.");
        } else {
            sender.sendMessage("§cEsse item não estava cadastrado.");
        }
    }

    /**
     * /shop admin list [piso]
     *
     * Sem argumento extra: lista todos os itens cadastrados, marcando com [NO PISO]
     * os que estão travados no preço mínimo de venda, e mostra um resumo no final.
     * Com "piso": mostra só os itens travados, pra facilitar auditoria rápida.
     *
     * Um item "travado no piso" é sinal de que a oferta de venda (passo * regen) não
     * está acompanhando o volume vendido — considere baixar ainda mais o `minimo` desse
     * item, ou aumentar `imposto.percentual-venda` no config.yml pra segurar a inflação.
     */
    private void tratarList(CommandSender sender, String[] args) {
        Map<Material, ShopItem> itens = plugin.getShopManager().getTodosItens();
        if (itens.isEmpty()) {
            sender.sendMessage("§7Nenhum item cadastrado na loja ainda.");
            return;
        }

        boolean somentePiso = args.length > 2 && args[2].equalsIgnoreCase("piso");

        int travados = 0;
        List<ShopItem> exibidos = new ArrayList<>();
        for (ShopItem item : itens.values()) {
            if (estaNoPiso(item)) {
                travados++;
                exibidos.add(item);
            } else if (!somentePiso) {
                exibidos.add(item);
            }
        }

        String titulo = somentePiso
                ? "§e§l--- Itens travados no piso (" + travados + "/" + itens.size() + ") ---"
                : "§e§l--- Itens da Loja (" + itens.size() + ") ---";
        sender.sendMessage(titulo);

        if (somentePiso && travados == 0) {
            sender.sendMessage("§7Nenhum item travado no preço mínimo no momento.");
            return;
        }

        for (ShopItem item : exibidos) {
            String tagPiso = estaNoPiso(item) ? " §c[NO PISO]" : "";
            sender.sendMessage("§f" + item.getMaterial().name() + " §7[" + item.getCategoria() + "] - Compra: §f"
                    + formatar(item.getPrecoBase())
                    + " §7| Venda atual: §f" + formatar(item.getPrecoVendaAtual())
                    + " §7(min " + formatar(item.getPrecoMinimo()) + " / max " + formatar(item.getPrecoMaximo()) + ")"
                    + tagPiso);
        }

        if (!somentePiso) {
            sender.sendMessage("§7---");
            if (travados > 0) {
                double percentual = (100.0 * travados) / itens.size();
                sender.sendMessage("§e⚠ §f" + travados + " §7de §f" + itens.size() + " §7itens ("
                        + String.format(Locale.forLanguageTag("pt-BR"), "%.0f", percentual)
                        + "%) estão travados no preço mínimo.");
                sender.sendMessage("§7Oferta acima do que §fpasso§7/regeneração aguentam — considere baixar o §fminimo§7 "
                        + "desses itens ou aumentar §fimposto.percentual-venda§7 no config.yml.");
                sender.sendMessage("§7Dica: use §f/shop admin list piso §7pra ver só esses itens.");
            } else {
                sender.sendMessage("§aNenhum item travado no preço mínimo no momento.");
            }
        }
    }

    /** Um item está "no piso" quando o preço de venda atual já bateu no mínimo configurado. */
    private boolean estaNoPiso(ShopItem item) {
        return item.getPrecoVendaAtual() <= item.getPrecoMinimo();
    }

    private Player exigirJogador(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cEsse subcomando precisa ser usado por um jogador (precisa segurar o item na mão).");
        return null;
    }

    private Double parseDouble(String texto, CommandSender sender) {
        try {
            return Double.parseDouble(texto.replace(",", "."));
        } catch (NumberFormatException e) {
            sender.sendMessage("§cValor inválido: " + texto);
            return null;
        }
    }

    private String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    private void enviarAjudaAdmin(CommandSender sender) {
        sender.sendMessage("§e§l--- Admin da Loja ---");
        sender.sendMessage("§f/shop admin additem <categoria> <base> <margem%> <min> <max> <passo%> §7- cadastra o item na mão");
        sender.sendMessage("§f/shop admin removeitem §7- remove o item na mão do catálogo");
        sender.sendMessage("§f/shop admin resetprice §7- reseta o preço de venda do item na mão pra âncora");
        sender.sendMessage("§f/shop admin list §7- lista todos os itens cadastrados e preços atuais");
        sender.sendMessage("§f/shop admin list piso §7- lista só os itens travados no preço mínimo");
        sender.sendMessage("§f/shop admin reload §7- recarrega o config.yml");
        sender.sendMessage("§f/shop historico <item> §7- mostra o gráfico de tendência das últimas 24h");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opcoes = new ArrayList<>();
            if (sender.hasPermission("shop.admin")) opcoes.add("admin");
            opcoes.add("historico");
            return opcoes;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("additem", "removeitem", "resetprice", "list", "reload");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("list")) {
            return List.of("piso");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("historico")) {
            List<String> opcoes = new ArrayList<>();
            String digitado = args[1].toLowerCase(Locale.ROOT);
            for (Material material : plugin.getShopManager().getTodosItens().keySet()) {
                String nome = material.name().toLowerCase(Locale.ROOT);
                if (nome.startsWith(digitado)) opcoes.add(nome);
            }
            return opcoes;
        }
        return List.of();
    }
}
