package com.example.ajuda;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Só os comandos do servidor pros jogadores. Sem isso, o Tab mostrava 322 comandos pra quem não é
 * staff: cada um repetido com o prefixo do plugin ("essentials:tpa"), os apelidos do Essentials
 * ("etpa", "tpyes"), o WorldEdit, o LuckPerms, os extras do AuthMe... Agora o Tab mostra só a lista
 * {@code comandos-permitidos.lista}, e o resto responde como comando desconhecido. Quem tem
 * {@code ajuda.comandos.todos} (a staff) vê e usa tudo.
 * <p>
 * Comando que um plugin roda pelo jogador (performCommand) não passa por aqui; o clique num botão do
 * chat passa, então o que os botões rodam precisa estar na lista.
 */
class ComandosPermitidos implements Listener {

    private static final String TODOS = "ajuda.comandos.todos";

    private final AjudaPlugin plugin;

    ComandosPermitidos(AjudaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean filtra(Player jogador) {
        return plugin.getConfig().getBoolean("comandos-permitidos.ativado", true) && !jogador.hasPermission(TODOS);
    }

    private Set<String> lista() {
        Set<String> set = new HashSet<>();
        for (String c : plugin.getConfig().getStringList("comandos-permitidos.lista")) set.add(c.toLowerCase(Locale.ROOT));
        return set;
    }

    @EventHandler
    public void aoMandarComandos(PlayerCommandSendEvent evento) {
        if (!filtra(evento.getPlayer())) return;
        Set<String> permitidos = lista();
        evento.getCommands().removeIf(c -> !permitidos.contains(c.toLowerCase(Locale.ROOT)));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoDigitar(PlayerCommandPreprocessEvent evento) {
        if (!filtra(evento.getPlayer())) return;
        String texto = evento.getMessage();
        int espaco = texto.indexOf(' ');
        String rotulo = (espaco < 0 ? texto.substring(1) : texto.substring(1, espaco)).toLowerCase(Locale.ROOT);
        if (lista().contains(rotulo)) return;
        evento.setCancelled(true);
        evento.getPlayer().sendMessage(AjudaPlugin.texto(plugin.getConfig().getString("comandos-permitidos.mensagem",
                "&cComando desconhecido. Veja os comandos do servidor em &f/comandos&c.")));
    }
}
