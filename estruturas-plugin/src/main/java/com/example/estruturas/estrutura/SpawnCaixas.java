package com.example.estruturas.estrutura;

import java.util.List;

/**
 * Só a Praça das Caixas do spawn "spring" (2026-11-18), no quadrante nordeste: gera o spawn inteiro e grava só
 * o círculo da praça, pra pôr as caixas num spawn que já existe sem mexer no resto (as barracas e os aluguéis
 * ficam). Cadastra as caixas no CaixasPlugin. Use a MESMA origem do spawn: no mapa do servidor,
 * {@code /estrutura construir spring-caixas spawn 0 65 43}.
 */
public class SpawnCaixas implements Estrutura {

    @Override
    public String getId() {
        return "spring-caixas";
    }

    @Override
    public String getNome() {
        return "Praça das Caixas do Spawn";
    }

    @Override
    public void gerar(Planta planta) {
        Planta spawn = new Planta();
        new ArenaSpringColiseu().gerar(spawn);
        for (Planta.Bloco b : spawn.getBlocos()) {
            if (PracaDasCaixas.dentro(b.x(), b.z()) && b.y() >= -1) planta.set(b.x(), b.y(), b.z(), b.dados());
        }
    }

    @Override
    public List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return PracaDasCaixas.comandos(mundo, ox, oy, oz);
    }
}
