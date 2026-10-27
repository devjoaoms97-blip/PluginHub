package com.example.oficinas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.List;
import java.util.Map;

/**
 * Compactador: transforma minérios e lingotes do inventário em blocos (9 → 1) e desfaz os blocos,
 * de graça. É o mesmo que a mesa de trabalho faz, só que de uma vez: não cria valor (a loja já foi
 * conferida contra craft) e libera espaço. Só mexe em item comum (sem nome, encantamento ou marca de
 * plugin). Os pares ficam em {@code compactador.pares}.
 */
public class Compactador implements Listener {

    private static final int SLOT_COMPACTAR = 11;
    private static final int SLOT_DESCOMPACTAR = 15;

    record Par(Material item, Material bloco, int quantidade) {}

    static class Menu implements InventoryHolder {
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final OficinasPlugin plugin;

    public Compactador(OficinasPlugin plugin) {
        this.plugin = plugin;
    }

    private List<Par> pares() {
        List<Par> lista = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("compactador.pares")) {
            Material item = Material.matchMaterial(String.valueOf(m.get("item")));
            Material bloco = Material.matchMaterial(String.valueOf(m.get("bloco")));
            int quantidade = m.get("quantidade") instanceof Number n ? n.intValue() : 9;
            if (item != null && bloco != null && quantidade > 1) lista.add(new Par(item, bloco, quantidade));
        }
        return lista;
    }

    public void abrir(Player jogador) {
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 27, "§8▣ Compactador");
        List<String> compacta = new ArrayList<>();
        List<String> descompacta = new ArrayList<>();
        for (Par p : pares()) {
            int itens = contar(jogador, p.item());
            if (itens >= p.quantidade()) compacta.add("§f" + itens / p.quantidade() * p.quantidade() + "x " + nome(p.item()) + " §7→ §f" + itens / p.quantidade() + "x " + nome(p.bloco()));
            int blocos = contar(jogador, p.bloco());
            if (blocos > 0) descompacta.add("§f" + blocos + "x " + nome(p.bloco()) + " §7→ §f" + blocos * p.quantidade() + "x " + nome(p.item()));
        }
        menu.inventario.setItem(SLOT_COMPACTAR, botao(Material.IRON_BLOCK, "§a§lCompactar tudo", compacta, "Nada pra compactar."));
        menu.inventario.setItem(SLOT_DESCOMPACTAR, botao(Material.IRON_INGOT, "§e§lDescompactar tudo", descompacta, "Nenhum bloco pra desfazer."));
        menu.inventario.setItem(22, Servicos.icone(Material.PAPER, "§7De graça", "§7Minérios e lingotes viram blocos (9 → 1) e voltam.",
                "§7Itens com nome ou encantamento ficam como estão."));
        jogador.openInventory(menu.inventario);
    }

    private static ItemStack botao(Material icone, String nome, List<String> linhas, String vazio) {
        List<String> lore = new ArrayList<>(linhas.isEmpty() ? List.of("§7" + vazio) : linhas.subList(0, Math.min(12, linhas.size())));
        if (linhas.size() > 12) lore.add("§7e mais " + (linhas.size() - 12) + "...");
        if (!linhas.isEmpty()) lore.add("§eClique");
        return Servicos.icone(icone, nome, lore.toArray(String[]::new));
    }

    private void compactar(Player jogador, boolean juntar) {
        int feitos = 0;
        for (Par p : pares()) {
            Material de = juntar ? p.item() : p.bloco();
            int tem = contar(jogador, de);
            int trocas = juntar ? tem / p.quantidade() : tem;
            if (trocas <= 0) continue;
            tirar(jogador, de, juntar ? trocas * p.quantidade() : trocas);
            Material para = juntar ? p.bloco() : p.item();
            int recebe = juntar ? trocas : trocas * p.quantidade();
            while (recebe > 0) {
                int pilha = Math.min(recebe, para.getMaxStackSize());
                jogador.getInventory().addItem(new ItemStack(para, pilha)).values()
                        .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
                recebe -= pilha;
            }
            feitos += trocas;
        }
        if (feitos == 0) {
            jogador.sendMessage(juntar ? "§7Nada pra compactar." : "§7Nenhum bloco pra desfazer.");
            return;
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.8f, juntar ? 0.8f : 1.2f);
        jogador.sendMessage(juntar ? "§a▣ Compactado: §f" + feitos + " §abloco(s)." : "§e▣ Descompactado: §f" + feitos + " §ebloco(s).");
        abrir(jogador);
    }

    /** Item comum desse material (sem nome, encantamento ou marca): o resto não entra na troca. */
    private static boolean comum(ItemStack item, Material material) {
        if (item == null || item.getType() != material) return false;
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasEnchants() && meta.getPersistentDataContainer().isEmpty();
    }

    private static int contar(Player jogador, Material material) {
        int total = 0;
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            if (comum(item, material)) total += item.getAmount();
        }
        return total;
    }

    private static void tirar(Player jogador, Material material, int quantos) {
        ItemStack[] conteudo = jogador.getInventory().getStorageContents();
        for (int i = 0; i < conteudo.length && quantos > 0; i++) {
            if (!comum(conteudo[i], material)) continue;
            int tirar = Math.min(quantos, conteudo[i].getAmount());
            conteudo[i].setAmount(conteudo[i].getAmount() - tirar);
            quantos -= tirar;
        }
        jogador.getInventory().setStorageContents(conteudo);
    }

    private static String nome(Material m) {
        return m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        if (evento.getRawSlot() == SLOT_COMPACTAR) compactar(jogador, true);
        else if (evento.getRawSlot() == SLOT_DESCOMPACTAR) compactar(jogador, false);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }
}
