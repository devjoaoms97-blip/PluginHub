package com.example.missoes.model;

import java.util.Set;

/**
 * Uma missão do pool configurado no config.yml (o "modelo" — o progresso de cada jogador
 * fica no {@link ProgressoJogador}).
 *
 * @param alvos nomes de material/entidade em maiúsculo; vazio = qualquer um conta
 * @param skill nome da skill do SkillsPlugin que recebe {@code xp} (ou null pra só coins)
 */
public record Missao(String id, TipoMissao tipo, Set<String> alvos, int quantidade, String descricao,
                     double coins, String skill, double xp) {

    public boolean aceita(String alvo) {
        return alvos.isEmpty() || alvos.contains(alvo);
    }
}
