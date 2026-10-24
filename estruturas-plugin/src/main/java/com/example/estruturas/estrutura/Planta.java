package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planta de uma estrutura em coordenadas relativas: (0, 0, 0) é o bloco do chão logo abaixo do
 * centro da arena (onde o admin está pisando). Gravar duas vezes na mesma posição sobrescreve —
 * então dá pra montar em camadas (limpa, base, paredes, detalhes) sem se preocupar com a ordem.
 */
public class Planta {

    public record Bloco(int x, int y, int z, BlockData dados) {}

    private final Map<Long, Bloco> blocos = new LinkedHashMap<>();

    public void set(int x, int y, int z, BlockData dados) {
        blocos.put(chave(x, y, z), new Bloco(x, y, z, dados));
    }

    public void set(int x, int y, int z, Material material) {
        set(x, y, z, material.createBlockData());
    }

    /** Preenche o cubo entre os dois cantos (inclusivos, em qualquer ordem). */
    public void preencher(int x1, int y1, int z1, int x2, int y2, int z2, BlockData dados) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, dados);
                }
            }
        }
    }

    public void preencher(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        preencher(x1, y1, z1, x2, y2, z2, material.createBlockData());
    }

    /** O que já foi gravado na posição, ou null se nada foi. */
    public Material get(int x, int y, int z) {
        Bloco bloco = blocos.get(chave(x, y, z));
        return bloco == null ? null : bloco.dados().getMaterial();
    }

    public List<Bloco> getBlocos() {
        return new ArrayList<>(blocos.values());
    }

    // --- Fábricas de BlockData com estado, pra não repetir cast em toda estrutura ---

    /** Escada com a parte alta virada pra {@code face}; {@code invertida} = presa no teto. */
    public static BlockData escada(Material material, BlockFace face, boolean invertida) {
        Stairs escada = (Stairs) material.createBlockData();
        escada.setFacing(face);
        escada.setHalf(invertida ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        return escada;
    }

    public static BlockData laje(Material material, boolean emCima) {
        Slab laje = (Slab) material.createBlockData();
        laje.setType(emCima ? Slab.Type.TOP : Slab.Type.BOTTOM);
        return laje;
    }

    /** Vidro/grade/cerca com as conexões ligadas nas faces informadas (setado sem física não conecta sozinho). */
    public static BlockData conectado(Material material, BlockFace... faces) {
        MultipleFacing dados = (MultipleFacing) material.createBlockData();
        for (BlockFace face : faces) {
            if (dados.getAllowedFaces().contains(face)) dados.setFace(face, true);
        }
        return dados;
    }

    public static BlockData eixo(Material material, Axis eixo) {
        Orientable dados = (Orientable) material.createBlockData();
        dados.setAxis(eixo);
        return dados;
    }

    public static BlockData direcional(Material material, BlockFace face) {
        Directional dados = (Directional) material.createBlockData();
        dados.setFacing(face);
        return dados;
    }

    public static BlockData lanterna(Material material, boolean pendurada) {
        Lantern dados = (Lantern) material.createBlockData();
        dados.setHanging(pendurada);
        return dados;
    }

    private static long chave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
