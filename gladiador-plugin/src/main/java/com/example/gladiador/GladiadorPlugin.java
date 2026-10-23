package com.example.gladiador;

import com.example.gladiador.command.GladiadorCommand;
import com.example.gladiador.listener.GladiadorListener;
import com.example.gladiador.manager.AgendaManager;
import com.example.gladiador.manager.EventoManager;
import com.example.gladiador.util.ClasIntegration;
import com.example.gladiador.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class GladiadorPlugin extends JavaPlugin {

    public static final String PERMISSAO_ADMIN = "gladiador.admin";

    private Economy economy;
    private EventoManager eventoManager;
    private AgendaManager agendaManager;
    private PixIntegration pixIntegration;
    private ClasIntegration clasIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado! O Gladiador não vai abrir até o Vault ser instalado.");
        }
        this.pixIntegration = new PixIntegration(this);
        this.clasIntegration = new ClasIntegration(this);
        this.eventoManager = new EventoManager(this);
        this.agendaManager = new AgendaManager(this);

        GladiadorCommand comando = new GladiadorCommand(this);
        getCommand("gladiador").setExecutor(comando);
        getCommand("gladiador").setTabCompleter(comando);
        getServer().getPluginManager().registerEvents(new GladiadorListener(this), this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            eventoManager.tick();
            agendaManager.tick();
        }, 20L, 20L);

        if (!eventoManager.getArena().pronta()) {
            getLogger().warning("A arena do Gladiador não está pronta. Marque com /gladiador arena pos1|pos2 e /gladiador arena spawn add.");
        }
    }

    @Override
    public void onDisable() {
        if (eventoManager != null) eventoManager.cancelar("servidor reiniciando");
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

    public EventoManager getEventoManager() {
        return eventoManager;
    }

    public AgendaManager getAgendaManager() {
        return agendaManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    public ClasIntegration getClasIntegration() {
        return clasIntegration;
    }

    public boolean isAdmin(Player jogador) {
        return jogador.hasPermission(PERMISSAO_ADMIN);
    }

    public double inscricaoPadrao() {
        return Math.max(0, getConfig().getDouble("inscricao.valor", 2000));
    }

    /** Está no mundo das arenas (num duelo ou noutro evento). */
    public boolean naArena(Player jogador) {
        return jogador.getWorld().getName().equalsIgnoreCase(getConfig().getString("mundo-arenas", "arenas"));
    }
}
