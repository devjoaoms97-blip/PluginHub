package com.example.vip.manager;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * Último uso de cada perk com cooldown (heal, feed, kit, kit mensal), por jogador, salvo em
 * {@code cooldowns.yml} — assim reiniciar o servidor não dá um "uso grátis" pra ninguém.
 *
 * Ao salvar, descarta registros que já passaram do MAIOR cooldown configurado pra aquele
 * perk entre todos os tiers (não fariam diferença nenhuma), pra o arquivo não crescer pra sempre.
 */
public class CooldownManager {

    public enum Perk {
        HEAL, FEED, KIT, KIT_MENSAL
    }

    private final VipPlugin plugin;
    private final File arquivo;
    private final Map<Perk, Map<UUID, Long>> ultimoUso = new HashMap<>();

    public CooldownManager(VipPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "cooldowns.yml");
        for (Perk perk : Perk.values()) {
            ultimoUso.put(perk, new HashMap<>());
        }
        carregar();
    }

    /** Milissegundos que ainda faltam de cooldown (0 = pode usar). {@code minutos <= 0} = sem cooldown. */
    public long restanteMs(Perk perk, UUID jogador, int minutos) {
        if (minutos <= 0) return 0;
        Long ultimo = ultimoUso.get(perk).get(jogador);
        if (ultimo == null) return 0;
        long restante = ultimo + minutos * 60_000L - System.currentTimeMillis();
        return Math.max(0, restante);
    }

    public void registrarUso(Perk perk, UUID jogador) {
        ultimoUso.get(perk).put(jogador, System.currentTimeMillis());
        salvar();
    }

    private int maiorCooldownMinutos(Perk perk) {
        ToIntFunction<VipTier> cooldownDoTier = switch (perk) {
            case HEAL -> plugin::getCooldownHealMinutos;
            case FEED -> plugin::getCooldownFeedMinutos;
            case KIT -> tier -> plugin.getCooldownKitHoras(tier) * 60;
            case KIT_MENSAL -> tier -> plugin.getCooldownKitMensalDias(tier) * 24 * 60;
        };
        int maior = 0;
        for (VipTier tier : VipTier.values()) {
            maior = Math.max(maior, cooldownDoTier.applyAsInt(tier));
        }
        return maior;
    }

    // ---------------------------------------------------------------------
    // Persistência (cooldowns.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (Perk perk : Perk.values()) {
            ConfigurationSection secao = yaml.getConfigurationSection(perk.name().toLowerCase(Locale.ROOT));
            if (secao == null) continue;
            for (String chave : secao.getKeys(false)) {
                try {
                    ultimoUso.get(perk).put(UUID.fromString(chave), secao.getLong(chave));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID inválido ignorado em cooldowns.yml: " + chave);
                }
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        long agora = System.currentTimeMillis();
        for (Perk perk : Perk.values()) {
            long validadeMs = maiorCooldownMinutos(perk) * 60_000L;
            Map<UUID, Long> usos = ultimoUso.get(perk);
            usos.values().removeIf(ultimo -> agora - ultimo >= validadeMs);
            for (Map.Entry<UUID, Long> entrada : usos.entrySet()) {
                yaml.set(perk.name().toLowerCase(Locale.ROOT) + "." + entrada.getKey(), entrada.getValue());
            }
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar cooldowns.yml", e);
        }
    }
}
