package com.example.tesouros;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * O item do mapa do tesouro: um papel com a cara de mapa preenchido, marcado com id, raridade e
 * coordenadas (renomear outro item não falsifica). Pode ser vendido e trocado: quem estiver com ele
 * cava. Cada id só abre uma câmara: um mapa duplicado por bug não vale de novo ({@code usados.yml}).
 */
public class Mapas {

    public record Mapa(String id, String raridade, int x, int z) {}

    public static final List<String> RARIDADES = List.of("comum", "raro", "arcano", "lendario");

    private final TesourosPlugin plugin;
    private final NamespacedKey chaveId;
    private final NamespacedKey chaveRaridade;
    private final NamespacedKey chaveX;
    private final NamespacedKey chaveZ;
    private final File arquivoUsados;
    private final Set<String> usados = new HashSet<>();

    public Mapas(TesourosPlugin plugin) {
        this.plugin = plugin;
        this.chaveId = new NamespacedKey(plugin, "mapa");
        this.chaveRaridade = new NamespacedKey(plugin, "raridade");
        this.chaveX = new NamespacedKey(plugin, "x");
        this.chaveZ = new NamespacedKey(plugin, "z");
        this.arquivoUsados = new File(plugin.getDataFolder(), "usados.yml");
        usados.addAll(YamlConfiguration.loadConfiguration(arquivoUsados).getStringList("usados"));
    }

    /** Um mapa novo, com coordenadas sorteadas. */
    public ItemStack criar(String raridade) {
        int min = Math.max(0, plugin.getConfig().getInt("coordenadas.raio-minimo", 150));
        int max = Math.max(min + 1, plugin.getConfig().getInt("coordenadas.raio-maximo", 1500));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angulo = r.nextDouble(Math.PI * 2);
        double raio = r.nextDouble(min, max);
        return criar(raridade, (int) Math.round(Math.cos(angulo) * raio), (int) Math.round(Math.sin(angulo) * raio));
    }

    public ItemStack criar(String raridade, int x, int z) {
        String nomeRaridade = plugin.nomeRaridade(raridade);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.minecraft("filled_map"));
        meta.setDisplayName("§6✦ Mapa do Tesouro §8(" + nomeRaridade + "§8)");
        List<String> lore = new ArrayList<>();
        lore.add("§7Raridade: " + nomeRaridade);
        lore.add("§7Onde cavar: §fX " + x + "§7, §fZ " + z);
        lore.add("§7No mundo dos tesouros: §e/tesouro ir");
        lore.add("");
        lore.add("§7Cave o chão no X e enfrente os guardiões.");
        lore.add("§cQuem morre na câmara perde tudo.");
        meta.setLore(lore);
        if ("lendario".equals(raridade) || "arcano".equals(raridade)) meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(chaveId, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(chaveRaridade, PersistentDataType.STRING, raridade);
        pdc.set(chaveX, PersistentDataType.INTEGER, x);
        pdc.set(chaveZ, PersistentDataType.INTEGER, z);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    /** O mapa daquele item, ou null se não for um mapa do tesouro. */
    public Mapa ler(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(chaveId, PersistentDataType.STRING);
        String raridade = pdc.get(chaveRaridade, PersistentDataType.STRING);
        Integer x = pdc.get(chaveX, PersistentDataType.INTEGER);
        Integer z = pdc.get(chaveZ, PersistentDataType.INTEGER);
        if (id == null || raridade == null || x == null || z == null) return null;
        return new Mapa(id, raridade, x, z);
    }

    /** Sorteia a raridade pelos pesos de uma seção ({comum: 45, raro: 30, ...}). */
    public String sortearRaridade(ConfigurationSection pesos) {
        if (pesos == null) return "comum";
        double total = 0;
        for (String r : RARIDADES) total += Math.max(0, pesos.getDouble(r, 0));
        if (total <= 0) return "comum";
        double sorteio = ThreadLocalRandom.current().nextDouble(total);
        for (String r : RARIDADES) {
            sorteio -= Math.max(0, pesos.getDouble(r, 0));
            if (sorteio < 0) return r;
        }
        return "comum";
    }

    public boolean usado(String id) {
        return usados.contains(id);
    }

    public void marcarUsado(String id) {
        if (!usados.add(id)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("usados", new ArrayList<>(usados));
        try {
            yaml.save(arquivoUsados);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não deu pra salvar o usados.yml", e);
        }
    }
}
