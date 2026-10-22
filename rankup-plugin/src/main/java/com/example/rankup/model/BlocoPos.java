package com.example.rankup.model;

import org.bukkit.block.Block;

/** Coordenada de bloco (sem mundo — o mundo fica na {@link Mina}). Serializa como "x,y,z". */
public record BlocoPos(int x, int y, int z) {

    public static BlocoPos de(Block bloco) {
        return new BlocoPos(bloco.getX(), bloco.getY(), bloco.getZ());
    }

    public static BlocoPos parse(String texto) {
        String[] partes = texto.split(",");
        return new BlocoPos(Integer.parseInt(partes[0].trim()), Integer.parseInt(partes[1].trim()),
                Integer.parseInt(partes[2].trim()));
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
