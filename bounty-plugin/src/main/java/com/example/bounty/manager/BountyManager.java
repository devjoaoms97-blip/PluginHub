package com.example.bounty.manager;

import com.example.bounty.BountyPlugin;
import com.example.bounty.model.Recompensa;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Recompensas ativas (alvo -> recompensa). Tudo salvo em {@code bounties.yml} a cada mudança,
 * porque é dinheiro retido de jogador: um restart não pode sumir com ele.
 */
public class BountyManager {

    private final BountyPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Recompensa> recompensas = new HashMap<>();

    // matador|alvo -> quando cobrou (anti-farm; em memória)
    private final Map<String, Long> ultimaCobranca = new HashMap<>();

    public BountyManager(BountyPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "bounties.yml");
        carregar();
    }

    public Recompensa get(UUID alvo) {
        return recompensas.get(alvo);
    }

    /** Recompensas ordenadas da maior pra menor. */
    public List<Recompensa> ranking() {
        List<Recompensa> lista = new ArrayList<>(recompensas.values());
        lista.sort(Comparator.comparingDouble(Recompensa::getTotal).reversed());
        return lista;
    }

    /** Soma uma contribuição (valor já líquido, depois da taxa). Retorna a recompensa atualizada. */
    public Recompensa adicionar(UUID alvo, String nomeAlvo, UUID contribuinte, String nomeContribuinte, double valorLiquido) {
        long agora = System.currentTimeMillis();
        Recompensa recompensa = recompensas.computeIfAbsent(alvo, k -> new Recompensa(alvo, nomeAlvo, agora));
        recompensa.setNomeAlvo(nomeAlvo);
        recompensa.adicionar(contribuinte, nomeContribuinte, valorLiquido, agora);
        salvar();
        return recompensa;
    }

    /** Tira a recompensa do alvo e paga o total pro matador. */
    public double pagar(Player matador, Recompensa recompensa) {
        double total = recompensa.getTotal();
        recompensas.remove(recompensa.getAlvo());
        salvar();

        Economy econ = plugin.getEconomy();
        if (econ != null) {
            econ.depositPlayer(matador, total);
        } else {
            plugin.getLogger().severe("Economia indisponível: " + matador.getName() + " não recebeu a recompensa de "
                    + total + " pela cabeça de " + recompensa.getNomeAlvo() + " — pague manualmente.");
        }
        ultimaCobranca.put(chaveCobranca(matador.getUniqueId(), recompensa.getAlvo()), System.currentTimeMillis());
        return total;
    }

    /** Devolve cada contribuição a quem ofereceu (expiração ou staff). */
    public void devolver(Recompensa recompensa, String motivo) {
        recompensas.remove(recompensa.getAlvo());
        salvar();

        Economy econ = plugin.getEconomy();
        for (Map.Entry<UUID, Double> entrada : recompensa.getContribuicoes().entrySet()) {
            OfflinePlayer contribuinte = Bukkit.getOfflinePlayer(entrada.getKey());
            if (econ != null) {
                econ.depositPlayer(contribuinte, entrada.getValue());
                plugin.getPixIntegration().registrarComServidor(entrada.getKey(), entrada.getValue(), true, "Bounty devolvido");
            } else {
                plugin.getLogger().severe("Economia indisponível: não deu pra devolver " + entrada.getValue()
                        + " pra " + entrada.getKey() + " — devolva manualmente.");
            }
            Player online = contribuinte.getPlayer();
            if (online != null) {
                online.sendMessage("§eA recompensa que você ofereceu pela cabeça de §f" + recompensa.getNomeAlvo()
                        + " §efoi devolvida (" + motivo + "): §f" + BountyPlugin.formatarValor(entrada.getValue()) + "§e.");
            }
        }
    }

    /** Ainda no intervalo anti-farm depois desse matador ter cobrado esse alvo? Retorna os ms restantes (0 = liberado). */
    public long cooldownRestante(UUID matador, UUID alvo) {
        long cooldownMs = Math.max(0, plugin.getConfig().getInt("anti-farm.cooldown-horas", 6)) * 3_600_000L;
        Long ultima = ultimaCobranca.get(chaveCobranca(matador, alvo));
        if (ultima == null) return 0;
        return Math.max(0, ultima + cooldownMs - System.currentTimeMillis());
    }

    private String chaveCobranca(UUID matador, UUID alvo) {
        return matador + "|" + alvo;
    }

    /** Devolve as recompensas sem contribuição nova há mais de {@code expira-dias}. */
    public void verificarExpiradas() {
        int dias = plugin.getConfig().getInt("expira-dias", 7);
        if (dias <= 0) return;
        long limite = System.currentTimeMillis() - dias * 86_400_000L;
        for (Recompensa recompensa : new ArrayList<>(recompensas.values())) {
            if (recompensa.getAtualizadaEm() < limite) {
                devolver(recompensa, "ninguém cobrou em " + dias + " dias");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Persistência (bounties.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("recompensas");
        if (secao == null) return;

        for (String chave : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(chave);
            if (s == null) continue;
            try {
                UUID alvo = UUID.fromString(chave);
                Recompensa recompensa = new Recompensa(alvo, s.getString("nome-alvo", "?"), s.getLong("atualizada-em"));
                ConfigurationSection contribuicoes = s.getConfigurationSection("contribuicoes");
                if (contribuicoes != null) {
                    for (String id : contribuicoes.getKeys(false)) {
                        UUID contribuinte = UUID.fromString(id);
                        recompensa.getContribuicoes().put(contribuinte, contribuicoes.getDouble(id + ".valor"));
                        recompensa.getNomesContribuintes().put(contribuinte, contribuicoes.getString(id + ".nome", "?"));
                    }
                }
                if (!recompensa.getContribuicoes().isEmpty()) {
                    recompensas.put(alvo, recompensa);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Recompensa inválida ignorada em bounties.yml: " + chave);
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Recompensa r : recompensas.values()) {
            String base = "recompensas." + r.getAlvo();
            yaml.set(base + ".nome-alvo", r.getNomeAlvo());
            yaml.set(base + ".atualizada-em", r.getAtualizadaEm());
            for (Map.Entry<UUID, Double> entrada : r.getContribuicoes().entrySet()) {
                yaml.set(base + ".contribuicoes." + entrada.getKey() + ".valor", entrada.getValue());
                yaml.set(base + ".contribuicoes." + entrada.getKey() + ".nome", r.getNomesContribuintes().get(entrada.getKey()));
            }
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar bounties.yml", e);
        }
    }
}
