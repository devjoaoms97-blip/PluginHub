package com.example.areas;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holograma fixo de uma caixa de PvP sem ciclo ({@code paineis} numa entrada de
 * {@code pvp-so-nas-caixas}): avisa, na entrada, que dali pra frente o PvP fica ligado o tempo todo
 * (a arena do boss, a partir do topo da escadaria). O texto sai de {@code texto-painel}, ou do padrão.
 */
final class PainelFixo {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final String TAG = "areas-painel-fixo";
    private static final String PADRAO = "&c&l⚔ PvP LIGADO ⚔\n&fo tempo todo, daqui pra frente";

    private final String mundo;
    private final double x, y, z;
    private final String texto;
    private TextDisplay holograma;

    private PainelFixo(String mundo, double x, double y, double z, String texto) {
        this.mundo = mundo;
        this.x = x;
        this.y = y;
        this.z = z;
        this.texto = texto;
    }

    /** Os painéis de uma entrada de pvp-so-nas-caixas sem ciclo (nenhum se ela tem ciclo ou não tem painéis). */
    static List<PainelFixo> de(String mundo, Map<?, ?> m) {
        List<PainelFixo> lista = new ArrayList<>();
        if (m.get("ciclo") != null || !(m.get("paineis") instanceof List<?> paineis)) return lista;
        String nome = String.valueOf(m.get("nome"));
        String texto = ChatColor.translateAlternateColorCodes('&',
                (m.get("texto-painel") != null ? String.valueOf(m.get("texto-painel")) : PADRAO).replace("%nome%", nome));
        for (Object p : paineis) {
            if (p instanceof List<?> c && c.size() == 3 && c.stream().allMatch(n -> n instanceof Number)) {
                lista.add(new PainelFixo(mundo, ((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue(),
                        ((Number) c.get(2)).doubleValue(), texto));
            }
        }
        return lista;
    }

    /** Apaga os hologramas fixos dos chunks carregados: no reload, o painel que mudou de lugar não fica pra trás. */
    static void apagarTodos() {
        for (World w : Bukkit.getWorlds()) {
            w.getEntitiesByClass(TextDisplay.class).stream().filter(t -> t.getScoreboardTags().contains(TAG)).forEach(TextDisplay::remove);
        }
    }

    /** A cada segundo: com o chunk carregado, garante o holograma (acha o de antes pela tag, ou cria). */
    void tick() {
        World w = Bukkit.getWorld(mundo);
        if (w == null || !w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return;
        Location onde = new Location(w, x, y, z);
        if (holograma == null || !holograma.isValid()) {
            holograma = w.getNearbyEntitiesByType(TextDisplay.class, onde, 1).stream()
                    .filter(t -> t.getScoreboardTags().contains(TAG)).findFirst().orElse(null);
        }
        if (holograma == null) {
            holograma = w.spawn(onde, TextDisplay.class, t -> {
                t.addScoreboardTag(TAG);
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setShadowed(true);
                t.setPersistent(true);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(1.8f, 1.8f, 1.8f), new AxisAngle4f()));
            });
        }
        holograma.text(LEGADO.deserialize(texto));
    }
}
