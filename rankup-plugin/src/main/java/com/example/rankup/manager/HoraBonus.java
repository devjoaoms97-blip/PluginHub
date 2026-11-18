package com.example.rankup.manager;

import com.example.rankup.RankupPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;

/**
 * Hora de bônus (2026-11-17): todo dia, uma hora sorteada dentro da janela de pico ({@code hora-bonus},
 * 17h às 21h por padrão) em que a venda automática das minas listadas (só a mina PvP) paga
 * {@code multiplicador} vezes. O sorteio sai da data, então é o mesmo depois de reiniciar. Avisa no chat
 * {@code aviso-minutos} antes, no começo (com o botão [IR]) e no fim. A staff liga na hora com
 * {@code /mina admin bonus <minutos>}, pra testar.
 */
public class HoraBonus {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final RankupPlugin plugin;
    private long forcadoAte;
    private boolean ativoAntes;
    private LocalDate avisouDia;

    public HoraBonus(RankupPlugin plugin) {
        this.plugin = plugin;
        this.ativoAntes = ativo();
    }

    private boolean ligado() {
        return plugin.getConfig().getBoolean("hora-bonus.ativado", true);
    }

    public List<String> minas() {
        return plugin.getConfig().getStringList("hora-bonus.minas");
    }

    private double fator() {
        return Math.max(1, plugin.getConfig().getDouble("hora-bonus.multiplicador", 2.0));
    }

    private int duracaoMinutos() {
        return Math.max(1, plugin.getConfig().getInt("hora-bonus.duracao-minutos", 60));
    }

    private ZoneId fuso() {
        try {
            return ZoneId.of(plugin.getConfig().getString("hora-bonus.fuso", "America/Sao_Paulo"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private ZonedDateTime agora() {
        return ZonedDateTime.now(fuso());
    }

    /** Início (hora cheia) da hora de bônus do dia: sorteado na janela, sempre o mesmo pra mesma data. */
    public ZonedDateTime inicio(LocalDate dia) {
        int de = plugin.getConfig().getInt("hora-bonus.janela-inicio", 17);
        int ate = plugin.getConfig().getInt("hora-bonus.janela-fim", 21);
        int horas = Math.max(1, (ate - de) * 60 - duracaoMinutos()) / 60 + 1; // horas cheias em que cabe
        int hora = de + new Random(dia.toEpochDay() * 7919L + 17).nextInt(horas);
        return dia.atTime(hora, 0).atZone(fuso());
    }

    public ZonedDateTime fim(LocalDate dia) {
        return inicio(dia).plusMinutes(duracaoMinutos());
    }

    public boolean ativo() {
        if (!ligado()) return false;
        if (System.currentTimeMillis() < forcadoAte) return true;
        ZonedDateTime agora = agora();
        LocalDate hoje = agora.toLocalDate();
        return !agora.isBefore(inicio(hoje)) && agora.isBefore(fim(hoje));
    }

    /** Quanto a venda vale a mais nessa mina agora (1 = nada). */
    public double multiplicador(String mina) {
        return mina != null && minas().contains(mina) && ativo() ? fator() : 1;
    }

    /** Liga na hora por {@code minutos} (0 desliga). Pra staff testar. */
    public void forcar(int minutos) {
        forcadoAte = minutos <= 0 ? 0 : System.currentTimeMillis() + minutos * 60_000L;
    }

    /** "hoje das 18h às 19h" (ou "agora, até 19h"). */
    public String descricao() {
        LocalDate hoje = agora().toLocalDate();
        if (System.currentTimeMillis() < forcadoAte) return "ligada pela staff";
        ZonedDateTime ini = inicio(hoje), fim = fim(hoje);
        String faixa = "das " + hora(ini) + " às " + hora(fim);
        if (ativo()) return "agora, até as " + hora(fim);
        if (agora().isAfter(fim)) {
            LocalDate amanha = hoje.plusDays(1);
            return "amanhã, das " + hora(inicio(amanha)) + " às " + hora(fim(amanha));
        }
        return "hoje, " + faixa;
    }

    private static String hora(ZonedDateTime t) {
        return t.getMinute() == 0 ? t.getHour() + "h" : t.getHour() + "h" + String.format("%02d", t.getMinute());
    }

    private String fatorTexto() {
        double f = fator();
        return f == Math.rint(f) ? "x" + (int) f : "x" + f;
    }

    /** Chamado a cada segundo: avisos de antes, do começo e do fim. */
    public void tick() {
        if (!ligado() || minas().isEmpty()) return;
        boolean ativo = ativo();
        ZonedDateTime agora = agora();
        LocalDate hoje = agora.toLocalDate();
        int antes = plugin.getConfig().getInt("hora-bonus.aviso-minutos", 10);
        ZonedDateTime ini = inicio(hoje);
        if (antes > 0 && !ativo && !hoje.equals(avisouDia) && !agora.isBefore(ini.minusMinutes(antes)) && agora.isBefore(ini)) {
            avisouDia = hoje;
            anunciar("§6⚔ §eEm §f" + antes + " min§e, a §cMina PvP §epaga §6" + fatorTexto()
                    + " §ena venda, até as " + hora(fim(hoje)) + "!");
        }
        if (ativo && !ativoAntes) {
            anunciar("§6§l⚔ HORA DE BÔNUS! §eA venda da §cMina PvP §eestá valendo §6§l" + fatorTexto()
                    + "§e" + (System.currentTimeMillis() < forcadoAte ? "" : " até as " + hora(fim(hoje))) + ". Cuidado: lá tem PvP!");
        } else if (!ativo && ativoAntes) {
            anunciar("§7A hora de bônus da §cMina PvP §7acabou. A próxima é " + descricao() + ".");
        }
        ativoAntes = ativo;
    }

    private void anunciar(String texto) {
        Component msg = LEGADO.deserialize(texto + " ")
                .append(LEGADO.deserialize("§a§l[IR]")
                        .clickEvent(ClickEvent.runCommand("/warp minapvp"))
                        .hoverEvent(HoverEvent.showText(LEGADO.deserialize("§7Vai pro saguão da Mina PvP (/warp minapvp)"))));
        Bukkit.broadcast(msg);
    }
}
