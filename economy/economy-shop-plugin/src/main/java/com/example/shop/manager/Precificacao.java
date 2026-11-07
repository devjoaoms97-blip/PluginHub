package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Todos os preços que o jogador vê e paga passam por aqui, pra valerem as mesmas regras no menu dos
 * vendedores, no /shop da staff e na venda automática das minas:
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

    /** Rank × VIP × a skill de profissão da categoria do item (o Agricultor no Fazendeiro). */
    public double multiplicadorVenda(Player jogador, ShopItem item) {
        return multiplicadorVenda(jogador) * (1 + bonusVendaSkill(jogador, item));
    }

    /**
     * Bônus de venda (fração) que o SkillsPlugin dá pra categoria desse item ({@code SkillsPlugin#bonusVenda}:
     * o Agricultor, nas categorias de {@code agricultor.categorias-venda} do config dele). 0 sem o plugin.
     */
    public double bonusVendaSkill(Player jogador, ShopItem item) {
        Plugin skills = Bukkit.getPluginManager().getPlugin("SkillsPlugin");
        if (skills == null || !skills.isEnabled()) return 0;
        try {
            Object valor = skills.getClass().getMethod("bonusVenda", UUID.class, String.class)
                    .invoke(skills, jogador.getUniqueId(), item.getCategoria());
            double bonus = valor instanceof Number n ? n.doubleValue() : 0;
            return Double.isFinite(bonus) && bonus > 0 ? bonus : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** O máximo que UMA unidade do inventário pode render, antes do imposto. */
    public double tetoUnitario(ShopItem item) {
        double fracao = plugin.getConfig().getDouble("trava-venda.percentual-da-compra", 90) / 100.0;
        return item.getPrecoBase() * (1 - maiorDescontoPossivel() / 100.0) * fracao;
    }

    /** O preço de uma unidade agora, pra esse jogador (o que aparece no menu), antes do imposto. */
    public double precoVendaUnitario(Player jogador, ShopItem item, boolean doInventario) {
        double preco = item.getPrecoVendaComEvento() * multiplicadorVenda(jogador, item);
        return doInventario ? Math.min(preco, tetoUnitario(item)) : preco;
    }

    /**
     * Vende {@code quantidade} unidades: empurra o preço dinâmico, deposita, registra nas vendas do dia
     * e nas missões. Quem chama tira os itens do inventário. Não lança no /pix: cada lugar lança do
     * seu jeito.
     */
    public Venda vender(Player jogador, ShopItem item, int quantidade, boolean doInventario) {
        if (quantidade <= 0 || plugin.getEconomy() == null) return new Venda(0, 0, 0, 1, false);

        double multiplicador = multiplicadorVenda(jogador, item);
        double teto = tetoUnitario(item);
        double imposto = plugin.getConfig().getDouble("imposto.percentual-venda", 2.0);
        double base = 0;
        double bruto = 0;
        boolean travou = false;
        int vendidas = 0;
        boolean dinamico = plugin.getConfig().getBoolean("preco-dinamico", false);
        for (int i = 0; i < quantidade; i++) {
            double unidade = item.venderUnidade(dinamico);
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
        if (doInventario) plugin.getAjudaIntegration().vendeu(jogador); // a venda automática das minas não conta
        return new Venda(vendidas, bruto, liquido, multiplicador, travou);
    }

    /** " (bônus: rank +25%, VIP +12%, Agricultor +20%)" pra mensagem de venda, ou "". */
    public String textoBonus(Player jogador, ShopItem item) {
        double rank = plugin.getRankupIntegration().multiplicador(jogador);
        double vip = bonusVendaVip(jogador);
        double skill = bonusVendaSkill(jogador, item);
        StringBuilder sb = new StringBuilder();
        if (rank > 1) sb.append(String.format(Locale.ROOT, "rank +%.0f%%", (rank - 1) * 100));
        if (vip > 0) sb.append(sb.isEmpty() ? "" : ", ").append(String.format(Locale.ROOT, "VIP +%.0f%%", vip));
        if (skill > 0) sb.append(sb.isEmpty() ? "" : ", ").append(String.format(Locale.ROOT, "Agricultor +%.1f%%", skill * 100));
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
