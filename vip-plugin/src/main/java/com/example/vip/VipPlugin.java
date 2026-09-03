package com.example.vip;

import com.example.vip.command.VipCommand;
import com.example.vip.listener.ChatTagListener;
import com.example.vip.listener.ParticleTrailListener;
import com.example.vip.manager.CodeManager;
import com.example.vip.manager.VipManager;
import com.example.vip.model.VipTier;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public class VipPlugin extends JavaPlugin {

    private VipManager vipManager;
    private CodeManager codeManager;
    private ChatTagListener chatTagListener;
    private ParticleTrailListener particleTrailListener;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("VIPs continuam funcionando normalmente, mas ninguém conseguirá usar /vip vender até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.vipManager = new VipManager(this);
        this.codeManager = new CodeManager(this);

        VipCommand vipCommand = new VipCommand(this);
        getCommand("vip").setExecutor(vipCommand);
        getCommand("vip").setTabCompleter(vipCommand);

        this.chatTagListener = new ChatTagListener(this);
        this.particleTrailListener = new ParticleTrailListener(this);
        Bukkit.getPluginManager().registerEvents(chatTagListener, this);
        Bukkit.getPluginManager().registerEvents(particleTrailListener, this);

        int intervaloMin = getConfig().getInt("verificacao-expiracao-minutos", 5);
        long periodoTicks = 20L * 60 * Math.max(1, intervaloMin);
        Bukkit.getScheduler().runTaskTimer(this, () -> vipManager.verificarExpirados(), periodoTicks, periodoTicks);

        getLogger().info("VipPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (particleTrailListener != null) {
            particleTrailListener.desligar();
        }
        getLogger().info("VipPlugin desativado!");
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

    public VipManager getVipManager() {
        return vipManager;
    }

    public CodeManager getCodeManager() {
        return codeManager;
    }

    public ParticleTrailListener getParticleTrailListener() {
        return particleTrailListener;
    }

    /** Atualiza a tag do jogador na tab list na hora (sem precisar dele relogar). */
    public void refrescarTagJogador(Player player) {
        if (chatTagListener != null) {
            chatTagListener.atualizarTabList(player);
        }
    }

    // -----------------------------------------------------------------
    // Helpers de config por tier
    // -----------------------------------------------------------------

    private String chaveTier(VipTier tier, String campo) {
        return "tiers." + tier.name() + "." + campo;
    }

    public String getGrupoLuckPerms(VipTier tier) {
        return getConfig().getString(chaveTier(tier, "grupo-luckperms"), "vip_" + tier.name().toLowerCase(Locale.ROOT));
    }

    public String getNomeExibicao(VipTier tier) {
        String texto = getConfig().getString(chaveTier(tier, "nome-exibicao"), tier.name());
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    public int getDescontoPercentual(VipTier tier) {
        return getConfig().getInt(chaveTier(tier, "desconto-loja-percentual"), 0);
    }

    public List<String> getKit(VipTier tier) {
        return getConfig().getStringList(chaveTier(tier, "kit"));
    }

    public List<String> getParticulasDisponiveis(VipTier tier) {
        return getConfig().getStringList(chaveTier(tier, "particulas-disponiveis"));
    }

    public int getCooldownKitHoras(VipTier tier) {
        return getConfig().getInt(chaveTier(tier, "cooldown-kit-horas"), 24);
    }

    public int getCooldownHealMinutos(VipTier tier) {
        return getConfig().getInt(chaveTier(tier, "cooldown-heal-minutos"), 30);
    }

    public int getCooldownFeedMinutos(VipTier tier) {
        return getConfig().getInt(chaveTier(tier, "cooldown-feed-minutos"), 15);
    }

    public boolean perkAtivo(String perk) {
        return getConfig().getBoolean("perks." + perk, true);
    }
}
