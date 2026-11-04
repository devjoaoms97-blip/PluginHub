package com.example.eventos.labirinto;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import com.example.eventos.util.Formato;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O Labirinto do Fauno: PvP num labirinto escuro, sorteado a cada evento, com baús escondidos nos becos sem
 * saída. Todos entram sem nada; o que se acha nos baús é do evento (some no fim, como o kit dos outros).
 * Morreu (pra outro jogador ou pro Fauno), está fora e vai pra cima do teto assistir. O último vivo leva o
 * cheque; se o tempo acabar com mais de um vivo, ninguém vence.
 * <p>
 * O Fauno acorda na clareira do meio uns segundos depois da largada e caça o jogador vivo mais perto. Ele
 * não morre (ninguém acerta ele): só dá pra fugir. É um mob invisível (um husk, que anda e bate) carregando
 * um item display com o modelo do Fauno do resource pack ({@code servidor:criatura_fauno}, do PacotePlugin),
 * que vira com ele e troca de quadro enquanto anda. Perto dele, ouve-se o coração batendo.
 */
public class LabirintoDoFauno extends Evento implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    /** O modelo foi encolhido no resource pack (montarias.py): o display aumenta de novo. */
    private static final float ESCALA_MODELO = 3f;
    /** No modelo, o chão fica tantos blocos abaixo do centro do display (-16 de -0.5..0.5, vezes a escala). */
    private static final float CHAO_DO_MODELO = 1.5f * ESCALA_MODELO;

    private ArenaLabirinto arena;
    private final NamespacedKey marca;
    private UUID fauno, modelo;
    private final ItemStack[] quadros = new ItemStack[5];
    private int quadro = -1, passos;
    private Location ultima;
    private long acordaEm;
    private int segundos;

    /** A barra do tempo que falta, e quem está vendo. */
    private final BossBar tempo = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> vendoTempo = new HashSet<>();

    public LabirintoDoFauno(EventosPlugin plugin) {
        super(plugin, "labirinto");
        this.marca = new NamespacedKey(plugin, "labirinto");
        quadros[0] = quadro("");
        for (int i = 1; i < 5; i++) quadros[i] = quadro("_" + (i - 1));
        recarregar();
    }

    private static ItemStack quadro(String sufixo) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey("servidor", "criatura_fauno" + sufixo));
        return item;
    }

    @Override
    public String nome() {
        return "Labirinto do Fauno";
    }

    @Override
    protected String titulo() {
        return "§2§l☽ LABIRINTO DO FAUNO ☽";
    }

    @Override
    protected String cor() {
        return "§2";
    }

    @Override
    public String rotulo() {
        return "§2☽ Labirinto do Fauno";
    }

    @Override
    public String descricao() {
        return "PvP num labirinto escuro e diferente a cada vez, com baús escondidos nos becos sem saída e o Fauno à caça. "
                + "Todos entram sem nada. Morreu, está fora. O último vivo leva o prêmio; se o tempo acabar com mais de um vivo, ninguém vence.";
    }

    @Override
    public String textoPremio() {
        return "O último vivo leva um cheque de §f" + Formato.valor(premio()) + "§7. Se o tempo acabar com mais de um vivo, ninguém leva.";
    }

    @Override
    public void recarregar() {
        this.arena = new ArenaLabirinto(cfg().getConfigurationSection("arena"));
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

    /** Não tem água: só se sai morrendo. */
    @Override
    public boolean caiu(Location l) {
        return false;
    }

    @Override
    public void construir() {
        removerFauno();
        arena.setQuantosBaus(cfg().getInt("baus.quantos"));
        arena.construir();
        encherBaus();
    }

    /** Um labirinto novo, com os baús cheios de novo; sem o Fauno e sem a barra do tempo. */
    @Override
    protected void resetar() {
        removerFauno();
        esconderTempo();
        arena.setQuantosBaus(cfg().getInt("baus.quantos"));
        arena.gerar(new Random());
        encherBaus();
    }

    @Override
    public int[][] caixa() {
        return arena.caixa();
    }

    @Override
    protected List<Location> pontosDeEntrada(int quantos) {
        return arena.pontosDeEntrada(quantos, ThreadLocalRandom.current());
    }

    /** Ninguém ganha kit: o que tiver, achou nos baús. */
    @Override
    protected void darKit(Player p) {
    }

    @Override
    public boolean danoDeMobs() {
        return true;
    }

    @Override
    public boolean pvp() {
        return true;
    }

    @Override
    protected String motivoDaMorte(Player p) {
        Player matador = p.getKiller();
        if (matador != null && matador != p) return "foi derrotado por §f" + matador.getName() + "§7";
        if (p.getLastDamageCause() instanceof EntityDamageByEntityEvent e && ehDoEvento(e.getDamager())) return "foi pego pelo " + cor() + "Fauno§7";
        return "morreu";
    }

    /** No tempo, com mais de um vivo: ninguém vence (e quem estava vivo volta sem o que achou). */
    @Override
    protected void aoAcabarOTempo() {
        esconderTempo();
        removerFauno();
        int vivos = vivos().size();
        finalizarSemVencedor("O tempo acabou com §f" + vivos + " §7vivo" + (vivos == 1 ? "" : "s") + ": ninguém venceu.");
    }

    @Override
    protected void finalizar(List<UUID> ganhadores) {
        esconderTempo();
        removerFauno();
        super.finalizar(ganhadores);
    }

    private boolean ehDoEvento(Entity e) {
        return e.getPersistentDataContainer().has(marca, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- baús

    /** Uma linha da tabela: o item, o peso no sorteio e quantos vêm (de min a max). */
    private record Sorteio(Material item, int peso, int min, int max) {
    }

    private List<Sorteio> tabela() {
        List<Sorteio> lista = new ArrayList<>();
        for (Map<?, ?> m : cfg().getMapList("baus.tabela")) {
            Material item = Material.matchMaterial(String.valueOf(m.get("item")));
            if (item == null || item.isAir()) {
                plugin.getLogger().warning("Labirinto: item inválido na tabela dos baús: " + m.get("item"));
                continue;
            }
            int peso = m.get("peso") instanceof Number n ? n.intValue() : 1;
            int min = 1, max = 1;
            if (m.get("quantidade") instanceof List<?> q && q.size() == 2 && q.get(0) instanceof Number a && q.get(1) instanceof Number b) {
                min = a.intValue();
                max = b.intValue();
            } else if (m.get("quantidade") instanceof Number q) {
                min = max = q.intValue();
            }
            if (peso > 0) lista.add(new Sorteio(item, peso, Math.max(1, min), Math.max(Math.max(1, min), max)));
        }
        return lista;
    }

    /** Cada baú com uns itens sorteados da tabela (pelo peso), em lugares sorteados. */
    private void encherBaus() {
        List<Sorteio> tabela = tabela();
        int total = tabela.stream().mapToInt(Sorteio::peso).sum();
        List<Integer> faixa = cfg().getIntegerList("baus.itens-por-bau");
        int min = faixa.size() > 0 ? faixa.get(0) : 2, max = faixa.size() > 1 ? faixa.get(1) : min;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Block b : arena.baus()) {
            if (!(b.getState() instanceof Chest bau)) continue;
            Inventory inv = bau.getBlockInventory();
            inv.clear();
            if (total <= 0) continue;
            int quantos = min + r.nextInt(Math.max(1, max - min + 1));
            for (int k = 0; k < quantos; k++) {
                int sorte = r.nextInt(total);
                Sorteio s = tabela.getFirst();
                for (Sorteio t : tabela) {
                    if (sorte < t.peso()) {
                        s = t;
                        break;
                    }
                    sorte -= t.peso();
                }
                int slot = r.nextInt(inv.getSize());
                for (int tentativa = 0; tentativa < inv.getSize() && inv.getItem(slot) != null; tentativa++) slot = (slot + 1) % inv.getSize();
                inv.setItem(slot, new ItemStack(s.item(), s.min() + r.nextInt(s.max() - s.min() + 1)));
            }
        }
    }

    // ---------------------------------------------------------------- luta

    @Override
    protected void aoComecarLuta(long agora) {
        segundos = 0;
        int acorda = Math.max(0, cfg().getInt("fauno.acorda-segundos"));
        acordaEm = agora + acorda * 1000L;
        anunciarNaArena(cor() + "§lValendo! §7Os baús estão nos becos sem saída. " + cor() + "O Fauno acorda em §f" + acorda + " s" + cor() + "...");
        atualizarTempo(agora);
    }

    @Override
    protected void tickLuta(long agora) {
        segundos++;
        if (fauno == null && agora >= acordaEm) acordarFauno();
        if (mobDoFauno() instanceof Husk h) {
            mirar(h);
            if (segundos % 2 == 0) {
                double ouvir = cfg().getDouble("fauno.coracao-blocos");
                for (Player p : jogadoresVivos()) {
                    if (p.getWorld().equals(h.getWorld()) && p.getLocation().distanceSquared(h.getLocation()) <= ouvir * ouvir) {
                        p.playSound(h.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f);
                    }
                }
            }
        }
        atualizarTempo(agora);
    }

    @Override
    protected void tickRapidoLuta(long agora) {
        animar();
    }

    /** No /labirinto, durante a luta: quantos vivos e se o Fauno já acordou. */
    @Override
    public String detalheLuta() {
        return vivos().size() + " vivo(s), " + (fauno == null ? "o Fauno ainda dorme" : "o Fauno está caçando");
    }

    // ---------------------------------------------------------------- o Fauno

    private Entity mobDoFauno() {
        return fauno == null ? null : Bukkit.getEntity(fauno);
    }

    /** O Fauno aparece na clareira: o husk invisível (que anda e bate) e o modelo em cima dele. */
    private void acordarFauno() {
        World w = mundo();
        Location onde = arena.clareira();
        Husk h = w.spawn(onde, Husk.class, m -> {
            m.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
            m.setAdult();
            m.setInvisible(true);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.getEquipment().clear();
            atributo(m, Attribute.ATTACK_DAMAGE, cfg().getDouble("fauno.dano"));
            atributo(m, Attribute.MOVEMENT_SPEED, cfg().getDouble("fauno.velocidade"));
            atributo(m, Attribute.FOLLOW_RANGE, 128);
            atributo(m, Attribute.KNOCKBACK_RESISTANCE, 1);
            atributo(m, Attribute.SPAWN_REINFORCEMENTS, 0);
            atributo(m, Attribute.SCALE, cfg().getDouble("fauno.escala", 1.5));
        });
        fauno = h.getUniqueId();
        int brilho = Math.max(0, Math.min(15, cfg().getInt("fauno.brilho")));
        ItemDisplay d = w.spawn(onde, ItemDisplay.class, m -> {
            m.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
            m.setPersistent(false);
            m.setItemStack(quadros[0]);
            m.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            m.setTransformation(new Transformation(new Vector3f(0, CHAO_DO_MODELO, 0), new AxisAngle4f(),
                    new Vector3f(ESCALA_MODELO, ESCALA_MODELO, ESCALA_MODELO), new AxisAngle4f()));
            m.setTeleportDuration(2);
            m.setBrightness(new Display.Brightness(brilho, 0));
        });
        modelo = d.getUniqueId();
        quadro = 0;
        ultima = onde;
        anunciarNaArena(cor() + "§l☠ O Fauno acordou! §7Ele está na clareira do meio, caçando quem estiver mais perto.");
        for (Player p : jogadoresVivos()) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 0.8f, 0.7f);
    }

    private static void atributo(LivingEntity e, Attribute a, double valor) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(valor);
    }

    /** Vai atrás do jogador vivo mais perto. */
    private void mirar(Husk h) {
        jogadoresVivos().stream().filter(p -> p.getWorld().equals(h.getWorld()))
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(h.getLocation())))
                .ifPresent(p -> {
                    if (h.getTarget() != p) h.setTarget(p);
                });
    }

    /** A cada 2 ticks: o modelo vai junto com o husk, vira com o corpo e, andando, troca de quadro. */
    private void animar() {
        if (!(mobDoFauno() instanceof Husk h) || modelo == null || !(Bukkit.getEntity(modelo) instanceof ItemDisplay d)) return;
        Location agora = h.getLocation();
        Location destino = agora.clone();
        destino.setYaw(h.getBodyYaw() + 180);
        destino.setPitch(0);
        d.teleport(destino);
        boolean andando = ultima != null && ultima.getWorld().equals(agora.getWorld())
                && (agora.getX() - ultima.getX()) * (agora.getX() - ultima.getX()) + (agora.getZ() - ultima.getZ()) * (agora.getZ() - ultima.getZ()) > 0.0025;
        ultima = agora;
        int novo = andando ? 1 + (passos++ / 2) % 4 : 0;
        if (!andando) passos = 0;
        if (novo != quadro) {
            quadro = novo;
            d.setItemStack(quadros[novo]);
        }
    }

    private void removerFauno() {
        fauno = null;
        modelo = null;
        World w = mundo();
        if (w == null) return;
        for (Entity e : w.getEntities()) if (sobra(e)) e.remove();
    }

    /**
     * O que não deveria estar lá: um Fauno (ou modelo) que não é o de agora (de antes de um reinício, por
     * exemplo) e o que ficou no chão do labirinto (flechas, itens).
     */
    private boolean sobra(Entity e) {
        if (ehDoEvento(e)) return !e.getUniqueId().equals(fauno) && !e.getUniqueId().equals(modelo);
        return (e instanceof Item || e instanceof AbstractArrow) && naArena(e.getLocation());
    }

    /** As entidades de um chunk só carregam depois dele: o que sobrou num chunk descarregado sai aqui. */
    @EventHandler
    public void aoCarregar(EntitiesLoadEvent evento) {
        if (!evento.getWorld().equals(mundo())) return;
        for (Entity e : evento.getEntities()) if (sobra(e)) e.remove();
    }

    /** O Fauno só caça quem está vivo no labirinto (nem quem assiste em cima do teto). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoMirar(EntityTargetEvent evento) {
        if (!ehDoEvento(evento.getEntity())) return;
        if (!(evento.getTarget() instanceof Player p) || lutando(p.getUniqueId()) == null) evento.setCancelled(true);
    }

    // ---------------------------------------------------------------- o tempo que falta

    /** A barra no alto da tela, pra quem luta e quem assiste: o tempo que falta e quantos estão vivos. */
    private void atualizarTempo(long agora) {
        long maximo = Math.max(1, cfg().getLong("luta.tempo-maximo-minutos")) * 60_000L;
        long falta = Math.max(0, maximo - (agora - inicioLuta()));
        long s = (falta + 999) / 1000;
        int vivos = vivos().size();
        tempo.name(LEGADO.deserialize(cor() + "☽ Labirinto do Fauno §7· §f" + s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60)
                + " §7restantes · §f" + vivos + " §7vivo" + (vivos == 1 ? "" : "s")));
        tempo.progress((float) Math.max(0, Math.min(1, (double) falta / maximo)));
        tempo.color(falta <= 60_000 ? BossBar.Color.RED : BossBar.Color.GREEN);
        Set<UUID> agoraVendo = new HashSet<>();
        World w = mundo();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean participa = getParticipantes().containsKey(p.getUniqueId()) && p.getWorld().equals(w);
            if (!participa && !(p.getWorld().equals(w) && (naArena(p.getLocation()) || naArquibancada(p.getLocation())))) continue;
            agoraVendo.add(p.getUniqueId());
            if (vendoTempo.add(p.getUniqueId())) p.showBossBar(tempo);
        }
        for (UUID id : new ArrayList<>(vendoTempo)) {
            if (agoraVendo.contains(id)) continue;
            vendoTempo.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(tempo);
        }
    }

    private void esconderTempo() {
        for (UUID id : vendoTempo) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(tempo);
        }
        vendoTempo.clear();
    }

    private void anunciarNaArena(String msg) {
        var texto = LEGADO.deserialize(msg);
        World w = mundo();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (naArena(p.getLocation()) || naArquibancada(p.getLocation())) p.sendMessage(texto);
        }
    }
}
