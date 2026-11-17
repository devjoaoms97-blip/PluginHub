package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A loja das fazendas ({@code /fazendas loja}): as Escrituras (por rank) e, embaixo, a Ração e o Adubo.
 */
public class LojaFazendas implements Listener {

    private static class Menu implements InventoryHolder {
        final Map<Integer, String> escrituras = new HashMap<>();
        final Map<Integer, String> insumos = new HashMap<>();
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    /** 12 escrituras: duas fileiras de 6 no meio. */
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24};
    private static final int[] SLOTS_INSUMO = {39, 41};

    private final FazendasPlugin plugin;

    LojaFazendas(FazendasPlugin plugin) {
        this.plugin = plugin;
    }

    void abrir(Player p) {
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 54, "§8§l🌾 Fazendas");
        int i = 0;
        for (FazendasPlugin.Tipo t : plugin.getTipos().values()) {
            if (i >= SLOTS.length) break;
            boolean libera = t.rank() == null || plugin.getRankup().temAcesso(p.getUniqueId(), t.rank());
            FazendasPlugin.Insumo ins = plugin.getInsumos().get(t.insumo());
            double safrasHora = 3600 / t.intervaloSegundos();
            double lucro = safrasHora * (t.rendaPorSafra() - t.insumoPorSafra() * ins.preco());
            ItemStack icone = icone(new ItemStack(t.icone()), (libera ? "" : "§7") + t.nome(),
                    "§7Rende §a" + FazendasPlugin.dinheiro(safrasHora * t.rendaPorSafra()) + "§7/h no cofre",
                    "§7Gasta §f" + FazendasPlugin.fmt(safrasHora * t.insumoPorSafra()) + " " + ChatColor.stripColor(ins.nome()) + "§7/h §8("
                            + FazendasPlugin.dinheiro(safrasHora * t.insumoPorSafra() * ins.preco()) + ")",
                    "§7Lucro: §a" + FazendasPlugin.dinheiro(lucro) + "§7/h §8(se paga em ~" + Math.round(t.preco() / lucro) + " h)",
                    "§7Preço: §a" + FazendasPlugin.dinheiro(t.preco()),
                    t.rank() == null ? "" : (libera ? "§7" : "§c") + "A partir do rank: §f" + t.rank(),
                    "", libera ? "§eClique pra comprar a Escritura" : "§cAinda não liberada pro seu rank");
            menu.inventario.setItem(SLOTS[i], icone);
            menu.escrituras.put(SLOTS[i], t.id());
            i++;
        }
        int j = 0;
        for (FazendasPlugin.Insumo ins : plugin.getInsumos().values()) {
            if (j >= SLOTS_INSUMO.length) break;
            menu.inventario.setItem(SLOTS_INSUMO[j], icone(plugin.insumo(ins, 16), ins.nome(),
                    "§7Preço: §a" + FazendasPlugin.dinheiro(ins.preco()) + " §7cada", "", "§eClique: 16 §7| §eShift: 64"));
            menu.insumos.put(SLOTS_INSUMO[j], ins.id());
            j++;
        }
        p.openInventory(menu.inventario);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player p) || evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        int slot = evento.getRawSlot();
        if (plugin.getEconomy() == null) return;
        if (menu.escrituras.containsKey(slot)) {
            FazendasPlugin.Tipo t = plugin.getTipos().get(menu.escrituras.get(slot));
            if (t.rank() != null && !plugin.getRankup().temAcesso(p.getUniqueId(), t.rank())) {
                p.sendMessage("§cA fazenda " + t.nome() + " §clibera no rank §f" + t.rank() + "§c.");
                return;
            }
            ItemStack escritura = plugin.escritura(t, 0, 0, 0);
            if (!cabe(p, escritura)) {
                p.sendMessage("§cSeu inventário está cheio: libere espaço e tente de novo. Nada foi cobrado.");
                return;
            }
            if (!cobrar(p, t.preco(), "Escritura: " + ChatColor.stripColor(t.nome()))) return;
            p.getInventory().addItem(escritura);
            p.sendMessage("§aVocê comprou a Escritura: " + t.nome() + " §apor §f" + FazendasPlugin.dinheiro(t.preco())
                    + "§a! Clique direito no chão do seu terreno (§f/terreno home§a) pra ver a área.");
        } else if (menu.insumos.containsKey(slot)) {
            FazendasPlugin.Insumo ins = plugin.getInsumos().get(menu.insumos.get(slot));
            int q = evento.getClick() == ClickType.SHIFT_LEFT || evento.getClick() == ClickType.SHIFT_RIGHT ? 64 : 16;
            ItemStack itens = plugin.insumo(ins, q);
            if (!cabe(p, itens)) {
                p.sendMessage("§cSeu inventário está cheio: libere espaço e tente de novo. Nada foi cobrado.");
                return;
            }
            double total = q * ins.preco();
            if (!cobrar(p, total, ChatColor.stripColor(ins.nome()))) return;
            p.getInventory().addItem(itens);
            p.sendMessage("§aVocê comprou §f" + q + " " + ins.nome() + " §apor §f" + FazendasPlugin.dinheiro(total) + "§a.");
        } else {
            return;
        }
        abrir(p);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    private boolean cobrar(Player p, double valor, String tipo) {
        if (!plugin.getEconomy().has(p, valor)) {
            p.sendMessage("§cSaldo insuficiente: custa §f" + FazendasPlugin.dinheiro(valor) + "§c.");
            return false;
        }
        plugin.getEconomy().withdrawPlayer(p, valor);
        plugin.getPix().registrarComServidor(p.getUniqueId(), valor, false, tipo);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
        return true;
    }

    /** O item cabe inteiro no inventário? Confere antes de cobrar. */
    static boolean cabe(Player p, ItemStack item) {
        Inventory copia = Bukkit.createInventory(null, 36);
        copia.setContents(p.getInventory().getStorageContents());
        return copia.addItem(item.clone()).isEmpty();
    }

    private static ItemStack icone(ItemStack base, String nome, String... lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        List<String> linhas = new ArrayList<>();
        for (String l : lore) if (!l.isEmpty() || !linhas.isEmpty()) linhas.add(l);
        meta.setLore(linhas);
        item.setItemMeta(meta);
        return item;
    }
}
