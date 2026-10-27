package com.example.boss.listener;

import com.example.boss.BossPlugin;
import com.example.boss.util.BossTagUtil;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Boss órfão: um boss que ficou salvo no mundo sem o plugin saber dele (o servidor caiu no meio da
 * luta). Ele não tem barra, não entra no ranking e mata quem chega na arena. Quando o chunk carrega,
 * qualquer entidade com a marca de boss que não seja o boss ativo some.
 */
public class BossOrfaoListener implements Listener {

    private final BossPlugin plugin;

    public BossOrfaoListener(BossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void aoCarregar(EntitiesLoadEvent evento) {
        for (Entity e : evento.getEntities()) {
            if (!e.getPersistentDataContainer().has(BossTagUtil.chave(), PersistentDataType.BYTE)) continue;
            if (e.equals(plugin.getBossManager().getBossAtivo())) continue;
            plugin.getLogger().info("Boss órfão removido (" + e.getType() + " em " + e.getWorld().getName() + " "
                    + e.getLocation().getBlockX() + " " + e.getLocation().getBlockY() + " " + e.getLocation().getBlockZ() + ").");
            e.remove();
        }
    }
}
