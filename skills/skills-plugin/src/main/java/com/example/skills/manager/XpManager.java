package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.LevelFormula;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class XpManager {

    private final SkillsPlugin plugin;
    private final SkillDataManager dataManager;
    private final RewardManager rewardManager;
    private final LevelFormula formula;

    public XpManager(SkillsPlugin plugin, SkillDataManager dataManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;

        double base = plugin.getConfig().getDouble("xp.base", 50);
        double expoente = plugin.getConfig().getDouble("xp.expoente", 1.6);
        int nivelMaximo = plugin.getConfig().getInt("nivel-maximo", 50);
        this.formula = new LevelFormula(base, expoente, nivelMaximo);
    }

    public void adicionarXp(Player jogador, Skill skill, double quantidade) {
        if (quantidade <= 0) return;
        // o XP em dobro (ou o multiplicador do evento) vale por cima do bônus do VIP
        double multiplicador = (1 + bonusVipPercentual(jogador) / 100.0) * plugin.getEventoXp().multiplicador();
        adicionar(jogador, skill, quantidade * multiplicador, true);
    }

    /**
     * XP de bônus (o da ativação do VIP, chamado por reflexão): sobe os níveis normalmente, mas
     * sem o dinheiro por nível nem a mensagem de cada um, e sem o bônus de XP do VIP por cima.
     * Sem isso, um bônus grande dado a um jogador novo pagaria dezenas de níveis baratos em
     * todas as skills de uma vez. Retorna quantos níveis subiu, pra quem chamou resumir.
     */
    public int adicionarXpBonus(Player jogador, Skill skill, double quantidade) {
        if (quantidade <= 0) return 0;
        return adicionar(jogador, skill, quantidade, false);
    }

    private int adicionar(Player jogador, Skill skill, double quantidade, boolean recompensar) {
        PlayerSkillData dados = dataManager.get(jogador.getUniqueId());
        int nivelAtual = dados.getNivel(skill);

        int nivelInicial = nivelAtual;
        if (nivelAtual >= formula.getNivelMaximo()) {
            return 0; // já no nível máximo, não acumula mais XP
        }

        double xpAtual = dados.getXp(skill) + quantidade;

        // loop, pra caso um único golpe/tick dê XP suficiente pra subir mais de um nível de uma vez
        while (nivelAtual < formula.getNivelMaximo()) {
            double xpNecessario = formula.xpParaProximoNivel(nivelAtual);
            if (xpAtual < xpNecessario) break;

            xpAtual -= xpNecessario;
            nivelAtual++;
            dados.setNivel(skill, nivelAtual);
            if (recompensar) rewardManager.aoSubirDeNivel(jogador, skill, nivelAtual);
        }

        if (nivelAtual >= formula.getNivelMaximo()) {
            xpAtual = 0;
        }

        dados.setXp(skill, xpAtual);
        return nivelAtual - nivelInicial;
    }

    /**
     * Maior bônus de XP (%) que o jogador tem por permissão, em {@code vip-bonus-xp.permissoes}.
     * É uma LISTA de {permissao, percentual}, igual ao desconto de VIP da loja: chave com ponto
     * viraria seção aninhada num mapa.
     */
    private double bonusVipPercentual(Player jogador) {
        if (!plugin.getConfig().getBoolean("vip-bonus-xp.ativado", true)) return 0;
        List<?> permissoes = plugin.getConfig().getList("vip-bonus-xp.permissoes");
        if (permissoes == null) return 0;
        double maior = 0;
        for (Object item : permissoes) {
            if (item instanceof Map<?, ?> mapa && mapa.get("permissao") instanceof String permissao
                    && mapa.get("percentual") instanceof Number percentual && jogador.hasPermission(permissao)) {
                maior = Math.max(maior, percentual.doubleValue());
            }
        }
        return maior;
    }

    public LevelFormula getFormula() {
        return formula;
    }
}
