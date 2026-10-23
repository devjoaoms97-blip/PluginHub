package com.example.gladiador.model;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Um Gladiador aberto: inscrições, preparação (sem dano) e luta. */
public class Evento {

    public enum Modo { INDIVIDUAL, CLA }

    public enum Fase { INSCRICAO, PREPARACAO, LUTA, FIM }

    /** Quem se inscreveu. {@code cla} é a tag no modo por clã (null no individual). */
    public static class Participante {
        private final String nome;
        private final String cla;
        private final Location retorno;
        private boolean vivo = true;
        private int abates;
        private double distancia;
        private double dano;

        public Participante(String nome, String cla, Location retorno) {
            this.nome = nome;
            this.cla = cla;
            this.retorno = retorno;
        }

        public String getNome() {
            return nome;
        }

        public String getCla() {
            return cla;
        }

        public Location getRetorno() {
            return retorno;
        }

        public boolean isVivo() {
            return vivo;
        }

        public void setVivo(boolean vivo) {
            this.vivo = vivo;
        }

        /** Destaques: abates, blocos andados e vida tirada de outros participantes durante a luta. */
        public int getAbates() {
            return abates;
        }

        public void somarAbate() {
            abates++;
        }

        public double getDistancia() {
            return distancia;
        }

        public void somarDistancia(double blocos) {
            distancia += blocos;
        }

        public double getDano() {
            return dano;
        }

        public void somarDano(double valor) {
            dano += valor;
        }
    }

    private final Modo modo;
    private final double inscricao;
    private final long fimInscricao;
    private final Map<UUID, Participante> participantes = new LinkedHashMap<>();
    private Fase fase = Fase.INSCRICAO;
    private long inicioLuta;

    public Evento(Modo modo, double inscricao, long fimInscricao) {
        this.modo = modo;
        this.inscricao = inscricao;
        this.fimInscricao = fimInscricao;
    }

    public Modo getModo() {
        return modo;
    }

    public double getInscricao() {
        return inscricao;
    }

    public long getFimInscricao() {
        return fimInscricao;
    }

    public Map<UUID, Participante> getParticipantes() {
        return participantes;
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

    /** Já está lutando (ou na preparação): as regras da arena valem. */
    public boolean emCombate() {
        return fase == Fase.PREPARACAO || fase == Fase.LUTA;
    }
}
