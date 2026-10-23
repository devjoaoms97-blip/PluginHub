package com.example.discord;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ponte com o Discord: os outros plugins chamam {@link #enviar(String, String)} por reflexão
 * (sem depender deste plugin pra compilar), e a mensagem vai pro webhook da categoria
 * ({@code categorias.<categoria>.webhook-url}) ou pro webhook padrão. O envio é numa fila
 * própria, uma mensagem de cada vez e respeitando o limite do Discord (HTTP 429), então nunca
 * trava o servidor; com o Discord fora do ar, só sai um aviso no console de vez em quando.
 * Menções (@everyone etc.) ficam desligadas.
 */
public class DiscordPlugin extends JavaPlugin {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ExecutorService fila;
    private long ultimoAvisoFalha;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fila = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DiscordPlugin-envio");
            t.setDaemon(true);
            return t;
        });
        if (getConfig().getString("webhook-url", "").isBlank()) {
            getLogger().info("Sem webhook padrão (webhook-url): só as categorias com webhook próprio vão pro Discord.");
        }
    }

    @Override
    public void onDisable() {
        if (fila != null) fila.shutdown();
    }

    /**
     * API pública (os outros plugins chamam por reflexão): manda a mensagem pro Discord, se a
     * categoria estiver ligada e tiver um webhook. Cores do Minecraft (§) são tiradas.
     */
    public void enviar(String categoria, String mensagem) {
        String base = "categorias." + categoria.toLowerCase(Locale.ROOT) + ".";
        if (!getConfig().getBoolean(base + "ativado", true)) return;
        String url = getConfig().getString(base + "webhook-url", "").trim();
        if (url.isEmpty()) url = getConfig().getString("webhook-url", "").trim();
        if (url.isEmpty() || fila == null) return;
        String nome = getConfig().getString(base + "nome", getConfig().getString("nome", "Servidor"));
        String json = "{\"username\":\"" + escapar(nome) + "\",\"content\":\"" + escapar(ChatColor.stripColor(mensagem))
                + "\",\"allowed_mentions\":{\"parse\":[]}}";
        String destino = url;
        fila.submit(() -> postar(destino, json, 0));
    }

    /** Roda na fila (fora da thread do servidor). Em 429, espera o que o Discord pedir e tenta de novo. */
    private void postar(String url, String json, int tentativa) {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() == 429 && tentativa < 3) {
                double espera = resposta.headers().firstValue("Retry-After").map(Double::parseDouble).orElse(2.0);
                Thread.sleep((long) (Math.min(espera, 30) * 1000));
                postar(url, json, tentativa + 1);
            } else if (resposta.statusCode() >= 300) {
                avisarFalha("o Discord respondeu HTTP " + resposta.statusCode());
            }
        } catch (IllegalArgumentException e) {
            avisarFalha("URL de webhook inválida: " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            avisarFalha(e.getMessage());
        }
    }

    /** No máximo um aviso a cada 10 min, pra um webhook quebrado não encher o console. */
    private synchronized void avisarFalha(String motivo) {
        long agora = System.currentTimeMillis();
        if (agora - ultimoAvisoFalha < 600_000) return;
        ultimoAvisoFalha = agora;
        getLogger().warning("Não foi possível mandar pro Discord: " + motivo);
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

    /** /discord teste [categoria] e /discord reload (staff). */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            reloadConfig();
            sender.sendMessage("§aConfig do Discord recarregado.");
        } else if (sub.equals("teste")) {
            String categoria = args.length >= 2 ? args[1] : "teste";
            enviar(categoria, "Mensagem de teste do servidor (categoria " + categoria + ").");
            sender.sendMessage("§aMensagem de teste enviada pra categoria §f" + categoria + "§a (se ela tiver webhook).");
        } else {
            sender.sendMessage("§cUso: §f/discord teste [categoria] §7| §f/discord reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("teste", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("teste")) {
            ConfigurationSection secao = getConfig().getConfigurationSection("categorias");
            return secao == null ? List.of() : List.copyOf(secao.getKeys(false));
        }
        return List.of();
    }
}
