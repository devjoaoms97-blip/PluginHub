package com.example.oficinas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Um NPC de oficina: o tipo diz o que ele faz quando o jogador clica, e o local é onde o
 * Mannequin fica (atrás do balcão da barraquinha). Nome e skin vêm do config do tipo. O alvo só
 * existe no guia de mina: o id da mina do RankupPlugin.
 */
public class Oficina {

    public enum Tipo {
        /** Conserta os itens do jogador por uma taxa. */
        FERREIRO,
        /** Abre uma mesa de encantamento com o poder máximo (15 estantes). */
        BRUXA,
        /** Vende picaretas (e outras ferramentas) prontas, encantadas ou não. */
        PICARETEIRO,
        /** Vende níveis de XP. */
        SABIO,
        /** Vende itens de PvP: armaduras, armas e poções (balcão PvP da Loja Oficial). */
        ARMEIRO,
        /** Transforma minérios e lingotes em blocos e de volta, de graça. */
        COMPACTADOR,
        /** Troca Fragmentos Arcanos (das minas) por encantamentos acima do máximo. */
        CAMBISTA,
        /** Guia na entrada de uma mina: mostra as informações dela e leva quem tem o rank. */
        MINA,
        /** Bancário: folha de cheque e depósito de cheques (o balcão é do ChequePlugin). */
        BANCARIO;

        public String chave() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Tipo de(String texto) {
            try {
                return valueOf(texto.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final String id;
    private final Tipo tipo;
    private final String mundo;
    private final double x, y, z;
    private final float yaw;
    private final String alvo;

    public Oficina(String id, Tipo tipo, String alvo, String mundo, double x, double y, double z, float yaw) {
        this.id = id;
        this.tipo = tipo;
        this.alvo = alvo;
        this.mundo = mundo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public String getId() {
        return id;
    }

    public Tipo getTipo() {
        return tipo;
    }

    /** Id da mina (guia de mina), ou null. */
    public String getAlvo() {
        return alvo;
    }

    public String getMundo() {
        return mundo;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    /** null se o mundo não estiver carregado. */
    public Location getLocal() {
        World w = Bukkit.getWorld(mundo);
        return w == null ? null : new Location(w, x, y, z, yaw, 0);
    }
}
