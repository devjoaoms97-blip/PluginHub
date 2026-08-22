package com.example.boss.manager;

import com.example.boss.BossPlugin;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduleManager {

    private record Horario(DayOfWeek dia, LocalTime hora) {
    }

    private final BossPlugin plugin;
    private final List<Horario> horarios = new ArrayList<>();
    private String ultimoMinutoDisparado = "";

    public ScheduleManager(BossPlugin plugin) {
        this.plugin = plugin;
        carregar();
    }

    private void carregar() {
        horarios.clear();
        List<String> entradas = plugin.getConfig().getStringList("agenda");

        for (String entrada : entradas) {
            String[] partes = entrada.trim().split("\\s+");
            if (partes.length != 2) {
                plugin.getLogger().warning("Entrada de agenda inválida (esperado 'DIA HH:mm'): " + entrada);
                continue;
            }
            try {
                DayOfWeek dia = mapearDia(partes[0]);
                LocalTime hora = LocalTime.parse(partes[1]);
                if (dia != null) {
                    horarios.add(new Horario(dia, hora));
                } else {
                    plugin.getLogger().warning("Dia da semana não reconhecido na agenda: " + partes[0]);
                }
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Horário inválido na agenda (esperado HH:mm): " + partes[1]);
            }
        }
    }

    private DayOfWeek mapearDia(String texto) {
        return switch (texto.toUpperCase(Locale.ROOT)) {
            case "SEGUNDA" -> DayOfWeek.MONDAY;
            case "TERCA", "TERÇA" -> DayOfWeek.TUESDAY;
            case "QUARTA" -> DayOfWeek.WEDNESDAY;
            case "QUINTA" -> DayOfWeek.THURSDAY;
            case "SEXTA" -> DayOfWeek.FRIDAY;
            case "SABADO", "SÁBADO" -> DayOfWeek.SATURDAY;
            case "DOMINGO" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    /** Chamado periodicamente; retorna true no minuto exato em que algum horário da agenda bate. */
    public boolean deveDispararAgora() {
        LocalDateTime agora = LocalDateTime.now();
        String chaveMinuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();

        if (chaveMinuto.equals(ultimoMinutoDisparado)) {
            return false; // já disparou nesse exato minuto, evita disparo duplicado
        }

        for (Horario h : horarios) {
            if (h.dia() == agora.getDayOfWeek()
                    && h.hora().getHour() == agora.getHour()
                    && h.hora().getMinute() == agora.getMinute()) {
                ultimoMinutoDisparado = chaveMinuto;
                return true;
            }
        }
        return false;
    }

    public void recarregar() {
        carregar();
    }
}
