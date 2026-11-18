package com.example.caixas;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Um prêmio de uma caixa: coins (% do preço do próximo rank, com piso), um item (do jogo ou de outro plugin
 * nosso) ou um comando do console. A raridade dá a cor e o anúncio; o peso, a chance.
 */
public final class Premio {

    enum Tipo { COINS, ITEM, COMANDO }

    final String id;
    final String raridade;
    final double peso;
    final Tipo tipo;
    final double coins;     // % do preço do próximo rank
    final double minimo;    // piso (e o valor sem o RankupPlugin)
    final String item;
    final int qtdMin, qtdMax;
    final String comando;
    final String nome;      // só pro comando (e opcional nos outros)
    final Material icone;

    Premio(String id, ConfigurationSection s) {
        this.id = id;
        this.raridade = s.getString("raridade", "comum");
        this.peso = Math.max(0, s.getDouble("peso", 1));
        this.coins = s.getDouble("coins", 0);
        this.minimo = s.getDouble("minimo", 0);
        this.item = s.getString("item");
        this.comando = s.getString("comando");
        this.tipo = s.contains("coins") ? Tipo.COINS : comando != null ? Tipo.COMANDO : Tipo.ITEM;
        List<Integer> qtd = s.getIntegerList("qtd");
        this.qtdMin = qtd.isEmpty() ? 1 : Math.max(1, qtd.get(0));
        this.qtdMax = qtd.size() >= 2 ? Math.max(qtdMin, qtd.get(1)) : qtdMin;
        this.nome = s.getString("nome");
        Material m = Material.matchMaterial(s.getString("icone", ""));
        this.icone = m != null ? m : tipo == Tipo.COINS ? Material.GOLD_INGOT : Material.CHEST;
    }

    int sortearQtd() {
        return qtdMin == qtdMax ? qtdMin : ThreadLocalRandom.current().nextInt(qtdMin, qtdMax + 1);
    }

    /** Quanto o prêmio em coins vale pra esse jogador. */
    double valor(CaixasPlugin plugin, Player jogador) {
        double preco = plugin.getIntegracoes().precoProximoRank(jogador.getUniqueId());
        double v = preco > 0 ? preco * coins / 100.0 : 0;
        return Math.round(Math.max(minimo, v));
    }

    /**
     * O ícone na roleta e no menu. Com {@code jogador}, o valor das coins é o dele; sem, o texto da regra
     * ("X% do próximo rank").
     */
    ItemStack icone(CaixasPlugin plugin, Player jogador) {
        ItemStack i = null;
        if (tipo == Tipo.ITEM && item != null) {
            i = plugin.getIntegracoes().item(item, qtdMin);
            if (i != null) i = i.clone();
        }
        if (i == null) i = new ItemStack(icone, tipo == Tipo.ITEM ? qtdMin : 1);
        ItemMeta meta = i.getItemMeta();
        String cor = plugin.corDa(raridade);
        if (tipo == Tipo.COINS) {
            meta.setDisplayName(cor + (jogador != null ? "R$ " + CaixasPlugin.dinheiro(valor(plugin, jogador))
                    : "Coins: " + CaixasPlugin.numero(coins) + "% do próximo rank"));
        } else if (nome != null) {
            meta.setDisplayName(CaixasPlugin.cores(nome));
        }
        i.setItemMeta(meta);
        return i;
    }

    /** O nome do prêmio pra mensagem (com a quantidade). */
    String descricao(CaixasPlugin plugin, ItemStack entregue, double valor) {
        String cor = plugin.corDa(raridade);
        if (tipo == Tipo.COINS) return cor + "R$ " + CaixasPlugin.dinheiro(valor);
        if (nome != null) return CaixasPlugin.cores(nome);
        if (entregue == null) return cor + id;
        ItemMeta meta = entregue.getItemMeta();
        String n = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : cor + CaixasPlugin.nomeDoItem(entregue);
        return (entregue.getAmount() > 1 ? "§f" + entregue.getAmount() + "x " : "") + n;
    }
}
