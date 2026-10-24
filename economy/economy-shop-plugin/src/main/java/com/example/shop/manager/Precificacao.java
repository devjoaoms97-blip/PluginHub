package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Todos os preços que o jogador vê e paga passam por aqui, pra valerem as mesmas regras no menu dos
 * vendedores, no /sell da staff e na venda automática das minas:
 * <ul>
 *   <li><b>Compra</b>: o preço fixo do item menos o desconto de VIP ({@code vip-descontos}).</li>
 *   <li><b>Venda</b>: o preço dinâmico (com o evento de mercado) vezes o bônus do rank e o bônus de
 *       VIP ({@code vip-bonus-venda}), menos o imposto.</li>
 *   <li><b>Trava</b> ({@code trava-venda}): vendendo o que está no inventário, uma unidade nunca rende
 *       mais que uma fração do MENOR preço de compra possível (com o maior desconto de VIP). Sem isso,
 *       um VIP no último rank comprava diamante a R$ 96 e vendia a R$ 117. A venda automática da mina
 *       não tem trava: o item nem chega a existir, então não dá pra comprar e revender.</li>
 *   <li>Sem limite de quantidade: o que muita gente vende cai de preço sozinho (preço dinâmico), então
 *       farm AFK se regula pela oferta e demanda.</li>
 * </ul>
 */
public class Precificacao {

    /** O que saiu de uma venda: quantas unidades, o bruto (já com os bônus), o líquido e se a trava agiu. */
    public record Venda(int quantidade, double bruto, double liquido, double multiplicador, boolean travou) {
        public double imposto() {
            return bruto - liquido;
        }
    }

    private final ShopPlugin plugin;

    public Precificacao(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- compra

    public double precoCompra(Player jogador, ShopItem item) {
        return item.getPrecoBase() * (1 - plugin.calcularDescontoVip(jogador) / 100.0);
    }

    // ---------------------------------------------------------------- venda

    /** Bônus de venda do VIP em % (o maior que o jogador tiver). */
    public double bonusVendaVip(Player jogador) {
        if (!plugin.getConfig().getBoolean("vip-bonus-venda.ativado", true)) return 0;
        return maiorPercentual(plugin.getConfig().getMapList("vip-bonus-venda.permissoes"), jogador);
    }

    /** Rank (com prestígio) × VIP. */
    public double multiplicadorVenda(Player jogador) {
        return plugin.getRankupIntegration().multiplicador(jogador) * (1 + bonusVendaVip(jogador) / 100.0);
    }

    /** O máximo que UMA unidade do inventário pode render, antes do imposto. */
    public double tetoUnitario(ShopItem item) {
        double fracao = plugin.getConfig().getDouble("trava-venda.percentual-da-compra", 90) / 100.0;
        return item.getPrecoBase() * (1 - maiorDescontoPossivel() / 100.0) * fracao;
    }

    /** O preço de uma unidade agora, pra esse jogador (o que aparece no menu), antes do imposto. */
    public double precoVendaUnitario(Player jogador, ShopItem item, boolean doInventario) {
        double preco = item.getPrecoVendaComEvento() * multiplicadorVenda(jogador);
        return doInventario ? Math.min(preco, tetoUnitario(item)) : preco;
    }

    /**
     * Vende {@code quantidade} unidades: empurra o preço dinâmico, deposita, registra nas vendas do dia
     * e nas missões. Quem chama tira os itens do inventário. Não lança no /pix: cada lugar lança do
     * seu jeito.
     */
    public Venda vender(Player jogador, ShopItem item, int quantidade, boolean doInventario) {
        if (quantidade <= 0 || plugin.getEconomy() == null) return new Venda(0, 0, 0, 1, false);

        double multiplicador = multiplicadorVenda(jogador);
        double teto = tetoUnitario(item);
        double imposto = plugin.getConfig().getDouble("imposto.percentual-venda", 2.0);
        double base = 0;
        double bruto = 0;
        boolean travou = false;
        int vendidas = 0;
        for (int i = 0; i < quantidade; i++) {
            double unidade = item.venderUnidade();
            base += unidade;
            double comBonus = unidade * multiplicador;
            if (doInventario && comBonus > teto) {
                comBonus = teto;
                travou = true;
            }
            bruto += comBonus;
            vendidas++;
        }
        if (vendidas == 0) return new Venda(0, 0, 0, multiplicador, false);
        plugin.getVendasDiariasManager().registrar(item.getMaterial(), vendidas, base);

        double liquido = bruto * (1 - imposto / 100.0);
        plugin.getEconomy().depositPlayer(jogador, liquido);
        plugin.getMissoesIntegration().vendeu(jogador, item.getMaterial(), vendidas);
        return new Venda(vendidas, bruto, liquido, multiplicador, travou);
    }

    /** " (bônus: rank +25%, VIP +12%)" pra mensagem de venda, ou "". */
    public String textoBonus(Player jogador) {
        double rank = plugin.getRankupIntegration().multiplicador(jogador);
        double vip = bonusVendaVip(jogador);
        StringBuilder sb = new StringBuilder();
        if (rank > 1) sb.append(String.format(Locale.ROOT, "rank +%.0f%%", (rank - 1) * 100));
        if (vip > 0) sb.append(sb.isEmpty() ? "" : ", ").append(String.format(Locale.ROOT, "VIP +%.0f%%", vip));
        return sb.isEmpty() ? "" : " §7(bônus: §a" + sb + "§7)";
    }

    // ---------------------------------------------------------------- utilidades

    /** O maior desconto de VIP do config: é com ele que a trava é calculada. */
    private double maiorDescontoPossivel() {
        double maior = 0;
        for (Map<?, ?> m : plugin.getConfig().getMapList("vip-descontos.permissoes")) {
            if (m.get("percentual") instanceof Number n) maior = Math.max(maior, n.doubleValue());
        }
        return Math.min(100, maior);
    }

    private static double maiorPercentual(List<Map<?, ?>> lista, Player jogador) {
        double maior = 0;
        for (Map<?, ?> m : lista) {
            if (m.get("permissao") instanceof String perm && m.get("percentual") instanceof Number n && jogador.hasPermission(perm)) {
                maior = Math.max(maior, n.doubleValue());
            }
        }
        return maior;
    }
}
