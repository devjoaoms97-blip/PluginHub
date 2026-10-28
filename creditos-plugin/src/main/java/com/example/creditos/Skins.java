package com.example.creditos;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Skins de armadura, ferramenta e arma: só o visual, do resource pack do servidor (PacotePlugin, gerado
 * pelo tools/pacote). A skin vai no item que o jogador já tem: o item continua o mesmo (material,
 * encantamentos, durabilidade, dano, proteção), só troca o modelo ({@code item_model}) e, na armadura,
 * a textura vestida ({@code equippable} com o {@code asset_id} da coleção). Tirar a skin volta o
 * visual original. A marca {@code creditosplugin:skin} diz qual coleção está no item.
 */
public class Skins {

    /** A peça de cada tipo de item (pelo fim do nome do material), ou null se não recebe skin. */
    static String peca(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return "capacete";
        if (n.endsWith("_CHESTPLATE")) return "peitoral";
        if (n.endsWith("_LEGGINGS")) return "calca";
        if (n.endsWith("_BOOTS")) return "botas";
        if (n.endsWith("_SWORD")) return "espada";
        if (n.endsWith("_PICKAXE")) return "picareta";
        if (n.endsWith("_AXE")) return "machado";
        if (n.endsWith("_SHOVEL")) return "pa";
        if (n.endsWith("_HOE")) return "enxada";
        return null;
    }

    static boolean armadura(String peca) {
        return switch (peca) {
            case "capacete", "peitoral", "calca", "botas" -> true;
            default -> false;
        };
    }

    private final CreditosPlugin plugin;
    private final NamespacedKey marca;

    public Skins(CreditosPlugin plugin) {
        this.plugin = plugin;
        this.marca = new NamespacedKey(plugin, "skin");
    }

    /** A seção da coleção no config ({@code skins.<id>}), ou null. */
    ConfigurationSection secao(String colecao) {
        return colecao == null ? null : plugin.getConfig().getConfigurationSection("skins." + colecao);
    }

    /** O modelo do item de uma peça da coleção (no resource pack). */
    static NamespacedKey modelo(String colecao, String peca) {
        return new NamespacedKey("servidor", "skin_" + colecao + "_" + peca);
    }

    /** A coleção cuja skin está no item, ou null. */
    String colecaoDe(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(marca, PersistentDataType.STRING);
    }

    /** Por que o item não pode receber skin (mensagem pro jogador), ou null se pode. */
    String motivoRecusa(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Segure na mão o item que vai receber a skin.";
        if (peca(item.getType()) == null) return "Skin só vai em armadura (capacete, peitoral, calça e botas), espada, machado, picareta, pá e enxada.";
        // item com modelo próprio de outro plugin (um item especial do servidor): não mexe
        if (item.isDataOverridden(DataComponentTypes.ITEM_MODEL) && colecaoDe(item) == null) return "Esse item já tem um visual especial.";
        return null;
    }

    /** Põe a skin da coleção no item (a peça sai do material). Confira antes com {@link #motivoRecusa}. */
    void aplicar(ItemStack item, String colecao) {
        String peca = peca(item.getType());
        if (peca == null) return;
        item.editPersistentDataContainer(pdc -> pdc.set(marca, PersistentDataType.STRING, colecao));
        item.setData(DataComponentTypes.ITEM_MODEL, modelo(colecao, peca));
        if (armadura(peca)) {
            Equippable atual = item.getData(DataComponentTypes.EQUIPPABLE);
            if (atual != null) {
                item.setData(DataComponentTypes.EQUIPPABLE, atual.toBuilder().assetId(new NamespacedKey("servidor", "skin_" + colecao)).build());
            }
        }
    }

    /**
     * Chapéu (modelo de terceiros, {@code servidor:chapeu_<id>}) num capacete: o modelo do item vira o
     * chapéu e o equippable fica sem {@code asset_id}, pro jogo desenhar o item na cabeça (como uma
     * abóbora) em vez da armadura. Proteção, encantos e durabilidade continuam os do capacete.
     */
    void aplicarChapeu(ItemStack item, String chapeu) {
        item.editPersistentDataContainer(pdc -> pdc.set(marca, PersistentDataType.STRING, "chapeu:" + chapeu));
        item.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey("servidor", "chapeu_" + chapeu));
        Equippable atual = item.getData(DataComponentTypes.EQUIPPABLE);
        Equippable.Builder novo = Equippable.equippable(EquipmentSlot.HEAD);
        if (atual != null) novo.equipSound(atual.equipSound());
        item.setData(DataComponentTypes.EQUIPPABLE, novo.build());
    }

    /** Volta o visual original do item. */
    void tirar(ItemStack item) {
        if (colecaoDe(item) == null) return;
        item.editPersistentDataContainer(pdc -> pdc.remove(marca));
        item.resetData(DataComponentTypes.ITEM_MODEL);
        item.resetData(DataComponentTypes.EQUIPPABLE);
    }

    static String nomePeca(String peca) {
        return switch (peca.toLowerCase(Locale.ROOT)) {
            case "calca" -> "calça";
            case "pa" -> "pá";
            default -> peca;
        };
    }
}
