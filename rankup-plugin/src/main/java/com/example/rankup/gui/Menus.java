package com.example.rankup.gui;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Mina;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.Map;
import java.util.UUID;

/**
 * Menus do /ranks e do /mina. Nos ranks, cada item é um rank (preço, bônus de venda, minas,
 * se já foi, é o atual ou está bloqueado); clicar no próximo faz o /rankup, e a estrela abre o
 * prestígio. Nas minas, cada item é uma mina (rank, composição, tempo até o reset, quanto já foi
 * minerado); clicar teleporta, se o rank deixar.
 */
public class Menus implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final RankupPlugin plugin;

    /** O dono do inventário diz que menu é e o que cada slot faz. */
    private static final class Menu implements InventoryHolder {
        private final Map<Integer, String> comandos = new HashMap<>();
        private Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    public Menus(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ativados() {
        return plugin.getConfig().getBoolean("menus.ativado", true);
    }

    // ---------------------------------------------------------------------
    // /ranks
    // ---------------------------------------------------------------------
    public void abrirRanks(Player jogador) {
        List<Rank> ranks = plugin.getRankManager().getRanks();
        Rank meu = plugin.getRankManager().getRankDe(jogador.getUniqueId());
        int meuIndice = plugin.getRankManager().indice(meu);
        Rank proximo = plugin.getRankManager().getProximo(meu);
        Menu menu = new Menu();
        int linhas = Math.min(6, (ranks.size() + 9) / 9 + 1);
        menu.inventario = Bukkit.createInventory(menu, linhas * 9, LEGADO.deserialize("§6§lRanks"));

        for (int i = 0; i < ranks.size() && i < (linhas - 1) * 9; i++) {
            Rank rank = ranks.get(i);
            List<String> lore = new ArrayList<>();
            if (i > 0) lore.add("§7Preço: §f" + Formato.valor(rank.preco()));
            String bonus = Formato.bonus(rank.multiplicadorVenda());
            if (!bonus.isEmpty()) lore.add("§7Venda na loja: §a" + bonus);
            List<String> minas = minasDoRank(rank);
            if (!minas.isEmpty()) lore.add("§7Minas: §f" + String.join(", ", minas));
            lore.add("");
            Material icone;
            if (i < meuIndice) {
                icone = Material.LIME_STAINED_GLASS_PANE;
                lore.add("§a✔ Já conquistado");
            } else if (i == meuIndice) {
                icone = Material.EMERALD;
                lore.add("§e➤ Seu rank");
            } else if (rank == proximo) {
                icone = Material.GOLD_INGOT;
                lore.add(plugin.getEconomy() != null && plugin.getEconomy().has(jogador, rank.preco())
                        ? "§aClique pra subir (/rankup)" : "§cFaltam §f" + Formato.valor(rank.preco()
                        - (plugin.getEconomy() == null ? 0 : plugin.getEconomy().getBalance(jogador))));
                menu.comandos.put(i, "rankup");
            } else {
                icone = Material.GRAY_STAINED_GLASS_PANE;
                lore.add("§8Bloqueado");
            }
            menu.inventario.setItem(i, item(icone, rank.nomeColorido(), lore, i == meuIndice));
        }

        if (plugin.getConfig().getBoolean("prestigio.ativado", true)) {
            int slot = linhas * 9 - 5;
            int nivel = plugin.getPrestigioManager().nivel(jogador.getUniqueId());
            double porNivel = plugin.getConfig().getDouble("prestigio.bonus-venda-percentual", 10);
            List<String> lore = new ArrayList<>(List.of("§7Seu prestígio: §f" + nivel,
                    "§7Bônus de venda: §a+" + Formato.numero(porNivel * nivel) + "%", "",
                    "§7No último rank, volte pro primeiro", "§7e ganhe §a+" + Formato.numero(porNivel) + "% §7de venda pra sempre."));
            if (proximo == null) {
                lore.add("");
                lore.add("§eClique pra ver o prestígio");
                menu.comandos.put(slot, "prestigio");
            }
            menu.inventario.setItem(slot, item(Material.NETHER_STAR, "§d§lPrestígio", lore, false));
        }
        jogador.openInventory(menu.inventario);
    }

    private List<String> minasDoRank(Rank rank) {
        List<String> ids = new ArrayList<>();
        for (Mina mina : plugin.getMinaManager().getMinas()) {
            if (rank.id().equalsIgnoreCase(mina.getRankMinimo())) ids.add(mina.getId());
        }
        return ids;
    }

    // ---------------------------------------------------------------------
    // /mina
    // ---------------------------------------------------------------------
    public void abrirMinas(Player jogador) {
        List<Mina> minas = new ArrayList<>(plugin.getMinaManager().getMinas());
        // na ordem dos ranks
        minas.sort((a, b) -> Integer.compare(indiceDoRank(a), indiceDoRank(b)));
        Menu menu = new Menu();
        int linhas = Math.max(1, Math.min(6, (minas.size() + 8) / 9));
        menu.inventario = Bukkit.createInventory(menu, linhas * 9, LEGADO.deserialize("§6§lMinas"));
        UUID id = jogador.getUniqueId();
        long agora = System.currentTimeMillis();
        for (int i = 0; i < minas.size() && i < linhas * 9; i++) {
            Mina mina = minas.get(i);
            Rank minimo = plugin.getRankManager().getRank(mina.getRankMinimo());
            boolean libera = plugin.getRankManager().temAcesso(id, mina.getRankMinimo());
            List<String> lore = new ArrayList<>();
            lore.add("§7Rank: " + (minimo != null ? minimo.nomeColorido() : "§fqualquer"));
            lore.add("§7Blocos: §f" + composicao(mina));
            if (mina.isResetando()) {
                lore.add("§eResetando agora...");
            } else if (mina.getProximoReset() != Long.MAX_VALUE) {
                lore.add("§7Reset em: §f" + Formato.tempo(mina.getProximoReset() - agora));
            }
            lore.add("§7Minerado: §f" + porcentagemMinerada(mina) + "%");
            lore.add("");
            lore.add(libera ? "§aClique pra ir" : "§cLibera no rank " + (minimo != null ? minimo.nomeColorido() : "?"));
            Material icone = libera ? iconeDaMina(mina) : Material.BARRIER;
            menu.inventario.setItem(i, item(icone, "§6Mina §f" + mina.getId(), lore, false));
            if (libera) menu.comandos.put(i, "mina " + mina.getId());
        }
        jogador.openInventory(menu.inventario);
    }

    private int indiceDoRank(Mina mina) {
        Rank r = plugin.getRankManager().getRank(mina.getRankMinimo());
        return r == null ? -1 : plugin.getRankManager().indice(r);
    }

    /** O bloco que não é pedra e mais pesa na composição, pra ícone (ou o mais pesado). */
    private Material iconeDaMina(Mina mina) {
        return mina.getComposicao().entrySet().stream()
                .sorted((a, b) -> Double.compare(peso(b.getKey(), b.getValue()), peso(a.getKey(), a.getValue())))
                .map(Map.Entry::getKey).filter(Material::isItem).findFirst().orElse(Material.STONE);
    }

    private static double peso(Material m, double peso) {
        String n = m.name();
        boolean enchimento = n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("NETHERRACK") || n.equals("COBBLESTONE");
        return enchimento ? peso / 1000 : peso;
    }

    private static String composicao(Mina mina) {
        double total = mina.getComposicao().values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return "?";
        List<String> partes = new ArrayList<>();
        mina.getComposicao().forEach((m, p) -> partes.add(Formato.nomeMaterial(m) + " " + Math.round(p * 100 / total) + "%"));
        return String.join(", ", partes);
    }

    public static long porcentagemMinerada(Mina mina) {
        return mina.getVolume() <= 0 ? 0 : Math.round(mina.getQuebrados() * 100.0 / mina.getVolume());
    }

    // ---------------------------------------------------------------------

    private static ItemStack item(Material material, String nome, List<String> lore, boolean brilho) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGADO.deserialize(nome).decoration(TextDecoration.ITALIC, false));
        List<Component> linhas = new ArrayList<>();
        for (String l : lore) linhas.add(LEGADO.deserialize(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(linhas);
        if (brilho) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true); // ninguém tira item do menu
        if (!(evento.getWhoClicked() instanceof Player jogador) || evento.getClickedInventory() != evento.getInventory()) return;
        String comando = menu.comandos.get(evento.getRawSlot());
        if (comando == null) return;
        jogador.closeInventory();
        jogador.performCommand(comando);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }
}
