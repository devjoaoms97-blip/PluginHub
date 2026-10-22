package com.example.clas;

import com.example.clas.command.ClaCommand;
import com.example.clas.listener.ClaListener;
import com.example.clas.manager.ClaManager;
import com.example.clas.model.Cla;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public class ClasPlugin extends JavaPlugin {

    private Economy economy;
    private ClaManager claManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("Os clãs funcionam, mas criar clã (se tiver custo) e o banco do clã ficam indisponíveis até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.claManager = new ClaManager(this);

        ClaCommand comando = new ClaCommand(this);
        getCommand("cla").setExecutor(comando);
        getCommand("cla").setTabCompleter(comando);
        getCommand("cc").setExecutor(comando);
        getCommand("cc").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new ClaListener(this), this);

        getLogger().info("ClasPlugin ativado! " + claManager.totalClas() + " clã(s) carregado(s).");
    }

    @Override
    public void onDisable() {
        if (claManager != null) {
            claManager.salvar();
        }
        getLogger().info("ClasPlugin desativado!");
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

    /**
     * API pública pra outros plugins (o BountyPlugin chama por reflexão): os dois jogadores
     * estão no mesmo clã? Não mude a assinatura sem atualizar quem usa.
     */
    public boolean mesmoCla(UUID a, UUID b) {
        return claManager != null && claManager.mesmoCla(a, b);
    }

    public Economy getEconomy() {
        return economy;
    }

    public ClaManager getClaManager() {
        return claManager;
    }

    /** "[TAG]" com as cores do config.yml. */
    public String tagFormatada(Cla cla) {
        String formato = getConfig().getString("tag.formato", "&8[&b%tag%&8]");
        return ChatColor.translateAlternateColorCodes('&', formato.replace("%tag%", cla.getTag()));
    }

    public static String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    public static Component legado(String texto) {
        return LegacyComponentSerializer.legacySection().deserialize(texto);
    }
}
