package com.example.painel;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Quanto dinheiro entrou (depósitos) e saiu (saques) da economia por dia, separado pelo
 * plugin que movimentou, mais uma "foto" diária do total em circulação.
 *
 * Grava em {@code historico.yml} de tempos em tempos (não a cada transação — o Pix e a loja
 * movimentam muito) e no desligamento. Guarda só os últimos {@code historico.dias-guardados}.
 */
public class FluxoManager {

    /** [0] = entrou (soma dos depósitos), [1] = saiu (soma dos saques, positiva). */
    public static final int ENTROU = 0;
    public static final int SAIU = 1;

    private final PainelEconomiaPlugin plugin;
    private final File arquivo;
    private final TreeMap<LocalDate, Map<String, double[]>> porDia = new TreeMap<>();
    private final TreeMap<LocalDate, Double> circulacaoPorDia = new TreeMap<>();
    private boolean sujo;

    public FluxoManager(PainelEconomiaPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "historico.yml");
        carregar();
    }

    /** {@code valor} positivo = entrou na conta de alguém; negativo = saiu. */
    public synchronized void registrar(String origem, double valor) {
        double[] totais = porDia.computeIfAbsent(LocalDate.now(), k -> new HashMap<>())
                .computeIfAbsent(origem, k -> new double[2]);
        if (valor >= 0) {
            totais[ENTROU] += valor;
        } else {
            totais[SAIU] += -valor;
        }
        sujo = true;
    }

    public synchronized Map<String, double[]> getDia(LocalDate dia) {
        Map<String, double[]> mapa = porDia.get(dia);
        Map<String, double[]> copia = new HashMap<>();
        if (mapa != null) {
            mapa.forEach((origem, totais) -> copia.put(origem, totais.clone()));
        }
        return copia;
    }

    public synchronized void registrarCirculacao(LocalDate dia, double total) {
        circulacaoPorDia.put(dia, total);
        sujo = true;
    }

    public synchronized Double getCirculacao(LocalDate dia) {
        return circulacaoPorDia.get(dia);
    }

    // ---------------------------------------------------------------------
    // Persistência (historico.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection dias = yaml.getConfigurationSection("dias");
        if (dias == null) return;

        for (String chave : dias.getKeys(false)) {
            try {
                LocalDate dia = LocalDate.parse(chave);
                ConfigurationSection secao = dias.getConfigurationSection(chave);
                if (secao == null) continue;
                if (secao.contains("circulacao")) {
                    circulacaoPorDia.put(dia, secao.getDouble("circulacao"));
                }
                ConfigurationSection origens = secao.getConfigurationSection("origens");
                if (origens == null) continue;
                Map<String, double[]> mapa = new HashMap<>();
                for (String origem : origens.getKeys(false)) {
                    mapa.put(origem, new double[]{origens.getDouble(origem + ".entrou"), origens.getDouble(origem + ".saiu")});
                }
                porDia.put(dia, mapa);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Dia inválido ignorado em historico.yml: " + chave);
            }
        }
    }

    public synchronized void salvarSeMudou() {
        if (sujo) salvar();
    }

    public synchronized void salvar() {
        LocalDate limite = LocalDate.now().minusDays(Math.max(1, plugin.getConfig().getInt("historico.dias-guardados", 90)));
        porDia.headMap(limite).clear();
        circulacaoPorDia.headMap(limite).clear();

        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<LocalDate, Double> entrada : circulacaoPorDia.entrySet()) {
            yaml.set("dias." + entrada.getKey() + ".circulacao", entrada.getValue());
        }
        for (Map.Entry<LocalDate, Map<String, double[]>> dia : porDia.entrySet()) {
            for (Map.Entry<String, double[]> origem : dia.getValue().entrySet()) {
                String base = "dias." + dia.getKey() + ".origens." + origem.getKey();
                yaml.set(base + ".entrou", origem.getValue()[ENTROU]);
                yaml.set(base + ".saiu", origem.getValue()[SAIU]);
            }
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar historico.yml", e);
        }
    }
}
