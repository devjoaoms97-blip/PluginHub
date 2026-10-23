package com.example.duelo.model;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Um duelo aceito. As duas apostas já saíram das contas (ficam retidas) e voltam pro vencedor,
 * menos a taxa, ou pros dois no empate. Cada lutador volta pra onde estava quando o duelo começou.
 */
public class Duelo {

    public enum Fase { CONTAGEM, LUTA, FIM }

    private final UUID desafiante;
    private final UUID desafiado;
    private final String nomeDesafiante;
    private final String nomeDesafiado;
    private final double aposta;
    private final Arena arena;
    private final Map<UUID, Location> retorno = new HashMap<>();
    private Fase fase = Fase.CONTAGEM;
    private long inicioLuta;

    public Duelo(UUID desafiante, String nomeDesafiante, UUID desafiado, String nomeDesafiado, double aposta, Arena arena) {
        this.desafiante = desafiante;
        this.nomeDesafiante = nomeDesafiante;
        this.desafiado = desafiado;
        this.nomeDesafiado = nomeDesafiado;
        this.aposta = aposta;
        this.arena = arena;
    }

    public boolean participa(UUID jogador) {
        return desafiante.equals(jogador) || desafiado.equals(jogador);
    }

    public UUID oponente(UUID jogador) {
        return desafiante.equals(jogador) ? desafiado : desafiante;
    }

    public String nome(UUID jogador) {
        return desafiante.equals(jogador) ? nomeDesafiante : nomeDesafiado;
    }

    public UUID getDesafiante() {
        return desafiante;
    }

    public UUID getDesafiado() {
        return desafiado;
    }

    public double getAposta() {
        return aposta;
    }

    public Arena getArena() {
        return arena;
    }

    public Map<UUID, Location> getRetorno() {
        return retorno;
    }

    public Fase getFase() {
        return fase;
    }

    public void setFase(Fase fase) {
        this.fase = fase;
    }

    public long getInicioLuta() {
        return inicioLuta;
    }

    public void setInicioLuta(long inicioLuta) {
        this.inicioLuta = inicioLuta;
    }
}
