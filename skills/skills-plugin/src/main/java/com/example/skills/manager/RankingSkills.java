package com.example.skills.manager;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking global das skills (/skills top): lê os dados de todo mundo (o playerdata de cada um, e o
 * cache pra quem está online, que tem o valor mais novo) e ordena por nível e depois por XP. Sem
 * skill, ordena pela soma dos níveis. O resultado fica guardado por um minuto pra não ler o disco a
 * cada comando.
 */
public class RankingSkills {

    public record Posicao(UUID jogador, String nome, int nivel, double xp) {}

    private static final long VALIDADE_MS = 60_000;

    private final SkillsPlugin plugin;
    private final Map<String, List<Posicao>> cache = new HashMap<>();
    private final Map<String, Long> calculadoEm = new HashMap<>();

    public RankingSkills(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Os primeiros {@code quantos} da skill (ou da soma dos níveis, com {@code skill} null). */
    public List<Posicao> top(Skill skill, int quantos) {
        String chave = skill == null ? "*" : skill.name();
        Long quando = calculadoEm.get(chave);
        if (quando == null || System.currentTimeMillis() - quando > VALIDADE_MS) {
            cache.put(chave, calcular(skill));
            calculadoEm.put(chave, System.currentTimeMillis());
        }
        List<Posicao> lista = cache.get(chave);
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    private List<Posicao> calcular(Skill skill) {
        List<Posicao> lista = new ArrayList<>();
        File[] arquivos = new File(plugin.getDataFolder(), "playerdata").listFiles((d, n) -> n.endsWith(".yml"));
        if (arquivos == null) return lista;
        for (File arquivo : arquivos) {
            UUID id;
            try {
                id = UUID.fromString(arquivo.getName().replace(".yml", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            int nivel;
            double xp;
            if (Bukkit.getPlayer(id) != null) {
                var dados = plugin.getSkillDataManager().get(id);
                nivel = skill == null ? soma(dados) : dados.getNivel(skill);
                xp = skill == null ? 0 : dados.getXp(skill);
            } else {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
                if (skill == null) {
                    nivel = 0;
                    for (Skill s : Skill.values()) nivel += yaml.getInt(s.getChaveConfig() + ".nivel", 1);
                    xp = 0;
                } else {
                    nivel = yaml.getInt(skill.getChaveConfig() + ".nivel", 1);
                    xp = yaml.getDouble(skill.getChaveConfig() + ".xp", 0);
                }
            }
            String nome = Bukkit.getOfflinePlayer(id).getName();
            lista.add(new Posicao(id, nome != null ? nome : id.toString().substring(0, 8), nivel, xp));
        }
        lista.sort(Comparator.comparingInt(Posicao::nivel).thenComparingDouble(Posicao::xp).reversed());
        return lista;
    }

    private int soma(PlayerSkillData dados) {
        int total = 0;
        for (Skill s : Skill.values()) total += dados.getNivel(s);
        return total;
    }
}
