package com.example.barracas.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Contas de inventário do jogador usadas pela loja (só a parte de guardar: sem armadura nem mão secundária). */
public final class Itens {

    private Itens() {
    }

    public static int contar(Player jogador, ItemStack modelo) {
        int total = 0;
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(modelo)) total += item.getAmount();
        }
        return total;
    }

    public static int espacoLivre(Player jogador, ItemStack modelo) {
        int livre = 0;
        int max = modelo.getMaxStackSize();
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) livre += max;
            else if (item.isSimilar(modelo)) livre += Math.max(0, max - item.getAmount());
        }
        return livre;
    }

    public static void remover(Player jogador, ItemStack modelo, int quantidade) {
        ItemStack[] slots = jogador.getInventory().getStorageContents();
        int faltam = quantidade;
        for (int i = 0; i < slots.length && faltam > 0; i++) {
            ItemStack item = slots[i];
            if (item == null || !item.isSimilar(modelo)) continue;
            int tirar = Math.min(faltam, item.getAmount());
            faltam -= tirar;
            item.setAmount(item.getAmount() - tirar);
        }
        jogador.getInventory().setStorageContents(slots);
    }

    /** Entrega no inventário (espaço já conferido); o que sobrar, se sobrar, cai no chão em vez de sumir. */
    public static void entregar(Player jogador, ItemStack modelo, int quantidade) {
        for (ItemStack pilha : pilhas(modelo, quantidade)) {
            jogador.getInventory().addItem(pilha).values()
                    .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
        }
    }

    /** Quebra {@code quantidade} unidades do item em pilhas cheias (e uma pilha com o resto). */
    public static List<ItemStack> pilhas(ItemStack modelo, int quantidade) {
        List<ItemStack> pilhas = new ArrayList<>();
        int faltam = quantidade;
        while (faltam > 0) {
            ItemStack pilha = modelo.clone();
            int qtd = Math.min(faltam, modelo.getMaxStackSize());
            pilha.setAmount(qtd);
            pilhas.add(pilha);
            faltam -= qtd;
        }
        return pilhas;
    }
}
