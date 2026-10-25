package com.example.oficinas;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * O que cada NPC faz ao ser clicado:
 * <ul>
 *   <li><b>Ferreiro</b>: menu com os itens gastos do jogador e o preço do reparo de cada um
 *       (proporcional ao desgaste, pelo material, mais um tanto por nível de encantamento).</li>
 *   <li><b>Bruxa</b>: abre a mesa de encantamento escondida embaixo da barraca dela, cercada por
 *       estantes: sempre o poder máximo (nível 30). O lápis é por conta dela (o espaço vem cheio e
 *       travado); o XP é do jogador, como no vanilla.</li>
 *   <li><b>Picareteiro</b>: vende as ferramentas da lista do config, com os encantamentos.</li>
 *   <li><b>Sábio</b>: vende níveis de XP (pelo custo em pontos, igual ao vanilla).</li>
 * </ul>
 * Todos aplicam o desconto de VIP ({@code vip-descontos}) e registram no /pix historico.
 */
public class Servicos implements Listener {

    /** Menu aberto por um NPC: o tipo e o que cada slot faz. */
    static class Menu implements InventoryHolder {
        final Oficina.Tipo tipo;
        final Map<Integer, Integer> slotParaInventario = new HashMap<>(); // ferreiro: slot do menu -> slot do jogador
        final Map<Integer, Integer> slotParaIndice = new HashMap<>();     // picareteiro/sábio: slot -> item da lista
        Inventory inventario;

        Menu(Oficina.Tipo tipo) {
            this.tipo = tipo;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    /** As mesas abertas pela bruxa: o espaço do lápis fica cheio e travado, e some quando fecha. */
    private final java.util.Set<org.bukkit.inventory.Inventory> mesasDaBruxa = new java.util.HashSet<>();

    private static final int SLOT_TUDO = 49;
    private static final int SLOT_FECHAR = 53;

    private final OficinasPlugin plugin;

    public Servicos(OficinasPlugin plugin) {
        this.plugin = plugin;
    }

    public void abrir(Player jogador, Oficina.Tipo tipo) {
        switch (tipo) {
            case FERREIRO -> abrirFerreiro(jogador);
            case BRUXA -> abrirBruxa(jogador);
            case PICARETEIRO -> abrirLista(jogador, Oficina.Tipo.PICARETEIRO);
            case ARMEIRO -> abrirLista(jogador, Oficina.Tipo.ARMEIRO);
            case SABIO -> abrirSabio(jogador);
        }
    }

    // ---------------------------------------------------------------------
    // Ferreiro
    // ---------------------------------------------------------------------

    private void abrirFerreiro(Player jogador) {
        Menu menu = new Menu(Oficina.Tipo.FERREIRO);
        menu.inventario = Bukkit.createInventory(menu, 54, "§8⚒ Ferreiro — reparos");
        double total = 0;
        int slot = 0;
        ItemStack[] conteudo = jogador.getInventory().getContents();
        for (int i = 0; i < conteudo.length && slot < 45; i++) {
            double preco = precoReparo(jogador, conteudo[i]);
            if (preco <= 0) continue;
            ItemStack icone = conteudo[i].clone();
            ItemMeta meta = icone.getItemMeta();
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            Damageable d = (Damageable) meta;
            lore.add("");
            lore.add("§7Desgaste: §f" + d.getDamage() + "/" + maxDurabilidade(conteudo[i]));
            lore.add("§7Reparo: §a" + plugin.dinheiro(preco));
            lore.add("§eClique pra reparar");
            meta.setLore(lore);
            icone.setItemMeta(meta);
            menu.inventario.setItem(slot, icone);
            menu.slotParaInventario.put(slot, i);
            slot++;
            total += preco;
        }
        if (slot == 0) {
            menu.inventario.setItem(22, icone(Material.ANVIL, "§aNada pra reparar", "§7Nenhum item seu está gasto."));
        } else {
            menu.inventario.setItem(SLOT_TUDO, icone(Material.ANVIL, "§a§lReparar tudo",
                    "§7" + slot + " item(ns) por §a" + plugin.dinheiro(total), descontoLinha(jogador), "§eClique"));
        }
        menu.inventario.setItem(SLOT_FECHAR, icone(Material.BARRIER, "§cFechar"));
        jogador.openInventory(menu.inventario);
    }

    /** Preço do reparo de {@code item} pra esse jogador (0 = não dá ou não precisa). */
    double precoReparo(Player jogador, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        int max = maxDurabilidade(item);
        if (max <= 0 || !(item.getItemMeta() instanceof Damageable d) || d.getDamage() <= 0 || item.getItemMeta().isUnbreakable()) return 0;
        double cheio = precoCheio(item.getType());
        int niveis = item.getEnchantments().values().stream().mapToInt(Integer::intValue).sum();
        double porNivel = plugin.getConfig().getDouble("ferreiro.acrescimo-por-nivel-encantamento", 12) / 100.0;
        double preco = cheio * ((double) d.getDamage() / max) * (1 + porNivel * niveis) * item.getAmount();
        preco *= 1 - plugin.descontoVip(jogador) / 100.0;
        return Math.max(plugin.getConfig().getDouble("ferreiro.minimo", 1), Math.round(preco * 100) / 100.0);
    }

    private static int maxDurabilidade(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable d && d.hasMaxDamage()) return d.getMaxDamage();
        return item.getType().getMaxDurability();
    }

    /** Preço de reparar do zero ao novo, pelo material (a primeira chave do config que aparece no nome). */
    private double precoCheio(Material material) {
        ConfigurationSection precos = plugin.getConfig().getConfigurationSection("ferreiro.preco-cheio");
        if (precos != null) {
            for (String chave : precos.getKeys(false)) {
                if (!chave.equalsIgnoreCase("outros") && material.name().contains(chave.toUpperCase(Locale.ROOT))) {
                    return precos.getDouble(chave);
                }
            }
            return precos.getDouble("outros", 200);
        }
        return 200;
    }

    private void cliqueFerreiro(Player jogador, Menu menu, int slot) {
        List<Integer> alvos = new ArrayList<>();
        if (slot == SLOT_TUDO) alvos.addAll(menu.slotParaInventario.values());
        else if (menu.slotParaInventario.containsKey(slot)) alvos.add(menu.slotParaInventario.get(slot));
        if (alvos.isEmpty()) return;

        double total = 0;
        for (int i : alvos) total += precoReparo(jogador, jogador.getInventory().getItem(i));
        if (total <= 0) {
            abrirFerreiro(jogador);
            return;
        }
        if (!cobrar(jogador, total, "Reparo no Ferreiro")) return;
        int reparados = 0;
        for (int i : alvos) {
            ItemStack item = jogador.getInventory().getItem(i);
            if (precoReparo(jogador, item) <= 0) continue;
            ItemMeta meta = item.getItemMeta();
            ((Damageable) meta).setDamage(0);
            item.setItemMeta(meta);
            jogador.getInventory().setItem(i, item);
            reparados++;
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        jogador.sendMessage("§a⚒ O ferreiro reparou §f" + reparados + " §aitem(ns) por §f" + plugin.dinheiro(total) + "§a.");
        abrirFerreiro(jogador);
    }

    // ---------------------------------------------------------------------
    // Bruxa
    // ---------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void abrirBruxa(Player jogador) {
        String mundo = plugin.getConfig().getString("bruxa.mesa.mundo", "");
        World w = Bukkit.getWorld(mundo);
        if (w == null) {
            jogador.sendMessage("§cA bruxa está sem o caldeirão (a mesa não foi configurada). Avise a staff.");
            return;
        }
        double taxa = plugin.getConfig().getDouble("bruxa.taxa", 0) * (1 - plugin.descontoVip(jogador) / 100.0);
        if (taxa > 0 && !cobrar(jogador, taxa, "Bruxa (encantamento)")) return;
        Location mesa = new Location(w, plugin.getConfig().getInt("bruxa.mesa.x"), plugin.getConfig().getInt("bruxa.mesa.y"),
                plugin.getConfig().getInt("bruxa.mesa.z"));
        // force: a mesa fica embaixo do chão, cercada pelas 15 estantes; o menu calcula o poder por elas
        org.bukkit.inventory.InventoryView view = jogador.openEnchanting(mesa, true);
        if (view != null && view.getTopInventory() instanceof org.bukkit.inventory.EnchantingInventory mesaAberta) {
            mesaAberta.setSecondary(new ItemStack(Material.LAPIS_LAZULI, 64));
            mesasDaBruxa.add(mesaAberta);
        }
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 0.8f, 1f);
        if (taxa > 0) jogador.sendMessage("§5✦ A bruxa cobrou §f" + plugin.dinheiro(taxa) + " §5pelo grimório.");
    }

    // ---------------------------------------------------------------------
    // Picareteiro e Sábio: listas de produtos
    // ---------------------------------------------------------------------

    /** A lista do config de cada vendedor de itens prontos ("picareteiro.itens", "armeiro.itens"). */
    private static String chaveLista(Oficina.Tipo tipo) {
        return tipo.chave() + ".itens";
    }

    private static String titulo(Oficina.Tipo tipo) {
        return tipo == Oficina.Tipo.ARMEIRO ? "§8⚔ Armeiro" : "§8⛏ Picareteiro";
    }

    /** Picareteiro e Armeiro: a lista do config, um item por slot; clique compra. */
    private void abrirLista(Player jogador, Oficina.Tipo tipo) {
        Menu menu = new Menu(tipo);
        List<Map<?, ?>> itens = plugin.getConfig().getMapList(chaveLista(tipo));
        int linhas = Math.max(1, Math.min(6, (itens.size() + 8) / 9));
        menu.inventario = Bukkit.createInventory(menu, linhas * 9, titulo(tipo));
        for (int i = 0; i < itens.size() && i < linhas * 9; i++) {
            ItemStack produto = produto(itens.get(i));
            if (produto == null) continue;
            double preco = precoComDesconto(jogador, numero(itens.get(i).get("preco")));
            ItemStack ic = produto.clone();
            ItemMeta meta = ic.getItemMeta();
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Preço: §a" + (preco <= 0 ? "Grátis" : plugin.dinheiro(preco)));
            String desc = preco <= 0 ? "" : descontoLinha(jogador);
            if (!desc.isEmpty()) lore.add(desc);
            lore.add(preco <= 0 ? "§eClique pra pegar" : "§eClique pra comprar");
            meta.setLore(lore);
            ic.setItemMeta(meta);
            menu.inventario.setItem(i, ic);
            menu.slotParaIndice.put(i, i);
        }
        jogador.openInventory(menu.inventario);
    }

    private void cliqueLista(Player jogador, Menu menu, int slot) {
        Integer indice = menu.slotParaIndice.get(slot);
        if (indice == null) return;
        Map<?, ?> dados = plugin.getConfig().getMapList(chaveLista(menu.tipo)).get(indice);
        ItemStack produto = produto(dados);
        if (produto == null) return;
        double preco = precoComDesconto(jogador, numero(dados.get("preco")));
        String quem = menu.tipo == Oficina.Tipo.ARMEIRO ? "Armeiro" : "Picareteiro";
        if (!cobrar(jogador, preco, "Compra no " + quem)) return;
        entregar(jogador, produto);
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
        jogador.sendMessage((menu.tipo == Oficina.Tipo.ARMEIRO ? "§a⚔ " : "§a⛏ ") + "Você comprou §f" + nome(produto)
                + (preco <= 0 ? " §ade graça." : " §apor §f" + plugin.dinheiro(preco) + "§a."));
    }

    /** Monta o item de uma entrada do config: material, quantidade, nome, encantamentos e poção. */
    private ItemStack produto(Map<?, ?> dados) {
        Material material = Material.matchMaterial(String.valueOf(dados.get("material")));
        if (material == null || material.isAir()) return null;
        int quantidade = dados.get("quantidade") instanceof Number n ? n.intValue() : 1;
        ItemStack item = new ItemStack(material, Math.max(1, quantidade));
        ItemMeta meta = item.getItemMeta();
        if (dados.get("nome") instanceof String nome) meta.setDisplayName(plugin.cores(nome));
        // poção (normal, de arremesso ou persistente) e flecha com efeito: o tipo do Minecraft
        if (dados.get("pocao") instanceof String pocao && meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            org.bukkit.potion.PotionType tipo = Registry.POTION.get(NamespacedKey.minecraft(pocao.toLowerCase(Locale.ROOT)));
            if (tipo != null) pm.setBasePotionType(tipo);
        }
        if (dados.get("encantamentos") instanceof Map<?, ?> encs) {
            for (Map.Entry<?, ?> e : encs.entrySet()) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT)));
                if (ench != null && e.getValue() instanceof Number nivel) meta.addEnchant(ench, nivel.intValue(), true);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private void abrirSabio(Player jogador) {
        Menu menu = new Menu(Oficina.Tipo.SABIO);
        menu.inventario = Bukkit.createInventory(menu, 27, "§8✦ Sábio — XP");
        int nivel = jogador.getLevel();
        int maximo = plugin.getConfig().getInt("sabio.nivel-maximo", 50);
        List<Integer> pacotes = plugin.getConfig().getIntegerList("sabio.pacotes-niveis");
        for (int i = 0; i < pacotes.size() && i < 9; i++) {
            int n = pacotes.get(i);
            int slot = 10 + i;
            if (nivel + n > maximo) {
                menu.inventario.setItem(slot, icone(Material.GLASS_BOTTLE, "§7+" + n + " níveis",
                        "§cPassaria do nível " + maximo + "."));
                continue;
            }
            double preco = precoXp(jogador, n);
            menu.inventario.setItem(slot, icone(Material.EXPERIENCE_BOTTLE, "§a+" + n + " níveis de XP",
                    "§7Do nível §f" + nivel + " §7pro §f" + (nivel + n), "§7Preço: §a" + plugin.dinheiro(preco), descontoLinha(jogador),
                    "§eClique pra comprar"));
            menu.slotParaIndice.put(slot, i);
        }
        menu.inventario.setItem(4, icone(Material.ENCHANTED_BOOK, "§d✦ O Sábio",
                "§7Seu nível agora: §f" + nivel, "§7Encante na §5Bruxa§7, ao lado."));
        jogador.openInventory(menu.inventario);
    }

    private void cliqueSabio(Player jogador, Menu menu, int slot) {
        Integer indice = menu.slotParaIndice.get(slot);
        if (indice == null) return;
        int n = plugin.getConfig().getIntegerList("sabio.pacotes-niveis").get(indice);
        if (jogador.getLevel() + n > plugin.getConfig().getInt("sabio.nivel-maximo", 50)) return;
        double preco = precoXp(jogador, n);
        if (!cobrar(jogador, preco, "XP no Sábio")) return;
        jogador.giveExpLevels(n);
        jogador.sendMessage("§a✦ Você comprou §f" + n + " níveis §apor §f" + plugin.dinheiro(preco) + "§a. Agora está no nível §f"
                + jogador.getLevel() + "§a.");
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        abrirSabio(jogador);
    }

    /** Preço de subir {@code n} níveis a partir do nível atual: os pontos de XP do vanilla vezes o preço do ponto. */
    private double precoXp(Player jogador, int n) {
        int nivel = jogador.getLevel();
        double pontos = pontosAte(nivel + n) - pontosAte(nivel);
        return precoComDesconto(jogador, pontos * plugin.getConfig().getDouble("sabio.preco-por-ponto", 2.0));
    }

    /** Pontos de XP totais pra chegar no nível {@code n} (fórmula do Minecraft). */
    static double pontosAte(int n) {
        if (n <= 16) return n * n + 6.0 * n;
        if (n <= 31) return 2.5 * n * n - 40.5 * n + 360;
        return 4.5 * n * n - 162.5 * n + 2220;
    }

    // ---------------------------------------------------------------------
    // Cliques e utilidades
    // ---------------------------------------------------------------------

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        int slot = evento.getRawSlot();
        if (slot == SLOT_FECHAR && menu.tipo == Oficina.Tipo.FERREIRO) {
            jogador.closeInventory();
            return;
        }
        switch (menu.tipo) {
            case FERREIRO -> cliqueFerreiro(jogador, menu, slot);
            case PICARETEIRO, ARMEIRO -> cliqueLista(jogador, menu, slot);
            case SABIO -> cliqueSabio(jogador, menu, slot);
            default -> { }
        }
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
        if (mesasDaBruxa.contains(evento.getInventory()) && evento.getRawSlots().contains(1)) evento.setCancelled(true);
    }

    /** Ninguém tira (nem troca) o lápis da mesa da bruxa. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void aoClicarNaMesa(InventoryClickEvent evento) {
        if (!mesasDaBruxa.contains(evento.getInventory())) return;
        boolean noLapis = evento.getRawSlot() == 1;
        boolean juntando = evento.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                && evento.getCursor() != null && evento.getCursor().getType() == Material.LAPIS_LAZULI;
        if (noLapis || juntando) evento.setCancelled(true);
    }

    /** Depois de encantar, o lápis gasto volta. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void aoEncantar(org.bukkit.event.enchantment.EnchantItemEvent evento) {
        if (!(evento.getInventory() instanceof org.bukkit.inventory.EnchantingInventory mesa) || !mesasDaBruxa.contains(mesa)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mesasDaBruxa.contains(mesa)) mesa.setSecondary(new ItemStack(Material.LAPIS_LAZULI, 64));
        });
    }

    /** Ao fechar, o lápis some antes do jogo devolver o que estava na mesa pro inventário. */
    @EventHandler
    public void aoFechar(org.bukkit.event.inventory.InventoryCloseEvent evento) {
        if (mesasDaBruxa.remove(evento.getInventory()) && evento.getInventory() instanceof org.bukkit.inventory.EnchantingInventory mesa) {
            mesa.setSecondary(null);
        }
    }

    private boolean cobrar(Player jogador, double valor, String tipo) {
        if (valor <= 0) return true; // de graça (as ferramentas de pedra pra quem está começando): nada no extrato
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            jogador.sendMessage("§cA economia está indisponível no momento.");
            return false;
        }
        if (!econ.has(jogador, valor)) {
            jogador.sendMessage("§cSaldo insuficiente: custa §f" + plugin.dinheiro(valor) + "§c, você tem §f"
                    + plugin.dinheiro(econ.getBalance(jogador)) + "§c.");
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return false;
        }
        econ.withdrawPlayer(jogador, valor);
        plugin.getPix().registrarComServidor(jogador.getUniqueId(), valor, false, tipo);
        return true;
    }

    private static void entregar(Player jogador, ItemStack item) {
        jogador.getInventory().addItem(item).values()
                .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
    }

    private double precoComDesconto(Player jogador, double preco) {
        return Math.round(preco * (1 - plugin.descontoVip(jogador) / 100.0) * 100) / 100.0;
    }

    private String descontoLinha(Player jogador) {
        double d = plugin.descontoVip(jogador);
        return d > 0 ? "§6(" + String.format(Locale.ROOT, "%.0f", d) + "% de desconto VIP)" : "";
    }

    private static double numero(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static String nome(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static ItemStack icone(Material material, String nome, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        List<String> linhas = new ArrayList<>();
        for (String l : lore) if (!l.isEmpty()) linhas.add(l);
        meta.setLore(linhas);
        item.setItemMeta(meta);
        return item;
    }
}
