package com.example.vip.model;

import java.util.UUID;

/**
 * Um código de resgate: quem tiver o código digita {@code /vip resgatar <codigo>} no jogo
 * e recebe o VIP na própria conta — sem o vendedor precisar saber o UUID/nick do comprador
 * de antemão. Serve pra dois casos:
 *
 * <ul>
 *   <li><b>Código de staff</b> (via {@code /vip gerarcodigo}): grátis pro comprador,
 *       {@code preco} 0 e {@code vendedorId} nulo — a compra já foi paga fora do jogo
 *       (Discord/site).</li>
 *   <li><b>Código de revenda entre jogadores</b> (via {@code /vip vender}): {@code preco}
 *       em coins e {@code vendedorId} apontando pra quem colocou o próprio VIP à venda —
 *       quem resgata paga o vendedor na hora, via Vault.</li>
 * </ul>
 */
public class VipCode {

    private final String codigo;
    private final VipTier tier;
    private final int dias;
    private int usosRestantes;
    private final long criadoEm;
    private final String criadoPor;
    private final double preco;
    private final UUID vendedorId;

    public VipCode(String codigo, VipTier tier, int dias, int usosRestantes, long criadoEm, String criadoPor,
                   double preco, UUID vendedorId) {
        this.codigo = codigo;
        this.tier = tier;
        this.dias = dias;
        this.usosRestantes = usosRestantes;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
        this.preco = preco;
        this.vendedorId = vendedorId;
    }

    public String getCodigo() {
        return codigo;
    }

    public VipTier getTier() {
        return tier;
    }

    public int getDias() {
        return dias;
    }

    public int getUsosRestantes() {
        return usosRestantes;
    }

    public long getCriadoEm() {
        return criadoEm;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public double getPreco() {
        return preco;
    }

    /** Nulo pra códigos gerados pela staff ({@code /vip gerarcodigo}) — só existe em revenda entre jogadores. */
    public UUID getVendedorId() {
        return vendedorId;
    }

    public void consumirUso() {
        usosRestantes--;
    }

    public boolean esgotado() {
        return usosRestantes <= 0;
    }
}
