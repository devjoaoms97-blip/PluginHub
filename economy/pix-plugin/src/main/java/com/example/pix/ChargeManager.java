package com.example.pix;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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
 * Atenção: tudo em memória — reinicia zerado se o servidor cair.
 */
public class ChargeManager {

    private static final int HISTORICO_MAX_POR_JOGADOR = 20;
    public static final long EXPIRACAO_TICKS = 20L * 60 * 5; // 5 minutos (20 ticks/segundo)

    private final PixPlugin plugin;

    // cobradoId -> (cobradorId -> cobrança)
    private final Map<UUID, Map<UUID, PixCharge>> pendentes = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<TransacaoRegistro>> historico = new ConcurrentHashMap<>();

    public ChargeManager(PixPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean existeCobranca(UUID cobrador, UUID cobrado) {
        Map<UUID, PixCharge> mapa = pendentes.get(cobrado);
        return mapa != null && mapa.containsKey(cobrador);
    }

    public PixCharge criarCobranca(UUID cobrador, UUID cobrado, double valor, String motivo) {
        PixCharge charge = new PixCharge(cobrador, cobrado, valor, motivo, System.currentTimeMillis());
        pendentes.computeIfAbsent(cobrado, k -> new ConcurrentHashMap<>()).put(cobrador, charge);
        agendarExpiracao(charge);
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

    private void agendarExpiracao(PixCharge charge) {
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
        }, EXPIRACAO_TICKS);
    }

    public void registrarTransacao(UUID de, UUID para, double valor, String tipo) {
        long agora = System.currentTimeMillis();
        adicionarAoHistorico(de, new TransacaoRegistro(de, para, valor, tipo, agora));
        adicionarAoHistorico(para, new TransacaoRegistro(de, para, valor, tipo, agora));
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
}
