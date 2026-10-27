package com.example.spawners;

import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * A /warp mobs: salão do servidor (estrutura "mobs" do EstruturasPlugin) com estátuas de mobs e, na
 * frente de cada uma, um spawner no chão. Em volta dele nascem mobs normais, um por vez (sem pilha),
 * até {@code limite-por-estatua}, enquanto tiver jogador por perto. Qualquer um mata: drops, XP e
 * skills como um mob qualquer (o XP de skill é o cheio, sem o desconto de pilha).
 * <ul>
 *   <li>O mob nasce com o motivo "spawner": as missões de matar não contam.</li>
 *   <li>Fica dentro da {@code area} (não anda nem teleporta pra fora: o túnel do spawn e a entrada
 *       ficam livres), não queima no sol do teto de vidro, não some de longe e não pega item.</li>
 *   <li>O spawner do bloco só mostra o mob girando: o spawn do vanilla fica desligado.</li>
 * </ul>
 * A estrutura cadastra a área e os pontos pelos comandos {@code /spawners mobs}; ficam em mobs.yml.
 */
public class WarpMobs implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    /** Uma estátua: o spawner no chão (onde os mobs nascem em volta) e o tipo do mob. */
    record Ponto(String id, String tipo, String mundo, int x, int y, int z) {
        Location bloco() {
            World w = Bukkit.getWorld(mundo);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final SpawnersPlugin plugin;
    private final File arquivo;
    private final Map<String, Ponto> pontos = new LinkedHashMap<>();
    private final Map<String, UUID> hologramas = new HashMap<>();
    private final NamespacedKey chavePonto;
    private String mundoArea;
    private BoundingBox area;

    WarpMobs(SpawnersPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "mobs.yml");
        this.chavePonto = new NamespacedKey(plugin, "warp-mobs");
        carregar();
    }

    int quantos() {
        return pontos.size();
    }

    // ---------------------------------------------------------------------
    // Ciclo: um mob a mais em cada estátua com jogador por perto
    // ---------------------------------------------------------------------

    void ciclo() {
        if (pontos.isEmpty()) return;
        double raio = plugin.getConfig().getDouble("warp-mobs.raio-ativacao", 32);
        int limite = Math.max(1, plugin.getConfig().getInt("warp-mobs.limite-por-estatua", 4));
        Map<String, Integer> vivos = contarVivos();
        for (Ponto p : pontos.values()) {
            Location bloco = p.bloco();
            if (bloco == null || !bloco.getWorld().isChunkLoaded(p.x() >> 4, p.z() >> 4)) continue;
            prepararSpawner(p, bloco.getBlock());
            atualizarHolograma(p, bloco);
            Location centro = bloco.clone().add(0.5, 0.5, 0.5);
            boolean alguemPerto = bloco.getWorld().getPlayers().stream()
                    .anyMatch(j -> j.getGameMode() != GameMode.SPECTATOR && j.getLocation().distanceSquared(centro) <= raio * raio);
            if (!alguemPerto || vivos.getOrDefault(p.id(), 0) >= limite) continue;
            SpawnersPlugin.Tipo tipo = plugin.tipo(p.tipo());
            Location onde = lugarParaNascer(bloco);
            if (tipo == null || onde == null) continue;
            Class<? extends Entity> classe = tipo.entidade().getEntityClass();
            if (classe == null) continue;
            // motivo "spawner": as missões de matar não contam mob de spawner
            onde.getWorld().spawn(onde, classe, CreatureSpawnEvent.SpawnReason.SPAWNER, e -> prepararMob(e, p));
        }
    }

    /** Quantos mobs vivos cada estátua tem agora (nos mundos dos pontos). */
    private Map<String, Integer> contarVivos() {
        Map<String, Integer> vivos = new HashMap<>();
        List<String> mundos = pontos.values().stream().map(Ponto::mundo).distinct().toList();
        for (String nome : mundos) {
            World w = Bukkit.getWorld(nome);
            if (w == null) continue;
            for (LivingEntity e : w.getLivingEntities()) {
                String id = e.getPersistentDataContainer().get(chavePonto, PersistentDataType.STRING);
                if (id != null && !e.isDead()) vivos.merge(id, 1, Integer::sum);
            }
        }
        return vivos;
    }

    /**
     * Um lugar livre no chão em volta do spawner (até 3 blocos, sorteado, pra não nascer todo mundo
     * no mesmo bloco), com dois de altura e chão firme. Null se não tiver nenhum.
     */
    private static Location lugarParaNascer(Location spawner) {
        List<Block> livres = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 1; dy >= 0; dy--) {
                    Block pe = spawner.getBlock().getRelative(dx, dy, dz);
                    if (livre(pe) && livre(pe.getRelative(0, 1, 0)) && pe.getRelative(0, -1, 0).getType().isSolid()) {
                        livres.add(pe);
                        break;
                    }
                }
            }
        }
        if (livres.isEmpty()) return null;
        Block b = livres.get(ThreadLocalRandom.current().nextInt(livres.size()));
        return b.getLocation().add(0.5, 0, 0.5);
    }

    private static boolean livre(Block b) {
        return b.isPassable() && !b.isLiquid();
    }

    private void prepararMob(Entity e, Ponto p) {
        if (e instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
        }
        if (e instanceof Zombie z) z.setAdult();
        else if (e instanceof Ageable a) a.setAdult(); // filhote não derruba nada
        if (e instanceof Slime s) s.setSize(1);        // só o pequeno derruba slime, e não se divide
        e.setPersistent(true);
        e.getPersistentDataContainer().set(chavePonto, PersistentDataType.STRING, p.id());
    }

    /** O bloco é um spawner (a estrutura põe) com o mob girando, e o vanilla não gera nada nele. */
    private void prepararSpawner(Ponto p, Block bloco) {
        if (bloco.getType() != Material.SPAWNER || !(bloco.getState() instanceof CreatureSpawner estado)) return;
        SpawnersPlugin.Tipo tipo = plugin.tipo(p.tipo());
        if (tipo == null || (estado.getSpawnedType() == tipo.entidade() && estado.getRequiredPlayerRange() == 0)) return;
        estado.setSpawnedType(tipo.entidade());
        estado.setRequiredPlayerRange(0);
        estado.update(true, false);
    }

    private void atualizarHolograma(Ponto p, Location bloco) {
        Entity atual = hologramas.containsKey(p.id()) ? Bukkit.getEntity(hologramas.get(p.id())) : null;
        if (atual != null && atual.isValid()) return;
        SpawnersPlugin.Tipo tipo = plugin.tipo(p.tipo());
        if (tipo == null) return;
        String texto = "§6§l⚔ §r" + tipo.nome() + " §6§l⚔\n§7Drops, XP e skills";
        // não fica salvo no mundo: o ciclo põe de novo quando o chunk volta
        TextDisplay td = bloco.getWorld().spawn(bloco.clone().add(0.5, 4.2, 0.5), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.text(LEGADO.deserialize(texto));
            d.setPersistent(false);
        });
        hologramas.put(p.id(), td.getUniqueId());
    }

    private boolean ehDaWarp(Entity e) {
        return e.getPersistentDataContainer().has(chavePonto, PersistentDataType.STRING);
    }

    private boolean naArea(Location l) {
        return area == null || (l.getWorld().getName().equals(mundoArea) && area.contains(l.toVector()));
    }

    // ---------------------------------------------------------------------
    // Os mobs ficam no salão
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void aoAndar(EntityMoveEvent evento) {
        if (area == null || !evento.getTo().getWorld().getName().equals(mundoArea) || area.contains(evento.getTo().toVector())) return;
        if (ehDaWarp(evento.getEntity())) evento.setCancelled(true);
    }

    /** O enderman teleporta, mas não pra fora do salão (nem pra cima do teto). */
    @EventHandler(ignoreCancelled = true)
    public void aoTeleportar(EntityTeleportEvent evento) {
        if (evento.getTo() != null && ehDaWarp(evento.getEntity()) && !naArea(evento.getTo())) evento.setCancelled(true);
    }

    /** O sol entra pelo teto de vidro: zumbi e esqueleto não queimam (fogo de verdade ainda queima). */
    @EventHandler(ignoreCancelled = true)
    public void aoQueimar(EntityCombustEvent evento) {
        if (evento.getClass() == EntityCombustEvent.class && ehDaWarp(evento.getEntity())) evento.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoSpawnVanilla(SpawnerSpawnEvent evento) {
        if (evento.getSpawner() == null) return;
        Block b = evento.getSpawner().getBlock();
        for (Ponto p : pontos.values()) {
            if (p.x() == b.getX() && p.y() == b.getY() && p.z() == b.getZ() && p.mundo().equals(b.getWorld().getName())) {
                evento.setCancelled(true);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------
    // /spawners mobs (a estrutura "mobs" roda esses comandos no fim)
    // ---------------------------------------------------------------------

    void comando(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "area" -> {
                if (args.length < 9) {
                    sender.sendMessage("§cUso: §f/spawners mobs area <mundo> <x1> <y1> <z1> <x2> <y2> <z2>");
                    return;
                }
                try {
                    double[] n = new double[6];
                    for (int i = 0; i < 6; i++) n[i] = Double.parseDouble(args[3 + i]);
                    mundoArea = args[2];
                    area = new BoundingBox(n[0], n[1], n[2], n[3], n[4], n[5]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cCoordenada inválida.");
                    return;
                }
                salvar();
                sender.sendMessage("§aÁrea da warp mobs: §f" + mundoArea + " " + area);
            }
            case "ponto" -> {
                SpawnersPlugin.Tipo tipo = args.length > 3 ? plugin.tipo(args[3].toLowerCase(Locale.ROOT)) : null;
                if (args.length < 8 || tipo == null) {
                    sender.sendMessage("§cUso: §f/spawners mobs ponto <id> <tipo> <mundo> <x> <y> <z> §7(o bloco do spawner) tipos: "
                            + String.join(", ", plugin.getTipos().keySet()));
                    return;
                }
                try {
                    Ponto p = new Ponto(args[2].toLowerCase(Locale.ROOT), tipo.id(), args[4],
                            Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]));
                    removerHolograma(p.id());
                    pontos.put(p.id(), p);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cCoordenada inválida.");
                    return;
                }
                salvar();
                sender.sendMessage("§aEstátua §f" + args[2] + "§a: " + tipo.nome());
            }
            case "limpar" -> {
                pontos.keySet().forEach(this::removerHolograma);
                pontos.clear();
                salvar();
                sender.sendMessage("§aPontos da warp mobs apagados.");
            }
            case "lista" -> {
                Map<String, Integer> vivos = contarVivos();
                sender.sendMessage("§e§l--- Warp mobs: " + pontos.size() + " estátuas ---");
                sender.sendMessage("§7Área: §f" + (area == null ? "nenhuma" : mundoArea + " " + area));
                for (Ponto p : pontos.values()) {
                    SpawnersPlugin.Tipo t = plugin.tipo(p.tipo());
                    sender.sendMessage("§f" + p.id() + " §7" + (t != null ? t.nome() : p.tipo()) + " §7em " + p.mundo() + " "
                            + p.x() + " " + p.y() + " " + p.z() + " §7vivos: §f" + vivos.getOrDefault(p.id(), 0));
                }
            }
            default -> sender.sendMessage("§f/spawners mobs lista §7| §farea <mundo> <x1> <y1> <z1> <x2> <y2> <z2> §7| "
                    + "§fponto <id> <tipo> <mundo> <x> <y> <z> §7| §flimpar");
        }
    }

    private void removerHolograma(String id) {
        UUID uuid = hologramas.remove(id);
        Entity e = uuid == null ? null : Bukkit.getEntity(uuid);
        if (e != null) e.remove();
    }

    void desligar() {
        new ArrayList<>(hologramas.keySet()).forEach(this::removerHolograma);
    }

    // ---------------------------------------------------------------------
    // mobs.yml
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        List<Double> a = yaml.getDoubleList("area.caixa");
        if (yaml.getString("area.mundo") != null && a.size() == 6) {
            mundoArea = yaml.getString("area.mundo");
            area = new BoundingBox(a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5));
        }
        ConfigurationSection secao = yaml.getConfigurationSection("pontos");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            if (s == null || s.getString("tipo") == null || s.getString("mundo") == null) continue;
            pontos.put(id, new Ponto(id, s.getString("tipo"), s.getString("mundo"), s.getInt("x"), s.getInt("y"), s.getInt("z")));
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (area != null) {
            yaml.set("area.mundo", mundoArea);
            yaml.set("area.caixa", List.of(area.getMinX(), area.getMinY(), area.getMinZ(), area.getMaxX(), area.getMaxY(), area.getMaxZ()));
        }
        for (Ponto p : pontos.values()) {
            String base = "pontos." + p.id();
            yaml.set(base + ".tipo", p.tipo());
            yaml.set(base + ".mundo", p.mundo());
            yaml.set(base + ".x", p.x());
            yaml.set(base + ".y", p.y());
            yaml.set(base + ".z", p.z());
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar mobs.yml", e);
        }
    }
}
