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
    private int usosRestantes;
    private final long criadoEm;
    private final String criadoPor;
    private final double preco;
    private final UUID vendedorId;
    private final long expiraEm; // 0 = não expira

    public VipCode(String codigo, VipTier tier, int usosRestantes, long criadoEm, String criadoPor,
                   double preco, UUID vendedorId, long expiraEm) {
        this.codigo = codigo;
        this.tier = tier;
        this.usosRestantes = usosRestantes;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
        this.preco = preco;
        this.vendedorId = vendedorId;
        this.expiraEm = expiraEm;
    }

    public String getCodigo() {
        return codigo;
    }

    public VipTier getTier() {
        return tier;
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

    /** Quando o código deixa de valer se ninguém resgatar (epoch ms), ou 0 se não expira. */
    public long getExpiraEm() {
        return expiraEm;
    }

    public boolean expirado(long agora) {
        return expiraEm > 0 && agora >= expiraEm;
    }

    public void consumirUso() {
        usosRestantes--;
    }

    public boolean esgotado() {
        return usosRestantes <= 0;
    }
}
