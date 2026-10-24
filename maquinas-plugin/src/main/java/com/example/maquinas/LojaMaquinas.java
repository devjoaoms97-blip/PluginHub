package com.example.maquinas;

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
import java.util.Locale;
import java.util.Map;

/**
 * O balcão Máquinas da Loja Oficial ({@code /maquinas loja}): em cima as máquinas (por rank), no meio a
 * Célula de Combustível e embaixo os produtos, que o jogador vende (clique vende tudo daquele produto).
 */
public class LojaMaquinas implements Listener {

    private static class Menu implements InventoryHolder {
        final Map<Integer, String> maquinas = new HashMap<>();
        final Map<Integer, String> produtos = new HashMap<>();
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private static final int SLOT_COMBUSTIVEL = 22;

    private final MaquinasPlugin plugin;

    LojaMaquinas(MaquinasPlugin plugin) {
        this.plugin = plugin;
    }

    void abrir(Player p) {
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 54, "§8§l⚙ Máquinas");
        int slot = 2;
        for (MaquinasPlugin.Tipo t : plugin.getTipos().values()) {
            if (slot > 6) break;
            boolean libera = t.rank() == null || plugin.getRankup().temAcesso(p.getUniqueId(), t.rank());
            MaquinasPlugin.Produto prod = plugin.getProdutos().get(t.produto());
            double porHora = 3600 / t.intervaloSegundos();
            menu.inventario.setItem(slot, icone(plugin.maquina(t, 0, 0), (libera ? "§e⚙ " : "§7⚙ ") + t.nome(),
                    "§7Fabrica: §f" + prod.nome() + " §7(" + fmt(porHora) + " por hora)",
                    "§7Gasta §f" + fmt(t.combustivelPorProduto()) + " §7Célula por produto",
                    "§7Preço: §a" + MaquinasPlugin.dinheiro(t.preco()),
                    t.rank() == null ? "" : (libera ? "§7" : "§c") + "A partir do rank: §f" + t.rank(),
                    "", libera ? "§eClique pra comprar" : "§cAinda não liberada pro seu rank"));
            menu.maquinas.put(slot, t.id());
            slot++;
        }
        double precoCel = plugin.getConfig().getDouble("combustivel.preco", 25);
        menu.inventario.setItem(SLOT_COMBUSTIVEL, icone(plugin.combustivel(16), plugin.combustivel(1).getItemMeta().getDisplayName(),
                "§7Preço: §a" + MaquinasPlugin.dinheiro(precoCel) + " §7cada", "", "§eClique: 16 §7| §eShift: 64"));
        slot = 38;
        double mult = plugin.getMercado().multiplicador(p);
        for (MaquinasPlugin.Produto prod : plugin.getProdutos().values()) {
            if (slot > 42) break;
            double unidade = plugin.getMercado().atual(prod) * mult * (1 - plugin.getConfig().getDouble("imposto-venda", 2) / 100.0);
            int tem = contar(p, prod);
            menu.inventario.setItem(slot, icone(plugin.produto(prod, 1), "§f" + prod.nome(),
                    "§7A loja paga §a" + MaquinasPlugin.dinheiro(unidade) + " §7cada §8(varia)",
                    "§7Você tem: §f" + tem, "", tem > 0 ? "§eClique: vender tudo" : "§8Nada pra vender"));
            menu.produtos.put(slot, prod.id());
            slot++;
        }
        p.openInventory(menu.inventario);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player p) || evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        int slot = evento.getRawSlot();
        var econ = plugin.getEconomy();
        if (econ == null) return;
        if (menu.maquinas.containsKey(slot)) {
            MaquinasPlugin.Tipo t = plugin.getTipos().get(menu.maquinas.get(slot));
            if (t.rank() != null && !plugin.getRankup().temAcesso(p.getUniqueId(), t.rank())) {
                p.sendMessage("§cA " + t.nome() + " §clibera no rank §f" + t.rank() + "§c.");
                return;
            }
            if (!cobrar(p, t.preco(), "Máquina: " + ChatColor.stripColor(t.nome()))) return;
            entregar(p, plugin.maquina(t, 0, 0));
            p.sendMessage("§aVocê comprou a " + t.nome() + "§a! Coloque no seu terreno (§f/terreno home§a).");
        } else if (slot == SLOT_COMBUSTIVEL) {
            int q = evento.getClick() == ClickType.SHIFT_LEFT || evento.getClick() == ClickType.SHIFT_RIGHT ? 64 : 16;
            if (!cobrar(p, q * plugin.getConfig().getDouble("combustivel.preco", 25), "Células de Combustível")) return;
            entregar(p, plugin.combustivel(q));
            p.sendMessage("§aVocê comprou §f" + q + " §aCélulas de Combustível.");
        } else if (menu.produtos.containsKey(slot)) {
            MaquinasPlugin.Produto prod = plugin.getProdutos().get(menu.produtos.get(slot));
            int quantidade = remover(p, prod);
            if (quantidade == 0) return;
            double liquido = plugin.getMercado().vender(p, prod, quantidade);
            plugin.getPix().registrarComServidor(p.getUniqueId(), liquido, true, "Venda de " + ChatColor.stripColor(prod.nome()));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
            p.sendMessage("§aVendeu §f" + quantidade + "x " + prod.nome() + " §apor §f" + MaquinasPlugin.dinheiro(liquido) + "§a.");
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
            p.sendMessage("§cSaldo insuficiente: custa §f" + MaquinasPlugin.dinheiro(valor) + "§c.");
            return false;
        }
        plugin.getEconomy().withdrawPlayer(p, valor);
        plugin.getPix().registrarComServidor(p.getUniqueId(), valor, false, tipo);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
        return true;
    }

    private int contar(Player p, MaquinasPlugin.Produto prod) {
        int n = 0;
        for (ItemStack i : p.getInventory().getContents()) if (plugin.produtoDoItem(i) == prod) n += i.getAmount();
        return n;
    }

    private int remover(Player p, MaquinasPlugin.Produto prod) {
        int n = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (plugin.produtoDoItem(i) == prod) {
                n += i.getAmount();
                i.setAmount(0);
            }
        }
        return n;
    }

    private static void entregar(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(s -> p.getWorld().dropItemNaturally(p.getLocation(), s));
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

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}
