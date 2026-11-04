package com.example.eventos;

import com.example.eventos.base.Evento;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * /eventos: o que está acontecendo agora, o próximo evento e a agenda de todos. Os horários vêm do config
 * de cada plugin (o deles manda; sem o plugin, a linha some): os eventos deste plugin, Gladiador,
 * Boss, Dragão do End (RecursosPlugin) e Mine-sena (ChequePlugin).
 */
public class AgendaCommand implements CommandExecutor {

    /** Um horário: os dias (todos = todo dia), a hora e um detalhe ("clã", "Wither"). */
    private record Horario(Set<DayOfWeek> dias, LocalTime hora, String detalhe) {
    }

    /** Uma linha da agenda: nome com cor, como participar e os horários. */
    private record Linha(String nome, String como, List<Horario> horarios) {
    }

    private static final Map<String, DayOfWeek> DIAS = Map.ofEntries(
            Map.entry("SEGUNDA", DayOfWeek.MONDAY), Map.entry("TERCA", DayOfWeek.TUESDAY), Map.entry("TERÇA", DayOfWeek.TUESDAY),
            Map.entry("QUARTA", DayOfWeek.WEDNESDAY), Map.entry("QUINTA", DayOfWeek.THURSDAY), Map.entry("SEXTA", DayOfWeek.FRIDAY),
            Map.entry("SABADO", DayOfWeek.SATURDAY), Map.entry("SÁBADO", DayOfWeek.SATURDAY), Map.entry("DOMINGO", DayOfWeek.SUNDAY));
    private static final String[] ABREV = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    private final EventosPlugin plugin;

    public AgendaCommand(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            if (args.length > 1) jogador(sender, args[1]);
            else top(sender);
            return true;
        }
        List<Linha> linhas = linhas();
        sender.sendMessage("§6§l✦ Eventos ✦");

        Evento aberto = plugin.eventoAberto();
        if (aberto != null) {
            String fase = switch (aberto.getFase()) {
                case INSCRICAO -> "inscrições abertas: §f/" + aberto.id() + " entrar";
                case PREPARACAO, LUTA -> "rolando agora: §f/" + aberto.id() + " assistir";
                case FIM -> "acabou de terminar";
            };
            sender.sendMessage("§aAgora: §f" + aberto.nome() + " §7(" + fase + "§7)");
        }
        if (plugin.getQuiz().isAtivo()) {
            sender.sendMessage("§aAgora: §fQuiz §7(pergunta " + plugin.getQuiz().getNumero() + "/" + plugin.getQuiz().total()
                    + ": §f/quiz <resposta>§7)");
        }

        // o próximo de todos (o mais cedo a partir de agora)
        LocalDateTime agora = LocalDateTime.now();
        Linha proxima = null;
        LocalDateTime quando = null;
        for (Linha l : linhas) {
            for (Horario h : l.horarios()) {
                LocalDateTime p = proximo(h, agora);
                if (quando == null || p.isBefore(quando)) {
                    quando = p;
                    proxima = l;
                }
            }
        }
        if (proxima != null) {
            long min = java.time.Duration.between(agora, quando).toMinutes() + 1;
            String dia = quando.toLocalDate().equals(agora.toLocalDate()) ? "hoje" : quando.toLocalDate().equals(agora.toLocalDate().plusDays(1))
                    ? "amanhã" : ABREV[quando.getDayOfWeek().getValue() - 1];
            sender.sendMessage("§ePróximo: " + proxima.nome() + " §e" + dia + " às §f" + quando.toLocalTime()
                    + " §7(em " + (min >= 60 ? min / 60 + "h " + min % 60 + "min" : min + " min") + ")");
        }

        sender.sendMessage("§7Agenda:");
        for (String linha : agenda(linhas)) sender.sendMessage(linha);
        return true;
    }

    /**
     * Um evento por linha, na ordem do horário ("☽ Labirinto do Fauno - 08:00", embaixo "⚑ Corrida Maluca -
     * 09:00"...): quem tem dois horários aparece duas vezes. Os de certos dias dizem quais ("sáb 19:00 (clã)").
     */
    private static List<String> agenda(List<Linha> linhas) {
        record Item(LocalTime hora, String texto) {
        }
        List<Item> itens = new ArrayList<>();
        for (Linha l : linhas) {
            Map<LocalTime, List<Horario>> porHora = new TreeMap<>();
            for (Horario h : l.horarios()) porHora.computeIfAbsent(h.hora(), k -> new ArrayList<>()).add(h);
            porHora.forEach((hora, hs) -> itens.add(new Item(hora,
                    " " + l.nome() + " §7- §f" + texto(hs).replaceFirst("^todo dia ", "") + " §8· §7" + l.como())));
        }
        itens.sort(Comparator.comparing(Item::hora));
        return itens.stream().map(Item::texto).toList();
    }

    /** O top 10 geral, a posição de quem pediu e quem mais venceu cada evento. */
    private void top(CommandSender sender) {
        RankingGeral r = plugin.getRanking();
        List<RankingGeral.Fonte> fontes = r.fontes();
        List<Map.Entry<java.util.UUID, Integer>> ordem = r.ordem(fontes);
        sender.sendMessage("§6§l✦ Vencedores de eventos ✦");
        for (int i = 0; i < Math.min(10, ordem.size()); i++) {
            Map.Entry<java.util.UUID, Integer> e = ordem.get(i);
            sender.sendMessage((i < 3 ? "§e" : "§7") + (i + 1) + "º §f" + r.nome(fontes, e.getKey()) + " §7- §f" + vitorias(e.getValue()));
        }
        if (ordem.isEmpty()) sender.sendMessage("§7Ninguém venceu ainda.");
        if (sender instanceof org.bukkit.entity.Player p) {
            int pos = 0;
            for (int i = 0; i < ordem.size(); i++) if (ordem.get(i).getKey().equals(p.getUniqueId())) pos = i + 1;
            sender.sendMessage(pos == 0 ? "§7Você ainda não venceu nenhum evento."
                    : "§aVocê: §f" + pos + "º§a, com §f" + vitorias(ordem.get(pos - 1).getValue()) + "§a.");
        }
        sender.sendMessage("§7Recordes:");
        for (RankingGeral.Fonte f : fontes) {
            Map.Entry<java.util.UUID, Integer> melhor = f.vitorias().entrySet().stream().filter(e -> e.getValue() > 0)
                    .max(Map.Entry.comparingByValue()).orElse(null);
            sender.sendMessage(" " + f.rotulo() + " §7- " + (melhor == null ? "ninguém ainda"
                    : "§f" + r.nome(fontes, melhor.getKey()) + " §7(" + vitorias(melhor.getValue()) + ")"));
        }
        sender.sendMessage("§7De um jogador: §f/eventos top <jogador>");
    }

    /** As vitórias de um jogador em cada evento. */
    private void jogador(CommandSender sender, String nome) {
        RankingGeral r = plugin.getRanking();
        List<RankingGeral.Fonte> fontes = r.fontes();
        java.util.UUID id = r.porNome(fontes, nome);
        if (id == null) {
            sender.sendMessage("§7" + nome + " ainda não venceu nenhum evento.");
            return;
        }
        List<Map.Entry<java.util.UUID, Integer>> ordem = r.ordem(fontes);
        int pos = 0;
        for (int i = 0; i < ordem.size(); i++) if (ordem.get(i).getKey().equals(id)) pos = i + 1;
        sender.sendMessage("§6§l✦ " + r.nome(fontes, id) + " ✦ §f" + vitorias(r.totais(fontes).getOrDefault(id, 0)) + " §7(" + pos + "º no geral)");
        for (RankingGeral.Fonte f : fontes) {
            int v = f.vitorias().getOrDefault(id, 0);
            if (v > 0) sender.sendMessage(" " + f.rotulo() + " §7- §f" + vitorias(v));
        }
    }

    private static String vitorias(int n) {
        return n + (n == 1 ? " vitória" : " vitórias");
    }

    private List<Linha> linhas() {
        List<Linha> linhas = new ArrayList<>();
        for (Evento e : plugin.getEventos()) {
            List<Horario> hs = new ArrayList<>();
            for (String s : e.cfg().getStringList("agenda")) adicionar(hs, null, s, null);
            if (!hs.isEmpty()) linhas.add(new Linha(e.rotulo(), "/" + e.id(), hs));
        }
        List<Horario> quiz = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("quiz.agenda")) adicionar(quiz, null, s, null);
        if (!quiz.isEmpty()) linhas.add(new Linha("§e✎ Quiz", "/quiz", quiz));
        Plugin glad = ativo("GladiadorPlugin");
        if (glad != null) {
            List<Horario> hs = new ArrayList<>();
            for (String s : glad.getConfig().getStringList("agenda")) {
                String[] p = s.trim().split("\\s+");
                if (p.length == 3) adicionar(hs, DIAS.get(p[0].toUpperCase(Locale.ROOT)), p[1], p[2].toLowerCase(Locale.ROOT).startsWith("cl") ? "clã" : "individual");
            }
            if (!hs.isEmpty()) linhas.add(new Linha("§6⚔ Gladiador", "/gladiador", hs));
        }
        Plugin boss = ativo("BossPlugin");
        if (boss != null) {
            List<Horario> hs = new ArrayList<>();
            for (String s : boss.getConfig().getStringList("agenda")) {
                String[] p = s.trim().split("\\s+");
                if (p.length >= 2) adicionar(hs, DIAS.get(p[0].toUpperCase(Locale.ROOT)), p[1], p.length > 2 ? capitalizar(p[2]) : null);
            }
            if (!hs.isEmpty()) linhas.add(new Linha("§c☠ Boss", "/warp boss", hs));
        }
        Plugin recursos = ativo("RecursosPlugin");
        if (recursos != null && recursos.getConfig().getBoolean("end.dragao.renascer", true)) {
            List<Horario> hs = new ArrayList<>();
            adicionar(hs, null, recursos.getConfig().getString("end.dragao.hora", ""), null);
            if (!hs.isEmpty()) linhas.add(new Linha("§5✸ Dragão do End", "/warp end", hs));
        }
        Plugin cheque = ativo("ChequePlugin");
        if (cheque != null && cheque.getConfig().getBoolean("minesena.ativado", true)) {
            List<Horario> hs = new ArrayList<>();
            adicionar(hs, null, cheque.getConfig().getString("minesena.horario", ""), null);
            if (!hs.isEmpty()) linhas.add(new Linha("§e✪ Mine-sena", "sorteio, no Banco", hs));
        }
        return linhas;
    }

    private static void adicionar(List<Horario> hs, DayOfWeek dia, String hora, String detalhe) {
        try {
            hs.add(new Horario(dia == null ? EnumSet.allOf(DayOfWeek.class) : EnumSet.of(dia), LocalTime.parse(hora.trim()), detalhe));
        } catch (DateTimeParseException e) {
            // horário inválido no config do outro plugin: a linha só não mostra esse
        }
    }

    private static Plugin ativo(String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nome);
        return p != null && p.isEnabled() ? p : null;
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /** A próxima vez desse horário a partir de agora. */
    private static LocalDateTime proximo(Horario h, LocalDateTime agora) {
        for (int d = 0; d <= 7; d++) {
            LocalDateTime t = agora.toLocalDate().plusDays(d).atTime(h.hora());
            if (h.dias().contains(t.getDayOfWeek()) && t.isAfter(agora)) return t;
        }
        return agora.plusYears(1);
    }

    /**
     * "todo dia 10:00 e 16:00", "sáb 19:00 (clã), dom 19:00 (individual)", "todo dia 20:00 (dom: Wither)":
     * junta os horários iguais e diz os dias.
     */
    private static String texto(List<Horario> hs) {
        // hora -> dias e detalhes por dia
        Map<LocalTime, Set<DayOfWeek>> diasPorHora = new TreeMap<>();
        Map<LocalTime, Map<DayOfWeek, String>> detalhes = new TreeMap<>();
        for (Horario h : hs) {
            diasPorHora.computeIfAbsent(h.hora(), k -> EnumSet.noneOf(DayOfWeek.class)).addAll(h.dias());
            if (h.detalhe() != null) for (DayOfWeek d : h.dias()) detalhes.computeIfAbsent(h.hora(), k -> new LinkedHashMap<>()).put(d, h.detalhe());
        }
        // agrupa as horas que valem todo dia (sem detalhe diferente)
        List<String> todoDia = new ArrayList<>();
        List<String> partes = new ArrayList<>();
        for (Map.Entry<LocalTime, Set<DayOfWeek>> e : diasPorHora.entrySet()) {
            Map<DayOfWeek, String> det = detalhes.getOrDefault(e.getKey(), Map.of());
            if (e.getValue().size() == 7) {
                String extra = det.isEmpty() ? "" : " (" + String.join(", ", det.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(d -> ABREV[d.getKey().getValue() - 1] + ": " + d.getValue()).toList()) + ")";
                todoDia.add(e.getKey() + extra);
            } else {
                for (DayOfWeek d : e.getValue()) {
                    String x = det.get(d);
                    partes.add(ABREV[d.getValue() - 1] + " " + e.getKey() + (x != null ? " (" + x + ")" : ""));
                }
            }
        }
        List<String> tudo = new ArrayList<>();
        if (!todoDia.isEmpty()) tudo.add("todo dia " + String.join(" e ", todoDia));
        tudo.addAll(partes);
        return String.join(", ", tudo);
    }
}
