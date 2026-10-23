package com.example.combate;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * Sem recarga de ataque, como antes da 1.9: um modificador enorme na velocidade de ataque deixa a
 * barra sempre cheia, então todo golpe sai com o dano inteiro, por mais rápido que se clique. O
 * limite continua sendo o tempo de invulnerabilidade depois de cada golpe (meio segundo), como
 * na 1.8. O modificador é transitório: não fica salvo no jogador, então tirar o plugin desfaz.
 */
public class SemRecarga implements Listener {

    private final CombatePlugin plugin;
    private final NamespacedKey chave;

    public SemRecarga(CombatePlugin plugin) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "sem-recarga");
    }

    public void aplicar(Player jogador) {
        AttributeInstance velocidade = jogador.getAttribute(Attribute.ATTACK_SPEED);
        if (velocidade == null || velocidade.getModifier(chave) != null) return;
        velocidade.addTransientModifier(new AttributeModifier(chave, 1000, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    public void remover(Player jogador) {
        AttributeInstance velocidade = jogador.getAttribute(Attribute.ATTACK_SPEED);
        if (velocidade != null) velocidade.removeModifier(chave);
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        aplicar(evento.getPlayer());
    }

    @EventHandler
    public void aoRenascer(PlayerRespawnEvent evento) {
        // o jogador "novo" do respawn perde os modificadores transitórios
        plugin.getServer().getScheduler().runTask(plugin, () -> aplicar(evento.getPlayer()));
    }
}
