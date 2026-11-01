package com.example.chat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat local e global. A mensagem normal é <b>local</b>: só quem está no mesmo mundo vê, com
 * {@code [L]} na frente. {@code /g <mensagem>} é <b>global</b>: todo mundo vê, com {@code [G]}.
 * <p>
 * O /g passa pelo chat de verdade ({@code Player#chat}), então as tags do VIP, do clã, do rank e do
 * Gladiador entram do mesmo jeito; o jogador fica marcado por um instante pra este ouvinte saber que
 * a mensagem é global (e o anti-flood não contar duas vezes: o /g já contou como comando de mensagem).
 * O prefixo do canal entra por último (HIGHEST, e o plugin carrega depois dos que põem tags), então
 * fica na frente de tudo.
 * <p>
 * Quem tem {@code chat.espiar} (staff) vê as mensagens locais dos outros mundos, com o nome do mundo.
 */
public class Canais implements Listener, CommandExecutor {

    private final ChatPlugin plugin;
    /** Quem acabou de usar o /g: a próxima mensagem dele é global. */
    private final Set<UUID> globais = ConcurrentHashMap.newKeySet();

    Canais(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    boolean ativado() {
        return plugin.getConfig().getBoolean("canais.ativado", true);
    }

    /** A mensagem que está saindo agora é um /g (o anti-flood já contou no comando)? */
    boolean global(UUID jogador) {
        return globais.contains(jogador);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores.");
            return true;
        }
        if (args.length == 0) {
            jogador.sendMessage("§cUso: §f/g <mensagem> §7- fala com o servidor todo (sem o /g, só quem está no seu mundo vê)");
            return true;
        }
        globais.add(jogador.getUniqueId());
        try {
            jogador.chat(String.join(" ", args));
        } finally {
            globais.remove(jogador.getUniqueId());
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        if (!ativado()) return;
        Player quem = evento.getPlayer();
        if (globais.contains(quem.getUniqueId())) {
            evento.setFormat(cor(plugin.getConfig().getString("canais.prefixo-global", "&6[G] ")).replace("%", "%%") + evento.getFormat());
            return;
        }
        evento.setFormat(cor(plugin.getConfig().getString("canais.prefixo-local", "&7[L] ")).replace("%", "%%") + evento.getFormat());
        List<Player> espioes = new ArrayList<>();
        evento.getRecipients().removeIf(p -> {
            if (p.getWorld().equals(quem.getWorld())) return false;
            if (p.hasPermission("chat.espiar")) espioes.add(p);
            return true;
        });
        if (!espioes.isEmpty()) {
            String linha = cor("&8[" + quem.getWorld().getName() + "] ")
                    + String.format(evento.getFormat(), quem.getDisplayName(), evento.getMessage());
            espioes.forEach(p -> p.sendMessage(linha));
        }
        if (evento.getRecipients().stream().noneMatch(p -> !p.equals(quem))
                && plugin.getConfig().getBoolean("canais.avisar-sozinho", true)) {
            quem.sendMessage(cor("&7Ninguém no seu mundo ouviu. Pra falar com todos: &f/g <mensagem>"));
        }
    }

    private static String cor(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }
}
