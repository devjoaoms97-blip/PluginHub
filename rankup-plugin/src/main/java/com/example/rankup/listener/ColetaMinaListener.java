package com.example.rankup.listener;

import com.example.rankup.RankupPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.enchantments.Enchantment;
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
        if (semToqueSuave(evento)) {
            for (ItemStack drop : evento.getBlockState().getDrops(ferramentaSemToque(jogador), jogador)) {
                if (drop.getType().isAir() || plugin.getLojaIntegration().vender(jogador, drop)) continue;
                for (ItemStack resto : jogador.getInventory().addItem(drop).values()) {
                    evento.getBlock().getWorld().dropItemNaturally(evento.getBlock().getLocation().add(0.5, 0.5, 0.5), resto);
                    sobrou = true;
                }
            }
        }
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

    /**
     * Toque suave não vale na mina: o bloco cairia inteiro (o minério), a loja não compra e ele iria pro
     * inventário. Com ele na ferramenta, os drops do evento saem da lista (não aparecem) e quem chama
     * entrega os de uma cópia da ferramenta sem o encantamento ({@link #ferramentaSemToque}).
     */
    private static boolean semToqueSuave(BlockDropItemEvent evento) {
        if (!evento.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) return false;
        evento.getItems().clear();
        return true;
    }

    private static ItemStack ferramentaSemToque(Player jogador) {
        ItemStack semToque = jogador.getInventory().getItemInMainHand().clone();
        semToque.removeEnchantment(Enchantment.SILK_TOUCH);
        return semToque;
    }
}
