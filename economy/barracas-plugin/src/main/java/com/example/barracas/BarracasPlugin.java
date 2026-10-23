package com.example.barracas;

import com.example.barracas.command.BarracaCommand;
import com.example.barracas.listener.JogadorListener;
import com.example.barracas.listener.LojaListener;
import com.example.barracas.listener.ProtecaoListener;
import com.example.barracas.manager.BarracaManager;
import com.example.barracas.manager.RetiradaManager;
import com.example.barracas.model.Barraca;
import com.example.barracas.util.DiscordIntegration;
import com.example.barracas.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class BarracasPlugin extends JavaPlugin {

    public static final String PERMISSAO_ADMIN = "barracas.admin";

    private Economy economy;
    private RetiradaManager retiradaManager;
    private BarracaManager barracaManager;
    private PixIntegration pixIntegration;
    private DiscordIntegration discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discord = new DiscordIntegration(this);

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O BarracasPlugin vai continuar ativo, mas ninguém conseguirá alugar, dar lance ou comprar até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.retiradaManager = new RetiradaManager(this);
        this.barracaManager = new BarracaManager(this);
        this.pixIntegration = new PixIntegration(this);

        BarracaCommand comando = new BarracaCommand(this);
        getCommand("barraca").setExecutor(comando);
        getCommand("barraca").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new ProtecaoListener(this), this);
        getServer().getPluginManager().registerEvents(new LojaListener(this), this);
        getServer().getPluginManager().registerEvents(new JogadorListener(this), this);

        // Vencimento de aluguel e fim de leilão — a cada 1s, pra leilão terminar na hora certa
        getServer().getScheduler().runTaskTimer(this, barracaManager::tick, 20L, 20L);

        getLogger().info("BarracasPlugin ativado! " + barracaManager.getBarracas().size() + " barraca(s) carregada(s).");
    }

    @Override
    public void onDisable() {
        if (barracaManager != null) {
            barracaManager.salvar();
        }
        if (retiradaManager != null) {
            retiradaManager.salvar();
        }
        getLogger().info("BarracasPlugin desativado!");
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

    public RetiradaManager getRetiradaManager() {
        return retiradaManager;
    }

    public BarracaManager getBarracaManager() {
        return barracaManager;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    // -----------------------------------------------------------------
    // Helpers de config
    // -----------------------------------------------------------------

    /** Preço padrão do aluguel — vale pras barracas sem preço próprio (/barraca preco). */
    public double getPrecoAluguel() {
        return Math.max(0, getConfig().getDouble("aluguel.preco", 500));
    }

    /** Preço do aluguel dessa barraca — e, se for especial, o lance mínimo do leilão dela. */
    public double getPrecoAluguel(Barraca barraca) {
        return barraca.getPreco() != null ? barraca.getPreco() : getPrecoAluguel();
    }

    public long getDuracaoAluguelMs() {
        return Math.max(1, getConfig().getInt("aluguel.duracao-horas", 6)) * 3_600_000L;
    }

    public int getMaxBaus(Barraca barraca) {
        String tipo = barraca.isEspecial() ? "especial" : "normal";
        return Math.max(1, getConfig().getInt("barracas." + tipo + ".max-baus", barraca.isEspecial() ? 8 : 4));
    }

    public double getTaxaVendaPercentual() {
        return Math.max(0, Math.min(100, getConfig().getDouble("vendas.taxa-percentual", 5)));
    }

    public boolean isBau(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL;
    }

    public boolean isAdmin(Player jogador) {
        return jogador.hasPermission(PERMISSAO_ADMIN);
    }

    /**
     * Maior desconto de VIP no aluguel que esse jogador tem, por permissão — mesmo formato
     * (lista de {permissao, percentual}) e mesma lógica do desconto da EconomyShop. Vale pro
     * aluguel pelo preço de tabela, não pros lances de leilão.
     */
    public int calcularDescontoVip(Player jogador) {
        if (!getConfig().getBoolean("vip-descontos.ativado", true)) {
            return 0;
        }

        List<?> permissoes = getConfig().getList("vip-descontos.permissoes");
        if (permissoes == null) {
            return 0;
        }

        int maiorDesconto = 0;
        for (Object item : permissoes) {
            if (!(item instanceof Map<?, ?> mapa)) {
                continue;
            }
            Object permissao = mapa.get("permissao");
            Object percentual = mapa.get("percentual");
            if (permissao == null || percentual == null) {
                continue;
            }
            if (jogador.hasPermission(permissao.toString())) {
                maiorDesconto = Math.max(maiorDesconto, ((Number) percentual).intValue());
            }
        }
        return Math.min(100, maiorDesconto);
    }

    public DiscordIntegration getDiscord() {
        return discord;
    }
}
