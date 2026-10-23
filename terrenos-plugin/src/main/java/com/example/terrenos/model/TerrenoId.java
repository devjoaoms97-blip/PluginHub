package com.example.terrenos.model;

/** Posição do terreno na grade, escrita como "x;z" (ex: "0;0", "-2;5"). */
public record TerrenoId(int x, int z) {

    @Override
    public String toString() {
        return x + ";" + z;
    }

    /** Lê "x;z"; retorna null se não for nesse formato. */
    public static TerrenoId ler(String texto) {
        if (texto == null) return null;
        String[] partes = texto.trim().split(";");
        if (partes.length != 2) return null;
        try {
            return new TerrenoId(Integer.parseInt(partes[0].trim()), Integer.parseInt(partes[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Distância em "anéis" do centro da grade, pra achar o terreno livre mais perto do início. */
    public int anel() {
        return Math.max(Math.abs(x), Math.abs(z));
    }
}
