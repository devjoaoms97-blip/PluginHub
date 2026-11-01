package com.example.spawners;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Spawner natural (masmorra, mina abandonada, fortaleza, fortaleza do End...) quebrado com picareta
 * com Toque Suave vira o spawner da loja do mesmo mob: empilhável e funcionando no terreno. Sem Toque
 * Suave, ou de um mob que a loja não tem, fica como no jogo (não dá nada).
 * <p>
 * Vale em qualquer mundo, mas só onde o jogador pode quebrar: roda depois das proteções (HIGHEST e
 * ignorando o que já foi cancelado), então o spawn, as arenas e os terrenos dos outros continuam
 * protegidos. Os spawners da loja (já colocados) e os das estátuas da /warp mobs ficam de fora.
 */
public class ToqueSuave implements Listener {

    private final SpawnersPlugin plugin;

    ToqueSuave(SpawnersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Block bloco = evento.getBlock();
        if (!plugin.getConfig().getBoolean("toque-suave.ativado", true)) return;
        if (!(bloco.getState() instanceof CreatureSpawner natural)) return;
        Player jogador = evento.getPlayer();
        if (jogador.getGameMode() == GameMode.CREATIVE) return;
        // o spawner da loja já colocado o SpawnerManager tratou antes (e desligou os drops)
        if (!evento.isDropItems() || plugin.getSpawners().ehColocado(bloco) || plugin.getWarpMobs().ehEstatua(bloco)) return;
        ItemStack ferramenta = jogador.getInventory().getItemInMainHand();
        if (!ferramenta.getType().name().endsWith("_PICKAXE") || !ferramenta.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        EntityType mob = natural.getSpawnedType();
        SpawnersPlugin.Tipo tipo = mob == null ? null : plugin.tipoDaEntidade(mob);
        if (tipo == null) {
            jogador.sendMessage("§7Esse spawner é de um mob que a loja não tem: ele não sai inteiro.");
            return;
        }
        evento.setExpToDrop(0);
        bloco.getWorld().dropItemNaturally(bloco.getLocation().add(0.5, 0.5, 0.5), plugin.item(tipo, 1));
        jogador.sendMessage("§aVocê coletou um §fSpawner de " + tipo.nome() + "§a. Coloque no seu terreno (§f/terreno home§a).");
    }
}
