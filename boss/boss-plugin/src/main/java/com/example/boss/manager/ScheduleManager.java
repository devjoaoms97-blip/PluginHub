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

    /** {@code bossId}: o boss fixo desse horário (ex: "wither"), ou null pra sortear um normal. */
    private record Horario(DayOfWeek dia, LocalTime hora, String bossId) {
    }

    /** O que a agenda pede agora: o boss fixo do horário, ou {@code null} em {@code bossId} pra sortear. */
    public record Disparo(String bossId) {
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
            if (partes.length != 2 && partes.length != 3) {
                plugin.getLogger().warning("Entrada de agenda inválida (esperado 'DIA HH:mm [boss]'): " + entrada);
                continue;
            }
            try {
                DayOfWeek dia = mapearDia(partes[0]);
                LocalTime hora = LocalTime.parse(partes[1]);
                String bossId = partes.length == 3 ? partes[2].toLowerCase(Locale.ROOT) : null;
                if (dia != null) {
                    horarios.add(new Horario(dia, hora, bossId));
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

    /**
     * Chamado periodicamente; no minuto exato em que algum horário da agenda bate, retorna o que
     * disparar (o boss fixo do horário ou sorteio). Fora disso, null.
     */
    public Disparo deveDispararAgora() {
        LocalDateTime agora = LocalDateTime.now();
        String chaveMinuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();

        if (chaveMinuto.equals(ultimoMinutoDisparado)) {
            return null; // já disparou nesse exato minuto, evita disparo duplicado
        }

        for (Horario h : horarios) {
            if (h.dia() == agora.getDayOfWeek()
                    && h.hora().getHour() == agora.getHour()
                    && h.hora().getMinute() == agora.getMinute()) {
                ultimoMinutoDisparado = chaveMinuto;
                return new Disparo(h.bossId());
            }
        }
        return null;
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

    /** O próximo horário da agenda (a partir de agora), ou null se a agenda estiver vazia. */
    public LocalDateTime proximo() {
        Horario h = proximoHorario();
        return h == null ? null : proximaData(h, LocalDateTime.now());
    }

    /** O boss fixo do próximo horário da agenda (ex: "wither"), ou null se for sorteado. */
    public String proximoBossId() {
        Horario h = proximoHorario();
        return h == null ? null : h.bossId();
    }

    private Horario proximoHorario() {
        LocalDateTime agora = LocalDateTime.now();
        Horario melhor = null;
        LocalDateTime melhorData = null;
        for (Horario h : horarios) {
            LocalDateTime candidato = proximaData(h, agora);
            if (candidato != null && (melhorData == null || candidato.isBefore(melhorData))) {
                melhor = h;
                melhorData = candidato;
            }
        }
        return melhor;
    }

    private LocalDateTime proximaData(Horario h, LocalDateTime agora) {
        for (int d = 0; d <= 7; d++) {
            LocalDateTime candidato = agora.toLocalDate().plusDays(d).atTime(h.hora());
            if (candidato.getDayOfWeek() == h.dia() && candidato.isAfter(agora)) return candidato;
        }
        return null;
    }

    public void recarregar() {
        carregar();
    }
}
