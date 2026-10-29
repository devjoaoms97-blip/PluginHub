package com.example.recursos;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * {@code /luz}: liga e desliga a visão noturna, pra todos. Ligada, ela fica até desligar: volta depois
 * de morrer, de beber leite e de sair do servidor (a escolha fica marcada no jogador). Sem partículas
 * e sem o ícone piscando.
 */
public class Luz implements CommandExecutor, Listener {

    private final RecursosPlugin plugin;
    private final NamespacedKey chave;

    public Luz(RecursosPlugin plugin) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "luz");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSó jogadores usam o /luz.");
            return true;
        }
        boolean ligar = !ligada(p);
        if (ligar) {
            p.getPersistentDataContainer().set(chave, PersistentDataType.BYTE, (byte) 1);
            aplicar(p);
            p.sendMessage("§e☀ Visão noturna ligada. §7Use §f/luz §7de novo pra desligar.");
        } else {
            p.getPersistentDataContainer().remove(chave);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.sendMessage("§7☾ Visão noturna desligada.");
        }
        return true;
    }

    private boolean ligada(Player p) {
        return p.getPersistentDataContainer().has(chave, PersistentDataType.BYTE);
    }

    private void aplicar(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        if (ligada(evento.getPlayer())) aplicar(evento.getPlayer());
    }

    /** Depois de renascer de vez (no PlayerRespawnEvent, o jogo ainda limpa os efeitos depois). */
    @EventHandler
    public void aoRenascer(PlayerPostRespawnEvent evento) {
        Player p = evento.getPlayer();
        if (ligada(p)) plugin.getServer().getScheduler().runTaskLater(plugin, () -> aplicar(p), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoBeberLeite(PlayerItemConsumeEvent evento) {
        Player p = evento.getPlayer();
        if (evento.getItem().getType() == Material.MILK_BUCKET && ligada(p)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> aplicar(p));
        }
    }
}
