package com.example.bounty;

import com.example.bounty.command.BountyCommand;
import com.example.bounty.listener.BountyListener;
import com.example.bounty.manager.BountyManager;
import com.example.bounty.util.ClasIntegration;
import com.example.bounty.util.DiscordWebhook;
import com.example.bounty.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class BountyPlugin extends JavaPlugin {

    private Economy economy;
    private BountyManager bountyManager;
    private PixIntegration pixIntegration;
    private ClasIntegration clasIntegration;
    private DiscordWebhook discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O BountyPlugin vai continuar ativo, mas ninguém conseguirá oferecer ou receber recompensa até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.pixIntegration = new PixIntegration(this);
        this.clasIntegration = new ClasIntegration(this);
        this.discord = new DiscordWebhook(this);
        this.bountyManager = new BountyManager(this);

        BountyCommand comando = new BountyCommand(this);
        getCommand("bounty").setExecutor(comando);
        getCommand("bounty").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new BountyListener(this), this);

        // Devolve recompensas vencidas — a cada 10 min (e logo depois de ligar)
        getServer().getScheduler().runTaskTimer(this, bountyManager::verificarExpiradas, 20L * 30, 20L * 60 * 10);

        getLogger().info("BountyPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (bountyManager != null) {
            bountyManager.salvar();
        }
        getLogger().info("BountyPlugin desativado!");
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

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    public ClasIntegration getClasIntegration() {
        return clasIntegration;
    }

    public DiscordWebhook getDiscord() {
        return discord;
    }

    public double getTaxaPercentual() {
        return Math.max(0, Math.min(100, getConfig().getDouble("taxa-percentual", 10)));
    }

    public static String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    /** "3d 4h", "5h 20min", "12min". */
    public static String formatarTempo(long ms) {
        long minutosTotais = Math.max(0, ms / 60_000);
        long dias = minutosTotais / 1440;
        long horas = (minutosTotais % 1440) / 60;
        long minutos = minutosTotais % 60;
        if (dias > 0) return dias + "d " + horas + "h";
        if (horas > 0) return horas + "h " + minutos + "min";
        return minutos + "min";
    }
}
