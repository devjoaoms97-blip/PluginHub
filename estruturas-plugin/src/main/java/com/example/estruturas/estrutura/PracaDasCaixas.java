package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Vault;

import java.util.ArrayList;
import java.util.List;

import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Praça das Caixas (2026-11-18), no quadrante nordeste do spawn "spring", no lugar da cerejeira: um piso
 * redondo de raio 9 em volta de (22, -22) (relativo ao centro do spawn), um obelisco no meio e 8 pedestais em
 * roda (raio 6). Quatro têm uma caixa (o cofre do CaixasPlugin: voto, mina, boss e tesouro, cada um em cima
 * da cor dele) e quatro ficam vazios, pras caixas que vierem. As caixas ficam na metade virada pras
 * avenidas (norte e leste), de frente pra quem chega.
 *
 * <p>O spawn chama {@link #gerar} no lugar da árvore do quadrante NE, e a estrutura "spring-caixas" grava só
 * esse pedaço num spawn que já existe.
 */
final class PracaDasCaixas {

    static final int CX = 22, CZ = -22;
    static final double RAIO = 9.5;

    /** Um pedestal: posição relativa ao centro da praça, a caixa (null = vazio) e a cor do piso dele. */
    record Pedestal(int dx, int dz, String caixa, Material cor, boolean ominoso) {}

    static final List<Pedestal> PEDESTAIS = List.of(
            new Pedestal(-4, 4, "voto", Material.LIGHT_BLUE_CONCRETE, false),
            new Pedestal(-6, 0, "mina", Material.GRAY_CONCRETE, false),
            new Pedestal(0, 6, "tesouro", Material.YELLOW_CONCRETE, false),
            new Pedestal(-4, -4, "boss", Material.RED_CONCRETE, true),
            new Pedestal(0, -6, null, Material.WHITE_CONCRETE, false),
            new Pedestal(4, -4, null, Material.WHITE_CONCRETE, false),
            new Pedestal(6, 0, null, Material.WHITE_CONCRETE, false),
            new Pedestal(4, 4, null, Material.WHITE_CONCRETE, false));

    private PracaDasCaixas() {
    }

    static boolean dentro(int x, int z) {
        double dx = x - CX, dz = z - CZ;
        return Math.sqrt(dx * dx + dz * dz) <= RAIO;
    }

    static void gerar(Planta p) {
        // limpa (a árvore, o caminho e os bancos que ficavam aqui) e faz o piso
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > RAIO) continue;
                int x = CX + dx, z = CZ + dz;
                for (int y = 1; y <= 14; y++) p.set(x, y, z, Material.AIR);
                Material piso;
                if (r > RAIO - 1) piso = Material.STONE_BRICKS;
                else if (r < 2.5) piso = Material.POLISHED_ANDESITE;
                else {
                    // raios de sol: 16 fatias alternando diorito e andesito polidos
                    double ang = Math.atan2(dz, dx);
                    int fatia = (int) Math.floor((ang + Math.PI) / (Math.PI / 8));
                    piso = fatia % 2 == 0 ? Material.POLISHED_DIORITE : Material.POLISHED_ANDESITE;
                    if (r > 7.5 && r <= RAIO - 1) piso = Material.SMOOTH_STONE;
                }
                p.set(x, -1, z, Material.STONE);
                p.set(x, 0, z, piso);
            }
        }
        obelisco(p);
        for (Pedestal pe : PEDESTAIS) pedestal(p, pe);
        // lanternas baixas na borda, entre os pedestais
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI / 8 + i * Math.PI / 4;
            int x = CX + (int) Math.round(Math.cos(ang) * 8.4), z = CZ + (int) Math.round(Math.sin(ang) * 8.4);
            p.set(x, 1, z, Material.STONE_BRICK_WALL);
            p.set(x, 2, z, lanterna(Material.LANTERN, false));
        }
    }

    private static void obelisco(Planta p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean meio = dx == 0 && dz == 0;
                p.set(CX + dx, 1, CZ + dz, meio ? Material.CHISELED_STONE_BRICKS.createBlockData() : laje(Material.STONE_BRICK_SLAB, false));
            }
        }
        for (int y = 2; y <= 5; y++) p.set(CX, y, CZ, eixo(Material.QUARTZ_PILLAR, Axis.Y));
        p.set(CX, 6, CZ, Material.GOLD_BLOCK);
        p.set(CX, 7, CZ, Material.AMETHYST_CLUSTER);
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            p.set(CX + f.getModX(), 5, CZ + f.getModZ(), escada(Material.QUARTZ_STAIRS, f.getOppositeFace(), true));
        }
    }

    private static void pedestal(Planta p, Pedestal pe) {
        int x = CX + pe.dx(), z = CZ + pe.dz();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) p.set(x + a, 0, z + b, pe.cor());
        }
        p.set(x, 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
        if (pe.caixa() == null) {
            p.set(x, 2, z, Material.DECORATED_POT);
            return;
        }
        Vault v = (Vault) Material.VAULT.createBlockData();
        v.setFacing(paraFora(pe.dx(), pe.dz()));
        v.setOminous(pe.ominoso());
        p.set(x, 2, z, (BlockData) v);
    }

    /** A frente da caixa: pra fora da praça (de frente pra quem chega). */
    private static BlockFace paraFora(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? BlockFace.WEST : BlockFace.EAST;
        return dz < 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /** Cadastra as caixas no CaixasPlugin (o bloco do cofre de cada uma). */
    static List<String> comandos(String mundo, int ox, int oy, int oz) {
        List<String> c = new ArrayList<>();
        for (Pedestal pe : PEDESTAIS) {
            if (pe.caixa() == null) continue;
            c.add("caixas local " + pe.caixa() + " " + mundo + " " + (ox + CX + pe.dx()) + " " + (oy + 2) + " " + (oz + CZ + pe.dz()));
        }
        return c;
    }
}
