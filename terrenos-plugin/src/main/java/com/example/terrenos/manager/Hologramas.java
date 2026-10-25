package com.example.terrenos.manager;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.Leilao;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.model.TerrenoId;
import com.example.terrenos.mundo.Grade;
import com.example.terrenos.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Holograma na entrada de cada terreno: na rua, em cima da borda, no meio do lado norte, de frente
 * pra quem chega pelo /terreno home ou /terreno ir. Mostra "À venda" com o preço, o dono, ou o
 * leilão com o lance atual e quantos itens estão guardados lá dentro. É um TextDisplay (com uma Interaction invisível embaixo, pra dar pra
 * clicar e ver o /terreno info) que não fica salvo no mundo: nasce quando o chunk carrega e some
 * com ele, então os terrenos à venda ganham holograma sem precisar guardar nada.
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

    private final TerrenosPlugin plugin;
    private final NamespacedKey chaveTerreno;
    private final Map<TerrenoId, Holo> hologramas = new HashMap<>();

    /** Quantos itens um terreno em leilão guarda, e quando foi contado. */
    private record Guardado(long itens, int recipientes, long contadoEm) {
    }

    /** A contagem carrega os chunks do terreno: fica guardada e é refeita de tempos em tempos. */
    private static final long RECONTAR_MS = 5 * 60_000L;
    private final Map<TerrenoId, Guardado> guardados = new HashMap<>();

    public Hologramas(TerrenosPlugin plugin) {
        this.plugin = plugin;
        this.chaveTerreno = new NamespacedKey(plugin, "terreno");
    }

    private boolean ativados() {
        return plugin.getConfig().getBoolean("holograma.ativado", true);
    }

    /** Bloco da entrada do terreno, na borda: no meio do lado norte. */
    private Block entrada(World mundo, TerrenoId id) {
        Grade g = plugin.getGrade();
        return mundo.getBlockAt(g.minX(id) + g.getTamanho() / 2, g.getAlturaChao() + 1, g.minZ(id) - 1);
    }

    /** O terreno cuja entrada é esse bloco, ou null. */
    private TerrenoId terrenoDaEntrada(Block bloco) {
        TerrenoId id = plugin.getGrade().terrenoEm(bloco.getX(), bloco.getZ() + 1);
        return id != null && entrada(bloco.getWorld(), id).equals(bloco) ? id : null;
    }

    /** Cria ou reescreve o holograma do terreno, se o chunk dele estiver carregado. */
    public void atualizar(TerrenoId id) {
        World mundo = plugin.getMundo();
        if (mundo == null || !ativados() || !plugin.getTerrenoManager().dentroDaGrade(id)) return;
        Block b = entrada(mundo, id);
        if (!mundo.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return;
        Holo holo = hologramas.get(id);
        if (holo == null || !holo.valido()) {
            if (holo != null) holo.remover();
            holo = criar(b, id);
            hologramas.put(id, holo);
        }
        holo.texto().text(LEGADO.deserialize(texto(id)));
    }

    private Holo criar(Block b, TerrenoId id) {
        // em cima da laje da borda (meio bloco), virado pro norte, de onde o jogador chega
        Location base = b.getLocation().add(0.5, 0.5, 0.5);
        base.setYaw(180);
        Location alto = base.clone().add(0, plugin.getConfig().getDouble("holograma.altura", 2.0), 0);
        TextDisplay texto = b.getWorld().spawn(alto, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setSeeThrough(false);
            t.setShadowed(true);
        });
        Interaction clique = b.getWorld().spawn(base, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.0f);
            i.setInteractionHeight((float) (alto.getY() - base.getY() + 0.5));
            i.getPersistentDataContainer().set(chaveTerreno, PersistentDataType.STRING, id.toString());
        });
        return new Holo(texto, clique);
    }

    private String texto(TerrenoId id) {
        Terreno t = plugin.getTerrenoManager().get(id);
        String titulo = "§8[§2Terreno §f" + id + "§8]";
        if (t == null) {
            return titulo + "\n§a§lÀ VENDA\n§f" + Formato.valor(plugin.getTerrenoManager().precoCompra()) + "\n§7/terreno comprar";
        }
        if (t.emLeilao()) {
            Leilao l = t.getLeilao();
            return titulo + "\n§6§lEM LEILÃO\n" + (l.temLance() ? "§f" + Formato.valor(l.getLance()) : "§7sem lances") + "\n§7/terreno lance";
        }
        return titulo + "\n§7Dono: §f" + t.getNomeDono();
    }

    private String linhaGuardado(TerrenoId id) {
        Guardado g = guardados.get(id);
        if (g == null || System.currentTimeMillis() - g.contadoEm() > RECONTAR_MS) {
            // conta no próximo tick: aqui pode ser dentro do ChunkLoadEvent, e a contagem carrega chunks
            plugin.getServer().getScheduler().runTask(plugin, () -> recontar(id));
            if (g == null) return "§7Contando os itens guardados...";
        }
        if (g.recipientes() == 0) return "§7Nenhum item guardado";
        return "§7Guardado: §f" + String.format(Locale.forLanguageTag("pt-BR"), "%,d", g.itens()) + " §7ite" + (g.itens() == 1 ? "m" : "ns")
                + " em §f" + g.recipientes() + " §7recipiente" + (g.recipientes() == 1 ? "" : "s");
    }

    private void recontar(TerrenoId id) {
        Guardado atual = guardados.get(id);
        if (atual != null && System.currentTimeMillis() - atual.contadoEm() <= RECONTAR_MS) return; // outro pedido já contou
        Terreno t = plugin.getTerrenoManager().get(id);
        World mundo = plugin.getMundo();
        if (t == null || !t.emLeilao() || mundo == null) return;
        Grade g = plugin.getGrade();
        int minX = g.minX(id), minZ = g.minZ(id), maxX = minX + g.getTamanho() - 1, maxZ = minZ + g.getTamanho() - 1;
        long itens = 0;
        int recipientes = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                Chunk chunk = mundo.getChunkAt(cx, cz);
                for (BlockState estado : chunk.getTileEntities()) {
                    if (!(estado instanceof Container recipiente) || !dentro(estado.getX(), estado.getZ(), minX, minZ, maxX, maxZ)) continue;
                    // o inventário do próprio bloco: cada metade do baú duplo conta a sua parte
                    itens += contar(recipiente.getSnapshotInventory());
                    recipientes++;
                }
                for (Entity entidade : chunk.getEntities()) {
                    // carrinho e bote com baú, burro, lhama...
                    if (entidade instanceof Player || !(entidade instanceof InventoryHolder dono)) continue;
                    Location l = entidade.getLocation();
                    if (!dentro(l.getBlockX(), l.getBlockZ(), minX, minZ, maxX, maxZ)) continue;
                    itens += contar(dono.getInventory());
                    recipientes++;
                }
            }
        }
        guardados.put(id, new Guardado(itens, recipientes, System.currentTimeMillis()));
        atualizar(id);
    }

    private static boolean dentro(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Itens do inventário, contando também o que está dentro de shulkers e bundles. */
    private static long contar(Inventory inventario) {
        long total = 0;
        for (ItemStack item : inventario.getContents()) total += contar(item);
        return total;
    }

    private static long contar(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        long total = item.getAmount();
        if (item.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState() && meta.getBlockState() instanceof ShulkerBox caixa) {
            total += item.getAmount() * contar(caixa.getInventory());
        } else if (item.getItemMeta() instanceof BundleMeta bundle) {
            for (ItemStack dentro : bundle.getItems()) total += item.getAmount() * contar(dentro);
        }
        return total;
    }

    /** Ao ligar (e no /terreno reload): os chunks que já estão carregados. */
    public void carregarTodos() {
        World mundo = plugin.getMundo();
        if (mundo == null) return;
        for (Chunk chunk : mundo.getLoadedChunks()) aoCarregar(new ChunkLoadEvent(chunk, false));
    }

    public void removerTodos() {
        hologramas.values().forEach(Holo::remover);
        hologramas.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        Chunk chunk = evento.getChunk();
        if (!chunk.getWorld().equals(plugin.getMundo())) return;
        Grade g = plugin.getGrade();
        int y = g.getAlturaChao() + 1;
        // a entrada fica numa linha de z (minZ - 1) e numa coluna de x (minX + meio) de cada terreno
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                Block b = chunk.getBlock(dx, y, dz);
                TerrenoId id = terrenoDaEntrada(b);
                if (id == null || !plugin.getTerrenoManager().dentroDaGrade(id)) continue;
                tirarPlacaAntiga(b);
                if (ativados()) atualizar(id);
            }
        }
    }

    /** Versões antigas punham uma placa em cima da borda (e a laje embaixo dela virava dupla). */
    private void tirarPlacaAntiga(Block borda) {
        Block acima = borda.getRelative(0, 1, 0);
        if (acima.getType() != Material.OAK_SIGN) return;
        acima.setType(Material.AIR, false);
        Grade g = plugin.getGrade();
        borda.setType(g.blocoEm(borda.getX(), borda.getY(), borda.getZ(), borda.getWorld().getMinHeight()), false);
    }

    @EventHandler
    public void aoDescarregar(ChunkUnloadEvent evento) {
        if (!evento.getWorld().equals(plugin.getMundo())) return;
        Chunk chunk = evento.getChunk();
        hologramas.entrySet().removeIf(e -> {
            Location l = e.getValue().clique().getLocation();
            if (l.getBlockX() >> 4 != chunk.getX() || l.getBlockZ() >> 4 != chunk.getZ()) return false;
            e.getValue().remover();
            return true;
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void aoClicar(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        Entity alvo = evento.getRightClicked();
        String id = alvo instanceof Interaction ? alvo.getPersistentDataContainer().get(chaveTerreno, PersistentDataType.STRING) : null;
        if (id == null) return;
        evento.setCancelled(true);
        evento.getPlayer().performCommand("terreno info " + id);
    }
}
