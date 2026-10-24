package com.example.barracas.manager;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import com.example.barracas.model.Leilao;
import com.example.barracas.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Holograma na entrada de cada barraca (o ponto que a staff marcou com /barraca holograma): o dono
 * e o tempo que falta, o leilão, ou "Aluga-se" com o preço. É um TextDisplay, com uma Interaction
 * invisível embaixo pra dar pra clicar e ver o /barraca info, que não fica salvo no mundo: nasce
 * quando o chunk carrega e some com ele.
 */
public class Hologramas implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private record Holo(TextDisplay texto, Interaction clique) {
        boolean valido() {
            return texto.isValid() && clique.isValid();
        }

        void remover() {
            texto.remove();
            clique.remove();
        }
    }

    private final BarracasPlugin plugin;
    private final NamespacedKey chaveBarraca;
    private final Map<String, Holo> hologramas = new HashMap<>();

    public Hologramas(BarracasPlugin plugin) {
        this.plugin = plugin;
        this.chaveBarraca = new NamespacedKey(plugin, "barraca");
    }

    /** Cria ou reescreve o holograma da barraca, se ela tiver entrada e o chunk estiver carregado. */
    public void atualizar(Barraca barraca) {
        BlocoPos pos = barraca.getEntrada();
        World mundo = Bukkit.getWorld(barraca.getMundo());
        Holo holo = hologramas.get(barraca.getId());
        if (pos == null || mundo == null) {
            if (holo != null) hologramas.remove(barraca.getId()).remover();
            return;
        }
        if (!mundo.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return;
        Block bloco = mundo.getBlockAt(pos.x(), pos.y(), pos.z());
        if (holo != null && (!holo.valido() || !bloco.getLocation().add(0.5, 0, 0.5).equals(holo.clique().getLocation()))) {
            holo.remover(); // sumiu, ou a staff mudou a entrada de lugar
            holo = null;
        }
        if (holo == null) {
            tirarPlacaAntiga(bloco);
            holo = criar(bloco, barraca.getId());
            hologramas.put(barraca.getId(), holo);
        }
        holo.texto().text(LEGADO.deserialize(texto(barraca)));
    }

    private Holo criar(Block bloco, String id) {
        Location base = bloco.getLocation().add(0.5, 0, 0.5);
        double altura = plugin.getConfig().getDouble("holograma.altura", 2.0);
        TextDisplay texto = bloco.getWorld().spawn(base.clone().add(0, altura, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setSeeThrough(false);
            t.setShadowed(true);
        });
        Interaction clique = bloco.getWorld().spawn(base, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.0f);
            i.setInteractionHeight((float) altura + 0.5f);
            i.getPersistentDataContainer().set(chaveBarraca, PersistentDataType.STRING, id);
        });
        return new Holo(texto, clique);
    }

    private String texto(Barraca barraca) {
        String titulo = (barraca.isEspecial() ? "§6§l[Barraca ★] " : "§9§l[Barraca] ") + "§f" + barraca.getId();
        if (barraca.getDono() != null) {
            return titulo + "\n§7Dono: §f" + barraca.getNomeDono()
                    + "\n§7Vence em §f" + Formato.tempo(barraca.getAlugadaAte() - System.currentTimeMillis());
        }
        if (barraca.emLeilao()) {
            Leilao leilao = barraca.getLeilao();
            return titulo + "\n§6§lEM LEILÃO\n§f" + Formato.valor(leilao.temLance() ? leilao.getLance() : plugin.getPrecoAluguel(barraca))
                    + "\n§7/barraca lance " + barraca.getId();
        }
        return titulo + "\n§a§lALUGA-SE\n§f" + Formato.valor(plugin.getPrecoAluguel(barraca)) + "\n§7/barraca alugar " + barraca.getId();
    }

    /** Versões antigas usavam uma placa no lugar da entrada: sai pra dar lugar ao holograma. */
    private void tirarPlacaAntiga(Block bloco) {
        if (Tag.ALL_SIGNS.isTagged(bloco.getType())) bloco.setType(Material.AIR, false);
    }

    /** Ao ligar: as barracas cujo chunk já está carregado. */
    public void carregarTodos() {
        plugin.getBarracaManager().getBarracas().values().forEach(this::atualizar);
    }

    public void removerTodos() {
        hologramas.values().forEach(Holo::remover);
        hologramas.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        Chunk chunk = evento.getChunk();
        for (Barraca b : plugin.getBarracaManager().getBarracas().values()) {
            BlocoPos pos = b.getEntrada();
            if (pos != null && pos.x() >> 4 == chunk.getX() && pos.z() >> 4 == chunk.getZ()
                    && b.getMundo().equals(chunk.getWorld().getName())) {
                atualizar(b);
            }
        }
    }

    @EventHandler
    public void aoDescarregar(ChunkUnloadEvent evento) {
        Chunk chunk = evento.getChunk();
        hologramas.values().removeIf(holo -> {
            Location l = holo.clique().getLocation();
            if (!chunk.getWorld().equals(l.getWorld()) || l.getBlockX() >> 4 != chunk.getX() || l.getBlockZ() >> 4 != chunk.getZ()) return false;
            holo.remover();
            return true;
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void aoClicar(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        Entity alvo = evento.getRightClicked();
        String id = alvo instanceof Interaction ? alvo.getPersistentDataContainer().get(chaveBarraca, PersistentDataType.STRING) : null;
        if (id == null) return;
        evento.setCancelled(true);
        evento.getPlayer().performCommand("barraca info " + id);
    }
}
