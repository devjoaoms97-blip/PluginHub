package com.example.barracas.listener;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

/**
 * Protege a área das barracas. Dentro de uma barraca:
 * <ul>
 *   <li>Staff ({@code barracas.admin}) faz qualquer coisa — é quem constrói e mantém.</li>
 *   <li>O inquilino só coloca/quebra <b>baús e placas</b> (até o máximo de baús), e só ele abre os baús.</li>
 *   <li>Ninguém mais mexe em nada.</li>
 * </ul>
 * Também bloqueia os jeitos "indiretos" de tirar item ou bloco de lá: funil puxando de baú,
 * pistão, explosão e balde.
 */
public class ProtecaoListener implements Listener {

    private final BarracasPlugin plugin;

    public ProtecaoListener(BarracasPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ehInquilino(Player jogador, Barraca barraca) {
        UUID dono = barraca.getDono();
        return dono != null && dono.equals(jogador.getUniqueId()) && barraca.estaAlugada(System.currentTimeMillis());
    }

    private boolean ehBauOuPlaca(Block bloco) {
        return plugin.isBau(bloco.getType()) || Tag.ALL_SIGNS.isTagged(bloco.getType());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Barraca barraca = plugin.getBarracaManager().getBarracaEm(evento.getBlock());
        if (barraca == null) return;

        BlocoPos pos = BlocoPos.de(evento.getBlock());
        Player jogador = evento.getPlayer();

        if (plugin.isAdmin(jogador)) {
            if (barraca.getBlocosDoDono().remove(pos)) plugin.getBarracaManager().salvar();
            return;
        }
        if (ehInquilino(jogador, barraca) && barraca.getBlocosDoDono().contains(pos)) {
            barraca.getBlocosDoDono().remove(pos);
            plugin.getBarracaManager().salvar();
            return;
        }

        evento.setCancelled(true);
        jogador.sendMessage(ehInquilino(jogador, barraca)
                ? "§cVocê só pode quebrar os baús e placas que você mesmo colocou."
                : "§cEssa área é de uma barraca do mercado.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        Barraca barraca = plugin.getBarracaManager().getBarracaEm(evento.getBlock());
        if (barraca == null) return;

        Player jogador = evento.getPlayer();
        if (plugin.isAdmin(jogador)) return;

        if (!ehInquilino(jogador, barraca)) {
            evento.setCancelled(true);
            jogador.sendMessage("§cEssa área é de uma barraca do mercado. Alugue uma com §f/barraca alugar§c.");
            return;
        }
        if (!ehBauOuPlaca(evento.getBlock())) {
            evento.setCancelled(true);
            jogador.sendMessage("§cNa barraca você só pode colocar baús, barris e placas.");
            return;
        }
        if (plugin.isBau(evento.getBlock().getType())
                && plugin.getBarracaManager().contarBaus(barraca) >= plugin.getMaxBaus(barraca)) {
            evento.setCancelled(true);
            jogador.sendMessage("§cSua barraca já tem o máximo de §f" + plugin.getMaxBaus(barraca) + " §cbaús.");
            return;
        }

        barraca.getBlocosDoDono().add(BlocoPos.de(evento.getBlock()));
        plugin.getBarracaManager().salvar();
    }

    /** Só o inquilino (e a staff) abre baús dentro da barraca. Placas de loja são tratadas no LojaListener. */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoInteragir(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getClickedBlock() == null) return;
        Block bloco = evento.getClickedBlock();
        if (!plugin.isBau(bloco.getType())) return;

        Barraca barraca = plugin.getBarracaManager().getBarracaEm(bloco);
        if (barraca == null) return;

        Player jogador = evento.getPlayer();
        if (plugin.isAdmin(jogador) || ehInquilino(jogador, barraca)) return;

        evento.setCancelled(true);
        jogador.sendMessage("§cEsse baú é de uma barraca. Clique na placa §f[Vender] §cpra comprar (ou §f[Comprar] §cpra vender).");
    }

    /** Ninguém além do inquilino/staff edita placa dentro da barraca (inclusive placas da decoração). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEditarPlaca(SignChangeEvent evento) {
        Barraca barraca = plugin.getBarracaManager().getBarracaEm(evento.getBlock());
        if (barraca == null) return;
        Player jogador = evento.getPlayer();
        if (plugin.isAdmin(jogador)) return;
        if (ehInquilino(jogador, barraca) && barraca.getBlocosDoDono().contains(BlocoPos.de(evento.getBlock()))) return;
        evento.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // Placa de entrada (pode ficar fora da área da barraca)
    // ---------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrarPlacaDeEntrada(BlockBreakEvent evento) {
        Barraca barraca = plugin.getBarracaManager().getBarracaDaPlaca(evento.getBlock());
        if (barraca == null) return;
        if (plugin.isAdmin(evento.getPlayer())) {
            barraca.setPlaca(null);
            plugin.getBarracaManager().salvar();
            evento.getPlayer().sendMessage("§7Placa de entrada da barraca §f" + barraca.getId() + " §7removida.");
            return;
        }
        evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEditarPlacaDeEntrada(SignChangeEvent evento) {
        if (plugin.getBarracaManager().getBarracaDaPlaca(evento.getBlock()) != null) {
            evento.setCancelled(true); // o texto é do plugin, nem a staff edita na mão
        }
    }

    /** Clique na placa de entrada mostra a situação da barraca (e como alugar). */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarPlacaDeEntrada(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getClickedBlock() == null
                || evento.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Barraca barraca = plugin.getBarracaManager().getBarracaDaPlaca(evento.getClickedBlock());
        if (barraca == null) return;
        evento.setCancelled(true);
        evento.getPlayer().performCommand("barraca info " + barraca.getId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        evento.blockList().removeIf(this::protegidoDeExplosao);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        evento.blockList().removeIf(this::protegidoDeExplosao);
    }

    private boolean protegidoDeExplosao(Block bloco) {
        return plugin.getBarracaManager().getBarracaEm(bloco) != null
                || plugin.getBarracaManager().getBarracaDaPlaca(bloco) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEmpurrarPistao(BlockPistonExtendEvent evento) {
        if (mexeEmBarraca(evento.getBlock(), evento.getBlocks(), evento.getDirection().getModX(),
                evento.getDirection().getModY(), evento.getDirection().getModZ())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoPuxarPistao(BlockPistonRetractEvent evento) {
        if (mexeEmBarraca(evento.getBlock(), evento.getBlocks(), evento.getDirection().getModX(),
                evento.getDirection().getModY(), evento.getDirection().getModZ())) {
            evento.setCancelled(true);
        }
    }

    /** Pistão dentro da barraca, ou movendo bloco de/para dentro dela. */
    private boolean mexeEmBarraca(Block pistao, List<Block> movidos, int dx, int dy, int dz) {
        if (plugin.getBarracaManager().getBarracaEm(pistao) != null) return true;
        for (Block bloco : movidos) {
            if (plugin.getBarracaManager().getBarracaEm(bloco) != null) return true;
            if (plugin.getBarracaManager().getBarracaEm(bloco.getRelative(dx, dy, dz)) != null) return true;
        }
        return false;
    }

    /** Funil/dropper puxando de (ou empurrando pra) baú da barraca — senão dava pra roubar o estoque. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMoverItem(InventoryMoveItemEvent evento) {
        if (dentroDeBarraca(evento.getSource().getLocation()) || dentroDeBarraca(evento.getDestination().getLocation())) {
            evento.setCancelled(true);
        }
    }

    private boolean dentroDeBarraca(Location local) {
        return local != null && plugin.getBarracaManager().getBarracaEm(local) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent evento) {
        if (plugin.isAdmin(evento.getPlayer())) return;
        if (plugin.getBarracaManager().getBarracaEm(evento.getBlock()) != null) {
            evento.setCancelled(true);
        }
    }
}
