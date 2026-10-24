package com.example.barracas.model;

import org.bukkit.inventory.ItemStack;

/**
 * Um anúncio da loja do NPC. {@link Tipo#VENDA}: o dono vende, e o estoque é o que ele depositou.
 * {@link Tipo#COMPRA}: o dono compra dos visitantes, e o estoque é o que já foi comprado, esperando
 * ele retirar. O estoque é virtual (fica no barracas.yml), em unidades do item.
 */
public class Oferta {

    public enum Tipo { VENDA, COMPRA }

    private final Tipo tipo;
    private final ItemStack modelo;
    private final int quantidade;
    private final double preco;
    private int estoque;

    public Oferta(Tipo tipo, ItemStack modelo, int quantidade, double preco, int estoque) {
        this.tipo = tipo;
        this.modelo = modelo.clone();
        this.modelo.setAmount(1);
        this.quantidade = quantidade;
        this.preco = preco;
        this.estoque = estoque;
    }

    public Tipo getTipo() {
        return tipo;
    }

    /** Uma unidade do item, sempre clonada — quem pega pode mexer à vontade. */
    public ItemStack getModelo() {
        return modelo.clone();
    }

    /** Quantas unidades vão em cada compra/venda. */
    public int getQuantidade() {
        return quantidade;
    }

    public double getPreco() {
        return preco;
    }

    public int getEstoque() {
        return estoque;
    }

    public void setEstoque(int estoque) {
        this.estoque = Math.max(0, estoque);
    }

    public boolean mesmoItem(ItemStack item) {
        return item != null && modelo.isSimilar(item);
    }
}
