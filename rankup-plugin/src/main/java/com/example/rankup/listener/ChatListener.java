package com.example.rankup.listener;

import com.example.rankup.RankupPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Tag do rank no chat. HIGH: roda depois do VipPlugin (LOW, monta o formato) e do ClasPlugin
 * (NORMAL, põe a tag do clã), então o rank fica na frente: [Rank] [CLÃ] [VIP Ouro] Nome: mensagem.
 */
public class ChatListener implements Listener {

    private final RankupPlugin plugin;

    public ChatListener(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        if (!plugin.getConfig().getBoolean("chat.tag", true)) return;
        // % no nome do rank quebraria o String.format do chat
        String tag = plugin.tagDoRank(evento.getPlayer().getUniqueId()).replace("%", "%%");
        evento.setFormat(tag + " " + evento.getFormat());
    }
}
