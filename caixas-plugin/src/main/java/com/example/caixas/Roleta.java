package com.example.caixas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * A roleta de uma abertura: a linha do meio de um baú de 27 corre da direita pra esquerda, freando, e para
 * com o prêmio (sorteado antes) no meio, entre os dois funis. Fechar antes ou sair do servidor entrega na
 * hora. Nada aqui dentro se pega.
 */
public final class Roleta implements InventoryHolder {

    private static final int CENTRO = 13;

    private final CaixasPlugin plugin;
    private final Player jogador;
    private final Caixa caixa;
    private final Premio premio;
    private final List<ItemStack> fita = new ArrayList<>();
    private final Inventory inv;
    private final int giros;
    private int passo;
    private BukkitTask tarefa;
    private boolean entregue;

    Roleta(CaixasPlugin plugin, Player jogador, Caixa caixa, Premio premio) {
        this.plugin = plugin;
        this.jogador = jogador;
        this.caixa = caixa;
        this.premio = premio;
        this.giros = Math.max(5, plugin.getConfig().getInt("roleta.giros", 32));
        this.inv = Bukkit.createInventory(this, 27, caixa.nome);
        for (int i = 0; i < giros + 9; i++) {
            Premio p = i == giros + 4 ? premio : caixa.sortear();
            fita.add(p.icone(plugin, jogador));
        }
        ItemStack vidro = vidro(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        inv.setItem(4, seta());
        inv.setItem(22, seta());
        desenhar();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    void comecar() {
        jogador.openInventory(inv);
        agendar();
    }

    private void agendar() {
        int ini = Math.max(1, plugin.getConfig().getInt("roleta.intervalo-inicial", 1));
        int fim = Math.max(ini, plugin.getConfig().getInt("roleta.intervalo-final", 7));
        double t = (double) passo / giros;
        long atraso = Math.round(ini + (fim - ini) * t * t);
        tarefa = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entregue) return;
            passo++;
            desenhar();
            jogador.playSound(jogador.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
            if (passo >= giros) terminar();
            else agendar();
        }, atraso);
    }

    private void desenhar() {
        for (int i = 0; i < 9; i++) inv.setItem(9 + i, fita.get(passo + i));
    }

    private void terminar() {
        entregar();
        ItemStack vidro = vidro(plugin.vidroDa(premio.raridade));
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17) inv.setItem(i, vidro);
        }
        inv.setItem(4, seta());
        inv.setItem(22, seta());
    }

    /** Entrega o prêmio (uma vez só): no fim da roleta, ou na hora se o jogador fechar ou sair. */
    void entregar() {
        if (entregue) return;
        entregue = true;
        if (tarefa != null) tarefa.cancel();
        plugin.entregar(jogador, caixa, premio, true);
    }

    boolean terminou() {
        return entregue;
    }

    private static ItemStack vidro(Material m) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(" ");
        i.setItemMeta(meta);
        return i;
    }

    private static ItemStack seta() {
        ItemStack i = new ItemStack(Material.HOPPER);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName("§e▼ Seu prêmio ▼");
        i.setItemMeta(meta);
        return i;
    }

    static int centro() {
        return CENTRO;
    }
}
