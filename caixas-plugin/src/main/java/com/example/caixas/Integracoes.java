package com.example.caixas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Conversa com os outros plugins nossos por reflexão, sem dependência de compilação (como o SkillsPlugin e o
 * TesourosPlugin): RankupPlugin (preço do próximo rank, minas e Fragmento Arcano), TesourosPlugin (mapas),
 * SpawnersPlugin e MaquinasPlugin (itens), PixPlugin (extrato) e DiscordPlugin (anúncio). Sem o plugin, aquela
 * parte só não acontece.
 */
public class Integracoes {

    private final CaixasPlugin plugin;
    private final Set<String> avisou = new HashSet<>();

    public Integracoes(CaixasPlugin plugin) {
        this.plugin = plugin;
    }

    private static Plugin ativo(String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nome);
        return p != null && p.isEnabled() ? p : null;
    }

    private void avisar(String oque, Exception e) {
        if (avisou.add(oque)) plugin.getLogger().warning(oque + " falhou (versão antiga?): " + e.getMessage());
    }

    private Object chamar(String nomePlugin, String metodo, Class<?>[] tipos, Object... args) {
        Plugin alvo = ativo(nomePlugin);
        if (alvo == null) return null;
        try {
            return alvo.getClass().getMethod(metodo, tipos).invoke(alvo, args);
        } catch (ReflectiveOperationException e) {
            avisar(nomePlugin + "#" + metodo, e);
            return null;
        }
    }

    /** Preço do próximo rank (ou do atual, no último), ou -1 sem o RankupPlugin. */
    public double precoProximoRank(UUID jogador) {
        return chamar("RankupPlugin", "precoProximoRank", new Class<?>[]{UUID.class}, jogador) instanceof Number n ? n.doubleValue() : -1;
    }

    /** O id da mina nesse lugar, ou null. */
    public String minaEm(Location local) {
        return chamar("RankupPlugin", "minaEm", new Class<?>[]{Location.class}, local) instanceof String id ? id : null;
    }

    /**
     * O item de um prêmio: fragmento, combustivel, mapa:<raridade>, spawner:<tipo>, maquina:<tipo>,
     * livro:<encantamento>:<nível>, chave:<caixa> ou um MATERIAL. null se não der.
     */
    public ItemStack item(String nome, int qtd) {
        String n = nome.toLowerCase(Locale.ROOT);
        ItemStack item = null;
        if (n.equals("fragmento")) {
            item = chamar("RankupPlugin", "fragmento", new Class<?>[]{int.class}, qtd) instanceof ItemStack i ? i : null;
        } else if (n.equals("combustivel")) {
            item = chamar("MaquinasPlugin", "combustivel", new Class<?>[]{int.class}, qtd) instanceof ItemStack i ? i : null;
        } else if (n.startsWith("mapa:")) {
            item = mapa(n.substring(5));
            if (item != null) item.setAmount(qtd);
        } else if (n.startsWith("spawner:")) {
            item = itemDeTipo("SpawnersPlugin", "item", n.substring(8), qtd);
        } else if (n.startsWith("maquina:")) {
            item = itemDeTipo("MaquinasPlugin", "maquina", n.substring(8), 0);
        } else if (n.startsWith("livro:")) {
            item = livro(n);
        } else if (n.startsWith("chave:")) {
            item = plugin.chave(n.substring(6), qtd);
        } else {
            Material m = Material.matchMaterial(nome);
            if (m != null && m.isItem()) item = new ItemStack(m, qtd);
        }
        if (item == null && avisou.add("item " + nome)) plugin.getLogger().warning("Prêmio com item que não deu pra criar: " + nome);
        return item;
    }

    private ItemStack mapa(String raridade) {
        Object mapas = chamar("TesourosPlugin", "getMapas", new Class<?>[0]);
        if (mapas == null) return null;
        try {
            return mapas.getClass().getMethod("criar", String.class).invoke(mapas, raridade) instanceof ItemStack i ? i : null;
        } catch (ReflectiveOperationException e) {
            avisar("TesourosPlugin Mapas#criar", e);
            return null;
        }
    }

    private ItemStack itemDeTipo(String nomePlugin, String metodo, String tipo, int quantidade) {
        Plugin p = ativo(nomePlugin);
        if (p == null) return null;
        try {
            Object tipos = p.getClass().getMethod("getTipos").invoke(p);
            Object t = tipos instanceof Map<?, ?> mapa ? mapa.get(tipo) : null;
            if (t == null) return null;
            Object item = metodo.equals("maquina")
                    ? p.getClass().getMethod(metodo, t.getClass(), int.class, int.class).invoke(p, t, 0, 0)
                    : p.getClass().getMethod(metodo, t.getClass(), int.class).invoke(p, t, quantidade);
            return item instanceof ItemStack i ? i : null;
        } catch (ReflectiveOperationException e) {
            avisar(nomePlugin + "#" + metodo, e);
            return null;
        }
    }

    private static ItemStack livro(String nome) {
        String[] partes = nome.split(":");
        Enchantment ench = partes.length >= 2 ? Registry.ENCHANTMENT.get(NamespacedKey.minecraft(partes[1])) : null;
        if (ench == null) return null;
        ItemStack livro = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) livro.getItemMeta();
        meta.addStoredEnchant(ench, partes.length >= 3 ? Integer.parseInt(partes[2]) : 1, true);
        livro.setItemMeta(meta);
        return livro;
    }

    /** Entrada de dinheiro no extrato do Bancário (PixPlugin). */
    public void extrato(UUID jogador, double valor, String motivo) {
        Plugin pix = ativo("PixPlugin");
        if (pix == null) return;
        try {
            Object charges = pix.getClass().getMethod("getChargeManager").invoke(pix);
            charges.getClass().getMethod("registrarTransacaoComEntidade", UUID.class, String.class, double.class, boolean.class, String.class)
                    .invoke(charges, jogador, "Caixas", valor, true, motivo);
        } catch (ReflectiveOperationException e) {
            avisar("PixPlugin#registrarTransacaoComEntidade", e);
        }
    }

    public void discord(String mensagem) {
        chamar("DiscordPlugin", "enviar", new Class<?>[]{String.class, String.class}, "caixas", mensagem);
    }
}
