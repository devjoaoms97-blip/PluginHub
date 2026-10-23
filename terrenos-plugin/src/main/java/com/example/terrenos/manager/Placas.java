package com.example.terrenos.manager;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.Leilao;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.model.TerrenoId;
import com.example.terrenos.mundo.Grade;
import com.example.terrenos.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Placa na entrada de cada terreno: na rua, em cima da borda de laje, no meio do lado norte, de
 * frente pra quem chega pelo /terreno home ou /terreno ir. Mostra "À venda" com o preço, o dono,
 * ou o leilão com o lance atual. É reescrita quando o terreno muda de situação e quando o chunk
 * carrega (assim os terrenos à venda ganham placa sem precisar guardar nada). Clicar nela mostra
 * o /terreno info.
 */
public class Placas implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final TerrenosPlugin plugin;

    public Placas(TerrenosPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ativadas() {
        return plugin.getConfig().getBoolean("placas.ativado", true);
    }

    /** Posição da placa do terreno: no meio do lado norte, sobre a laje. */
    private Block bloco(World mundo, TerrenoId id) {
        Grade g = plugin.getGrade();
        return mundo.getBlockAt(g.minX(id) + g.getTamanho() / 2, g.getAlturaChao() + 2, g.minZ(id) - 1);
    }

    /** O terreno cuja placa é esse bloco, ou null. */
    private TerrenoId terrenoDaPlaca(Block bloco) {
        if (!bloco.getWorld().equals(plugin.getMundo())) return null;
        TerrenoId id = plugin.getGrade().terrenoEm(bloco.getX(), bloco.getZ() + 1);
        return id != null && bloco(bloco.getWorld(), id).equals(bloco) ? id : null;
    }

    /** Reescreve a placa do terreno, se o chunk dela estiver carregado. */
    public void atualizar(TerrenoId id) {
        World mundo = plugin.getMundo();
        if (mundo == null || !ativadas() || !plugin.getTerrenoManager().dentroDaGrade(id)) return;
        Block b = bloco(mundo, id);
        if (!mundo.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return;
        escrever(b, id);
    }

    private void escrever(Block b, TerrenoId id) {
        if (b.getType() != Material.OAK_SIGN) {
            b.setType(Material.OAK_SIGN, false);
        }
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Sign dados && dados.getRotation() != BlockFace.NORTH) {
            dados.setRotation(BlockFace.NORTH);
            b.setBlockData(dados, false);
        }
        if (!(b.getState() instanceof Sign placa)) return;
        String[] linhas = linhas(id);
        SignSide frente = placa.getSide(Side.FRONT);
        boolean mudou = false;
        for (int i = 0; i < 4; i++) {
            if (!LEGADO.serialize(frente.line(i)).equals(linhas[i])) {
                frente.line(i, LEGADO.deserialize(linhas[i]));
                mudou = true;
            }
        }
        if (mudou || !placa.isWaxed()) {
            placa.setWaxed(true); // ninguém edita com o clique
            placa.update(true, false);
        }
    }

    private String[] linhas(TerrenoId id) {
        Terreno t = plugin.getTerrenoManager().get(id);
        String titulo = "§8[§2Terreno §f" + id + "§8]";
        if (t == null) {
            return new String[]{titulo, "§a§lÀ VENDA", "§f" + Formato.valor(plugin.getTerrenoManager().precoCompra()), "§7/terreno comprar"};
        }
        if (t.emLeilao()) {
            Leilao l = t.getLeilao();
            return new String[]{titulo, "§6§lEM LEILÃO", l.temLance() ? "§f" + Formato.valor(l.getLance()) : "§7sem lances",
                    "§7/terreno lance"};
        }
        return new String[]{titulo, "§7Dono:", "§f" + t.getNomeDono(), ""};
    }

    /** Os terrenos com placa nesse chunk ganham a placa certa (inclusive os que estão à venda). */
    @EventHandler
    public void aoCarregarChunk(ChunkLoadEvent evento) {
        Chunk chunk = evento.getChunk();
        if (!ativadas() || !chunk.getWorld().equals(plugin.getMundo())) return;
        Grade g = plugin.getGrade();
        int y = g.getAlturaChao() + 2;
        // a placa fica numa linha de z (minZ - 1) e numa coluna de x (minX + meio) de cada terreno
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                Block b = chunk.getBlock(dx, y, dz);
                TerrenoId id = terrenoDaPlaca(b);
                if (id != null && plugin.getTerrenoManager().dentroDaGrade(id)) escrever(b, id);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void aoClicar(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getHand() != EquipmentSlot.HAND || evento.getClickedBlock() == null) return;
        TerrenoId id = terrenoDaPlaca(evento.getClickedBlock());
        if (id == null) return;
        evento.setCancelled(true);
        evento.getPlayer().performCommand("terreno info " + id);
    }
}
