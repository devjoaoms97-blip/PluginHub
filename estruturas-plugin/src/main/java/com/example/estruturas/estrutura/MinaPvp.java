package com.example.estruturas.estrutura;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.List;

import static com.example.estruturas.estrutura.Planta.eixo;
import static com.example.estruturas.estrutura.Planta.escada;
import static com.example.estruturas.estrutura.Planta.laje;
import static com.example.estruturas.estrutura.Planta.lanterna;

/**
 * Mina PvP (2026-11-17): recinto murado colado no fundo da Avenida das Minas, depois do portão norte dela.
 * Primeiro vem o <b>saguão</b> (z -186 a -196), seguro, onde chegam a {@code /warp minapvp} e o
 * {@code /mina pvp}; depois de um muro baixo com o arco do "PVP", o <b>campo</b> (z -199 a -249), com o
 * poço da mina no meio (41x41, x -20..20, z -244..-204) e uma calçada larga em volta, com ruínas pra se
 * esconder. O PvP do campo é do AreasPlugin (caixa "mina pvp"); a mina, do RankupPlugin.
 *
 * <p>Mesma origem da Avenida das Minas: {@code /estrutura construir minapvp spawn 0 65 -21}. x e z são
 * ABSOLUTOS e y é relativo ao chão (y = 0 é o piso em 64). Como na avenida, o poço e a borda de tijolo
 * dele são do montar-mapa e do plugin de minas: aqui, ali dentro, só entra ar acima do chão.
 */
public class MinaPvp implements Estrutura {

    private static final int ORIGEM_Z = -21;

    static final int X_EXT = 31, X_INT = 28;
    static final int Z_SUL = -186;           // encosta na face de fora da muralha norte da avenida (-185)
    static final int Z_MURO_SAGUAO = -197;   // muro entre o saguão e o campo (-197 e -198)
    static final int Z_CAMPO = -199;         // o PvP começa aqui
    static final int Z_NORTE_INT = -249, Z_NORTE = -252;
    // o poço (sem a borda): x -20..20, z -244..-204
    static final int POCO_X = 20, POCO_Z_SUL = -204, POCO_Z_NORTE = -244;

    private static final int ALTURA = 12;
    private static final int Y_LIMPAR = 22;
    private static final int MEIO_ARCO = 4;  // abertura x -4..4 no muro do saguão
    // A borda do pulo (2026-11-18): o piso do arco fica DEGRAU blocos acima do campo, e uma escada no
    // saguão sobe até ela. Do campo não dá pra subir de volta: só se sai por comando (/spawn, /warp...),
    // que o "em combate" do CombatePlugin barra. Mesmo esquema do túnel da arena do boss.
    static final int DEGRAU = 4;

    private static final Material PEDRA = Material.STONE_BRICKS;
    private static final Material FAIXA = Material.SMOOTH_STONE;
    private static final Material VERMELHO = Material.RED_NETHER_BRICKS;
    private static final Material CALCADA = Material.POLISHED_ANDESITE;
    private static final Material LUZ = Material.SHROOMLIGHT;

    private Planta p;

    @Override
    public String getId() {
        return "minapvp";
    }

    @Override
    public String getNome() {
        return "Mina PvP";
    }

    /** O poço ou a borda de tijolo dele (x, z absolutos). */
    static boolean noPoco(int x, int z) {
        return Math.abs(x) <= POCO_X + 1 && z <= POCO_Z_SUL + 1 && z >= POCO_Z_NORTE - 1;
    }

    static boolean permitido(int x, int y, int z, Material m) {
        if (z > Z_SUL || z < Z_NORTE || Math.abs(x) > X_EXT) return false;
        if (noPoco(x, z)) return y >= 1 && m == Material.AIR;
        return true;
    }

    private void set(int x, int y, int z, BlockData d) {
        if (!permitido(x, y, z, d.getMaterial())) return;
        p.set(x, y, z - ORIGEM_Z, d);
    }

    private void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    private Material get(int x, int y, int z) {
        return p.get(x, y, z - ORIGEM_Z);
    }

    @Override
    public void gerar(Planta planta) {
        this.p = planta;
        limpar();
        piso();
        muralha();
        torres();
        muroDoSaguao();
        saguao();
        campo();
        luzes();
        verificar();
    }

    private static Material pedra(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h = Math.floorMod(h ^ (h >>> 13), 100);
        if (h < 62) return PEDRA;
        if (h < 86) return Material.MOSSY_STONE_BRICKS;
        return Material.CRACKED_STONE_BRICKS;
    }

    private static BlockData folha(Material m) {
        Leaves f = (Leaves) m.createBlockData();
        f.setPersistent(true);
        return f;
    }

    // ------------------------------------------------------------------ base

    private void limpar() {
        BlockData ar = Material.AIR.createBlockData();
        for (int x = -X_EXT; x <= X_EXT; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                for (int y = 1; y <= Y_LIMPAR; y++) set(x, y, z, ar);
            }
        }
    }

    /** Fundação de pedra e o piso: lajotas no saguão, calçada mais gasta (com cascalho de pedra) no campo. */
    private void piso() {
        for (int x = -X_EXT; x <= X_EXT; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                set(x, -1, z, Material.STONE);
                if (Math.abs(x) > X_INT || z < Z_NORTE_INT) continue;
                Material m;
                if (z > Z_MURO_SAGUAO) {
                    m = Math.floorMod(x, 8) == 0 ? FAIXA : CALCADA;
                } else {
                    int h = Math.floorMod((x * 31) ^ (z * 17), 9);
                    m = Math.floorMod(z, 8) == 0 ? FAIXA : h == 0 ? Material.COBBLESTONE : h == 1 ? Material.TUFF : CALCADA;
                }
                set(x, 0, z, m);
            }
        }
        // caminho do portão da avenida até o arco do campo
        for (int x = -3; x <= 3; x++) {
            for (int z = Z_MURO_SAGUAO + 2; z <= Z_SUL; z++) {
                set(x, 0, z, Math.abs(x) == 3 ? PEDRA : Math.floorMod(z, 6) == 0 ? FAIXA : Material.POLISHED_DIORITE);
            }
        }
    }

    // ------------------------------------------------------------------ muralha

    /** Muralha de 3 de espessura (leste, oeste e norte), com faixas, pilastras de luz a cada 8 e ameias. */
    private void muralha() {
        for (int x = -X_EXT; x <= X_EXT; x++) {
            for (int z = Z_NORTE; z <= Z_SUL; z++) {
                int ax = Math.abs(x);
                boolean lado = ax > X_INT;
                boolean fundo = z < Z_NORTE_INT;
                if (!lado && !fundo) continue;
                int d = lado ? ax - (X_INT + 1) : (Z_NORTE_INT - 1) - z; // 0 = face de dentro
                int u = lado ? z : x;
                for (int y = -1; y <= ALTURA; y++) {
                    Material m = (y == 1 || y == 7 || y == ALTURA) ? FAIXA : pedra(x, y, z);
                    if (d == 2 && y == 10) m = VERMELHO;
                    if (d == 0 && Math.floorMod(u, 8) == 0 && y >= 2 && y <= 11) {
                        m = (y == 4 || y == 10) ? LUZ : Material.CHISELED_STONE_BRICKS;
                    }
                    set(x, y, z, m);
                }
                if (d != 1 && Math.floorMod(u, 2) == 0) set(x, ALTURA + 1, z, pedra(x, ALTURA + 1, z));
                if (d == 1 && Math.floorMod(u, 8) == 4) set(x, ALTURA + 1, z, lanterna(Material.LANTERN, false));
            }
        }
    }

    /** Torres 5x5 nos dois cantos do fundo e dos dois lados do muro do saguão. */
    private void torres() {
        for (int s : new int[]{-1, 1}) {
            for (int z1 : new int[]{Z_NORTE, Z_MURO_SAGUAO - 2}) {
                for (int ax = X_EXT - 4; ax <= X_EXT; ax++) {
                    int x = s * ax;
                    for (int z = z1; z <= z1 + 4; z++) {
                        boolean beirada = ax == X_EXT - 4 || ax == X_EXT || z == z1 || z == z1 + 4;
                        for (int y = -1; y <= 20; y++) {
                            Material m = (y == 1 || y == 7 || y == 12 || y == 20) ? FAIXA : pedra(x, y, z);
                            if (beirada && (y == 9 || y == 16)) m = VERMELHO;
                            set(x, y, z, m);
                        }
                        if (beirada && Math.floorMod(x + z, 2) == 0) set(x, 21, z, pedra(x, 21, z));
                    }
                }
                set(s * (X_EXT - 2), 21, z1 + 2, lanterna(Material.LANTERN, false));
            }
        }
    }

    // ------------------------------------------------------------------ saguão (seguro)

    /**
     * Muro entre o saguão e o campo: tijolo até a cintura e grade em cima (dá pra ver a mina), com o arco
     * do meio e o letreiro "PVP" vermelho em cima dele, lido do saguão. O piso do arco é a borda do pulo
     * (DEGRAU acima do campo): uma escada no saguão sobe até ela, e do lado do campo a parede é lisa.
     */
    private void muroDoSaguao() {
        for (int x = -X_INT; x <= X_INT; x++) {
            for (int z = Z_MURO_SAGUAO - 1; z <= Z_MURO_SAGUAO; z++) {
                if (Math.abs(x) <= MEIO_ARCO) {
                    for (int y = 1; y <= 7; y++) set(x, y, z, Material.AIR);
                    for (int y = 0; y < DEGRAU; y++) set(x, y, z, pedra(x, y, z));
                    set(x, DEGRAU, z, Math.abs(x) == MEIO_ARCO ? PEDRA : Material.POLISHED_DIORITE);
                    continue;
                }
                // tijolo até a cintura (y 1..3) e, na face do saguão, grade até o y 6 com laje em cima
                set(x, 1, z, pedra(x, 1, z));
                set(x, 2, z, pedra(x, 2, z));
                set(x, 3, z, FAIXA);
                if (z == Z_MURO_SAGUAO) {
                    for (int y = 4; y <= 6; y++) set(x, y, z, Planta.conectado(Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST));
                    set(x, 7, z, laje(Material.SMOOTH_STONE_SLAB, false));
                }
            }
        }
        // pilares do arco e o arco em si (mesmo jeito dos portões)
        for (int s : new int[]{-1, 1}) {
            for (int y = 1; y <= 9; y++) {
                set(s * (MEIO_ARCO + 1), y, Z_MURO_SAGUAO, eixo(Material.CRIMSON_STEM, Axis.Y));
                set(s * (MEIO_ARCO + 1), y, Z_MURO_SAGUAO - 1, pedra(s * (MEIO_ARCO + 1), y, Z_MURO_SAGUAO - 1));
                set(s * (MEIO_ARCO + 2), y, Z_MURO_SAGUAO, eixo(Material.CHERRY_LOG, Axis.Y));
                set(s * (MEIO_ARCO + 2), y, Z_MURO_SAGUAO - 1, pedra(s * (MEIO_ARCO + 2), y, Z_MURO_SAGUAO - 1));
            }
        }
        for (int z = Z_MURO_SAGUAO - 1; z <= Z_MURO_SAGUAO; z++) {
            for (int x = -MEIO_ARCO; x <= MEIO_ARCO; x++) {
                set(x, 8, z, Math.abs(x) >= 3 ? escada(Material.STONE_BRICK_STAIRS, x < 0 ? BlockFace.WEST : BlockFace.EAST, true)
                        : Material.AIR.createBlockData());
                set(x, 9, z, pedra(x, 9, z));
            }
            for (int x = -MEIO_ARCO - 2; x <= MEIO_ARCO + 2; x++) set(x, 10, z, VERMELHO);
        }
        // o pedaço de muro entre o arco e as pontas do letreiro
        for (int s : new int[]{-1, 1}) {
            for (int ax = MEIO_ARCO + 3; ax <= 8; ax++) {
                for (int y = 4; y <= 10; y++) {
                    set(s * ax, y, Z_MURO_SAGUAO, pedra(s * ax, y, Z_MURO_SAGUAO));
                    set(s * ax, y, Z_MURO_SAGUAO - 1, pedra(s * ax, y, Z_MURO_SAGUAO - 1));
                }
            }
        }
        set(0, 10, Z_MURO_SAGUAO, LUZ);
        // letreiro em cima do arco, lido do saguão (de frente pro norte, a direita é +x)
        for (int x = -8; x <= 8; x++) {
            for (int y = 11; y <= 17; y++) {
                boolean borda = Math.abs(x) == 8 || y == 11 || y == 17;
                set(x, y, Z_MURO_SAGUAO, borda ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_BLACKSTONE);
                set(x, y, Z_MURO_SAGUAO - 1, pedra(x, y, Z_MURO_SAGUAO - 1));
            }
        }
        escrever("PVP", -6, 16, Z_MURO_SAGUAO, Material.REDSTONE_BLOCK);
        // a lanterna no alto do arco, longe da cabeça de quem pula da borda
        set(0, 8, Z_MURO_SAGUAO, lanterna(Material.LANTERN, true));
        escadaDoPulo();
    }

    /**
     * A escada do saguão até a borda do pulo: DEGRAU degraus de diorito polido subindo pro norte, encostados
     * no muro, com pedra por baixo. O de cima (z -196) termina no nível da borda: sobe-se andando, sem pular.
     */
    private void escadaDoPulo() {
        for (int i = 1; i <= DEGRAU; i++) {
            int z = Z_MURO_SAGUAO + 1 + DEGRAU - i; // -193 (1º degrau) .. -196 (último, na altura da borda)
            for (int x = -MEIO_ARCO; x <= MEIO_ARCO; x++) {
                for (int y = 1; y < i; y++) set(x, y, z, pedra(x, y, z));
                boolean beira = Math.abs(x) == MEIO_ARCO;
                set(x, i, z, escada(beira ? Material.STONE_BRICK_STAIRS : Material.POLISHED_DIORITE_STAIRS, BlockFace.NORTH, false));
            }
        }
    }

    private void escrever(String texto, int xInicio, int yTopo, int z, Material material) {
        int coluna = 0;
        for (char c : texto.toCharArray()) {
            String[] letra = AvenidaDasMinas.FONTE.get(c);
            for (int linha = 0; linha < letra.length; linha++) {
                for (int i = 0; i < letra[linha].length(); i++) {
                    if (letra[linha].charAt(i) == 'X') set(xInicio + coluna + i, yTopo - linha, z, material);
                }
            }
            coluna += letra[0].length() + 1;
        }
    }

    /** Bancos encostados nas laterais, canteiros de azaleia e os postes do caminho. */
    private void saguao() {
        for (int s : new int[]{-1, 1}) {
            BlockFace costas = s < 0 ? BlockFace.WEST : BlockFace.EAST;
            for (int z : new int[]{-189, -193}) {
                for (int dz = -1; dz <= 1; dz++) set(s * X_INT, 1, z + dz, escada(Material.CHERRY_STAIRS, costas, false));
            }
            // canteiros entre o caminho e as laterais
            for (int ax = 8; ax <= 22; ax++) {
                for (int z = -195; z <= -187; z++) {
                    boolean borda = ax == 8 || ax == 22 || z == -195 || z == -187;
                    int x = s * ax;
                    if (borda) {
                        set(x, 0, z, PEDRA);
                        set(x, 1, z, laje(Material.STONE_BRICK_SLAB, false));
                    } else {
                        set(x, 0, z, Material.GRASS_BLOCK);
                        int h = Math.floorMod(ax * 7 + z * 13, 10);
                        if (h < 4) set(x, 1, z, folha(h < 2 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                        else if (h < 6) set(x, 1, z, h == 4 ? Material.POPPY : Material.RED_TULIP);
                    }
                }
            }
            poste(s * 5, -188);
            poste(s * 5, -195);
        }
    }

    private void poste(int x, int z) {
        set(x, 1, z, Material.STONE_BRICK_WALL);
        set(x, 2, z, Material.DARK_OAK_FENCE);
        set(x, 3, z, Material.DARK_OAK_FENCE);
        set(x, 4, z, lanterna(Material.LANTERN, false));
    }

    // ------------------------------------------------------------------ campo (PvP)

    /** Ruínas (pedaços de muro) na calçada em volta do poço, pra se esconder e emboscar. */
    private void campo() {
        int[][] ruinas = {{-25, -210}, {25, -210}, {-25, -226}, {25, -226}, {-25, -240}, {25, -240}, {-12, -247}, {12, -247}};
        for (int[] r : ruinas) ruina(r[0], r[1]);
        // bandeiras vermelhas nas pilastras do campo
        for (int s : new int[]{-1, 1}) {
            for (int z = -200; z >= Z_NORTE_INT; z--) {
                if (Math.floorMod(z, 8) != 4) continue;
                set(s * X_INT, 6, z, Material.RED_WOOL);
            }
        }
    }

    /** Um pedaço de muro em L, de 2 a 3 de altura, com musgo. */
    private void ruina(int cx, int cz) {
        boolean deitado = Math.abs(cx) < 20; // as do fundo e da frente ficam ao longo do x
        for (int i = -2; i <= 2; i++) {
            int x = deitado ? cx + i : cx, z = deitado ? cz : cz + i;
            int altura = Math.abs(i) == 2 ? 1 : (i == 0 ? 3 : 2);
            for (int y = 1; y <= altura; y++) set(x, y, z, pedra(x, y, z));
            set(x, altura + 1, z, laje(Material.STONE_BRICK_SLAB, false));
        }
        int x = deitado ? cx + 2 : cx + (cx < 0 ? 1 : -1), z = deitado ? cz + (cz < -225 ? 1 : -1) : cz + 2;
        set(x, 1, z, Material.MOSSY_COBBLESTONE);
        set(x, 2, z, Material.MOSSY_COBBLESTONE_WALL);
    }

    private void luzes() {
        // em volta do poço (como nas outras minas), pra superfície da mina não ficar escura
        for (int d = -18; d <= 18; d += 3) {
            set(d, 0, POCO_Z_SUL + 2, LUZ);
            set(d, 0, POCO_Z_NORTE - 2, LUZ);
            set(-POCO_X - 2, 0, (POCO_Z_SUL + POCO_Z_NORTE) / 2 + d, LUZ);
            set(POCO_X + 2, 0, (POCO_Z_SUL + POCO_Z_NORTE) / 2 + d, LUZ);
        }
        for (int x = -24; x <= 24; x += 8) set(x, 0, -191, LUZ);
    }

    // ------------------------------------------------------------------ segurança

    /** Nada escrito no poço nem na borda (só ar acima do chão), e o lugar da warp livre. */
    private void verificar() {
        for (Planta.Bloco b : p.getBlocos()) {
            int z = b.z() + ORIGEM_Z;
            if (!permitido(b.x(), b.y(), z, b.dados().getMaterial())) {
                throw new IllegalStateException("Mina PvP: bloco proibido em " + b.x() + " " + (b.y() + 64) + " " + z);
            }
        }
        // do campo, nada encostado na parede do pulo que sirva de degrau pra voltar
        for (int x = -MEIO_ARCO - 2; x <= MEIO_ARCO + 2; x++) {
            for (int z = Z_CAMPO; z >= Z_CAMPO - 2; z--) {
                for (int y = 1; y <= DEGRAU + 1; y++) {
                    if (get(x, y, z) != null && get(x, y, z) != Material.AIR) {
                        throw new IllegalStateException("Mina PvP: bloco em " + x + " " + (y + 64) + " " + z + " vira degrau pra subir do campo");
                    }
                }
            }
        }
        for (int[] ponto : new int[][]{{0, -188}, {0, -192}}) {
            for (int y = 1; y <= 3; y++) {
                if (get(ponto[0], y, ponto[1]) != Material.AIR) {
                    throw new IllegalStateException("Mina PvP: o ponto de chegada " + ponto[0] + " " + ponto[1] + " está obstruído");
                }
            }
        }
    }

    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return List.of();
    }
}
