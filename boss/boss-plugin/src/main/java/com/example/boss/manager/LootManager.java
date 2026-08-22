package com.example.boss.manager;

import com.example.boss.BossPlugin;
import com.example.boss.model.LootEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Loot do chão: cada item tem sua própria chance (0-100%), rolada independentemente —
 * então pode cair de 0 a N itens.
 *
 * Prêmio do campeão: um sorteio ponderado entre os itens cadastrados — exatamente 1 item
 * é escolhido, com probabilidade proporcional ao peso de cada um (não precisa somar 100%,
 * é normalizado automaticamente).
 */
public class LootManager {

    private final BossPlugin plugin;
    private final File arquivo;
    private final List<LootEntry> lootChao = new ArrayList<>();
    private final List<LootEntry> lootCampeao = new ArrayList<>();

    public LootManager(BossPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "loot.yml");
        carregar();
    }

    public void adicionarLootChao(ItemStack item, double chancePercentual) {
        lootChao.add(new LootEntry(item.clone(), chancePercentual / 100.0));
        salvar();
    }

    public void adicionarLootCampeao(ItemStack item, double peso) {
        lootCampeao.add(new LootEntry(item.clone(), peso));
        salvar();
    }

    public List<ItemStack> sortearLootChao() {
        List<ItemStack> resultado = new ArrayList<>();
        for (LootEntry entrada : lootChao) {
            if (ThreadLocalRandom.current().nextDouble() < entrada.getChance()) {
                resultado.add(entrada.getItem().clone());
            }
        }
        return resultado;
    }

    /** Sorteia exatamente 1 item da lista de prêmios do campeão, ponderado pelo peso de cada um. */
    public ItemStack sortearItemCampeao() {
        if (lootCampeao.isEmpty()) return null;

        double somaTotal = lootCampeao.stream().mapToDouble(LootEntry::getChance).sum();
        if (somaTotal <= 0) return null;

        double sorteio = ThreadLocalRandom.current().nextDouble() * somaTotal;
        double acumulado = 0;
        for (LootEntry entrada : lootCampeao) {
            acumulado += entrada.getChance();
            if (sorteio <= acumulado) {
                return entrada.getItem().clone();
            }
        }
        // Fallback de arredondamento de ponto flutuante (raro, mas possível)
        return lootCampeao.get(lootCampeao.size() - 1).getItem().clone();
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        salvarLista(yaml, "chao", lootChao);
        salvarLista(yaml, "campeao", lootCampeao);
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar loot.yml", e);
        }
    }

    private void salvarLista(YamlConfiguration yaml, String chaveRaiz, List<LootEntry> lista) {
        for (int i = 0; i < lista.size(); i++) {
            yaml.set(chaveRaiz + "." + i + ".item", lista.get(i).getItem());
            yaml.set(chaveRaiz + "." + i + ".chance", lista.get(i).getChance());
        }
    }

    private void carregar() {
        if (!arquivo.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        carregarLista(yaml, "chao", lootChao);
        carregarLista(yaml, "campeao", lootCampeao);
    }

    private void carregarLista(YamlConfiguration yaml, String chaveRaiz, List<LootEntry> destino) {
        ConfigurationSection secao = yaml.getConfigurationSection(chaveRaiz);
        if (secao == null) return;

        for (String indice : secao.getKeys(false)) {
            ItemStack item = secao.getItemStack(indice + ".item");
            double chance = secao.getDouble(indice + ".chance");
            if (item != null) {
                destino.add(new LootEntry(item, chance));
            }
        }
    }

    public List<LootEntry> getLootChao() {
        return lootChao;
    }

    public List<LootEntry> getLootCampeao() {
        return lootCampeao;
    }
}
