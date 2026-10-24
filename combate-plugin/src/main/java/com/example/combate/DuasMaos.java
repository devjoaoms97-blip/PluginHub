package com.example.combate;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Golpe com a segunda mão: o botão direito num inimigo bate com o que está na segunda mão, uma
 * arma curta (espada ou machado, no config) ou a mão vazia (soco). Cada mão pode ter uma arma
 * diferente. O golpe é um ataque de verdade (encantamentos, crítico, repulsão, skills,
 * proteções), feito trocando as mãos só durante o golpe. Com {@code separar-maos}, cada mão tem o
 * próprio tempo entre golpes, então alternar as duas bate mais vezes por segundo.
 * <p>
 * Mirando num inimigo, o botão direito bate; mirando em outro lugar, a espada da mão principal
 * bloqueia. Por isso o golpe da segunda mão desfaz o bloqueio que o mesmo clique começaria.
 * <p>
 * O cliente tenta a mão principal antes: interage com a entidade e, se nada acontecer, usa o item
 * dela. Como a espada da principal bloqueia (o "usar" dela sempre faz algo), o clique da segunda mão
 * nunca chega ao servidor. Então o clique que chega pela mão principal também vale — só contra
 * jogadores e monstros, pra não atrapalhar a troca com aldeões, montar e afins. Um clique pode
 * chegar como mais de um evento (principal e segunda, "interact" e "interact at"): só um golpe por tick.
 */
public class DuasMaos implements Listener {

    private final CombatePlugin plugin;
    private final NamespacedKey chaveAjuste;
    /** Quem está no meio de um golpe da segunda mão (pra Invulnerabilidade saber de que mão veio). */
    private final Set<UUID> golpeando = new HashSet<>();
    private final Map<UUID, Integer> ultimoGolpe = new HashMap<>();

    public DuasMaos(CombatePlugin plugin) {
        this.plugin = plugin;
        this.chaveAjuste = new NamespacedKey(plugin, "golpe-segunda-mao");
    }

    public boolean golpeandoComASegunda(Player jogador) {
        return golpeando.contains(jogador.getUniqueId());
    }

    /** Arma curta da lista, ou a mão vazia (soco, se ligado). */
    private boolean podeGolpear(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return plugin.getConfig().getBoolean("duas-maos.soco", true);
        String nome = item.getType().name();
        List<String> sufixos = plugin.getConfig().getStringList("duas-maos.armas");
        return sufixos.stream().anyMatch(s -> nome.endsWith(s.toUpperCase(Locale.ROOT)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoClicarComDireito(PlayerInteractEntityEvent evento) {
        if (!(evento.getRightClicked() instanceof LivingEntity alvo) || alvo instanceof ArmorStand) return;
        Player jogador = evento.getPlayer();
        PlayerInventory inv = jogador.getInventory();
        if (evento.getHand() == EquipmentSlot.HAND) {
            // o clique da segunda mão não vem quando a espada da principal pode bloquear
            boolean espadaNaPrincipal = inv.getItemInMainHand().getType().name().endsWith("_SWORD");
            if (!espadaNaPrincipal || !(alvo instanceof Player || alvo instanceof Enemy)) return;
        } else if (evento.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (!podeGolpear(inv.getItemInOffHand())) return;
        evento.setCancelled(true);
        int tick = Bukkit.getCurrentTick();
        Integer anterior = ultimoGolpe.put(jogador.getUniqueId(), tick);
        if (anterior != null && anterior == tick) return; // o mesmo clique, em outro evento
        golpearComASegundaMao(jogador, alvo);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        ultimoGolpe.remove(evento.getPlayer().getUniqueId());
    }

    private void golpearComASegundaMao(Player jogador, LivingEntity alvo) {
        PlayerInventory inv = jogador.getInventory();
        ItemStack principal = inv.getItemInMainHand();
        ItemStack segunda = inv.getItemInOffHand();

        // O atributo de dano só é recalculado no tick seguinte à troca de item, então o golpe usaria
        // o dano da arma da mão principal. O ajuste compensa a diferença durante o golpe.
        double ajuste = danoDaArma(segunda) - danoDaArma(principal);
        AttributeInstance dano = jogador.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dano != null && ajuste != 0) {
            dano.addTransientModifier(new AttributeModifier(chaveAjuste, ajuste, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        inv.setItemInMainHand(segunda);
        inv.setItemInOffHand(principal);
        golpeando.add(jogador.getUniqueId());
        try {
            jogador.attack(alvo);
        } finally {
            golpeando.remove(jogador.getUniqueId());
            // o desgaste do golpe ficou no item que estava na mão principal (a arma da segunda mão)
            ItemStack usada = inv.getItemInMainHand();
            inv.setItemInMainHand(inv.getItemInOffHand());
            inv.setItemInOffHand(usada);
            if (dano != null) dano.removeModifier(chaveAjuste);
        }
        jogador.swingOffHand();
        // o mesmo clique, depois do golpe, ainda chega como "usar a espada" (bloquear): desfaz
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (jogador.isHandRaised()) jogador.clearActiveItem();
        });
    }

    /** Dano que a arma soma ao ataque (modificadores de dano de ataque da mão principal). */
    private double danoDaArma(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemAttributeModifiers mods = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods == null) return 0;
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            AttributeModifier m = e.modifier();
            if (e.attribute() == Attribute.ATTACK_DAMAGE && m.getOperation() == AttributeModifier.Operation.ADD_NUMBER
                    && m.getSlotGroup().test(EquipmentSlot.HAND)) {
                total += m.getAmount();
            }
        }
        return total;
    }
}
