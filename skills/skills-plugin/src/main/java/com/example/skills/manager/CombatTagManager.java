package com.example.skills.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager {

    private static final long JANELA_COMBATE_MS = 10_000; // 10 segundos

    private final Map<UUID, Long> ultimoCombate = new ConcurrentHashMap<>();

    public void marcar(UUID jogadorId) {
        ultimoCombate.put(jogadorId, System.currentTimeMillis());
    }

    public boolean emCombate(UUID jogadorId) {
        Long ultimo = ultimoCombate.get(jogadorId);
        if (ultimo == null) return false;
        return (System.currentTimeMillis() - ultimo) <= JANELA_COMBATE_MS;
    }

    public void remover(UUID jogadorId) {
        ultimoCombate.remove(jogadorId);
    }
}
