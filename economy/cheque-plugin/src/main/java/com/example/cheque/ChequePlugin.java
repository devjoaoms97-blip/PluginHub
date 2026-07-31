package com.example.cheque;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class ChequePlugin extends JavaPlugin {

    private Economy economy = null;

    // Chaves usadas para gravar dados no item (PersistentDataContainer)
    private NamespacedKey chequeValueKey;
    private NamespacedKey chequeIdKey;

    @Override
    public void onEnable() {
        this.chequeValueKey = new NamespacedKey(this, "cheque_valor");
        this.chequeIdKey = new NamespacedKey(this, "cheque_id");

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O plugin vai continuar ativo, mas ninguém conseguirá resgatar cheques até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        getCommand("cheque").setExecutor(new ChequeCommand(this));
        getServer().getPluginManager().registerEvents(new ChequeListener(this), this);

        getLogger().info("ChequePlugin ativado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChequePlugin desativado!");
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

    public NamespacedKey getChequeValueKey() {
        return chequeValueKey;
    }

    public NamespacedKey getChequeIdKey() {
        return chequeIdKey;
    }
}
