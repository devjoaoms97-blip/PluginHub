package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class ShopManager {

    private final ShopPlugin plugin;
    private final File arquivo;
    private final Map<Material, ShopItem> itens = new LinkedHashMap<>();

    public ShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "itens.yml");
        carregar();
    }

    public boolean existeItem(Material material) {
        return itens.containsKey(material);
    }

    public ShopItem getItem(Material material) {
        return itens.get(material);
    }

    public Map<Material, ShopItem> getTodosItens() {
        return itens;
    }

    /**
     * As categorias do menu: primeiro as fixas do config ({@code categorias}, na ordem de lá, mesmo
     * vazias), depois as que só aparecem nos itens cadastrados.
     */
    public java.util.List<String> getCategorias() {
        java.util.LinkedHashSet<String> categorias = new java.util.LinkedHashSet<>();
        for (String entrada : plugin.getConfig().getStringList("categorias")) {
            String nome = entrada.split(":", 2)[0].trim();
            if (!nome.isEmpty()) categorias.add(nome);
        }
        for (ShopItem item : itens.values()) {
            boolean jaTem = categorias.stream().anyMatch(c -> c.equalsIgnoreCase(item.getCategoria()));
            if (!jaTem) categorias.add(item.getCategoria());
        }
        return new java.util.ArrayList<>(categorias);
    }

    /** O ícone da categoria fixa no config ("Nome:MATERIAL"), ou null. */
    public Material getIconeCategoria(String categoria) {
        for (String entrada : plugin.getConfig().getStringList("categorias")) {
            String[] partes = entrada.split(":", 2);
            if (partes.length == 2 && partes[0].trim().equalsIgnoreCase(categoria)) {
                return Material.matchMaterial(partes[1].trim());
            }
        }
        return null;
    }

    public java.util.List<ShopItem> getItensDaCategoria(String categoria) {
        java.util.List<ShopItem> resultado = new java.util.ArrayList<>();
        for (ShopItem item : itens.values()) {
            if (item.getCategoria().equalsIgnoreCase(categoria)) {
                resultado.add(item);
            }
        }
        return resultado;
    }

    public void cadastrarItem(Material material, String categoria, double precoBase, double margem, double min, double max, double passo) {
        ShopItem item = new ShopItem(material, categoria, precoBase, margem, min, max, passo);
        itens.put(material, item);
        salvar();
    }

    public boolean removerItem(Material material) {
        boolean removeu = itens.remove(material) != null;
        if (removeu) salvar();
        return removeu;
    }

    public boolean resetarPreco(Material material) {
        ShopItem item = itens.get(material);
        if (item == null) return false;
        item.resetarParaAncora();
        salvar();
        return true;
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ShopItem item : itens.values()) {
            String caminho = "itens." + item.getMaterial().name();
            yaml.set(caminho + ".categoria", item.getCategoria());
            yaml.set(caminho + ".preco-base", item.getPrecoBase());
            yaml.set(caminho + ".margem", item.getMargemPercentual());
            yaml.set(caminho + ".minimo", item.getPrecoMinimo());
            yaml.set(caminho + ".maximo", item.getPrecoMaximo());
            yaml.set(caminho + ".passo", item.getPassoPercentual());
            yaml.set(caminho + ".preco-venda-atual", item.getPrecoVendaAtual());
            if (!item.isCompravel()) yaml.set(caminho + ".compra", false);
            if (!item.isVendivel()) yaml.set(caminho + ".venda", item.isVendivelNaMina() ? "so-mina" : false);
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar itens.yml", e);
        }
    }

    /** Recarrega a lista de itens direto do itens.yml do disco (usado no /shop admin reload). */
    public void recarregar() {
        itens.clear();
        carregar();
    }

    private void carregar() {
        if (!arquivo.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("itens");
        if (secao == null) return;

        for (String chave : secao.getKeys(false)) {
            try {
                Material material = Material.valueOf(chave);
                String categoria = secao.getString(chave + ".categoria", "Geral");
                double precoBase = secao.getDouble(chave + ".preco-base");
                double margem = secao.getDouble(chave + ".margem");
                double min = secao.getDouble(chave + ".minimo");
                double max = secao.getDouble(chave + ".maximo");
                double passo = secao.getDouble(chave + ".passo");
                double precoAtualSalvo = secao.getDouble(chave + ".preco-venda-atual", -1);

                ShopItem item = new ShopItem(material, categoria, precoBase, margem, min, max, passo);
                if (precoAtualSalvo >= 0) {
                    item.setPrecoVendaAtual(precoAtualSalvo);
                }
                item.setCompravel(secao.getBoolean(chave + ".compra", true));
                String venda = secao.getString(chave + ".venda", "true");
                item.setVendivel(venda.equalsIgnoreCase("true"));
                item.setVendivelNaMina(!venda.equalsIgnoreCase("false"));
                itens.put(material, item);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Item inválido em itens.yml: " + chave);
            }
        }
    }
}
