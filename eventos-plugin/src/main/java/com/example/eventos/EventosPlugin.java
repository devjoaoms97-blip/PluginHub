package com.example.eventos;

import com.example.eventos.base.Evento;
import com.example.eventos.base.EventoCommand;
import com.example.eventos.base.EventosListener;
import com.example.eventos.correoucai.CorreOuCai;
import com.example.eventos.corrida.CorridaMaluca;
import com.example.eventos.quiz.Quiz;
import com.example.eventos.quiz.QuizCommand;
import com.example.eventos.sobrevivencia.Sobrevivencia;
import com.example.eventos.spleef.Spleef;
import com.example.eventos.util.DiscordIntegration;
import com.example.eventos.util.Premio;
import com.example.eventos.vietna.Vietna;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Eventos do servidor: o Vietnã ({@link Vietna}), o Spleef ({@link Spleef}), o Corre ou Cai ({@link CorreOuCai}), a Sobrevivência ({@link Sobrevivencia}) e a Corrida Maluca ({@link CorridaMaluca}). Cada um abre sozinho nos
 * horários de {@code <evento>.agenda}, e só um fica aberto por vez. O que é comum a todos (inscrição,
 * regras, prêmio) está em {@link Evento} e {@link EventosListener}.
 */
public class EventosPlugin extends JavaPlugin {

    private List<Evento> eventos;
    private DiscordIntegration discord;
    private Premio premio;
    private Quiz quiz;
    private RankingGeral ranking;
    private String ultimoMinutoAgenda = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discord = new DiscordIntegration(this);
        this.premio = new Premio(this);
        this.quiz = new Quiz(this);
        this.ranking = new RankingGeral(this);
        QuizCommand comandoQuiz = new QuizCommand(this);
        getCommand("quiz").setExecutor(comandoQuiz);
        getCommand("quiz").setTabCompleter(comandoQuiz);
        this.eventos = List.of(new Vietna(this), new Spleef(this), new CorreOuCai(this), new Sobrevivencia(this), new CorridaMaluca(this));

        for (Evento e : eventos) {
            EventoCommand comando = new EventoCommand(this, e);
            getCommand(e.id()).setExecutor(comando);
            getCommand(e.id()).setTabCompleter(comando);
        }
        getCommand("eventos").setExecutor(new AgendaCommand(this));
        getServer().getPluginManager().registerEvents(new EventosListener(this), this);
        for (Evento e : eventos) if (e instanceof org.bukkit.event.Listener l) getServer().getPluginManager().registerEvents(l, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> eventos.forEach(Evento::tickRapido), 2L, 2L);
        // o holograma do ranking: as vitórias do Gladiador mudam fora daqui, e o lugar pode ter acabado de carregar
        getServer().getScheduler().runTaskTimer(this, ranking::atualizarHolograma, 100L, 1200L);
        // um /reload com gente que tinha caído no meio de um evento: volta pra onde estava
        getServer().getOnlinePlayers().forEach(p -> eventos.forEach(e -> e.aoEntrar(p)));
        getLogger().info("EventosPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (eventos != null) eventos.forEach(e -> e.cancelar("o servidor está desligando"));
        if (quiz != null) quiz.cancelar("o servidor está desligando");
        if (ranking != null) ranking.removerHolograma();
    }

    private void tick() {
        agenda();
        eventos.forEach(Evento::tick);
        quiz.tick();
    }

    /** Abre cada evento nos horários do dia ({@code <evento>.agenda: ["10:00", "16:00"]}), uma vez por minuto agendado. */
    private void agenda() {
        LocalDateTime agora = LocalDateTime.now();
        String minuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();
        if (minuto.equals(ultimoMinutoAgenda)) return;
        // o Quiz é no chat: roda junto com qualquer evento de arena
        for (String horario : getConfig().getStringList("quiz.agenda")) {
            if (!horario.trim().equals(String.format("%02d:%02d", agora.getHour(), agora.getMinute()))) continue;
            ultimoMinutoAgenda = minuto;
            if (!quiz.isAtivo()) quiz.abrir();
            return;
        }
        for (Evento e : eventos) {
            for (String horario : e.cfg().getStringList("agenda")) {
                try {
                    LocalTime h = LocalTime.parse(horario.trim());
                    if (h.getHour() != agora.getHour() || h.getMinute() != agora.getMinute()) continue;
                } catch (DateTimeParseException ex) {
                    continue;
                }
                ultimoMinutoAgenda = minuto;
                Evento aberto = eventoAberto();
                if (aberto != null) getLogger().info(e.nome() + " agendado pulado: " + aberto.oNome() + (aberto.feminino() ? " está aberta." : " está aberto."));
                else e.abrir();
                return;
            }
        }
    }

    public boolean isAdmin(Player p) {
        return p.hasPermission("eventos.admin");
    }

    public List<Evento> getEventos() {
        return eventos;
    }

    /** O evento aberto agora (inscrição, luta ou fim), ou null. */
    public Evento eventoAberto() {
        for (Evento e : eventos) if (e.getFase() != null) return e;
        return null;
    }

    /** O evento em que o jogador está lutando, ou null. */
    public Evento eventoLutando(UUID jogador) {
        for (Evento e : eventos) if (e.lutando(jogador) != null) return e;
        return null;
    }

    /** O evento em que o jogador está inscrito (ou lutando), ou null. */
    public Evento eventoInscrito(UUID jogador) {
        for (Evento e : eventos) if (e.getFase() != null && e.getParticipantes().containsKey(jogador)) return e;
        return null;
    }

    public DiscordIntegration getDiscord() {
        return discord;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public RankingGeral getRanking() {
        return ranking;
    }

    public Premio getPremio() {
        return premio;
    }
}
