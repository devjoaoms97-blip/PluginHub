package com.example.pix;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PixPlugin extends JavaPlugin {

    private Economy economy = null;
    private ChargeManager chargeManager;

    @Override
    public void onEnable() {
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
}
