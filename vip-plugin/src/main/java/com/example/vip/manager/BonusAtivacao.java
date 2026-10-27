package com.example.vip.manager;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipTier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Bônus de quando um VIP é ativado ({@code /vip add} ou {@code /vip resgatar}). Todo VIP dura
 * {@link VipManager#DIAS_VIP} dias, então o bônus é sempre o cheio:
 * <ul>
 *   <li>dinheiro: uma % do preço do próximo rank do jogador (RankupPlugin). Assim o bônus pesa o
 *   mesmo no começo e no fim do jogo, e nunca passa de uma fração de rank por mês;</li>
 *   <li>XP em todas as skills (SkillsPlugin, sem o dinheiro por nível das skills: pagaria
 *   muito mais que o próprio bônus a um jogador novo). Quem está offline recebe ao entrar
 *   ({@code bonus-pendentes.yml}).</li>
 * </ul>
 * As duas integrações são por reflexão: sem o plugin, aquela parte do bônus é pulada.
 */
public class BonusAtivacao implements Listener {

    private final VipPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Double> xpPendente = new HashMap<>();
    private boolean avisouFalha = false;

    public BonusAtivacao(VipPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "bonus-pendentes.yml");
        carregar();
    }

    public void conceder(OfflinePlayer jogador, VipTier tier) {
        String base = "tiers." + tier.name() + ".bonus-ativacao.";

        double percentual = plugin.getConfig().getDouble(base + "dinheiro-percentual-proximo-rank", 0);
        double preco = precoProximoRank(jogador.getUniqueId());
        double dinheiro = preco * percentual / 100.0;
        if (dinheiro > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(jogador, dinheiro);
            plugin.getPix().comEntidade(jogador.getUniqueId(), "VIP", dinheiro, true,
                    "Bônus de ativação do VIP " + org.bukkit.ChatColor.stripColor(plugin.getNomeExibicao(tier)));
        }

        double xp = plugin.getConfig().getDouble(base + "xp-por-skill", 0);
        Player online = jogador.getPlayer();
        int niveis = 0;
        if (xp > 0) {
            if (online != null) {
                niveis = darXpEmTodas(online, xp);
            } else {
                xpPendente.merge(jogador.getUniqueId(), xp, Double::sum);
                salvar();
            }
        }

        if (online != null && (dinheiro > 0 || xp > 0)) {
            online.sendMessage("§6§lBônus de ativação: "
                    + (dinheiro > 0 ? "§f" + formatar(dinheiro) + " §6" : "")
                    + (dinheiro > 0 && xp > 0 ? "e " : "")
                    + (xp > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "§f+%,.0f XP §6em todas as skills", xp) : ""));
            avisarNiveis(online, niveis);
        }
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Double xp = xpPendente.remove(evento.getPlayer().getUniqueId());
        if (xp == null) return;
        salvar();
        int niveis = darXpEmTodas(evento.getPlayer(), xp);
        evento.getPlayer().sendMessage(String.format(Locale.forLanguageTag("pt-BR"),
                "§6§lBônus de ativação do VIP: §f+%,.0f XP §6em todas as skills", xp));
        avisarNiveis(evento.getPlayer(), niveis);
    }

    /** Uma linha só no lugar do "SKILL UP" de cada nível, que o XP de bônus não manda. */
    private void avisarNiveis(Player jogador, int niveis) {
        if (niveis <= 0) return;
        jogador.sendMessage("§6Suas skills subiram §f" + niveis + (niveis == 1 ? " nível" : " níveis")
                + " §6no total. Veja em §f/skills§6.");
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.0f);
    }

    /** Preço do próximo rank (RankupPlugin#precoProximoRank), ou 0 sem o RankupPlugin. */
    private double precoProximoRank(UUID jogador) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return 0;
        try {
            Object valor = rankup.getClass().getMethod("precoProximoRank", UUID.class).invoke(rankup, jogador);
            return valor instanceof Number n && Double.isFinite(n.doubleValue()) ? Math.max(0, n.doubleValue()) : 0;
        } catch (ReflectiveOperationException e) {
            avisarFalha("RankupPlugin encontrado, mas sem o preço do próximo rank (versão antiga?): " + e.getMessage());
            return 0;
        }
    }

    /** XpManager#adicionarXpBonus do SkillsPlugin em cada skill do enum Skill; retorna os níveis subidos. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int darXpEmTodas(Player jogador, double xp) {
        Plugin skills = Bukkit.getPluginManager().getPlugin("SkillsPlugin");
        if (skills == null || !skills.isEnabled()) return 0;
        try {
            Class<? extends Enum> classeSkill = (Class<? extends Enum>) skills.getClass().getClassLoader()
                    .loadClass("com.example.skills.skill.Skill");
            Object xpManager = skills.getClass().getMethod("getXpManager").invoke(skills);
            Method adicionar = xpManager.getClass().getMethod("adicionarXpBonus", Player.class, classeSkill, double.class);
            int niveis = 0;
            for (Enum skill : classeSkill.getEnumConstants()) {
                if (adicionar.invoke(xpManager, jogador, skill, xp) instanceof Integer n) niveis += n;
            }
            return niveis;
        } catch (ReflectiveOperationException e) {
            avisarFalha("Não foi possível dar XP de skill pelo SkillsPlugin (versão incompatível?): " + e.getMessage());
            return 0;
        }
    }

    private void avisarFalha(String mensagem) {
        if (!avisouFalha) {
            plugin.getLogger().warning(mensagem);
            avisouFalha = true;
        }
    }

    private static String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    // ---------------------------------------------------------------------
    // Persistência (bonus-pendentes.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String chave : yaml.getKeys(false)) {
            try {
                xpPendente.put(UUID.fromString(chave), yaml.getDouble(chave));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido ignorado em bonus-pendentes.yml: " + chave);
            }
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        xpPendente.forEach((uuid, xp) -> yaml.set(uuid.toString(), xp));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar bonus-pendentes.yml", e);
        }
    }
}
