package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * O desenho 7x7 de cada fazenda, no sistema local: (lx, ly, lz) com o centro em (0, 0, 0), o chão em
 * ly = -1 e a FRENTE ao norte (lz = -3), onde fica a abertura da cerca com o NPC. A {@link Construcao}
 * gira o desenho pro lado de quem colocou (e gira junto os blocos com direção).
 *
 * <p>Todas têm a mesma moldura (chão da borda, cerca, postes nos cantos e luz em cima), com o
 * material de cada uma; o miolo 5x5 (lx e lz de -2 a 2) e os enfeites vivos são de cada tipo.
 */
final class Planos {

    private Planos() {
    }

    /** Um bicho ou enfeite parado, em coordenadas locais (o centro do bloco é +0,5). */
    record Enfeite(EntityType tipo, double lx, double ly, double lz, float yawLocal, DyeColor cor, Material item) {
        static Enfeite bicho(EntityType tipo, double lx, double lz, float yaw) {
            return new Enfeite(tipo, lx, 0, lz, yaw, null, null);
        }
    }

    /** A moldura: chão da borda, cerca (ou muro), poste dos cantos e a luz em cima do poste. */
    private record Estilo(Material borda, Material cerca, Material poste, Material luz) {}

    private static Estilo estilo(String tipo) {
        return switch (tipo) {
            case "horta" -> new Estilo(Material.COARSE_DIRT, Material.OAK_FENCE, Material.STRIPPED_OAK_LOG, Material.LANTERN);
            case "galinheiro" -> new Estilo(Material.BIRCH_PLANKS, Material.BIRCH_FENCE, Material.BIRCH_LOG, Material.LANTERN);
            case "pomar" -> new Estilo(Material.MOSS_BLOCK, Material.OAK_FENCE, Material.OAK_LOG, Material.LANTERN);
            case "pocilga" -> new Estilo(Material.PACKED_MUD, Material.SPRUCE_FENCE, Material.SPRUCE_LOG, Material.LANTERN);
            case "trigal" -> new Estilo(Material.DIRT_PATH, Material.OAK_FENCE, Material.STRIPPED_SPRUCE_LOG, Material.LANTERN);
            case "curral" -> new Estilo(Material.COARSE_DIRT, Material.DARK_OAK_FENCE, Material.DARK_OAK_LOG, Material.LANTERN);
            case "ovelhas" -> new Estilo(Material.PODZOL, Material.SPRUCE_FENCE, Material.STRIPPED_SPRUCE_LOG, Material.LANTERN);
            case "apiario" -> new Estilo(Material.MOSS_BLOCK, Material.BIRCH_FENCE, Material.STRIPPED_BIRCH_LOG, Material.LANTERN);
            case "aboboras" -> new Estilo(Material.COARSE_DIRT, Material.DARK_OAK_FENCE, Material.STRIPPED_DARK_OAK_LOG, Material.LANTERN);
            case "cogumelos" -> new Estilo(Material.PODZOL, Material.MANGROVE_FENCE, Material.MUSHROOM_STEM, Material.LANTERN);
            case "verrugas" -> new Estilo(Material.NETHER_BRICKS, Material.NETHER_BRICK_FENCE, Material.CHISELED_NETHER_BRICKS, Material.SOUL_LANTERN);
            case "chorus" -> new Estilo(Material.PURPUR_BLOCK, Material.END_STONE_BRICK_WALL, Material.PURPUR_PILLAR, Material.END_ROD);
            default -> new Estilo(Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE, Material.SPRUCE_LOG, Material.LANTERN);
        };
    }

    /** O bloco do desenho em (lx, ly, lz), null = ar. */
    static BlockData bloco(String tipo, int lx, int ly, int lz) {
        int r = Fazenda.RAIO;
        boolean bordaX = Math.abs(lx) == r, bordaZ = Math.abs(lz) == r;
        if (bordaX || bordaZ) return moldura(estilo(tipo), lx, ly, lz, bordaX && bordaZ);
        return miolo(tipo, lx, ly, lz);
    }

    private static BlockData moldura(Estilo e, int lx, int ly, int lz, boolean canto) {
        if (ly == -1) return dado(e.borda());
        if (canto) {
            if (ly == 0) return dado(e.poste());
            if (ly == 1) return dado(e.luz());
            return null;
        }
        if (ly != 0 || (lx == 0 && lz == -Fazenda.RAIO)) return null; // a abertura do NPC
        BlockData cerca = dado(e.cerca());
        // liga com as vizinhas da borda (e os postes), menos pro lado da abertura
        BlockFace[] lados = Math.abs(lz) == Fazenda.RAIO ? new BlockFace[]{BlockFace.EAST, BlockFace.WEST}
                : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
        for (BlockFace lado : lados) {
            if (lx + lado.getModX() == 0 && lz + lado.getModZ() == -Fazenda.RAIO) continue;
            if (cerca instanceof MultipleFacing mf) mf.setFace(lado, true);
            else if (cerca instanceof Wall w) w.setHeight(lado, Wall.Height.LOW);
        }
        if (cerca instanceof Wall w) w.setUp(false);
        return cerca;
    }

    // ---------------------------------------------------------------------
    // O miolo de cada uma (lx e lz de -2 a 2; lz = -2 é a fileira da frente)
    // ---------------------------------------------------------------------

    private static BlockData miolo(String tipo, int lx, int ly, int lz) {
        return switch (tipo) {
            case "horta" -> horta(lx, ly, lz);
            case "galinheiro" -> galinheiro(lx, ly, lz);
            case "pomar" -> pomar(lx, ly, lz);
            case "pocilga" -> pocilga(lx, ly, lz);
            case "trigal" -> trigal(lx, ly, lz);
            case "curral" -> curral(lx, ly, lz);
            case "ovelhas" -> ovelhas(lx, ly, lz);
            case "apiario" -> apiario(lx, ly, lz);
            case "aboboras" -> aboboras(lx, ly, lz);
            case "cogumelos" -> cogumelos(lx, ly, lz);
            case "verrugas" -> verrugas(lx, ly, lz);
            case "chorus" -> chorus(lx, ly, lz);
            default -> ly == -1 ? dado(Material.GRASS_BLOCK) : null;
        };
    }

    /** Canteiros de cenoura, batata e beterraba com um rego d'água no meio, composteira e espantalho no fundo. */
    private static BlockData horta(int lx, int ly, int lz) {
        boolean frente = lz == -2;
        boolean fundoEsq = lx == -2 && lz == 2, fundoDir = lx == 2 && lz == 2;
        if (ly == -1) {
            if (frente) return dado(Material.DIRT_PATH);
            if (lx == 0) return dado(Material.WATER);
            if (fundoEsq || fundoDir) return dado(Material.COARSE_DIRT);
            return terraArada();
        }
        if (ly == 0) {
            if (frente || lx == 0) return null;
            if (fundoEsq) return dado(Material.COMPOSTER);
            if (fundoDir) return dado(Material.OAK_FENCE);
            Material planta = lx == -2 ? Material.CARROTS : lx == -1 ? Material.POTATOES : lx == 1 ? Material.BEETROOTS : Material.CARROTS;
            return madura(planta);
        }
        if (ly == 1 && fundoDir) return virado(Material.CARVED_PUMPKIN, BlockFace.NORTH); // o espantalho
        return null;
    }

    /** Galinheiro de tábuas com telhado de feno no fundo, ninhos de feno e flores. */
    private static BlockData galinheiro(int lx, int ly, int lz) {
        boolean casinha = lx <= 0 && lz >= 1;
        if (ly == -1) return dado(casinha ? Material.BIRCH_PLANKS : lz == -2 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
        if (ly == 0) {
            if (casinha) return lx == -1 && lz == 1 ? null : dado(Material.BIRCH_PLANKS); // porta aberta
            if (lx == 2 && lz >= 1) return dado(Material.HAY_BLOCK); // ninhos
            if (lx == -2 && lz == -1) return dado(Material.DANDELION);
            if (lx == 2 && lz == -2) return dado(Material.POPPY);
            return null;
        }
        if (ly == 1 && casinha) return dado(Material.HAY_BLOCK);
        return null;
    }

    /** Duas macieiras (uma florida) com cestos de colheita na frente. */
    private static BlockData pomar(int lx, int ly, int lz) {
        if (ly == -1) return dado(lz == -2 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
        boolean troncoA = lx == -1 && lz == 0, troncoB = lx == 1 && lz == 1;
        if (ly == 0) {
            if (troncoA || troncoB) return dado(Material.OAK_LOG);
            if (lz == -2 && Math.abs(lx) == 2) return virado(Material.BARREL, BlockFace.UP);
            if (lx == 2 && lz == -1) return dado(Material.SHORT_GRASS);
            return null;
        }
        if (ly == 1 && (troncoA || troncoB)) return dado(Material.OAK_LOG);
        boolean copaA2 = Math.abs(lx + 1) <= 1 && Math.abs(lz) <= 1, copaB2 = Math.abs(lx - 1) <= 1 && Math.abs(lz - 1) <= 1;
        if (ly == 2) {
            if (troncoA || troncoB) return dado(Material.OAK_LOG);
            if (copaB2) return folhas(Material.FLOWERING_AZALEA_LEAVES);
            if (copaA2) return folhas(Material.OAK_LEAVES);
        }
        if (ly == 3) {
            if (Math.abs(lx + 1) + Math.abs(lz) <= 1) return folhas(Material.OAK_LEAVES);
            if (Math.abs(lx - 1) + Math.abs(lz - 1) <= 1) return folhas(Material.FLOWERING_AZALEA_LEAVES);
        }
        return null;
    }

    /** Chiqueiro de lama, cocho (composteiras) e feno no fundo. */
    private static BlockData pocilga(int lx, int ly, int lz) {
        if (ly == -1) {
            if (lz == -2) return dado(Material.PACKED_MUD);
            return dado((lx + lz) % 2 == 0 ? Material.MUD : Material.COARSE_DIRT);
        }
        if (ly == 0 && lz == 2) {
            if (lx >= -1 && lx <= 0) return dado(Material.COMPOSTER);
            if (lx == 2) return dado(Material.HAY_BLOCK);
        }
        return null;
    }

    /** Trigal maduro com rego d'água, fardos de feno e um mastro de moinho com pás de lã. */
    private static BlockData trigal(int lx, int ly, int lz) {
        boolean moinho = lx == -2 && lz == 2;
        boolean feno = lz == 2 && lx >= 1;
        if (ly == -1) {
            if (lz == -2) return dado(Material.DIRT_PATH);
            if (lx == 0 && lz < 2) return dado(Material.WATER);
            if (moinho || feno) return dado(Material.COARSE_DIRT);
            return terraArada();
        }
        if (moinho) {
            if (ly <= 2) return dado(Material.STRIPPED_SPRUCE_LOG);
            if (ly == 3) return dado(Material.SPRUCE_PLANKS);
            return null;
        }
        // as pás do moinho: uma cruz de lã na frente do mastro, virada pro meio da fazenda
        if (lz == 1 && ((lx == -2 && ly >= 2 && ly <= 4) || (lx == -1 && ly == 3))) return dado(Material.WHITE_WOOL);
        if (ly == 0) {
            if (lz == -2 || (lx == 0 && lz < 2)) return null;
            if (feno) return dado(Material.HAY_BLOCK);
            if (lx == -1 && lz == 2) return null;
            if (lx == 0 && lz == 2) return dado(Material.COMPOSTER);
            return madura(Material.WHEAT);
        }
        if (ly == 1 && lx == 2 && lz == 2) return dado(Material.HAY_BLOCK);
        return null;
    }

    /** Pasto com feno empilhado e um caldeirão d'água de bebedouro. */
    private static BlockData curral(int lx, int ly, int lz) {
        if (ly == -1) return dado(lz == -2 ? Material.DIRT_PATH : (lx * 3 + lz) % 4 == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
        if (lz == 2 && lx <= -1) {
            if (ly == 0) return dado(Material.HAY_BLOCK);
            if (ly == 1 && lx == -2) return dado(Material.HAY_BLOCK);
        }
        if (ly == 0 && lx == 2 && lz == 2) {
            BlockData c = dado(Material.WATER_CAULDRON);
            if (c instanceof Levelled l) l.setLevel(l.getMaximumLevel());
            return c;
        }
        if (ly == 0 && lx == 2 && lz == -1) return dado(Material.SHORT_GRASS);
        return null;
    }

    /** Pasto com fardos de lã colorida e feno no fundo. */
    private static BlockData ovelhas(int lx, int ly, int lz) {
        if (ly == -1) return dado(lz == -2 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
        if (lz == 2) {
            if (ly == 0 && lx == -2) return dado(Material.WHITE_WOOL);
            if (ly == 0 && lx == -1) return dado(Material.LIGHT_GRAY_WOOL);
            if (ly == 1 && lx == -2) return dado(Material.PINK_WOOL);
            if (ly == 0 && lx == 2) return dado(Material.HAY_BLOCK);
        }
        if (ly == 0 && lx == 2 && lz == -1) return dado(Material.SHORT_GRASS);
        return null;
    }

    /** Colmeias cheias de mel no fundo e um jardim de flores. */
    private static BlockData apiario(int lx, int ly, int lz) {
        if (ly == -1) return dado(lz == -2 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
        if (lz == 2 && Math.abs(lx) == 2) {
            if (ly == 0) return dado(Material.STRIPPED_BIRCH_LOG);
            if (ly == 1) {
                BlockData c = virado(Material.BEEHIVE, BlockFace.NORTH);
                if (c instanceof Beehive b) b.setHoneyLevel(b.getMaximumHoneyLevel());
                return c;
            }
        }
        if (ly == 0 && lz > -2 && !(lz == 2 && Math.abs(lx) == 2)) {
            Material[] flores = {Material.POPPY, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM, Material.DANDELION, Material.AZURE_BLUET};
            if ((lx + lz) % 2 == 0) return dado(flores[Math.floorMod(lx * 3 + lz * 5, flores.length)]);
        }
        return null;
    }

    /** Abóboras de um lado, melancias do outro, com os caules ligados e um rego no meio. */
    private static BlockData aboboras(int lx, int ly, int lz) {
        if (ly == -1) {
            if (lz == -2) return dado(Material.DIRT_PATH);
            if (lx == 0) return dado(Material.WATER);
            return terraArada();
        }
        if (ly != 0 || lz == -2 || lx == 0) return null;
        boolean abobora = lx < 0;
        boolean comFruto = lz == -1 || lz == 1;
        if (Math.abs(lx) == 2) {
            if (comFruto) return dado(abobora ? Material.PUMPKIN : Material.MELON);
            if (lz == 2) return abobora ? virado(Material.CARVED_PUMPKIN, BlockFace.NORTH) : dado(Material.MELON);
            return null;
        }
        if (comFruto) return virado(abobora ? Material.ATTACHED_PUMPKIN_STEM : Material.ATTACHED_MELON_STEM, abobora ? BlockFace.WEST : BlockFace.EAST);
        BlockData caule = dado(abobora ? Material.PUMPKIN_STEM : Material.MELON_STEM);
        if (caule instanceof Ageable a) a.setAge(4);
        return caule;
    }

    /** Um cogumelo gigante vermelho no fundo, sobre micélio, e cogumelos pequenos em volta. */
    private static BlockData cogumelos(int lx, int ly, int lz) {
        if (ly == -1) return dado(lz == -2 ? Material.PODZOL : Material.MYCELIUM);
        boolean pe = lx == 0 && lz == 1;
        if (pe && ly <= 2) return dado(Material.MUSHROOM_STEM);
        boolean chapeuTopo = Math.abs(lx) <= 1 && lz >= 0 && lz <= 2;
        if (ly == 3 && chapeuTopo) return dado(Material.RED_MUSHROOM_BLOCK);
        if (ly == 2 && !chapeuTopo && Math.abs(lx) <= 2 && lz >= -1 && !(Math.abs(lx) == 2 && lz == -1)) return dado(Material.RED_MUSHROOM_BLOCK);
        if (ly == 0) {
            if (lz == -2 && Math.abs(lx) == 2) return dado(lx < 0 ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM);
            if (lz == -1 && Math.abs(lx) == 1) return dado(lx < 0 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM);
        }
        return null;
    }

    /** Canteiros de verruga madura na areia das almas, com uma fogueira das almas no fundo. */
    private static BlockData verrugas(int lx, int ly, int lz) {
        if (ly == -1) {
            if (lz == -2) return dado(Material.POLISHED_BLACKSTONE);
            if (lx == 0) return dado(Material.SOUL_SOIL);
            return dado(Material.SOUL_SAND);
        }
        if (ly == 0) {
            if (lz == -2) return null;
            if (lx == 0) {
                if (lz != 2) return null;
                BlockData f = dado(Material.SOUL_CAMPFIRE);
                if (f instanceof Campfire c) c.setSignalFire(false);
                return f;
            }
            return madura(Material.NETHER_WART);
        }
        return null;
    }

    /** Hastes de chorus florindo sobre pedra do End. */
    private static BlockData chorus(int lx, int ly, int lz) {
        if (ly == -1) return dado(lz == -2 ? Material.END_STONE_BRICKS : Material.END_STONE);
        int altura = haste(lx, lz);
        if (altura == 0 || ly >= altura) return null;
        if (ly == altura - 1) return dado(Material.CHORUS_FLOWER);
        BlockData p = dado(Material.CHORUS_PLANT);
        if (p instanceof MultipleFacing mf) {
            mf.setFace(BlockFace.DOWN, true);
            mf.setFace(BlockFace.UP, true);
        }
        return p;
    }

    /** Altura de cada haste de chorus (0 = sem haste). */
    private static int haste(int lx, int lz) {
        if (lx == -1 && lz == 1) return 4;
        if (lx == 1 && lz == 0) return 3;
        if (lx == 2 && lz == 2) return 2;
        if (lx == -2 && lz == -1) return 2;
        if (lx == 0 && lz == 2) return 5;
        return 0;
    }

    // ---------------------------------------------------------------------
    // Enfeites vivos (bichos parados)
    // ---------------------------------------------------------------------

    static List<Enfeite> enfeites(String tipo) {
        return switch (tipo) {
            case "galinheiro" -> List.of(Enfeite.bicho(EntityType.CHICKEN, 1, 0, 200), Enfeite.bicho(EntityType.CHICKEN, 0.3, -1, 140),
                    Enfeite.bicho(EntityType.CHICKEN, 1.6, 1.4, 260), Enfeite.bicho(EntityType.CHICKEN, -1.4, 0, 30));
            case "pocilga" -> List.of(Enfeite.bicho(EntityType.PIG, -1, 0, 120), Enfeite.bicho(EntityType.PIG, 1, -0.6, 220));
            case "curral" -> List.of(Enfeite.bicho(EntityType.COW, -0.6, -0.4, 90), Enfeite.bicho(EntityType.COW, 1.2, 0.6, 250));
            case "ovelhas" -> List.of(new Enfeite(EntityType.SHEEP, -1, 0, 0, 100, DyeColor.WHITE, null),
                    new Enfeite(EntityType.SHEEP, 1, 0, -0.8, 200, DyeColor.LIGHT_GRAY, null),
                    new Enfeite(EntityType.SHEEP, 0.8, 0, 1, 300, DyeColor.PINK, null));
            case "apiario" -> List.of(new Enfeite(EntityType.BEE, -1, 1.3, 0, 45, null, null), new Enfeite(EntityType.BEE, 1, 1.6, -1, 210, null, null),
                    new Enfeite(EntityType.BEE, 0.2, 1.1, 1.2, 120, null, null));
            // maçãs penduradas nas copas
            case "pomar" -> List.of(new Enfeite(EntityType.ITEM_DISPLAY, -1.9, 1.75, -0.6, 0, null, Material.APPLE),
                    new Enfeite(EntityType.ITEM_DISPLAY, -0.2, 1.75, 0.8, 0, null, Material.APPLE),
                    new Enfeite(EntityType.ITEM_DISPLAY, 1.9, 1.75, 0.4, 0, null, Material.APPLE),
                    new Enfeite(EntityType.ITEM_DISPLAY, 0.6, 1.75, 1.9, 0, null, Material.APPLE));
            default -> List.of();
        };
    }

    // ---------------------------------------------------------------------

    private static BlockData dado(Material m) {
        return Bukkit.createBlockData(m);
    }

    private static BlockData terraArada() {
        BlockData d = dado(Material.FARMLAND);
        if (d instanceof Farmland f) f.setMoisture(f.getMaximumMoisture());
        return d;
    }

    private static BlockData madura(Material planta) {
        BlockData d = dado(planta);
        if (d instanceof Ageable a) a.setAge(a.getMaximumAge());
        return d;
    }

    private static BlockData folhas(Material m) {
        BlockData d = dado(m);
        if (d instanceof Leaves l) l.setPersistent(true);
        return d;
    }

    private static BlockData virado(Material m, BlockFace lado) {
        BlockData d = dado(m);
        if (d instanceof Directional dir) dir.setFacing(lado);
        return d;
    }
}
