package com.example.terrenos.model;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
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
    /** Amigos e o que cada um pode fazer. */
    private final Map<UUID, Set<Permissao>> amigos = new LinkedHashMap<>();
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

    public Map<UUID, Set<Permissao>> getAmigos() {
        return amigos;
    }

    /** O dono pode tudo; o amigo, o que tiver recebido. */
    public boolean pode(UUID jogador, Permissao permissao) {
        if (jogador.equals(dono)) return true;
        Set<Permissao> dele = amigos.get(jogador);
        return dele != null && dele.contains(permissao);
    }

    /** Dono ou amigo com qualquer permissão (vê o imposto e os amigos no /terreno info). */
    public boolean ehDonoOuAmigo(UUID jogador) {
        return jogador.equals(dono) || amigos.containsKey(jogador);
    }

    public static Set<Permissao> todas() {
        return EnumSet.allOf(Permissao.class);
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
