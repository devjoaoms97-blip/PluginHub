package com.example.recursos;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code /mapa [mundo]}: um mapa de cada mundo de recursos. O mapa mostra o mundo inteiro (a borda é a
 * moldura), com o terreno, uma grade a cada 500 blocos, o centro e, no End, a área protegida e o
 * quadrado verde. Com o mapa na mão dentro daquele mundo, a seta é você, e embaixo aparecem as coordenadas.
 * <p>
 * <b>Terreno:</b> cada pixel é a cor (do mapa do jogo) de um ponto do mundo, lida só de chunk que já
 * existe, sem gerar nada: o mapa se preenche conforme os jogadores exploram. Quando o plugin liga sem o
 * desenho salvo, ele lê aos poucos os chunks já gerados; depois, todo chunk que carrega pinta o pedaço
 * dele. O desenho fica em {@code mapa-<mundo>.dat}, marcado com o ciclo do mundo, e é apagado no reset
 * (o mundo novo começa em branco).
 * <p>
 * Cada mundo tem um mapa só (o id fica no {@code dados.yml}); quando o plugin liga, o desenho volta
 * pros mapas que já existem.
 */
public class Mapas implements Listener {

    private static final int LADO = 128;
    /** Pixel ainda sem terreno. */
    private static final int VAZIO = 0; // as cores salvas têm alfa 255, então nunca são 0
    /** Leitura dos chunks já gerados, quando não há desenho salvo: pixels por tick e carregamentos por tick. */
    private static final int PIXELS_POR_TICK = 64, CARREGAMENTOS_POR_TICK = 6;

    private final RecursosPlugin plugin;
    private final NamespacedKey chave;
    /** mundo → id do mapa. */
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<String, Terreno> terrenos = new HashMap<>();

    public Mapas(RecursosPlugin plugin, Map<String, Integer> salvos) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "mapa");
        for (Map.Entry<String, Integer> e : salvos.entrySet()) {
            MapView view = Bukkit.getMap(e.getValue());
            if (view == null) continue;
            ids.put(e.getKey(), e.getValue());
            desenharCom(view, e.getKey());
        }
    }

    public Map<String, Integer> getIds() {
        return ids;
    }

    /** O item do mapa do mundo. */
    public ItemStack item(ResetManager m) {
        MapView view = view(m.getMundo());
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("§aMapa: " + nomeCurto(m));
        meta.setLore(List.of("§7Com ele na mão, no " + m.getNome() + ",", "§7a seta mostra onde você está.",
                "§7O terreno aparece conforme o mundo é explorado."));
        meta.getPersistentDataContainer().set(chave, PersistentDataType.STRING, m.getMundo());
        item.setItemMeta(meta);
        return item;
    }

    /** O jogador já tem o mapa desse mundo? */
    public boolean tem(Player p, String mundo) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && mundo.equals(i.getItemMeta().getPersistentDataContainer().get(chave, PersistentDataType.STRING))) return true;
        }
        return false;
    }

    private MapView view(String mundo) {
        Integer id = ids.get(mundo);
        MapView view = id == null ? null : Bukkit.getMap(id);
        if (view != null) return view;
        World w = Bukkit.getWorld(mundo);
        view = Bukkit.createMap(w != null ? w : Bukkit.getWorlds().get(0));
        ids.put(mundo, view.getId());
        plugin.salvarDados();
        desenharCom(view, mundo);
        return view;
    }

    private void desenharCom(MapView view, String mundo) {
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.addRenderer(new Desenho(mundo));
    }

    private static String nomeCurto(ResetManager m) {
        String n = m.getNome();
        return n.isEmpty() ? m.getMundo() : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Sem acento: a fonte do mapa não tem todos. */
    private static String ascii(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Lado do mundo coberto pelo mapa: a borda, ou 6.000 sem borda. */
    private static double borda(World w) {
        return w != null && w.getWorldBorder().getSize() < 1_000_000 ? w.getWorldBorder().getSize() : 6000;
    }

    // ---------------------------------------------------------------------
    // Terreno
    // ---------------------------------------------------------------------

    /** O terreno do mundo (lido do arquivo, ou começando a leitura dos chunks já gerados). */
    private Terreno terreno(World w) {
        Terreno t = terrenos.get(w.getName());
        double borda = borda(w);
        ResetManager m = plugin.getResets().get(w.getName());
        int ciclo = m == null ? 0 : m.getCiclo();
        if (t != null && t.borda == borda && t.ciclo == ciclo) return t;
        t = new Terreno(w.getName(), borda, ciclo);
        if (!t.carregar()) t.lerChunksGerados();
        terrenos.put(w.getName(), t);
        return t;
    }

    /** O mundo foi resetado: o desenho antigo some e o mundo novo começa em branco. */
    public void limpar(String mundo) {
        Terreno t = terrenos.remove(mundo);
        if (t != null) t.fila.clear();
        try {
            Files.deleteIfExists(arquivo(mundo).toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não deu pra apagar o mapa antigo de " + mundo, e);
        }
        World w = Bukkit.getWorld(mundo);
        if (w != null) terreno(w);
    }

    /** Quando liga: o terreno de cada mundo que já existe. */
    public void prepararTerrenos() {
        for (String nome : plugin.getResets().keySet()) {
            World w = Bukkit.getWorld(nome);
            if (w != null) terreno(w);
        }
    }

    /** A cada tick: um pouco da leitura dos chunks já gerados. */
    public void tick() {
        for (Terreno t : terrenos.values()) t.lerUmPouco();
    }

    /** Quantos pixels do mapa do mundo já têm terreno, e quantos faltam ler dos chunks já gerados. */
    public String resumo(String mundo) {
        World w = Bukkit.getWorld(mundo);
        if (w == null) return mundo + ": mundo não carregado";
        Terreno t = terreno(w);
        int com = 0;
        for (int c : t.rgb) if (c != VAZIO) com++;
        return mundo + ": " + com + " de " + (LADO * LADO) + " pixels com terreno"
                + (t.fila.isEmpty() ? "" : " (lendo os chunks já gerados: faltam " + t.fila.size() + ")");
    }

    /** Salva os desenhos que mudaram (a cada 5 min e quando desliga). */
    public void salvar() {
        for (Terreno t : terrenos.values()) if (t.sujo) t.salvar();
    }

    @EventHandler
    public void aoCarregarChunk(ChunkLoadEvent evento) {
        World w = evento.getWorld();
        if (!plugin.getResets().containsKey(w.getName())) return;
        terreno(w).pintar(evento.getChunk());
    }

    private File arquivo(String mundo) {
        return new File(plugin.getDataFolder(), "mapa-" + mundo + ".dat");
    }

    private final class Terreno {

        final String mundo;
        final double borda;
        final double celula;
        final int ciclo;
        final int[] rgb = new int[LADO * LADO];
        /** Pixels que ainda falta ler, na leitura dos chunks já gerados. */
        final Deque<Integer> fila = new ArrayDeque<>();
        int versao;
        boolean sujo;

        Terreno(String mundo, double borda, int ciclo) {
            this.mundo = mundo;
            this.borda = borda;
            this.celula = borda / LADO;
            this.ciclo = ciclo;
            Arrays.fill(rgb, VAZIO);
        }

        /** O ponto do mundo que o pixel mostra (o meio da célula). */
        int mundoX(int px) {
            return (int) Math.floor(-borda / 2 + (px + 0.5) * celula);
        }

        /** Os pixels cujo ponto cai dentro do chunk, lidos agora. */
        void pintar(Chunk chunk) {
            World w = chunk.getWorld();
            int x0 = chunk.getX() << 4, z0 = chunk.getZ() << 4;
            int pxMin = Math.max(0, (int) Math.floor((x0 + borda / 2) / celula - 1)), pxMax = Math.min(LADO - 1, (int) Math.ceil((x0 + 16 + borda / 2) / celula));
            int pzMin = Math.max(0, (int) Math.floor((z0 + borda / 2) / celula - 1)), pzMax = Math.min(LADO - 1, (int) Math.ceil((z0 + 16 + borda / 2) / celula));
            for (int px = pxMin; px <= pxMax; px++) {
                int x = mundoX(px);
                if (x >> 4 != chunk.getX()) continue;
                for (int pz = pzMin; pz <= pzMax; pz++) {
                    int z = mundoX(pz);
                    if (z >> 4 != chunk.getZ()) continue;
                    int cor = cor(w, x, z);
                    if (rgb[pz * LADO + px] != cor) {
                        rgb[pz * LADO + px] = cor;
                        versao++;
                        sujo = true;
                    }
                }
            }
        }

        /** Põe na fila todos os pixels, pra ler os chunks que já foram gerados. */
        void lerChunksGerados() {
            fila.clear();
            for (int i = 0; i < LADO * LADO; i++) fila.add(i);
        }

        void lerUmPouco() {
            World w = Bukkit.getWorld(mundo);
            if (w == null || fila.isEmpty()) return;
            int carregamentos = 0;
            for (int n = 0; n < PIXELS_POR_TICK && !fila.isEmpty(); n++) {
                int i = fila.peek();
                int cx = mundoX(i % LADO) >> 4, cz = mundoX(i / LADO) >> 4;
                if (w.isChunkLoaded(cx, cz)) {
                    fila.poll();
                    pintar(w.getChunkAt(cx, cz));
                } else if (!w.isChunkGenerated(cx, cz)) {
                    fila.poll();
                } else if (carregamentos++ < CARREGAMENTOS_POR_TICK) {
                    fila.poll();
                    // carrega sem gerar; o ChunkLoadEvent pinta
                    w.getChunkAtAsync(cx, cz, false);
                } else {
                    return; // o resto no próximo tick
                }
            }
        }

        boolean carregar() {
            File f = arquivo(mundo);
            if (!f.exists()) return false;
            try (DataInputStream in = new DataInputStream(Files.newInputStream(f.toPath()))) {
                if (in.readInt() != ciclo || in.readDouble() != borda) return false;
                for (int i = 0; i < rgb.length; i++) rgb[i] = in.readInt();
                versao++;
                return true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Mapa de " + mundo + " ilegível; lendo o terreno de novo", e);
                Arrays.fill(rgb, VAZIO);
                return false;
            }
        }

        void salvar() {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(arquivo(mundo).toPath()))) {
                out.writeInt(ciclo);
                out.writeDouble(borda);
                for (int c : rgb) out.writeInt(c);
                sujo = false;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Não deu pra salvar o mapa de " + mundo, e);
            }
        }
    }

    /**
     * A cor do ponto: o bloco de cima (no Nether, o chão debaixo do teto; no End, o vazio fica escuro),
     * mais clara no alto e mais escura no fundo.
     */
    private static int cor(World w, int x, int z) {
        Block b;
        if (w.getEnvironment() == World.Environment.NETHER) {
            b = null;
            boolean ar = false;
            for (int y = 120; y > 0; y--) {
                Block atual = w.getBlockAt(x, y, z);
                if (atual.getType().isAir()) ar = true;
                else if (ar) {
                    b = atual;
                    break;
                }
            }
            if (b == null) return new Color(60, 20, 20).getRGB();
        } else {
            b = w.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
            if (b.getY() <= w.getMinHeight() || b.getType().isAir()) return new Color(15, 12, 25).getRGB(); // o vazio do End
        }
        org.bukkit.Color c = b.getBlockData().getMapColor();
        for (int i = 0; i < 4 && c.getAlpha() == 0; i++) {
            b = b.getRelative(0, -1, 0);
            c = b.getBlockData().getMapColor();
        }
        int nivelBase = w.getEnvironment() == World.Environment.NORMAL ? 63 : 60;
        double luz = Math.max(0.7, Math.min(1.15, 0.9 + (b.getY() - nivelBase) / 120.0));
        return new Color(Math.min(255, (int) (c.getRed() * luz)), Math.min(255, (int) (c.getGreen() * luz)),
                Math.min(255, (int) (c.getBlue() * luz))).getRGB();
    }

    /** O desenho do mapa, por jogador (a seta e as coordenadas são de quem segura). */
    private final class Desenho extends MapRenderer {

        private final String mundo;
        private final Map<UUID, Integer> versaoDesenhada = new HashMap<>();
        private final Map<UUID, Integer> ultimoTick = new HashMap<>();
        private final Map<UUID, String> ultimoTexto = new HashMap<>();

        Desenho(String mundo) {
            super(true);
            this.mundo = mundo;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            ResetManager m = plugin.getResets().get(mundo);
            World w = Bukkit.getWorld(mundo);
            if (m == null) return;
            double borda = borda(w);
            Terreno t = w == null ? null : terreno(w);

            // o fundo inteiro só quando o terreno mudou, no máximo uma vez por segundo
            UUID id = p.getUniqueId();
            int versao = t == null ? 0 : t.versao;
            int agora = Bukkit.getCurrentTick();
            boolean novo = !versaoDesenhada.containsKey(id);
            if (novo || (versaoDesenhada.get(id) != versao && agora - ultimoTick.getOrDefault(id, 0) >= 20)) {
                fundo(canvas, w, t, borda);
                versaoDesenhada.put(id, versao);
                ultimoTick.put(id, agora);
                ultimoTexto.remove(id);
            }

            MapCursorCollection cursores = new MapCursorCollection();
            String texto;
            if (w != null && p.getWorld().equals(w)) {
                Location l = p.getLocation();
                int px = (int) Math.round(l.getX() / borda * 256), pz = (int) Math.round(l.getZ() / borda * 256);
                px = Math.max(-128, Math.min(127, px));
                pz = Math.max(-128, Math.min(127, pz));
                byte direcao = (byte) (Math.floorMod(Math.round(l.getYaw() / 22.5f), 16));
                cursores.addCursor(new MapCursor((byte) px, (byte) pz, direcao, MapCursor.Type.PLAYER, true));
                texto = "X " + l.getBlockX() + "  Z " + l.getBlockZ();
            } else {
                texto = "Voce nao esta aqui";
            }
            canvas.setCursors(cursores);
            if (!texto.equals(ultimoTexto.put(id, texto))) {
                for (int x = 2; x < 126; x++) for (int y = 116; y < 126; y++) canvas.setPixelColor(x, y, new Color(40, 40, 40));
                canvas.drawText(4, 117, MinecraftFont.Font, texto);
            }
        }

        private void fundo(MapCanvas canvas, World w, Terreno t, double borda) {
            boolean end = w != null && w.getEnvironment() == World.Environment.THE_END;
            boolean nether = w != null && w.getEnvironment() == World.Environment.NETHER;
            Color base = end ? new Color(222, 218, 160) : nether ? new Color(150, 60, 50) : new Color(214, 196, 150); // ainda não explorado
            Color moldura = new Color(60, 60, 60);
            int passo = Math.max(8, (int) Math.round(500 / borda * LADO)); // uma linha a cada 500 blocos
            for (int x = 0; x < LADO; x++) {
                for (int y = 0; y < LADO; y++) {
                    if (x < 2 || y < 2 || x > 125 || y > 125) {
                        canvas.setPixelColor(x, y, moldura);
                        continue;
                    }
                    int rgb = t == null ? VAZIO : t.rgb[y * LADO + x];
                    Color c = rgb == VAZIO ? base : new Color(rgb);
                    if ((x - 64) % passo == 0 || (y - 64) % passo == 0) c = c.darker(); // a grade
                    canvas.setPixelColor(x, y, c);
                }
            }
            if (end) {
                // a área protegida (círculo) e o quadrado verde
                double r = plugin.getConfig().getInt("end.raio-protegido", 100) / borda * LADO;
                for (int a = 0; a < 360; a += 2) {
                    int x = 64 + (int) Math.round(Math.cos(Math.toRadians(a)) * r), y = 64 + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                    if (x > 1 && x < 126 && y > 1 && y < 126) canvas.setPixelColor(x, y, new Color(170, 120, 220));
                }
                List<Integer> pl = plugin.getConfig().getIntegerList("end.plataforma");
                int qx = 64 + (int) Math.round((pl.size() >= 2 ? pl.get(0) : 0) / borda * LADO);
                int qz = 64 + (int) Math.round((pl.size() >= 2 ? pl.get(1) : 25) / borda * LADO);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) canvas.setPixelColor(qx + dx, qz + dz, new Color(60, 200, 60));
            }
            // o centro
            for (int d = -2; d <= 2; d++) {
                canvas.setPixelColor(64 + d, 64, Color.BLACK);
                canvas.setPixelColor(64, 64 + d, Color.BLACK);
            }
            for (int x = 2; x < 126; x++) for (int y = 2; y < 12; y++) canvas.setPixelColor(x, y, new Color(40, 40, 40));
            ResetManager m = plugin.getResets().get(mundo);
            canvas.drawText(4, 3, MinecraftFont.Font, ascii(m != null ? nomeCurto(m) : mundo) + "  (" + (int) borda + " blocos)");
        }
    }
}
