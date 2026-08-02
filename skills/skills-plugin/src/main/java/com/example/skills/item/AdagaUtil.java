package com.example.skills.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A Adaga é uma espada comum marcada de forma inviolável (via PersistentDataContainer,
 * não dá pra falsificar renomeando no bigorna). Enquanto empunhada, os golpes treinam
 * a skill Crítico em vez da skill de Espadas.
 */
public class AdagaUtil {

    private static final Set<Material> ESPADAS_VALIDAS = Set.of(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.GOLDEN_SWORD,
            Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    );

    private final NamespacedKey chaveAdaga;

    public AdagaUtil(JavaPlugin plugin) {
        this.chaveAdaga = new NamespacedKey(plugin, "adaga_critico");
    }

    public boolean ehEspadaValida(Material material) {
        return ESPADAS_VALIDAS.contains(material);
    }

    /** Transforma uma espada comum em Adaga, marcando ela de forma segura. */
    public ItemStack transformarEmAdaga(ItemStack espada) {
        ItemMeta meta = espada.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Adaga");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Uma arma especial de treino.");
        lore.add(ChatColor.GRAY + "Golpes com ela treinam a skill");
        lore.add(ChatColor.GRAY + "§d§lCrítico §7em vez de Espadas.");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(chaveAdaga, PersistentDataType.BYTE, (byte) 1);

        espada.setItemMeta(meta);
        return espada;
    }

    public boolean ehAdaga(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(chaveAdaga, PersistentDataType.BYTE);
    }
}
