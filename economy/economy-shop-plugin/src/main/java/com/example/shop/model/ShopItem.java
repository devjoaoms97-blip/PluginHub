package com.example.shop.model;

import org.bukkit.Material;

/**
 * Um item cadastrado na loja.
 *
 * - Preço de COMPRA (jogador compra do servidor) = sempre {@link #precoBase}, fixo.
 * - Preço de VENDA (jogador vende pro servidor) = dinâmico, começa na "âncora"
 *   (precoBase menos a margem) e só se move quando alguém VENDE (desce) ou com
 *   o tempo (recupera de volta em direção à âncora). Comprar NÃO afeta o preço de venda.
 */
public class ShopItem {

    private final Material material;
    private final String categoria;
    private final double precoBase;
    private final double margemPercentual;
    private final double precoMinimo;
    private final double precoMaximo;
    private final double passoPercentual;

    private double precoVendaAtual;

    public ShopItem(Material material, String categoria, double precoBase, double margemPercentual,
                     double precoMinimo, double precoMaximo, double passoPercentual) {
        this.material = material;
        this.categoria = (categoria == null || categoria.isBlank()) ? "Geral" : categoria;
        this.precoBase = precoBase;
        this.margemPercentual = margemPercentual;
        this.precoMinimo = precoMinimo;
        this.precoMaximo = precoMaximo;
        this.passoPercentual = passoPercentual;
        this.precoVendaAtual = getAncora();
    }

    public String getCategoria() {
        return categoria;
    }

    /** Preço de venda "de referência", pro qual o preço dinâmico tende a voltar com o tempo. */
    public double getAncora() {
        double ancora = precoBase * (1 - margemPercentual / 100.0);
        return Math.max(precoMinimo, Math.min(precoMaximo, ancora));
    }

    /** Vende 1 unidade: retorna o preço pago por ela e já empurra o preço pra baixo pra próxima unidade. */
    public double venderUnidade() {
        double precoPago = precoVendaAtual;
        double novoPreco = precoVendaAtual * (1 - passoPercentual / 100.0);
        precoVendaAtual = Math.max(precoMinimo, novoPreco);
        return precoPago;
    }

    /** Move o preço de venda uma fração da distância até a âncora (usado pela regeneração periódica). */
    public void regenerar(double fator) {
        double ancora = getAncora();
        precoVendaAtual += (ancora - precoVendaAtual) * fator;
        precoVendaAtual = Math.max(precoMinimo, Math.min(precoMaximo, precoVendaAtual));
    }

    public void resetarParaAncora() {
        precoVendaAtual = getAncora();
    }

    public Material getMaterial() {
        return material;
    }

    public double getPrecoBase() {
        return precoBase;
    }

    public double getMargemPercentual() {
        return margemPercentual;
    }

    public double getPrecoMinimo() {
        return precoMinimo;
    }

    public double getPrecoMaximo() {
        return precoMaximo;
    }

    public double getPassoPercentual() {
        return passoPercentual;
    }

    public double getPrecoVendaAtual() {
        return precoVendaAtual;
    }

    public void setPrecoVendaAtual(double precoVendaAtual) {
        this.precoVendaAtual = Math.max(precoMinimo, Math.min(precoMaximo, precoVendaAtual));
    }
}
