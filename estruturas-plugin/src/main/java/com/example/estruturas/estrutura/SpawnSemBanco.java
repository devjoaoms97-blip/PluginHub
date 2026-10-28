package com.example.estruturas.estrutura;

import org.bukkit.Material;

/**
 * Tira a barraquinha antiga do Bancário do spawn "spring" (ele foi pro fim da Avenida das Minas, na
 * estrutura "banco"): gera o spawn inteiro e grava só o pedaço onde ela ficava (x 26..34, z -18..-10 a
 * partir do centro), com o chão, o poste e a grama de antes. Use a MESMA origem do spawn: no mapa do
 * servidor, {@code /estrutura construir spring-banco spawn 0 65 43}.
 */
public class SpawnSemBanco implements Estrutura {

    @Override
    public String getId() {
        return "spring-banco";
    }

    @Override
    public String getNome() {
        return "Spawn sem a barraquinha do Bancário";
    }

    @Override
    public void gerar(Planta planta) {
        for (int x = 26; x <= 34; x++) {
            for (int z = -18; z <= -10; z++) {
                for (int y = 1; y <= 8; y++) planta.set(x, y, z, Material.AIR);
            }
        }
        Planta spawn = new Planta();
        new ArenaSpringColiseu().gerar(spawn);
        for (Planta.Bloco b : spawn.getBlocos()) {
            if (b.x() >= 26 && b.x() <= 34 && b.z() >= -18 && b.z() <= -10) planta.set(b.x(), b.y(), b.z(), b.dados());
        }
    }
}
