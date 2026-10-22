package com.example.clas.manager;

import com.example.clas.ClasPlugin;
import com.example.clas.model.Cargo;
import com.example.clas.model.Cla;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Todos os clãs, o índice jogador -> clã e os convites pendentes.
 *
 * O índice é um ConcurrentHashMap porque o chat (que mostra a tag) roda fora da thread
 * principal. Os clãs são salvos em {@code clas.yml} a cada mudança — o banco é dinheiro de
 * jogador. Convites ficam só em memória e expiram sozinhos.
 */
public class ClaManager {

    private final ClasPlugin plugin;
    private final File arquivo;
    private final Map<String, Cla> clas = new ConcurrentHashMap<>();       // tag minúscula -> clã
    private final Map<UUID, String> claDoJogador = new ConcurrentHashMap<>(); // jogador -> tag minúscula
    private final Map<UUID, Map<String, Long>> convites = new HashMap<>();    // convidado -> (tag -> expira em)

    public ClaManager(ClasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "clas.yml");
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------
    public Cla get(String tag) {
        return tag == null ? null : clas.get(tag.toLowerCase(Locale.ROOT));
    }

    public Cla getClaDe(UUID jogador) {
        String tag = claDoJogador.get(jogador);
        return tag == null ? null : clas.get(tag);
    }

    public boolean mesmoCla(UUID a, UUID b) {
        String claA = claDoJogador.get(a);
        return claA != null && claA.equals(claDoJogador.get(b));
    }

    public List<Cla> ranking() {
        List<Cla> lista = new ArrayList<>(clas.values());
        lista.sort(Comparator.comparingInt(Cla::getAbates).reversed()
                .thenComparing(Comparator.comparingDouble(Cla::getBanco).reversed()));
        return lista;
    }

    public int totalClas() {
        return clas.size();
    }

    // ---------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------
    public Cla criar(String tag, String nome, UUID lider, String nomeLider) {
        Cla cla = new Cla(tag, nome, System.currentTimeMillis());
        cla.getMembros().put(lider, Cargo.LIDER);
        cla.getNomes().put(lider, nomeLider);
        clas.put(cla.chave(), cla);
        claDoJogador.put(lider, cla.chave());
        convites.remove(lider);
        salvar();
        return cla;
    }

    public void adicionarMembro(Cla cla, UUID jogador, String nome) {
        cla.getMembros().put(jogador, Cargo.MEMBRO);
        cla.getNomes().put(jogador, nome);
        claDoJogador.put(jogador, cla.chave());
        convites.remove(jogador);
        salvar();
    }

    public void removerMembro(Cla cla, UUID jogador) {
        cla.getMembros().remove(jogador);
        cla.getNomes().remove(jogador);
        claDoJogador.remove(jogador);
        salvar();
    }

    public void definirCargo(Cla cla, UUID jogador, Cargo cargo) {
        cla.getMembros().put(jogador, cargo);
        salvar();
    }

    /** Apaga o clã. O banco NÃO é devolvido aqui — quem chama decide pra quem vai. */
    public void desfazer(Cla cla) {
        for (UUID membro : cla.getMembros().keySet()) {
            claDoJogador.remove(membro);
        }
        clas.remove(cla.chave());
        convites.values().forEach(mapa -> mapa.remove(cla.chave()));
        salvar();
    }

    // ---------------------------------------------------------------------
    // Convites
    // ---------------------------------------------------------------------
    public void convidar(Cla cla, UUID convidado) {
        long expiraMs = Math.max(1, plugin.getConfig().getInt("convite-expira-minutos", 5)) * 60_000L;
        convites.computeIfAbsent(convidado, k -> new HashMap<>()).put(cla.chave(), System.currentTimeMillis() + expiraMs);
    }

    /** true se havia um convite válido desse clã (e consome o convite). */
    public boolean consumirConvite(UUID convidado, Cla cla) {
        Map<String, Long> doJogador = convites.get(convidado);
        if (doJogador == null) return false;
        Long expiraEm = doJogador.remove(cla.chave());
        return expiraEm != null && System.currentTimeMillis() < expiraEm;
    }

    public List<String> convitesValidos(UUID convidado) {
        Map<String, Long> doJogador = convites.get(convidado);
        List<String> tags = new ArrayList<>();
        if (doJogador == null) return tags;
        long agora = System.currentTimeMillis();
        doJogador.values().removeIf(expira -> agora >= expira);
        for (String chave : doJogador.keySet()) {
            Cla cla = clas.get(chave);
            if (cla != null) tags.add(cla.getTag());
        }
        return tags;
    }

    // ---------------------------------------------------------------------
    // Persistência (clas.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("clas");
        if (secao == null) return;

        for (String chave : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(chave);
            if (s == null) continue;
            try {
                Cla cla = new Cla(s.getString("tag", chave), s.getString("nome", chave), s.getLong("criado-em"));
                cla.setBanco(s.getDouble("banco"));
                cla.setFogoAmigo(s.getBoolean("fogo-amigo"));
                cla.setAbates(s.getInt("abates"));
                cla.setMortes(s.getInt("mortes"));

                ConfigurationSection membros = s.getConfigurationSection("membros");
                if (membros != null) {
                    for (String id : membros.getKeys(false)) {
                        UUID uuid = UUID.fromString(id);
                        cla.getMembros().put(uuid, Cargo.valueOf(membros.getString(id + ".cargo", "MEMBRO")));
                        cla.getNomes().put(uuid, membros.getString(id + ".nome", "?"));
                    }
                }
                for (Map<?, ?> l : s.getMapList("extrato")) {
                    cla.getExtrato().add(new Cla.Lancamento(((Number) l.get("data")).longValue(), String.valueOf(l.get("jogador")),
                            String.valueOf(l.get("tipo")), ((Number) l.get("valor")).doubleValue()));
                }
                if (cla.getLider() == null) {
                    plugin.getLogger().warning("Clã " + cla.getTag() + " sem líder em clas.yml — ignorado.");
                    continue;
                }

                clas.put(cla.chave(), cla);
                for (UUID membro : cla.getMembros().keySet()) {
                    claDoJogador.put(membro, cla.chave());
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Clã inválido ignorado em clas.yml: " + chave + " (" + e.getMessage() + ")");
            }
        }
    }

    public synchronized void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Cla cla : clas.values()) {
            String base = "clas." + cla.chave();
            yaml.set(base + ".tag", cla.getTag());
            yaml.set(base + ".nome", cla.getNome());
            yaml.set(base + ".criado-em", cla.getCriadoEm());
            yaml.set(base + ".banco", cla.getBanco());
            yaml.set(base + ".fogo-amigo", cla.isFogoAmigo());
            yaml.set(base + ".abates", cla.getAbates());
            yaml.set(base + ".mortes", cla.getMortes());
            for (Map.Entry<UUID, Cargo> m : cla.getMembros().entrySet()) {
                yaml.set(base + ".membros." + m.getKey() + ".cargo", m.getValue().name());
                yaml.set(base + ".membros." + m.getKey() + ".nome", cla.getNomes().get(m.getKey()));
            }
            List<Map<String, Object>> extrato = new ArrayList<>();
            for (Cla.Lancamento l : cla.getExtrato()) {
                Map<String, Object> mapa = new LinkedHashMap<>();
                mapa.put("data", l.data());
                mapa.put("jogador", l.jogador());
                mapa.put("tipo", l.tipo());
                mapa.put("valor", l.valor());
                extrato.add(mapa);
            }
            yaml.set(base + ".extrato", extrato);
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar clas.yml", e);
        }
    }
}
