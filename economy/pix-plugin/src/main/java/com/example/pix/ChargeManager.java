package com.example.pix;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;

/**
 * Guarda cobranças pendentes e histórico de transações.
 *
 * Regra: cada jogador (cobrador) só pode ter UMA cobrança ativa enviada a um
 * determinado alvo (cobrado) por vez. Por isso a cobrança é indexada pelo par
 * (cobrado -> cobrador), e pode ser referenciada só pelo nome do jogador,
 * sem precisar de um ID numérico.
 *
 * Cobranças expiram automaticamente 5 minutos depois de criadas.
 *
 * Cobranças pendentes e histórico são salvos em {@code dados.yml} a cada mudança, então
 * sobrevivem a reinícios do servidor. Ao carregar, cada cobrança retoma só o tempo de
 * expiração que faltava (as que já venceram com o servidor desligado são descartadas).
 */
public class ChargeManager {

    private static final int HISTORICO_MAX_POR_JOGADOR = 20;
    public static final long EXPIRACAO_TICKS = 20L * 60 * 5; // 5 minutos (20 ticks/segundo)

    /**
     * UUID "fictício" usado pra representar a Loja do Servidor (EconomyShopPlugin) no
     * histórico de transações — não corresponde a nenhuma conta de jogador de verdade.
     * O PixCommand reconhece esse UUID especificamente e mostra "Loja do Servidor" em
     * vez de tentar resolver um nome de jogador (que daria null).
     */
    public static final UUID ENTIDADE_LOJA = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PixPlugin plugin;
    private final File arquivo;

    // cobradoId -> (cobradorId -> cobrança)
    private final Map<UUID, Map<UUID, PixCharge>> pendentes = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<TransacaoRegistro>> historico = new ConcurrentHashMap<>();

    public ChargeManager(PixPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "dados.yml");
        carregar();
    }

    public boolean existeCobranca(UUID cobrador, UUID cobrado) {
        Map<UUID, PixCharge> mapa = pendentes.get(cobrado);
        return mapa != null && mapa.containsKey(cobrador);
    }

    public PixCharge criarCobranca(UUID cobrador, UUID cobrado, double valor, String motivo) {
        PixCharge charge = new PixCharge(cobrador, cobrado, valor, motivo, System.currentTimeMillis());
        pendentes.computeIfAbsent(cobrado, k -> new ConcurrentHashMap<>()).put(cobrador, charge);
        agendarExpiracao(charge, EXPIRACAO_TICKS);
        salvar();
        return charge;
    }

    public PixCharge getCobranca(UUID cobrador, UUID cobrado) {
        Map<UUID, PixCharge> mapa = pendentes.get(cobrado);
        return mapa == null ? null : mapa.get(cobrador);
    }

    public PixCharge removerCobranca(UUID cobrador, UUID cobrado) {
        Map<UUID, PixCharge> mapa = pendentes.get(cobrado);
        if (mapa == null) return null;
        PixCharge removida = mapa.remove(cobrador);
        if (mapa.isEmpty()) {
            pendentes.remove(cobrado);
        }
        if (removida != null) {
            salvar();
        }
        return removida;
    }

    public List<PixCharge> cobrancasRecebidasPor(UUID cobrado) {
        Map<UUID, PixCharge> mapa = pendentes.get(cobrado);
        return mapa == null ? new ArrayList<>() : new ArrayList<>(mapa.values());
    }

    public List<PixCharge> cobrancasEnviadasPor(UUID cobrador) {
        List<PixCharge> lista = new ArrayList<>();
        for (Map<UUID, PixCharge> mapa : pendentes.values()) {
            PixCharge c = mapa.get(cobrador);
            if (c != null) {
                lista.add(c);
            }
        }
        return lista;
    }

    private void agendarExpiracao(PixCharge charge, long atrasoTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PixCharge atual = getCobranca(charge.getCobradorId(), charge.getCobradoId());
            // só expira se ainda for exatamente essa mesma cobrança (não foi paga/recusada/cancelada antes)
            if (atual == charge) {
                removerCobranca(charge.getCobradorId(), charge.getCobradoId());

                OfflinePlayer cobrador = Bukkit.getOfflinePlayer(charge.getCobradorId());
                OfflinePlayer cobrado = Bukkit.getOfflinePlayer(charge.getCobradoId());
                String valorFmt = String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", charge.getValor());

                if (cobrado.isOnline()) {
                    Player p = cobrado.getPlayer();
                    p.sendMessage("§7A cobrança de §f" + cobrador.getName() + " §7(" + valorFmt + ") expirou.");
                }
                if (cobrador.isOnline()) {
                    Player p = cobrador.getPlayer();
                    p.sendMessage("§7Sua cobrança para §f" + cobrado.getName() + " §7(" + valorFmt + ") expirou sem resposta.");
                }
            }
        }, atrasoTicks);
    }

    public void registrarTransacao(UUID de, UUID para, double valor, String tipo) {
        long agora = System.currentTimeMillis();
        adicionarAoHistorico(de, new TransacaoRegistro(de, para, valor, tipo, agora));
        adicionarAoHistorico(para, new TransacaoRegistro(de, para, valor, tipo, agora));
        salvar();
    }

    /**
     * Registra uma transação entre um jogador e a Loja do Servidor (compra/venda no
     * EconomyShopPlugin), pra unificar tudo no mesmo extrato do /pix historico.
     * Só adiciona a entrada pro jogador (a "loja" não é uma conta de verdade).
     */
    public void registrarTransacaoComLoja(UUID jogador, double valor, boolean jogadorRecebeu, String tipo) {
        long agora = System.currentTimeMillis();
        UUID de = jogadorRecebeu ? ENTIDADE_LOJA : jogador;
        UUID para = jogadorRecebeu ? jogador : ENTIDADE_LOJA;
        adicionarAoHistorico(jogador, new TransacaoRegistro(de, para, valor, tipo, agora));
        salvar();
    }

    private void adicionarAoHistorico(UUID jogador, TransacaoRegistro registro) {
        Deque<TransacaoRegistro> deque = historico.computeIfAbsent(jogador, k -> new ConcurrentLinkedDeque<>());
        deque.addFirst(registro);
        while (deque.size() > HISTORICO_MAX_POR_JOGADOR) {
            deque.removeLast();
        }
    }

    public List<TransacaoRegistro> getHistorico(UUID jogador, int quantidade) {
        Deque<TransacaoRegistro> deque = historico.get(jogador);
        List<TransacaoRegistro> resultado = new ArrayList<>();
        if (deque == null) {
            return resultado;
        }
        int i = 0;
        for (TransacaoRegistro registro : deque) {
            if (i >= quantidade) break;
            resultado.add(registro);
            i++;
        }
        return resultado;
    }

    // ---------------------------------------------------------------------
    // Persistência (dados.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        long agora = System.currentTimeMillis();
        long expiracaoMs = EXPIRACAO_TICKS * 50L;
        int cobrancasCarregadas = 0;

        ConfigurationSection secaoCobrancas = yaml.getConfigurationSection("cobrancas");
        if (secaoCobrancas != null) {
            for (String chave : secaoCobrancas.getKeys(false)) {
                ConfigurationSection c = secaoCobrancas.getConfigurationSection(chave);
                if (c == null) continue;
                try {
                    PixCharge charge = new PixCharge(
                            UUID.fromString(c.getString("cobrador")),
                            UUID.fromString(c.getString("cobrado")),
                            c.getDouble("valor"),
                            c.getString("motivo"),
                            c.getLong("criado-em"));
                    long restanteMs = charge.getCriadoEm() + expiracaoMs - agora;
                    if (restanteMs <= 0) continue; // venceu com o servidor desligado

                    pendentes.computeIfAbsent(charge.getCobradoId(), k -> new ConcurrentHashMap<>())
                            .put(charge.getCobradorId(), charge);
                    agendarExpiracao(charge, Math.max(1L, restanteMs / 50L));
                    cobrancasCarregadas++;
                } catch (IllegalArgumentException | NullPointerException e) {
                    plugin.getLogger().warning("Cobrança inválida ignorada em dados.yml: " + chave);
                }
            }
        }

        ConfigurationSection secaoHistorico = yaml.getConfigurationSection("historico");
        if (secaoHistorico != null) {
            for (String jogadorId : secaoHistorico.getKeys(false)) {
                ConfigurationSection doJogador = secaoHistorico.getConfigurationSection(jogadorId);
                if (doJogador == null) continue;
                try {
                    Deque<TransacaoRegistro> deque = new ConcurrentLinkedDeque<>();
                    // salvo do mais recente (0) pro mais antigo, mesma ordem do deque
                    for (String indice : doJogador.getKeys(false)) {
                        ConfigurationSection r = doJogador.getConfigurationSection(indice);
                        if (r == null) continue;
                        deque.addLast(new TransacaoRegistro(
                                UUID.fromString(r.getString("de")),
                                UUID.fromString(r.getString("para")),
                                r.getDouble("valor"),
                                r.getString("tipo"),
                                r.getLong("data")));
                        if (deque.size() >= HISTORICO_MAX_POR_JOGADOR) break;
                    }
                    if (!deque.isEmpty()) {
                        historico.put(UUID.fromString(jogadorId), deque);
                    }
                } catch (IllegalArgumentException | NullPointerException e) {
                    plugin.getLogger().warning("Histórico inválido ignorado em dados.yml: " + jogadorId);
                }
            }
        }

        plugin.getLogger().info(cobrancasCarregadas + " cobrança(s) pendente(s) e histórico de "
                + historico.size() + " jogador(es) carregados de dados.yml.");
    }

    public synchronized void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();

        int i = 0;
        for (Map<UUID, PixCharge> mapa : pendentes.values()) {
            for (PixCharge c : mapa.values()) {
                String base = "cobrancas." + i++;
                yaml.set(base + ".cobrador", c.getCobradorId().toString());
                yaml.set(base + ".cobrado", c.getCobradoId().toString());
                yaml.set(base + ".valor", c.getValor());
                yaml.set(base + ".motivo", c.getMotivo());
                yaml.set(base + ".criado-em", c.getCriadoEm());
            }
        }

        for (Map.Entry<UUID, Deque<TransacaoRegistro>> entrada : historico.entrySet()) {
            int j = 0;
            for (TransacaoRegistro r : entrada.getValue()) {
                String base = "historico." + entrada.getKey() + "." + j++;
                yaml.set(base + ".de", r.getDe().toString());
                yaml.set(base + ".para", r.getPara().toString());
                yaml.set(base + ".valor", r.getValor());
                yaml.set(base + ".tipo", r.getTipo());
                yaml.set(base + ".data", r.getData());
            }
        }

        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar dados.yml", e);
        }
    }
}
