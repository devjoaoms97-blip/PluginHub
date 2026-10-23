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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Prefixa o chat e o nome na tab list com a tag colorida do VIP ativo do jogador
 * ({@code nome-exibicao} no config.yml). As permissões/grupos em si continuam sendo
 * geridas pelo LuckPerms — isso aqui é só a parte visual, que o LuckPerms sozinho não
 * aplica sem um plugin de chat dedicado.
 *
 * Na tab, o rank (RankupPlugin) e a tag do clã (ClasPlugin) vão antes da do VIP: quem monta o
 * nome é sempre este listener, e o rankup e o clã pedem pra refazer (via
 * {@link VipPlugin#refrescarTagJogador}) quando o rank ou o clã do jogador muda.
 */
public class ChatTagListener implements Listener {

    private final VipPlugin plugin;
    private final Set<String> avisouFalha = new HashSet<>();

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
        player.setPlayerListName(prefixo("RankupPlugin", player.getUniqueId()) + prefixo("ClasPlugin", player.getUniqueId())
                + tag + "§f" + player.getName());
    }

    /**
     * Prefixo pra tab de outro plugin ({@code prefixoTab(UUID)}, por reflexão): o rank do
     * RankupPlugin e a tag do ClasPlugin. "" sem o plugin ou sem prefixo.
     */
    private String prefixo(String nomePlugin, UUID jogador) {
        Plugin outro = plugin.getServer().getPluginManager().getPlugin(nomePlugin);
        if (outro == null || !outro.isEnabled()) return "";
        try {
            Object prefixo = outro.getClass().getMethod("prefixoTab", UUID.class).invoke(outro, jogador);
            return prefixo instanceof String texto ? texto : "";
        } catch (ReflectiveOperationException e) {
            if (avisouFalha.add(nomePlugin)) {
                plugin.getLogger().warning(nomePlugin + " encontrado, mas não foi possível ler o prefixo pra tab (versão antiga?): " + e.getMessage());
            }
            return "";
        }
    }
}
