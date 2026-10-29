package com.example.recursos;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * O centro do End:
 * <ul>
 *   <li><b>Área protegida</b>: até {@code end.raio-protegido} blocos do 0, 0 (em qualquer altura),
 *       ninguém quebra, constrói, despeja balde, e explosão não quebra bloco.</li>
 *   <li><b>Chegada</b>: o {@code /end} leva pro quadrado 3x3 verde ({@code end.plataforma}), que o
 *       plugin põe no chão da ilha quando o mundo carrega (e depois de cada reset). Em cima dele não
 *       tem PvP.</li>
 *   <li><b>Gaiolas</b>: as grades de ferro em volta dos cristais saem, pra todos caírem com flecha
 *       (dentro da área protegida não dá pra subir até eles).</li>
 *   <li><b>Dragão</b>: renasce todo dia no horário do config, se não estiver vivo, pelo ritual do jogo
 *       (4 cristais em volta do portal). O ovo é só enfeite: não quebra e não teleporta.</li>
 * </ul>
 */
public class CentroDoEnd implements Listener {

    private final RecursosPlugin plugin;
    /** O dia em que o dragão já renasceu (ou foi conferido) no horário, pra não repetir. */
    private LocalDate dragaoConferidoEm;
    private int segundos;

    public CentroDoEnd(RecursosPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ehEnd(World w) {
        return w != null && w.getEnvironment() == World.Environment.THE_END && plugin.getResets().containsKey(w.getName());
    }

    private int raioProtegido() {
        return plugin.getConfig().getInt("end.raio-protegido", 100);
    }

    private int[] plataforma() {
        List<Integer> p = plugin.getConfig().getIntegerList("end.plataforma");
        return p.size() >= 2 ? new int[]{p.get(0), p.get(1)} : new int[]{0, 25};
    }

    private boolean protegido(Location l) {
        if (!ehEnd(l.getWorld())) return false;
        int r = raioProtegido();
        return (long) l.getBlockX() * l.getBlockX() + (long) l.getBlockZ() * l.getBlockZ() <= (long) r * r;
    }

    /** Em cima do quadrado verde (as 3x3 colunas, até 4 blocos acima)? */
    private boolean naPlataforma(Location l) {
        if (!ehEnd(l.getWorld())) return false;
        int[] p = plataforma();
        int chao = l.getWorld().getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING);
        return Math.abs(l.getBlockX() - p[0]) <= 1 && Math.abs(l.getBlockZ() - p[1]) <= 1
                && l.getBlockY() >= chao && l.getBlockY() <= chao + 4;
    }

    private static boolean staffConstruindo(Player p) {
        return p.hasPermission("recursos.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    // ---------------------------------------------------------------------
    // Chegada e preparação
    // ---------------------------------------------------------------------

    /** Põe o quadrado verde no chão da ilha (refaz igual, se já estiver lá). */
    public void preparar(World mundo) {
        if (!ehEnd(mundo)) return;
        int[] p = plataforma();
        mundo.getChunkAt(p[0] >> 4, p[1] >> 4);
        int y = mundo.getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING);
        if (y <= mundo.getMinHeight()) {
            plugin.getLogger().warning("Sem chão no End em " + p[0] + ", " + p[1] + ": confira end.plataforma no config.");
            return;
        }
        Material bloco = Material.matchMaterial(plugin.getConfig().getString("end.bloco-plataforma", "LIME_CONCRETE"));
        if (bloco == null || !bloco.isBlock()) bloco = Material.LIME_CONCRETE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                mundo.getBlockAt(p[0] + dx, y, p[1] + dz).setType(bloco, false);
                for (int dy = 1; dy <= 3; dy++) mundo.getBlockAt(p[0] + dx, y + dy, p[1] + dz).setType(Material.AIR, false);
            }
        }
        mundo.setSpawnLocation(p[0], y + 1, p[1]);
        removerGaiolas(mundo);
    }

    /** Em cima do quadrado verde, olhando pro portal. */
    public Location chegada(World mundo) {
        int[] p = plataforma();
        int y = mundo.getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING);
        Location l = new Location(mundo, p[0] + 0.5, y + 1, p[1] + 0.5);
        l.setDirection(new Location(mundo, 0.5, y + 1, 0.5).toVector().subtract(l.toVector()));
        return l;
    }

    /** Tira as grades de ferro em volta dos cristais carregados perto do centro. */
    public void removerGaiolas(World mundo) {
        if (!plugin.getConfig().getBoolean("end.remover-gaiolas", true)) return;
        int r = raioProtegido();
        for (EnderCrystal cristal : mundo.getEntitiesByClass(EnderCrystal.class)) {
            Location c = cristal.getLocation();
            if (c.getX() * c.getX() + c.getZ() * c.getZ() > (double) r * r) continue;
            // a gaiola é um cubo de grades de 5x5 em volta do cristal, do topo do pilar até 2 acima dele
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 4; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block b = c.getBlock().getRelative(dx, dy, dz);
                        if (b.getType() == Material.IRON_BARS) b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Dragão (a cada segundo)
    // ---------------------------------------------------------------------

    public void tick() {
        // gaiolas: a cada 30 s, com gente no End (o ritual do dragão refaz os pilares)
        if (++segundos % 30 == 0) {
            for (String nome : plugin.getResets().keySet()) {
                World w = Bukkit.getWorld(nome);
                if (ehEnd(w) && !w.getPlayers().isEmpty()) removerGaiolas(w);
            }
        }
        if (!plugin.getConfig().getBoolean("end.dragao.renascer", true)) return;
        LocalTime hora;
        try {
            hora = LocalTime.parse(plugin.getConfig().getString("end.dragao.hora", "21:00"));
        } catch (DateTimeParseException e) {
            return;
        }
        // só nos 10 minutos do horário: um reinício mais tarde no mesmo dia não renasce de novo o dragão já morto
        LocalDate hoje = LocalDate.now();
        LocalTime agora = LocalTime.now();
        if (agora.isBefore(hora) || agora.isAfter(hora.plusMinutes(10)) || hoje.equals(dragaoConferidoEm)) return;
        dragaoConferidoEm = hoje;
        for (String nome : plugin.getResets().keySet()) {
            World w = Bukkit.getWorld(nome);
            if (ehEnd(w)) renascerDragao(w, false);
        }
    }

    /** Renasce o dragão, se ele não estiver vivo nem renascendo. Devolve o que aconteceu. */
    public String renascerDragao(World mundo, boolean staff) {
        DragonBattle luta = mundo.getEnderDragonBattle();
        if (luta == null) return "Esse mundo não tem a luta do dragão.";
        if (luta.getEnderDragon() != null && !luta.getEnderDragon().isDead()) return "O dragão já está vivo.";
        if (luta.getRespawnPhase() != DragonBattle.RespawnPhase.NONE) return "O dragão já está renascendo.";
        // o jogo só renasce um dragão que já morreu: sem dragão e sem a marca (o primeiro sumiu sem morrer), marca
        if (!luta.hasBeenPreviouslyKilled()) luta.setPreviouslyKilled(true);
        Location portal = luta.getEndPortalLocation();
        if (portal == null) {
            luta.generateEndPortal(false);
            portal = luta.getEndPortalLocation();
            if (portal == null) return "Não achei o portal do End.";
        }
        mundo.getChunkAt(portal);
        // o ritual do jogo: um cristal em cada lado do portal
        List<EnderCrystal> cristais = new ArrayList<>();
        for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            Location l = portal.clone().add(d[0] + 0.5, 1, d[1] + 0.5);
            cristais.add(mundo.spawn(l, EnderCrystal.class, c -> c.setShowingBottom(false)));
        }
        if (!luta.initiateRespawn(cristais)) {
            cristais.forEach(Entity::remove);
            return "O jogo não aceitou o renascimento do dragão.";
        }
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("end.dragao.mensagem", "&5&l🐉 O Dragão do End renasceu! &dVá com &f/end&d."))));
        return staff ? "Dragão renascendo." : null;
    }

    // ---------------------------------------------------------------------
    // Proteção, PvP e ovo
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (evento.getBlock().getType() == Material.DRAGON_EGG && ehEnd(evento.getBlock().getWorld()) && !staffConstruindo(evento.getPlayer())) {
            evento.setCancelled(true);
            return;
        }
        if (protegido(evento.getBlock().getLocation()) && !staffConstruindo(evento.getPlayer())) {
            evento.setCancelled(true);
            evento.getPlayer().sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§cO centro do End é protegido: até " + raioProtegido() + " blocos do portal, ninguém quebra nem constrói."));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (protegido(evento.getBlock().getLocation()) && !staffConstruindo(evento.getPlayer())) {
            evento.setCancelled(true);
            evento.getPlayer().sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§cO centro do End é protegido: até " + raioProtegido() + " blocos do portal, ninguém quebra nem constrói."));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoDespejar(PlayerBucketEmptyEvent evento) {
        if (protegido(evento.getBlock().getLocation()) && !staffConstruindo(evento.getPlayer())) evento.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodir(EntityExplodeEvent evento) {
        if (ehEnd(evento.getLocation().getWorld())) evento.blockList().removeIf(b -> protegido(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        if (ehEnd(evento.getBlock().getWorld())) evento.blockList().removeIf(b -> protegido(b.getLocation()));
    }

    /** O ovo teleporta quando alguém clica nele: aqui ele fica parado. */
    @EventHandler(priority = EventPriority.LOW)
    public void aoClicarNoOvo(PlayerInteractEvent evento) {
        Block b = evento.getClickedBlock();
        if (b == null || b.getType() != Material.DRAGON_EGG || !ehEnd(b.getWorld())) return;
        if (evento.getAction() != Action.LEFT_CLICK_BLOCK && evento.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (staffConstruindo(evento.getPlayer()) && evento.getAction() == Action.LEFT_CLICK_BLOCK) return;
        evento.setCancelled(true);
    }

    /** Sem PvP em cima do quadrado verde: nem quem está nele apanha, nem bate. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoBater(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player vitima)) return;
        Player atacante = evento.getDamager() instanceof Player p ? p
                : evento.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (atacante == null || atacante.equals(vitima)) return;
        if (naPlataforma(vitima.getLocation()) || naPlataforma(atacante.getLocation())) {
            evento.setCancelled(true);
            atacante.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§aNo quadrado verde do End não tem PvP."));
        }
    }
}
