package com.example.tesouros;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * O mini templo da chegada do mundo dos tesouros ({@code /warp tesouros}), em volta do 0, 0: um
 * quadrado de pedra de 13x13, com quatro pilares, telhado em degraus e os quatro lados abertos. Em cada
 * lado, um holograma diz que região fica pra lá (Norte, Sul, Leste, Oeste) e se o X ou o Z do mapa
 * cresce ou diminui; um caminho sai de cada lado. No centro, um holograma explica como achar o tesouro.
 * <p>
 * É construído uma vez por mundo (o {@code templo.yml} guarda o UID do mundo e a versão do desenho):
 * muda a versão aqui, ou o mundo é refeito, e ele é construído de novo.
 */
public class Templo {

    /** Sobe quando o desenho muda, pra reconstruir nos servidores que já têm o templo. */
    private static final int VERSAO = 1;
    private static final String TAG = "tesouros_templo";

    private final TesourosPlugin plugin;
    private final File arquivo;

    public Templo(TesourosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "templo.yml");
    }

    /** Constrói se ainda não foi construído nesse mundo (ou se o desenho mudou). */
    public void garantir(World mundo) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        if (yaml.getInt("versao") == VERSAO && mundo.getUID().toString().equals(yaml.getString("mundo"))) return;
        construir(mundo);
        yaml.set("versao", VERSAO);
        yaml.set("mundo", mundo.getUID().toString());
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não deu pra salvar o templo.yml", e);
        }
    }

    public void construir(World mundo) {
        int chao = plugin.ySuperficie(mundo); // o bloco de grama
        int y = chao + 1;                     // onde se pisa
        mundo.getChunkAt(0, 0);
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) mundo.getChunkAt(cx, cz);

        // limpa o espaço e faz o piso
        caixa(mundo, -7, y, -7, 7, y + 9, 7, Material.AIR);
        caixa(mundo, -6, chao, -6, 6, chao, 6, Material.POLISHED_ANDESITE);
        for (int i = -6; i <= 6; i++) {
            for (int[] borda : new int[][]{{i, -6}, {i, 6}, {-6, i}, {6, i}}) bloco(mundo, borda[0], chao, borda[1], Material.CHISELED_STONE_BRICKS);
        }
        caixa(mundo, -2, chao, -2, 2, chao, 2, Material.GOLD_BLOCK);          // o centro dourado
        caixa(mundo, -1, chao, -1, 1, chao, 1, Material.CHISELED_DEEPSLATE);
        bloco(mundo, 0, chao, 0, Material.LODESTONE);

        // pilares nos cantos, com lanterna em cima de um capitel
        for (int[] c : new int[][]{{-5, -5}, {-5, 5}, {5, -5}, {5, 5}}) {
            caixa(mundo, c[0], y, c[1], c[0], y + 4, c[1], Material.STONE_BRICKS);
            bloco(mundo, c[0], y, c[1], Material.CHISELED_STONE_BRICKS);
            bloco(mundo, c[0], y + 4, c[1], Material.CHISELED_STONE_BRICKS);
        }
        // telhado em degraus, com luz embaixo
        caixa(mundo, -6, y + 5, -6, 6, y + 5, 6, Material.STONE_BRICKS);
        caixa(mundo, -4, y + 6, -4, 4, y + 6, 4, Material.MOSSY_STONE_BRICKS);
        caixa(mundo, -2, y + 7, -2, 2, y + 7, 2, Material.STONE_BRICKS);
        bloco(mundo, 0, y + 8, 0, Material.GOLD_BLOCK);
        for (int[] l : new int[][]{{0, 0}, {-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) bloco(mundo, l[0], y + 5, l[1], Material.SEA_LANTERN);

        // um caminho saindo de cada lado
        for (int d = 7; d <= 24; d++) {
            for (int largura = -1; largura <= 1; largura++) {
                Material m = largura == 0 ? Material.DIRT_PATH : Material.GRAVEL;
                bloco(mundo, largura, chao, -d, m);
                bloco(mundo, largura, chao, d, m);
                bloco(mundo, -d, chao, largura, m);
                bloco(mundo, d, chao, largura, m);
            }
        }

        // hologramas
        for (TextDisplay t : mundo.getEntitiesByClass(TextDisplay.class)) if (t.getScoreboardTags().contains(TAG)) t.remove();
        holograma(mundo, new Location(mundo, 0.5, y + 3.2, 0.5), 1.0f,
                "&6&l✦ Mundo dos Tesouros ✦\n&fAnde até o X do seu mapa e cave o chão.\n"
                        + "&7Com o mapa na mão, a barra mostra quanto falta.\n&cNa câmara, quem morre perde tudo.");
        holograma(mundo, new Location(mundo, 0.5, y + 2.6, -7.5), 1.4f, "&b&l⬆ NORTE\n&fpra lá o &bZ diminui\n&7(Z negativo)");
        holograma(mundo, new Location(mundo, 0.5, y + 2.6, 8.5), 1.4f, "&c&l⬇ SUL\n&fpra lá o &cZ aumenta\n&7(Z positivo)");
        holograma(mundo, new Location(mundo, 8.5, y + 2.6, 0.5), 1.4f, "&e&l➡ LESTE\n&fpra lá o &eX aumenta\n&7(X positivo)");
        holograma(mundo, new Location(mundo, -7.5, y + 2.6, 0.5), 1.4f, "&a&l⬅ OESTE\n&fpra lá o &aX diminui\n&7(X negativo)");

        // a chegada é no centro, olhando pro norte
        mundo.setSpawnLocation(new Location(mundo, 0.5, y, 0.5, 180f, 0f));
        plugin.getLogger().info("Templo da chegada construído no mundo " + mundo.getName() + ".");
    }

    private void holograma(World mundo, Location onde, float escala, String texto) {
        mundo.spawn(onde, TextDisplay.class, t -> {
            t.addScoreboardTag(TAG);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setPersistent(true);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala, escala, escala), new AxisAngle4f()));
            t.text(LegacyComponentSerializer.legacySection().deserialize(plugin.cores(texto)));
        });
    }

    private static void bloco(World mundo, int x, int y, int z, Material m) {
        Block b = mundo.getBlockAt(x, y, z);
        b.setType(m, false);
    }

    private static void caixa(World mundo, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) bloco(mundo, x, y, z, m);
    }
}
