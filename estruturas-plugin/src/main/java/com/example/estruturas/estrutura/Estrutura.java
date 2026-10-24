package com.example.estruturas.estrutura;

import java.util.List;

/**
 * Uma estrutura que o {@code /estrutura construir <id>} sabe montar. A planta é relativa ao bloco
 * do chão sob o admin: x/z = 0 é o centro e y = 0 é o nível do piso.
 */
public interface Estrutura {

    /** Id usado no comando (minúsculo, sem espaço). */
    String getId();

    /** Nome curto mostrado no /estrutura lista. */
    String getNome();

    void gerar(Planta planta);

    /**
     * Comandos de console rodados depois que os blocos terminam de entrar, pra ligar a estrutura
     * aos outros plugins (local do boss, barracas...). {@code ox/oy/oz} é a origem da planta no
     * mundo. Comando de plugin que não está instalado é pulado, com aviso.
     */
    default List<String> getComandos(String mundo, int ox, int oy, int oz) {
        return List.of();
    }
}
