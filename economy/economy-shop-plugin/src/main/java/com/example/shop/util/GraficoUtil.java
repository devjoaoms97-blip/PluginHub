package com.example.shop.util;

import com.example.shop.manager.PriceHistoryManager;

import java.util.List;

public class GraficoUtil {

    private static final char[] BARRAS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    /** Gera uma "sparkline" (linha de barrinhas Unicode) representando a tendência da lista de pontos. */
    public static String gerarSparkline(List<PriceHistoryManager.Ponto> pontos) {
        if (pontos.size() < 2) {
            return "§7(ainda não há dados suficientes — volte daqui a pouco)";
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (PriceHistoryManager.Ponto p : pontos) {
            min = Math.min(min, p.valor());
            max = Math.max(max, p.valor());
        }

        StringBuilder sb = new StringBuilder("§b");
        for (PriceHistoryManager.Ponto p : pontos) {
            int indice;
            if (max == min) {
                indice = BARRAS.length / 2;
            } else {
                indice = (int) Math.round((p.valor() - min) / (max - min) * (BARRAS.length - 1));
            }
            indice = Math.max(0, Math.min(BARRAS.length - 1, indice));
            sb.append(BARRAS[indice]);
        }
        return sb.toString();
    }

    public static double minimo(List<PriceHistoryManager.Ponto> pontos) {
        return pontos.stream().mapToDouble(PriceHistoryManager.Ponto::valor).min().orElse(0);
    }

    public static double maximo(List<PriceHistoryManager.Ponto> pontos) {
        return pontos.stream().mapToDouble(PriceHistoryManager.Ponto::valor).max().orElse(0);
    }

    /** Variação percentual entre o primeiro e o último ponto da janela. */
    public static double variacaoPercentual(List<PriceHistoryManager.Ponto> pontos) {
        if (pontos.size() < 2) return 0;
        double primeiro = pontos.get(0).valor();
        double ultimo = pontos.get(pontos.size() - 1).valor();
        if (primeiro == 0) return 0;
        return ((ultimo - primeiro) / primeiro) * 100.0;
    }
}
