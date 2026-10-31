package com.example.ajuda;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Pedido de {@code /tpa} ou {@code /tpahere} (EssentialsX): quem recebe ganha os botões Aceitar e
 * Recusar, que rodam {@code /tpaccept <quem pediu>} e {@code /tpdeny <quem pediu>}. O Essentials
 * continua cuidando de tudo (se o pedido existe, se expirou, quem pode aceitar): o clique só digita o
 * comando. Ouve o TPARequestEvent por reflexão, sem dependência de compilação.
 */
class TpaClique implements Listener {

    private static final String EVENTO = "net.ess3.api.events.TPARequestEvent";

    private final AjudaPlugin plugin;

    TpaClique(AjudaPlugin plugin) {
        this.plugin = plugin;
    }

    void registrar() {
        if (!plugin.getConfig().getBoolean("tpa-clique.ativado", true)) return;
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            plugin.getLogger().info("Sem o EssentialsX: o /tpa com clique fica desligado.");
            return;
        }
        try {
            Class<? extends Event> tipo = Class.forName(EVENTO, true, essentials.getClass().getClassLoader()).asSubclass(Event.class);
            // MONITOR e ignorando os cancelados: só pedidos que o Essentials (e os outros plugins) deixaram passar
            Bukkit.getPluginManager().registerEvent(tipo, this, EventPriority.MONITOR, (ouvinte, evento) -> {
                if (tipo.isInstance(evento)) aoPedir(evento);
            }, plugin, true);
        } catch (ClassNotFoundException | ClassCastException e) {
            plugin.getLogger().warning("Não achei o " + EVENTO + " no Essentials (versão antiga?): o /tpa com clique fica desligado.");
        }
    }

    private void aoPedir(Event evento) {
        Player quemPediu, alvo;
        boolean praCa;
        try {
            Object fonte = evento.getClass().getMethod("getRequester").invoke(evento);
            quemPediu = (Player) fonte.getClass().getMethod("getPlayer").invoke(fonte);
            Object usuario = evento.getClass().getMethod("getTarget").invoke(evento);
            alvo = (Player) usuario.getClass().getMethod("getBase").invoke(usuario);
            praCa = (boolean) evento.getClass().getMethod("isTeleportHere").invoke(evento);
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Não foi possível ler o pedido de /tpa: " + e.getMessage());
            return;
        }
        if (quemPediu == null || alvo == null) return;
        String nome = quemPediu.getName();
        // um tick depois: as mensagens do Essentials ("fulano pediu pra...") chegam antes dos botões
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!alvo.isOnline()) return;
            String acao = praCa ? "ir até &f" + nome : "trazer &f" + nome + " &7até você";
            alvo.sendMessage(Component.text("  ")
                    .append(AjudaPlugin.botao("&a&l[✔ ACEITAR]", "/tpaccept " + nome, "&7Clique pra aceitar e " + acao, true))
                    .append(Component.text("   "))
                    .append(AjudaPlugin.botao("&c&l[✖ RECUSAR]", "/tpdeny " + nome, "&7Clique pra recusar o pedido de &f" + nome, true)));
        });
    }
}
