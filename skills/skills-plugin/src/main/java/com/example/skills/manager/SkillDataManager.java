package com.example.skills.manager;

import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Guarda os dados de skill de cada jogador em disco (plugins/SkillsPlugin/playerdata/<uuid>.yml),
 * pra sobreviver a reinícios do servidor. Mantém um cache em memória enquanto o jogador está online.
 */
public class SkillDataManager {

    private final JavaPlugin plugin;
    private final File pastaDados;
    private final Map<UUID, PlayerSkillData> cache = new ConcurrentHashMap<>();

    public SkillDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pastaDados = new File(plugin.getDataFolder(), "playerdata");
        if (!pastaDados.exists()) {
            pastaDados.mkdirs();
        }
    }

    public PlayerSkillData get(UUID jogadorId) {
        return cache.computeIfAbsent(jogadorId, this::carregarDoDisco);
    }

    private PlayerSkillData carregarDoDisco(UUID jogadorId) {
        PlayerSkillData dados = new PlayerSkillData(jogadorId);
        File arquivo = new File(pastaDados, jogadorId + ".yml");

        if (arquivo.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
            for (Skill skill : Skill.values()) {
                String chave = skill.getChaveConfig();
                dados.setNivel(skill, yaml.getInt(chave + ".nivel", 1));
                dados.setXp(skill, yaml.getDouble(chave + ".xp", 0.0));
            }
        }

        return dados;
    }

    public void salvar(UUID jogadorId) {
        PlayerSkillData dados = cache.get(jogadorId);
        if (dados == null) return;

        YamlConfiguration yaml = new YamlConfiguration();
        for (Skill skill : Skill.values()) {
            String chave = skill.getChaveConfig();
            yaml.set(chave + ".nivel", dados.getNivel(skill));
            yaml.set(chave + ".xp", dados.getXp(skill));
        }

        File arquivo = new File(pastaDados, jogadorId + ".yml");
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar os dados de skill de " + jogadorId, e);
        }
    }

    public void salvarTodos() {
        for (UUID id : cache.keySet()) {
            salvar(id);
        }
    }

    public void descarregar(UUID jogadorId) {
        salvar(jogadorId);
        cache.remove(jogadorId);
    }
}
