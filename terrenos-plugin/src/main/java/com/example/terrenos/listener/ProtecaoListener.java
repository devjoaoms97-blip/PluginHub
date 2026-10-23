package com.example.terrenos.listener;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.model.TerrenoId;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Proteção do mundo de terrenos. Só o dono e os amigos mexem num terreno (construir, quebrar,
 * abrir baú, usar porta, bater em animal, tirar item de moldura...). A rua e os terrenos sem dono
 * ou em leilão são só da staff ({@code terrenos.admin}). Também barra o que atravessa a divisa:
 * água e lava escorrendo, pistão, árvore crescendo, fogo e explosão.
 */
public class ProtecaoListener implements Listener {

    private final TerrenosPlugin plugin;

    public ProtecaoListener(TerrenosPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean noMundo(Location local) {
        return local != null && local.getWorld() != null && local.getWorld().equals(plugin.getMundo());
    }

    /** Pode construir e usar nessa posição. Fora do mundo de terrenos, sempre pode (não é com a gente). */
    private boolean pode(Player jogador, Location local) {
        if (!noMundo(local) || plugin.isAdmin(jogador)) return true;
        Terreno t = plugin.getTerrenoManager().getEm(local);
        return t != null && t.temDono() && t.podeMexer(jogador.getUniqueId());
    }

    private void negar(Player jogador, Location local, Cancellable evento) {
        evento.setCancelled(true);
        Terreno t = plugin.getTerrenoManager().getEm(local);
        TerrenoId id = plugin.getTerrenoManager().idEm(local);
        jogador.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                id == null ? "§cA rua é protegida."
                        : t == null ? "§cTerreno à venda: §f/terreno comprar"
                        : t.emLeilao() ? "§cTerreno em leilão: §f/terreno lance " + id + " <valor>"
                        : "§cTerreno de §f" + t.getNomeDono() + "§c."));
    }

    /** Mesmo lugar da grade (o mesmo terreno, ou os dois na rua). */
    private boolean mesmoLugar(Location a, Location b) {
        return Objects.equals(plugin.getTerrenoManager().idEm(a), plugin.getTerrenoManager().idEm(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (!pode(evento.getPlayer(), evento.getBlock().getLocation())) negar(evento.getPlayer(), evento.getBlock().getLocation(), evento);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (!pode(evento.getPlayer(), evento.getBlock().getLocation())) negar(evento.getPlayer(), evento.getBlock().getLocation(), evento);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent evento) {
        if (!pode(evento.getPlayer(), evento.getBlock().getLocation())) negar(evento.getPlayer(), evento.getBlock().getLocation(), evento);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEncherBalde(PlayerBucketFillEvent evento) {
        if (!pode(evento.getPlayer(), evento.getBlock().getLocation())) negar(evento.getPlayer(), evento.getBlock().getLocation(), evento);
    }

    /**
     * Clique em bloco (baú, porta, botão, alavanca, cama...) e pisar em placa de pressão ou
     * plantação. Visitante pode comer olhando pra um bloco, mas não usar item nele (isqueiro,
     * farinha de osso, ovo de mob, balde...).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoInteragir(PlayerInteractEvent evento) {
        Block bloco = evento.getClickedBlock();
        if (bloco == null || (evento.getAction() != Action.RIGHT_CLICK_BLOCK && evento.getAction() != Action.PHYSICAL)) return;
        if (pode(evento.getPlayer(), bloco.getLocation())) return;

        evento.setUseInteractedBlock(Event.Result.DENY);
        ItemStack item = evento.getItem();
        if (evento.getAction() == Action.PHYSICAL || item == null || !item.getType().isEdible()) {
            evento.setUseItemInHand(Event.Result.DENY);
        }
        if (evento.getAction() == Action.RIGHT_CLICK_BLOCK && (bloco.getType().isInteractable() || item != null)) {
            negar(evento.getPlayer(), bloco.getLocation(), evento);
        } else {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoInteragirComEntidade(PlayerInteractEntityEvent evento) {
        Location local = evento.getRightClicked().getLocation();
        if (!(evento.getRightClicked() instanceof Player) && !pode(evento.getPlayer(), local)) negar(evento.getPlayer(), local, evento);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMexerNoSuporte(PlayerArmorStandManipulateEvent evento) {
        Location local = evento.getRightClicked().getLocation();
        if (!pode(evento.getPlayer(), local)) negar(evento.getPlayer(), local, evento);
    }

    /** Bater em animal, suporte de armadura, moldura... (direto ou com flecha). PvP é do mundo. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoBaterEmEntidade(EntityDamageByEntityEvent evento) {
        if (evento.getEntity() instanceof Player) return;
        Player jogador = quemFoi(evento.getDamager());
        if (jogador != null && !pode(jogador, evento.getEntity().getLocation())) {
            negar(jogador, evento.getEntity().getLocation(), evento);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrarPendurado(HangingBreakByEntityEvent evento) {
        Player jogador = quemFoi(evento.getRemover());
        Location local = evento.getEntity().getLocation();
        if (jogador != null) {
            if (!pode(jogador, local)) negar(jogador, local, evento);
        } else if (noMundo(local)) {
            evento.setCancelled(true); // explosão, mob...
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPendurar(HangingPlaceEvent evento) {
        Location local = evento.getEntity().getLocation();
        if (evento.getPlayer() != null && !pode(evento.getPlayer(), local)) negar(evento.getPlayer(), local, evento);
    }

    private Player quemFoi(Entity entidade) {
        if (entidade instanceof Player p) return p;
        if (entidade instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---------------------------------------------------------------------
    // O que atravessa a divisa
    // ---------------------------------------------------------------------
    /** Explosão só quebra blocos do mesmo terreno em que explodiu; na rua, nada. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        filtrarExplosao(evento.getLocation(), evento.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        filtrarExplosao(evento.getBlock().getLocation(), evento.blockList());
    }

    private void filtrarExplosao(Location origem, List<Block> blocos) {
        if (!noMundo(origem)) return;
        TerrenoId id = plugin.getTerrenoManager().idEm(origem);
        if (id == null) {
            blocos.clear();
            return;
        }
        blocos.removeIf(b -> !id.equals(plugin.getTerrenoManager().idEm(b.getLocation())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEscorrer(BlockFromToEvent evento) {
        Location de = evento.getBlock().getLocation();
        if (noMundo(de) && !mesmoLugar(de, evento.getToBlock().getLocation())) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEspalhar(BlockSpreadEvent evento) {
        Location de = evento.getSource().getLocation();
        if (noMundo(de) && !mesmoLugar(de, evento.getBlock().getLocation())) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoCrescer(StructureGrowEvent evento) {
        if (!noMundo(evento.getLocation())) return;
        evento.getBlocks().removeIf((BlockState b) -> !mesmoLugar(evento.getLocation(), b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEmpurrarPistao(BlockPistonExtendEvent evento) {
        if (pistaoAtravessa(evento.getBlock(), evento.getBlocks(), evento.getDirection().getModX(),
                evento.getDirection().getModY(), evento.getDirection().getModZ())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPuxarPistao(BlockPistonRetractEvent evento) {
        if (pistaoAtravessa(evento.getBlock(), evento.getBlocks(), evento.getDirection().getModX(),
                evento.getDirection().getModY(), evento.getDirection().getModZ())) {
            evento.setCancelled(true);
        }
    }

    /** Algum bloco movido (ou o lugar pra onde ele vai) fica fora do terreno do pistão. */
    private boolean pistaoAtravessa(Block pistao, List<Block> movidos, int dx, int dy, int dz) {
        Location origem = pistao.getLocation();
        if (!noMundo(origem)) return false;
        if (plugin.getTerrenoManager().idEm(origem) == null) return true;
        // a cabeça do pistão também ocupa o bloco da frente
        if (!mesmoLugar(origem, pistao.getRelative(dx, dy, dz).getLocation())) return true;
        for (Block b : movidos) {
            if (!mesmoLugar(origem, b.getLocation()) || !mesmoLugar(origem, b.getRelative(dx, dy, dz).getLocation())) return true;
        }
        return false;
    }
}
