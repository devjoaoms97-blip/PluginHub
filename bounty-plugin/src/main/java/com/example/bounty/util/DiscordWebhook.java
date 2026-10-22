package com.example.bounty.util;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Manda anúncios pra um canal do Discord por webhook ({@code discord.webhook-url} no config).
 * O envio é assíncrono (não trava o servidor) e uma falha só gera aviso no console — o Discord
 * fora do ar nunca atrapalha o jogo. Menções (@everyone etc.) ficam desligadas no envio.
 */
public class DiscordWebhook {

    private final JavaPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private long ultimoAvisoFalha;

    public DiscordWebhook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Envia se o webhook estiver configurado e {@code valor} for pelo menos {@code discord.valor-minimo}. */
    public void anunciar(String mensagem, double valor) {
        String url = plugin.getConfig().getString("discord.webhook-url", "").trim();
        if (url.isEmpty() || valor < plugin.getConfig().getDouble("discord.valor-minimo", 1000)) return;

        String nome = plugin.getConfig().getString("discord.nome", "Bounty");
        String json = "{\"username\":\"" + escapar(nome) + "\",\"content\":\"" + escapar(ChatColor.stripColor(mensagem))
                + "\",\"allowed_mentions\":{\"parse\":[]}}";
        HttpRequest requisicao;
        try {
            requisicao = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (IllegalArgumentException e) {
            avisarFalha("URL do webhook inválida em discord.webhook-url");
            return;
        }
        http.sendAsync(requisicao, HttpResponse.BodyHandlers.discarding()).whenComplete((resposta, erro) -> {
            if (erro != null) {
                avisarFalha(erro.getMessage());
            } else if (resposta.statusCode() >= 300) {
                avisarFalha("o Discord respondeu HTTP " + resposta.statusCode());
            }
        });
    }

    /** No máximo um aviso a cada 10 min, pra um webhook quebrado não encher o console. */
    private synchronized void avisarFalha(String motivo) {
        long agora = System.currentTimeMillis();
        if (agora - ultimoAvisoFalha < 600_000) return;
        ultimoAvisoFalha = agora;
        plugin.getLogger().warning("Não foi possível anunciar no Discord: " + motivo);
    }

    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
