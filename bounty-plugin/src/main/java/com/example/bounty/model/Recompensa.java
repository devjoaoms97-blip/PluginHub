package com.example.bounty.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Recompensa pela cabeça de um jogador. Várias pessoas podem contribuir: os valores se somam
 * e quem matar leva tudo. Cada contribuição já está RETIDA (saiu da conta de quem ofereceu,
 * com a taxa descontada) — se ninguém cobrar até expirar, cada um recebe a sua parte de volta.
 */
public class Recompensa {

    private final UUID alvo;
    private String nomeAlvo;
    private final Map<UUID, Double> contribuicoes = new LinkedHashMap<>();
    private final Map<UUID, String> nomesContribuintes = new LinkedHashMap<>();
    private long atualizadaEm;

    public Recompensa(UUID alvo, String nomeAlvo, long atualizadaEm) {
        this.alvo = alvo;
        this.nomeAlvo = nomeAlvo;
        this.atualizadaEm = atualizadaEm;
    }

    public void adicionar(UUID contribuinte, String nome, double valorLiquido, long agora) {
        contribuicoes.merge(contribuinte, valorLiquido, Double::sum);
        nomesContribuintes.put(contribuinte, nome);
        atualizadaEm = agora;
    }

    public double getTotal() {
        double total = 0;
        for (double valor : contribuicoes.values()) total += valor;
        return total;
    }

    public UUID getAlvo() {
        return alvo;
    }

    public String getNomeAlvo() {
        return nomeAlvo;
    }

    public void setNomeAlvo(String nomeAlvo) {
        this.nomeAlvo = nomeAlvo;
    }

    public Map<UUID, Double> getContribuicoes() {
        return contribuicoes;
    }

    public Map<UUID, String> getNomesContribuintes() {
        return nomesContribuintes;
    }

    /** Última vez que alguém contribuiu — a expiração conta a partir daqui. */
    public long getAtualizadaEm() {
        return atualizadaEm;
    }
}
