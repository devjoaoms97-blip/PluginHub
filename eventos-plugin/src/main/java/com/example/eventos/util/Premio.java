package com.example.eventos.util;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Paga o prêmio de um evento: um cheque do servidor (ChequePlugin#chequeDoServidor, por reflexão), que
 * só vira dinheiro quando é depositado no Bancário. Sem o ChequePlugin, o valor cai direto na conta (Vault).
 */
public class Premio {

    private final JavaPlugin plugin;

    public Premio(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void pagar(Player jogador, double valor, String origem) {
        if (valor <= 0) return;
        ItemStack cheque = cheque(valor, origem);
        if (cheque != null) {
            jogador.getInventory().addItem(cheque).values().forEach(i -> jogador.getWorld().dropItem(jogador.getLocation(), i));
            jogador.sendMessage("§a[" + origem + "] Seu cheque de §f" + Formato.valor(valor) + " §aestá no inventário. Deposite no Bancário.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            rsp.getProvider().depositPlayer(jogador, valor);
            jogador.sendMessage("§a[" + origem + "] §f" + Formato.valor(valor) + " §acaíram na sua conta.");
        } else {
            plugin.getLogger().warning("Sem ChequePlugin e sem Vault: o prêmio de " + jogador.getName() + " não foi pago.");
        }
    }

    /** Pelo UUID: online, como {@link #pagar(Player, double, String)}; quem saiu do servidor recebe direto na conta (Vault). */
    public void pagar(UUID jogador, double valor, String origem) {
        if (valor <= 0) return;
        Player online = Bukkit.getPlayer(jogador);
        if (online != null) {
            pagar(online, valor, origem);
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) rsp.getProvider().depositPlayer(Bukkit.getOfflinePlayer(jogador), valor);
        else plugin.getLogger().warning("Sem Vault: o prêmio de " + jogador + " (offline) não foi pago.");
    }

    private ItemStack cheque(double valor, String origem) {
        Plugin cheque = Bukkit.getPluginManager().getPlugin("ChequePlugin");
        if (cheque == null || !cheque.isEnabled()) return null;
        try {
            return (ItemStack) cheque.getClass().getMethod("chequeDoServidor", double.class, String.class).invoke(cheque, valor, origem);
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("ChequePlugin sem chequeDoServidor (versão antiga?): " + e.getMessage());
            return null;
        }
    }
}
