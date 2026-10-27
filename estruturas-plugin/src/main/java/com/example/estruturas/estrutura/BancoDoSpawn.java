package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.List;

import static com.example.estruturas.estrutura.Planta.conectado;
import static com.example.estruturas.estrutura.Planta.direcional;
import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Barraquinha do Bancário no spawn "spring": quartzo, ouro e toldo verde e branco, aberta pro oeste
 * (pra cerejeira do quadrante nordeste), entre a árvore e a fileira de barracas da avenida leste. O NPC
 * é o Bancário do OficinasPlugin (folha de cheque e depósito de cheques, o balcão é do ChequePlugin).
 *
 * <p>Use a MESMA origem do spawn: no mapa do servidor, {@code /estrutura construir banco spawn 0 65 43}.
 * Ocupa x 27..33 e z -17..-11 a partir do centro (o poste de luz que ficava em 30, -14 sai) e não
 * escreve fora disso: dá pra construir num spawn que já existe.
 */
public class BancoDoSpawn implements Estrutura {

    // Barraca 5 x 5 (x 28..32, z -16..-12): balcão em x 28, fundo em x 32, aba do toldo em x 27.
    private static final int X_FRENTE = 28;
    private static final int X_FUNDO = 32;
    private static final int Z1 = -16;
    private static final int Z2 = -12;
    private static final int Z_MEIO = -14;
    private static final int X_NPC = 29;

    @Override
    public String getId() {
        return "banco";
    }

    @Override
    public String getNome() {
        return "Bancário do Spawn";
    }

    @Override
    public void gerar(Planta p) {
        // limpa a área (o poste de luz e a grama alta que estiverem ali)
        for (int x = X_FRENTE - 1; x <= X_FUNDO + 1; x++) {
            for (int z = Z1 - 1; z <= Z2 + 1; z++) {
                for (int y = 1; y <= 7; y++) p.set(x, y, z, Material.AIR);
            }
        }
        // piso de diorito com borda de quartzo
        for (int x = X_FRENTE; x <= X_FUNDO; x++) {
            for (int z = Z1; z <= Z2; z++) {
                boolean borda = x == X_FRENTE || x == X_FUNDO || z == Z1 || z == Z2;
                p.set(x, 0, z, borda ? Material.SMOOTH_QUARTZ : Material.POLISHED_DIORITE);
            }
        }
        p.set(X_NPC + 1, 0, Z_MEIO, Material.GOLD_BLOCK);

        // pilares de quartzo nos cantos
        for (int x : new int[]{X_FRENTE, X_FUNDO}) {
            for (int z : new int[]{Z1, Z2}) {
                for (int y = 1; y <= 4; y++) p.set(x, y, z, eixo(Material.QUARTZ_PILLAR, Axis.Y));
            }
        }
        // fundo: parede de quartzo com o "cofre" (bloco de ferro e ouro) atrás do NPC
        for (int z = Z1 + 1; z <= Z2 - 1; z++) {
            for (int y = 1; y <= 4; y++) p.set(X_FUNDO, y, z, Material.QUARTZ_BRICKS);
        }
        p.set(X_FUNDO, 1, Z_MEIO, Material.IRON_BLOCK);
        p.set(X_FUNDO, 2, Z_MEIO, Material.GOLD_BLOCK);
        p.set(X_FUNDO, 3, Z_MEIO, Material.IRON_BLOCK);
        // laterais: quartzo embaixo e grade de ferro (o guichê)
        for (int z : new int[]{Z1, Z2}) {
            for (int x = X_FRENTE + 1; x <= X_FUNDO - 1; x++) {
                p.set(x, 1, z, Material.QUARTZ_BRICKS);
                for (int y = 2; y <= 3; y++) p.set(x, y, z, conectado(Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST));
                p.set(x, 4, z, Material.QUARTZ_BRICKS);
            }
        }
        // balcão: quartzo lavrado nas pontas, laje no meio (pra ver o NPC), balança de ouro e livro de registro
        p.set(X_FRENTE, 1, Z1 + 1, Material.CHISELED_QUARTZ_BLOCK);
        p.set(X_FRENTE, 1, Z2 - 1, Material.CHISELED_QUARTZ_BLOCK);
        p.set(X_FRENTE, 1, Z_MEIO, laje(Material.SMOOTH_QUARTZ_SLAB, true));
        p.set(X_FRENTE, 2, Z1 + 1, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        p.set(X_FRENTE, 2, Z2 - 1, direcional(Material.LECTERN, BlockFace.WEST));
        // dentro: barris de moedas e um baú
        p.set(X_FUNDO - 1, 1, Z1 + 1, direcional(Material.BARREL, BlockFace.UP));
        p.set(X_FUNDO - 1, 2, Z1 + 1, Material.RAW_GOLD_BLOCK);
        p.set(X_FUNDO - 1, 1, Z2 - 1, direcional(Material.CHEST, BlockFace.WEST));

        // toldo listrado verde e branco, aba um bloco mais baixa na frente, luz no meio e ouro no topo
        for (int x = X_FRENTE - 1; x <= X_FUNDO; x++) {
            for (int z = Z1; z <= Z2; z++) {
                Material cor = Math.floorMod(z, 2) == 0 ? Material.GREEN_WOOL : Material.WHITE_WOOL;
                p.set(x, x < X_FRENTE ? 4 : 5, z, cor);
            }
        }
        p.set((X_FRENTE + X_FUNDO) / 2, 5, Z_MEIO, Material.PEARLESCENT_FROGLIGHT);
        for (int x = X_FRENTE + 1; x <= X_FUNDO - 1; x++) {
            for (int z = Z1 + 1; z <= Z2 - 1; z++) p.set(x, 6, z, Material.GREEN_WOOL);
        }
        p.set((X_FRENTE + X_FUNDO) / 2, 7, Z_MEIO, Material.GOLD_BLOCK);
        p.set(X_FUNDO - 1, 4, Z1 + 1, lanterna(Material.LANTERN, true));
        p.set(X_FUNDO - 1, 4, Z2 - 1, lanterna(Material.LANTERN, true));
    }

    /** O Bancário (OficinasPlugin) atrás do balcão, olhando pro oeste (yaw 90). */
    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return List.of("oficina criar bancario bancario " + mundo + " " + (ox + X_NPC + 0.5) + " " + (oy + 1) + " "
                + (oz + Z_MEIO + 0.5) + " 90");
    }
}
