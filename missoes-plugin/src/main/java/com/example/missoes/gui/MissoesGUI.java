package com.example.missoes.gui;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.Missao;
import com.example.missoes.model.Periodo;
import com.example.missoes.model.ProgressoJogador;
import com.example.missoes.model.TipoMissao;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Menu do /missoes: só leitura (clicar não faz nada). Diárias em cima, com o bônus de completar
 * todas logo abaixo; semanais embaixo (se estiverem ativadas). Missão concluída brilha.
 */
public class MissoesGUI implements Listener {

    private static final int TAMANHO_BARRA = 20;

    private final MissoesPlugin plugin;

    public MissoesGUI(MissoesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Marca o inventário como sendo do menu de missões. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    public void abrir(Player jogador) {
        boolean semanais = plugin.getMissaoManager().semanaisAtivas()
                && !plugin.getMissaoManager().getPool(Periodo.SEMANAL).isEmpty();
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, semanais ? 45 : 27, "§8§lMissões");
        holder.inventario = inv;

        montarPeriodo(inv, jogador, Periodo.DIARIA, 0);
        if (semanais) {
            montarPeriodo(inv, jogador, Periodo.SEMANAL, 27);
        }
        jogador.openInventory(inv);
    }

    /** Linha de cabeçalho em {@code inicio}, missões na linha de baixo e o bônus na seguinte (só diárias). */
    private void montarPeriodo(Inventory inv, Player jogador, Periodo periodo, int inicio) {
        ProgressoJogador atual = plugin.getMissaoManager().getAtual(jogador.getUniqueId(), periodo);
        boolean diaria = periodo == Periodo.DIARIA;

        Duration falta = Duration.between(LocalDateTime.now(), periodo.proximoInicio(java.time.LocalDate.now()).atStartOfDay());
        String tempo = falta.toDays() > 0
                ? falta.toDays() + "d " + falta.toHoursPart() + "h"
                : falta.toHours() + "h " + falta.toMinutesPart() + "min";
        inv.setItem(inicio + 4, item(diaria ? Material.CLOCK : Material.WRITABLE_BOOK,
                diaria ? "§e§lMissões de hoje" : "§d§lMissões da semana",
                List.of("§7Novas missões em §f" + tempo + "§7.")));

        List<Map.Entry<String, Integer>> lista = new ArrayList<>(atual.getProgresso().entrySet());
        lista.removeIf(e -> plugin.getMissaoManager().getMissao(periodo, e.getKey()) == null); // removida do config
        int quantas = Math.min(7, lista.size());
        int primeiro = inicio + 9 + (9 - quantas) / 2;
        for (int i = 0; i < quantas; i++) {
            Missao missao = plugin.getMissaoManager().getMissao(periodo, lista.get(i).getKey());
            inv.setItem(primeiro + i, itemMissao(missao, lista.get(i).getValue()));
        }
        if (quantas == 0) {
            inv.setItem(inicio + 13, item(Material.BARRIER, "§7Nenhuma missão disponível", List.of()));
        }

        String chave = plugin.getMissaoManager().chaveBonus(periodo);
        double coins = plugin.getConfig().getDouble(chave + ".coins", 0);
        double xp = plugin.getConfig().getDouble(chave + ".xp", 0);
        // o bônus semanal fica no canto da linha do cabeçalho, pra caber em 45 slots
        int slotBonus = diaria ? inicio + 22 : inicio + 8;
        if (coins > 0 || xp > 0) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Complete todas as missões " + (diaria ? "de hoje" : "da semana") + ":");
            lore.add("  " + recompensa(coins, plugin.getConfig().getString(chave + ".skill"), xp));
            lore.add("");
            lore.add(atual.isBonusPago() ? "§a✔ Bônus recebido" : "§7Ainda não recebido");
            ItemStack bonus = item(Material.NETHER_STAR, "§6§l★ Bônus", lore);
            brilhar(bonus, atual.isBonusPago());
            inv.setItem(slotBonus, bonus);
        }
    }

    private ItemStack itemMissao(Missao missao, int atual) {
        boolean feita = atual >= missao.quantidade();
        List<String> lore = new ArrayList<>();
        lore.add(barra(atual, missao.quantidade()) + " §f" + atual + "/" + missao.quantidade());
        lore.add("");
        lore.add("§7Recompensa:");
        lore.add("  " + recompensa(missao.coins(), missao.skill(), missao.xp()));
        lore.add("");
        lore.add(feita ? "§a✔ Concluída" : "§7Em andamento");
        ItemStack item = item(icone(missao.tipo()), (feita ? "§a" : "§e") + missao.descricao(), lore);
        brilhar(item, feita);
        return item;
    }

    private Material icone(TipoMissao tipo) {
        return switch (tipo) {
            case MINERAR -> Material.IRON_PICKAXE;
            case COLHER -> Material.WHEAT;
            case MATAR -> Material.IRON_SWORD;
            case PESCAR -> Material.FISHING_ROD;
            case VENDER -> Material.EMERALD;
            case DANO_BOSS -> Material.DRAGON_HEAD;
        };
    }

    private ItemStack item(Material material, String nome, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values()); // esconde dano de ataque, durabilidade etc.
        item.setItemMeta(meta);
        return item;
    }

    private void brilhar(ItemStack item, boolean brilha) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(brilha);
        item.setItemMeta(meta);
    }

    private String barra(int atual, int total) {
        int cheios = (int) Math.round((double) atual / total * TAMANHO_BARRA);
        return "§a" + "|".repeat(cheios) + "§8" + "|".repeat(TAMANHO_BARRA - cheios);
    }

    public String recompensa(double coins, String skill, double xp) {
        List<String> partes = new ArrayList<>();
        if (coins > 0) partes.add("§f" + MissoesPlugin.formatarValor(coins));
        if (skill != null && xp > 0 && plugin.getSkillsIntegration().disponivel()) {
            partes.add("§b" + (int) xp + " XP de " + skill.toLowerCase(Locale.ROOT));
        }
        return partes.isEmpty() ? "§7sem recompensa" : String.join("§7 + ", partes);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (evento.getInventory().getHolder() instanceof Holder) evento.setCancelled(true);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Holder) evento.setCancelled(true);
    }
}
