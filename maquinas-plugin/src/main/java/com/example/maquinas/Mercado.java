package com.example.maquinas;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Preço dos produtos, igual à loja: cada unidade vendida empurra o preço pra baixo ({@code passo}%,
 * até o {@code minimo}) e, com o tempo, ele volta pra âncora ({@code regeneracao}). O que muita
 * gente fabrica vale menos. A venda tem o bônus do rank, o do VIP e o imposto.
 */
public class Mercado {

    private final MaquinasPlugin plugin;
    private final File arquivo;
    private final Map<String, Double> precos = new HashMap<>();

    Mercado(MaquinasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "precos.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String id : yaml.getKeys(false)) precos.put(id, yaml.getDouble(id));
    }

    /** O preço base de agora (sem bônus) de uma unidade. */
    public double atual(MaquinasPlugin.Produto p) {
        return precos.getOrDefault(p.id(), p.ancora());
    }

    /** Rank × VIP: o que multiplica a venda desse jogador. */
    public double multiplicador(Player jogador) {
        return plugin.getRankup().multiplicadorVenda(jogador.getUniqueId()) * (1 + plugin.bonusVendaVip(jogador) / 100.0);
    }

    /** Vende {@code quantidade} unidades, deposita e devolve o líquido. */
    public double vender(Player jogador, MaquinasPlugin.Produto p, int quantidade) {
        double preco = atual(p);
        double bruto = 0;
        for (int i = 0; i < quantidade; i++) {
            bruto += preco;
            preco = Math.max(p.minimo(), preco * (1 - p.passo() / 100.0));
        }
        precos.put(p.id(), preco);
        double imposto = plugin.getConfig().getDouble("imposto-venda", 2) / 100.0;
        double liquido = bruto * multiplicador(jogador) * (1 - imposto);
        plugin.getEconomy().depositPlayer(jogador, liquido);
        salvar();
        return liquido;
    }

    /** A cada intervalo, cada preço anda uma fração do caminho de volta pra âncora. */
    void regenerar() {
        double fator = plugin.getConfig().getDouble("regeneracao.fator", 0.02);
        for (MaquinasPlugin.Produto p : plugin.getProdutos().values()) {
            double atual = atual(p);
            if (atual != p.ancora()) precos.put(p.id(), atual + (p.ancora() - atual) * fator);
        }
    }

    void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        precos.forEach(yaml::set);
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar precos.yml", e);
        }
    }
}
