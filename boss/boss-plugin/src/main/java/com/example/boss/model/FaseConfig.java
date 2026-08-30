package com.example.boss.model;

import java.util.Locale;
import java.util.Map;

/**
 * Representa uma "ação configurada" no config.yml. Usada em dois contextos:
 * - Como uma FASE fixa (tem "tipo" + "valor"/"segundos"): dispara em um marco específico
 *   de % de vida ou intervalo de tempo.
 * - Como uma entrada do ARSENAL ALEATÓRIO (só tem "acao" + parâmetros, sem "tipo"):
 *   nesse caso o gatilho fica null — a escolha de qual disparar é aleatória, feita pelo
 *   BossManager, não por um marco fixo.
 */
public class FaseConfig {

    private final TipoGatilhoFase gatilho; // null quando é uma entrada do arsenal aleatório
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
        TipoGatilhoFase gatilho = null;
        double valor = 0;

        Object tipoObj = mapa.get("tipo");
        if (tipoObj != null) {
            gatilho = TipoGatilhoFase.valueOf(String.valueOf(tipoObj).toUpperCase(Locale.ROOT));
            valor = gatilho == TipoGatilhoFase.PERCENTUAL_VIDA
                    ? numeroDe(mapa.get("valor"))
                    : numeroDe(mapa.get("segundos"));
        }

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
