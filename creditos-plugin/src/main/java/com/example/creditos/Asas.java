package com.example.creditos;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asas nas costas (cosmético; modelos de terceiros no resource pack, item {@code servidor:asa_<id>}).
 * Cada jogador com asa em uso ganha um item display montado nele (passageiro): o cliente desenha grudado
 * nas costas, sem o atraso de teleportar a cada tick; daqui só sai o giro, pro lado do corpo. O teleporte
 * continua funcionando (/tp e /spawn do Essentials e /tp do jogo: tools/bots/teste-asas.js) e, se a asa
 * cair do jogador, ela volta no tick seguinte. {@code asas.montada: false} volta pro jeito antigo
 * (teleportar o display a cada tick). Some quando ele fica invisível, espectador, planando de elytra ou
 * morto, e volta sozinha depois.
 */
public class Asas implements Listener {

    /** Onde fica um passageiro do jogador (em pé), em blocos acima dos pés. */
    private static final double ALTURA_DO_PASSAGEIRO = 1.8;

    private final CreditosPlugin plugin;
    /** jogador → o display da asa dele. */
    private final Map<UUID, UUID> displays = new HashMap<>();
    /** jogador → qual asa o display está mostrando. */
    private final Map<UUID, String> mostrando = new HashMap<>();

    public Asas(CreditosPlugin plugin) {
        this.plugin = plugin;
    }

    /** A cada tick: cria, move, troca ou tira a asa de cada jogador. */
    public void atualizar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String asa = plugin.getDados().emUso(p.getUniqueId(), Dados.Categoria.ASA);
            boolean esconder = asa == null || plugin.getConfig().getConfigurationSection("cosmeticos.asas." + asa) == null
                    || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible() || p.isGliding();
            ItemDisplay display = display(p.getUniqueId());
            if (esconder) {
                tirar(p.getUniqueId());
                continue;
            }
            Location onde = lugar(p);
            if (display == null || !display.getWorld().equals(onde.getWorld())) {
                tirar(p.getUniqueId());
                display = criar(p, asa, onde);
            } else if (!asa.equals(mostrando.get(p.getUniqueId()))) {
                display.setItemStack(item(asa));
                mostrando.put(p.getUniqueId(), asa);
            }
            if (!montada()) {
                display.teleport(onde);
            } else {
                // montada no jogador: o cliente desenha grudada, sem atraso; só o giro é daqui
                if (!p.getPassengers().contains(display)) {
                    if (display.isInsideVehicle()) display.leaveVehicle();
                    display.teleport(onde);
                    p.addPassenger(display);
                }
                display.setRotation(onde.getYaw(), 0);
            }
        }
    }

    private boolean montada() {
        return plugin.getConfig().getBoolean("asas.montada", true);
    }

    /** Onde o display fica: no jogador, virado pro lado do corpo (mais o giro do config). */
    private Location lugar(Player p) {
        Location l = p.getLocation();
        l.setYaw(p.getBodyYaw() + (float) plugin.getConfig().getDouble("asas.giro", 0));
        l.setPitch(0);
        return l;
    }

    private ItemDisplay criar(Player p, String asa, Location onde) {
        float escala = (float) plugin.getConfig().getDouble("asas.escala", 1.1);
        // montada, o display fica no ponto de passageiro do jogador (em cima da cabeça): desce até a altura pedida
        float altura = (float) (plugin.getConfig().getDouble("asas.altura", 1.15) - (montada() ? ALTURA_DO_PASSAGEIRO : 0));
        float recuo = (float) plugin.getConfig().getDouble("asas.recuo", -0.75);
        ItemDisplay d = onde.getWorld().spawn(onde, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(item(asa));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(0, altura, recuo), new AxisAngle4f(),
                    new Vector3f(escala, escala, escala), new AxisAngle4f()));
            // o movimento de um tick pro outro sai suave
            e.setTeleportDuration(1);
        });
        displays.put(p.getUniqueId(), d.getUniqueId());
        mostrando.put(p.getUniqueId(), asa);
        return d;
    }

    static ItemStack item(String asa) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey("servidor", "asa_" + asa));
        return item;
    }

    private ItemDisplay display(UUID jogador) {
        UUID id = displays.get(jogador);
        return id != null && Bukkit.getEntity(id) instanceof ItemDisplay d && d.isValid() ? d : null;
    }

    private void tirar(UUID jogador) {
        UUID id = displays.remove(jogador);
        mostrando.remove(jogador);
        if (id != null && Bukkit.getEntity(id) instanceof Entity e) e.remove();
    }

    /** Ao desligar: tira todas. */
    public void tirarTodas() {
        for (UUID jogador : Map.copyOf(displays).keySet()) tirar(jogador);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        tirar(evento.getPlayer().getUniqueId());
    }
}
