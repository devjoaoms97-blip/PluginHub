package com.example.missoes.model;

import java.util.Set;

/**
 * Uma missão de um pool do config.yml (o "modelo" — o progresso de cada jogador fica no
 * {@link ProgressoJogador}). O id é único dentro do pool do seu {@link Periodo}.
 *
 * @param alvos nomes de material/entidade em maiúsculo; vazio = qualquer um conta
 * @param skill nome da skill do SkillsPlugin que recebe {@code xp} (ou null pra só coins)
 * @param rankMinimo id do rank do RankupPlugin a partir do qual a missão pode ser sorteada (null = qualquer um)
 */
public record Missao(String id, Periodo periodo, TipoMissao tipo, Set<String> alvos, int quantidade, String descricao,
                     double coins, String skill, double xp, String rankMinimo) {

    public boolean aceita(String alvo) {
        return alvos.isEmpty() || alvos.contains(alvo);
    }
}
