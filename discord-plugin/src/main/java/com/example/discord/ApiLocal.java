package com.example.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * API HTTP local pro bot do Discord (discord-bot/). Escuta só em {@code api.endereco}
 * (127.0.0.1 por padrão) e exige {@code Authorization: Bearer <api.token>} em tudo.
 *
 * <pre>
 * GET  /saude        → {"ok":true,"online":3,"maximo":20,"jogadores":[...],"tps":20.0,"proximoBoss","poteMineSena","horarioMineSena"}
 * GET  /vinculos     → {"vinculos":[{"uuid","nick","discordId","vip"}]}
 * POST /vinculo      {"codigo","discordId","discordNome","recompensa":true} → {"ok","nick","uuid","recompensado"} ou {"ok":false,"erro"}
 * POST /desvincular  {"discordId"} ou {"nick"} → {"ok","nick","discordId"}
 * POST /entregar     {"pagamento","pedido","uuid","creditos"} → {"ok","nick","repetida"} (compra da loja; veja Entregas)
 * </pre>
 * Cada pedido roda na thread do servidor (mexe em jogador e roda comandos) e responde em até 5 s.
 */
public class ApiLocal {

    private final DiscordPlugin plugin;
    private final Vinculos vinculos;
    private final Entregas entregas;
    private final Gson gson = new Gson();
    private HttpServer http;

    public ApiLocal(DiscordPlugin plugin, Vinculos vinculos, Entregas entregas) {
        this.plugin = plugin;
        this.vinculos = vinculos;
        this.entregas = entregas;
    }

    public void ligar(String endereco, int porta, String token) throws IOException {
        http = HttpServer.create(new InetSocketAddress(endereco, porta), 0);
        http.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DiscordPlugin-api");
            t.setDaemon(true);
            return t;
        }));
        http.createContext("/", troca -> {
            try {
                atender(troca, token);
            } catch (Exception e) {
                responder(troca, 500, erro("erro interno: " + e.getMessage()));
            } finally {
                troca.close();
            }
        });
        http.start();
    }

    public void desligar() {
        if (http != null) http.stop(0);
    }

    private void atender(HttpExchange troca, String token) throws Exception {
        String recebido = troca.getRequestHeaders().getFirst("Authorization");
        if (recebido == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8))) {
            responder(troca, 401, erro("token inválido"));
            return;
        }
        String rota = troca.getRequestMethod() + " " + troca.getRequestURI().getPath();
        JsonObject corpo = new JsonObject();
        if (troca.getRequestMethod().equals("POST")) {
            String texto = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                corpo = texto.isBlank() ? new JsonObject() : gson.fromJson(texto, JsonObject.class);
            } catch (JsonParseException e) {
                responder(troca, 400, erro("JSON inválido"));
                return;
            }
        }
        JsonObject pedido = corpo;
        JsonObject resposta = switch (rota) {
            case "GET /saude" -> naThread(this::saude);
            case "GET /vinculos" -> naThread(this::listar);
            case "POST /vinculo" -> naThread(() -> vincular(pedido));
            case "POST /desvincular" -> naThread(() -> desvincular(pedido));
            case "POST /entregar" -> naThread(() -> entregar(pedido));
            default -> null;
        };
        if (resposta == null) responder(troca, 404, erro("rota desconhecida: " + rota));
        else responder(troca, resposta.has("ok") && !resposta.get("ok").getAsBoolean() ? 400 : 200, resposta);
    }

    /** Pro #status: online, nomes, TPS, próximo boss (BossPlugin) e pote da Mine-sena (ChequePlugin). */
    private JsonObject saude() {
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("online", Bukkit.getOnlinePlayers().size());
        r.addProperty("maximo", Bukkit.getMaxPlayers());
        JsonArray nomes = new JsonArray();
        Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(nomes::add);
        r.add("jogadores", nomes);
        r.addProperty("tps", Math.min(20, Math.round(Bukkit.getTPS()[0] * 10) / 10.0));
        Object boss = chamar("BossPlugin", "textoProximoBoss");
        if (boss != null) r.addProperty("proximoBoss", boss.toString());
        Object mineSena = chamar("ChequePlugin", "getMineSena");
        if (mineSena != null) {
            try {
                r.addProperty("poteMineSena", (Double) mineSena.getClass().getMethod("getPote").invoke(mineSena));
                Plugin cheque = Bukkit.getPluginManager().getPlugin("ChequePlugin");
                r.addProperty("horarioMineSena", cheque.getConfig().getString("minesena.horario", "18:00"));
            } catch (ReflectiveOperationException | ClassCastException ignorado) {
                // versão sem a Mine-sena: só não mostra
            }
        }
        return r;
    }

    /** Método sem argumentos de outro plugin, por reflexão; null se ele não existir ou falhar. */
    private static Object chamar(String nomePlugin, String metodo) {
        Plugin outro = Bukkit.getPluginManager().getPlugin(nomePlugin);
        if (outro == null || !outro.isEnabled()) return null;
        try {
            return outro.getClass().getMethod(metodo).invoke(outro);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private JsonObject listar() {
        JsonArray lista = new JsonArray();
        for (Vinculos.Vinculo v : vinculos.todos()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", v.uuid().toString());
            o.addProperty("nick", v.nick());
            o.addProperty("discordId", v.discordId());
            o.addProperty("vip", vinculos.tierVip(v.uuid()));
            lista.add(o);
        }
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.add("vinculos", lista);
        return r;
    }

    private JsonObject vincular(JsonObject p) {
        Vinculos.Resultado res = vinculos.confirmar(texto(p, "codigo"), texto(p, "discordId"), texto(p, "discordNome"),
                p.has("recompensa") && p.get("recompensa").getAsBoolean());
        if (!res.ok()) return erro(res.erro());
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("nick", res.vinculo().nick());
        r.addProperty("uuid", res.vinculo().uuid().toString());
        r.addProperty("vip", vinculos.tierVip(res.vinculo().uuid()));
        r.addProperty("recompensado", res.recompensado());
        return r;
    }

    private JsonObject desvincular(JsonObject p) {
        UUID uuid = null;
        String nick = texto(p, "nick");
        if (nick != null) {
            var jogador = vinculos.acharJogador(nick);
            if (jogador == null) return erro("Jogador " + nick + " não encontrado.");
            uuid = jogador.getUniqueId();
        }
        Vinculos.Vinculo v = vinculos.remover(uuid, texto(p, "discordId"));
        if (v == null) return erro("Não há vínculo pra desfazer.");
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("nick", v.nick());
        r.addProperty("discordId", v.discordId());
        return r;
    }

    private JsonObject entregar(JsonObject p) {
        UUID uuid;
        long creditos;
        try {
            uuid = UUID.fromString(texto(p, "uuid"));
            creditos = p.get("creditos").getAsLong();
        } catch (RuntimeException e) {
            return erro("uuid ou creditos inválidos.");
        }
        Entregas.Resultado res = entregas.entregar(texto(p, "pagamento"), texto(p, "pedido"), uuid, creditos);
        if (!res.ok()) return erro(res.erro());
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("nick", res.nick());
        r.addProperty("repetida", res.repetida());
        return r;
    }

    private JsonObject naThread(Callable<JsonObject> tarefa) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, tarefa).get(5, TimeUnit.SECONDS);
    }

    private static String texto(JsonObject o, String campo) {
        return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsString() : null;
    }

    private static JsonObject erro(String mensagem) {
        JsonObject r = new JsonObject();
        r.addProperty("ok", false);
        r.addProperty("erro", mensagem);
        return r;
    }

    private void responder(HttpExchange troca, int status, JsonObject corpo) throws IOException {
        byte[] bytes = gson.toJson(corpo).getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }
}
