package com.example.clas;

import com.example.clas.command.ClaCommand;
import com.example.clas.listener.BossListener;
import com.example.clas.listener.ClaListener;
import com.example.clas.manager.ClaManager;
import com.example.clas.model.Cla;
import com.example.clas.util.PixIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public class ClasPlugin extends JavaPlugin {

    private Economy economy;
    private ClaManager claManager;
    private PixIntegration pixIntegration;
    private boolean avisouFalhaVip;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("Os clãs funcionam, mas criar clã (se tiver custo) e o banco do clã ficam indisponíveis até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.pixIntegration = new PixIntegration(this);
        this.claManager = new ClaManager(this);

        ClaCommand comando = new ClaCommand(this);
        getCommand("cla").setExecutor(comando);
        getCommand("cla").setTabCompleter(comando);
        getCommand("cc").setExecutor(comando);
        getCommand("cc").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new ClaListener(this), this);
        getServer().getPluginManager().registerEvents(new BossListener(this), this);

        // /reload ou plugin ligado com gente online: põe a tag na tab de quem já está no servidor
        Bukkit.getOnlinePlayers().forEach(this::atualizarTab);

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

    /** API pública (BountyPlugin, por reflexão): tag do clã do jogador, ou null se não tem clã. */
    public String tagDoCla(UUID jogador) {
        Cla cla = claManager == null ? null : claManager.getClaDe(jogador);
        return cla == null ? null : cla.getTag();
    }

    /** API pública (BountyPlugin, por reflexão): a tag como foi criada (maiúsculas certas), ou null se não existe. */
    public String tagExistente(String tag) {
        Cla cla = claManager == null ? null : claManager.get(tag);
        return cla == null ? null : cla.getTag();
    }

    /**
     * API pública (VipPlugin, por reflexão): o que vai antes do nome na tab list — a tag do clã
     * com um espaço no fim, ou "" se o jogador não tem clã (ou a tag na tab estiver desligada).
     */
    public String prefixoTab(UUID jogador) {
        if (!getConfig().getBoolean("tag.na-tab", true)) return "";
        Cla cla = claManager == null ? null : claManager.getClaDe(jogador);
        if (cla == null) return "";
        String formato = getConfig().getString("tag.formato-tab", getConfig().getString("tag.formato", "&8[&b%tag%&8]"));
        return ChatColor.translateAlternateColorCodes('&', formato.replace("%tag%", cla.getTag())) + " ";
    }

    /**
     * Atualiza o nome do jogador na tab list. Com o VipPlugin instalado, quem monta o nome é ele
     * (tag do clã + tag do VIP + nome, chamando o {@link #prefixoTab}); aqui só pedimos pra ele
     * refazer. Sem o VIP, o próprio clã põe a tag.
     */
    public void atualizarTab(UUID jogador) {
        Player online = Bukkit.getPlayer(jogador);
        if (online != null) atualizarTab(online);
    }

    public void atualizarTab(Player jogador) {
        Plugin vip = getServer().getPluginManager().getPlugin("VipPlugin");
        if (vip != null && vip.isEnabled()) {
            try {
                vip.getClass().getMethod("refrescarTagJogador", Player.class).invoke(vip, jogador);
                return;
            } catch (ReflectiveOperationException e) {
                if (!avisouFalhaVip) {
                    getLogger().warning("Não foi possível pedir ao VipPlugin pra atualizar a tab (versão incompatível?): " + e.getMessage());
                    avisouFalhaVip = true;
                }
            }
        }
        if (!getConfig().getBoolean("tag.na-tab", true)) return;
        String prefixo = prefixoTab(jogador.getUniqueId());
        jogador.setPlayerListName(prefixo.isEmpty() ? null : prefixo + "§f" + jogador.getName());
    }

    public Economy getEconomy() {
        return economy;
    }

    public ClaManager getClaManager() {
        return claManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    /** Nome do clã como aparece no /pix historico. */
    public static String nomeNoExtrato(Cla cla) {
        return "Clã [" + cla.getTag() + "]";
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
