package com.example.rankup.listener;

import com.example.rankup.RankupPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.Map;

/**
 * Coleta do que se minera dentro de uma mina, pra todos os jogadores: o drop vai direto pro
 * inventário e a experiência direto pro jogador, sem entidades no chão (menos lag com muita gente
 * minerando). Só cai no chão o que não coube no inventário.
 * <p>
 * Venda automática (benefício de VIP, permissão vip.perk.autovenda): antes de ir pro inventário,
 * o drop é vendido na hora pela loja; o que a loja não compra vai pro inventário. O jogador
 * desliga e liga com /autovenda; a escolha fica gravada no próprio jogador
 * (PersistentDataContainer), sem arquivo.
 */
public class ColetaMinaListener implements Listener {

    public static final String PERMISSAO_AUTOVENDA = "vip.perk.autovenda";

    private final RankupPlugin plugin;
    private final NamespacedKey chaveDesligada;

    public ColetaMinaListener(RankupPlugin plugin) {
        this.plugin = plugin;
        this.chaveDesligada = new NamespacedKey(plugin, "autovenda-desligada");
    }

    public boolean autoVendaLigada(Player jogador) {
        return !jogador.getPersistentDataContainer().has(chaveDesligada, PersistentDataType.BYTE);
    }

    /** Alterna a venda automática e retorna o novo estado (true = ligada). */
    public boolean alternarAutoVenda(Player jogador) {
        if (autoVendaLigada(jogador)) {
            jogador.getPersistentDataContainer().set(chaveDesligada, PersistentDataType.BYTE, (byte) 1);
            return false;
        }
        jogador.getPersistentDataContainer().remove(chaveDesligada);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (evento.getExpToDrop() <= 0) return;
        if (plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation()) == null) return;
        // applyMending = true: conserta a ferramenta como o orbe faria
        evento.getPlayer().giveExp(evento.getExpToDrop(), true);
        evento.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoDropar(BlockDropItemEvent evento) {
        Player jogador = evento.getPlayer();
        if (plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation()) == null) return;
        boolean vender = jogador.hasPermission(PERMISSAO_AUTOVENDA) && autoVendaLigada(jogador);

        // tirar o Item da lista impede que ele apareça no mundo
        boolean sobrou = false;
        Iterator<Item> itens = evento.getItems().iterator();
        while (itens.hasNext()) {
            Item item = itens.next();
            if (vender && plugin.getLojaIntegration().vender(jogador, item.getItemStack())) {
                itens.remove();
                continue;
            }
            Map<Integer, ItemStack> naoCoube = jogador.getInventory().addItem(item.getItemStack());
            if (naoCoube.isEmpty()) {
                itens.remove();
            } else {
                item.setItemStack(naoCoube.values().iterator().next());
                sobrou = true;
            }
        }
        if (sobrou) {
            jogador.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§cInventário cheio! §7Venda com §f/sell" + (jogador.hasPermission(PERMISSAO_AUTOVENDA) ? " §7ou ligue o §f/autovenda" : "")));
        }
    }
}
