package com.example.estruturas.estrutura;

/**
 * Só o pedaço oeste do spawn "spring" (o portão "MOBS", o túnel e o fim da avenida oeste), pra abrir
 * o portão num spawn que já existe sem reconstruir o resto (as barracas, os aluguéis e tudo mais
 * ficam como estão). Gera o spawn inteiro e grava só o que cai em x <= -35 e |z| <= 19. Use a
 * MESMA origem do spawn: no mapa do servidor, /estrutura construir spring-oeste spawn 0 65 43.
 */
public class PortaoOesteDoSpawn implements Estrutura {

    @Override
    public String getId() {
        return "spring-oeste";
    }

    @Override
    public String getNome() {
        return "Portão oeste do Spawn (MOBS)";
    }

    @Override
    public void gerar(Planta planta) {
        Planta spawn = new Planta();
        new ArenaSpringColiseu().gerar(spawn);
        for (Planta.Bloco b : spawn.getBlocos()) {
            if (b.x() <= -35 && Math.abs(b.z()) <= 19) planta.set(b.x(), b.y(), b.z(), b.dados());
        }
    }
}
