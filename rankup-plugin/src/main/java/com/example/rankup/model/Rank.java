package com.example.rankup.model;

/**
 * Um rank da progressão. {@code cor} já vem com § (convertida do &amp; do config).
 * {@code preco} é o custo pra subir pra este rank; o do primeiro rank é ignorado.
 */
public record Rank(String id, String nome, String cor, double preco, double multiplicadorVenda) {

    /** Nome colorido, ex: "§aFazendeiro". */
    public String nomeColorido() {
        return cor + nome;
    }
}
