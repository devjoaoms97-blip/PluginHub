package com.example.gladiador.manager;

import com.example.gladiador.GladiadorPlugin;
import com.example.gladiador.model.Evento;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Abre o Gladiador sozinho nos horários do config ({@code agenda: ["SABADO 20:00 cla"]}), no
 * mesmo formato da agenda do Boss com o modo no fim. Se já houver um evento aberto, pula.
 */
public class AgendaManager {

    private record Horario(DayOfWeek dia, LocalTime hora, Evento.Modo modo) {
    }

    private static final Map<String, DayOfWeek> DIAS = Map.ofEntries(
            Map.entry("SEGUNDA", DayOfWeek.MONDAY), Map.entry("TERCA", DayOfWeek.TUESDAY), Map.entry("TERÇA", DayOfWeek.TUESDAY),
            Map.entry("QUARTA", DayOfWeek.WEDNESDAY), Map.entry("QUINTA", DayOfWeek.THURSDAY), Map.entry("SEXTA", DayOfWeek.FRIDAY),
            Map.entry("SABADO", DayOfWeek.SATURDAY), Map.entry("SÁBADO", DayOfWeek.SATURDAY), Map.entry("DOMINGO", DayOfWeek.SUNDAY));

    private final GladiadorPlugin plugin;
    private final List<Horario> horarios = new ArrayList<>();
    private String ultimoMinuto = "";

    public AgendaManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        carregar();
    }

    public void carregar() {
        horarios.clear();
        for (String entrada : plugin.getConfig().getStringList("agenda")) {
            String[] partes = entrada.trim().split("\\s+");
            DayOfWeek dia = partes.length == 3 ? DIAS.get(partes[0].toUpperCase(Locale.ROOT)) : null;
            Evento.Modo modo = partes.length == 3 ? modo(partes[2]) : null;
            try {
                if (dia == null || modo == null) throw new DateTimeParseException("", entrada, 0);
                horarios.add(new Horario(dia, LocalTime.parse(partes[1]), modo));
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Entrada de agenda inválida (esperado 'DIA HH:mm individual|cla'): " + entrada);
            }
        }
    }

    public static Evento.Modo modo(String texto) {
        return switch (texto.toLowerCase(Locale.ROOT)) {
            case "individual", "solo" -> Evento.Modo.INDIVIDUAL;
            case "cla", "clã", "clas", "clãs" -> Evento.Modo.CLA;
            default -> null;
        };
    }

    /** A cada segundo; dispara uma vez por minuto agendado. */
    public void tick() {
        LocalDateTime agora = LocalDateTime.now();
        String minuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();
        if (minuto.equals(ultimoMinuto)) return;
        for (Horario h : horarios) {
            if (h.dia() == agora.getDayOfWeek() && h.hora().getHour() == agora.getHour() && h.hora().getMinute() == agora.getMinute()) {
                ultimoMinuto = minuto;
                if (plugin.getEventoManager().getEvento() != null) {
                    plugin.getLogger().info("Gladiador agendado pulado: já há um evento aberto.");
                } else if (h.modo() == Evento.Modo.CLA && !plugin.getClasIntegration().disponivel()) {
                    plugin.getLogger().warning("Gladiador por clã agendado pulado: o ClasPlugin não está instalado.");
                } else {
                    plugin.getEventoManager().abrir(h.modo(), plugin.inscricaoPadrao());
                }
                return;
            }
        }
    }
}
