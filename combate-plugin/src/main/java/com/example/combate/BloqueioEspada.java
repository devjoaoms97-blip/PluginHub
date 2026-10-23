package com.example.combate;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bloqueio com a espada, como na 1.8: segurar o botão direito com a espada na mão principal
 * levanta a espada e reduz o dano. Usa o mesmo componente do escudo ({@code blocks_attacks}),
 * então a animação, o "está bloqueando" e a skill de Bloqueio funcionam como com o escudo.
 * <p>
 * Só vale <b>sem escudo na segunda mão</b>: com escudo, quem bloqueia é o escudo. Por isso o
 * componente é posto e tirado da espada da mão principal conforme o que está na outra mão, e
 * marcado no item, pra nunca mexer numa espada que já tenha o componente de outro jeito.
 */
public class BloqueioEspada implements Listener {

    private final CombatePlugin plugin;
    private final NamespacedKey marca;

    public BloqueioEspada(CombatePlugin plugin) {
        this.plugin = plugin;
        this.marca = new NamespacedKey(plugin, "bloqueio-espada");
    }

    private boolean ehEspada(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    private boolean marcada(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(marca, PersistentDataType.BYTE);
    }

    /** Põe ou tira o bloqueio da espada da mão principal, conforme a outra mão. Roda a cada 2 ticks. */
    public void atualizar(Player jogador) {
        PlayerInventory inv = jogador.getInventory();
        ItemStack principal = inv.getItemInMainHand();
        if (!ehEspada(principal)) return;
        boolean deveBloquear = inv.getItemInOffHand().getType() != Material.SHIELD;
        if (deveBloquear && !principal.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
            ItemStack nova = principal.clone();
            nova.setData(DataComponentTypes.BLOCKS_ATTACKS, componente());
            nova.editPersistentDataContainer(pdc -> pdc.set(marca, PersistentDataType.BYTE, (byte) 1));
            inv.setItemInMainHand(nova);
        } else if (!deveBloquear && marcada(principal)) {
            inv.setItemInMainHand(limpar(principal));
        }
    }

    /** O componente do bloqueio: % do dano tirado, de qualquer direção, sem gastar a espada. */
    private BlocksAttacks componente() {
        float reducao = (float) Math.max(0, Math.min(1, plugin.getConfig().getDouble("bloqueio-espada.reducao-percentual", 50) / 100.0));
        float angulo = (float) plugin.getConfig().getDouble("bloqueio-espada.angulo", 180);
        return BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0)
                .disableCooldownScale(0) // machado não desarma a espada
                .addDamageReduction(DamageReduction.damageReduction().base(0).factor(reducao).horizontalBlockingAngle(angulo).build())
                .itemDamage(ItemDamageFunction.itemDamageFunction().threshold(Float.MAX_VALUE).base(0).factor(0).build())
                .build();
    }

    /** Espada sem o bloqueio que o plugin pôs (qualquer outra coisa no item fica). */
    private ItemStack limpar(ItemStack item) {
        ItemStack limpa = item.clone();
        // reset (e não unset): volta ao padrão da espada, sem deixar marca de "componente removido"
        limpa.resetData(DataComponentTypes.BLOCKS_ATTACKS);
        limpa.editPersistentDataContainer(pdc -> pdc.remove(marca));
        return limpa;
    }

    // a espada que sai da mão principal deixa de bloquear
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoTrocarDeSlot(PlayerItemHeldEvent evento) {
        PlayerInventory inv = evento.getPlayer().getInventory();
        ItemStack anterior = inv.getItem(evento.getPreviousSlot());
        if (marcada(anterior)) inv.setItem(evento.getPreviousSlot(), limpar(anterior));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTrocarDeMao(PlayerSwapHandItemsEvent evento) {
        if (marcada(evento.getOffHandItem())) evento.setOffHandItem(limpar(evento.getOffHandItem()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoSoltar(PlayerDropItemEvent evento) {
        ItemStack item = evento.getItemDrop().getItemStack();
        if (marcada(item)) evento.getItemDrop().setItemStack(limpar(item));
    }

    /** Desligando: tira o bloqueio das espadas nas mãos de quem está online. */
    public void limparTodos() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack item = inv.getItem(slot);
                if (marcada(item)) inv.setItem(slot, limpar(item));
            }
        }
    }
}
