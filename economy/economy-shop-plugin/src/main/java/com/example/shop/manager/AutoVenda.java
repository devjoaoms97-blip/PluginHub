package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Venda automática do que o jogador minera (benefício de VIP), chamada pelo RankupPlugin por
 * reflexão via {@link ShopPlugin#venderAutomaticamente}. Mesmas regras do /sell: preço dinâmico,
 * bônus do rank e imposto. Não grava o itens.yml a cada bloco (a regeneração já grava a cada
 * minuto) e não lança no /pix historico, que viraria uma linha por bloco minerado.
 */
public class AutoVenda {

    private final ShopPlugin plugin;

    public AutoVenda(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Vende o stack inteiro. Retorna false (e não mexe em nada) se a loja não compra esse item. */
    public boolean vender(Player jogador, ItemStack stack) {
        if (plugin.getEconomy() == null || stack == null || stack.getAmount() <= 0) return false;
        // item com nome ou encantamento não é minério comum: fica no chão
        if (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName() || stack.getItemMeta().hasEnchants())) return false;
        ShopItem item = plugin.getShopManager().getItem(stack.getType());
        if (item == null) return false;

        int quantidade = stack.getAmount();
        double bruto = 0;
        for (int i = 0; i < quantidade; i++) {
            bruto += item.venderUnidade();
        }
        plugin.getVendasDiariasManager().registrar(item.getMaterial(), quantidade, bruto);

        bruto *= plugin.getRankupIntegration().multiplicador(jogador);
        double imposto = plugin.getConfig().getDouble("imposto.percentual-venda", 2.0);
        double liquido = bruto * (1 - imposto / 100.0);
        plugin.getEconomy().depositPlayer(jogador, liquido);
        plugin.getMissoesIntegration().vendeu(jogador, item.getMaterial(), quantidade);

        jogador.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                String.format(Locale.forLanguageTag("pt-BR"), "§a+R$ %,.2f §7(%dx %s, venda automática)",
                        liquido, quantidade, item.getMaterial().name().toLowerCase(Locale.ROOT).replace('_', ' '))));
        return true;
    }
}
