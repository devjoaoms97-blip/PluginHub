package com.example.vip.model;

import java.util.UUID;

/**
 * Um VIP ativo de um jogador. Imutável — renovar ou trocar de tier substitui a entrada
 * inteira no {@link com.example.vip.manager.VipManager}, nunca muta os campos aqui.
 * {@code evento}: veio de um código de evento (prêmio do servidor): os benefícios passivos e o kit
 * diário, sem o bônus de ativação nem o kit mensal.
 */
public record VipAtivo(UUID jogadorId, VipTier tier, long expiraEm, boolean evento) {

    public boolean expirado() {
        return System.currentTimeMillis() >= expiraEm;
    }
}
