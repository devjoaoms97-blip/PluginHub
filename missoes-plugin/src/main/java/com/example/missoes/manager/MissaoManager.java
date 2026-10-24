package com.example.missoes.manager;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.Missao;
import com.example.missoes.model.Periodo;
import com.example.missoes.model.ProgressoJogador;
import com.example.missoes.model.TipoMissao;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
 * Pools de missões (do config.yml) e o progresso de cada jogador, em dois períodos:
 * <ul>
 *   <li><b>Diárias</b> ({@code pool}): {@code missoes-por-dia} sorteadas; zeram à meia-noite.</li>
 *   <li><b>Semanais</b> ({@code semanais.pool}): {@code semanais.quantidade} sorteadas; zeram na
 *       virada de domingo pra segunda. Maiores, com recompensa melhor.</li>
 * </ul>
 * No primeiro acesso do período, o jogador recebe as missões sorteadas (sem repetir) do pool.
 * Completar uma missão paga na hora; completar todas do período paga o bônus daquele período.
 *
 * O progresso é gravado em {@code progresso.yml} de tempos em tempos (não a cada bloco
 * quebrado) e no desligamento.
 */
public class MissaoManager {

    private final MissoesPlugin plugin;
    private final File arquivo;
    private final Map<Periodo, Map<String, Missao>> pools = new EnumMap<>(Periodo.class);
    private final Map<Periodo, Map<UUID, ProgressoJogador>> jogadores = new EnumMap<>(Periodo.class);
    private final Map<UUID, Long> ultimoAvisoBarra = new HashMap<>();

    // Anti-farm da missão de vender: quanto de cada material o jogador COMPROU na loja hoje.
    // Vender isso de volta não conta (senão era só comprar e revender). Em memória, zera por dia.
    private final Map<UUID, Map<String, Integer>> compradosHoje = new HashMap<>();
    private LocalDate diaDasCompras = LocalDate.now();

    private boolean sujo;

    public MissaoManager(MissoesPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "progresso.yml");
        for (Periodo periodo : Periodo.values()) {
            pools.put(periodo, new LinkedHashMap<>());
            jogadores.put(periodo, new HashMap<>());
        }
        carregarPool();
        carregar();
    }

    // ---------------------------------------------------------------------
    // Pools
    // ---------------------------------------------------------------------
    public void carregarPool() {
        lerPool(Periodo.DIARIA, plugin.getConfig().getMapList("pool"));
        lerPool(Periodo.SEMANAL, semanaisAtivas() ? plugin.getConfig().getMapList("semanais.pool") : List.of());
        if (pools.get(Periodo.DIARIA).isEmpty()) {
            plugin.getLogger().warning("O pool de missões diárias está vazio — ninguém vai receber missão diária.");
        }
    }

    private void lerPool(Periodo periodo, List<Map<?, ?>> lista) {
        Map<String, Missao> pool = pools.get(periodo);
        pool.clear();
        for (Map<?, ?> bruto : lista) {
            try {
                String id = String.valueOf(bruto.get("id")).toLowerCase(Locale.ROOT);
                TipoMissao tipo = TipoMissao.de(String.valueOf(bruto.get("tipo")));
                if (tipo == null) throw new IllegalArgumentException("tipo inválido: " + bruto.get("tipo"));
                int quantidade = ((Number) bruto.get("quantidade")).intValue();
                if (quantidade < 1) throw new IllegalArgumentException("quantidade precisa ser >= 1");

                Set<String> alvos = new HashSet<>();
                Object brutoAlvos = bruto.get("alvos");
                if (brutoAlvos instanceof List<?> nomes) {
                    for (Object alvo : nomes) alvos.add(String.valueOf(alvo).toUpperCase(Locale.ROOT));
                }

                Object descricao = bruto.get("descricao");
                Object coins = bruto.get("coins");
                Object skill = bruto.get("skill");
                Object xp = bruto.get("xp");
                Object rankMinimo = bruto.get("rank-minimo");
                pool.put(id, new Missao(id, periodo, tipo, alvos, quantidade,
                        descricao != null ? String.valueOf(descricao) : tipo.name().toLowerCase(Locale.ROOT) + " " + quantidade,
                        coins instanceof Number n ? n.doubleValue() : 0,
                        skill != null ? String.valueOf(skill).toUpperCase(Locale.ROOT) : null,
                        xp instanceof Number n ? n.doubleValue() : 0,
                        rankMinimo != null ? String.valueOf(rankMinimo).toLowerCase(Locale.ROOT) : null));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Missão " + (periodo == Periodo.SEMANAL ? "semanal" : "diária")
                        + " inválida ignorada: " + bruto + " (" + e.getMessage() + ")");
            }
        }
    }

    public boolean semanaisAtivas() {
        return plugin.getConfig().getBoolean("semanais.ativado", true);
    }

    public Map<String, Missao> getPool(Periodo periodo) {
        return pools.get(periodo);
    }

    public Missao getMissao(Periodo periodo, String id) {
        return pools.get(periodo).get(id);
    }

    /** Seção do config com o bônus por completar todas do período. */
    public String chaveBonus(Periodo periodo) {
        return periodo == Periodo.DIARIA ? "bonus-todas" : "semanais.bonus-todas";
    }

    // ---------------------------------------------------------------------
    // Progresso
    // ---------------------------------------------------------------------

    /** As missões do período atual do jogador, sorteando se for o primeiro acesso do período. */
    public ProgressoJogador getAtual(UUID jogador, Periodo periodo) {
        LocalDate inicio = periodo.inicio(LocalDate.now());
        ProgressoJogador atual = jogadores.get(periodo).get(jogador);
        if (atual != null && atual.getDia().equals(inicio)) {
            if (periodo == Periodo.DIARIA) completarExtras(jogador, atual);
            return atual;
        }
        ProgressoJogador novo = sortear(periodo, inicio, jogador);
        jogadores.get(periodo).put(jogador, novo);
        sujo = true;
        return novo;
    }

    /**
     * Quem virou VIP depois do sorteio do dia ganha na hora as missões extras que faltam, sem
     * perder o progresso das que já tinha.
     */
    private void completarExtras(UUID jogador, ProgressoJogador atual) {
        int faltam = quantasDiarias(jogador) - atual.getProgresso().size();
        if (faltam <= 0) return;
        List<String> ids = disponiveis(Periodo.DIARIA, jogador);
        ids.removeAll(atual.getProgresso().keySet());
        Collections.shuffle(ids);
        for (String id : ids.subList(0, Math.min(faltam, ids.size()))) {
            atual.getProgresso().put(id, 0);
            sujo = true;
        }
    }

    /** missoes-por-dia mais as extras de VIP (a maior que o jogador tiver, se estiver online). */
    private int quantasDiarias(UUID jogador) {
        int base = Math.max(1, plugin.getConfig().getInt("missoes-por-dia", 3));
        Player online = Bukkit.getPlayer(jogador);
        if (online == null || !plugin.getConfig().getBoolean("vip-missoes-extras.ativado", true)) return base;
        int extras = 0;
        List<?> permissoes = plugin.getConfig().getList("vip-missoes-extras.permissoes");
        if (permissoes != null) {
            for (Object item : permissoes) {
                if (item instanceof Map<?, ?> mapa && mapa.get("permissao") instanceof String permissao
                        && mapa.get("quantidade") instanceof Number quantidade && online.hasPermission(permissao)) {
                    extras = Math.max(extras, quantidade.intValue());
                }
            }
        }
        return base + extras;
    }

    /** Descarta as missões do período atual do jogador e sorteia outras (staff). */
    public ProgressoJogador resortear(UUID jogador, Periodo periodo) {
        ProgressoJogador novo = sortear(periodo, periodo.inicio(LocalDate.now()), jogador);
        jogadores.get(periodo).put(jogador, novo);
        sujo = true;
        return novo;
    }

    /** Ids do pool que o jogador pode receber: as de {@code rank-minimo} só se ele já tiver o rank. */
    private List<String> disponiveis(Periodo periodo, UUID jogador) {
        List<String> ids = new ArrayList<>();
        for (Missao missao : pools.get(periodo).values()) {
            if (missao.rankMinimo() == null || plugin.getRankupIntegration().temAcesso(jogador, missao.rankMinimo())) {
                ids.add(missao.id());
            }
        }
        return ids;
    }

    private ProgressoJogador sortear(Periodo periodo, LocalDate inicio, UUID jogador) {
        List<String> ids = disponiveis(periodo, jogador);
        Collections.shuffle(ids);
        int quantas = periodo == Periodo.DIARIA
                ? quantasDiarias(jogador)
                : Math.max(1, plugin.getConfig().getInt("semanais.quantidade", 2));
        ProgressoJogador progresso = new ProgressoJogador(inicio);
        for (String id : ids.subList(0, Math.min(quantas, ids.size()))) {
            progresso.getProgresso().put(id, 0);
        }
        return progresso;
    }

    /**
     * Soma {@code quantidade} em toda missão atual (diária e semanal) do jogador que for desse
     * tipo e aceitar esse alvo. Paga a recompensa quando alguma completa.
     */
    public void registrar(Player jogador, TipoMissao tipo, String alvo, int quantidade) {
        if (quantidade <= 0) return;
        if (tipo == TipoMissao.VENDER) {
            quantidade = descontarComprados(jogador.getUniqueId(), alvo, quantidade);
            if (quantidade <= 0) return;
        }
        for (Periodo periodo : Periodo.values()) {
            if (periodo == Periodo.SEMANAL && !semanaisAtivas()) continue;
            registrar(jogador, periodo, tipo, alvo, quantidade);
        }
    }

    private void registrar(Player jogador, Periodo periodo, TipoMissao tipo, String alvo, int quantidade) {
        ProgressoJogador atual = getAtual(jogador.getUniqueId(), periodo);
        Map<String, Missao> pool = pools.get(periodo);

        for (Map.Entry<String, Integer> entrada : atual.getProgresso().entrySet()) {
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

        if (!atual.isBonusPago() && todasConcluidas(atual, periodo)) {
            atual.setBonusPago(true);
            pagarBonus(jogador, periodo);
        }
    }

    public boolean todasConcluidas(ProgressoJogador atual, Periodo periodo) {
        if (atual.getProgresso().isEmpty()) return false;
        for (Map.Entry<String, Integer> entrada : atual.getProgresso().entrySet()) {
            Missao missao = pools.get(periodo).get(entrada.getKey());
            if (missao != null && entrada.getValue() < missao.quantidade()) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Anti-farm de compra e revenda na loja
    // ---------------------------------------------------------------------
    public void registrarCompra(UUID jogador, String material, int quantidade) {
        if (quantidade <= 0) return;
        zerarComprasSeVirouODia();
        compradosHoje.computeIfAbsent(jogador, k -> new HashMap<>()).merge(material, quantidade, Integer::sum);
    }

    /** Desconta da venda o que o jogador comprou desse material hoje; retorna o que sobra pra contar. */
    private int descontarComprados(UUID jogador, String material, int vendidos) {
        zerarComprasSeVirouODia();
        Map<String, Integer> doJogador = compradosHoje.get(jogador);
        if (doJogador == null) return vendidos;
        int comprados = doJogador.getOrDefault(material, 0);
        int abatidos = Math.min(comprados, vendidos);
        if (comprados - abatidos > 0) {
            doJogador.put(material, comprados - abatidos);
        } else {
            doJogador.remove(material);
        }
        return vendidos - abatidos;
    }

    private void zerarComprasSeVirouODia() {
        LocalDate hoje = LocalDate.now();
        if (!hoje.equals(diaDasCompras)) {
            compradosHoje.clear();
            diaDasCompras = hoje;
        }
    }

    // ---------------------------------------------------------------------
    // Recompensas
    // ---------------------------------------------------------------------
    private void concluir(Player jogador, Missao missao) {
        String recompensa = pagar(jogador, missao.coins(), missao.skill(), missao.xp(),
                missao.periodo() == Periodo.DIARIA ? "Missão diária" : "Missão semanal");
        jogador.sendMessage("§a§l✔ Missão " + (missao.periodo() == Periodo.SEMANAL ? "semanal " : "") + "concluída: §f"
                + missao.descricao() + (recompensa.isEmpty() ? "" : " §7(" + recompensa + "§7)"));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void pagarBonus(Player jogador, Periodo periodo) {
        String chave = chaveBonus(periodo);
        double coins = plugin.getConfig().getDouble(chave + ".coins", 0);
        String skill = plugin.getConfig().getString(chave + ".skill");
        double xp = plugin.getConfig().getDouble(chave + ".xp", 0);
        String recompensa = pagar(jogador, coins, skill, xp,
                periodo == Periodo.DIARIA ? "Bônus das missões diárias" : "Bônus das missões semanais");
        if (recompensa.isEmpty()) return;
        jogador.sendMessage("§6§l★ Todas as missões " + (periodo == Periodo.DIARIA ? "de hoje" : "da semana")
                + " concluídas! §6Bônus: " + recompensa);
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /** Entrega coins e XP de skill; retorna a descrição do que foi entregue (vazio se nada). */
    private String pagar(Player jogador, double coins, String skill, double xp, String tipoExtrato) {
        List<String> partes = new ArrayList<>();
        Economy econ = plugin.getEconomy();
        if (coins > 0) {
            if (econ != null) {
                econ.depositPlayer(jogador, coins);
                plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), coins, true, tipoExtrato);
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
    // Persistência (progresso.yml): "jogadores" = diárias, "semanais" = semanais
    // ---------------------------------------------------------------------
    private String secaoDo(Periodo periodo) {
        return periodo == Periodo.DIARIA ? "jogadores" : "semanais";
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        LocalDate hoje = LocalDate.now();

        for (Periodo periodo : Periodo.values()) {
            ConfigurationSection secao = yaml.getConfigurationSection(secaoDo(periodo));
            if (secao == null) continue;
            LocalDate inicio = periodo.inicio(hoje);
            for (String chave : secao.getKeys(false)) {
                try {
                    LocalDate dia = LocalDate.parse(secao.getString(chave + ".dia"));
                    if (!dia.equals(inicio)) continue; // de outro período: zera sozinho
                    ProgressoJogador progresso = new ProgressoJogador(dia);
                    progresso.setBonusPago(secao.getBoolean(chave + ".bonus-pago"));
                    ConfigurationSection missoes = secao.getConfigurationSection(chave + ".missoes");
                    if (missoes != null) {
                        for (String id : missoes.getKeys(false)) {
                            progresso.getProgresso().put(id, missoes.getInt(id));
                        }
                    }
                    jogadores.get(periodo).put(UUID.fromString(chave), progresso);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Progresso inválido ignorado em progresso.yml: " + chave);
                }
            }
        }
    }

    public void salvarSeMudou() {
        if (sujo) salvar();
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        LocalDate hoje = LocalDate.now();
        for (Periodo periodo : Periodo.values()) {
            LocalDate inicio = periodo.inicio(hoje);
            Map<UUID, ProgressoJogador> doPeriodo = jogadores.get(periodo);
            doPeriodo.values().removeIf(p -> !p.getDia().equals(inicio));
            for (Map.Entry<UUID, ProgressoJogador> entrada : doPeriodo.entrySet()) {
                ProgressoJogador p = entrada.getValue();
                String base = secaoDo(periodo) + "." + entrada.getKey();
                yaml.set(base + ".dia", p.getDia().toString());
                yaml.set(base + ".bonus-pago", p.isBonusPago());
                for (Map.Entry<String, Integer> missao : p.getProgresso().entrySet()) {
                    yaml.set(base + ".missoes." + missao.getKey(), missao.getValue());
                }
            }
        }
        try {
            yaml.save(arquivo);
            sujo = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar progresso.yml", e);
        }
    }
}
