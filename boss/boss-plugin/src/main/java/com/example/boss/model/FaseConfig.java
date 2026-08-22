package com.example.boss.model;

import java.util.Locale;
import java.util.Map;

/**
 * Representa uma fase configurada no config.yml — um gatilho (percentual de vida ou
 * intervalo de tempo) associado a uma ação (invocar mobs, efeito de área, buff no boss,
 * ou mensagem). Os parâmetros específicos de cada ação ficam guardados no mapa bruto,
 * já que cada tipo de ação usa campos diferentes.
 */
public class FaseConfig {

    private final TipoGatilhoFase gatilho;
    private final double valorGatilho;
    private final TipoAcaoFase acao;
    private final Map<?, ?> parametros;

    private FaseConfig(TipoGatilhoFase gatilho, double valorGatilho, TipoAcaoFase acao, Map<?, ?> parametros) {
        this.gatilho = gatilho;
        this.valorGatilho = valorGatilho;
        this.acao = acao;
        this.parametros = parametros;
    }

    public static FaseConfig deConfig(Map<?, ?> mapa) {
        String tipoStr = String.valueOf(mapa.get("tipo")).toUpperCase(Locale.ROOT);
        TipoGatilhoFase gatilho = TipoGatilhoFase.valueOf(tipoStr);

        double valor = gatilho == TipoGatilhoFase.PERCENTUAL_VIDA
                ? numeroDe(mapa.get("valor"))
                : numeroDe(mapa.get("segundos"));

        String acaoStr = String.valueOf(mapa.get("acao")).toUpperCase(Locale.ROOT);
        TipoAcaoFase acao = TipoAcaoFase.valueOf(acaoStr);

        return new FaseConfig(gatilho, valor, acao, mapa);
    }

    private static double numeroDe(Object valor) {
        if (valor instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException("Valor numérico ausente ou inválido: " + valor);
    }

    public TipoGatilhoFase getGatilho() {
        return gatilho;
    }

    public double getValorGatilho() {
        return valorGatilho;
    }

    public TipoAcaoFase getAcao() {
        return acao;
    }

    public String getParametro(String chave, String padrao) {
        Object valor = parametros.get(chave);
        return valor != null ? String.valueOf(valor) : padrao;
    }

    public double getParametroNumero(String chave, double padrao) {
        Object valor = parametros.get(chave);
        return valor instanceof Number n ? n.doubleValue() : padrao;
    }
}
