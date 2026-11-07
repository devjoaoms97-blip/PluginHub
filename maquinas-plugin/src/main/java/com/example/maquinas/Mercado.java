package com.example.maquinas;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Preço dos produtos, por jogador: cada unidade que o jogador vende empurra o preço DELE pra baixo
 * ({@code passo}%, até o {@code minimo}) e, com o tempo, ele volta pra âncora ({@code regeneracao}).
 * Quem fabrica muito vende mais barato, mas não derruba o preço dos outros (até 2026-10-31 o preço era
 * um só pro servidor: com uns 15 teares vendendo, o tecido ficava abaixo do custo do combustível pra
 * todo mundo). A venda tem o bônus do rank, o do VIP e o imposto.
 */
public class Mercado {

    private final MaquinasPlugin plugin;
    private final File arquivo;
    /** Jogador → produto → preço. Quem não está aqui (ou o produto que falta) está na âncora. */
    private final Map<UUID, Map<String, Double>> precos = new HashMap<>();

    Mercado(MaquinasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "precos.yml");
        // o precos.yml antigo (um preço por produto, pro servidor todo) é ignorado: todo mundo começa na âncora
        ConfigurationSection jogadores = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("jogadores");
        if (jogadores == null) return;
        for (String id : jogadores.getKeys(false)) {
            ConfigurationSection secao = jogadores.getConfigurationSection(id);
            if (secao == null) continue;
            Map<String, Double> dele = new HashMap<>();
            for (String produto : secao.getKeys(false)) dele.put(produto, secao.getDouble(produto));
            try {
                precos.put(UUID.fromString(id), dele);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Jogador inválido ignorado em precos.yml: " + id);
            }
        }
    }

    /** O preço base de agora (sem bônus) de uma unidade, pra esse jogador. */
    public double atual(UUID jogador, MaquinasPlugin.Produto p) {
        if (!dinamico()) return p.ancora();
        Map<String, Double> dele = precos.get(jogador);
        return dele == null ? p.ancora() : dele.getOrDefault(p.id(), p.ancora());
    }

    /** Rank × VIP: o que multiplica a venda desse jogador. */
    public double multiplicador(Player jogador) {
        return plugin.getRankup().multiplicadorVenda(jogador.getUniqueId()) * (1 + plugin.bonusVendaVip(jogador) / 100.0);
    }

    /** O preço de cada um cai a cada venda? Desligado desde 2026-11-07: sempre a âncora. */
    private boolean dinamico() {
        return plugin.getConfig().getBoolean("preco-dinamico", false);
    }

    /** Vende {@code quantidade} unidades, deposita e devolve o líquido. */
    public double vender(Player jogador, MaquinasPlugin.Produto p, int quantidade) {
        double preco = atual(jogador.getUniqueId(), p);
        double bruto = 0;
        for (int i = 0; i < quantidade; i++) {
            bruto += preco;
            if (dinamico()) preco = Math.max(p.minimo(), preco * (1 - p.passo() / 100.0));
        }
        if (dinamico()) precos.computeIfAbsent(jogador.getUniqueId(), k -> new HashMap<>()).put(p.id(), preco);
        double imposto = plugin.getConfig().getDouble("imposto-venda", 2) / 100.0;
        double liquido = bruto * multiplicador(jogador) * (1 - imposto);
        plugin.getEconomy().depositPlayer(jogador, liquido);
        salvar();
        return liquido;
    }

    /**
     * A cada intervalo, cada preço anda uma fração do caminho de volta pra âncora. O que chega a menos
     * de 0,1% da âncora volta pra ela e sai da lista.
     */
    void regenerar() {
        double fator = plugin.getConfig().getDouble("regeneracao.fator", 0.02);
        Map<String, MaquinasPlugin.Produto> produtos = plugin.getProdutos();
        precos.values().forEach(dele -> dele.entrySet().removeIf(e -> {
            MaquinasPlugin.Produto p = produtos.get(e.getKey());
            if (p == null) return true;
            double novo = e.getValue() + (p.ancora() - e.getValue()) * fator;
            if (Math.abs(p.ancora() - novo) < p.ancora() * 0.001) return true;
            e.setValue(novo);
            return false;
        }));
        precos.values().removeIf(Map::isEmpty);
    }

    void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        precos.forEach((jogador, dele) -> dele.forEach((produto, preco) -> yaml.set("jogadores." + jogador + "." + produto, preco)));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar precos.yml", e);
        }
    }
}
