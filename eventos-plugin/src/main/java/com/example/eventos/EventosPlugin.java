package com.example.eventos;

import com.example.eventos.base.Evento;
import com.example.eventos.base.EventoCommand;
import com.example.eventos.base.EventosListener;
import com.example.eventos.correoucai.CorreOuCai;
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
 * Eventos do servidor: o Vietnã ({@link Vietna}), o Spleef ({@link Spleef}) e o Corre ou Cai ({@link CorreOuCai}). Cada um abre sozinho nos
 * horários de {@code <evento>.agenda}, e só um fica aberto por vez. O que é comum aos dois (inscrição,
 * regras, prêmio) está em {@link Evento} e {@link EventosListener}.
 */
public class EventosPlugin extends JavaPlugin {

    private List<Evento> eventos;
    private DiscordIntegration discord;
    private Premio premio;
    private String ultimoMinutoAgenda = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discord = new DiscordIntegration(this);
        this.premio = new Premio(this);
        this.eventos = List.of(new Vietna(this), new Spleef(this), new CorreOuCai(this));

        for (Evento e : eventos) {
            EventoCommand comando = new EventoCommand(this, e);
            getCommand(e.id()).setExecutor(comando);
            getCommand(e.id()).setTabCompleter(comando);
        }
        getCommand("eventos").setExecutor(new AgendaCommand(this));
        getServer().getPluginManager().registerEvents(new EventosListener(this), this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> eventos.forEach(Evento::tickRapido), 2L, 2L);
        // um /reload com gente que tinha caído no meio de um evento: volta pra onde estava
        getServer().getOnlinePlayers().forEach(p -> eventos.forEach(e -> e.aoEntrar(p)));
        getLogger().info("EventosPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (eventos != null) eventos.forEach(e -> e.cancelar("o servidor está desligando"));
    }

    private void tick() {
        agenda();
        eventos.forEach(Evento::tick);
    }

    /** Abre cada evento nos horários do dia ({@code <evento>.agenda: ["10:00", "16:00"]}), uma vez por minuto agendado. */
    private void agenda() {
        LocalDateTime agora = LocalDateTime.now();
        String minuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();
        if (minuto.equals(ultimoMinutoAgenda)) return;
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
                if (aberto != null) getLogger().info(e.nome() + " agendado pulado: o " + aberto.nome() + " está aberto.");
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

    public Premio getPremio() {
        return premio;
    }
}
