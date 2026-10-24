package com.example.pix;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Chave Pix: um apelido único por jogador (/pix chave <apelido>) que vale no lugar do nome no
 * /pix pagar, receber e agendar. Não pode ser o nome de outro jogador que já entrou no servidor,
 * pra ninguém se passar por outro. Fica em chaves.yml (apelido em minúsculas → UUID).
 */
public class ChaveManager {

    private final PixPlugin plugin;
    private final File arquivo;
    private final Map<String, UUID> chaves = new HashMap<>();

    public ChaveManager(PixPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "chaves.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String chave : yaml.getKeys(false)) {
            try {
                chaves.put(chave, UUID.fromString(yaml.getString(chave)));
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("Chave Pix inválida ignorada em chaves.yml: " + chave);
            }
        }
    }

    /** O dono da chave, ou null. */
    public UUID dono(String apelido) {
        return chaves.get(apelido.toLowerCase(Locale.ROOT));
    }

    /** A chave do jogador, ou null. */
    public String chaveDe(UUID jogador) {
        for (Map.Entry<String, UUID> e : chaves.entrySet()) {
            if (e.getValue().equals(jogador)) return e.getKey();
        }
        return null;
    }

    /** Cadastra (ou troca) a chave do jogador. Devolve o erro, ou null se deu certo. */
    public String definir(UUID jogador, String apelido) {
        String chave = apelido.toLowerCase(Locale.ROOT);
        if (!chave.matches("[a-z0-9_.-]{3,20}")) return "A chave precisa ter de 3 a 20 letras, números, _ . ou -.";
        UUID atual = chaves.get(chave);
        if (atual != null && !atual.equals(jogador)) return "Essa chave já é de outro jogador.";
        OfflinePlayer comEsseNome = Bukkit.getOfflinePlayerIfCached(apelido);
        if (comEsseNome != null && !comEsseNome.getUniqueId().equals(jogador)) return "Essa chave é o nome de outro jogador.";
        chaves.values().removeIf(jogador::equals);
        chaves.put(chave, jogador);
        salvar();
        return null;
    }

    public boolean remover(UUID jogador) {
        boolean removeu = chaves.values().removeIf(jogador::equals);
        if (removeu) salvar();
        return removeu;
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        chaves.forEach((k, v) -> yaml.set(k, v.toString()));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar chaves.yml", e);
        }
    }
}
