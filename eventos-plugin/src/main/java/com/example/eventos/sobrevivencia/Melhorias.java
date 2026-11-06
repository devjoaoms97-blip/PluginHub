package com.example.eventos.sobrevivencia;

import com.example.eventos.EventosPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * As cartas de melhoria da Sobrevivência (pedido de 2026-11-06): quando uma onda cai (menos a última), cada
 * um vivo vê 3 cartas sorteadas entre Velocidade, Dano, Vida, Regeneração e Defesa e escolhe uma em
 * {@code melhorias.segundos-pra-escolher} (3 s); sem escolher, ganha uma das 3 ao acaso. É individual, e a
 * mesma carta de novo sobe o nível dela. Vale até o fim do evento: a vida e a velocidade são modificadores
 * de atributo com a chave {@code eventosplugin:melhoria_*} (o {@code Evento.restaurar} e a morte tiram), a
 * regeneração é um efeito sem fim (sai com os efeitos) e o dano e a defesa são contas no dano.
 */
class Melhorias implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final int[] SLOTS = {2, 4, 6};

    enum Tipo {
        VELOCIDADE(Material.SUGAR, "§b", "Velocidade"),
        DANO(Material.IRON_SWORD, "§c", "Dano"),
        VIDA(Material.GOLDEN_APPLE, "§d", "Vida"),
        REGENERACAO(Material.GHAST_TEAR, "§a", "Regeneração"),
        DEFESA(Material.SHIELD, "§9", "Defesa");

        final Material icone;
        final String cor, nome;

        Tipo(Material icone, String cor, String nome) {
            this.icone = icone;
            this.cor = cor;
            this.nome = nome;
        }
    }

    /** A janela das 3 cartas de um jogador. */
    private static final class Escolha implements InventoryHolder {
        final List<Tipo> cartas;
        Inventory inventario;
        BukkitTask fim;

        Escolha(List<Tipo> cartas) {
            this.cartas = cartas;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final EventosPlugin plugin;
    private final Sobrevivencia evento;
    private final Map<UUID, Map<Tipo, Integer>> niveis = new HashMap<>();
    private final Map<UUID, Escolha> escolhendo = new HashMap<>();

    Melhorias(EventosPlugin plugin, Sobrevivencia evento) {
        this.plugin = plugin;
        this.evento = evento;
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = evento.cfg().getConfigurationSection("melhorias");
        return s != null ? s : evento.cfg().createSection("melhorias");
    }

    boolean ativado() {
        return cfg().getBoolean("ativado", true);
    }

    int nivel(UUID jogador, Tipo t) {
        return niveis.getOrDefault(jogador, Map.of()).getOrDefault(t, 0);
    }

    /** Começo da luta e fim do evento: ninguém tem carta nem janela aberta. */
    void zerar() {
        for (Map.Entry<UUID, Escolha> e : new ArrayList<>(escolhendo.entrySet())) {
            if (e.getValue().fim != null) e.getValue().fim.cancel();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() == e.getValue()) p.closeInventory();
        }
        escolhendo.clear();
        niveis.clear();
    }

    // ---------------------------------------------------------------- a escolha

    /** A onda caiu: abre as 3 cartas pra cada um vivo. */
    void oferecer(List<Player> vivos) {
        if (!ativado()) return;
        for (Player p : vivos) oferecer(p);
    }

    private void oferecer(Player p) {
        List<Tipo> todas = new ArrayList<>(List.of(Tipo.values()));
        // a Regeneração para no nível máximo: depois disso, não aparece mais
        if (nivel(p.getUniqueId(), Tipo.REGENERACAO) >= Math.max(1, cfg().getInt("regeneracao-nivel-maximo", 3))) {
            todas.remove(Tipo.REGENERACAO);
        }
        Collections.shuffle(todas, ThreadLocalRandom.current());
        Escolha e = new Escolha(new ArrayList<>(todas.subList(0, Math.min(3, todas.size()))));
        e.inventario = Bukkit.createInventory(e, 9, LEGADO.deserialize("§6§lEscolha uma melhoria"));
        for (int i = 0; i < e.cartas.size(); i++) e.inventario.setItem(SLOTS[i], carta(p.getUniqueId(), e.cartas.get(i)));

        Escolha antiga = escolhendo.put(p.getUniqueId(), e);
        if (antiga != null && antiga.fim != null) antiga.fim.cancel();
        p.openInventory(e.inventario);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);

        int segundos = Math.max(1, cfg().getInt("segundos-pra-escolher", 3));
        e.fim = new org.bukkit.scheduler.BukkitRunnable() {
            int falta = segundos;

            @Override
            public void run() {
                if (escolhendo.get(p.getUniqueId()) != e || !p.isOnline()) {
                    cancel();
                    return;
                }
                if (falta <= 0) {
                    cancel();
                    escolher(p, e, e.cartas.get(ThreadLocalRandom.current().nextInt(e.cartas.size())), true);
                    return;
                }
                p.sendActionBar(LEGADO.deserialize("§6Escolha uma melhoria: §f" + falta + " s"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
                falta--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private ItemStack carta(UUID jogador, Tipo t) {
        int novo = nivel(jogador, t) + 1;
        ItemStack item = new ItemStack(t.icone);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGADO.deserialize(t.cor + "§l" + t.nome + " " + romano(novo)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(LEGADO.deserialize("§7" + efeito(t, novo)).decoration(TextDecoration.ITALIC, false));
        if (novo > 1) lore.add(LEGADO.deserialize("§8(você tem o nível " + romano(novo - 1) + ")").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(LEGADO.deserialize("§eClique pra escolher").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** O que o nível dá, no total. */
    private String efeito(Tipo t, int nivel) {
        return switch (t) {
            case VELOCIDADE -> "+" + pct(cfg().getDouble("velocidade", 0.10) * nivel) + " de velocidade";
            case DANO -> "+" + pct(cfg().getDouble("dano", 0.15) * nivel) + " de dano (espada e arco)";
            case VIDA -> "+" + fmt(cfg().getDouble("vida", 4) * nivel / 2) + " coração(ões) de vida máxima";
            case REGENERACAO -> "Regeneração " + romano(nivel) + " até o fim do evento";
            case DEFESA -> "-" + pct(1 - Math.pow(1 - cfg().getDouble("defesa", 0.12), nivel)) + " de dano recebido";
        };
    }

    private void escolher(Player p, Escolha e, Tipo t, boolean sorteada) {
        if (escolhendo.get(p.getUniqueId()) != e) return;
        escolhendo.remove(p.getUniqueId());
        if (e.fim != null) e.fim.cancel();
        if (p.getOpenInventory().getTopInventory().getHolder() == e) p.closeInventory();
        if (evento.lutando(p.getUniqueId()) == null || !evento.lutando(p.getUniqueId()).isVivo()) return;

        int nivel = niveis.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Tipo.class)).merge(t, 1, Integer::sum);
        aplicar(p, t, nivel);
        p.sendMessage(LEGADO.deserialize((sorteada ? "§7O tempo acabou, e saiu: " : "§a✦ Melhoria: ") + t.cor + "§l" + t.nome + " "
                + romano(nivel) + " §7(" + efeito(t, nivel) + ")"));
        p.sendActionBar(LEGADO.deserialize(t.cor + "§l" + t.nome + " " + romano(nivel)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    private void aplicar(Player p, Tipo t, int nivel) {
        switch (t) {
            case VELOCIDADE -> modificador(p, Attribute.MOVEMENT_SPEED, "melhoria_velocidade",
                    cfg().getDouble("velocidade", 0.10) * nivel, AttributeModifier.Operation.ADD_SCALAR);
            case VIDA -> {
                double porNivel = cfg().getDouble("vida", 4);
                modificador(p, Attribute.MAX_HEALTH, "melhoria_vida", porNivel * nivel, AttributeModifier.Operation.ADD_NUMBER);
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) p.setHealth(Math.min(max.getValue(), p.getHealth() + porNivel));
            }
            case REGENERACAO -> {
                int maximo = Math.max(1, cfg().getInt("regeneracao-nivel-maximo", 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION,
                        Math.min(nivel, maximo) - 1, true, false, true));
            }
            case DANO, DEFESA -> {
                // contas no dano (aoBater e aoApanhar)
            }
        }
    }

    private void modificador(Player p, Attribute atributo, String chave, double valor, AttributeModifier.Operation op) {
        AttributeInstance a = p.getAttribute(atributo);
        if (a == null) return;
        NamespacedKey key = new NamespacedKey(plugin, chave);
        a.removeModifier(key);
        a.addModifier(new AttributeModifier(key, valor, op, EquipmentSlotGroup.ANY));
    }

    // ---------------------------------------------------------------- janela, dano

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getView().getTopInventory().getHolder() instanceof Escolha e)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player p) || evento.getClickedInventory() != e.inventario) return;
        for (int i = 0; i < e.cartas.size(); i++) {
            if (evento.getSlot() == SLOTS[i]) escolher(p, e, e.cartas.get(i), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getView().getTopInventory().getHolder() instanceof Escolha) evento.setCancelled(true);
    }

    /** A carta de Dano: o golpe e a flecha de quem tem ela, nos mobs do evento. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoBater(EntityDamageByEntityEvent evento) {
        Player batedor = evento.getDamager() instanceof Player p ? p
                : evento.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
        if (batedor == null || !this.evento.podeAcertar(evento.getEntity())) return;
        int n = nivel(batedor.getUniqueId(), Tipo.DANO);
        if (n > 0) evento.setDamage(evento.getDamage() * (1 + cfg().getDouble("dano", 0.15) * n));
    }

    /** A carta de Defesa: menos dano em quem tem ela, de qualquer coisa, enquanto luta. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoApanhar(EntityDamageEvent evento) {
        if (!(evento.getEntity() instanceof Player p) || this.evento.lutando(p.getUniqueId()) == null) return;
        int n = nivel(p.getUniqueId(), Tipo.DEFESA);
        if (n > 0) evento.setDamage(evento.getDamage() * Math.pow(1 - cfg().getDouble("defesa", 0.12), n));
    }

    // ---------------------------------------------------------------- texto

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }

    static String romano(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
