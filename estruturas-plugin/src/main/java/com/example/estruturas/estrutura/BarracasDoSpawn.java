package com.example.estruturas.estrutura;

import java.util.List;

/**
 * Só as 32 barracas do spawn "spring", pra refazer elas sem reconstruir o spawn inteiro. Use a
 * MESMA origem do spawn (o centro da fonte): no mapa do servidor, /estrutura construir
 * spring-barracas spawn 0 65 50. Cada barraca é redesenhada no próprio cubo (o NPC e o aluguel
 * continuam), e as que não estiverem cadastradas no BarracasPlugin são cadastradas.
 */
public class BarracasDoSpawn implements Estrutura {

    @Override
    public String getId() {
        return "spring-barracas";
    }

    @Override
    public String getNome() {
        return "Barracas do Spawn Primavera";
    }

    @Override
    public void gerar(Planta planta) {
        new ArenaSpringColiseu().gerarBarracas(planta);
    }

    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return ArenaSpringColiseu.comandosDasBarracas(mundo, ox, oy, oz);
    }
}
