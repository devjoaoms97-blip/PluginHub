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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * O balcão do Bancário (NPC do OficinasPlugin, que chama {@link ChequePlugin#abrirBanco}): vende a
 * folha de cheque em branco, sem a qual ninguém emite cheque, e é o único lugar onde um cheque vira
 * dinheiro: "Depositar cheques" credita todos os cheques do inventário em quem depositou.
 * <p>
 * O balcão também vende os bilhetes da Mine-sena ({@link MineSena}), a loteria diária, e entrega o
 * <b>extrato</b>: um livro com as últimas movimentações de dinheiro do jogador (as que o PixPlugin
 * guarda: Pix, loja, oficinas, rankup, cheques, Mine-sena, boss...).
 * <p>
 * O depósito cobra {@code banco.taxa-deposito-percentual} do valor (igual à taxa do Pix por padrão),
 * senão o cheque viraria um Pix sem taxa. A taxa some da economia. No extrato, a folha e o cheque
 * emitido saem de quem emite, e o depósito entra pra quem deposita (já sem a taxa).
 */
public class Banco implements Listener {

    private static final int SLOT_FOLHA_1 = 10;
    private static final int SLOT_FOLHA_10 = 12;
    private static final int SLOT_DEPOSITAR = 15;
    private static final int SLOT_MINESENA = 13;
    private static final int SLOT_EXTRATO = 16;

    static class Menu implements InventoryHolder {
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final ChequePlugin plugin;

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
        meta.setLore(List.of(ChatColor.GRAY + "Em branco. Com ela na mão:",
                ChatColor.YELLOW + "/cheque <valor>" + ChatColor.GRAY + " (qualquer um deposita)",
                ChatColor.YELLOW + "/cheque <valor> <jogador>" + ChatColor.GRAY + " (só ele)",
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
        if (plugin.getConfig().getBoolean("minesena.ativado", true)) {
            menu.inventario.setItem(SLOT_MINESENA, icone(Material.SUNFLOWER, "§6§l🎟 Mine-sena",
                    "§7Escolha um número de 1 a " + plugin.getConfig().getInt("minesena.numero-maximo", 100) + ".",
                    "§7Pote: §a" + dinheiro(plugin.getMineSena().getPote()),
                    "§7Sorteio todo dia às §f" + plugin.getConfig().getString("minesena.horario", "18:00") + "§7.", "§eClique"));
        }
        menu.inventario.setItem(SLOT_EXTRATO, icone(Material.WRITTEN_BOOK, "§b§lExtrato",
                "§7Suas últimas movimentações de dinheiro:", "§7Pix, loja, oficinas, rankup, cheques,", "§7Mine-sena, boss e o resto.", "§eClique"));
        menu.inventario.setItem(22, icone(Material.WRITABLE_BOOK, "§6Como funciona",
                "§71. Compre uma folha de cheque.", "§72. Com ela na mão: §f/cheque <valor>",
                "§7   (ao portador: qualquer um deposita)", "§7   ou §f/cheque <valor> <jogador>",
                "§7   (nominal: só esse jogador deposita).", "§7   O valor sai do seu saldo.",
                "§73. Quem recebe deposita aqui.",
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
            case SLOT_EXTRATO -> {
                jogador.closeInventory();
                abrirExtrato(jogador);
                return;
            }
            case SLOT_MINESENA -> {
                if (plugin.getConfig().getBoolean("minesena.ativado", true)) plugin.getMineSena().abrir(jogador, 0);
                return;
            }
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
            plugin.getPix().registrar(jogador.getUniqueId(), "Bancário", preco, false, quantas + " folha(s) de cheque");
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
            String deQuem = emissao != null ? Bukkit.getOfflinePlayer(emissao.emissor()).getName() : plugin.origemDoCheque(item);
            plugin.getPix().registrar(jogador.getUniqueId(), deQuem != null ? "Cheque de " + deQuem : "Cheque", valor - taxa, true,
                    "Depósito de cheque" + (taxa > 0 ? " (taxa " + dinheiro(taxa) + ")" : ""));
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

    /**
     * O extrato em livro (aberto na tela, não vai pro inventário): as últimas
     * {@code banco.extrato-transacoes} movimentações, 3 por página, da mais nova pra mais velha.
     */
    private void abrirExtrato(Player jogador) {
        int quantas = Math.max(1, Math.min(100, plugin.getConfig().getInt("banco.extrato-transacoes", 30)));
        List<String[]> linhas = plugin.getPix().extrato(jogador.getUniqueId(), quantas);
        var legado = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        List<net.kyori.adventure.text.Component> paginas = new ArrayList<>();
        Economy econ = plugin.getEconomy();
        StringBuilder capa = new StringBuilder("§1§lExtrato bancário\n§0" + jogador.getName() + "\n\n");
        if (econ != null) capa.append("§0Saldo: §2").append(dinheiro(econ.getBalance(jogador))).append("\n\n");
        if (!plugin.getPix().disponivel()) capa.append("§8O extrato está fora do ar (sem o PixPlugin).");
        else if (linhas.isEmpty()) capa.append("§8Nenhuma movimentação ainda.");
        else capa.append("§8As últimas ").append(linhas.size()).append(" movimentações, da mais nova pra mais velha. Vire a página.");
        paginas.add(legado.deserialize(capa.toString()));
        for (int i = 0; i < linhas.size(); i += 3) {
            StringBuilder pagina = new StringBuilder();
            for (int j = i; j < Math.min(i + 3, linhas.size()); j++) {
                String[] l = linhas.get(j);
                boolean entrou = l[1].equals("+");
                pagina.append("§8").append(l[0]).append("\n")
                        .append(entrou ? "§2+" : "§4-").append(l[2]).append("\n")
                        .append("§0").append(entrou ? "de " : "pra ").append(l[3]).append("\n")
                        .append("§8").append(l[4]).append(l[5].isEmpty() ? "" : " (taxa " + l[5] + ")").append("\n\n");
            }
            paginas.add(legado.deserialize(pagina.toString()));
        }
        jogador.openBook(net.kyori.adventure.inventory.Book.book(legado.deserialize("Extrato"), legado.deserialize("Bancário"), paginas));
        jogador.playSound(jogador.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
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
