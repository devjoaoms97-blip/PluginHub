package com.example.cheque;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * O balcão do Bancário (NPC do OficinasPlugin, que chama {@link ChequePlugin#abrirBanco}): vende a
 * folha de cheque em branco, sem a qual ninguém emite cheque, e é o único lugar onde um cheque vira
 * dinheiro: "Depositar cheques" credita todos os cheques do inventário em quem depositou.
 * <p>
 * O depósito cobra {@code banco.taxa-deposito-percentual} do valor (igual à taxa do Pix por padrão),
 * senão o cheque viraria um Pix sem taxa. A taxa some da economia, e o depósito aparece no
 * /pix historico como uma transação do emissor pra quem depositou.
 */
public class Banco implements Listener {

    private static final int SLOT_FOLHA_1 = 10;
    private static final int SLOT_FOLHA_10 = 12;
    private static final int SLOT_DEPOSITAR = 15;

    static class Menu implements InventoryHolder {
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final ChequePlugin plugin;
    private boolean avisouFalhaPix;

    public Banco(ChequePlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------------
    // Folha de cheque
    // ---------------------------------------------------------------------

    public ItemStack folha(int quantidade) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, quantidade));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Folha de cheque");
        meta.setLore(List.of(ChatColor.GRAY + "Em branco. Com ela na mão:", ChatColor.YELLOW + "/cheque <valor> [jogador]",
                ChatColor.GRAY + "O valor sai do seu saldo."));
        meta.getPersistentDataContainer().set(plugin.getFolhaKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean ehFolha(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.getFolhaKey(), PersistentDataType.BYTE);
    }

    public boolean ehCheque(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.getChequeValueKey(), PersistentDataType.DOUBLE);
    }

    // ---------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------

    public void abrir(Player jogador) {
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 27, "§8🏦 Bancário");
        double preco = plugin.getConfig().getDouble("banco.preco-folha", 50);
        double taxa = plugin.getConfig().getDouble("banco.taxa-deposito-percentual", 1.0);
        menu.inventario.setItem(SLOT_FOLHA_1, icone(Material.PAPER, "§fComprar 1 folha de cheque",
                "§7Preço: §a" + dinheiro(preco), "§eClique"));
        menu.inventario.setItem(SLOT_FOLHA_10, icone(Material.BOOK, "§fComprar 10 folhas de cheque",
                "§7Preço: §a" + dinheiro(preco * 10), "§eClique"));
        int cheques = 0;
        double total = 0;
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            if (!ehCheque(item)) continue;
            cheques += item.getAmount();
            Double v = item.getItemMeta().getPersistentDataContainer().get(plugin.getChequeValueKey(), PersistentDataType.DOUBLE);
            if (v != null) total += v * item.getAmount();
        }
        menu.inventario.setItem(SLOT_DEPOSITAR, icone(cheques > 0 ? Material.GOLD_INGOT : Material.BARRIER, "§a§lDepositar cheques",
                cheques > 0 ? "§7" + cheques + " cheque(s) no inventário: §f" + dinheiro(total) : "§7Nenhum cheque no inventário.",
                "§7Taxa de compensação: §f" + String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", taxa),
                "§7Cai na conta de quem deposita.", cheques > 0 ? "§eClique" : ""));
        menu.inventario.setItem(22, icone(Material.WRITABLE_BOOK, "§6Como funciona",
                "§71. Compre uma folha de cheque.", "§72. Com ela na mão: §f/cheque <valor> [jogador]",
                "§7   O valor sai do seu saldo.", "§73. Quem recebe deposita aqui.",
                "§7Errou? §f/cheque cancelar §7com ele na mão devolve."));
        jogador.openInventory(menu.inventario);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        switch (evento.getRawSlot()) {
            case SLOT_FOLHA_1 -> comprarFolhas(jogador, 1);
            case SLOT_FOLHA_10 -> comprarFolhas(jogador, 10);
            case SLOT_DEPOSITAR -> depositar(jogador);
            default -> { return; }
        }
        abrir(jogador);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    private void comprarFolhas(Player jogador, int quantas) {
        Economy econ = plugin.getEconomy();
        double preco = plugin.getConfig().getDouble("banco.preco-folha", 50) * quantas;
        if (preco > 0) {
            if (econ == null || !econ.has(jogador, preco)) {
                jogador.sendMessage(ChatColor.RED + "Saldo insuficiente: " + quantas + " folha(s) custam " + dinheiro(preco) + ".");
                jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                return;
            }
            econ.withdrawPlayer(jogador, preco);
        }
        jogador.getInventory().addItem(folha(quantas)).values()
                .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
        jogador.sendMessage(ChatColor.GREEN + "Você comprou " + quantas + " folha(s) de cheque por " + dinheiro(preco) + ".");
        jogador.playSound(jogador.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    /** Deposita todos os cheques do inventário (os que pode: nominal de outro fica). */
    private void depositar(Player jogador) {
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            jogador.sendMessage(ChatColor.RED + "Não há um plugin de economia (Vault) instalado no servidor.");
            return;
        }
        if (!jogador.hasPermission("cheque.resgatar")) {
            jogador.sendMessage(ChatColor.RED + "Você não tem permissão para depositar cheques.");
            return;
        }
        double taxaPercentual = Math.max(0, plugin.getConfig().getDouble("banco.taxa-deposito-percentual", 1.0));
        ChequeLog log = plugin.getChequeLog();
        double creditado = 0;
        int depositados = 0;
        ItemStack[] conteudo = jogador.getInventory().getStorageContents();
        for (int i = 0; i < conteudo.length; i++) {
            ItemStack item = conteudo[i];
            if (!ehCheque(item)) continue;
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            Double valor = pdc.get(plugin.getChequeValueKey(), PersistentDataType.DOUBLE);
            String id = pdc.get(plugin.getChequeIdKey(), PersistentDataType.STRING);
            String nominal = pdc.get(plugin.getChequeNominalKey(), PersistentDataType.STRING);
            if (valor == null || valor <= 0) {
                conteudo[i] = null;
                continue;
            }
            if (nominal != null && !nominal.equals(jogador.getUniqueId().toString())) {
                jogador.sendMessage(ChatColor.RED + "Um cheque é nominal a "
                        + Bukkit.getOfflinePlayer(UUID.fromString(nominal)).getName() + ": só ele deposita. Ficou com você.");
                continue;
            }
            if (log.cancelado(id) || log.jaResgatado(id)) {
                log.recusado(jogador.getName(), valor, id, log.cancelado(id) ? "cancelado" : "já resgatado");
                conteudo[i] = null;
                jogador.sendMessage(ChatColor.RED + "Um cheque de " + dinheiro(valor) + " estava cancelado ou já tinha sido depositado (cópia). Foi anulado.");
                continue;
            }
            ChequeLog.Emissao emissao = log.emissao(id);
            double taxa = Math.round(valor * taxaPercentual) / 100.0;
            econ.depositPlayer(jogador, valor - taxa);
            log.resgatado(jogador.getName(), valor, id);
            registrarNoPix(emissao, jogador.getUniqueId(), valor, taxa);
            creditado += valor - taxa;
            depositados++;
            // mais de uma unidade com o mesmo ID é cópia: só a primeira vale
            if (item.getAmount() > 1) {
                log.recusado(jogador.getName(), valor, id, "cópia (" + (item.getAmount() - 1) + "x)");
            }
            conteudo[i] = null;
        }
        jogador.getInventory().setStorageContents(conteudo);
        if (depositados == 0) {
            jogador.sendMessage(ChatColor.GRAY + "Nenhum cheque depositado.");
            return;
        }
        jogador.sendMessage(ChatColor.GREEN + "Depositado: " + depositados + " cheque(s), " + ChatColor.GOLD + dinheiro(creditado)
                + ChatColor.GREEN + " na sua conta" + (taxaPercentual > 0 ? ChatColor.GRAY + " (já sem a taxa de compensação)" : "") + ChatColor.GREEN + ".");
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    /** O depósito no /pix historico: do emissor pra quem depositou (só os cheques com emissor conhecido). */
    private void registrarNoPix(ChequeLog.Emissao emissao, UUID depositante, double valor, double taxa) {
        if (emissao == null) return;
        Plugin pix = Bukkit.getPluginManager().getPlugin("PixPlugin");
        if (pix == null || !pix.isEnabled()) return;
        try {
            Object charges = pix.getClass().getMethod("getChargeManager").invoke(pix);
            charges.getClass().getMethod("registrarTransacao", UUID.class, UUID.class, double.class, String.class, double.class)
                    .invoke(charges, emissao.emissor(), depositante, valor, "Cheque", taxa);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalhaPix) {
                plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível registrar o cheque no histórico: " + e.getMessage());
                avisouFalhaPix = true;
            }
        }
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

    private static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }
}
