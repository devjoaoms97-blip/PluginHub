package com.example.boss.manager;

import com.example.boss.BossPlugin;
import com.example.boss.util.SkillsIntegration;
import com.example.boss.util.VipIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O que o boss paga quando morre, além do loot: coins pros primeiros do ranking de dano,
 * dinheiro pro campeão (último golpe), XP de skill pra todo mundo que bateu (proporcional ao
 * dano, na skill da arma usada) e, se ligado, um código de VIP pro campeão. O dinheiro vem do
 * servidor (é dinheiro novo na economia); sem Vault, só o dinheiro fica de fora. O dinheiro aparece no
 * extrato (o histórico do PixPlugin, que o Bancário entrega).
 */
public class Recompensas {

    private final BossPlugin plugin;
    private final SkillsIntegration skills;
    private final VipIntegration vip;
    private final com.example.boss.util.PixIntegration pix;

    public Recompensas(BossPlugin plugin) {
        this.plugin = plugin;
        this.skills = new SkillsIntegration(plugin);
        this.vip = new VipIntegration(plugin);
        this.pix = new com.example.boss.util.PixIntegration(plugin);
    }

    private Economy economia() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    /** Distribui tudo. {@code campeao} pode ser null (o boss morreu sem um jogador no último golpe). */
    public void distribuir(Player campeao) {
        Economy eco = economia();
        if (eco == null) plugin.getLogger().warning("Vault não encontrado: as recompensas em dinheiro do boss ficaram de fora.");

        List<Map.Entry<UUID, Double>> ranking = plugin.getBossManager().getRankingDano();
        List<Double> premios = plugin.getConfig().getDoubleList("recompensas.ranking-dano");
        double mult = plugin.getBossManager().multiplicadorRecompensas(); // elite paga mais
        if (eco != null) {
            for (int i = 0; i < Math.min(premios.size(), ranking.size()); i++) {
                double valor = premios.get(i) * mult;
                if (valor <= 0) continue;
                UUID id = ranking.get(i).getKey();
                eco.depositPlayer(Bukkit.getOfflinePlayer(id), valor);
                pix.comEntidade(id, "Boss", valor, true, (i + 1) + "º no ranking de dano do boss");
                avisar(id, "§6⚔ §e" + (i + 1) + "º no ranking de dano: §f+" + formatar(valor) + "§e.");
            }
            double dinheiroCampeao = plugin.getConfig().getDouble("recompensas.campeao-dinheiro", 0) * mult;
            if (campeao != null && dinheiroCampeao > 0) {
                eco.depositPlayer(campeao, dinheiroCampeao);
                pix.comEntidade(campeao.getUniqueId(), "Boss", dinheiroCampeao, true, "Campeão do boss (último golpe)");
                campeao.sendMessage("§6★ §eRecompensa de campeão: §f+" + formatar(dinheiroCampeao) + "§e.");
            }
        }

        darXpDeParticipacao();

        if (campeao != null && plugin.getConfig().getBoolean("recompensas.codigo-vip.ativado", false) && bossDaVip()) {
            int dias = Math.max(1, plugin.getConfig().getInt("recompensas.codigo-vip.dias", 7));
            String codigo = vip.gerarCodigo(plugin.getConfig().getString("recompensas.codigo-vip.tier", "bronze"), dias,
                    Math.max(0, plugin.getConfig().getInt("recompensas.codigo-vip.validade-dias", 7)));
            if (codigo != null) {
                campeao.sendMessage("§6★ §eVocê ganhou um código de VIP de evento (" + dias + " dias): §f" + codigo
                        + " §7(use §f/vip resgatar " + codigo + "§7; se já tiver VIP, guarde pra quando ele acabar)");
            }
        }
    }

    /** O boss que morreu dá o código de VIP? Só os de {@code recompensas.codigo-vip.bosses} (vazio = todos). */
    private boolean bossDaVip() {
        java.util.List<String> bosses = plugin.getConfig().getStringList("recompensas.codigo-vip.bosses");
        if (bosses.isEmpty()) return true;
        var perfil = plugin.getBossManager().getPerfilAtivo();
        return perfil != null && bosses.stream().anyMatch(b -> b.equalsIgnoreCase(perfil.getId()));
    }

    /**
     * Cada jogador ganha até {@code xp-skill-maximo}, proporcional ao % da vida do boss que tirou
     * (com um mínimo pra quem bateu pelo menos um pouco), dividido entre as skills das armas que usou.
     */
    private void darXpDeParticipacao() {
        double maximo = plugin.getConfig().getDouble("recompensas.xp-skill-maximo", 0);
        if (maximo <= 0 || !skills.disponivel()) return;
        double minimo = plugin.getConfig().getDouble("recompensas.xp-skill-minimo", 0);
        double vidaMaxima = plugin.getBossManager().getVidaMaxima();
        if (vidaMaxima <= 0) return;

        for (Map.Entry<UUID, Double> entrada : plugin.getBossManager().getRankingDano()) {
            Player jogador = Bukkit.getPlayer(entrada.getKey());
            if (jogador == null) continue; // o XP do SkillsPlugin é por jogador online
            double total = Math.max(minimo, Math.min(1, entrada.getValue() / vidaMaxima) * maximo) * plugin.getBossManager().multiplicadorRecompensas();
            Map<String, Double> porSkill = plugin.getBossManager().getDanoPorSkill(entrada.getKey());
            double danoComSkill = porSkill.values().stream().mapToDouble(Double::doubleValue).sum();
            if (danoComSkill <= 0) continue;
            StringBuilder resumo = new StringBuilder();
            for (Map.Entry<String, Double> s : porSkill.entrySet()) {
                double xp = Math.round(total * s.getValue() / danoComSkill);
                if (xp > 0 && skills.darXp(jogador, s.getKey(), xp)) {
                    if (!resumo.isEmpty()) resumo.append("§7, ");
                    resumo.append("§f+").append((long) xp).append(" §7").append(s.getKey().toLowerCase(Locale.ROOT));
                }
            }
            if (!resumo.isEmpty()) jogador.sendMessage("§a✦ XP por participar do boss: " + resumo);
        }
    }

    private void avisar(UUID id, String mensagem) {
        OfflinePlayer jogador = Bukkit.getOfflinePlayer(id);
        if (jogador.getPlayer() != null) jogador.getPlayer().sendMessage(mensagem);
    }

    private String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
