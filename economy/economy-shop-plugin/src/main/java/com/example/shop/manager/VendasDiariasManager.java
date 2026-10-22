package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Quanto de cada item foi vendido pra loja por dia, e por quanto (no preço dinâmico, antes do
 * bônus do rank e do imposto). Serve pra análise da economia: qual item está sendo farmado e
 * quanto o preço dele cai na prática.
 *
 * Grava em {@code vendas-diarias.yml} a cada 5 min (se mudou) e no desligamento. Guarda só os
 * últimos {@code estatisticas.dias-guardados}.
 */
public class VendasDiariasManager {

    /** [0] = unidades vendidas, [1] = valor pago (bruto). */
    private static final int QUANTIDADE = 0;
    private static final int VALOR = 1;

    private final ShopPlugin plugin;
    private final File arquivo;
    private final TreeMap<LocalDate, Map<Material, double[]>> porDia = new TreeMap<>();
    private boolean sujo;

    public VendasDiariasManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "vendas-diarias.yml");
        carregar();
    }

    public void registrar(Material material, int quantidade, double valorBruto) {
        if (quantidade <= 0) return;
        double[] totais = porDia.computeIfAbsent(LocalDate.now(), k -> new EnumMap<>(Material.class))
                .computeIfAbsent(material, k -> new double[2]);
        totais[QUANTIDADE] += quantidade;
        totais[VALOR] += valorBruto;
        sujo = true;
    }

    public void salvarSeMudou() {
        if (sujo) salvar();
    }

    public void salvar() {
        LocalDate limite = LocalDate.now().minusDays(Math.max(1, plugin.getConfig().getInt("estatisticas.dias-guardados", 90)));
        porDia.headMap(limite).clear();

        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<LocalDate, Map<Material, double[]>> dia : porDia.entrySet()) {
            for (Map.Entry<Material, double[]> item : dia.getValue().entrySet()) {
                String base = "dias." + dia.getKey() + "." + item.getKey().name();
                double[] totais = item.getValue();
                yaml.set(base + ".quantidade", (long) totais[QUANTIDADE]);
                yaml.set(base + ".valor", Math.round(totais[VALOR] * 100) / 100.0);
                yaml.set(base + ".preco-medio", Math.round(totais[VALOR] / totais[QUANTIDADE] * 100) / 100.0);
            }
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar vendas-diarias.yml", e);
        }
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection dias = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("dias");
        if (dias == null) return;

        for (String chave : dias.getKeys(false)) {
            ConfigurationSection secao = dias.getConfigurationSection(chave);
            if (secao == null) continue;
            try {
                Map<Material, double[]> itens = new EnumMap<>(Material.class);
                for (String nome : secao.getKeys(false)) {
                    Material material = Material.matchMaterial(nome);
                    if (material == null) continue;
                    itens.put(material, new double[]{secao.getDouble(nome + ".quantidade"), secao.getDouble(nome + ".valor")});
                }
                porDia.put(LocalDate.parse(chave), itens);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Dia inválido ignorado em vendas-diarias.yml: " + chave);
            }
        }
    }
}
