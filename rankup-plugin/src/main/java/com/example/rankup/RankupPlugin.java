package com.example.rankup;

import com.example.rankup.util.MissoesIntegration;
import com.example.rankup.command.MinaCommand;
import com.example.rankup.command.PrestigioCommand;
import com.example.rankup.command.RanksCommand;
import com.example.rankup.command.RankupCommand;
import com.example.rankup.gui.Hologramas;
import com.example.rankup.gui.Menus;
import com.example.rankup.listener.ColetaMinaListener;
import com.example.rankup.listener.ChatListener;
import com.example.rankup.listener.MinaListener;
import com.example.rankup.manager.MinaManager;
import com.example.rankup.manager.PrestigioManager;
import com.example.rankup.manager.RankManager;
import com.example.rankup.manager.RegistroRankups;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import com.example.rankup.util.LojaIntegration;
import com.example.rankup.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class RankupPlugin extends JavaPlugin {

    private final MissoesIntegration missoes = new MissoesIntegration(this);

    public static final String PERMISSAO_ADMIN = "rankup.admin";

    private Economy economy;
    private RankManager rankManager;
    private MinaManager minaManager;
    private PixIntegration pixIntegration;
    private RegistroRankups registroRankups;
    private LojaIntegration lojaIntegration;
    private PrestigioManager prestigioManager;
    private Menus menus;
    private Hologramas hologramas;
    private boolean avisouFalhaTab;

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
        this.lojaIntegration = new LojaIntegration(this);
        this.prestigioManager = new PrestigioManager(this);
        this.menus = new Menus(this);
        this.hologramas = new Hologramas(this);

        RankupCommand rankup = new RankupCommand(this);
        getCommand("rankup").setExecutor(rankup);
        getCommand("rankup").setTabCompleter(rankup);
        RanksCommand ranks = new RanksCommand(this);
        getCommand("ranks").setExecutor(ranks);
        getCommand("ranks").setTabCompleter(ranks);
        MinaCommand mina = new MinaCommand(this);
        getCommand("mina").setExecutor(mina);
        getCommand("mina").setTabCompleter(mina);
        PrestigioCommand prestigio = new PrestigioCommand(this);
        getCommand("prestigio").setExecutor(prestigio);
        getCommand("prestigio").setTabCompleter(prestigio);
        getServer().getPluginManager().registerEvents(menus, this);

        getServer().getPluginManager().registerEvents(new MinaListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        ColetaMinaListener coleta = new ColetaMinaListener(this);
        getServer().getPluginManager().registerEvents(coleta, this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            minaManager.tick();
            hologramas.tick();
        }, 20L, 20L);
        if (getConfig().getBoolean("minas.resetar-ao-ligar", true)) {
            // 1 tick depois: os mundos já estão carregados
            getServer().getScheduler().runTask(this, () -> minaManager.getMinas().forEach(m -> minaManager.resetar(m, "servidor ligou")));
        }

        getLogger().info("RankupPlugin ativado! " + rankManager.getRanks().size() + " rank(s) e "
                + minaManager.getMinas().size() + " mina(s).");
    }

    @Override
    public void onDisable() {
        if (hologramas != null) hologramas.removerTodos();
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
        if (jogador.getPlayer() != null) atualizarTab(jogador.getPlayer());
        // missão de subir de rank: só quando sobe mesmo (o /ranks admin set e o prestígio podem descer)
        if (rankManager.indice(novo) > rankManager.indice(anterior)) {
            missoes.progresso(jogador.getPlayer(), "SUBIR_RANK", novo.id(), 1);
        }
    }

    public MissoesIntegration getMissoes() {
        return missoes;
    }

    /**
     * Refaz o nome na tab com o rank novo. Com o VipPlugin, quem monta o nome é ele (rank + clã + VIP,
     * chamando o {@link #prefixoTab}); sem ele, o próprio rankup põe o rank.
     */
    public void atualizarTab(Player jogador) {
        if (!getConfig().getBoolean("chat.na-tab", true)) return;
        Plugin vip = getServer().getPluginManager().getPlugin("VipPlugin");
        if (vip != null && vip.isEnabled()) {
            try {
                vip.getClass().getMethod("refrescarTagJogador", Player.class).invoke(vip, jogador);
                return;
            } catch (ReflectiveOperationException e) {
                if (!avisouFalhaTab) {
                    getLogger().warning("Não foi possível pedir ao VipPlugin pra atualizar a tab (versão incompatível?): " + e.getMessage());
                    avisouFalhaTab = true;
                }
            }
        }
        jogador.setPlayerListName(prefixoTab(jogador.getUniqueId()) + "§f" + jogador.getName());
    }

    // ---------------------------------------------------------------------
    // API pra outros plugins (por reflexão)
    // ---------------------------------------------------------------------

    /** Bônus de venda do rank do jogador: 1.25 = vende 25% mais caro. Usado pelo EconomyShopPlugin no /sell. */
    public double multiplicadorVenda(UUID jogador) {
        return rankManager.getRankDe(jogador).multiplicadorVenda() * prestigioManager.multiplicador(jogador);
    }

    /** API pública (VipPlugin, por reflexão): o rank pra tab, com espaço no fim, ou "" se desligado. */
    public String prefixoTab(UUID jogador) {
        return getConfig().getBoolean("chat.na-tab", true) ? tagDoRank(jogador) + " " : "";
    }

    /** Nível de prestígio do jogador (0 = nenhum). */
    public int prestigioDe(UUID jogador) {
        return prestigioManager.nivel(jogador);
    }

    /**
     * Preço do próximo rank do jogador; no último rank, o preço dele mesmo. Usado pelo VipPlugin
     * pro bônus de ativação em dinheiro, proporcional ao rank.
     */
    public double precoProximoRank(UUID jogador) {
        Rank atual = rankManager.getRankDe(jogador);
        Rank proximo = rankManager.getProximo(atual);
        return proximo != null ? proximo.preco() : atual.preco();
    }

    /**
     * Preço do rank em que o jogador está (o que ele pagou pra chegar nele). Usado pelo
     * TerrenosPlugin: o imposto semanal do terreno é uma % desse preço. No primeiro rank é 0.
     */
    public double precoRankAtual(UUID jogador) {
        return rankManager.getRankDe(jogador).preco();
    }

    /** Nome colorido do próximo rank, ou null no último (PlacarPlugin). */
    public String nomeProximoRank(UUID jogador) {
        Rank proximo = rankManager.getProximo(rankManager.getRankDe(jogador));
        return proximo == null ? null : proximo.nomeColorido();
    }

    /** Id do rank do jogador (ex: "fazendeiro"). */
    public String rankDe(UUID jogador) {
        return rankManager.getRankDe(jogador).id();
    }

    /** Tag do rank como aparece no chat, ex: "§8[§aFazendeiro§8]". */
    public String tagDoRank(UUID jogador) {
        int nivel = prestigioManager.nivel(jogador);
        String prestigio = nivel > 0 ? Formato.cores(getConfig().getString("prestigio.formato", "&d✦%nivel% ")).replace("%nivel%", String.valueOf(nivel)) : "";
        return Formato.cores(getConfig().getString("chat.formato", "&8[%rank%&8]"))
                .replace("%rank%", prestigio + rankManager.getRankDe(jogador).nomeColorido());
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

    public LojaIntegration getLojaIntegration() {
        return lojaIntegration;
    }

    public PrestigioManager getPrestigioManager() {
        return prestigioManager;
    }

    public Menus getMenus() {
        return menus;
    }

    public RegistroRankups getRegistroRankups() {
        return registroRankups;
    }
}
