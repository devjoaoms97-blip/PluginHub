package com.example.skills;

import com.example.skills.command.SkillsCommand;
import com.example.skills.gui.SkillsGUI;
import com.example.skills.gui.SkillsGUIListener;
import com.example.skills.listener.CombatListener;
import com.example.skills.listener.PlayerConnectionListener;
import com.example.skills.listener.RegenListener;
import com.example.skills.listener.MorteListener;
import com.example.skills.listener.StunListener;
import com.example.skills.item.AdagaUtil;
import com.example.skills.manager.EventoXp;
import com.example.skills.manager.RankingSkills;
import com.example.skills.manager.AtordoamentoManager;
import com.example.skills.manager.BonusCalculator;
import com.example.skills.manager.CombatTagManager;
import com.example.skills.manager.ParryManager;
import com.example.skills.manager.PassiveSkillsTask;
import com.example.skills.manager.RewardManager;
import com.example.skills.manager.SangramentoManager;
import com.example.skills.manager.SkillDataManager;
import com.example.skills.manager.XpManager;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

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
    private ParryManager parryManager;
    private EventoXp eventoXp;
    private RankingSkills rankingSkills;
    private PvpXpLimitador pvpXpLimitador;

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
        this.pvpXpLimitador = new PvpXpLimitador(this);
        this.combatTagManager = new CombatTagManager();
        this.bonusCalculator = new BonusCalculator(this, skillDataManager);
        this.skillsGUI = new SkillsGUI(this);
        this.adagaUtil = new AdagaUtil(this);
        this.sangramentoManager = new SangramentoManager(this);
        this.atordoamentoManager = new AtordoamentoManager(this);
        this.parryManager = new ParryManager(this);
        this.eventoXp = new EventoXp(this);
        this.rankingSkills = new RankingSkills(this);

        SkillsCommand skillsCommand = new SkillsCommand(this);
        getCommand("skills").setExecutor(skillsCommand);
        getCommand("skills").setTabCompleter(skillsCommand);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new RegenListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillsGUIListener(), this);
        getServer().getPluginManager().registerEvents(new StunListener(this), this);
        getServer().getPluginManager().registerEvents(new MorteListener(this), this);

        this.passiveSkillsTask = new PassiveSkillsTask(this);
        int intervaloSegundos = getConfig().getInt("passivas.intervalo-segundos", 10);
        long intervaloTicks = intervaloSegundos * 20L;
        passiveSkillsTask.runTaskTimer(this, intervaloTicks, intervaloTicks);
        // começo e fim do XP em dobro são anunciados no chat
        getServer().getScheduler().runTaskTimer(this, eventoXp::conferir, 20L * 60, 20L * 60);

        getLogger().info("SkillsPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (pvpXpLimitador != null) {
            pvpXpLimitador.salvar();
        }
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

    /**
     * Configs gerados por versões antigas do plugin não possuem as chaves das skills
     * adicionadas depois (Lanceiro e Marreteiro), o que faz o bônus de dano ficar
     * permanentemente em 0%. Este método adiciona as chaves faltantes ao config.yml
     * existente, preservando os valores já configurados das demais skills.
     */
    private void garantirChavesNoConfig() {
        FileConfiguration config = getConfig();
        boolean modificado = false;

        for (Skill skill : Skill.values()) {
            // Crítico não usa bônus percentual genérico (usa chance + multiplicador)
            if (skill == Skill.CRITICO) continue;

            String caminho = "bonus-por-nivel." + skill.getChaveConfig();
            if (!config.contains(caminho)) {
                config.set(caminho, 0.005); // +0,5% por nível (mesmo padrão das demais skills de dano)
                modificado = true;
            }
        }

        if (modificado) {
            saveConfig();
            getLogger().info("Config atualizado: chaves de bônus por nível adicionadas ao config.yml.");
        }
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

    /** A skill de arma que um golpe com esse item treina, ou null (item que não é arma). */
    public Skill skillDoItem(ItemStack item) {
        // A Adaga é uma espada marcada — treina Crítico em vez de Espadas
        if (adagaUtil.ehAdaga(item)) return Skill.CRITICO;
        Material tipo = item == null ? Material.AIR : item.getType();
        String nome = tipo.name();
        if (tipo == Material.TRIDENT) return Skill.TRIDENTE;
        if (tipo == Material.MACE) return Skill.MARRETEIRO;
        if (nome.endsWith("_SWORD")) return Skill.ESPADAS;
        if (nome.endsWith("_AXE")) return Skill.MACHADO;
        if (nome.endsWith("_SPEAR")) return Skill.LANCEIRO;
        if (tipo == Material.BOW || tipo == Material.CROSSBOW) return Skill.ARQUEIRO;
        if (tipo == Material.AIR) return Skill.PORRADEIRO;
        if (tipo == Material.SHIELD) return Skill.BLOQUEIO;
        return null;
    }

    /**
     * A skill "em uso", pro PlacarPlugin: a da arma do último golpe no último minuto ou, sem
     * nenhum, a da arma na mão. Mão vazia sem luta recente não mostra nada (senão seria sempre Porradeiro).
     */
    private Skill skillEmUso(Player jogador) {
        Skill recente = xpManager.ultimaSkill(jogador.getUniqueId(), 60);
        if (recente != null) return recente;
        if (jogador.getInventory().getItemInMainHand().getType() == Material.AIR) return null;
        return skillDoItem(jogador.getInventory().getItemInMainHand());
    }

    /** PlacarPlugin ({skill}): "Espadas ✦2 nv 23", ou null sem skill em uso. */
    public String textoSkillEmUso(UUID jogadorId) {
        Player jogador = getServer().getPlayer(jogadorId);
        Skill skill = jogador == null ? null : skillEmUso(jogador);
        if (skill == null) return null;
        PlayerSkillData dados = skillDataManager.get(jogadorId);
        String prestigio = dados.getPrestigio(skill) > 0 ? " §d✦" + dados.getPrestigio(skill) : "";
        return "§e" + skill.getNomeExibicao() + prestigio + " §7nv §f" + dados.getNivel(skill);
    }

    /** PlacarPlugin ({skill-progresso}): barra de 10 blocos até o próximo nível, com a %. */
    public String progressoSkillEmUso(UUID jogadorId) {
        Player jogador = getServer().getPlayer(jogadorId);
        Skill skill = jogador == null ? null : skillEmUso(jogador);
        if (skill == null) return null;
        PlayerSkillData dados = skillDataManager.get(jogadorId);
        int nivel = dados.getNivel(skill);
        if (nivel >= xpManager.getFormula().getNivelMaximo()) return "§6✦ nível máximo";
        double fracao = Math.max(0, Math.min(1, dados.getXp(skill) / xpManager.getFormula().xpParaProximoNivel(nivel)));
        int cheios = (int) Math.floor(fracao * 10);
        return "§b" + "■".repeat(cheios) + "§8" + "■".repeat(10 - cheios)
                + String.format(Locale.forLanguageTag("pt-BR"), " §f%.0f%%", Math.floor(fracao * 100));
    }

    private java.lang.reflect.Method metodoMaldicao;

    /** A maldição do BossPlugin (boss que escapou): multiplicador do XP de skill, 1 sem ela ou sem o plugin. */
    public double fatorMaldicaoBoss() {
        org.bukkit.plugin.Plugin boss = getServer().getPluginManager().getPlugin("BossPlugin");
        if (boss == null || !boss.isEnabled()) return 1;
        try {
            if (metodoMaldicao == null || metodoMaldicao.getDeclaringClass() != boss.getClass()) {
                metodoMaldicao = boss.getClass().getMethod("fatorXpMaldicao");
            }
            return ((Number) metodoMaldicao.invoke(boss)).doubleValue();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return 1;
        }
    }

    public EventoXp getEventoXp() {
        return eventoXp;
    }

    public RankingSkills getRankingSkills() {
        return rankingSkills;
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

    public PvpXpLimitador getPvpXpLimitador() {
        return pvpXpLimitador;
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

    public ParryManager getParryManager() {
        return parryManager;
    }
}
