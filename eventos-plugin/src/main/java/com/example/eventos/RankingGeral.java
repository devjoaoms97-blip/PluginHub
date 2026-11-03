package com.example.eventos;

import com.example.eventos.base.Evento;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * O ranking geral de vencedores de eventos: soma as vitórias de cada um em todos os eventos deste plugin
 * (os de arena e o Quiz) e no Gladiador individual (o por clã não conta), 1 por vitória, desde sempre.
 * Não guarda nada: lê o que cada evento já guarda (o GladiadorPlugin, por reflexão). Mostra no
 * {@code /eventos top} e num holograma ({@code ranking.holograma}), atualizado a cada minuto.
 */
public class RankingGeral {

    private static final String TAG = "eventos-ranking";

    /** Um evento que conta: o nome com cor, as vitórias de cada um e o nome guardado de quem venceu. */
    public record Fonte(String rotulo, Map<UUID, Integer> vitorias, Function<UUID, String> nome) {
    }

    private final EventosPlugin plugin;

    public RankingGeral(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Fonte> fontes() {
        List<Fonte> fontes = new ArrayList<>();
        for (Evento e : plugin.getEventos()) fontes.add(new Fonte(e.rotulo(), e.vitorias(), e::nomeDe));
        fontes.add(new Fonte("§e✎ Quiz", plugin.getQuiz().vitorias(), plugin.getQuiz()::nomeDe));
        Fonte glad = gladiador();
        if (glad != null) fontes.add(glad);
        return fontes;
    }

    /** O Gladiador individual, se o plugin estiver ligado e for de uma versão que separa as vitórias. */
    @SuppressWarnings("unchecked")
    private Fonte gladiador() {
        Plugin p = Bukkit.getPluginManager().getPlugin("GladiadorPlugin");
        if (p == null || !p.isEnabled()) return null;
        try {
            Map<UUID, Integer> vitorias = (Map<UUID, Integer>) p.getClass().getMethod("vitoriasIndividuais").invoke(p);
            var nome = p.getClass().getMethod("nomeDoVencedor", UUID.class);
            return new Fonte("§6⚔ Gladiador", vitorias, id -> {
                try {
                    return (String) nome.invoke(p, id);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            });
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    public Map<UUID, Integer> totais(List<Fonte> fontes) {
        Map<UUID, Integer> totais = new HashMap<>();
        for (Fonte f : fontes) f.vitorias().forEach((id, v) -> totais.merge(id, v, Integer::sum));
        return totais;
    }

    /** Todos, do que mais venceu pro que menos (no empate, pelo nome). */
    public List<Map.Entry<UUID, Integer>> ordem(List<Fonte> fontes) {
        List<Map.Entry<UUID, Integer>> lista = new ArrayList<>(totais(fontes).entrySet());
        lista.removeIf(e -> e.getValue() <= 0);
        lista.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(e -> String.valueOf(nome(fontes, e.getKey())), String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    /** O nome guardado por algum evento (quem venceu offline também aparece), ou o do servidor. */
    public String nome(List<Fonte> fontes, UUID id) {
        for (Fonte f : fontes) {
            if (!f.vitorias().containsKey(id)) continue;
            String n = f.nome().apply(id);
            if (n != null) return n;
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    /** Quem tem esse nome no ranking (sem ligar pra maiúscula), ou null. */
    public UUID porNome(List<Fonte> fontes, String nome) {
        for (UUID id : totais(fontes).keySet()) if (nome.equalsIgnoreCase(nome(fontes, id))) return id;
        return null;
    }

    // ---------------------------------------------------------------- holograma

    /** Cria (se o lugar estiver carregado) ou atualiza o holograma com o top 10. */
    public void atualizarHolograma() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("ranking.holograma");
        if (cfg == null || !cfg.getBoolean("ativado")) return;
        World w = Bukkit.getWorld(String.valueOf(cfg.getString("mundo")));
        List<Double> pos = cfg.getDoubleList("posicao");
        if (w == null || pos.size() < 3) return;
        Location l = new Location(w, pos.get(0), pos.get(1), pos.get(2));
        if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;

        List<Fonte> fontes = fontes();
        List<Map.Entry<UUID, Integer>> ordem = ordem(fontes);
        StringBuilder texto = new StringBuilder("§6§l✦ VENCEDORES DE EVENTOS ✦");
        int quantos = Math.max(1, cfg.getInt("quantos"));
        for (int i = 0; i < Math.min(quantos, ordem.size()); i++) {
            Map.Entry<UUID, Integer> e = ordem.get(i);
            texto.append("\n").append(i < 3 ? "§e" : "§7").append(i + 1).append("º §f").append(nome(fontes, e.getKey()))
                    .append(" §7- §e").append(e.getValue());
        }
        if (ordem.isEmpty()) texto.append("\n§7Ninguém venceu ainda.");
        texto.append("\n§8/eventos top");

        TextDisplay holo = w.getNearbyEntitiesByType(TextDisplay.class, l, 1.5).stream()
                .filter(d -> d.getScoreboardTags().contains(TAG)).findFirst().orElse(null);
        if (holo == null) {
            holo = w.spawn(l, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                // some no reinício e volta no próximo minuto: nunca fica um duplicado pra trás
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
            });
        }
        holo.text(LegacyComponentSerializer.legacySection().deserialize(texto.toString()));
    }

    public void removerHolograma() {
        for (World w : Bukkit.getWorlds()) {
            w.getEntitiesByClass(TextDisplay.class).stream().filter(d -> d.getScoreboardTags().contains(TAG)).forEach(TextDisplay::remove);
        }
    }
}
