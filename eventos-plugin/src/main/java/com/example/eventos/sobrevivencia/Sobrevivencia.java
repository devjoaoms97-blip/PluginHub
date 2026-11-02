package com.example.eventos.sobrevivencia;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A Sobrevivência: todos juntos, com o mesmo kit de ferro, contra 10 ondas de invasores (saqueadores,
 * vingadores, bruxas, evocadores), com chefe na 5 e na 10. Os mobs machucam de verdade; quem morre está
 * fora (sem perder nada: só o kit estava com ele) e vai pra galeria. Quem estiver vivo quando a última
 * onda cair divide o prêmio; se todos morrerem, ninguém ganha.
 * <p>
 * As ondas ({@code ondas.lista}) crescem com o número de jogadores ({@code ondas.a-mais-por-jogador}).
 * Os mobs do evento são marcados ({@code eventosplugin:sobrevivencia}): só eles podem ser acertados por
 * quem luta, e não deixam cair nada nem experiência.
 */
public class Sobrevivencia extends Evento implements Listener {

    private ArenaSobrevivencia arena;
    private final NamespacedKey marca;
    private final Set<UUID> mobs = new HashSet<>();
    private int onda;
    private boolean esperando;
    private long proximaOnda;
    private int jogadoresNoComeco;

    public Sobrevivencia(EventosPlugin plugin) {
        super(plugin, "sobrevivencia");
        this.marca = new NamespacedKey(plugin, "sobrevivencia");
        recarregar();
    }

    @Override
    public String nome() {
        return "Sobrevivência";
    }

    @Override
    protected String titulo() {
        return "§6§l⚔ SOBREVIVÊNCIA ⚔";
    }

    @Override
    protected String cor() {
        return "§6";
    }

    @Override
    public String rotulo() {
        return "§6⚔ Sobrevivência";
    }

    @Override
    public String descricao() {
        return "Todos juntos, com o mesmo kit de ferro, contra 10 ondas de invasores no pátio do castelo, com chefe na 5 e na 10. Morreu, está fora (sem perder nada). Quem chegar vivo ao fim divide o prêmio.";
    }

    @Override
    public void recarregar() {
        this.arena = new ArenaSobrevivencia(cfg().getConfigurationSection("arena"));
    }

    @Override
    public World mundo() {
        return arena.mundo();
    }

    @Override
    public boolean naArena(Location l) {
        return arena.naArena(l);
    }

    @Override
    public boolean naArquibancada(Location l) {
        return arena.naArquibancada(l);
    }

    @Override
    public Location arquibancada() {
        return arena.arquibancada();
    }

    /** Não tem água: só se sai morrendo (ou saindo da arena). */
    @Override
    public boolean caiu(Location l) {
        return false;
    }

    @Override
    public void construir() {
        limparMobs();
        arena.construir();
    }

    @Override
    protected void resetar() {
        limparMobs();
    }

    @Override
    public int[][] caixa() {
        return arena.caixa();
    }

    @Override
    protected List<Location> pontosDeEntrada(int quantos) {
        return arena.pontosDeEntrada(quantos);
    }

    @Override
    public boolean danoDeMobs() {
        return true;
    }

    @Override
    public boolean podeAcertar(Entity alvo) {
        return ehDoEvento(alvo);
    }

    @Override
    protected boolean acabaComUmDePe() {
        return false;
    }

    private boolean ehDoEvento(Entity e) {
        return e.getPersistentDataContainer().has(marca, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- kit

    @Override
    protected void darKit(Player p) {
        ConfigurationSection kit = cfg().getConfigurationSection("kit");
        PlayerInventory inv = p.getInventory();
        inv.setHelmet(inquebravel(Material.IRON_HELMET));
        inv.setChestplate(inquebravel(Material.IRON_CHESTPLATE));
        inv.setLeggings(inquebravel(Material.IRON_LEGGINGS));
        inv.setBoots(inquebravel(Material.IRON_BOOTS));
        inv.setItem(0, inquebravel(Material.IRON_SWORD));
        inv.setItem(1, inquebravel(Material.BOW));
        inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, Math.max(0, kit.getInt("macas-douradas"))));
        inv.setItem(9, new ItemStack(Material.ARROW, Math.max(1, kit.getInt("flechas"))));
        inv.setHeldItemSlot(0);
    }

    private static ItemStack inquebravel(Material m) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------- ondas

    private List<String> lista() {
        return cfg().getStringList("ondas.lista");
    }

    @Override
    protected void aoComecarLuta(long agora) {
        limparMobs();
        onda = 0;
        jogadoresNoComeco = Math.max(1, getParticipantes().size());
        esperando = true;
        proximaOnda = agora + 3000;
        anunciarNaArena(cor() + "§lA onda 1 chega em 3 segundos!");
    }

    @Override
    protected void tickLuta(long agora) {
        World w = arena.mundo();
        mobs.removeIf(id -> {
            Entity e = w.getEntity(id);
            return e == null || e.isDead() || !e.isValid();
        });
        mirar();

        if (!esperando && mobs.isEmpty()) {
            if (onda >= lista().size()) {
                anunciar(titulo() + " " + cor() + "A última onda caiu!");
                finalizar(vivos());
                return;
            }
            esperando = true;
            int pausa = Math.max(1, cfg().getInt("ondas.pausa-segundos"));
            proximaOnda = agora + pausa * 1000L;
            int flechas = cfg().getInt("kit.flechas-por-onda");
            for (Player p : jogadoresVivos()) {
                if (flechas > 0) p.getInventory().addItem(new ItemStack(Material.ARROW, flechas));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
            }
            anunciarNaArena(cor() + "§lOnda " + onda + " vencida! §f" + (flechas > 0 ? "+" + flechas + " flechas. " : "")
                    + cor() + "A onda " + (onda + 1) + " chega em " + pausa + " s.");
        } else if (esperando && agora >= proximaOnda) {
            esperando = false;
            onda++;
            soltarOnda(onda);
        }
    }

    /** Os mobs da onda (a mais com mais gente), saindo dos 4 portões em rodízio, e o chefe, se a onda tiver. */
    private void soltarOnda(int n) {
        double fator = 1 + cfg().getDouble("ondas.a-mais-por-jogador") * (jogadoresNoComeco - 1);
        List<Location> portoes = arena.portoes();
        int i = 0;
        String linha = n <= lista().size() ? lista().get(n - 1) : "";
        for (String parte : linha.split(",")) {
            String[] p = parte.trim().split(":");
            if (p.length != 2) continue;
            EntityType tipo;
            try {
                tipo = EntityType.valueOf(p[0].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Sobrevivência: mob inválido na onda " + n + ": " + p[0]);
                continue;
            }
            int quantos = (int) Math.ceil(Integer.parseInt(p[1].trim()) * fator);
            for (int k = 0; k < quantos; k++) soltar(tipo, portoes.get(i++ % portoes.size()), null, 0, 1);
        }
        ConfigurationSection chefe = cfg().getConfigurationSection("ondas.chefes." + n);
        boolean temChefe = chefe != null;
        if (temChefe) {
            try {
                EntityType tipo = EntityType.valueOf(chefe.getString("tipo", "VINDICATOR").toUpperCase(Locale.ROOT));
                soltar(tipo, portoes.get(0), ChatColor.translateAlternateColorCodes('&', chefe.getString("nome", "Chefe")),
                        chefe.getDouble("vida") * fator, chefe.getDouble("dano", 1));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Sobrevivência: chefe inválido na onda " + n);
            }
        }
        anunciarNaArena(cor() + "§l⚔ ONDA " + n + (n == lista().size() ? " (A ÚLTIMA)" : "") + (temChefe ? " §c§lCOM CHEFE" : "")
                + cor() + "§l! §f" + mobs.size() + cor() + " invasores.");
        for (Player p : jogadoresVivos()) p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
    }

    private void soltar(EntityType tipo, Location onde, String nome, double vida, double dano) {
        Entity e = onde.getWorld().spawnEntity(onde, tipo);
        e.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        // fora de ataque (raid). Sem setPatrolLeader: ele liga a patrulha, e o mob fica parado esperando
        if (e instanceof Raider r) r.setCanJoinRaid(false);
        if (e instanceof LivingEntity le) {
            le.setRemoveWhenFarAway(false);
            if (nome != null) {
                le.setCustomName(nome);
                le.setCustomNameVisible(true);
            }
            if (vida > 0) {
                AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(vida);
                    le.setHealth(vida);
                }
            }
            if (dano != 1) {
                AttributeInstance ataque = le.getAttribute(Attribute.ATTACK_DAMAGE);
                if (ataque != null) ataque.setBaseValue(ataque.getBaseValue() * dano);
            }
        }
        mobs.add(e.getUniqueId());
    }

    /** Cada mob sem alvo (ou com alvo que já saiu) vai atrás do jogador vivo mais perto. */
    private void mirar() {
        List<Player> vivos = jogadoresVivos();
        if (vivos.isEmpty()) return;
        World w = arena.mundo();
        for (UUID id : mobs) {
            if (!(w.getEntity(id) instanceof Mob m)) continue;
            LivingEntity alvo = m.getTarget();
            if (alvo instanceof Player p && vivos.contains(p)) continue;
            vivos.stream().filter(p -> p.getWorld().equals(m.getWorld()))
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(m.getLocation()))).ifPresent(m::setTarget);
        }
    }

    private void limparMobs() {
        World w = arena.mundo();
        if (w == null) return;
        for (UUID id : mobs) {
            Entity e = w.getEntity(id);
            if (e != null) e.remove();
        }
        mobs.clear();
        // e qualquer um marcado que tenha sobrado (de antes de um reinício, por exemplo)
        for (Entity e : w.getEntities()) if (ehDoEvento(e) || (e instanceof Vex && arena.naArena(e.getLocation()))) e.remove();
    }

    private void anunciarNaArena(String msg) {
        var texto = LegacyComponentSerializer.legacySection().deserialize(msg);
        for (Player p : arena.mundo().getPlayers()) {
            if (arena.naArena(p.getLocation()) || arena.naArquibancada(p.getLocation())) p.sendMessage(texto);
        }
    }

    // ---------------------------------------------------------------- mobs do evento

    /** Os mobs do evento não deixam cair nada nem experiência. */
    @EventHandler(priority = EventPriority.HIGH)
    public void aoMobMorrer(EntityDeathEvent evento) {
        if (!ehDoEvento(evento.getEntity())) return;
        evento.getDrops().clear();
        evento.setDroppedExp(0);
    }

    /** As vexes que o evocador chama entram na onda (e são marcadas, pra poderem ser acertadas). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoNascer(EntitySpawnEvent evento) {
        if (!(evento.getEntity() instanceof Vex v) || getFase() != Fase.LUTA || !arena.naArena(v.getLocation())) return;
        v.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        mobs.add(v.getUniqueId());
    }

    /** No /sobrevivencia, durante a luta: a onda atual e quantos invasores ainda estão de pé. */
    @Override
    public String detalheLuta() {
        return "onda " + onda + " de " + lista().size() + ", " + mobs.size() + " invasor(es)";
    }
}
