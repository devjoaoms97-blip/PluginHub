package com.example.skills;

import com.example.skills.command.SkillsCommand;
import com.example.skills.gui.SkillsGUI;
import com.example.skills.gui.SkillsGUIListener;
import com.example.skills.listener.CombatListener;
import com.example.skills.listener.PlayerConnectionListener;
import com.example.skills.listener.RegenListener;
import com.example.skills.listener.StunListener;
import com.example.skills.item.AdagaUtil;
import com.example.skills.manager.AtordoamentoManager;
import com.example.skills.manager.BonusCalculator;
import com.example.skills.manager.CombatTagManager;
import com.example.skills.manager.PassiveSkillsTask;
import com.example.skills.manager.RewardManager;
import com.example.skills.manager.SangramentoManager;
import com.example.skills.manager.SkillDataManager;
import com.example.skills.manager.XpManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SkillsPlugin extends JavaPlugin {

    private Economy economy;

    private SkillDataManager skillDataManager;
    private RewardManager rewardManager;
    private XpManager xpManager;
    private CombatTagManager combatTagManager;
    private BonusCalculator bonusCalculator;
    private SkillsGUI skillsGUI;
    private PassiveSkillsTask passiveSkillsTask;
    private AdagaUtil adagaUtil;
    private SangramentoManager sangramentoManager;
    private AtordoamentoManager atordoamentoManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("As recompensas em dinheiro ao subir de nível não vão funcionar até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.skillDataManager = new SkillDataManager(this);
        this.rewardManager = new RewardManager(this);
        this.xpManager = new XpManager(this, skillDataManager, rewardManager);
        this.combatTagManager = new CombatTagManager();
        this.bonusCalculator = new BonusCalculator(this, skillDataManager);
        this.skillsGUI = new SkillsGUI(this);
        this.adagaUtil = new AdagaUtil(this);
        this.sangramentoManager = new SangramentoManager(this);
        this.atordoamentoManager = new AtordoamentoManager(this);

        SkillsCommand skillsCommand = new SkillsCommand(this);
        getCommand("skills").setExecutor(skillsCommand);
        getCommand("skills").setTabCompleter(skillsCommand);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new RegenListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillsGUIListener(), this);
        getServer().getPluginManager().registerEvents(new StunListener(this), this);

        this.passiveSkillsTask = new PassiveSkillsTask(this);
        int intervaloSegundos = getConfig().getInt("passivas.intervalo-segundos", 10);
        long intervaloTicks = intervaloSegundos * 20L;
        passiveSkillsTask.runTaskTimer(this, intervaloTicks, intervaloTicks);

        getLogger().info("SkillsPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (skillDataManager != null) {
            skillDataManager.salvarTodos();
        }
        if (sangramentoManager != null) {
            sangramentoManager.cancelarTodas();
        }
        if (atordoamentoManager != null) {
            atordoamentoManager.cancelarTodas();
        }
        getLogger().info("SkillsPlugin desativado!");
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

    public SkillDataManager getSkillDataManager() {
        return skillDataManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public XpManager getXpManager() {
        return xpManager;
    }

    public CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    public BonusCalculator getBonusCalculator() {
        return bonusCalculator;
    }

    public SkillsGUI getSkillsGUI() {
        return skillsGUI;
    }

    public PassiveSkillsTask getPassiveSkillsTask() {
        return passiveSkillsTask;
    }

    public AdagaUtil getAdagaUtil() {
        return adagaUtil;
    }

    public SangramentoManager getSangramentoManager() {
        return sangramentoManager;
    }

    public AtordoamentoManager getAtordoamentoManager() {
        return atordoamentoManager;
    }
}
