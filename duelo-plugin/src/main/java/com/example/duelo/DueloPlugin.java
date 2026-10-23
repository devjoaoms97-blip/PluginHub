package com.example.duelo;

import com.example.duelo.command.DueloCommand;
import com.example.duelo.listener.DueloListener;
import com.example.duelo.manager.DueloManager;
import com.example.duelo.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class DueloPlugin extends JavaPlugin {

    public static final String PERMISSAO_ADMIN = "duelo.admin";

    private Economy economy;
    private DueloManager dueloManager;
    private PixIntegration pixIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado! Só duelos amistosos (sem aposta) vão funcionar.");
        }
        this.dueloManager = new DueloManager(this);
        this.pixIntegration = new PixIntegration(this);

        DueloCommand comando = new DueloCommand(this);
        getCommand("duelo").setExecutor(comando);
        getCommand("duelo").setTabCompleter(comando);
        getServer().getPluginManager().registerEvents(new DueloListener(this), this);
        getServer().getScheduler().runTaskTimer(this, dueloManager::tick, 20L, 20L);

        if (dueloManager.getArenas().values().stream().noneMatch(a -> a.pronta())) {
            getLogger().warning("Nenhuma arena de X1 pronta. Crie com /duelo arena criar <nome> e marque pos1 e pos2.");
        }
    }

    @Override
    public void onDisable() {
        if (dueloManager != null) dueloManager.encerrarTodos();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public DueloManager getDueloManager() {
        return dueloManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    public boolean isAdmin(Player jogador) {
        return jogador.hasPermission(PERMISSAO_ADMIN);
    }

    /** Está no mundo das arenas (num duelo, no Gladiador ou na arena do boss). */
    public boolean naArena(Player jogador) {
        return jogador.getWorld().getName().equalsIgnoreCase(getConfig().getString("mundo-arenas", "arenas"));
    }
}
