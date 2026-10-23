package com.example.terrenos.manager;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.TerrenoId;
import com.example.terrenos.mundo.Grade;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Volta um terreno a como foi gerado (abandono, ou leilão sem lances). São ~400 mil blocos por
 * terreno de 32x32, então a limpeza é feita aos poucos: algumas colunas de x por tick, numa fila,
 * pra não travar o servidor. As entidades de dentro (itens, molduras, suportes, animais) somem
 * antes; jogadores ficam.
 */
public class Limpador {

    private static final int COLUNAS_POR_TICK = 2;

    private final TerrenosPlugin plugin;
    private final Deque<Tarefa> fila = new ArrayDeque<>();

    private record Tarefa(TerrenoId id, int[] proximaColuna) {
    }

    public Limpador(TerrenosPlugin plugin) {
        this.plugin = plugin;
    }

    public void limpar(TerrenoId id) {
        World mundo = plugin.getMundo();
        if (mundo == null) return;
        Grade g = plugin.getGrade();
        int minX = g.minX(id), minZ = g.minZ(id), t = g.getTamanho();
        BoundingBox caixa = new BoundingBox(minX, mundo.getMinHeight(), minZ, minX + t, mundo.getMaxHeight(), minZ + t);
        for (Entity entidade : mundo.getNearbyEntities(caixa)) {
            if (!(entidade instanceof Player)) entidade.remove();
        }
        fila.add(new Tarefa(id, new int[]{0}));
    }

    public boolean ocupado(TerrenoId id) {
        return fila.stream().anyMatch(tarefa -> tarefa.id().equals(id));
    }

    /** Roda a cada tick. */
    public void tick() {
        Tarefa tarefa = fila.peek();
        if (tarefa == null) return;
        World mundo = plugin.getMundo();
        if (mundo == null) {
            fila.clear();
            return;
        }
        Grade g = plugin.getGrade();
        int minX = g.minX(tarefa.id()), minZ = g.minZ(tarefa.id()), t = g.getTamanho();
        int minY = mundo.getMinHeight(), maxY = mundo.getMaxHeight() - 1;
        for (int n = 0; n < COLUNAS_POR_TICK && tarefa.proximaColuna()[0] < t; n++) {
            int x = minX + tarefa.proximaColuna()[0]++;
            for (int z = minZ; z < minZ + t; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Material certo = g.blocoEm(x, y, z, minY);
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (bloco.getType() != certo) bloco.setType(certo, false);
                }
            }
        }
        if (tarefa.proximaColuna()[0] >= t) fila.poll();
    }
}
