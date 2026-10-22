package com.example.missoes.manager;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.Missao;
import com.example.missoes.model.ProgressoJogador;
import com.example.missoes.model.TipoMissao;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Pool de missões (do config.yml) e o progresso diário de cada jogador.
 *
 * No primeiro acesso do dia, o jogador recebe {@code missoes-por-dia} missões sorteadas (sem
 * repetir) do pool; à meia-noite (horário do servidor) isso zera e o próximo acesso sorteia de
 * novo. Completar uma missão paga na hora; completar todas paga o bônus.
 *
 * O progresso é gravado em {@code progresso.yml} de tempos em tempos (não a cada bloco
 * quebrado) e no desligamento.
 */
public class MissaoManager {

    private final MissoesPlugin plugin;
    private final File arquivo;
    private final Map<String, Missao> pool = new LinkedHashMap<>();
    private final Map<UUID, ProgressoJogador> jogadores = new HashMap<>();
    private final Map<UUID, Long> ultimoAvisoBarra = new HashMap<>();
    private boolean sujo;

    public MissaoManager(MissoesPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "progresso.yml");
        carregarPool();
        carregar();
    }

    // ---------------------------------------------------------------------
    // Pool
    // ---------------------------------------------------------------------
    public void carregarPool() {
        pool.clear();
        for (Map<?, ?> bruto : plugin.getConfig().getMapList("pool")) {
            try {
                String id = String.valueOf(bruto.get("id")).toLowerCase(Locale.ROOT);
                TipoMissao tipo = TipoMissao.de(String.valueOf(bruto.get("tipo")));
                if (tipo == null) throw new IllegalArgumentException("tipo inválido: " + bruto.get("tipo"));
                int quantidade = ((Number) bruto.get("quantidade")).intValue();
                if (quantidade < 1) throw new IllegalArgumentException("quantidade precisa ser >= 1");

                Set<String> alvos = new HashSet<>();
                Object brutoAlvos = bruto.get("alvos");
                if (brutoAlvos instanceof List<?> lista) {
                    for (Object alvo : lista) alvos.add(String.valueOf(alvo).toUpperCase(Locale.ROOT));
                }

                Object descricao = bruto.get("descricao");
                Object coins = bruto.get("coins");
                Object skill = bruto.get("skill");
                Object xp = bruto.get("xp");
                pool.put(id, new Missao(id, tipo, alvos, quantidade,
                        descricao != null ? String.valueOf(descricao) : tipo.name().toLowerCase(Locale.ROOT) + " " + quantidade,
                        coins instanceof Number n ? n.doubleValue() : 0,
                        skill != null ? String.valueOf(skill).toUpperCase(Locale.ROOT) : null,
                        xp instanceof Number n ? n.doubleValue() : 0));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Missão inválida ignorada no pool: " + bruto + " (" + e.getMessage() + ")");
            }
        }
        if (pool.isEmpty()) {
            plugin.getLogger().warning("O pool de missões está vazio — ninguém vai receber missão.");
        }
    }

    public Map<String, Missao> getPool() {
        return pool;
    }

    public Missao getMissao(String id) {
        return pool.get(id);
    }

    // ---------------------------------------------------------------------
    // Progresso
    // ---------------------------------------------------------------------

    /** As missões de hoje do jogador, sorteando se for o primeiro acesso do dia. */
    public ProgressoJogador getDoDia(UUID jogador) {
        LocalDate hoje = LocalDate.now();
        ProgressoJogador atual = jogadores.get(jogador);
        if (atual != null && atual.getDia().equals(hoje)) {
            return atual;
        }
        ProgressoJogador novo = sortear(hoje);
        jogadores.put(jogador, novo);
        sujo = true;
        return novo;
    }

    /** Descarta as missões de hoje do jogador e sorteia outras (staff). */
    public ProgressoJogador resortear(UUID jogador) {
        ProgressoJogador novo = sortear(LocalDate.now());
        jogadores.put(jogador, novo);
        sujo = true;
        return novo;
    }

    private ProgressoJogador sortear(LocalDate dia) {
        List<String> ids = new ArrayList<>(pool.keySet());
        Collections.shuffle(ids);
        int quantas = Math.max(1, plugin.getConfig().getInt("missoes-por-dia", 3));
        ProgressoJogador progresso = new ProgressoJogador(dia);
        for (String id : ids.subList(0, Math.min(quantas, ids.size()))) {
            progresso.getProgresso().put(id, 0);
        }
        return progresso;
    }

    /**
     * Soma {@code quantidade} em toda missão de hoje do jogador que for desse tipo e aceitar
     * esse alvo. Paga a recompensa quando alguma completa.
     */
    public void registrar(Player jogador, TipoMissao tipo, String alvo, int quantidade) {
        if (quantidade <= 0) return;
        ProgressoJogador dia = getDoDia(jogador.getUniqueId());

        for (Map.Entry<String, Integer> entrada : dia.getProgresso().entrySet()) {
            Missao missao = pool.get(entrada.getKey());
            if (missao == null || missao.tipo() != tipo || !missao.aceita(alvo)) continue;

            int antes = entrada.getValue();
            if (antes >= missao.quantidade()) continue; // já concluída

            int depois = Math.min(missao.quantidade(), antes + quantidade);
            entrada.setValue(depois);
            sujo = true;

            if (depois >= missao.quantidade()) {
                concluir(jogador, missao);
            } else {
                mostrarProgresso(jogador, missao, depois);
            }
        }

        if (!dia.isBonusPago() && todasConcluidas(dia)) {
            dia.setBonusPago(true);
            pagarBonus(jogador);
        }
    }

    public boolean todasConcluidas(ProgressoJogador dia) {
        if (dia.getProgresso().isEmpty()) return false;
        for (Map.Entry<String, Integer> entrada : dia.getProgresso().entrySet()) {
            Missao missao = pool.get(entrada.getKey());
            if (missao != null && entrada.getValue() < missao.quantidade()) return false;
        }
        return true;
    }

    private void concluir(Player jogador, Missao missao) {
        String recompensa = pagar(jogador, missao.coins(), missao.skill(), missao.xp());
        jogador.sendMessage("§a§l✔ Missão concluída: §f" + missao.descricao() + (recompensa.isEmpty() ? "" : " §7(" + recompensa + "§7)"));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void pagarBonus(Player jogador) {
        double coins = plugin.getConfig().getDouble("bonus-todas.coins", 0);
        String skill = plugin.getConfig().getString("bonus-todas.skill");
        double xp = plugin.getConfig().getDouble("bonus-todas.xp", 0);
        String recompensa = pagar(jogador, coins, skill, xp);
        if (recompensa.isEmpty()) return;
        jogador.sendMessage("§6§l★ Todas as missões de hoje concluídas! §6Bônus: " + recompensa);
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /** Entrega coins e XP de skill; retorna a descrição do que foi entregue (vazio se nada). */
    private String pagar(Player jogador, double coins, String skill, double xp) {
        List<String> partes = new ArrayList<>();
        Economy econ = plugin.getEconomy();
        if (coins > 0) {
            if (econ != null) {
                econ.depositPlayer(jogador, coins);
                plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), coins, true, "Missão diária");
                partes.add("§f+" + MissoesPlugin.formatarValor(coins));
            } else {
                plugin.getLogger().warning("Economia indisponível: " + jogador.getName() + " não recebeu " + coins + " coins de missão.");
            }
        }
        if (skill != null && xp > 0 && plugin.getSkillsIntegration().darXp(jogador, skill, xp)) {
            partes.add("§b+" + (int) xp + " XP de " + skill.toLowerCase(Locale.ROOT));
        }
        return String.join("§7, ", partes);
    }

    /** Barra de ação com o progresso, no máximo 1x por segundo por jogador (minerar dispara muito). */
    private void mostrarProgresso(Player jogador, Missao missao, int atual) {
        long agora = System.currentTimeMillis();
        Long ultimo = ultimoAvisoBarra.get(jogador.getUniqueId());
        if (ultimo != null && agora - ultimo < 1000) return;
        ultimoAvisoBarra.put(jogador.getUniqueId(), agora);
        jogador.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize("§e" + missao.descricao() + " §f" + atual + "/" + missao.quantidade()));
    }

    public void esquecerAviso(UUID jogador) {
        ultimoAvisoBarra.remove(jogador);
    }

    // ---------------------------------------------------------------------
    // Persistência (progresso.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("jogadores");
        if (secao == null) return;

        LocalDate hoje = LocalDate.now();
        for (String chave : secao.getKeys(false)) {
            try {
                LocalDate dia = LocalDate.parse(secao.getString(chave + ".dia"));
                if (!dia.equals(hoje)) continue; // de outro dia: zera sozinho
                ProgressoJogador progresso = new ProgressoJogador(dia);
                progresso.setBonusPago(secao.getBoolean(chave + ".bonus-pago"));
                ConfigurationSection missoes = secao.getConfigurationSection(chave + ".missoes");
                if (missoes != null) {
                    for (String id : missoes.getKeys(false)) {
                        progresso.getProgresso().put(id, missoes.getInt(id));
                    }
                }
                jogadores.put(UUID.fromString(chave), progresso);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Progresso inválido ignorado em progresso.yml: " + chave);
            }
        }
    }

    public void salvarSeMudou() {
        if (sujo) salvar();
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        LocalDate hoje = LocalDate.now();
        for (Map.Entry<UUID, ProgressoJogador> entrada : jogadores.entrySet()) {
            ProgressoJogador p = entrada.getValue();
            if (!p.getDia().equals(hoje)) continue;
            String base = "jogadores." + entrada.getKey();
            yaml.set(base + ".dia", p.getDia().toString());
            yaml.set(base + ".bonus-pago", p.isBonusPago());
            for (Map.Entry<String, Integer> missao : p.getProgresso().entrySet()) {
                yaml.set(base + ".missoes." + missao.getKey(), missao.getValue());
            }
        }
        jogadores.values().removeIf(p -> !p.getDia().equals(hoje));
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar progresso.yml", e);
        }
    }
}
