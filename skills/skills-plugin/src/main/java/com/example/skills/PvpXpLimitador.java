package com.example.skills;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Limita e reduz progressivamente o XP de arma ganho contra a mesma pessoa no mesmo dia. */
public final class PvpXpLimitador {

    private final SkillsPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Map<UUID, Double>> xpPorDupla = new HashMap<>();
    private LocalDate data = LocalDate.now();

    public PvpXpLimitador(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "xp-pvp.yml");
        carregar();
    }

    /** Recebe XP já multiplicado por VIP/evento e retorna quanto pode ser concedido. */
    public synchronized double limitar(Player atacante, Player vitima, double xpSolicitado) {
        if (xpSolicitado <= 0) return 0;
        atualizarDia();

        UUID atacanteId = atacante.getUniqueId();
        UUID vitimaId = vitima.getUniqueId();
        Map<UUID, Double> porVitima = xpPorDupla.computeIfAbsent(atacanteId, ignorado -> new HashMap<>());
        double acumulado = porVitima.getOrDefault(vitimaId, 0.0);
        double limite = Math.max(0, plugin.getConfig().getDouble("xp.pvp.limite-diario-por-vitima", 1000.0));
        double restante = limite - acumulado;
        if (restante <= 0) return 0;

        double faixaCheia = Math.max(0, plugin.getConfig().getDouble("xp.pvp.faixa-xp-integral", 250.0));
        double faixaMeia = Math.max(faixaCheia, plugin.getConfig().getDouble("xp.pvp.faixa-xp-meia", 500.0));
        double multiplicador = acumulado < faixaCheia ? 1.0
                : acumulado < faixaMeia
                ? limitarMultiplicador(plugin.getConfig().getDouble("xp.pvp.multiplicador-meia", 0.5))
                : limitarMultiplicador(plugin.getConfig().getDouble("xp.pvp.multiplicador-reduzido", 0.25));

        double concedido = Math.min(restante, xpSolicitado * multiplicador);
        if (concedido > 0) {
            porVitima.put(vitimaId, acumulado + concedido);
            if (!salvar()) {
                if (acumulado == 0) porVitima.remove(vitimaId);
                else porVitima.put(vitimaId, acumulado);
                return 0;
            }
        }
        return concedido;
    }

    public synchronized boolean salvar() {
        FileConfiguration dados = new YamlConfiguration();
        dados.set("data", data.toString());
        xpPorDupla.forEach((atacante, vitimas) -> vitimas.forEach((vitima, xp) ->
                dados.set("pairs." + atacante + "." + vitima, xp)));
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Não foi possível criar a pasta para salvar o limite diário de XP PvP.");
                return false;
            }
            dados.save(arquivo);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o limite diário de XP PvP: " + e.getMessage());
            return false;
        }
    }

    private void carregar() {
        if (!arquivo.isFile()) return;
        FileConfiguration dados = YamlConfiguration.loadConfiguration(arquivo);
        LocalDate hoje = LocalDate.now();
        boolean dataValida = true;
        try {
            data = LocalDate.parse(dados.getString("data", ""));
        } catch (RuntimeException ignorado) {
            dataValida = false;
        }
        if (!dataValida || !data.equals(hoje)) {
            data = hoje;
            salvar();
            return;
        }

        ConfigurationSection pares = dados.getConfigurationSection("pairs");
        if (pares == null) return;
        for (String atacanteTexto : pares.getKeys(false)) {
            UUID atacante;
            try {
                atacante = UUID.fromString(atacanteTexto);
            } catch (IllegalArgumentException ignorado) {
                continue;
            }
            ConfigurationSection vitimas = pares.getConfigurationSection(atacanteTexto);
            if (vitimas == null) continue;
            for (String vitimaTexto : vitimas.getKeys(false)) {
                try {
                    double xp = Math.max(0, vitimas.getDouble(vitimaTexto));
                    xpPorDupla.computeIfAbsent(atacante, ignorado -> new HashMap<>())
                            .put(UUID.fromString(vitimaTexto), xp);
                } catch (IllegalArgumentException ignorado) {
                    // Ignora IDs inválidos sem descartar os demais registros.
                }
            }
        }
    }

    private void atualizarDia() {
        LocalDate hoje = LocalDate.now();
        if (data.equals(hoje)) return;
        xpPorDupla.clear();
        data = hoje;
        salvar();
    }

    private double limitarMultiplicador(double valor) {
        return Math.max(0, Math.min(1, valor));
    }
}
