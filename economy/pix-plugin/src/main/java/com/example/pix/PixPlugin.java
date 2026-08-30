package com.example.pix;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PixPlugin extends JavaPlugin {

    private Economy economy = null;
    private ChargeManager chargeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.chargeManager = new ChargeManager(this);

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O PixPlugin vai continuar ativo, mas ninguém conseguirá usar /pix até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        PixCommand executor = new PixCommand(this);
        getCommand("pix").setExecutor(executor);
        getCommand("pix").setTabCompleter(executor);

        getLogger().info("PixPlugin ativado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PixPlugin desativado!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    /**
     * Calcula a taxa (em valor monetário, não percentual) cobrada sobre uma transação de
     * "valor" entre jogadores, de acordo com {@code taxa.percentual-transacao} e
     * {@code taxa.minimo} no config.yml. A taxa nunca ultrapassa o próprio valor da
     * transação (evita zerar ou inverter o pagamento em transações muito pequenas).
     */
    public double calcularTaxaTransacao(double valor) {
        double percentual = getConfig().getDouble("taxa.percentual-transacao", 1.0);
        double minimo = getConfig().getDouble("taxa.minimo", 0.0);

        double taxa = valor * (percentual / 100.0);
        taxa = Math.max(taxa, minimo);
        return Math.min(taxa, valor);
    }
}
