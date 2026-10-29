package com.example.areas;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Uma caixa de PvP com ciclo ({@code ciclo} numa entrada de {@code pvp-so-nas-caixas}): fica
 * {@code desligado} segundos sem PvP e {@code ligado} segundos com, sem parar. A fase sai do relógio
 * (segundos desde 1970), então continua a mesma depois de reiniciar o servidor. Quem está em
 * {@code avisos} (a warp mobs inteira, com o vestíbulo e a escadaria) vê a barra de boss com o tempo,
 * o título na troca e o aviso no chat 1 minuto antes de ligar. Na parede, o holograma de {@code painel}
 * mostra a fase e o tempo, e os blocos de concreto verde/vermelho da {@code moldura} trocam de cor.
 */
final class CicloPvp {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final String TAG = "areas-ciclo-pvp";

    final String id;
    final String nome;
    private final String mundo;
    private final int desligado, ligado;
    private final int[] avisosMin, avisosMax, molduraMin, molduraMax;
    private final double[] painel;

    /** Somado ao relógio: o /areas ciclo força uma fase mexendo só nisso. */
    private long deslocamento;
    private Boolean faseAnterior;
    private BossBar barra;
    private TextDisplay holograma;

    private CicloPvp(String id, String nome, String mundo, int desligado, int ligado, int[] avisosMin, int[] avisosMax,
                     double[] painel, int[] molduraMin, int[] molduraMax) {
        this.id = id;
        this.nome = nome;
        this.mundo = mundo;
        this.desligado = desligado;
        this.ligado = ligado;
        this.avisosMin = avisosMin;
        this.avisosMax = avisosMax;
        this.painel = painel;
        this.molduraMin = molduraMin;
        this.molduraMax = molduraMax;
    }

    /** Lê o ciclo de uma entrada de pvp-so-nas-caixas, ou null se ela não tem ciclo (PvP sempre). */
    static CicloPvp de(String mundo, Map<?, ?> m) {
        if (!(m.get("ciclo") instanceof Map<?, ?> ciclo)) return null;
        String nome = String.valueOf(m.get("nome"));
        String id = m.get("id") != null ? String.valueOf(m.get("id")) : nome.replace(" ", "-");
        Map<?, ?> avisos = m.get("avisos") instanceof Map<?, ?> a ? a : m;
        Map<?, ?> moldura = m.get("moldura") instanceof Map<?, ?> b ? b : null;
        List<?> painel = m.get("painel") instanceof List<?> c && c.size() == 3 ? c : null;
        return new CicloPvp(id, nome, mundo, numero(ciclo.get("desligado"), 900), numero(ciclo.get("ligado"), 900),
                canto(avisos, "min", true), canto(avisos, "max", false),
                painel == null ? null : new double[]{numero(painel.get(0)), numero(painel.get(1)), numero(painel.get(2))},
                moldura == null ? null : canto(moldura, "min", true), moldura == null ? null : canto(moldura, "max", false));
    }

    private static int numero(Object o, int padrao) {
        return o instanceof Number n ? n.intValue() : padrao;
    }

    private static double numero(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    /** O canto menor (ou maior) da caixa min/max, eixo por eixo. */
    private static int[] canto(Map<?, ?> m, String chave, boolean menor) {
        if (!(m.get("min") instanceof List<?> a) || !(m.get("max") instanceof List<?> b) || a.size() != 3 || b.size() != 3) return null;
        int[] r = new int[3];
        for (int i = 0; i < 3; i++) {
            int x = ((Number) a.get(i)).intValue(), y = ((Number) b.get(i)).intValue();
            r[i] = menor ? Math.min(x, y) : Math.max(x, y);
        }
        return r;
    }

    // ------------------------------------------------------------------ fase

    private long agora() {
        return System.currentTimeMillis() / 1000 + deslocamento;
    }

    private long posicao() {
        return Math.floorMod(agora(), (long) desligado + ligado);
    }

    boolean ligado() {
        return posicao() >= desligado;
    }

    /** Segundos até a próxima troca. */
    long restante() {
        long t = posicao();
        return t < desligado ? desligado - t : (long) desligado + ligado - t;
    }

    /** Começa a fase pedida agora (staff: teste ou evento). */
    void forcar(boolean ligar) {
        long t = Math.floorMod(System.currentTimeMillis() / 1000, (long) desligado + ligado);
        deslocamento = (ligar ? desligado : 0) - t;
    }

    // ------------------------------------------------------------------ a cada segundo

    void tick() {
        boolean on = ligado();
        long resta = restante();
        World w = Bukkit.getWorld(mundo);
        List<Player> aqui = w == null ? List.of() : w.getPlayers().stream().filter(this::nosAvisos).toList();

        if (faseAnterior != null && faseAnterior != on) trocou(on, aqui);
        faseAnterior = on;
        if (!on && resta == 60) {
            for (Player p : aqui) {
                p.sendMessage("§e⚔ Em 1 minuto o PvP liga na " + nome + ", da linha vermelha pra dentro. §7Quem fica no vestíbulo não briga.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            }
        }

        atualizarBarra(on, resta, aqui);
        if (w != null) {
            atualizarHolograma(w, on, resta);
            pintarMoldura(w, on);
        }
    }

    private boolean nosAvisos(Player p) {
        if (avisosMin == null) return false;
        Location l = p.getLocation();
        return l.getX() >= avisosMin[0] && l.getX() <= avisosMax[0] + 1 && l.getY() >= avisosMin[1] && l.getY() <= avisosMax[1] + 1
                && l.getZ() >= avisosMin[2] && l.getZ() <= avisosMax[2] + 1;
    }

    private void trocou(boolean on, List<Player> aqui) {
        Title titulo = Title.title(
                LEGADO.deserialize(on ? "§c§l⚔ PvP LIGADO ⚔" : "§a§lPvP DESLIGADO"),
                LEGADO.deserialize(on ? "§7da linha vermelha pra dentro, por " + tempo(ligado)
                        : "§7volta em " + tempo(desligado)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
        for (Player p : aqui) {
            p.showTitle(titulo);
            p.playSound(p.getLocation(), on ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1f);
        }
    }

    private void atualizarBarra(boolean on, long resta, List<Player> aqui) {
        if (barra == null) barra = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
        barra.setTitle(on ? "§c§l⚔ PvP LIGADO §r§f— desliga em §e" + tempo(resta)
                : "§a§lPvP DESLIGADO §r§f— liga em §e" + tempo(resta));
        barra.setColor(on ? BarColor.RED : BarColor.GREEN);
        barra.setProgress(Math.max(0, Math.min(1, resta / (double) (on ? ligado : desligado))));
        for (Player p : List.copyOf(barra.getPlayers())) if (!aqui.contains(p)) barra.removePlayer(p);
        for (Player p : aqui) if (!barra.getPlayers().contains(p)) barra.addPlayer(p);
    }

    private void atualizarHolograma(World w, boolean on, long resta) {
        if (painel == null || !w.isChunkLoaded((int) Math.floor(painel[0]) >> 4, (int) Math.floor(painel[2]) >> 4)) return;
        Location onde = new Location(w, painel[0], painel[1], painel[2]);
        if (holograma == null || !holograma.isValid()) {
            holograma = w.getNearbyEntitiesByType(TextDisplay.class, onde, 2).stream()
                    .filter(t -> t.getScoreboardTags().contains(TAG)).findFirst().orElse(null);
        }
        if (holograma == null) {
            holograma = w.spawn(onde, TextDisplay.class, t -> {
                t.addScoreboardTag(TAG);
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setShadowed(true);
                t.setPersistent(true);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(3f, 3f, 3f), new AxisAngle4f()));
            });
        }
        holograma.text(LEGADO.deserialize(on
                ? "§c§l⚔ PvP LIGADO ⚔\n§fdesliga em §e" + tempo(resta)
                : "§a§lPvP DESLIGADO\n§fliga em §e" + tempo(resta)));
    }

    /** Troca o concreto da moldura: verde sem PvP, vermelho com. */
    private void pintarMoldura(World w, boolean on) {
        if (molduraMin == null) return;
        Material cor = on ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        for (int x = molduraMin[0]; x <= molduraMax[0]; x++) {
            for (int z = molduraMin[2]; z <= molduraMax[2]; z++) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = molduraMin[1]; y <= molduraMax[1]; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if ((m == Material.RED_CONCRETE || m == Material.LIME_CONCRETE) && m != cor) b.setType(cor, false);
                }
            }
        }
    }

    void desligar() {
        if (barra != null) barra.removeAll();
    }

    static String tempo(long segundos) {
        return String.format("%02d:%02d", segundos / 60, segundos % 60);
    }

    // usado no /areas
    String resumo() {
        return (ligado() ? "§cPvP ligado" : "§aPvP desligado") + "§7, troca em " + tempo(restante())
                + " (" + tempo(desligado) + " sem, " + tempo(ligado) + " com)";
    }
}
