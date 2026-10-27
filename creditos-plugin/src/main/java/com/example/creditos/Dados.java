package com.example.creditos;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Os dados de cada jogador (dados.yml): saldo de créditos, cosméticos comprados, o que está em uso
 * em cada categoria e até quando é apoiador. Toda entrada e saída de créditos vira uma linha no
 * creditos.log (data, jogador, quanto, saldo e o motivo), pra conferir com os pagamentos do Discord.
 */
public class Dados {

    /** As categorias de cosmético: em cada uma o jogador usa no máximo um. */
    public enum Categoria { RASTRO, COR, ABATE }

    static class Jogador {
        long creditos;
        final Set<String> cosmeticos = new HashSet<>();
        final Map<Categoria, String> emUso = new EnumMap<>(Categoria.class);
        long apoiadorAte;
    }

    private final JavaPlugin plugin;
    private final File arquivo;
    private final File log;
    private final Map<UUID, Jogador> jogadores = new HashMap<>();

    public Dados(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.arquivo = new File(plugin.getDataFolder(), "dados.yml");
        this.log = new File(plugin.getDataFolder(), "creditos.log");
        carregar();
    }

    Jogador de(UUID id) {
        return jogadores.computeIfAbsent(id, k -> new Jogador());
    }

    public long creditos(UUID id) {
        return de(id).creditos;
    }

    /** Soma (ou tira, com valor negativo) créditos e registra no log. Retorna o saldo novo. */
    public long mudar(UUID id, String nome, long valor, String motivo) {
        Jogador j = de(id);
        j.creditos = Math.max(0, j.creditos + valor);
        salvar();
        registrar((valor >= 0 ? "+" : "") + valor + " " + nome + " (" + id + ") saldo=" + j.creditos + " motivo=" + motivo);
        return j.creditos;
    }

    public boolean tem(UUID id, String cosmetico) {
        return de(id).cosmeticos.contains(cosmetico);
    }

    public void darCosmetico(UUID id, String cosmetico) {
        de(id).cosmeticos.add(cosmetico);
        salvar();
    }

    public String emUso(UUID id, Categoria c) {
        Jogador j = jogadores.get(id);
        return j == null ? null : j.emUso.get(c);
    }

    public void usar(UUID id, Categoria c, String cosmetico) {
        if (cosmetico == null) de(id).emUso.remove(c);
        else de(id).emUso.put(c, cosmetico);
        salvar();
    }

    public boolean apoiador(UUID id) {
        Jogador j = jogadores.get(id);
        return j != null && j.apoiadorAte > System.currentTimeMillis();
    }

    public long apoiadorAte(UUID id) {
        Jogador j = jogadores.get(id);
        return j == null ? 0 : j.apoiadorAte;
    }

    /** Mais {@code dias} de apoiador, somados ao que ainda faltava. Retorna até quando. */
    public long renovarApoiador(UUID id, String nome, int dias) {
        Jogador j = de(id);
        j.apoiadorAte = Math.max(System.currentTimeMillis(), j.apoiadorAte) + dias * 24L * 60 * 60 * 1000;
        salvar();
        registrar("APOIADOR " + nome + " (" + id + ") +" + dias + " dias, até " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(j.apoiadorAte)));
        return j.apoiadorAte;
    }

    public void registrar(String linha) {
        String data = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PrintWriter out = new PrintWriter(new FileWriter(log, StandardCharsets.UTF_8, true))) {
            out.println("[" + data + "] " + linha);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível escrever no creditos.log", e);
        }
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("jogadores");
        if (s == null) return;
        for (String chave : s.getKeys(false)) {
            ConfigurationSection js = s.getConfigurationSection(chave);
            if (js == null) continue;
            Jogador j = new Jogador();
            j.creditos = js.getLong("creditos");
            j.cosmeticos.addAll(js.getStringList("cosmeticos"));
            for (Categoria c : Categoria.values()) {
                String usando = js.getString("em-uso." + c.name().toLowerCase());
                if (usando != null) j.emUso.put(c, usando);
            }
            j.apoiadorAte = js.getLong("apoiador-ate");
            try {
                jogadores.put(UUID.fromString(chave), j);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Jogador inválido ignorado em dados.yml: " + chave);
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Jogador> e : jogadores.entrySet()) {
            Jogador j = e.getValue();
            String base = "jogadores." + e.getKey() + ".";
            yaml.set(base + "creditos", j.creditos);
            if (!j.cosmeticos.isEmpty()) yaml.set(base + "cosmeticos", j.cosmeticos.stream().sorted().toList());
            j.emUso.forEach((c, v) -> yaml.set(base + "em-uso." + c.name().toLowerCase(), v));
            if (j.apoiadorAte > 0) yaml.set(base + "apoiador-ate", j.apoiadorAte);
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar dados.yml", e);
        }
    }
}
