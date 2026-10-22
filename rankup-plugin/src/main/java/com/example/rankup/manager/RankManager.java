package com.example.rankup.manager;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Lista de ranks (do config.yml) e o rank de cada jogador (em jogadores.yml). Quem não está no
 * arquivo, ou está num rank que não existe mais, fica no primeiro rank.
 */
public class RankManager {

    private final RankupPlugin plugin;
    private final File arquivo;
    private final List<Rank> ranks = new ArrayList<>();
    /** Concurrent: o chat (assíncrono) lê o rank de quem fala. */
    private final Map<UUID, String> jogadores = new ConcurrentHashMap<>();

    public RankManager(RankupPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "jogadores.yml");
        carregarRanks();
        carregarJogadores();
    }

    public void carregarRanks() {
        ranks.clear();
        for (Map<?, ?> mapa : plugin.getConfig().getMapList("ranks")) {
            Object id = mapa.get("id");
            if (id == null || id.toString().isBlank()) {
                plugin.getLogger().warning("Rank sem id no config.yml, ignorado: " + mapa);
                continue;
            }
            String idTexto = id.toString().toLowerCase(Locale.ROOT);
            if (getRank(idTexto) != null) {
                plugin.getLogger().warning("Rank repetido no config.yml, ignorado: " + idTexto);
                continue;
            }
            Object nome = mapa.get("nome");
            Object cor = mapa.get("cor");
            ranks.add(new Rank(idTexto, nome != null ? nome.toString() : idTexto, Formato.cores(cor != null ? cor.toString() : "&7"),
                    Math.max(0, numero(mapa.get("preco"), 0)), Math.max(0, numero(mapa.get("multiplicador-venda"), 1.0))));
        }
        if (ranks.isEmpty()) {
            plugin.getLogger().warning("Nenhum rank no config.yml! Usando um rank único \"Camponês\".");
            ranks.add(new Rank("campones", "Camponês", "§7", 0, 1.0));
        }
    }

    private double numero(Object valor, double padrao) {
        if (valor instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue();
        return padrao;
    }

    private void carregarJogadores() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        var secao = yaml.getConfigurationSection("jogadores");
        if (secao == null) return;
        for (String chave : secao.getKeys(false)) {
            try {
                jogadores.put(UUID.fromString(chave), secao.getString(chave, ""));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em jogadores.yml: " + chave);
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        jogadores.forEach((uuid, rank) -> yaml.set("jogadores." + uuid, rank));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar jogadores.yml", e);
        }
    }

    public List<Rank> getRanks() {
        return Collections.unmodifiableList(ranks);
    }

    public Rank getRank(String id) {
        if (id == null) return null;
        for (Rank rank : ranks) {
            if (rank.id().equalsIgnoreCase(id)) return rank;
        }
        return null;
    }

    public Rank getPrimeiro() {
        return ranks.getFirst();
    }

    public Rank getRankDe(UUID jogador) {
        Rank rank = getRank(jogadores.get(jogador));
        return rank != null ? rank : getPrimeiro();
    }

    /** O rank seguinte, ou null se já é o último. */
    public Rank getProximo(Rank rank) {
        int i = indice(rank);
        return i >= 0 && i + 1 < ranks.size() ? ranks.get(i + 1) : null;
    }

    public int indice(Rank rank) {
        return ranks.indexOf(rank);
    }

    /** Se o jogador tem pelo menos esse rank. Rank desconhecido (mina com rank apagado do config) libera pra todos. */
    public boolean temAcesso(UUID jogador, String rankMinimo) {
        Rank minimo = getRank(rankMinimo);
        return minimo == null || indice(getRankDe(jogador)) >= indice(minimo);
    }

    public void setRank(UUID jogador, Rank rank) {
        jogadores.put(jogador, rank.id());
        salvar();
    }
}
