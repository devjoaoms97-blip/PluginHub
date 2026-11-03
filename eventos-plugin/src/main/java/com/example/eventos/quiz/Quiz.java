package com.example.eventos.quiz;

import com.example.eventos.EventosPlugin;
import com.example.eventos.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * O Quiz: uma rodada de perguntas no chat (sem arena e sem inscrição: responde quem estiver online, de
 * onde estiver, com {@code /quiz <resposta>}). As perguntas misturam quatro tipos, pelos pesos do config:
 * sobre o servidor e sobre o Minecraft (listas do config), contas (geradas na hora) e palavras
 * embaralhadas. Cada pergunta vale 1 ponto pra quem acertar primeiro; quem fizer mais pontos leva o
 * prêmio (dividido no empate). A resposta não liga pra acento, maiúscula nem "o"/"a" na frente.
 */
public class Quiz {

    private record Pergunta(String texto, String exibir, Set<String> aceitas) {
    }

    private final EventosPlugin plugin;
    private final File arquivoVitorias;
    private boolean ativo;
    private int numero;
    private Pergunta atual;
    private long prazo, proximaEm;
    private final Map<UUID, Integer> pontos = new LinkedHashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();
    private final Map<UUID, Long> ultimaTentativa = new HashMap<>();
    private final Set<String> usadas = new HashSet<>();
    private final Map<UUID, Integer> vitorias = new HashMap<>();

    public Quiz(EventosPlugin plugin) {
        this.plugin = plugin;
        this.arquivoVitorias = new File(plugin.getDataFolder(), "quiz-vitorias.yml");
        carregarVitorias();
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("quiz");
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getNumero() {
        return numero;
    }

    public int total() {
        return Math.max(1, cfg().getInt("perguntas-por-rodada"));
    }

    public boolean perguntaAberta() {
        return ativo && atual != null;
    }

    public double premio() {
        return Math.max(0, cfg().getDouble("premio"));
    }

    /** A resposta da pergunta aberta (pra staff), ou null. */
    public String respostaAtual() {
        return atual == null ? null : atual.exibir();
    }

    // ---------------------------------------------------------------- rodada

    public void abrir() {
        ativo = true;
        numero = 0;
        atual = null;
        pontos.clear();
        nomes.clear();
        usadas.clear();
        ultimaTentativa.clear();
        proximaEm = System.currentTimeMillis() + 5000;
        String abertura = "§e§l✎ QUIZ ✎ §ecomeça em 5 s! §f" + total() + " §eperguntas, responda com §f/quiz <resposta>§e. "
                + "Quem fizer mais pontos leva §f" + Formato.valor(premio()) + "§e.";
        anunciar(abertura);
        plugin.getDiscord().enviar("eventos", abertura);
    }

    public void cancelar(String motivo) {
        if (!ativo) return;
        ativo = false;
        atual = null;
        anunciar("§e§l✎ QUIZ ✎ §cCancelado: " + motivo + ".");
    }

    /** A cada segundo: a próxima pergunta, ou o fim do tempo da atual. */
    public void tick() {
        if (!ativo) return;
        long agora = System.currentTimeMillis();
        if (atual != null && agora >= prazo) {
            anunciar("§e[Quiz] §7Ninguém acertou. A resposta era §f" + atual.exibir() + "§7.");
            depoisDaPergunta(agora);
        } else if (atual == null && agora >= proximaEm) {
            if (numero >= total()) {
                finalizar();
                return;
            }
            numero++;
            atual = sortear();
            prazo = agora + Math.max(5, cfg().getInt("segundos-por-pergunta")) * 1000L;
            anunciar("§e§l✎ Pergunta " + numero + "/" + total() + ": §f" + atual.texto()
                    + " §7(/quiz <resposta>, " + Math.max(5, cfg().getInt("segundos-por-pergunta")) + " s)");
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
        }
    }

    private void depoisDaPergunta(long agora) {
        atual = null;
        proximaEm = agora + Math.max(1, cfg().getInt("pausa-segundos")) * 1000L;
    }

    /** null se acertou; senão, o que dizer pro jogador. */
    public String responder(Player p, String texto) {
        if (!perguntaAberta()) return "§7Não tem pergunta aberta agora. Veja quando é o próximo Quiz no §f/eventos§7.";
        long agora = System.currentTimeMillis();
        long espera = Math.max(0, cfg().getInt("espera-entre-tentativas-segundos")) * 1000L;
        Long ultima = ultimaTentativa.get(p.getUniqueId());
        if (ultima != null && agora - ultima < espera) return "§cCalma! Espere um pouco antes de tentar de novo.";
        ultimaTentativa.put(p.getUniqueId(), agora);
        if (!atual.aceitas().contains(normalizar(texto))) return "§cNão é isso.";

        int total = pontos.merge(p.getUniqueId(), 1, Integer::sum);
        nomes.put(p.getUniqueId(), p.getName());
        anunciar("§e[Quiz] §f" + p.getName() + " §aacertou! §7A resposta era §f" + atual.exibir() + "§7. (" + total + " ponto(s))");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        depoisDaPergunta(agora);
        return null;
    }

    private void finalizar() {
        ativo = false;
        atual = null;
        int maior = pontos.values().stream().max(Integer::compare).orElse(0);
        if (maior <= 0) {
            anunciar("§e§l✎ QUIZ ✎ §7Acabou, e ninguém acertou nenhuma.");
            return;
        }
        List<UUID> ganhadores = pontos.entrySet().stream().filter(e -> e.getValue() == maior).map(Map.Entry::getKey).toList();
        double parte = premio() / ganhadores.size();
        List<String> nomesGanhadores = new ArrayList<>();
        for (UUID id : ganhadores) {
            nomesGanhadores.add(nomes.get(id));
            vitorias.merge(id, 1, Integer::sum);
            pagar(id, parte);
        }
        salvarVitorias();
        // o placar: os 3 primeiros
        List<Map.Entry<UUID, Integer>> ordem = new ArrayList<>(pontos.entrySet());
        ordem.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder placar = new StringBuilder();
        for (int i = 0; i < Math.min(3, ordem.size()); i++) {
            placar.append(i == 0 ? "" : "§7, ").append("§f").append(nomes.get(ordem.get(i).getKey())).append(" §7")
                    .append(ordem.get(i).getValue());
        }
        String resultado = "§e§l✎ QUIZ ✎ §f" + String.join(", ", nomesGanhadores) + " §e§l" + (ganhadores.size() > 1 ? "empataram" : "venceu")
                + " §ecom §f" + maior + " §eponto(s)! Prêmio: cheque de §f" + Formato.valor(parte)
                + (ganhadores.size() > 1 ? " §epra cada um" : "") + "§e. §7Placar: " + placar;
        anunciar(resultado);
        plugin.getDiscord().enviar("eventos", resultado);
    }

    private void pagar(UUID id, double valor) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            plugin.getPremio().pagar(p, valor, "Quiz");
            return;
        }
        // saiu antes do fim: cai na conta
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) rsp.getProvider().depositPlayer(Bukkit.getOfflinePlayer(id), valor);
    }

    // ---------------------------------------------------------------- perguntas

    /** Sorteia o tipo pelos pesos e uma pergunta dele que ainda não saiu nesta rodada. */
    private Pergunta sortear() {
        ConfigurationSection pesos = cfg().getConfigurationSection("pesos");
        List<String> tipos = new ArrayList<>();
        for (String tipo : List.of("servidor", "minecraft", "conta", "palavra")) {
            int peso = pesos == null ? 1 : pesos.getInt(tipo);
            for (int i = 0; i < peso; i++) tipos.add(tipo);
        }
        Collections.shuffle(tipos);
        for (String tipo : tipos) {
            Pergunta p = switch (tipo) {
                case "conta" -> conta();
                case "palavra" -> palavra();
                default -> daLista(tipo);
            };
            if (p != null && usadas.add(p.texto())) return p;
        }
        return conta(); // sempre tem
    }

    private Pergunta daLista(String tipo) {
        List<Map<?, ?>> lista = new ArrayList<>(cfg().getMapList(tipo));
        Collections.shuffle(lista);
        for (Map<?, ?> m : lista) {
            String texto = String.valueOf(m.get("pergunta"));
            if (usadas.contains(texto) || !(m.get("respostas") instanceof List<?> respostas) || respostas.isEmpty()) continue;
            Set<String> aceitas = new HashSet<>();
            for (Object r : respostas) aceitas.add(normalizar(String.valueOf(r)));
            return new Pergunta(texto, String.valueOf(respostas.get(0)), aceitas);
        }
        return null;
    }

    /** "17 x 6 = ?", "83 + 45 = ?", "91 - 37 = ?": gerada na hora. */
    private static Pergunta conta() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int a, b, resultado;
        String op;
        switch (r.nextInt(3)) {
            case 0 -> {
                a = r.nextInt(12, 100);
                b = r.nextInt(12, 100);
                op = "+";
                resultado = a + b;
            }
            case 1 -> {
                a = r.nextInt(30, 100);
                b = r.nextInt(5, a);
                op = "-";
                resultado = a - b;
            }
            default -> {
                a = r.nextInt(3, 16);
                b = r.nextInt(3, 16);
                op = "x";
                resultado = a * b;
            }
        }
        String res = String.valueOf(resultado);
        return new Pergunta("Quanto é " + a + " " + op + " " + b + "?", res, Set.of(res));
    }

    private Pergunta palavra() {
        List<String> palavras = new ArrayList<>(cfg().getStringList("palavras"));
        Collections.shuffle(palavras);
        for (String palavra : palavras) {
            String limpa = normalizar(palavra);
            if (limpa.length() < 4) continue;
            List<Character> letras = new ArrayList<>();
            for (char c : limpa.toCharArray()) letras.add(c);
            String embaralhada = limpa;
            for (int i = 0; i < 10 && embaralhada.equals(limpa); i++) {
                Collections.shuffle(letras);
                StringBuilder sb = new StringBuilder();
                letras.forEach(sb::append);
                embaralhada = sb.toString();
            }
            String texto = "Desembaralhe: " + embaralhada.toUpperCase(Locale.ROOT);
            if (usadas.contains(texto)) continue;
            return new Pergunta(texto, palavra, Set.of(limpa));
        }
        return null;
    }

    /** Sem acento, minúscula, sem pontuação de número ("10.000" = "10000") e sem "o"/"a"/"um" na frente. */
    static String normalizar(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        t = t.replaceAll("(?<=\\d)[.,](?=\\d{3}\\b)", "").replaceAll("[!?\"'´`]", "").replaceAll("\\s+", " ");
        t = t.replaceFirst("^(o|a|os|as|um|uma) ", "");
        return t.trim();
    }

    // ---------------------------------------------------------------- vitórias

    public List<Map.Entry<UUID, Integer>> ranking(int quantos) {
        List<Map.Entry<UUID, Integer>> lista = new ArrayList<>(vitorias.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    /** As vitórias de cada um (pro ranking geral). */
    public Map<UUID, Integer> vitorias() {
        return Map.copyOf(vitorias);
    }

    public String nomeDe(UUID id) {
        String n = nomes.get(id);
        return n != null ? n : Bukkit.getOfflinePlayer(id).getName();
    }

    private void carregarVitorias() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoVitorias);
        for (String chave : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(chave);
                vitorias.put(id, yaml.getInt(chave + ".vitorias"));
                nomes.put(id, yaml.getString(chave + ".nome"));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido ignorado em quiz-vitorias.yml: " + chave);
            }
        }
    }

    private void salvarVitorias() {
        YamlConfiguration yaml = new YamlConfiguration();
        vitorias.forEach((id, v) -> {
            yaml.set(id + ".nome", nomeDe(id));
            yaml.set(id + ".vitorias", v);
        });
        try {
            yaml.save(arquivoVitorias);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar quiz-vitorias.yml", e);
        }
        plugin.getRanking().atualizarHolograma();
    }

    private static void anunciar(String msg) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(msg));
    }
}
