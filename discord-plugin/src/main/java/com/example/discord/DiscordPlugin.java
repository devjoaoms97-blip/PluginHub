package com.example.discord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Ponte com o Discord: os outros plugins chamam {@link #enviar(String, String)} por reflexão
 * (sem depender deste plugin pra compilar), e a mensagem vai como embed pro webhook da categoria
 * ({@code categorias.<categoria>.webhook-url}) ou pro webhook padrão, com o título, a cor e,
 * se configurado, a menção ao cargo da categoria. Além disso, os anúncios do chat que batem com
 * {@code encaminhar} vão pro Discord sem o plugin de origem saber, e o servidor avisa quando liga
 * e desliga (categoria "status").
 *
 * O envio é numa fila própria, uma mensagem de cada vez e respeitando o limite do Discord (HTTP
 * 429), então nunca trava o servidor; com o Discord fora do ar, só sai um aviso no console de vez
 * em quando. Só o cargo configurado da categoria pode ser marcado (nunca @everyone).
 */
public class DiscordPlugin extends JavaPlugin implements Listener {

    private static final int LIMITE_DESCRICAO = 4000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ExecutorService fila;
    private long ultimoAvisoFalha;
    private final List<Encaminhamento> encaminhamentos = new ArrayList<>();
    private Vinculos vinculos;
    private ApiLocal api;

    private record Encaminhamento(String categoria, Pattern padrao) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        vinculos = new Vinculos(this);
        ligarApi();
        getServer().getScheduler().runTaskTimer(this, vinculos::limparCodigos, 20L * 60, 20L * 60);
        fila = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DiscordPlugin-envio");
            t.setDaemon(true);
            return t;
        });
        carregarEncaminhamentos();
        getServer().getPluginManager().registerEvents(this, this);
        if (getConfig().getString("webhook-url", "").isBlank()) {
            getLogger().info("Sem webhook padrão (webhook-url): só as categorias com webhook próprio vão pro Discord.");
        }
        // O primeiro tick só roda com o servidor pronto (todos os plugins ligados e mundos carregados).
        getServer().getScheduler().runTask(this, () ->
                enviar("status", getConfig().getString("status.ligou", "🟢 Servidor online! Bora jogar.")));
    }

    @Override
    public void onDisable() {
        if (api != null) api.desligar();
        if (fila != null) fila.shutdown();
        // Na hora de desligar não dá pra contar com a fila: manda direto, com prazo curto.
        String json = montar("status", getConfig().getString("status.desligou", "🔴 Servidor desligado."));
        String url = urlDa("status");
        if (json != null && url != null) postar(url, json, 2, Duration.ofSeconds(3));
    }

    /**
     * API pública (os outros plugins chamam por reflexão): manda a mensagem pro Discord, se a
     * categoria estiver ligada e tiver um webhook. Cores do Minecraft (§) são tiradas.
     */
    public void enviar(String categoria, String mensagem) {
        String url = urlDa(categoria);
        String json = montar(categoria, mensagem);
        if (url == null || json == null || fila == null || fila.isShutdown()) return;
        fila.submit(() -> postar(url, json, 0, Duration.ofSeconds(10)));
    }

    private String base(String categoria) {
        return "categorias." + categoria.toLowerCase(Locale.ROOT) + ".";
    }

    /** Webhook da categoria, ou o padrão; null se a categoria estiver desligada ou sem webhook. */
    private String urlDa(String categoria) {
        String base = base(categoria);
        if (!getConfig().getBoolean(base + "ativado", true)) return null;
        String url = getConfig().getString(base + "webhook-url", "").trim();
        if (url.isEmpty()) url = getConfig().getString("webhook-url", "").trim();
        return url.isEmpty() ? null : url;
    }

    /** O JSON do webhook: um embed com o título e a cor da categoria, e a menção ao cargo dela. */
    private String montar(String categoria, String mensagem) {
        String base = base(categoria);
        String texto = ChatColor.stripColor(mensagem == null ? "" : mensagem).trim();
        if (texto.isEmpty()) return null;
        if (texto.length() > LIMITE_DESCRICAO) texto = texto.substring(0, LIMITE_DESCRICAO - 1) + "…";

        String nome = getConfig().getString(base + "nome", getConfig().getString("nome", "BrasaMC"));
        String titulo = getConfig().getString(base + "titulo", "");
        int cor = cor(getConfig().getString(base + "cor", getConfig().getString("cor", "#f26b1d")));
        String cargo = getConfig().getString(base + "mencionar", "").trim();
        String quando = getConfig().getString(base + "mencionar-se", "").trim();
        boolean marcar = cargo.matches("\\d{5,25}") && (quando.isEmpty() || casa(quando, texto));

        StringBuilder json = new StringBuilder("{\"username\":\"").append(escapar(nome)).append('"');
        if (marcar) json.append(",\"content\":\"<@&").append(cargo).append(">\"");
        json.append(",\"allowed_mentions\":{\"parse\":[]");
        if (marcar) json.append(",\"roles\":[\"").append(cargo).append("\"]");
        json.append("},\"embeds\":[{");
        if (!titulo.isBlank()) json.append("\"title\":\"").append(escapar(titulo)).append("\",");
        json.append("\"description\":\"").append(escapar(texto)).append("\",\"color\":").append(cor)
                .append(",\"timestamp\":\"").append(Instant.now()).append('"');
        String rodape = getConfig().getString("rodape", "BrasaMC");
        if (!rodape.isBlank()) json.append(",\"footer\":{\"text\":\"").append(escapar(rodape)).append("\"}");
        json.append("}]}");
        return json.toString();
    }

    private boolean casa(String regex, String texto) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(texto).find();
        } catch (PatternSyntaxException e) {
            avisarFalha("padrão inválido em mencionar-se: " + regex);
            return false;
        }
    }

    private static int cor(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16);
        } catch (NumberFormatException e) {
            return 0xf26b1d;
        }
    }

    /** Em 429, espera o que o Discord pedir e tenta de novo (até 3 vezes). */
    private void postar(String url, String json, int tentativa, Duration prazo) {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                    .timeout(prazo)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() == 429 && tentativa < 3) {
                double espera = resposta.headers().firstValue("Retry-After").map(Double::parseDouble).orElse(2.0);
                Thread.sleep((long) (Math.min(espera, 30) * 1000));
                postar(url, json, tentativa + 1, prazo);
            } else if (resposta.statusCode() >= 300) {
                avisarFalha("o Discord respondeu HTTP " + resposta.statusCode() + ": " + resposta.body());
            }
        } catch (IllegalArgumentException e) {
            avisarFalha("URL de webhook inválida");
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

    // ---------------------------------------------------------------------
    // API local pro bot e vínculo de contas
    // ---------------------------------------------------------------------

    /** Liga a API do bot. Sem token no config, gera um e salva (o bot precisa do mesmo no .env dele). */
    private void ligarApi() {
        if (!getConfig().getBoolean("api.ativado", true)) return;
        String token = getConfig().getString("api.token", "").trim();
        if (token.length() < 16) {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            token = HexFormat.of().formatHex(bytes);
            getConfig().set("api.token", token);
            saveConfig();
            getLogger().info("Token da API do bot gerado em config.yml (api.token). Copie pro .env do bot (PLUGIN_API_TOKEN).");
        }
        String endereco = getConfig().getString("api.endereco", "127.0.0.1");
        int porta = getConfig().getInt("api.porta", 8155);
        api = new ApiLocal(this, vinculos, new Entregas(this));
        try {
            api.ligar(endereco, porta, token);
            getLogger().info("API do bot em http://" + endereco + ":" + porta);
        } catch (IOException e) {
            api = null;
            getLogger().warning("Não foi possível abrir a API do bot na porta " + porta + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        vinculos.atualizarNick(evento.getPlayer());
    }

    /** /vincular: gera o código que o jogador digita no Discord. */
    private void comandoVincular(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores vinculam a conta.");
            return;
        }
        Vinculos.Vinculo atual = vinculos.doJogador(jogador.getUniqueId());
        if (atual != null) {
            jogador.sendMessage("§aSua conta já está vinculada ao Discord §f" + atual.discordNome()
                    + "§a. §7Pra trocar, abra um ticket no Discord.");
            return;
        }
        String codigo = vinculos.gerarCodigo(jogador);
        jogador.sendMessage("§6§l🔗 Vincular ao Discord");
        jogador.sendMessage(Component.text("Seu código: ", NamedTextColor.GRAY)
                .append(Component.text(codigo, NamedTextColor.YELLOW,
                        TextDecoration.BOLD))
                .append(Component.text(" (clique pra copiar)", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.copyToClipboard(codigo))
                .hoverEvent(HoverEvent.showText(Component.text("Copiar o código"))));
        jogador.sendMessage("§7No Discord, use §f/vincular " + codigo + " §7ou o botão em §f#vincular-conta§7. Vale por §f"
                + vinculos.minutosDoCodigo() + " min§7.");
        String convite = getConfig().getString("vinculo.convite", "");
        if (!convite.isBlank()) jogador.sendMessage("§7Discord: §b" + convite);
    }

    /** /discord vinculo <jogador> e /discord desvincular <jogador> (staff). */
    private void comandoVinculoStaff(CommandSender sender, String sub, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: §f/discord " + sub + " <jogador>");
            return;
        }
        OfflinePlayer alvo = vinculos.acharJogador(args[1]);
        if (alvo == null) {
            sender.sendMessage("§cJogador §f" + args[1] + " §cnão encontrado.");
            return;
        }
        if (sub.equals("desvincular")) {
            Vinculos.Vinculo v = vinculos.remover(alvo.getUniqueId(), null);
            sender.sendMessage(v == null ? "§e" + args[1] + " não tem vínculo." : "§aVínculo de §f" + v.nick() + " §acom §f" + v.discordNome() + " §adesfeito.");
            return;
        }
        Vinculos.Vinculo v = vinculos.doJogador(alvo.getUniqueId());
        sender.sendMessage(v == null ? "§e" + args[1] + " não tem vínculo."
                : "§f" + v.nick() + " §7→ Discord §f" + v.discordNome() + " §7(" + v.discordId() + "), desde §f"
                + new SimpleDateFormat("dd/MM/yyyy").format(new Date(v.desde())));
    }

    // ---------------------------------------------------------------------
    // Anúncios do chat encaminhados (rankup, prestígio, XP em dobro, alta na loja...)
    // ---------------------------------------------------------------------

    private void carregarEncaminhamentos() {
        encaminhamentos.clear();
        for (Map<?, ?> item : getConfig().getMapList("encaminhar")) {
            Object categoria = item.get("categoria");
            Object padrao = item.get("padrao");
            if (categoria == null || padrao == null) continue;
            try {
                encaminhamentos.add(new Encaminhamento(categoria.toString(), Pattern.compile(padrao.toString())));
            } catch (PatternSyntaxException e) {
                getLogger().warning("Padrão inválido em encaminhar (" + categoria + "): " + e.getDescription());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoAnunciar(BroadcastMessageEvent evento) {
        if (encaminhamentos.isEmpty()) return;
        String texto = PlainTextComponentSerializer.plainText().serialize(evento.message());
        for (Encaminhamento e : encaminhamentos) {
            if (e.padrao().matcher(texto).find()) {
                enviar(e.categoria(), texto);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Comandos
    // ---------------------------------------------------------------------

    /** /vincular (jogador) e /discord teste|reload|vinculo|desvincular (staff). */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vincular")) {
            comandoVincular(sender);
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("vinculo") || sub.equals("desvincular")) {
            comandoVinculoStaff(sender, sub, args);
        } else if (sub.equals("reload")) {
            reloadConfig();
            carregarEncaminhamentos();
            sender.sendMessage("§aConfig do Discord recarregado (" + encaminhamentos.size() + " encaminhamentos).");
        } else if (sub.equals("teste")) {
            String alvo = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "teste";
            List<String> categorias = alvo.equals("todas") ? categorias() : List.of(alvo);
            for (String categoria : categorias) {
                if (urlDa(categoria) == null) {
                    sender.sendMessage("§e" + categoria + "§7: desligada ou sem webhook, nada enviado.");
                    continue;
                }
                enviar(categoria, "Mensagem de teste do servidor (categoria **" + categoria + "**). "
                        + getConfig().getString(base(categoria) + "teste", ""));
                sender.sendMessage("§a" + categoria + "§7: teste enviado.");
            }
        } else {
            sender.sendMessage("§cUso: §f/discord teste [categoria|todas] §7| §f/discord reload §7| §f/discord vinculo <jogador> §7| §f/discord desvincular <jogador>");
        }
        return true;
    }

    private List<String> categorias() {
        ConfigurationSection secao = getConfig().getConfigurationSection("categorias");
        return secao == null ? List.of() : List.copyOf(secao.getKeys(false));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("vincular")) return List.of();
        if (args.length == 1) return List.of("teste", "reload", "vinculo", "desvincular");
        if (args.length == 2 && (args[0].equalsIgnoreCase("vinculo") || args[0].equalsIgnoreCase("desvincular"))) return null;
        if (args.length == 2 && args[0].equalsIgnoreCase("teste")) {
            List<String> opcoes = new ArrayList<>(categorias());
            opcoes.add("todas");
            return opcoes;
        }
        return List.of();
    }
}
