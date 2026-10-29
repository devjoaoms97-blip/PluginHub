package com.example.tesouros;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * O loot de cada raridade, no {@code loot.yml}: coins entre um mínimo e um máximo, e tantas rolagens
 * de itens sorteados pelo peso. A staff acrescenta um item qualquer (com nome, encantamentos...) com
 * {@code /tesouro loot add}.
 */
public class Loot {

    public record Premio(double coins, List<ItemStack> itens) {}

    private final TesourosPlugin plugin;
    private final File arquivo;
    private YamlConfiguration yaml;

    public Loot(TesourosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "loot.yml");
        carregar();
    }

    public void carregar() {
        if (!arquivo.exists()) plugin.saveResource("loot.yml", false);
        yaml = YamlConfiguration.loadConfiguration(arquivo);
    }

    public Premio sortear(String raridade) {
        ConfigurationSection secao = yaml.getConfigurationSection(raridade);
        if (secao == null) return new Premio(0, List.of());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Double> faixa = secao.getDoubleList("coins");
        double coins = faixa.size() >= 2 ? r.nextLong(faixa.get(0).longValue(), (long) Math.max(faixa.get(0), faixa.get(1)) + 1) : 0;

        List<ConfigurationSection> entradas = new ArrayList<>();
        double total = 0;
        ConfigurationSection itens = secao.getConfigurationSection("itens");
        if (itens != null) {
            for (String chave : itens.getKeys(false)) {
                ConfigurationSection e = itens.getConfigurationSection(chave);
                if (e == null || e.getDouble("peso", 0) <= 0) continue;
                entradas.add(e);
                total += e.getDouble("peso");
            }
        }
        // sem repetir: o item sorteado sai da lista (e o peso dele, do total)
        List<ItemStack> sorteados = new ArrayList<>();
        for (int i = 0; i < secao.getInt("rolagens", 0) && !entradas.isEmpty() && total > 0; i++) {
            double sorteio = r.nextDouble(total);
            for (int j = 0; j < entradas.size(); j++) {
                ConfigurationSection e = entradas.get(j);
                sorteio -= e.getDouble("peso");
                if (sorteio < 0 || j == entradas.size() - 1) {
                    ItemStack item = item(e);
                    // em pilhas que caibam (128 células viram 2 de 64)
                    for (int resta = item == null ? 0 : item.getAmount(); resta > 0; resta -= item.getMaxStackSize()) {
                        ItemStack pilha = item.clone();
                        pilha.setAmount(Math.min(resta, item.getMaxStackSize()));
                        sorteados.add(pilha);
                    }
                    entradas.remove(j);
                    total -= e.getDouble("peso");
                    break;
                }
            }
        }
        return new Premio(coins, sorteados);
    }

    private ItemStack item(ConfigurationSection e) {
        List<Integer> qtd = e.getIntegerList("qtd");
        int quantidade = qtd.size() >= 2 ? ThreadLocalRandom.current().nextInt(qtd.get(0), Math.max(qtd.get(0), qtd.get(1)) + 1) : 1;
        ItemStack pronto = e.getItemStack("itemstack");
        if (pronto != null) {
            ItemStack copia = pronto.clone();
            copia.setAmount(Math.max(1, quantidade));
            return copia;
        }
        String nome = e.getString("item", "");
        if (nome.equalsIgnoreCase("fragmento")) return plugin.getIntegracoes().fragmento(quantidade);
        if (nome.equalsIgnoreCase("combustivel")) return plugin.getIntegracoes().combustivel(quantidade);
        String minusculo = nome.toLowerCase(Locale.ROOT);
        if (minusculo.startsWith("spawner:")) return plugin.getIntegracoes().spawner(minusculo.substring(8), quantidade);
        if (minusculo.startsWith("maquina:")) return plugin.getIntegracoes().maquina(minusculo.substring(8));
        if (nome.toLowerCase(Locale.ROOT).startsWith("livro:")) {
            String[] partes = nome.split(":");
            Enchantment ench = partes.length >= 2 ? Registry.ENCHANTMENT.get(NamespacedKey.minecraft(partes[1].toLowerCase(Locale.ROOT))) : null;
            if (ench == null) {
                plugin.getLogger().warning("loot.yml: encantamento desconhecido em \"" + nome + "\"");
                return null;
            }
            ItemStack livro = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) livro.getItemMeta();
            meta.addStoredEnchant(ench, partes.length >= 3 ? Integer.parseInt(partes[2]) : 1, true);
            livro.setItemMeta(meta);
            return livro;
        }
        Material material = Material.matchMaterial(nome);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("loot.yml: item desconhecido \"" + nome + "\"");
            return null;
        }
        return new ItemStack(material, Math.max(1, quantidade));
    }

    /** Acrescenta um item qualquer ao loot de uma raridade. Devolve a chave criada. */
    public String adicionar(String raridade, ItemStack item, double peso, int min, int max) {
        String chave = "item" + (System.currentTimeMillis() % 1_000_000);
        String base = raridade + ".itens." + chave + ".";
        ItemStack copia = item.clone();
        copia.setAmount(1);
        yaml.set(base + "itemstack", copia);
        yaml.set(base + "qtd", List.of(min, max));
        yaml.set(base + "peso", peso);
        salvar();
        return chave;
    }

    public boolean remover(String raridade, String chave) {
        String caminho = raridade + ".itens." + chave;
        if (!yaml.contains(caminho)) return false;
        yaml.set(caminho, null);
        salvar();
        return true;
    }

    /** As linhas do /tesouro loot <raridade>. */
    public List<String> descrever(String raridade) {
        List<String> linhas = new ArrayList<>();
        ConfigurationSection secao = yaml.getConfigurationSection(raridade);
        if (secao == null) return linhas;
        List<Double> faixa = secao.getDoubleList("coins");
        linhas.add("§7Coins: §f" + (faixa.size() >= 2 ? plugin.dinheiro(faixa.get(0)) + " a " + plugin.dinheiro(faixa.get(1)) : "nenhuma")
                + " §7· rolagens: §f" + secao.getInt("rolagens", 0));
        ConfigurationSection itens = secao.getConfigurationSection("itens");
        if (itens == null) return linhas;
        for (String chave : itens.getKeys(false)) {
            ConfigurationSection e = itens.getConfigurationSection(chave);
            if (e == null) continue;
            ItemStack pronto = e.getItemStack("itemstack");
            String nome = pronto != null
                    ? (pronto.hasItemMeta() && pronto.getItemMeta().hasDisplayName() ? pronto.getItemMeta().getDisplayName() : pronto.getType().name())
                    : e.getString("item", "?");
            List<Integer> qtd = e.getIntegerList("qtd");
            linhas.add("§8- §e" + chave + "§7: §f" + nome + (qtd.size() >= 2 ? " §7x" + qtd.get(0) + "-" + qtd.get(1) : "")
                    + " §7peso §f" + e.getDouble("peso"));
        }
        return linhas;
    }

    private void salvar() {
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não deu pra salvar o loot.yml", e);
        }
    }
}
