package com.example.rankup.manager;

import com.example.rankup.RankupPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Prestígio: quem chega no último rank pode voltar pro primeiro e ganhar um nível de prestígio,
 * com bônus permanente de venda na loja e a marca no chat e na tab. Salvo em prestigio.yml.
 */
public class PrestigioManager {

    private final RankupPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Integer> niveis = new ConcurrentHashMap<>();

    public PrestigioManager(RankupPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "prestigio.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String chave : yaml.getKeys(false)) {
            try {
                niveis.put(UUID.fromString(chave), yaml.getInt(chave));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em prestigio.yml: " + chave);
            }
        }
    }

    public int nivel(UUID jogador) {
        return niveis.getOrDefault(jogador, 0);
    }

    public int maximo() {
        return Math.max(0, plugin.getConfig().getInt("prestigio.maximo", 10));
    }

    /** Bônus de venda do prestígio: 1.2 = 20% a mais (soma por nível). */
    public double multiplicador(UUID jogador) {
        double porNivel = Math.max(0, plugin.getConfig().getDouble("prestigio.bonus-venda-percentual", 10)) / 100.0;
        return 1 + porNivel * nivel(jogador);
    }

    public void subir(UUID jogador) {
        niveis.merge(jogador, 1, Integer::sum);
        YamlConfiguration yaml = new YamlConfiguration();
        niveis.forEach((id, n) -> yaml.set(id.toString(), n));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar prestigio.yml", e);
        }
    }
}
