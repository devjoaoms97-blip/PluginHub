package com.example.oficinas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cambista: troca Fragmentos Arcanos (que caem raramente nas minas, pelo RankupPlugin) e coins por
 * encantamentos acima do máximo no item da mão: Eficiência, Fortuna, Afiação, Proteção e Poder VI a
 * VIII, Inquebrável IV a VI... A lista, os níveis e os custos ficam em {@code cambista.encantamentos}.
 * <p>
 * Sobe um nível por vez: do que o item tem (qualquer nível abaixo do primeiro da lista) pro primeiro
 * nível, e depois um a um. É caro de propósito: esses níveis desequilibram a economia e o PvP, então
 * cada um custa dezenas de horas de mina em fragmentos, mais coins (que saem da economia). O desconto
 * de VIP vale só nas coins. Encantamento que conflita com um do item (Fortuna e Toque Suave, as
 * proteções entre si) não é oferecido.
 * <p>
 * Cada encantamento só vai nos itens da lista {@code itens} dele (Afiação em espada e machado, nunca
 * em picareta ou pá): "_PICKAXE" vale pra todo material que termina assim, e "BOW" só pro arco. Sem
 * a lista, vale o que o jogo aceita.
 */
public class Cambista implements Listener {

    /** A marca que o RankupPlugin põe no Fragmento Arcano. */
    private static final NamespacedKey FRAGMENTO = new NamespacedKey("rankupplugin", "fragmento");

    static class Menu implements InventoryHolder {
        final Map<Integer, String> slotParaEncantamento = new HashMap<>();
        int slotDaMao;
        ItemStack itemNoMomento;
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final OficinasPlugin plugin;

    public Cambista(OficinasPlugin plugin) {
        this.plugin = plugin;
    }

    public void abrir(Player jogador) {
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 36, "§8✦ Cambista — fragmentos");
        menu.slotDaMao = jogador.getInventory().getHeldItemSlot();
        ItemStack mao = jogador.getInventory().getItemInMainHand();
        menu.itemNoMomento = mao.clone();
        int fragmentos = contarFragmentos(jogador);

        menu.inventario.setItem(31, Servicos.icone(Material.AMETHYST_SHARD, "§d✦ Seus fragmentos: §f" + fragmentos,
                "§7Caem raramente ao minerar nas minas.", "§7Aqui viram encantamentos acima do máximo."));
        if (mao.getType().isAir()) {
            menu.inventario.setItem(4, Servicos.icone(Material.BARRIER, "§cSegure o item na mão",
                    "§7Feche, segure na mão a ferramenta, arma ou", "§7armadura que quer encantar e fale comigo de novo."));
        } else {
            ItemStack copia = mao.clone();
            menu.inventario.setItem(4, copia);
        }

        int slot = 9;
        ConfigurationSection secao = secao("cambista.encantamentos");
        if (secao != null) {
            for (String chave : secao.getKeys(false)) {
                if (slot >= 27) break;
                Enchantment ench = encantamento(chave);
                TreeMap<Integer, double[]> niveis = niveis(chave);
                if (ench == null || niveis.isEmpty()) continue;
                String nome = plugin.cores(secao.getString(chave + ".nome", chave));
                if (mao.getType().isAir()) {
                    // sem item: só a vitrine, com os custos
                    menu.inventario.setItem(slot++, vitrine(nome, niveis, jogador));
                    continue;
                }
                if (!serve(chave, ench, mao)) continue;
                menu.inventario.setItem(slot, oferta(jogador, mao, ench, nome, niveis, fragmentos));
                menu.slotParaEncantamento.put(slot, chave);
                slot++;
            }
        }
        if (!mao.getType().isAir() && menu.slotParaEncantamento.isEmpty()) {
            menu.inventario.setItem(13, Servicos.icone(Material.GRAY_DYE, "§7Nada pra esse item",
                    "§7Nenhum encantamento meu serve nele."));
        }
        jogador.openInventory(menu.inventario);
    }

    /** O ícone de um encantamento pro item da mão: o próximo nível, o custo e se dá pra comprar. */
    private ItemStack oferta(Player jogador, ItemStack mao, Enchantment ench, String nome, TreeMap<Integer, double[]> niveis, int fragmentos) {
        int atual = mao.getEnchantmentLevel(ench);
        Integer proximo = niveis.higherKey(atual);
        if (proximo == null) {
            return Servicos.icone(Material.BOOK, "§a" + nome + " " + romano(atual), "§7Esse item já está no máximo.");
        }
        if (conflita(mao, ench)) {
            return Servicos.icone(Material.BOOK, "§7" + nome, "§cConflita com um encantamento do item.");
        }
        double[] custo = niveis.get(proximo);
        double preco = precoComDesconto(jogador, custo[1]);
        List<String> lore = new ArrayList<>();
        lore.add("§7No item: §f" + (atual > 0 ? nome + " " + romano(atual) : "nada"));
        lore.add("");
        lore.add("§7Custo: §d" + (int) custo[0] + " fragmentos §7+ §a" + plugin.dinheiro(preco));
        if (plugin.descontoVip(jogador) > 0) lore.add("§6(" + String.format(Locale.ROOT, "%.0f", plugin.descontoVip(jogador)) + "% de desconto VIP nas coins)");
        boolean temFragmentos = fragmentos >= custo[0];
        boolean temDinheiro = plugin.getEconomy() != null && plugin.getEconomy().has(jogador, preco);
        lore.add(temFragmentos ? "§a✔ fragmentos" : "§c✖ faltam " + (int) (custo[0] - fragmentos) + " fragmentos");
        lore.add(temDinheiro ? "§a✔ coins" : "§c✖ coins insuficientes");
        Integer depois = niveis.higherKey(proximo);
        if (depois != null) lore.add("§8Depois: " + nome + " " + romano(depois));
        lore.add("");
        lore.add(temFragmentos && temDinheiro ? "§eClique pra encantar" : "§7Junte o que falta e volte");
        ItemStack livro = Servicos.icone(Material.ENCHANTED_BOOK, "§d" + nome + " " + romano(proximo), lore.toArray(String[]::new));
        return livro;
    }

    /** Sem item na mão: o que o cambista faz e quanto custa cada nível. */
    private ItemStack vitrine(String nome, TreeMap<Integer, double[]> niveis, Player jogador) {
        List<String> lore = new ArrayList<>();
        niveis.forEach((nivel, custo) -> lore.add("§f" + romano(nivel) + "§7: §d" + (int) custo[0] + " frag. §7+ §a"
                + plugin.dinheiro(precoComDesconto(jogador, custo[1]))));
        return Servicos.icone(Material.ENCHANTED_BOOK, "§d" + nome, lore.toArray(String[]::new));
    }

    private void clique(Player jogador, Menu menu, int slot) {
        String chave = menu.slotParaEncantamento.get(slot);
        if (chave == null) return;
        ItemStack item = jogador.getInventory().getItem(menu.slotDaMao);
        // o item mudou desde que o menu abriu (trocou de mão, usou): refaz em vez de cobrar no errado
        if (item == null || !item.equals(menu.itemNoMomento) || jogador.getInventory().getHeldItemSlot() != menu.slotDaMao) {
            abrir(jogador);
            return;
        }
        Enchantment ench = encantamento(chave);
        TreeMap<Integer, double[]> niveis = niveis(chave);
        if (ench == null || !serve(chave, ench, item) || conflita(item, ench)) return;
        Integer proximo = niveis.higherKey(item.getEnchantmentLevel(ench));
        if (proximo == null) return;
        double[] custo = niveis.get(proximo);
        int fragmentos = (int) custo[0];
        if (contarFragmentos(jogador) < fragmentos) {
            jogador.sendMessage("§cFaltam fragmentos: precisa de §f" + fragmentos + "§c, você tem §f" + contarFragmentos(jogador) + "§c.");
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        String nome = plugin.cores(plugin.getConfig().getString("cambista.encantamentos." + chave + ".nome", chave));
        double preco = precoComDesconto(jogador, custo[1]);
        if (!plugin.getServicos().cobrar(jogador, preco, "Cambista (" + org.bukkit.ChatColor.stripColor(nome) + " " + romano(proximo) + ")")) return;
        tirarFragmentos(jogador, fragmentos);

        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(ench, proximo, true);
        item.setItemMeta(meta);
        jogador.getInventory().setItem(menu.slotDaMao, item);
        plugin.getIntegracoes().missao(jogador, "ENCANTAR", item.getType().name(), 1);

        jogador.playSound(jogador.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
        jogador.sendMessage("§d✦ O cambista gravou §f" + nome + " " + romano(proximo) + " §dno seu item por §f" + fragmentos
                + " fragmentos §de §f" + plugin.dinheiro(preco) + "§d.");
        if (niveis.higherKey(proximo) == null) {
            Bukkit.broadcastMessage("§d§l✦ §f" + jogador.getName() + " §dconseguiu §f" + nome + " " + romano(proximo) + " §dno Cambista!");
        }
        abrir(jogador);
    }

    // ---------------------------------------------------------------------

    private Enchantment encantamento(String chave) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(chave.toLowerCase(Locale.ROOT)));
    }

    /** nível → {fragmentos, preço}, em ordem. */
    private TreeMap<Integer, double[]> niveis(String chave) {
        TreeMap<Integer, double[]> mapa = new TreeMap<>();
        ConfigurationSection s = secao("cambista.encantamentos." + chave + ".niveis");
        if (s == null) return mapa;
        for (String n : s.getKeys(false)) {
            try {
                mapa.put(Integer.parseInt(n), new double[]{s.getInt(n + ".fragmentos"), s.getDouble(n + ".preco")});
            } catch (NumberFormatException ignorado) {
                // nível que não é número: ignora
            }
        }
        return mapa;
    }

    /** O encantamento pode ir nesse item? Pela lista {@code itens} do config, ou pelo jogo sem ela. */
    private boolean serve(String chave, Enchantment ench, ItemStack item) {
        List<String> itens = plugin.getConfig().getStringList("cambista.encantamentos." + chave + ".itens");
        if (itens.isEmpty()) return ench.canEnchantItem(item);
        String material = item.getType().name();
        for (String i : itens) {
            String padrao = i.toUpperCase(Locale.ROOT);
            if (padrao.startsWith("_") ? material.endsWith(padrao) : material.equals(padrao)) return true;
        }
        return false;
    }

    /**
     * A seção do config ou, se o config do servidor é de antes dela existir, a do jar. O
     * getConfigurationSection do Bukkit não cai no padrão: criaria uma seção vazia, e o Cambista
     * ficaria sem nada pra oferecer depois de uma atualização do plugin.
     */
    private ConfigurationSection secao(String caminho) {
        if (plugin.getConfig().contains(caminho, true)) return plugin.getConfig().getConfigurationSection(caminho);
        return plugin.getConfig().getDefaults() == null ? null : plugin.getConfig().getDefaults().getConfigurationSection(caminho);
    }

    private static boolean conflita(ItemStack item, Enchantment ench) {
        for (Enchantment outro : item.getEnchantments().keySet()) {
            if (!outro.equals(ench) && outro.conflictsWith(ench)) return true;
        }
        return false;
    }

    static boolean ehFragmento(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(FRAGMENTO);
    }

    private static int contarFragmentos(Player jogador) {
        int total = 0;
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            if (ehFragmento(item)) total += item.getAmount();
        }
        return total;
    }

    private static void tirarFragmentos(Player jogador, int quantos) {
        ItemStack[] conteudo = jogador.getInventory().getStorageContents();
        for (int i = 0; i < conteudo.length && quantos > 0; i++) {
            if (!ehFragmento(conteudo[i])) continue;
            int tirar = Math.min(quantos, conteudo[i].getAmount());
            conteudo[i].setAmount(conteudo[i].getAmount() - tirar);
            quantos -= tirar;
        }
        jogador.getInventory().setStorageContents(conteudo);
    }

    private double precoComDesconto(Player jogador, double preco) {
        return Math.round(preco * (1 - plugin.descontoVip(jogador) / 100.0) * 100) / 100.0;
    }

    static String romano(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        clique(jogador, menu, evento.getRawSlot());
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }
}
