package com.example.duelo.model;

import java.util.UUID;

/** Desafio ainda não aceito. Nada é cobrado até o desafiado aceitar. */
public record Convite(UUID desafiante, String nomeDesafiante, UUID desafiado, double aposta, long expiraEm) {
}
