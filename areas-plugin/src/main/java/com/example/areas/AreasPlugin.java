package com.example.areas;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Áreas fechadas: lugares de onde só se sai pelo caminho (portões, portais, /spawn), pra quem joga e
 * pra quem assiste. Lá dentro não dá pra voar (/fly do VIP), planar de elytra, jogar pérola do fim,
 * usar tridente com Correnteza, comer fruta do coro nem carga de vento. Quem entra voando cai, sem
 * dano de queda. Também não dá pra quebrar nem colocar blocos (fora das minas do RankupPlugin), usar
 * balde, pôr fogo, pisotear plantação nem mexer em moldura, quadro e suporte de armadura; explosão
 * não abre buraco. Os admins ({@code areas.ignorar}) e quem está no criativo passam. No modo
 * espectador o jogo sempre voa e atravessa parede: quem assiste duelo assim fica preso pelo DueloPlugin
 * (raio em volta da arena, sem comandos nem teleporte).
 * <p>
 * Cada área é um mundo inteiro ou uma caixa nele ({@code areas} no config).
 * <p>
 * Nas áreas de {@code teleporte-por-comando.areas} (as arenas), os comandos de teleporte entre
 * jogadores e de casa ({@code /tpa}, {@code /tpaccept}, {@code /back}, {@code /home}, {@code /sethome}...)
 * não funcionam lá dentro, e o teleporte que um deles acabou de pedir não leva ninguém pra lá
 * (um {@code /back} pro lugar em que morreu na arena, uma casa antiga marcada lá). /spawn e /warp
 * (a plateia) continuam valendo.
 * <p>
 * PvP só em partes de um mundo ({@code pvp-so-nas-caixas}): no mundo spawn o PvP do jogo fica ligado,
 * mas um jogador só machuca outro se os dois estiverem dentro de uma das caixas (a arena do boss).
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
    /** Quando cada jogador usou por último um comando de teleporte da lista (o teleporte vem logo depois). */
    private final Map<UUID, Long> comandoDeTeleporte = new HashMap<>();
    /** RankupPlugin.getMinaManager() e MinaManager.getMinaEm(Location), achados por reflexão. */
    private Method getMinaManager, getMinaEm;
    private boolean avisouFalhaRankup;

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

    // ---------------------------------------------------------------------
    // PvP só nas caixas (a arena do boss, no mundo spawn)
    // ---------------------------------------------------------------------

    /** O mundo tem PvP só em caixas, e esse lugar está fora de todas? */
    private boolean pvpBarrado(Location l) {
        ConfigurationSection s = getConfig().getConfigurationSection("pvp-so-nas-caixas");
        if (l == null || l.getWorld() == null || s == null || !s.isList(l.getWorld().getName())) return false;
        for (Map<?, ?> m : s.getMapList(l.getWorld().getName())) {
            List<?> min = m.get("min") instanceof List<?> a ? a : null, max = m.get("max") instanceof List<?> b ? b : null;
            if (min == null || max == null || min.size() != 3 || max.size() != 3) continue;
            boolean dentro = true;
            double[] p = {l.getX(), l.getY(), l.getZ()};
            for (int i = 0; i < 3 && dentro; i++) {
                double a = ((Number) min.get(i)).doubleValue(), b = ((Number) max.get(i)).doubleValue();
                dentro = p[i] >= Math.min(a, b) && p[i] <= Math.max(a, b) + 1;
            }
            if (dentro) return false;
        }
        return true;
    }

    /** Jogador contra jogador (golpe, flecha, tridente...): só vale com os dois dentro de uma caixa. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoPvp(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player vitima)) return;
        Player atacante = jogadorDe(evento.getDamager());
        if (atacante == null || atacante == vitima) return;
        if (pvpBarrado(vitima.getLocation()) || pvpBarrado(atacante.getLocation())) evento.setCancelled(true);
    }

    /** Poção de arremesso de um jogador não pega em outro fora das caixas. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoPocao(org.bukkit.event.entity.PotionSplashEvent evento) {
        if (!(evento.getPotion().getShooter() instanceof Player atirador)) return;
        for (org.bukkit.entity.LivingEntity alvo : evento.getAffectedEntities()) {
            if (alvo instanceof Player p && p != atirador && (pvpBarrado(p.getLocation()) || pvpBarrado(atirador.getLocation()))) {
                evento.setIntensity(alvo, 0);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Teleporte por comando (/tpa, /back, /home...) nas arenas
    // ---------------------------------------------------------------------

    /** Área sem teleporte por comando em que o lugar está, ou null. */
    private Area semTeleporte(Location l) {
        Area a = areaEm(l);
        return a != null && getConfig().getStringList("teleporte-por-comando.areas").contains(a.nome()) ? a : null;
    }

    /** O comando (sem a barra e sem o "plugin:" na frente) é da lista de teleporte? */
    private boolean comandoDeTeleporte(String mensagem) {
        String rotulo = mensagem.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        rotulo = rotulo.substring(rotulo.indexOf(':') + 1);
        return getConfig().getStringList("teleporte-por-comando.comandos").contains(rotulo);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoUsarComando(PlayerCommandPreprocessEvent evento) {
        if (!comandoDeTeleporte(evento.getMessage())) return;
        Player p = evento.getPlayer();
        if (p.hasPermission("areas.ignorar")) return;
        if (semTeleporte(p.getLocation()) != null) {
            evento.setCancelled(true);
            avisar(p, "usar esse comando de teleporte");
            return;
        }
        comandoDeTeleporte.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** O teleporte que um comando da lista pediu (até 10 s antes: o /back e o /home esperam) não entra nas arenas. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportarPorComando(PlayerTeleportEvent evento) {
        Player p = evento.getPlayer();
        Long quando = comandoDeTeleporte.get(p.getUniqueId());
        if (quando == null || System.currentTimeMillis() - quando > 10_000 || p.hasPermission("areas.ignorar")) return;
        if (semTeleporte(evento.getTo()) != null) {
            evento.setCancelled(true);
            comandoDeTeleporte.remove(p.getUniqueId());
            avisar(p, "chegar por teleporte de comando: entre pelo /warp");
        }
    }

    // ---------------------------------------------------------------------
    // Construção: ninguém quebra nem coloca nada (as minas do RankupPlugin ficam de fora)
    // ---------------------------------------------------------------------

    /** O lugar está dentro de uma mina do RankupPlugin? Lá quem cuida das regras é ele. */
    private boolean emMina(Location l) {
        Plugin rankup = getServer().getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return false;
        try {
            if (getMinaManager == null) getMinaManager = rankup.getClass().getMethod("getMinaManager");
            Object minas = getMinaManager.invoke(rankup);
            if (getMinaEm == null) getMinaEm = minas.getClass().getMethod("getMinaEm", Location.class);
            return getMinaEm.invoke(minas, l) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!avisouFalhaRankup) {
                getLogger().warning("Não foi possível consultar as minas no RankupPlugin (versão antiga?): " + e.getMessage());
                avisouFalhaRankup = true;
            }
            return false;
        }
    }

    /** Lugar protegido contra o que não vem de jogador (explosão, fogo que espalha, mob): numa área, fora das minas. */
    private boolean protegido(Location onde) {
        return bloqueia("construir") && areaEm(onde) != null && !emMina(onde);
    }

    /** Barra o jogador de mexer aqui? Admin e criativo passam. */
    private boolean barraConstrucao(Player p, Location onde) {
        return !p.hasPermission("areas.ignorar") && p.getGameMode() != GameMode.CREATIVE && protegido(onde);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (barraConstrucao(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
            avisar(evento.getPlayer(), "quebrar blocos");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (barraConstrucao(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
            avisar(evento.getPlayer(), "colocar blocos");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent evento) {
        if (barraConstrucao(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
            avisar(evento.getPlayer(), "usar balde");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoEncherBalde(PlayerBucketFillEvent evento) {
        if (barraConstrucao(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
            avisar(evento.getPlayer(), "usar balde");
        }
    }

    /** Fogo: o de isqueiro e carga de fogo, e o que se espalha sozinho. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoPegarFogo(BlockIgniteEvent evento) {
        Location onde = evento.getBlock().getLocation();
        Player p = evento.getPlayer();
        if (p != null ? barraConstrucao(p, onde) : protegido(onde)) {
            evento.setCancelled(true);
            if (p != null) avisar(p, "pôr fogo");
        }
    }

    /** Pisotear plantação (pulando em cima). */
    @EventHandler(priority = EventPriority.LOW)
    public void aoPisar(PlayerInteractEvent evento) {
        Block bloco = evento.getClickedBlock();
        if (evento.getAction() != Action.PHYSICAL || bloco == null || bloco.getType() != Material.FARMLAND) return;
        if (barraConstrucao(evento.getPlayer(), bloco.getLocation())) evento.setCancelled(true);
    }

    /** Mob que mexe em bloco (enderman pegando, plantação pisoteada, ravager...). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoMobMexer(EntityChangeBlockEvent evento) {
        if (evento.getEntity() instanceof FallingBlock) return; // areia e cascalho caindo
        Location onde = evento.getBlock().getLocation();
        if (evento.getEntity() instanceof Player p ? barraConstrucao(p, onde) : protegido(onde)) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        evento.blockList().removeIf(b -> protegido(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        evento.blockList().removeIf(b -> protegido(b.getLocation()));
    }

    /** Moldura e quadro: nem pôr, nem quebrar, nem girar ou tirar o item. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoPendurar(HangingPlaceEvent evento) {
        if (evento.getPlayer() != null && barraConstrucao(evento.getPlayer(), evento.getEntity().getLocation())) {
            evento.setCancelled(true);
            avisar(evento.getPlayer(), "colocar blocos");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoQuebrarPendurado(HangingBreakByEntityEvent evento) {
        Player p = jogadorDe(evento.getRemover());
        Location onde = evento.getEntity().getLocation();
        // sem jogador (explosão, mob, flecha de esqueleto): também não quebra
        if (p != null ? barraConstrucao(p, onde) : protegido(onde)) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoMexerEmEntidade(PlayerInteractEntityEvent evento) {
        Entity alvo = evento.getRightClicked();
        if ((alvo instanceof Hanging || alvo instanceof ArmorStand) && barraConstrucao(evento.getPlayer(), alvo.getLocation())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoMexerNoSuporte(PlayerArmorStandManipulateEvent evento) {
        if (barraConstrucao(evento.getPlayer(), evento.getRightClicked().getLocation())) evento.setCancelled(true);
    }

    /** Bater na moldura (tira o item) ou no suporte de armadura (quebra). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoBaterEmDecoracao(EntityDamageByEntityEvent evento) {
        Entity alvo = evento.getEntity();
        if (!(alvo instanceof Hanging) && !(alvo instanceof ArmorStand)) return;
        Player p = jogadorDe(evento.getDamager());
        if (p != null ? barraConstrucao(p, alvo.getLocation()) : protegido(alvo.getLocation())) evento.setCancelled(true);
    }

    private static Player jogadorDe(Entity causa) {
        if (causa instanceof Player p) return p;
        if (causa instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
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
