package com.example.missoes;

import com.example.missoes.command.MissoesCommand;
import com.example.missoes.listener.ProgressoListener;
import com.example.missoes.manager.MissaoManager;
import com.example.missoes.util.PixIntegration;
import com.example.missoes.util.SkillsIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class MissoesPlugin extends JavaPlugin {

    private Economy economy;
    private MissaoManager missaoManager;
    private PixIntegration pixIntegration;
    private SkillsIntegration skillsIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("As missões continuam contando, mas a recompensa em coins não vai ser paga até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.pixIntegration = new PixIntegration(this);
        this.skillsIntegration = new SkillsIntegration(this);
        this.missaoManager = new MissaoManager(this);

        MissoesCommand comando = new MissoesCommand(this);
        getCommand("missoes").setExecutor(comando);
        getCommand("missoes").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new ProgressoListener(this), this);

        long cincoMin = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this, missaoManager::salvarSeMudou, cincoMin, cincoMin);

        getLogger().info("MissoesPlugin ativado! " + missaoManager.getPool().size() + " missão(ões) no pool.");
    }

    @Override
    public void onDisable() {
        if (missaoManager != null) {
            missaoManager.salvar();
        }
        getLogger().info("MissoesPlugin desativado!");
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

    public MissaoManager getMissaoManager() {
        return missaoManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    public SkillsIntegration getSkillsIntegration() {
        return skillsIntegration;
    }

    public static String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
