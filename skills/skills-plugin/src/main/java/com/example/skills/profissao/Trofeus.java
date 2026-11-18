package com.example.skills.profissao;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.example.skills.SkillsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Os troféus das profissões (2026-11-18): as <b>cabeças</b> de mob do Caçador e as <b>relíquias</b> do Escavador.
 * São itens com a marca {@code skillsplugin:trofeu} ("cabeca:<mob>" ou "reliquia:<id>") e um preço no config;
 * vendem com {@code /trofeus vender} (como os peixes raros com o /peixes vender). A loja não compra item com
 * marca. As cabeças também enfeitam: colocada, vira só a cabeça (sem o valor).
 *
 * <p>Mob sem cabeça no jogo (aranha, enderman...) usa uma cabeça de jogador com a textura do mob
 * ({@code cacador.cabecas.<mob>.textura}, o hash do textures.minecraft.net).
 */
public class Trofeus {

    private final SkillsPlugin plugin;
    private final NamespacedKey chave;

    public Trofeus(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "trofeu");
    }

    private static String cores(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public ConfigurationSection cabecas() {
        return plugin.getConfig().getConfigurationSection("cacador.cabecas");
    }

    public ConfigurationSection reliquias() {
        return plugin.getConfig().getConfigurationSection("escavador.reliquias");
    }

    /** A cabeça do mob (o id é o nome do EntityType em minúsculas), ou null se ele não tem cabeça no config. */
    public ItemStack cabeca(String mob) {
        ConfigurationSection s = cabecas() == null ? null : cabecas().getConfigurationSection(mob);
        if (s == null) return null;
        Material vanilla = Material.matchMaterial(s.getString("item", ""));
        ItemStack item = new ItemStack(vanilla != null ? vanilla : Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        String textura = s.getString("textura", "");
        if (vanilla == null && meta instanceof SkullMeta caveira && !textura.isBlank()) {
            PlayerProfile perfil = Bukkit.createProfile(UUID.nameUUIDFromBytes(("cabeca:" + mob).getBytes(StandardCharsets.UTF_8)), "Cabeca");
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textura + "\"}}}";
            perfil.setProperty(new ProfileProperty("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
            caveira.setPlayerProfile(perfil);
        }
        double preco = s.getDouble("preco");
        meta.setDisplayName(cores(s.getString("nome", "&6Cabeça")));
        meta.setLore(List.of(cores("&7Troféu do &fCaçador&7."), cores("&7Vale &a" + PescaLoot.dinheiro(preco) + " &7(&f/trofeus vender&7)."),
                cores("&8Colocada, vira só enfeite.")));
        meta.getPersistentDataContainer().set(chave, PersistentDataType.STRING, "cabeca:" + mob);
        item.setItemMeta(meta);
        return item;
    }

    /** A relíquia do Escavador com esse id, ou null. */
    public ItemStack reliquia(String id) {
        ConfigurationSection s = reliquias() == null ? null : reliquias().getConfigurationSection(id);
        if (s == null) return null;
        Material m = Material.matchMaterial(s.getString("material", "GOLD_NUGGET"));
        ItemStack item = new ItemStack(m != null && m.isItem() ? m : Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(cores(s.getString("nome", id)));
        meta.setLore(List.of(cores("&7Relíquia do &fEscavador &8(" + s.getString("raridade", "comum") + ")&7."),
                cores("&7Vale &a" + PescaLoot.dinheiro(s.getDouble("preco")) + " &7(&f/trofeus vender&7).")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(chave, PersistentDataType.STRING, "reliquia:" + id);
        item.setItemMeta(meta);
        return item;
    }

    /** "cabeca:<mob>" ou "reliquia:<id>" do item, ou null. */
    public String marca(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(chave, PersistentDataType.STRING);
    }

    /** O preço de UM desse troféu, ou -1 (não é troféu, ou saiu do config). */
    public double preco(String marca) {
        if (marca == null) return -1;
        String[] p = marca.split(":", 2);
        if (p.length < 2) return -1;
        ConfigurationSection s = "cabeca".equals(p[0]) ? cabecas() : "reliquia".equals(p[0]) ? reliquias() : null;
        return s != null && s.contains(p[1] + ".preco") ? s.getDouble(p[1] + ".preco") : -1;
    }

    /** Vende os troféus do inventário. Devolve o total (0 se não tinha nenhum). */
    public double vender(Player jogador) {
        ItemStack[] conteudo = jogador.getInventory().getStorageContents();
        double total = 0;
        for (int i = 0; i < conteudo.length; i++) {
            double preco = preco(marca(conteudo[i]));
            if (preco <= 0) continue;
            total += preco * conteudo[i].getAmount();
            conteudo[i] = null;
        }
        if (total <= 0) return 0;
        jogador.getInventory().setStorageContents(conteudo);
        if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(jogador, total);
        plugin.getIntegracoes().extrato(jogador, total, "Venda de troféus (cabeças e relíquias)", "Troféus");
        return total;
    }

    /** Pra /trofeus dar: "cabeca:<mob>" ou "reliquia:<id>". */
    public ItemStack criar(String nome, int qtd) {
        String n = nome.toLowerCase(Locale.ROOT);
        ItemStack item = n.startsWith("cabeca:") ? cabeca(n.substring(7)) : n.startsWith("reliquia:") ? reliquia(n.substring(9)) : null;
        if (item != null) item.setAmount(Math.max(1, Math.min(64, qtd)));
        return item;
    }

    public List<String> ids() {
        List<String> l = new ArrayList<>();
        if (cabecas() != null) cabecas().getKeys(false).forEach(k -> l.add("cabeca:" + k));
        if (reliquias() != null) reliquias().getKeys(false).forEach(k -> l.add("reliquia:" + k));
        return l;
    }
}
