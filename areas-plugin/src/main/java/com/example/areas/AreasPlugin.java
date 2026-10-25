package com.example.areas;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Áreas fechadas: lugares de onde só se sai pelo caminho (portões, portais, /spawn), pra quem joga e
 * pra quem assiste. Lá dentro não dá pra voar (/fly do VIP), planar de elytra, jogar pérola do fim,
 * usar tridente com Correnteza, comer fruta do coro nem carga de vento. Quem entra voando cai, sem
 * dano de queda. Os admins ({@code areas.ignorar}) e quem está no criativo passam. No modo
 * espectador o jogo sempre voa e atravessa parede: quem assiste duelo assim fica preso pelo DueloPlugin
 * (raio em volta da arena, sem comandos nem teleporte).
 * <p>
 * Cada área é um mundo inteiro ou uma caixa nele ({@code areas} no config).
 */
public class AreasPlugin extends JavaPlugin implements Listener {

    /** Uma área: o mundo e, se tiver, a caixa (sem caixa, o mundo inteiro). */
    private record Area(String nome, String mundo, int[] min, int[] max) {
        boolean contem(Location l) {
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
            if (min == null) return true;
            return l.getX() >= min[0] && l.getX() <= max[0] + 1 && l.getY() >= min[1] && l.getY() <= max[1] + 1
                    && l.getZ() >= min[2] && l.getZ() <= max[2] + 1;
        }
    }

    private final List<Area> areas = new ArrayList<>();
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        carregar();
        getServer().getPluginManager().registerEvents(this, this);
        // quem entra voando ou planando (pelo portão, por teleporte) cai na hora
        getServer().getScheduler().runTaskTimer(this, this::conferirVoo, 10L, 10L);
        getLogger().info("AreasPlugin ativado! " + areas.size() + " área(s) fechada(s).");
    }

    private void carregar() {
        areas.clear();
        ConfigurationSection secao = getConfig().getConfigurationSection("areas");
        if (secao == null) return;
        for (String nome : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(nome);
            List<Integer> min = s.getIntegerList("min"), max = s.getIntegerList("max");
            boolean caixa = min.size() == 3 && max.size() == 3;
            areas.add(new Area(nome, s.getString("mundo", nome),
                    caixa ? new int[]{Math.min(min.get(0), max.get(0)), Math.min(min.get(1), max.get(1)), Math.min(min.get(2), max.get(2))} : null,
                    caixa ? new int[]{Math.max(min.get(0), max.get(0)), Math.max(min.get(1), max.get(1)), Math.max(min.get(2), max.get(2))} : null));
        }
    }

    /** A área fechada em que o lugar está, ou null. */
    private Area areaEm(Location l) {
        for (Area a : areas) if (a.contem(l)) return a;
        return null;
    }

    /** Vale a regra pra esse jogador aqui? Os admins (areas.ignorar) e o criativo passam. */
    private boolean preso(Player p, Location onde) {
        if (p.hasPermission("areas.ignorar") || p.getGameMode() == GameMode.CREATIVE) return false;
        return areaEm(onde) != null;
    }

    private boolean bloqueia(String coisa) {
        return getConfig().getBoolean("bloquear." + coisa, true);
    }

    private void avisar(Player p, String coisa) {
        long agora = System.currentTimeMillis();
        if (agora - ultimoAviso.getOrDefault(p.getUniqueId(), 0L) < 2000) return;
        ultimoAviso.put(p.getUniqueId(), agora);
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("mensagem",
                "&cAqui não dá pra %coisa%: saia pelos portões.").replace("%coisa%", coisa)));
    }

    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoLancar(PlayerLaunchProjectileEvent evento) {
        Player p = evento.getPlayer();
        boolean perola = evento.getProjectile() instanceof EnderPearl && bloqueia("perola");
        boolean vento = evento.getProjectile() instanceof WindCharge && bloqueia("carga-de-vento");
        if ((perola || vento) && preso(p, p.getLocation())) {
            evento.setShouldConsume(false);
            evento.setCancelled(true);
            avisar(p, perola ? "usar pérola do fim" : "usar carga de vento");
        }
    }

    /** Pérola ou fruta do coro: nem saindo de dentro, nem caindo lá dentro de fora. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        String causa = evento.getCause().name();
        boolean perola = causa.equals("ENDER_PEARL") && bloqueia("perola");
        boolean coro = (causa.equals("CHORUS_FRUIT") || causa.equals("CONSUMABLE_EFFECT")) && bloqueia("fruta-do-coro");
        if (!perola && !coro) return;
        Player p = evento.getPlayer();
        if (preso(p, evento.getFrom()) || preso(p, evento.getTo())) {
            evento.setCancelled(true);
            avisar(p, perola ? "usar pérola do fim" : "se teleportar com a fruta do coro");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPlanar(EntityToggleGlideEvent evento) {
        if (!evento.isGliding() || !bloqueia("elytra") || !(evento.getEntity() instanceof Player p)) return;
        if (preso(p, p.getLocation())) {
            evento.setCancelled(true);
            avisar(p, "planar de elytra");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoVoar(PlayerToggleFlightEvent evento) {
        if (!evento.isFlying() || !bloqueia("voo")) return;
        Player p = evento.getPlayer();
        if (preso(p, p.getLocation())) {
            evento.setCancelled(true);
            avisar(p, "voar");
        }
    }

    /** Tridente com Correnteza: barra o uso (segurar o botão direito) lá dentro. */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoUsarTridente(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_AIR && evento.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = evento.getItem();
        if (item == null || item.getType() != Material.TRIDENT || !item.containsEnchantment(Enchantment.RIPTIDE)) return;
        if (bloqueia("tridente-correnteza") && preso(evento.getPlayer(), evento.getPlayer().getLocation())) {
            evento.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            avisar(evento.getPlayer(), "usar tridente com Correnteza");
        }
    }

    private void conferirVoo() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // no modo espectador o voo é do próprio modo (não dá pra desligar): quem cuida é o DueloPlugin
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!(p.isFlying() && bloqueia("voo")) && !(p.isGliding() && bloqueia("elytra"))) continue;
            if (!preso(p, p.getLocation())) continue;
            String oQue = p.isGliding() ? "planar de elytra" : "voar";
            p.setFlying(false);
            p.setGliding(false);
            p.setFallDistance(0);
            avisar(p, oQue);
        }
    }

    /** Sem dano da queda de quem foi tirado do voo aqui dentro. */
    @EventHandler(ignoreCancelled = true)
    public void aoCair(org.bukkit.event.entity.EntityDamageEvent evento) {
        if (evento.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL || !(evento.getEntity() instanceof Player p)) return;
        if (p.getAllowFlight() && bloqueia("voo") && areaEm(p.getLocation()) != null && !p.hasPermission("areas.ignorar")) {
            evento.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            carregar();
            sender.sendMessage("§aÁreas recarregadas (" + areas.size() + ").");
            return true;
        }
        sender.sendMessage("§e§l--- Áreas fechadas ---");
        for (Area a : areas) {
            sender.sendMessage("§f" + a.nome() + " §7: " + a.mundo() + (a.min() == null ? " (mundo inteiro)"
                    : " de " + a.min()[0] + "," + a.min()[1] + "," + a.min()[2] + " a " + a.max()[0] + "," + a.max()[1] + "," + a.max()[2]));
        }
        if (sender instanceof Player p) {
            Area aqui = areaEm(p.getLocation());
            sender.sendMessage("§7Você está em: §f" + (aqui == null ? "nenhuma" : aqui.nome()));
        }
        return true;
    }
}
