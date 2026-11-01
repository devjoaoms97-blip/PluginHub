package com.example.placar;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * As tags no nome acima da cabeça. O Minecraft só decora esse nome pelo time do scoreboard (prefixo e
 * cor), e cada jogador vê o PRÓPRIO scoreboard (o do placar, ou o principal pra quem desligou): então
 * em cada scoreboard em uso fica um time por jogador online, com o mesmo prefixo da tab. O nome da tab
 * quem monta é o VipPlugin (rank, clã, VIP, apoiador e a cor do nome); aqui ele é só copiado: o que
 * vem antes do nome vira o prefixo, e a última cor antes do nome, a cor do nome.
 * <p>
 * Roda junto com a atualização do placar (a cada {@code intervalo-ticks}) e só mexe no time que mudou.
 */
class NomesNaCabeca {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final String PREFIXO_TIME = "nome_";

    private final PlacarPlugin plugin;
    /** Scoreboard → time → o que já foi aplicado nele (prefixo|cor|nome). Some junto com o scoreboard. */
    private final Map<Scoreboard, Map<String, String>> aplicados = new WeakHashMap<>();

    NomesNaCabeca(PlacarPlugin plugin) {
        this.plugin = plugin;
    }

    boolean ativado() {
        return plugin.getConfig().getBoolean("nome-na-cabeca.ativado", true);
    }

    /** Põe (ou atualiza) os times de todos os jogadores online em todos os scoreboards em uso. */
    void atualizar(Collection<Scoreboard> placares) {
        Set<Scoreboard> todos = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        todos.addAll(placares);
        todos.add(Bukkit.getScoreboardManager().getMainScoreboard());
        if (!ativado()) {
            todos.forEach(this::limpar);
            return;
        }
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        Set<String> times = new HashSet<>();
        for (Player p : online) {
            String nomeTime = PREFIXO_TIME + p.getUniqueId().toString().replace("-", "").substring(0, 16);
            times.add(nomeTime);
            String[] partes = partes(p);
            String chave = partes[0] + "|" + partes[1] + "|" + p.getName();
            for (Scoreboard s : todos) {
                Map<String, String> feitos = aplicados.computeIfAbsent(s, k -> new HashMap<>());
                Team time = s.getTeam(nomeTime);
                if (time != null && chave.equals(feitos.get(nomeTime))) continue; // nada mudou
                if (time == null) time = s.registerNewTeam(nomeTime);
                time.prefix(LEGADO.deserialize(partes[0]));
                ChatColor cor = ChatColor.getByChar(partes[1].isEmpty() ? 'f' : partes[1].charAt(0));
                time.setColor(cor != null && cor.isColor() ? cor : ChatColor.WHITE);
                if (!time.hasEntry(p.getName())) time.addEntry(p.getName());
                feitos.put(nomeTime, chave);
            }
        }
        // times de quem saiu
        for (Scoreboard s : todos) {
            for (Team t : Set.copyOf(s.getTeams())) {
                String nome = t.getName(); // depois do unregister, o time não responde mais nem o nome
                if (nome.startsWith(PREFIXO_TIME) && !times.contains(nome)) {
                    t.unregister();
                    aplicados.getOrDefault(s, new HashMap<>()).remove(nome);
                }
            }
        }
    }

    private void limpar(Scoreboard s) {
        for (Team t : Set.copyOf(s.getTeams())) {
            if (t.getName().startsWith(PREFIXO_TIME)) t.unregister();
        }
        aplicados.remove(s);
    }

    /**
     * O prefixo (o que vem antes do nome na tab) e o código da cor do nome ("b", ou "" sem cor).
     * Sem nome próprio na tab, sem prefixo.
     */
    private static String[] partes(Player p) {
        String tab = LEGADO.serialize(p.playerListName());
        int fim = tab.lastIndexOf(p.getName());
        if (fim <= 0) return new String[]{"", ""};
        String antes = tab.substring(0, fim);
        // a cor do nome: o último código de cor antes dele (os de formato, como §l, não contam)
        String cor = "";
        for (int i = antes.length() - 2; i >= 0; i--) {
            if (antes.charAt(i) == '§') {
                ChatColor c = ChatColor.getByChar(antes.charAt(i + 1));
                if (c != null && c.isColor()) {
                    cor = String.valueOf(antes.charAt(i + 1));
                    break;
                }
            }
        }
        return new String[]{antes, cor};
    }
}
