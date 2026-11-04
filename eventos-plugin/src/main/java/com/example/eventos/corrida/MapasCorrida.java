package com.example.eventos.corrida;

import java.util.List;
import java.util.function.Supplier;

/**
 * Os 5 mapas da Corrida Maluca, um do lado do outro (a cada {@link #ESPACO} blocos em x a partir do
 * {@code inicio}). Todos têm 3 fases, cada uma mais difícil, 2 checkpoints por fase e uns 450 blocos; o que
 * muda é a ordem e a combinação dos obstáculos e o tema. A cada corrida, um é sorteado ({@code corrida.mapas}).
 */
public final class MapasCorrida {

    /** A distância em x entre o meio de um mapa e o do seguinte (o pavilhão tem 33 de largura). */
    static final int ESPACO = 40;

    /** Um mapa: o id (no config), o tema e o traçado. */
    record Mapa(String id, TemaCorrida tema, Supplier<TracadoCorrida> tracado) {
    }

    static final List<Mapa> TODOS = List.of(
            new Mapa("parque", TemaCorrida.PARQUE, MapasCorrida::parque),
            new Mapa("doces", TemaCorrida.DOCES, MapasCorrida::doces),
            new Mapa("gelo", TemaCorrida.GELO, MapasCorrida::gelo),
            new Mapa("neon", TemaCorrida.NEON, MapasCorrida::neon),
            new Mapa("selva", TemaCorrida.SELVA, MapasCorrida::selva));

    private MapasCorrida() {
    }

    /** O primeiro: portas, trampolim e gelo; martelos e carrossel; parkour, vento e o pula-pula no fim. */
    private static TracadoCorrida parque() {
        return new TracadoCorrida()
                .fase(0).listras(5).portas().listras(5).portas().listras(5).trampolim().listras(3).gelo(12).checkpoint()
                .listras(2).plataformas(4).listras(2).tapete(16, 3).checkpoint()
                .listras(2).pulaPula(3).listras(7).disco().listras(3).checkpoint()
                .fase(1).listras(2).portas().listras(4).portas().listras(4).portas().listras(4).martelos(3).listras(3).gelo(16).checkpoint()
                .carrossel().plataformas(5).listras(2).tapete(20, 2).checkpoint()
                .fase(2).listras(2).parkour(10).listras(3).discoDuplo().listras(3).checkpoint()
                .vento(9).listras(3).martelos(3).listras(4).pulaPula(4).listras(10).portas().listras(5).portas().listras(5)
                .chegada();
    }

    /** Doces: começa pulando (trampolim, plataformas e pula-pula); no meio, carrossel e parkour. */
    private static TracadoCorrida doces() {
        return new TracadoCorrida()
                .fase(0).listras(5).trampolim().listras(3).plataformas(4).listras(2).checkpoint()
                .listras(2).pulaPula(3).listras(4).portas().listras(4).portas().listras(3).gelo(12).checkpoint()
                .fase(1).listras(2).carrossel().listras(3).martelos(3).listras(3).tapete(20, 2).checkpoint()
                .listras(2).parkour(8).listras(3).portas().listras(4).portas().listras(4).portas().listras(3).checkpoint()
                .fase(2).listras(2).discoDuplo().listras(3).vento(9).listras(3).checkpoint()
                .listras(2).plataformas(5).listras(2).martelos(3).listras(3).checkpoint()
                .listras(2).pulaPula(4).listras(5).portas().listras(4).portas().listras(4)
                .chegada();
    }

    /** Polo Norte: muito gelo (um trecho por fase), com tapete, discos e martelos no meio. */
    private static TracadoCorrida gelo() {
        return new TracadoCorrida()
                .fase(0).listras(5).portas().listras(4).portas().listras(3).gelo(12).checkpoint()
                .tapete(16, 3).listras(3).disco().listras(3).trampolim().listras(3).checkpoint()
                .fase(1).listras(2).plataformas(5).listras(2).pulaPula(3).listras(4).gelo(16).checkpoint()
                .martelos(3).listras(3).carrossel().listras(2).checkpoint()
                .fase(2).parkour(10).listras(3).vento(9).listras(3).checkpoint()
                .gelo(12).listras(2).discoDuplo().listras(3).checkpoint()
                .martelos(3).listras(3).portas().listras(4).portas().listras(4)
                .chegada();
    }

    /** Neon: abre com um disco; martelos já na fase 1; a fase 3 fecha com 3 fileiras de portas. */
    private static TracadoCorrida neon() {
        return new TracadoCorrida()
                .fase(0).listras(5).disco().listras(3).portas().listras(4).portas().listras(3).trampolim().listras(3).checkpoint()
                .plataformas(4).listras(2).martelos(3).listras(3).checkpoint()
                .fase(1).listras(2).tapete(16, 2).listras(3).pulaPula(3).listras(4).carrossel().listras(2).checkpoint()
                .parkour(8).listras(3).vento(6).listras(3).checkpoint()
                .fase(2).listras(2).plataformas(5).listras(2).discoDuplo().listras(3).checkpoint()
                .martelos(3).listras(3).gelo(16).checkpoint()
                .pulaPula(4).listras(5).portas().listras(4).portas().listras(4).portas().listras(4)
                .chegada();
    }

    /** Selva: abre com o pula-pula e o parkour; vento e parkour de novo na fase 3. */
    private static TracadoCorrida selva() {
        return new TracadoCorrida()
                .fase(0).listras(5).pulaPula(3).listras(4).portas().listras(4).portas().listras(3).checkpoint()
                .parkour(6).listras(3).tapete(16, 3).listras(3).checkpoint()
                .fase(1).listras(2).disco().listras(3).martelos(3).listras(3).plataformas(5).listras(2).checkpoint()
                .trampolim().listras(3).gelo(16).listras(2).carrossel().listras(2).checkpoint()
                .fase(2).listras(2).vento(9).listras(3).parkour(10).listras(3).checkpoint()
                .discoDuplo().listras(3).martelos(3).listras(3).checkpoint()
                .pulaPula(4).listras(5).portas().listras(4).portas().listras(4)
                .chegada();
    }
}
