package com.example.missoes;

import com.example.missoes.command.MissoesCommand;
import com.example.missoes.gui.MissoesGUI;
import com.example.missoes.listener.ProgressoListener;
import com.example.missoes.manager.MissaoManager;
import com.example.missoes.model.Periodo;
import com.example.missoes.model.TipoMissao;
import com.example.missoes.util.PixIntegration;
import com.example.missoes.util.SkillsIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class MissoesPlugin extends JavaPlugin {

    private Economy economy;
    private MissaoManager missaoManager;
    private PixIntegration pixIntegration;
    private SkillsIntegration skillsIntegration;
    private MissoesGUI missoesGUI;

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
        this.missoesGUI = new MissoesGUI(this);

        MissoesCommand comando = new MissoesCommand(this);
        getCommand("missoes").setExecutor(comando);
        getCommand("missoes").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new ProgressoListener(this), this);
        getServer().getPluginManager().registerEvents(missoesGUI, this);

        long cincoMin = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this, missaoManager::salvarSeMudou, cincoMin, cincoMin);

        getLogger().info("MissoesPlugin ativado! " + missaoManager.getPool(Periodo.DIARIA).size() + " missão(ões) diária(s) e "
                + missaoManager.getPool(Periodo.SEMANAL).size() + " semanal(is) no pool.");
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

    /**
     * API pública pra outros plugins (o EconomyShopPlugin chama por reflexão): soma progresso nas
     * missões desse tipo ({@code VENDER}, por exemplo). Tipo desconhecido é ignorado. Não mude a
     * assinatura sem atualizar quem usa.
     */
    public void registrarProgresso(Player jogador, String tipo, String alvo, int quantidade) {
        TipoMissao tipoMissao = TipoMissao.de(tipo);
        if (tipoMissao == null || missaoManager == null) return;
        missaoManager.registrar(jogador, tipoMissao, alvo.toUpperCase(java.util.Locale.ROOT), quantidade);
    }

    /**
     * API pública (EconomyShopPlugin, por reflexão): o jogador comprou isso da loja. Vender de volta
     * o que comprou no mesmo dia não conta pra missão de vender.
     */
    public void registrarCompraNaLoja(Player jogador, String material, int quantidade) {
        if (missaoManager != null) {
            missaoManager.registrarCompra(jogador.getUniqueId(), material.toUpperCase(java.util.Locale.ROOT), quantidade);
        }
    }

    public MissoesGUI getMissoesGUI() {
        return missoesGUI;
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
