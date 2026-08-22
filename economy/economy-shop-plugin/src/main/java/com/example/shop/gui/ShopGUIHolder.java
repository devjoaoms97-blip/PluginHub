package com.example.shop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopGUIHolder implements InventoryHolder {

    public enum Tela {
        CATEGORIAS,
        ITENS
    }

    private final Tela tela;
    private final String categoria; // null quando tela == CATEGORIAS
    private final int pagina;
    private Inventory inventory;

    public ShopGUIHolder(Tela tela, String categoria, int pagina) {
        this.tela = tela;
        this.categoria = categoria;
        this.pagina = pagina;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Tela getTela() {
        return tela;
    }

    public String getCategoria() {
        return categoria;
    }

    public int getPagina() {
        return pagina;
    }
}
