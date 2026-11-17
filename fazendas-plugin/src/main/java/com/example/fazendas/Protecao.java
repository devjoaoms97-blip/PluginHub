package com.example.fazendas;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.block.data.Directional;

import java.util.List;

/**
 * Ninguém mexe na construção de uma fazenda: nem o dono, nem visitante, nem a staff (só o
 * {@code /fazendas remover}). Nada quebra, coloca, explode, queima, escorre, empurra com pistão,
 * cresce, seca ou é pisoteado dentro da área. A única forma de tirar é recolher a Escritura no NPC.
 */
final class Protecao implements Listener {

    private final FazendasPlugin plugin;

    Protecao(FazendasPlugin plugin) {
        this.plugin = plugin;
    }

    private Fazenda em(Block b) {
        return b == null ? null : plugin.getFazendas().em(b);
    }

    private void avisar(Player p, Fazenda f) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                "§cFazenda de §f" + Bukkit.getOfflinePlayer(f.dono).getName() + "§c: só sai recolhendo a Escritura no fazendeiro."));
    }

    // jogador -------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoQuebrar(BlockBreakEvent evento) {
        Fazenda f = em(evento.getBlock());
        if (f == null) return;
        evento.setCancelled(true);
        avisar(evento.getPlayer(), f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoColocar(BlockPlaceEvent evento) {
        Fazenda f = em(evento.getBlockPlaced());
        if (f == null) return;
        evento.setCancelled(true);
        avisar(evento.getPlayer(), f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent evento) {
        Fazenda f = em(evento.getBlock());
        if (f == null) return;
        evento.setCancelled(true);
        avisar(evento.getPlayer(), f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEncherBalde(PlayerBucketFillEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    /**
     * Clique em bloco da fazenda: nada de usar item nele (enxada, machado, isqueiro, farinha de osso...)
     * nem abrir/girar nada; pisar também não (pisotear a plantação). Comer olhando pra ela pode. A
     * Escritura passa: quem cuida dela é a {@link Colocacao}.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoInteragir(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK && evento.getAction() != Action.PHYSICAL
                && evento.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (em(evento.getClickedBlock()) == null || plugin.tipoDaEscritura(evento.getItem()) != null) return;
        if (evento.getAction() == Action.LEFT_CLICK_BLOCK) return; // quebrar é com o BlockBreakEvent
        evento.setUseInteractedBlock(Event.Result.DENY);
        if (evento.getAction() == Action.PHYSICAL || evento.getItem() == null || !evento.getItem().getType().isEdible()) {
            evento.setUseItemInHand(Event.Result.DENY);
        }
    }

    // mundo ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        evento.blockList().removeIf(b -> em(b) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        evento.blockList().removeIf(b -> em(b) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEmpurrar(BlockPistonExtendEvent evento) {
        if (mexeNaFazenda(evento.getBlocks(), evento.getDirection()) || em(evento.getBlock().getRelative(evento.getDirection())) != null) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoPuxar(BlockPistonRetractEvent evento) {
        if (mexeNaFazenda(evento.getBlocks(), evento.getDirection())) evento.setCancelled(true);
    }

    private boolean mexeNaFazenda(List<Block> blocos, org.bukkit.block.BlockFace direcao) {
        for (Block b : blocos) if (em(b) != null || em(b.getRelative(direcao)) != null) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEscorrer(BlockFromToEvent evento) {
        if (em(evento.getToBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoQueimar(BlockBurnEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoAcender(BlockIgniteEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSumir(BlockFadeEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoFormar(BlockFormEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEspalhar(BlockSpreadEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoCrescer(BlockGrowEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoSecar(MoistureChangeEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoAdubar(BlockFertilizeEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoCairFolha(LeavesDecayEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoArvoreCrescer(StructureGrowEvent evento) {
        evento.getBlocks().removeIf(s -> em(s.getBlock()) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void aoEsponja(SpongeAbsorbEvent evento) {
        evento.getBlocks().removeIf((BlockState s) -> em(s.getBlock()) != null);
    }

    /** Enderman, pisotear plantação, bloco caindo, ovelha comendo grama, Wither... */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoMudarBloco(EntityChangeBlockEvent evento) {
        if (em(evento.getBlock()) != null) evento.setCancelled(true);
    }

    /** Ejetor/dispensador soltando água, lava ou bloco dentro da área. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void aoDispensar(BlockDispenseEvent evento) {
        if (evento.getBlock().getBlockData() instanceof Directional d && em(evento.getBlock().getRelative(d.getFacing())) != null) {
            evento.setCancelled(true);
        }
    }
}
