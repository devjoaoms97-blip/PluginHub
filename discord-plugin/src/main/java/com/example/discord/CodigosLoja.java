package com.example.discord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Os códigos da loja do site: quem compra digita o nick, o site pede um código (POST /loja/codigo) e
 * o jogador recebe no chat do jogo; digitando o código no site (POST /loja/confirmar), o site sabe que
 * é ele mesmo e cria o pedido pra conta certa. Assim ninguém compra pro nick errado por engano.
 * <p>
 * O jogador precisa estar online. Cada código vale {@code loja.codigo-minutos} (10), aceita 5
 * tentativas erradas e some quando é usado. O mesmo jogador só pede outro depois de 60 s (o site
 * também limita por IP), pra ninguém encher o chat de alguém de códigos. Chamar na thread do servidor.
 */
public class CodigosLoja {

    private static final String LETRAS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // sem 0/O, 1/I/L
    private static final SecureRandom SORTEIO = new SecureRandom();
    private static final long ESPERA_MS = 60_000;
    private static final int TENTATIVAS = 5;

    public record Resultado(boolean ok, String erro, UUID uuid, String nick) {
        static Resultado falha(String erro) {
            return new Resultado(false, erro, null, null);
        }
    }

    private static final class Pendente {
        final String codigo;
        final String nick;
        final long expira;
        int erros;

        Pendente(String codigo, String nick, long expira) {
            this.codigo = codigo;
            this.nick = nick;
            this.expira = expira;
        }
    }

    private final DiscordPlugin plugin;
    private final Map<UUID, Pendente> porJogador = new HashMap<>();
    private final Map<UUID, Long> ultimoPedido = new HashMap<>();

    public CodigosLoja(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    private long minutos() {
        return Math.max(1, plugin.getConfig().getLong("loja.codigo-minutos", 10));
    }

    /** Manda um código novo pro jogador (online), que vale pro site confirmar. */
    public Resultado pedir(String nick) {
        if (nick == null || !nick.matches("[A-Za-z0-9_]{2,16}")) return Resultado.falha("Nick inválido.");
        Player jogador = Bukkit.getPlayerExact(nick);
        if (jogador == null) return Resultado.falha("O jogador " + nick + " precisa estar online no servidor pra receber o código.");
        long agora = System.currentTimeMillis();
        Long antes = ultimoPedido.get(jogador.getUniqueId());
        if (antes != null && agora - antes < ESPERA_MS) {
            return Resultado.falha("Já mandamos um código agora há pouco. Olhe o chat do jogo ou espere " + ((ESPERA_MS - (agora - antes)) / 1000 + 1) + " s.");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(LETRAS.charAt(SORTEIO.nextInt(LETRAS.length())));
        String codigo = sb.toString();
        porJogador.put(jogador.getUniqueId(), new Pendente(codigo, jogador.getName(), agora + minutos() * 60_000));
        ultimoPedido.put(jogador.getUniqueId(), agora);

        jogador.sendMessage(Component.text("🛒 Loja do site", NamedTextColor.GOLD, TextDecoration.BOLD));
        jogador.sendMessage(Component.text("Seu código: ", NamedTextColor.GRAY)
                .append(Component.text(codigo, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" (clique pra copiar)", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.copyToClipboard(codigo))
                .hoverEvent(HoverEvent.showText(Component.text("Copiar o código"))));
        jogador.sendMessage(Component.text("Digite no site pra continuar a compra. Vale por " + minutos()
                + " min. Não foi você? É só ignorar: sem o código, ninguém compra na sua conta.", NamedTextColor.GRAY));
        return new Resultado(true, null, null, jogador.getName());
    }

    /** Confere o código do nick. Certo: devolve o UUID e apaga o código. */
    public Resultado confirmar(String nick, String codigo) {
        if (nick == null || codigo == null) return Resultado.falha("Faltou o nick ou o código.");
        var alvo = Bukkit.getOfflinePlayerIfCached(nick);
        Pendente p = alvo == null ? null : porJogador.get(alvo.getUniqueId());
        if (p == null || p.expira < System.currentTimeMillis()) {
            if (alvo != null) porJogador.remove(alvo.getUniqueId());
            return Resultado.falha("Código vencido ou não pedido. Peça outro.");
        }
        String digitado = codigo.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (!java.security.MessageDigest.isEqual(digitado.getBytes(), p.codigo.getBytes())) {
            if (++p.erros >= TENTATIVAS) {
                porJogador.remove(alvo.getUniqueId());
                return Resultado.falha("Código errado de novo. Peça um código novo.");
            }
            return Resultado.falha("Código errado. Confira no chat do jogo (" + (TENTATIVAS - p.erros) + " tentativa(s) restante(s)).");
        }
        porJogador.remove(alvo.getUniqueId());
        return new Resultado(true, null, alvo.getUniqueId(), p.nick);
    }

    /** Tira os vencidos (de minuto em minuto). */
    public void limpar() {
        long agora = System.currentTimeMillis();
        porJogador.values().removeIf(p -> p.expira < agora);
        ultimoPedido.values().removeIf(t -> agora - t > ESPERA_MS);
    }
}
