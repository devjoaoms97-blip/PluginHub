package com.example.rankup.listener;

import com.example.rankup.RankupPlugin;
import com.example.rankup.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fragmento Arcano: cai raramente de qualquer bloco quebrado numa mina ({@code fragmentos.chance-por-bloco})
 * e vai direto pro inventário. É a moeda do Cambista (OficinasPlugin), que troca fragmentos por
 * Eficiência e Fortuna acima do máximo. Por isso é raro de propósito: com ~4.000 blocos por hora,
 * a chance padrão dá cerca de 1 por hora de mina.
 * <p>
 * O item é marcado ({@code rankupplugin:fragmento}): renomear outro item não falsifica, e a loja não
 * compra (item com nome não entra na venda automática). O Cambista reconhece pela mesma marca.
 */
public class FragmentosListener implements Listener {

    private final RankupPlugin plugin;
    private final NamespacedKey chave;

    public FragmentosListener(RankupPlugin plugin) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "fragmento");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (!plugin.getConfig().getBoolean("fragmentos.ativado", true)) return;
        Player jogador = evento.getPlayer();
        if (jogador.getGameMode() != GameMode.SURVIVAL) return;
        if (plugin.getMinaManager().getMinaEm(evento.getBlock().getLocation()) == null) return;
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("fragmentos.chance-por-bloco", 0.00025)) return;

        Map<Integer, ItemStack> naoCoube = jogador.getInventory().addItem(fragmento(1));
        naoCoube.values().forEach(i -> jogador.getWorld().dropItem(jogador.getLocation(), i));
        jogador.sendMessage(Formato.cores(plugin.getConfig().getString("fragmentos.mensagem",
                "&d&l✦ &dVocê achou um &fFragmento Arcano&d! &7Troque com o Cambista, no fundo da avenida.")));
        jogador.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§d✦ Fragmento Arcano!"));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
        jogador.getWorld().spawnParticle(Particle.WITCH, evento.getBlock().getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
    }

    /** O item do fragmento (papel com o modelo do resource pack, como os produtos das máquinas). */
    public ItemStack fragmento(int quantidade) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, quantidade));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("servidor", "fragmento_arcano"));
        meta.setDisplayName(Formato.cores(plugin.getConfig().getString("fragmentos.nome", "&d✦ Fragmento Arcano")));
        meta.setLore(List.of("§7Cai raramente nas minas.", "§7O §dCambista§7, no fundo da avenida, troca",
                "§7por encantamentos acima do máximo."));
        meta.getPersistentDataContainer().set(chave, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
