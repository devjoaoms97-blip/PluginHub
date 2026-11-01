package com.example.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-flood, pra todo mundo (menos quem tem {@code chat.antiflood.ignorar}, que nem a staff tem por
 * padrão):
 * <ul>
 *   <li><b>Chat e comandos de mensagem</b> ({@code comandos-de-mensagem}: /msg, /r, /cc...), contados
 *       juntos: um intervalo mínimo entre mensagens, a mesma mensagem não repete por um tempo
 *       (comparando sem maiúsculas, acentos, espaços e pontuação) e, numa rajada, o jogador fica um
 *       tempo sem falar;</li>
 *   <li><b>Comandos que anunciam ou cobram</b> ({@code comandos-com-intervalo}: /vip vender, /pix
 *       qrcode, /tpa...): um a cada tantos segundos.</li>
 * </ul>
 * O comando é reconhecido pelo nome dele, então os aliases contam (/clan c = /cla c, /tell = /msg).
 * O chat é assíncrono e os comandos rodam na thread principal: o estado de cada jogador é sincronizado.
 */
public class AntiFlood implements Listener {

    private static final String IGNORAR = "chat.antiflood.ignorar";

    private static class Estado {
        long ultima;
        String ultimoTexto = "";
        long ultimoTextoEm;
        final Deque<Long> recentes = new ArrayDeque<>();
        long silenciadoAte;
        final Map<String, Long> comandos = new HashMap<>();
    }

    private final ChatPlugin plugin;
    private final Map<UUID, Estado> estados = new ConcurrentHashMap<>();
    private Map<String, Integer> deMensagem = Map.of();
    private Map<String, Integer> comIntervalo = Map.of();

    AntiFlood(ChatPlugin plugin) {
        this.plugin = plugin;
        recarregar();
    }

    void recarregar() {
        deMensagem = ler("comandos-de-mensagem");
        comIntervalo = ler("comandos-com-intervalo");
    }

    private Map<String, Integer> ler(String chave) {
        Map<String, Integer> mapa = new HashMap<>();
        ConfigurationSection secao = plugin.getConfig().getConfigurationSection(chave);
        if (secao != null) {
            for (String k : secao.getKeys(false)) mapa.put(k.toLowerCase(Locale.ROOT).trim(), secao.getInt(k));
        }
        return mapa;
    }

    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        Player jogador = evento.getPlayer();
        // o /g já contou como comando de mensagem: a mensagem que ele solta no chat não conta de novo
        if (jogador.hasPermission(IGNORAR) || plugin.getCanais().global(jogador.getUniqueId())) return;
        String recusa = mensagem(jogador.getUniqueId(), evento.getMessage());
        if (recusa != null) {
            evento.setCancelled(true);
            jogador.sendMessage(recusa);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoComando(PlayerCommandPreprocessEvent evento) {
        Player jogador = evento.getPlayer();
        if (jogador.hasPermission(IGNORAR)) return;
        String[] partes = evento.getMessage().substring(1).trim().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) return;
        String nome = nomeDoComando(partes[0]);
        String sub = partes.length > 1 ? nome + " " + partes[1].toLowerCase(Locale.ROOT) : null;

        // comandos de mensagem: duas palavras antes de uma ("cla c" antes de "cla")
        String chave = sub != null && deMensagem.containsKey(sub) ? sub : deMensagem.containsKey(nome) ? nome : null;
        if (chave != null) {
            int pular = (chave.equals(sub) ? 2 : 1) + deMensagem.get(chave);
            if (partes.length <= pular) return; // sem texto: o próprio comando mostra o uso
            String recusa = mensagem(jogador.getUniqueId(), String.join(" ", Arrays.copyOfRange(partes, pular, partes.length)));
            if (recusa != null) {
                evento.setCancelled(true);
                jogador.sendMessage(recusa);
            }
            return;
        }

        chave = sub != null && comIntervalo.containsKey(sub) ? sub : comIntervalo.containsKey(nome) ? nome : null;
        if (chave == null) return;
        long agora = System.currentTimeMillis();
        long intervalo = comIntervalo.get(chave) * 1000L;
        Estado e = estado(jogador.getUniqueId());
        synchronized (e) {
            long falta = e.comandos.getOrDefault(chave, 0L) + intervalo - agora;
            if (falta > 0) {
                evento.setCancelled(true);
                jogador.sendMessage(texto("comando", "&cEspere &f%s s &cpra usar esse comando de novo.", falta));
                return;
            }
            e.comandos.put(chave, agora);
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        // o silêncio e o intervalo dos comandos continuam valendo se ele voltar logo: só some quem já passou de tudo
        long agora = System.currentTimeMillis();
        estados.entrySet().removeIf(en -> {
            Estado e = en.getValue();
            synchronized (e) {
                return e.silenciadoAte < agora && agora - e.ultima > 60_000
                        && e.comandos.values().stream().allMatch(t -> agora - t > 600_000);
            }
        });
    }

    // ---------------------------------------------------------------------

    /** Confere e registra uma mensagem. Devolve o aviso (se for barrada) ou null. */
    private String mensagem(UUID jogador, String texto) {
        long agora = System.currentTimeMillis();
        Estado e = estado(jogador);
        synchronized (e) {
            if (e.silenciadoAte > agora) {
                return texto("silenciado", "&cMuitas mensagens seguidas: você pode falar de novo em &f%s s&c.", e.silenciadoAte - agora);
            }
            long intervalo = (long) (plugin.getConfig().getDouble("chat.intervalo-segundos", 2) * 1000);
            if (agora - e.ultima < intervalo) {
                return texto("intervalo", "&cCalma! Espere &f%s s &cpra mandar outra mensagem.", intervalo - (agora - e.ultima));
            }
            String normal = normalizar(texto);
            long repetida = (long) (plugin.getConfig().getDouble("chat.repetida-segundos", 30) * 1000);
            if (!normal.isEmpty() && normal.equals(e.ultimoTexto) && agora - e.ultimoTextoEm < repetida) {
                return texto("repetida", "&cVocê acabou de mandar essa mensagem. Espere &f%s s &cpra repetir.",
                        repetida - (agora - e.ultimoTextoEm));
            }
            long janela = (long) (plugin.getConfig().getDouble("chat.rajada.segundos", 15) * 1000);
            int maximo = plugin.getConfig().getInt("chat.rajada.mensagens", 5);
            while (!e.recentes.isEmpty() && agora - e.recentes.peekFirst() > janela) e.recentes.pollFirst();
            if (maximo > 0 && e.recentes.size() >= maximo) {
                e.silenciadoAte = agora + (long) (plugin.getConfig().getDouble("chat.rajada.silencio-segundos", 30) * 1000);
                e.recentes.clear();
                return texto("silenciado", "&cMuitas mensagens seguidas: você pode falar de novo em &f%s s&c.", e.silenciadoAte - agora);
            }
            e.recentes.addLast(agora);
            e.ultima = agora;
            if (!normal.isEmpty()) {
                e.ultimoTexto = normal;
                e.ultimoTextoEm = agora;
            }
            return null;
        }
    }

    private Estado estado(UUID jogador) {
        return estados.computeIfAbsent(jogador, k -> new Estado());
    }

    /** O nome do comando (os aliases viram o nome; "plugin:cmd" vira "cmd"). */
    private static String nomeDoComando(String digitado) {
        String rotulo = digitado.toLowerCase(Locale.ROOT);
        int dois = rotulo.indexOf(':');
        if (dois >= 0) rotulo = rotulo.substring(dois + 1);
        Command comando = Bukkit.getCommandMap().getCommand(rotulo);
        return comando == null ? rotulo : comando.getName().toLowerCase(Locale.ROOT);
    }

    /** Sem maiúsculas, acentos, espaços e pontuação: "Olá!!" e "ola" são a mesma mensagem. */
    static String normalizar(String texto) {
        String sem = Normalizer.normalize(ChatColor.stripColor(texto), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sem.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String texto(String chave, String padrao, long faltaMs) {
        String modelo = plugin.getConfig().getString("mensagens." + chave, padrao);
        return ChatColor.translateAlternateColorCodes('&', String.format(modelo, Math.max(1, (faltaMs + 999) / 1000)));
    }
}
