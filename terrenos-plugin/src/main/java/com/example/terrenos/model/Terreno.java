package com.example.terrenos.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Um terreno que tem dono ou está em leilão. Terreno livre não tem registro: a grade é infinita
 * e só os que já foram comprados são guardados.
 */
public class Terreno {

    private final TerrenoId id;
    private UUID dono;               // null = em leilão
    private String nomeDono;
    private final Set<UUID> amigos = new LinkedHashSet<>();
    private long impostoPagoAte;
    private boolean autoPagar = true;
    private long compradoEm;
    private double ultimoPreco;
    private Leilao leilao;

    public Terreno(TerrenoId id) {
        this.id = id;
    }

    public TerrenoId getId() {
        return id;
    }

    public UUID getDono() {
        return dono;
    }

    public String getNomeDono() {
        return nomeDono;
    }

    public void setDono(UUID dono, String nome) {
        this.dono = dono;
        this.nomeDono = nome;
    }

    public boolean temDono() {
        return dono != null;
    }

    public Set<UUID> getAmigos() {
        return amigos;
    }

    /** Dono ou amigo: pode construir e usar tudo dentro. */
    public boolean podeMexer(UUID jogador) {
        return jogador.equals(dono) || amigos.contains(jogador);
    }

    public long getImpostoPagoAte() {
        return impostoPagoAte;
    }

    public void setImpostoPagoAte(long impostoPagoAte) {
        this.impostoPagoAte = impostoPagoAte;
    }

    public boolean isAutoPagar() {
        return autoPagar;
    }

    public void setAutoPagar(boolean autoPagar) {
        this.autoPagar = autoPagar;
    }

    public long getCompradoEm() {
        return compradoEm;
    }

    public void setCompradoEm(long compradoEm) {
        this.compradoEm = compradoEm;
    }

    public double getUltimoPreco() {
        return ultimoPreco;
    }

    public void setUltimoPreco(double ultimoPreco) {
        this.ultimoPreco = ultimoPreco;
    }

    public Leilao getLeilao() {
        return leilao;
    }

    public void setLeilao(Leilao leilao) {
        this.leilao = leilao;
    }

    public boolean emLeilao() {
        return leilao != null;
    }
}
