package com.example.boss.listener;

import com.example.boss.BossPlugin;
import com.example.boss.util.BossTagUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BossDeathListener implements Listener {

    private final BossPlugin plugin;

    public BossDeathListener(BossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) {
            return; // não é o nosso boss
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        // o elite rola o loot do chão mais vezes
        int rolagens = plugin.getBossManager().multiplicadorRecompensas() > 1 ? Math.max(1, plugin.getConfig().getInt("elite.rolagens-loot", 2)) : 1;
        for (int r = 0; r < rolagens; r++) {
            for (ItemStack item : plugin.getLootManager().sortearLootChao()) {
                event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), item);
            }
        }

        Player matador = event.getEntity().getKiller();
        StringBuilder discord = new StringBuilder(); // o mesmo anúncio, numa mensagem só pro Discord

        if (matador != null) {
            ItemStack premio = plugin.getLootManager().sortearItemCampeao();
            if (premio != null) {
                Map<Integer, ItemStack> sobras = matador.getInventory().addItem(premio);
                sobras.values().forEach(item -> matador.getWorld().dropItemNaturally(matador.getLocation(), item));
            }

            plugin.getChampionManager().coroarNovoCampeao(matador);

            String mensagem = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("mensagem-vitoria",
                            "&6★ &e%jogador% &6derrotou o chefe e agora é o novo campeão! ★")
                            .replace("%jogador%", matador.getName()));
            Bukkit.broadcastMessage(mensagem);
            discord.append(mensagem);
        } else {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&7O chefe foi derrotado."));
            discord.append("O chefe foi derrotado.");
        }

        anunciarRankingDano(discord);
        plugin.getDiscord().enviar("boss", discord.toString());
        plugin.getRecompensas().distribuir(matador);
        plugin.getMaldicao().quebrar();

        plugin.getBossManager().finalizar(true);
    }

    /** Top N de quem mais causou dano, com o % da vida máxima do boss que cada um tirou. */
    private void anunciarRankingDano(StringBuilder discord) {
        if (!plugin.getConfig().getBoolean("ranking-dano.ativado", true)) return;

        List<Map.Entry<UUID, Double>> ranking = plugin.getBossManager().getRankingDano();
        if (ranking.isEmpty()) return;

        int top = Math.max(1, plugin.getConfig().getInt("ranking-dano.top", 3));
        double vidaMaxima = plugin.getBossManager().getVidaMaxima();
        String[] cores = {"§6", "§f", "§c"};

        Bukkit.broadcastMessage("§e§l⚔ Ranking de dano no boss:");
        discord.append("\nRanking de dano:");
        for (int i = 0; i < Math.min(top, ranking.size()); i++) {
            Map.Entry<UUID, Double> entrada = ranking.get(i);
            OfflinePlayer jogador = Bukkit.getOfflinePlayer(entrada.getKey());
            String cor = i < cores.length ? cores[i] : "§7";
            String percentual = vidaMaxima > 0
                    ? String.format(Locale.ROOT, " §7(%.1f%%)", entrada.getValue() / vidaMaxima * 100)
                    : "";
            String linha = "  " + cor + (i + 1) + "º §f" + jogador.getName()
                    + " §7- " + String.format(Locale.ROOT, "%.0f", entrada.getValue()) + " de dano" + percentual;
            Bukkit.broadcastMessage(linha);
            discord.append("\n").append(linha);
        }
    }
}
