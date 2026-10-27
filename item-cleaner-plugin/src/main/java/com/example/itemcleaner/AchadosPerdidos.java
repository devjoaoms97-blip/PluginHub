package com.example.itemcleaner;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Achados e perdidos: o item que a limpeza tiraria do chão e que tem dono (quem jogou, ou quem morreu
 * ali) vai pra caixa desse jogador, em vez de sumir. {@code /achados} abre a caixa, e cada pilha volta
 * pagando {@code achados.taxa-por-pilha} (as coins somem da economia). A caixa guarda até
 * {@code achados.maximo-por-jogador} pilhas por {@code achados.dias} dias; o que passa disso some.
 * <p>
 * Quem morreu: os drops nascem no mesmo tick da morte, perto do corpo, sem "quem jogou". Por isso a
 * morte fica marcada por um instante e o item que nasce ali ganha o morto como dono. Nos mundos de
 * {@code achados.mundos-sem-caixa} (as arenas, onde quem morre perde os itens pro vencedor) nada vai
 * pra caixa: o item só some, como antes.
 */
public class AchadosPerdidos implements Listener, CommandExecutor {

    private record Morte(UUID jogador, Location onde, long tick) {}

    private record Guardado(ItemStack item, long quando) {}

    static class Menu implements InventoryHolder {
        final Map<Integer, Integer> slotParaIndice = new HashMap<>();
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final ItemCleanerPlugin plugin;
    private final PixIntegration pix;
    private final File arquivo;
    private final Map<UUID, List<Guardado>> caixas = new HashMap<>();
    private final List<Morte> mortes = new ArrayList<>();

    public AchadosPerdidos(ItemCleanerPlugin plugin) {
        this.plugin = plugin;
        this.pix = new PixIntegration(plugin);
        this.arquivo = new File(plugin.getDataFolder(), "achados.yml");
        carregar();
    }

    boolean ativado() {
        return plugin.getConfig().getBoolean("achados.ativado", true);
    }

    // ---------------------------------------------------------------------
    // Dono dos drops de morte
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        if (!ativado() || evento.getKeepInventory()) return;
        long tick = Bukkit.getCurrentTick();
        mortes.removeIf(m -> tick - m.tick() > 1);
        mortes.add(new Morte(evento.getEntity().getUniqueId(), evento.getEntity().getLocation(), tick));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoNascerItem(ItemSpawnEvent evento) {
        if (mortes.isEmpty()) return;
        Item item = evento.getEntity();
        if (item.getThrower() != null) return;
        long tick = Bukkit.getCurrentTick();
        for (Morte m : mortes) {
            if (tick - m.tick() <= 1 && m.onde().getWorld().equals(item.getWorld()) && m.onde().distanceSquared(item.getLocation()) <= 9) {
                item.setThrower(m.jogador());
                return;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Limpeza → caixa
    // ---------------------------------------------------------------------

    /** O item vai pra caixa do dono? (com dono, fora dos mundos sem caixa e com a caixa sem lotar) */
    boolean guardar(Item item) {
        if (!ativado()) return false;
        UUID dono = item.getThrower();
        if (dono == null) return false;
        if (plugin.getConfig().getStringList("achados.mundos-sem-caixa").contains(item.getWorld().getName())) return false;
        List<Guardado> caixa = caixas.computeIfAbsent(dono, k -> new ArrayList<>());
        vencer(caixa);
        if (caixa.size() >= plugin.getConfig().getInt("achados.maximo-por-jogador", 45)) return false;
        caixa.add(new Guardado(item.getItemStack().clone(), System.currentTimeMillis()));
        Player online = Bukkit.getPlayer(dono);
        if (online != null && plugin.getConfig().getBoolean("achados.avisar", true)) {
            online.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§e[Limpeza] §fUm item seu foi pros achados e perdidos: §e/achados"));
        }
        return true;
    }

    private void vencer(List<Guardado> caixa) {
        long validade = (long) (plugin.getConfig().getDouble("achados.dias", 3) * 24 * 60 * 60 * 1000);
        long agora = System.currentTimeMillis();
        caixa.removeIf(g -> agora - g.quando() > validade);
    }

    // ---------------------------------------------------------------------
    // /achados
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores.");
            return true;
        }
        if (!ativado()) {
            jogador.sendMessage("§cOs achados e perdidos estão desligados.");
            return true;
        }
        abrir(jogador);
        return true;
    }

    private void abrir(Player jogador) {
        List<Guardado> caixa = caixas.getOrDefault(jogador.getUniqueId(), new ArrayList<>());
        vencer(caixa);
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 54, "§8Achados e perdidos");
        double taxa = plugin.getConfig().getDouble("achados.taxa-por-pilha", 100);
        long validade = (long) (plugin.getConfig().getDouble("achados.dias", 3) * 24 * 60 * 60 * 1000);
        for (int i = 0; i < caixa.size() && i < 45; i++) {
            ItemStack icone = caixa.get(i).item().clone();
            ItemMeta meta = icone.getItemMeta();
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            long resta = validade - (System.currentTimeMillis() - caixa.get(i).quando());
            lore.add("");
            lore.add("§7Recuperar: §a" + dinheiro(taxa));
            lore.add("§7Some em §f" + Math.max(1, resta / 3_600_000) + " h");
            lore.add("§eClique pra recuperar");
            meta.setLore(lore);
            icone.setItemMeta(meta);
            menu.inventario.setItem(i, icone);
            menu.slotParaIndice.put(i, i);
        }
        ItemStack info = new ItemStack(caixa.isEmpty() ? Material.BARRIER : Material.CHEST);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(caixa.isEmpty() ? "§7Nada por aqui" : "§e" + caixa.size() + " item(ns) guardado(s)");
        meta.setLore(List.of("§7O que a limpeza tira do chão e era seu", "§7(jogado por você ou do seu corpo) vem pra cá.",
                "§7Cada pilha volta por §a" + dinheiro(taxa) + "§7."));
        info.setItemMeta(meta);
        menu.inventario.setItem(49, info);
        jogador.openInventory(menu.inventario);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        Integer indice = menu.slotParaIndice.get(evento.getRawSlot());
        List<Guardado> caixa = caixas.get(jogador.getUniqueId());
        if (indice == null || caixa == null || indice >= caixa.size()) return;

        double taxa = plugin.getConfig().getDouble("achados.taxa-por-pilha", 100);
        Economy econ = economia();
        if (taxa > 0) {
            if (econ == null) {
                jogador.sendMessage("§cA economia está indisponível no momento.");
                return;
            }
            if (!econ.has(jogador, taxa)) {
                jogador.sendMessage("§cSaldo insuficiente: recuperar custa §f" + dinheiro(taxa) + "§c.");
                jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                return;
            }
            econ.withdrawPlayer(jogador, taxa);
        }
        Guardado g = caixa.remove((int) indice);
        if (taxa > 0) {
            pix.comEntidade(jogador.getUniqueId(), "Achados e perdidos", taxa, false,
                    "Recuperou " + g.item().getAmount() + "x " + g.item().getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        jogador.getInventory().addItem(g.item()).values()
                .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
        salvar();
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        jogador.sendMessage("§aItem recuperado" + (taxa > 0 ? " por §f" + dinheiro(taxa) : "") + "§a.");
        abrir(jogador);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    // ---------------------------------------------------------------------

    private Economy economia() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    private static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("caixas");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            List<Guardado> caixa = new ArrayList<>();
            for (Map<?, ?> m : secao.getMapList(id)) {
                if (m.get("item") instanceof ItemStack item && m.get("quando") instanceof Number quando) {
                    caixa.add(new Guardado(item, quando.longValue()));
                }
            }
            try {
                caixas.put(UUID.fromString(id), caixa);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Caixa inválida ignorada em achados.yml: " + id);
            }
        }
    }

    void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Guardado>> e : caixas.entrySet()) {
            vencer(e.getValue());
            if (e.getValue().isEmpty()) continue;
            List<Map<String, Object>> lista = new ArrayList<>();
            for (Guardado g : e.getValue()) lista.add(Map.of("item", g.item(), "quando", g.quando()));
            yaml.set("caixas." + e.getKey(), lista);
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar achados.yml", e);
        }
    }
}
