package com.example.rankup.listener;

import com.example.rankup.RankupPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Map;

/**
 * Coleta do que se minera dentro de uma mina, pra todos os jogadores: o drop é vendido na hora pela
 * loja (preço dinâmico, bônus do rank e do VIP, imposto), e o dinheiro aparece na barra de ação.
 * O minério da mina nunca vira item: não dá pra guardar diamante da mina, craftar blocos nem revender
 * na loja. O que a loja não compra vai direto pro inventário, e a experiência vai direto pro jogador
 * (sem entidades no chão: menos lag com muita gente minerando). Só cai no chão o que não coube.
 */
public class ColetaMinaListener implements Listener {

    private final RankupPlugin plugin;

    public ColetaMinaListener(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    /** Missão de minerar numa mina: todo bloco quebrado dentro dela conta (com ou sem XP). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void contarMissao(BlockBreakEvent evento) {
        var mina = plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation());
        if (mina != null) plugin.getMissoes().progresso(evento.getPlayer(), "MINERAR_MINA", mina.getId(), 1);
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
        // tirar o Item da lista impede que ele apareça no mundo
        boolean sobrou = false;
        Iterator<Item> itens = evento.getItems().iterator();
        while (itens.hasNext()) {
            Item item = itens.next();
            if (plugin.getLojaIntegration().vender(jogador, item.getItemStack())) {
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
                    "§cInventário cheio! §7O que a loja não compra está caindo no chão."));
        }
    }
}
