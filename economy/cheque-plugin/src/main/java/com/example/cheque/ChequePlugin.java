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
    private NamespacedKey chequeNominalKey;
    private NamespacedKey chequeEmissorKey;
    private NamespacedKey folhaKey;
    private Banco banco;
    private ChequeLog chequeLog;

    @Override
    public void onEnable() {
        this.chequeValueKey = new NamespacedKey(this, "cheque_valor");
        this.chequeIdKey = new NamespacedKey(this, "cheque_id");
        this.chequeNominalKey = new NamespacedKey(this, "cheque_nominal");
        this.chequeEmissorKey = new NamespacedKey(this, "cheque_emissor");
        this.folhaKey = new NamespacedKey(this, "folha");
        saveDefaultConfig();
        this.chequeLog = new ChequeLog(this);

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O plugin vai continuar ativo, mas ninguém conseguirá resgatar cheques até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        getCommand("cheque").setExecutor(new ChequeCommand(this));
        this.banco = new Banco(this);
        getServer().getPluginManager().registerEvents(new ChequeListener(this), this);
        getServer().getPluginManager().registerEvents(banco, this);

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

    public NamespacedKey getChequeNominalKey() {
        return chequeNominalKey;
    }

    public NamespacedKey getChequeEmissorKey() {
        return chequeEmissorKey;
    }

    public NamespacedKey getFolhaKey() {
        return folhaKey;
    }

    public Banco getBanco() {
        return banco;
    }

    /** API pública (OficinasPlugin, NPC Bancário, por reflexão): abre o balcão do banco. */
    public void abrirBanco(org.bukkit.entity.Player jogador) {
        banco.abrir(jogador);
    }

    public ChequeLog getChequeLog() {
        return chequeLog;
    }

    public NamespacedKey getChequeIdKey() {
        return chequeIdKey;
    }
}
