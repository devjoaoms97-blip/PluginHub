package com.example.pix;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Alerta de Pix suspeito pra staff. Depois de cada Pix entre jogadores (pagamento, cobrança aceita,
 * agendado), três regras:
 * <ul>
 *   <li><b>Pix alto</b>: a partir de {@code discord.pix-suspeito-valor} (o alerta que já existia);</li>
 *   <li><b>Perto do limite</b>: o envio deixou o pagador com {@code alertas.perto-do-limite-percentual}
 *       do limite diário ou mais (conta roubada sendo esvaziada no máximo permitido);</li>
 *   <li><b>Conta nova recebendo de várias</b>: quem entrou pela primeira vez há menos de
 *       {@code alertas.conta-nova-horas} recebeu de {@code alertas.remetentes-distintos} contas diferentes
 *       nas últimas 24 h (contas fantasma juntando dinheiro numa só).</li>
 * </ul>
 * O alerta vai pro chat de quem tem {@code pix.alertas} online, pra categoria "pix" do DiscordPlugin e
 * pro {@code alertas.log}. O mesmo alerta sobre o mesmo jogador não repete por 10 minutos. O registro
 * de quem recebeu de quem fica só na memória (24 h): um reinício zera.
 */
public class AlertaManager {

    private static final long DIA = 24L * 60 * 60 * 1000;
    private static final long REPETIR_MS = 10L * 60 * 1000;

    private record Recebimento(UUID de, long quando) {}

    private final PixPlugin plugin;
    private final DiscordIntegration discord;
    private final File arquivoLog;
    private final Map<UUID, Deque<Recebimento>> recebimentos = new HashMap<>();
    private final Map<String, Long> ultimoAlerta = new HashMap<>();

    public AlertaManager(PixPlugin plugin, DiscordIntegration discord) {
        this.plugin = plugin;
        this.discord = discord;
        this.arquivoLog = new File(plugin.getDataFolder(), "alertas.log");
    }

    /** Chamado depois do Pix já registrado (o limite do dia já conta esse envio). */
    public void conferir(UUID de, String nomeDe, UUID para, String nomePara, double valor, String tipo) {
        String valorFmt = String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);

        double alto = plugin.getConfig().getDouble("discord.pix-suspeito-valor"); // padrão do jar em configs antigos
        if (alto > 0 && valor >= alto) {
            alertar("alto:" + de + ":" + System.nanoTime(), "Pix alto: " + nomeDe + " → " + nomePara + ": " + valorFmt + " (" + tipo + ")");
        }

        LimiteManager limites = plugin.getLimiteManager();
        double percentual = plugin.getConfig().getDouble("alertas.perto-do-limite-percentual", 90);
        Player pagador = Bukkit.getPlayer(de);
        boolean semLimite = pagador != null && pagador.hasPermission(LimiteManager.PERMISSAO_IGNORAR);
        if (percentual > 0 && limites.getLimiteDiario() > 0 && !semLimite
                && limites.getGastoHoje(de) >= limites.getLimiteDiario() * percentual / 100) {
            alertar("limite:" + de, String.format(Locale.forLanguageTag("pt-BR"),
                    "%s já enviou R$ %,.2f hoje (%.0f%% do limite diário). Último: %s → %s (%s)",
                    nomeDe, limites.getGastoHoje(de), limites.getGastoHoje(de) * 100 / limites.getLimiteDiario(), valorFmt, nomePara, tipo));
        }

        int distintos = plugin.getConfig().getInt("alertas.remetentes-distintos", 3);
        double horasNova = plugin.getConfig().getDouble("alertas.conta-nova-horas", 48);
        if (distintos > 1 && horasNova > 0) {
            long agora = System.currentTimeMillis();
            Deque<Recebimento> lista = recebimentos.computeIfAbsent(para, k -> new ArrayDeque<>());
            lista.addLast(new Recebimento(de, agora));
            while (!lista.isEmpty() && agora - lista.peekFirst().quando() > DIA) lista.removeFirst();
            OfflinePlayer recebedor = Bukkit.getOfflinePlayer(para);
            long primeiraVez = recebedor.getFirstPlayed();
            boolean nova = primeiraVez > 0 && agora - primeiraVez < horasNova * 60 * 60 * 1000;
            Set<UUID> remetentes = new HashSet<>();
            lista.forEach(r -> remetentes.add(r.de()));
            if (nova && remetentes.size() >= distintos) {
                alertar("nova:" + para, String.format(Locale.forLanguageTag("pt-BR"),
                        "Conta nova (%s, entrou há %.0f h) recebeu de %d contas diferentes nas últimas 24 h. Último: %s → %s (%s)",
                        nomePara, (agora - primeiraVez) / 3_600_000.0, remetentes.size(), nomeDe, valorFmt, tipo));
            }
        }
    }

    private void alertar(String chave, String texto) {
        long agora = System.currentTimeMillis();
        if (agora - ultimoAlerta.getOrDefault(chave, 0L) < REPETIR_MS) return;
        ultimoAlerta.put(chave, agora);
        ultimoAlerta.values().removeIf(t -> agora - t > REPETIR_MS); // não cresce sem parar

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("pix.alertas")) p.sendMessage("§c§l[Pix] §e⚠ " + texto);
        }
        discord.enviar("pix", "⚠️ " + texto);
        String data = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PrintWriter out = new PrintWriter(new FileWriter(arquivoLog, StandardCharsets.UTF_8, true))) {
            out.println("[" + data + "] " + texto);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível escrever em alertas.log", e);
        }
    }
}
