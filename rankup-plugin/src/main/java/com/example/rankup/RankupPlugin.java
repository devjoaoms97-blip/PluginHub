package com.example.rankup;

import com.example.rankup.command.MinaCommand;
import com.example.rankup.command.RanksCommand;
import com.example.rankup.command.RankupCommand;
import com.example.rankup.listener.ChatListener;
import com.example.rankup.listener.MinaListener;
import com.example.rankup.manager.MinaManager;
import com.example.rankup.manager.RankManager;
import com.example.rankup.manager.RegistroRankups;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import com.example.rankup.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class RankupPlugin extends JavaPlugin {

    public static final String PERMISSAO_ADMIN = "rankup.admin";

    private Economy economy;
    private RankManager rankManager;
    private MinaManager minaManager;
    private PixIntegration pixIntegration;
    private RegistroRankups registroRankups;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O RankupPlugin vai continuar ativo, mas ninguém conseguirá subir de rank até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.rankManager = new RankManager(this);
        this.minaManager = new MinaManager(this);
        this.pixIntegration = new PixIntegration(this);
        this.registroRankups = new RegistroRankups(this);

        RankupCommand rankup = new RankupCommand(this);
        getCommand("rankup").setExecutor(rankup);
        getCommand("rankup").setTabCompleter(rankup);
        RanksCommand ranks = new RanksCommand(this);
        getCommand("ranks").setExecutor(ranks);
        getCommand("ranks").setTabCompleter(ranks);
        MinaCommand mina = new MinaCommand(this);
        getCommand("mina").setExecutor(mina);
        getCommand("mina").setTabCompleter(mina);

        getServer().getPluginManager().registerEvents(new MinaListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getServer().getScheduler().runTaskTimer(this, minaManager::tick, 20L, 20L);
        if (getConfig().getBoolean("minas.resetar-ao-ligar", true)) {
            // 1 tick depois: os mundos já estão carregados
            getServer().getScheduler().runTask(this, () -> minaManager.getMinas().forEach(m -> minaManager.resetar(m, "servidor ligou")));
        }

        getLogger().info("RankupPlugin ativado! " + rankManager.getRanks().size() + " rank(s) e "
                + minaManager.getMinas().size() + " mina(s).");
    }

    @Override
    public void onDisable() {
        if (rankManager != null) {
            rankManager.salvar();
        }
        if (minaManager != null) {
            minaManager.salvar();
        }
        getLogger().info("RankupPlugin desativado!");
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
     * Muda o rank e roda os comandos-ao-subir. Usado pelo /rankup e pelo /ranks admin set.
     */
    public void aplicarRank(OfflinePlayer jogador, Rank anterior, Rank novo) {
        rankManager.setRank(jogador.getUniqueId(), novo);
        String nick = jogador.getName() != null ? jogador.getName() : jogador.getUniqueId().toString();
        for (String comando : getConfig().getStringList("comandos-ao-subir")) {
            String pronto = comando.replace("%jogador%", nick).replace("%rank%", novo.id())
                    .replace("%rank_anterior%", anterior.id());
            getServer().dispatchCommand(getServer().getConsoleSender(), pronto.startsWith("/") ? pronto.substring(1) : pronto);
        }
    }

    // ---------------------------------------------------------------------
    // API pra outros plugins (por reflexão)
    // ---------------------------------------------------------------------

    /** Bônus de venda do rank do jogador: 1.25 = vende 25% mais caro. Usado pelo EconomyShopPlugin no /sell. */
    public double multiplicadorVenda(UUID jogador) {
        return rankManager.getRankDe(jogador).multiplicadorVenda();
    }

    /** Id do rank do jogador (ex: "fazendeiro"). */
    public String rankDe(UUID jogador) {
        return rankManager.getRankDe(jogador).id();
    }

    /** Tag do rank como aparece no chat, ex: "§8[§aFazendeiro§8]". */
    public String tagDoRank(UUID jogador) {
        return Formato.cores(getConfig().getString("chat.formato", "&8[%rank%&8]"))
                .replace("%rank%", rankManager.getRankDe(jogador).nomeColorido());
    }

    // ---------------------------------------------------------------------

    public Economy getEconomy() {
        return economy;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public MinaManager getMinaManager() {
        return minaManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    public RegistroRankups getRegistroRankups() {
        return registroRankups;
    }
}
