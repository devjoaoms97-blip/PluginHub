package com.example.painel;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

/**
 * Quanto dinheiro entrou (depósitos) e saiu (saques) da economia por dia, separado pelo
 * plugin que movimentou (e, dentro dele, por tipo), mais uma "foto" diária do total em circulação.
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
    private final TreeMap<LocalDate, Map<String, Map<String, double[]>>> tiposPorDia = new TreeMap<>();
    private final TreeMap<LocalDate, Double> circulacaoPorDia = new TreeMap<>();
    private final Set<LocalDate> diasAlertados = new HashSet<>();
    private boolean sujo;

    public FluxoManager(PainelEconomiaPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "historico.yml");
        carregar();
    }

    /** {@code valor} positivo = entrou na conta de alguém; negativo = saiu. */
    public synchronized void registrar(String origem, String tipo, double valor) {
        LocalDate hoje = LocalDate.now();
        somar(porDia.computeIfAbsent(hoje, k -> new HashMap<>()).computeIfAbsent(origem, k -> new double[2]), valor);
        somar(tiposPorDia.computeIfAbsent(hoje, k -> new HashMap<>())
                .computeIfAbsent(origem, k -> new HashMap<>())
                .computeIfAbsent(tipo, k -> new double[2]), valor);
        sujo = true;
    }

    private static void somar(double[] totais, double valor) {
        if (valor >= 0) {
            totais[ENTROU] += valor;
        } else {
            totais[SAIU] += -valor;
        }
    }

    /** Tipos de um plugin somados de {@code de} até {@code ate} (inclusive). */
    public synchronized Map<String, double[]> getTipos(String origem, LocalDate de, LocalDate ate) {
        Map<String, double[]> soma = new HashMap<>();
        for (Map<String, Map<String, double[]>> dia : tiposPorDia.subMap(de, true, ate, true).values()) {
            Map<String, double[]> tipos = dia.get(origem);
            if (tipos == null) continue;
            tipos.forEach((tipo, totais) -> {
                double[] acumulado = soma.computeIfAbsent(tipo, k -> new double[2]);
                acumulado[ENTROU] += totais[ENTROU];
                acumulado[SAIU] += totais[SAIU];
            });
        }
        return soma;
    }

    /** Plugins com alguma movimentação de {@code de} até {@code ate} (pro tab complete). */
    public synchronized Set<String> getOrigens(LocalDate de, LocalDate ate) {
        Set<String> origens = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        porDia.subMap(de, true, ate, true).values().forEach(m -> origens.addAll(m.keySet()));
        return origens;
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

    /** Marca o dia como já alertado; retorna false se já estava (o alerta sai uma vez por dia). */
    public synchronized boolean marcarAlerta(LocalDate dia) {
        boolean novo = diasAlertados.add(dia);
        if (novo) sujo = true;
        return novo;
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
                if (secao.getBoolean("alertado")) {
                    diasAlertados.add(dia);
                }
                ConfigurationSection origens = secao.getConfigurationSection("origens");
                if (origens == null) continue;
                Map<String, double[]> mapa = new HashMap<>();
                Map<String, Map<String, double[]>> tiposDoDia = new HashMap<>();
                for (String origem : origens.getKeys(false)) {
                    mapa.put(origem, new double[]{origens.getDouble(origem + ".entrou"), origens.getDouble(origem + ".saiu")});
                    ConfigurationSection tipos = origens.getConfigurationSection(origem + ".tipos");
                    if (tipos == null) continue;
                    Map<String, double[]> doPlugin = new HashMap<>();
                    for (String tipo : tipos.getKeys(false)) {
                        doPlugin.put(tipo, new double[]{tipos.getDouble(tipo + ".entrou"), tipos.getDouble(tipo + ".saiu")});
                    }
                    tiposDoDia.put(origem, doPlugin);
                }
                porDia.put(dia, mapa);
                tiposPorDia.put(dia, tiposDoDia);
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
        tiposPorDia.headMap(limite).clear();
        circulacaoPorDia.headMap(limite).clear();
        diasAlertados.removeIf(dia -> dia.isBefore(limite));

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
        for (Map.Entry<LocalDate, Map<String, Map<String, double[]>>> dia : tiposPorDia.entrySet()) {
            for (Map.Entry<String, Map<String, double[]>> origem : dia.getValue().entrySet()) {
                for (Map.Entry<String, double[]> tipo : origem.getValue().entrySet()) {
                    String base = "dias." + dia.getKey() + ".origens." + origem.getKey() + ".tipos." + tipo.getKey();
                    yaml.set(base + ".entrou", tipo.getValue()[ENTROU]);
                    yaml.set(base + ".saiu", tipo.getValue()[SAIU]);
                }
            }
        }
        for (LocalDate dia : diasAlertados) {
            yaml.set("dias." + dia + ".alertado", true);
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar historico.yml", e);
        }
    }
}
