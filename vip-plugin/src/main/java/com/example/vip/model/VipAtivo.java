package com.example.vip.model;

import java.util.UUID;

/**
 * Um VIP ativo de um jogador. Imutável — renovar ou trocar de tier substitui a entrada
 * inteira no {@link com.example.vip.manager.VipManager}, nunca muta os campos aqui.
 */
public record VipAtivo(UUID jogadorId, VipTier tier, long expiraEm) {

    public boolean expirado() {
        return System.currentTimeMillis() >= expiraEm;
    }
}
