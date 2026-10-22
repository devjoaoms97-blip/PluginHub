package com.example.vip.listener;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipTier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Prefixa o chat e o nome na tab list com a tag colorida do VIP ativo do jogador
 * ({@code nome-exibicao} no config.yml). As permissões/grupos em si continuam sendo
 * geridas pelo LuckPerms — isso aqui é só a parte visual, que o LuckPerms sozinho não
 * aplica sem um plugin de chat dedicado.
 *
 * Na tab, se o ClasPlugin estiver instalado, a tag do clã vai antes da do VIP: quem monta o
 * nome é sempre este listener, e o clã pede pra refazer (via {@link VipPlugin#refrescarTagJogador})
 * quando o jogador entra ou sai de um clã.
 */
public class ChatTagListener implements Listener {

    private final VipPlugin plugin;
    private boolean avisouFalhaClas;

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
        player.setPlayerListName(prefixoCla(player.getUniqueId()) + tag + "§f" + player.getName());
    }

    /** Tag do clã pra tab (ClasPlugin#prefixoTab, por reflexão), ou "" sem clã ou sem o ClasPlugin. */
    private String prefixoCla(UUID jogador) {
        Plugin clas = plugin.getServer().getPluginManager().getPlugin("ClasPlugin");
        if (clas == null || !clas.isEnabled()) return "";
        try {
            Object prefixo = clas.getClass().getMethod("prefixoTab", UUID.class).invoke(clas, jogador);
            return prefixo instanceof String texto ? texto : "";
        } catch (ReflectiveOperationException e) {
            if (!avisouFalhaClas) {
                plugin.getLogger().warning("ClasPlugin encontrado, mas não foi possível ler a tag do clã pra tab (versão antiga?): " + e.getMessage());
                avisouFalhaClas = true;
            }
            return "";
        }
    }
}
