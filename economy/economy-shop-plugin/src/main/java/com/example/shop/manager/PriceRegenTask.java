package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.scheduler.BukkitRunnable;

public class PriceRegenTask extends BukkitRunnable {

    private final ShopPlugin plugin;

    public PriceRegenTask(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        double fator = plugin.getConfig().getDouble("regeneracao.fator-por-tick", 0.02);
        boolean houveMudanca = false;

        for (ShopItem item : plugin.getShopManager().getTodosItens().values()) {
            double antes = item.getPrecoVendaAtual();
            item.regenerar(fator);
            if (item.getPrecoVendaAtual() != antes) {
                houveMudanca = true;
            }
        }

        if (houveMudanca) {
            plugin.getShopManager().salvar();
        }
    }
}
