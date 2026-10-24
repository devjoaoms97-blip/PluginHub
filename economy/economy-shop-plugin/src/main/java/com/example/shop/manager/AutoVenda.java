package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Venda automática do que se minera nas minas (pra todos), chamada pelo RankupPlugin por reflexão
 * via {@link ShopPlugin#venderAutomaticamente}. Preço dinâmico, bônus do rank e do VIP e imposto
 * ({@link Precificacao}), sem a trava nem o limite diário da venda do inventário. Não grava o itens.yml a cada bloco (a regeneração já grava a cada
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
        if (item == null || !item.isVendivelNaMina()) return false;

        // sem trava e sem limite diário: o item nem chega a existir, não dá pra comprar e revender
        var venda = plugin.getPrecificacao().vender(jogador, item, stack.getAmount(), false);
        if (venda.quantidade() <= 0) return false;

        jogador.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                String.format(Locale.forLanguageTag("pt-BR"), "§a+R$ %,.2f §7(%dx %s)",
                        venda.liquido(), venda.quantidade(), item.getMaterial().name().toLowerCase(Locale.ROOT).replace('_', ' '))));
        return true;
    }
}
