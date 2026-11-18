package com.example.skills.profissao;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A segunda leva das skills de profissão (2026-11-18):
 * <ul>
 *   <li><b>Caçador</b>: XP por mob morto pelo jogador, só os que nasceram sozinhos (os de spawner, ovo,
 *       criação, plugin... não contam); chance de drop a mais (dobra o que o mob larga) e de a cabeça dele
 *       (troféu que vende, {@link Trofeus}).</li>
 *   <li><b>Escavador</b>: XP por terra, areia e cascalho quebrados nos mundos de recursos (bloco colocado por
 *       jogador não conta); chance de achar uma relíquia (4 raridades, vende) e, mais rara, de Fragmento
 *       Arcano e mapa do tesouro.</li>
 *   <li><b>Alquimista</b>: XP por poção pronta tirada do suporte (com efeito: água, estranha, mundana e
 *       espessa não contam); quem mexeu no suporte por último ganha a poção mais longa e a chance de uma a
 *       mais.</li>
 * </ul>
 * O Ferreiro (desconto e XP nas oficinas) é pelo OficinasPlugin, que pergunta ao {@link SkillsPlugin}.
 */
public class Profissoes2Listener implements Listener {

    private static final int MAX_COLOCADOS = 100_000;
    private static final Set<PotionType> SEM_EFEITO = Set.of(PotionType.WATER, PotionType.AWKWARD, PotionType.MUNDANE, PotionType.THICK);
    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final SkillsPlugin plugin;
    private final Integracoes integracoes;
    private final Trofeus trofeus;
    private final NamespacedKey fermentada;
    /** Blocos de cavar colocados por jogador (os mais recentes): não dão XP nem achado. */
    private final Map<String, Boolean> colocados = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> mais) {
            return size() > MAX_COLOCADOS;
        }
    };
    /** Quem mexeu por último em cada suporte de poções. */
    private final Map<String, UUID> alquimistaDoSuporte = new HashMap<>();

    public Profissoes2Listener(SkillsPlugin plugin, Integracoes integracoes, Trofeus trofeus) {
        this.plugin = plugin;
        this.integracoes = integracoes;
        this.trofeus = trofeus;
        this.fermentada = new NamespacedKey(plugin, "fermentada");
    }

    private static String chave(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static String chave(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private boolean sorteio(Player jogador, Skill skill, String caminho) {
        return ThreadLocalRandom.current().nextDouble() < plugin.getBonusCalculator().porNivel(jogador.getUniqueId(), skill, caminho);
    }

    private void xp(Player jogador, Skill skill, double quanto) {
        if (quanto <= 0) return;
        plugin.getXpManager().adicionarXp(jogador, skill, quanto);
        plugin.getXpManager().marcarEmUso(jogador.getUniqueId(), skill);
    }

    private static void entregar(Player jogador, ItemStack item) {
        jogador.getInventory().addItem(item).values().forEach(i -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), i));
    }

    // ---------------------------------------------------------------- Caçador

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMatar(EntityDeathEvent evento) {
        LivingEntity mob = evento.getEntity();
        Player jogador = mob.getKiller();
        if (jogador == null || mob instanceof Player || jogador.getGameMode() != GameMode.SURVIVAL) return;
        if (!plugin.getConfig().getStringList("cacador.origens").contains(mob.getEntitySpawnReason().name())) return;
        if (mob.getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getKey().contains("boss"))) return;
        String tipo = mob.getType().name();
        double base = plugin.getConfig().getDouble("cacador.xp-por-mob", 14);
        xp(jogador, Skill.CACADOR, plugin.getConfig().isSet("cacador.xp-mob." + tipo) ? plugin.getConfig().getDouble("cacador.xp-mob." + tipo) : base);

        if (sorteio(jogador, Skill.CACADOR, "cacador.chance-drop-extra-por-nivel") && !evento.getDrops().isEmpty()) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack d : evento.getDrops()) if (d != null && !d.getType().isAir()) extra.add(d.clone());
            evento.getDrops().addAll(extra);
            mob.getWorld().spawnParticle(Particle.CRIT, mob.getLocation().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0);
        }
        if (sorteio(jogador, Skill.CACADOR, "cacador.chance-cabeca-por-nivel")) {
            ItemStack cabeca = trofeus.cabeca(tipo.toLowerCase(Locale.ROOT));
            if (cabeca != null) {
                evento.getDrops().add(cabeca);
                jogador.sendMessage("§6☠ §eCaçador: §fo " + nomeDoMob(tipo) + " §edeixou a cabeça! §7(troféu, §f/trofeus§7)");
                jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            }
        }
    }

    private String nomeDoMob(String tipo) {
        String nome = plugin.getConfig().getString("cacador.cabecas." + tipo.toLowerCase(Locale.ROOT) + ".nome");
        return nome == null || nome.isBlank() ? tipo.toLowerCase(Locale.ROOT).replace('_', ' ') : org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', nome)).replace("Cabeça de ", "").replace("Cabeça do ", "").toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- Escavador

    private boolean deCavar(Block b) {
        return plugin.getConfig().getStringList("escavador.blocos").contains(b.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (deCavar(evento.getBlock())) colocados.put(chave(evento.getBlock()), Boolean.TRUE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoCavar(BlockBreakEvent evento) {
        Player jogador = evento.getPlayer();
        Block bloco = evento.getBlock();
        if (jogador.getGameMode() != GameMode.SURVIVAL || !deCavar(bloco)) return;
        if (colocados.remove(chave(bloco)) != null) return;
        if (!plugin.getConfig().getStringList("escavador.mundos").contains(bloco.getWorld().getName())) return;
        xp(jogador, Skill.ESCAVADOR, plugin.getConfig().getDouble("escavador.xp-por-bloco", 1));
        Location centro = bloco.getLocation().add(0.5, 0.5, 0.5);

        if (sorteio(jogador, Skill.ESCAVADOR, "escavador.chance-achado-por-nivel")) {
            String id = sortearReliquia(plugin.getSkillDataManager().get(jogador.getUniqueId()).getNivel(Skill.ESCAVADOR));
            ItemStack reliquia = id == null ? null : trofeus.reliquia(id);
            if (reliquia != null) {
                bloco.getWorld().dropItemNaturally(centro, reliquia);
                jogador.sendMessage("§6⛏ §eEscavador: §fvocê achou " + reliquia.getItemMeta().getDisplayName() + "§e! §7(§f/trofeus§7)");
                jogador.playSound(centro, Sound.BLOCK_SUSPICIOUS_SAND_BREAK, 1f, 0.8f);
                jogador.playSound(centro, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                String raridade = plugin.getConfig().getString("escavador.reliquias." + id + ".raridade", "comum");
                if ("lendario".equals(raridade)) {
                    plugin.getServer().broadcastMessage("§6⛏ §e" + jogador.getName() + " §7desenterrou " + reliquia.getItemMeta().getDisplayName() + "§7!");
                }
            }
        }
        if (sorteio(jogador, Skill.ESCAVADOR, "escavador.chance-fragmento-por-nivel")) {
            ItemStack f = integracoes.fragmento();
            if (f != null) {
                entregar(jogador, f);
                jogador.sendMessage("§d§l✦ §dCavando, você achou um §fFragmento Arcano§d! §7Troque com o Cambista.");
            }
        }
        if (sorteio(jogador, Skill.ESCAVADOR, "escavador.chance-mapa-por-nivel")) {
            ItemStack mapa = integracoes.mapa("comum");
            if (mapa != null) {
                entregar(jogador, mapa);
                jogador.sendMessage("§6✦ §eCavando, você achou um §fMapa do Tesouro§e! §7Use §f/warp tesouros§7.");
            }
        }
    }

    /** A relíquia: a raridade pela faixa de nível ({@code escavador.pesos-raridade}) e o item pelo peso, dentro dela. */
    private String sortearReliquia(int nivel) {
        Map<?, ?> faixa = null;
        int melhor = Integer.MIN_VALUE;
        for (Map<?, ?> m : plugin.getConfig().getMapList("escavador.pesos-raridade")) {
            int desde = m.get("nivel") instanceof Number n ? n.intValue() : 0;
            if (desde <= nivel && desde > melhor) {
                melhor = desde;
                faixa = m;
            }
        }
        String[] raridades = {"comum", "raro", "epico", "lendario"};
        String raridade = raridades[0];
        if (faixa != null) {
            double total = 0;
            for (String r : raridades) total += faixa.get(r) instanceof Number n ? n.doubleValue() : 0;
            double s = ThreadLocalRandom.current().nextDouble() * total;
            for (String r : raridades) {
                s -= faixa.get(r) instanceof Number n ? n.doubleValue() : 0;
                if (s < 0) {
                    raridade = r;
                    break;
                }
            }
        }
        var rel = trofeus.reliquias();
        if (rel == null) return null;
        List<String> ids = new ArrayList<>();
        double total = 0;
        for (String id : rel.getKeys(false)) {
            if (!raridade.equals(rel.getString(id + ".raridade", "comum"))) continue;
            ids.add(id);
            total += rel.getDouble(id + ".peso", 1);
        }
        double s = ThreadLocalRandom.current().nextDouble() * total;
        for (String id : ids) {
            s -= rel.getDouble(id + ".peso", 1);
            if (s < 0) return id;
        }
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ---------------------------------------------------------------- Alquimista

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoAbrirSuporte(InventoryOpenEvent evento) {
        if (evento.getInventory() instanceof BrewerInventory inv && inv.getHolder() instanceof BrewingStand suporte
                && evento.getPlayer() instanceof Player p) {
            alquimistaDoSuporte.put(chave(suporte.getLocation()), p.getUniqueId());
        }
    }

    /** O suporte terminou: marca as poções (dão XP a quem tirar) e dá o bônus do Alquimista de quem mexeu nele. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoFermentar(BrewEvent evento) {
        UUID dono = alquimistaDoSuporte.get(chave(evento.getBlock().getLocation()));
        Player alquimista = dono == null ? null : plugin.getServer().getPlayer(dono);
        double bonus = alquimista == null ? 0
                : plugin.getBonusCalculator().porNivel(alquimista.getUniqueId(), Skill.ALQUIMISTA, "alquimista.duracao-por-nivel");
        List<ItemStack> prontas = new ArrayList<>();
        List<ItemStack> resultados = evento.getResults();
        for (int i = 0; i < resultados.size(); i++) {
            ItemStack r = resultados.get(i);
            if (r == null || !(r.getItemMeta() instanceof PotionMeta meta) || meta.getBasePotionType() == null
                    || SEM_EFEITO.contains(meta.getBasePotionType())) continue;
            if (bonus > 0) {
                for (PotionEffect e : meta.getBasePotionType().getPotionEffects()) {
                    if (e.getType().isInstant() || e.getDuration() <= 1) continue;
                    meta.addCustomEffect(e.withDuration((int) Math.round(e.getDuration() * (1 + bonus))), false);
                }
                List<String> lore = new ArrayList<>();
                lore.add("§d✦ Alquimista: §f+" + Math.round(bonus * 100) + "% §dde duração");
                meta.setLore(lore);
            }
            meta.getPersistentDataContainer().set(fermentada, PersistentDataType.BYTE, (byte) 1);
            r.setItemMeta(meta);
            resultados.set(i, r);
            prontas.add(r);
        }
        if (alquimista != null && !prontas.isEmpty() && sorteio(alquimista, Skill.ALQUIMISTA, "alquimista.chance-pocao-extra-por-nivel")) {
            ItemStack extra = prontas.get(ThreadLocalRandom.current().nextInt(prontas.size())).clone();
            Location em = evento.getBlock().getLocation().add(0.5, 1.1, 0.5);
            evento.getBlock().getWorld().dropItem(em, extra);
            evento.getBlock().getWorld().spawnParticle(Particle.WITCH, em, 12, 0.3, 0.3, 0.3, 0);
            if (alquimista.getLocation().distanceSquared(em) < 400) {
                alquimista.sendActionBar(LEGADO.deserialize("§d⚗ Alquimista: §fuma poção a mais saiu do suporte!"));
            }
        }
    }

    /** Tirar do suporte uma poção que ele fez dá XP de Alquimista (e a marca sai, pra empilhar com as outras). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTirarPocao(InventoryClickEvent evento) {
        if (!(evento.getClickedInventory() instanceof BrewerInventory inv) || evento.getSlot() > 2) return;
        if (!(evento.getWhoClicked() instanceof Player p) || p.getGameMode() != GameMode.SURVIVAL) return;
        if (inv.getHolder() instanceof BrewingStand suporte) alquimistaDoSuporte.put(chave(suporte.getLocation()), p.getUniqueId());
        ItemStack item = evento.getCurrentItem();
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) return;
        if (!meta.getPersistentDataContainer().has(fermentada)) return;
        meta.getPersistentDataContainer().remove(fermentada);
        item.setItemMeta(meta);
        evento.setCurrentItem(item);
        xp(p, Skill.ALQUIMISTA, plugin.getConfig().getDouble("alquimista.xp-por-pocao", 10) * item.getAmount());
    }
}
