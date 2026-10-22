package com.example.clas.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Um clã: tag (2 a 5 letras/números, única sem diferenciar maiúscula), nome, membros com
 * cargo, banco compartilhado com extrato e placar de abates.
 */
public class Cla {

    public record Lancamento(long data, String jogador, String tipo, double valor) {
    }

    public static final int EXTRATO_MAXIMO = 30;

    private final String tag;
    private String nome;
    private final long criadoEm;
    private final Map<UUID, Cargo> membros = new LinkedHashMap<>();
    private final Map<UUID, String> nomes = new LinkedHashMap<>();
    private double banco;
    private final List<Lancamento> extrato = new ArrayList<>(); // mais recente primeiro
    private boolean fogoAmigo;
    private int abates;
    private int mortes;

    public Cla(String tag, String nome, long criadoEm) {
        this.tag = tag;
        this.nome = nome;
        this.criadoEm = criadoEm;
    }

    public String chave() {
        return tag.toLowerCase(java.util.Locale.ROOT);
    }

    public UUID getLider() {
        for (Map.Entry<UUID, Cargo> e : membros.entrySet()) {
            if (e.getValue() == Cargo.LIDER) return e.getKey();
        }
        return null;
    }

    public Cargo getCargo(UUID jogador) {
        return membros.get(jogador);
    }

    public void lancar(String jogador, String tipo, double valor) {
        extrato.add(0, new Lancamento(System.currentTimeMillis(), jogador, tipo, valor));
        while (extrato.size() > EXTRATO_MAXIMO) {
            extrato.remove(extrato.size() - 1);
        }
    }

    public String getTag() {
        return tag;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public long getCriadoEm() {
        return criadoEm;
    }

    public Map<UUID, Cargo> getMembros() {
        return membros;
    }

    public Map<UUID, String> getNomes() {
        return nomes;
    }

    public double getBanco() {
        return banco;
    }

    public void setBanco(double banco) {
        this.banco = banco;
    }

    public List<Lancamento> getExtrato() {
        return extrato;
    }

    public boolean isFogoAmigo() {
        return fogoAmigo;
    }

    public void setFogoAmigo(boolean fogoAmigo) {
        this.fogoAmigo = fogoAmigo;
    }

    public int getAbates() {
        return abates;
    }

    public void setAbates(int abates) {
        this.abates = abates;
    }

    public int getMortes() {
        return mortes;
    }

    public void setMortes(int mortes) {
        this.mortes = mortes;
    }
}
