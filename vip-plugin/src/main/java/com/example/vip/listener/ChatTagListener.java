package com.example.vip.listener;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipTier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Prefixa o chat e o nome na tab list com a tag colorida do VIP ativo do jogador
 * ({@code nome-exibicao} no config.yml). As permissões/grupos em si continuam sendo
 * geridas pelo LuckPerms — isso aqui é só a parte visual, que o LuckPerms sozinho não
 * aplica sem um plugin de chat dedicado.
 */
public class ChatTagListener implements Listener {

    private final VipPlugin plugin;

    public ChatTagListener(VipPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        VipTier tier = plugin.getVipManager().getTierAtivo(player.getUniqueId());
        String tag = tier != null ? plugin.getNomeExibicao(tier) + " " : "";
        event.setFormat(tag + "§f%1$s§7: §f%2$s");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        atualizarTabList(event.getPlayer());
    }

    public void atualizarTabList(Player player) {
        VipTier tier = plugin.getVipManager().getTierAtivo(player.getUniqueId());
        String tag = tier != null ? plugin.getNomeExibicao(tier) + " " : "";
        player.setPlayerListName(tag + "§f" + player.getName());
    }
}
