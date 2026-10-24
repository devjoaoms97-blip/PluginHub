package com.example.spawners;

import org.bukkit.Bukkit;
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
 * O menu do balcão "Spawners" da Loja Oficial ({@code /spawners loja}): um ovo por tipo, com o preço e
 * o rank que libera. Clique compra 1; shift + clique compra 8. O dinheiro sai de circulação.
 */
public class LojaSpawners implements Listener {

    private static class Menu implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final SpawnersPlugin plugin;

    LojaSpawners(SpawnersPlugin plugin) {
        this.plugin = plugin;
    }

    void abrir(Player jogador) {
        Menu menu = new Menu();
        int linhas = Math.max(1, Math.min(6, (plugin.getTipos().size() + 8) / 9));
        menu.inventario = Bukkit.createInventory(menu, linhas * 9, "§8§l🐷 Spawners");
        int slot = 0;
        for (SpawnersPlugin.Tipo tipo : plugin.getTipos().values()) {
            if (slot >= linhas * 9) break;
            boolean libera = tipo.rank() == null || plugin.getRankup().temAcesso(jogador.getUniqueId(), tipo.rank());
            ItemStack icone = new ItemStack(tipo.icone());
            ItemMeta meta = icone.getItemMeta();
            meta.setDisplayName((libera ? "§e" : "§7") + "Spawner de " + tipo.nome());
            List<String> lore = new ArrayList<>();
            lore.add("§7Preço: §a" + SpawnersPlugin.dinheiro(tipo.preco()));
            if (tipo.rank() != null) lore.add((libera ? "§7" : "§c") + "A partir do rank: §f" + tipo.rank());
            lore.add("");
            lore.add("§7Os mobs se juntam numa pilha em cima");
            lore.add("§7do spawner. Mate na espada e venda os");
            lore.add("§7drops pro §4Caçador§7.");
            lore.add("");
            lore.add(libera ? "§eClique: comprar 1 §7| §eShift: comprar 8" : "§cAinda não liberado pro seu rank");
            meta.setLore(lore);
            icone.setItemMeta(meta);
            menu.inventario.setItem(slot, icone);
            menu.slots.put(slot, tipo.id());
            slot++;
        }
        jogador.openInventory(menu.inventario);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador) || evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        SpawnersPlugin.Tipo tipo = plugin.tipo(menu.slots.get(evento.getRawSlot()));
        if (tipo == null) return;
        if (tipo.rank() != null && !plugin.getRankup().temAcesso(jogador.getUniqueId(), tipo.rank())) {
            jogador.sendMessage("§cO Spawner de " + tipo.nome() + " §clibera no rank §f" + tipo.rank() + "§c.");
            return;
        }
        int quantidade = evento.getClick() == ClickType.SHIFT_LEFT || evento.getClick() == ClickType.SHIFT_RIGHT ? 8 : 1;
        double total = tipo.preco() * quantidade;
        var econ = plugin.getEconomy();
        if (econ == null || !econ.has(jogador, total)) {
            jogador.sendMessage("§cSaldo insuficiente: custa §f" + SpawnersPlugin.dinheiro(total) + "§c.");
            return;
        }
        econ.withdrawPlayer(jogador, total);
        plugin.getPix().registrarComServidor(jogador.getUniqueId(), total, false, "Spawner de " + org.bukkit.ChatColor.stripColor(tipo.nome()));
        jogador.getInventory().addItem(plugin.item(tipo, quantidade)).values()
                .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
        jogador.sendMessage("§aVocê comprou §f" + quantidade + "x Spawner de " + tipo.nome() + " §apor §f" + SpawnersPlugin.dinheiro(total)
                + "§a. Coloque no seu terreno (§f/terreno home§a).");
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }
}
