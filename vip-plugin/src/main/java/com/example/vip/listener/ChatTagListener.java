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
 * Prefixa o chat e o nome na tab list com a tag colorida do VIP ativo do jogador: o prefixo do
 * grupo no LuckPerms ou, sem ele, o {@code nome-exibicao} do config.yml ({@link VipPlugin#getTag}).
 * As permissões/grupos em si continuam sendo geridas pelo LuckPerms — isso aqui é só a parte
 * visual, que o LuckPerms sozinho não aplica sem um plugin de chat dedicado.
 *
 * A tag de apoiador e a cor do nome vêm do CreditosPlugin (a loja de créditos), depois da do VIP.
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
        String tag = (tier != null ? plugin.getTag(tier) + " " : "") + apoiador(player.getUniqueId());
        event.setFormat(tag.replace("%", "%%") + corDoNome(player.getUniqueId()) + "%1$s§7: §f%2$s");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        atualizarTabList(event.getPlayer());
        plugin.getCodeManager().entregarAvisos(event.getPlayer());
    }

    public void atualizarTabList(Player player) {
        VipTier tier = plugin.getVipManager().getTierAtivo(player.getUniqueId());
        String tag = (tier != null ? plugin.getTag(tier) + " " : "") + apoiador(player.getUniqueId());
        player.setPlayerListName(prefixo("RankupPlugin", player.getUniqueId()) + prefixo("ClasPlugin", player.getUniqueId())
                + tag + corDoNome(player.getUniqueId()) + player.getName());
    }

    /** A tag de apoiador do CreditosPlugin, com espaço no fim, ou "" (não é apoiador ou sem o plugin). */
    private String apoiador(UUID jogador) {
        String tag = doCreditos("tagApoiador", jogador);
        return tag == null ? "" : tag + " ";
    }

    /** A cor do nome comprada no CreditosPlugin ("§b"), ou branco. */
    private String corDoNome(UUID jogador) {
        String cor = doCreditos("corDoNome", jogador);
        return cor == null ? "§f" : cor;
    }

    private String doCreditos(String metodo, UUID jogador) {
        Plugin creditos = plugin.getServer().getPluginManager().getPlugin("CreditosPlugin");
        if (creditos == null || !creditos.isEnabled()) return null;
        try {
            return creditos.getClass().getMethod(metodo, UUID.class).invoke(creditos, jogador) instanceof String texto ? texto : null;
        } catch (ReflectiveOperationException e) {
            if (avisouFalha.add("CreditosPlugin#" + metodo)) {
                plugin.getLogger().warning("CreditosPlugin encontrado, mas não deu pra ler " + metodo + " (versão antiga?): " + e.getMessage());
            }
            return null;
        }
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
