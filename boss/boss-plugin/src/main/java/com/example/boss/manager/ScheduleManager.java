package com.example.boss.manager;

import com.example.boss.BossPlugin;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ScheduleManager {

    private record Horario(DayOfWeek dia, LocalTime hora) {
    }

    private final BossPlugin plugin;
    private final List<Horario> horarios = new ArrayList<>();
    private String ultimoMinutoDisparado = "";

    // Quantos minutos antes de cada horário da agenda avisar no chat (ex: 10, 5, 1)
    private final List<Integer> minutosDeAviso = new ArrayList<>();
    // Avisos já mandados no minuto atual, pra não repetir quando a checagem roda mais de uma vez no mesmo minuto
    private final Set<Integer> avisosDisparadosNoMinuto = new HashSet<>();
    private String minutoDosAvisos = "";

    public ScheduleManager(BossPlugin plugin) {
        this.plugin = plugin;
        carregar();
    }

    private void carregar() {
        horarios.clear();
        minutosDeAviso.clear();
        // config.yml antigo (sem a chave) cai no padrão; lista vazia explícita desativa
        List<Integer> avisos = plugin.getConfig().contains("aviso-agenda.minutos-antes")
                ? plugin.getConfig().getIntegerList("aviso-agenda.minutos-antes")
                : List.of(10, 5, 1);
        for (int minutos : avisos) {
            if (minutos > 0) minutosDeAviso.add(minutos);
        }
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

    /**
     * Chamado periodicamente; retorna quantos minutos faltam pro próximo horário da agenda
     * quando esse número bate com um dos avisos configurados (aviso-agenda.minutos-antes),
     * ou -1 se não tem aviso pra mandar agora. Cada aviso dispara uma vez só.
     */
    public int avisoParaDispararAgora() {
        LocalDateTime agora = LocalDateTime.now();
        String chaveMinuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();
        if (!chaveMinuto.equals(minutoDosAvisos)) {
            minutoDosAvisos = chaveMinuto;
            avisosDisparadosNoMinuto.clear();
        }

        for (int minutos : minutosDeAviso) {
            if (avisosDisparadosNoMinuto.contains(minutos)) continue;

            LocalDateTime alvo = agora.plusMinutes(minutos);
            for (Horario h : horarios) {
                if (h.dia() == alvo.getDayOfWeek()
                        && h.hora().getHour() == alvo.getHour()
                        && h.hora().getMinute() == alvo.getMinute()) {
                    avisosDisparadosNoMinuto.add(minutos);
                    return minutos;
                }
            }
        }
        return -1;
    }

    public void recarregar() {
        carregar();
    }
}
