package com.example.recursos;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /mapa [mundo]}: um mapa de localização de cada mundo de recursos. O mapa mostra o mundo
 * inteiro (a borda é a moldura), com uma grade, o centro e, no End, a área protegida e o quadrado
 * verde. Com o mapa na mão dentro daquele mundo, a seta é você, e embaixo aparecem as coordenadas.
 * Não desenha o terreno: é só pra se localizar.
 * <p>
 * Cada mundo tem um mapa só (o id fica no {@code dados.yml}); quando o plugin liga, o desenho volta
 * pros mapas que já existem.
 */
public class Mapas {

    private final RecursosPlugin plugin;
    private final NamespacedKey chave;
    /** mundo → id do mapa. */
    private final Map<String, Integer> ids = new HashMap<>();

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
        meta.setLore(List.of("§7Com ele na mão, no " + m.getNome() + ",", "§7a seta mostra onde você está."));
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

    /** O desenho do mapa, por jogador (a seta e as coordenadas são de quem segura). */
    private final class Desenho extends MapRenderer {

        private final String mundo;
        private final Set<UUID> fundoFeito = new HashSet<>();
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
            double borda = w != null && w.getWorldBorder().getSize() < 1_000_000 ? w.getWorldBorder().getSize() : 6000;
            boolean end = w != null && w.getEnvironment() == World.Environment.THE_END;
            boolean nether = w != null && w.getEnvironment() == World.Environment.NETHER;

            if (fundoFeito.add(p.getUniqueId())) fundo(canvas, borda, end, nether);

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
            if (!texto.equals(ultimoTexto.put(p.getUniqueId(), texto))) {
                for (int x = 2; x < 126; x++) for (int y = 116; y < 126; y++) canvas.setPixelColor(x, y, new Color(40, 40, 40));
                canvas.drawText(4, 117, MinecraftFont.Font, texto);
            }
        }

        private void fundo(MapCanvas canvas, double borda, boolean end, boolean nether) {
            Color base = end ? new Color(222, 218, 160) : nether ? new Color(150, 60, 50) : new Color(110, 160, 80);
            Color linha = end ? new Color(190, 186, 130) : nether ? new Color(120, 45, 40) : new Color(90, 135, 65);
            Color moldura = new Color(60, 60, 60);
            int passo = Math.max(8, (int) Math.round(500 / borda * 128)); // uma linha a cada 500 blocos
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    boolean naGrade = (x - 64) % passo == 0 || (y - 64) % passo == 0;
                    boolean naMoldura = x < 2 || y < 2 || x > 125 || y > 125;
                    canvas.setPixelColor(x, y, naMoldura ? moldura : naGrade ? linha : base);
                }
            }
            if (end) {
                // a área protegida (círculo) e o quadrado verde
                double r = plugin.getConfig().getInt("end.raio-protegido", 100) / borda * 128;
                for (int a = 0; a < 360; a += 2) {
                    int x = 64 + (int) Math.round(Math.cos(Math.toRadians(a)) * r), y = 64 + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                    if (x > 1 && x < 126 && y > 1 && y < 126) canvas.setPixelColor(x, y, new Color(120, 90, 160));
                }
                List<Integer> pl = plugin.getConfig().getIntegerList("end.plataforma");
                int qx = 64 + (int) Math.round((pl.size() >= 2 ? pl.get(0) : 0) / borda * 128);
                int qz = 64 + (int) Math.round((pl.size() >= 2 ? pl.get(1) : 25) / borda * 128);
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
