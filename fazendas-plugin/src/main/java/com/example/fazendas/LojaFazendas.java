package com.example.fazendas;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
 * A loja das fazendas: o balcão do Corretor Rural na Loja Oficial abre o {@code /fazendas loja} em nome do
 * jogador. As Escrituras (por rank), embaixo a Ração e o Adubo, e a venda dos achados raros.
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
    /** Vende de uma vez todos os achados raros do inventário. */
    private static final int SLOT_VENDER_RAROS = 49;

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
        int raros = 0;
        double total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            FazendasPlugin.Tipo t = plugin.tipoDoRaro(it);
            if (t == null) continue;
            raros += it.getAmount();
            total += it.getAmount() * plugin.valorRaro(t);
        }
        double liquido = total * plugin.multiplicadorColeta(p) * (1 - plugin.getConfig().getDouble("imposto-coleta", 2) / 100.0);
        menu.inventario.setItem(SLOT_VENDER_RAROS, icone(new ItemStack(raros > 0 ? Material.GOLD_BLOCK : Material.GRAY_STAINED_GLASS_PANE),
                "§6§l✦ Vender achados raros",
                "§7Ovo Dourado, Lã de Ouro, Favo Real... cada", "§7fazenda acha o dela, de vez em quando.",
                "", raros > 0 ? "§7Você tem §f" + raros + "§7: §a" + FazendasPlugin.dinheiro(liquido) : "§8Você não tem nenhum",
                raros > 0 ? "§8(com o bônus do rank e do VIP, menos o imposto)" : "",
                raros > 0 ? "§eClique: vender todos" : ""));
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
        } else if (slot == SLOT_VENDER_RAROS) {
            double total = 0;
            int n = 0;
            for (ItemStack i : p.getInventory().getContents()) {
                FazendasPlugin.Tipo t = plugin.tipoDoRaro(i);
                if (t == null) continue;
                total += i.getAmount() * plugin.valorRaro(t);
                n += i.getAmount();
                i.setAmount(0);
            }
            if (n == 0) return;
            double liquido = Math.floor(total * plugin.multiplicadorColeta(p) * (1 - plugin.getConfig().getDouble("imposto-coleta", 2) / 100.0) * 100) / 100;
            plugin.getEconomy().depositPlayer(p, liquido);
            plugin.getPix().registrarComServidor(p.getUniqueId(), liquido, true, "Achados raros de fazenda");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            p.sendMessage("§aVendeu §f" + n + " §aachado" + (n == 1 ? "" : "s") + " raro" + (n == 1 ? "" : "s") + " por §f" + FazendasPlugin.dinheiro(liquido) + "§a.");
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
