package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class PriceHistoryManager {

    private static final long JANELA_MS = 24L * 60 * 60 * 1000; // 24 horas

    public record Ponto(long timestamp, double valor) {
    }

    private final ShopPlugin plugin;
    private final File arquivo;
    private final Map<Material, List<Ponto>> historico = new HashMap<>();

    public PriceHistoryManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "historico.yml");
        carregar();
    }

    /** Chamado periodicamente: tira uma "foto" do preço de venda atual de cada item cadastrado. */
    public void registrarSnapshotDeTodos() {
        long agora = System.currentTimeMillis();
        for (var item : plugin.getShopManager().getTodosItens().values()) {
            List<Ponto> lista = historico.computeIfAbsent(item.getMaterial(), k -> new ArrayList<>());
            lista.add(new Ponto(agora, item.getPrecoVendaAtual()));
            lista.removeIf(p -> agora - p.timestamp() > JANELA_MS);
        }
        salvar();
    }

    public List<Ponto> getHistorico(Material material) {
        return historico.getOrDefault(material, List.of());
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entrada : historico.entrySet()) {
            String base = "historico." + entrada.getKey().name();
            List<Ponto> pontos = entrada.getValue();
            for (int i = 0; i < pontos.size(); i++) {
                yaml.set(base + "." + i + ".t", pontos.get(i).timestamp());
                yaml.set(base + "." + i + ".v", pontos.get(i).valor());
            }
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar historico.yml", e);
        }
    }

    private void carregar() {
        if (!arquivo.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secaoRaiz = yaml.getConfigurationSection("historico");
        if (secaoRaiz == null) return;

        long agora = System.currentTimeMillis();
        for (String materialNome : secaoRaiz.getKeys(false)) {
            try {
                Material material = Material.valueOf(materialNome);
                ConfigurationSection secaoItem = secaoRaiz.getConfigurationSection(materialNome);
                List<Ponto> pontos = new ArrayList<>();
                if (secaoItem != null) {
                    for (String indice : secaoItem.getKeys(false)) {
                        long t = secaoItem.getLong(indice + ".t");
                        double v = secaoItem.getDouble(indice + ".v");
                        if (agora - t <= JANELA_MS) {
                            pontos.add(new Ponto(t, v));
                        }
                    }
                }
                pontos.sort(Comparator.comparingLong(Ponto::timestamp));
                historico.put(material, pontos);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Material inválido em historico.yml: " + materialNome);
            }
        }
    }
}
