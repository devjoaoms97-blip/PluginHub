package com.example.boss.util;

import org.bukkit.NamespacedKey;

/**
 * Chave compartilhada entre plugins pra marcar uma entidade como "boss mundial".
 *
 * O SkillsPlugin (efeitos de Atordoamento do Marreteiro e Empurrão do Tridente) checa
 * essa MESMA chave (mesmo namespace/valor, "pluginhub:boss_mob") pra ignorar esses
 * efeitos de controle no boss — sem precisar de dependência direta entre os dois plugins,
 * já que PersistentDataContainer aceita ler uma chave criada por qualquer plugin, desde
 * que o namespace e a chave batam exatamente.
 */
public class BossTagUtil {

    public static final String NAMESPACE = "pluginhub";
    public static final String KEY = "boss_mob";

    public static NamespacedKey chave() {
        return new NamespacedKey(NAMESPACE, KEY);
    }
}
