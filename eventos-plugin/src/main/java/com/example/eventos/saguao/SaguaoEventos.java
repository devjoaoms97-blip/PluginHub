package com.example.eventos.saguao;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import com.example.eventos.util.Formato;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O saguão dos eventos ({@code /warp eventos}): um salão no mundo das arenas, com um portal por evento na
 * parede do fundo, o painel da agenda de hoje numa parede, o ranking de vencedores na outra e o portal de
 * volta pro spawn atrás de quem chega.
 * <p>
 * Andar num portal: com as inscrições abertas, é o {@code /<evento> entrar}; com a luta rolando, leva pra
 * arquibancada; fora disso, diz quando abre. Quem se inscreve (pelo portal ou pelo comando, de qualquer
 * lugar) espera aqui até começar, vendo na barra do alto quanto falta e quantos estão inscritos: sair do
 * saguão (/spawn, o portal de volta) cancela a inscrição.
 * <p>
 * Os textos em cima dos portais e o painel são text displays não persistentes, refeitos sozinhos (somem no
 * reinício e voltam no próximo segundo). O salão é construído pelo plugin ({@code /eventos construir}).
 */
public class SaguaoEventos implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final String TAG = "eventos-saguao";
    /** Meia largura e meia profundidade do salão por dentro, e a altura do teto. */
    private static final int MEIO_X = 19, MEIO_Z = 12, ALTURA = 9;
    /** Cada portal: 5 de largura (3 de vão) e 6 de altura (4 de vão), um ao lado do outro. */
    private static final int PORTAL = 5, VAO_ALTURA = 4;

    /** A cor de cada evento (o código da cor dele) no portal: o concreto da moldura e as partículas. */
    private record Tinta(Material concreto, Material vidro, Color particula) {
    }

    private static final Map<Character, Tinta> TINTAS = Map.of(
            'a', new Tinta(Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS, Color.fromRGB(128, 255, 32)),
            '2', new Tinta(Material.GREEN_CONCRETE, Material.GREEN_STAINED_GLASS, Color.fromRGB(40, 160, 60)),
            'b', new Tinta(Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, Color.fromRGB(120, 210, 255)),
            'd', new Tinta(Material.MAGENTA_CONCRETE, Material.MAGENTA_STAINED_GLASS, Color.fromRGB(255, 90, 230)),
            '6', new Tinta(Material.ORANGE_CONCRETE, Material.ORANGE_STAINED_GLASS, Color.fromRGB(255, 160, 30)),
            'e', new Tinta(Material.YELLOW_CONCRETE, Material.YELLOW_STAINED_GLASS, Color.fromRGB(255, 230, 60)));
    private static final Tinta BRANCA = new Tinta(Material.WHITE_CONCRETE, Material.WHITE_STAINED_GLASS, Color.WHITE);

    private final EventosPlugin plugin;
    private final BossBar espera = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private final Set<UUID> vendoEspera = new HashSet<>();
    private final Map<UUID, Long> ultimoPortal = new HashMap<>();
    private final Map<String, UUID> displays = new HashMap<>();

    public SaguaoEventos(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("saguao");
    }

    public World mundo() {
        return Bukkit.getWorld(String.valueOf(cfg().getString("mundo")));
    }

    private int cx() {
        List<Integer> c = cfg().getIntegerList("centro");
        return c.size() > 0 ? c.get(0) : 0;
    }

    private int cz() {
        List<Integer> c = cfg().getIntegerList("centro");
        return c.size() > 1 ? c.get(1) : 0;
    }

    private int chao() {
        return cfg().getInt("chao-y");
    }

    /** Dentro do salão. */
    public boolean dentro(Location l) {
        World w = mundo();
        return w != null && l.getWorld() != null && l.getWorld().equals(w)
                && l.getX() >= cx() - MEIO_X && l.getX() < cx() + MEIO_X + 1 && l.getZ() >= cz() - MEIO_Z && l.getZ() < cz() + MEIO_Z + 1
                && l.getY() >= chao() && l.getY() < chao() + ALTURA;
    }

    /** Onde se chega (e se espera): no meio, um pouco pra trás, olhando pros portais. */
    public Location chegada() {
        return new Location(mundo(), cx() + 0.5, chao() + 1, cz() + 6.5, 180, 0);
    }

    /** O canto oeste (x) do portal de cada evento, na ordem do plugin. */
    private int xDoPortal(int i) {
        int n = plugin.getEventos().size();
        return cx() - (n * PORTAL + (n - 1)) / 2 + i * (PORTAL + 1);
    }

    /** O plano dos portais: a fileira logo à frente da parede do fundo. */
    private int zDosPortais() {
        return cz() - MEIO_Z + 1;
    }

    /** O plano do portal de volta, na parede da frente. */
    private int zDaVolta() {
        return cz() + MEIO_Z - 1;
    }

    // ---------------------------------------------------------------- construção

    /** O salão inteiro (paredes, piso, teto e as molduras dos portais). */
    public void construir() {
        World w = mundo();
        if (w == null) return;
        int x0 = cx() - MEIO_X, x1 = cx() + MEIO_X, z0 = cz() - MEIO_Z, z1 = cz() + MEIO_Z, y0 = chao(), teto = chao() + ALTURA;
        encher(w, x0 - 3, y0 - 2, z0 - 3, x1 + 3, teto + 3, z1 + 3, Material.AIR);
        encher(w, x0 - 1, y0 - 1, z0 - 1, x1 + 1, y0 - 1, z1 + 1, Material.POLISHED_BLACKSTONE);
        // piso xadrez escuro, com o tapete vermelho da chegada até os portais
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                w.getBlockAt(x, y0, z).setType((x + z) % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES, false);
            }
        }
        encher(w, cx() - 1, y0, zDosPortais() + 1, cx() + 1, y0, zDaVolta() - 1, Material.RED_WOOL);
        encher(w, x0, y0, zDosPortais() + 1, x1, y0, zDosPortais() + 1, Material.RED_WOOL);
        // paredes de carvalho escuro com colunas, rodapé de pedra-negra e friso de ouro
        for (int y = y0; y <= teto; y++) anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, Material.DARK_OAK_PLANKS);
        for (int y = y0 + 1; y < teto; y++) {
            for (int x = x0 - 1; x <= x1 + 1; x += 4) {
                w.getBlockAt(x, y, z0 - 1).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                w.getBlockAt(x, y, z1 + 1).setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
            for (int z = z0 - 1; z <= z1 + 1; z += 4) {
                w.getBlockAt(x0 - 1, y, z).setType(Material.STRIPPED_DARK_OAK_LOG, false);
                w.getBlockAt(x1 + 1, y, z).setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
        }
        anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y0 + 1, Material.POLISHED_BLACKSTONE_BRICKS);
        anel(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, teto - 1, Material.GOLD_BLOCK);
        // teto com lanternas do mar em grade
        encher(w, x0 - 1, teto, z0 - 1, x1 + 1, teto, z1 + 1, Material.DARK_OAK_PLANKS);
        for (int x = x0 + 2; x <= x1 - 2; x += 5) {
            for (int z = z0 + 2; z <= z1 - 2; z += 5) w.getBlockAt(x, teto, z).setType(Material.SEA_LANTERN, false);
        }

        // um portal por evento, na cor dele
        List<Evento> eventos = plugin.getEventos();
        for (int i = 0; i < eventos.size(); i++) moldura(w, xDoPortal(i), zDosPortais(), tinta(eventos.get(i)));
        // o de volta pro spawn, na parede da frente
        moldura(w, cx() - 2, zDaVolta(), new Tinta(Material.QUARTZ_BLOCK, Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA));
        // os pedestais do painel e do ranking, nas paredes do lado
        encher(w, x1, y0 + 1, cz() - 1, x1, y0 + 1, cz() + 1, Material.POLISHED_BLACKSTONE_BRICKS);
        encher(w, x0, y0 + 1, cz() - 1, x0, y0 + 1, cz() + 1, Material.POLISHED_BLACKSTONE_BRICKS);
        removerDisplays();
    }

    /** Moldura de 5 x 6 (vão de 3 x 4), com o topo de vidro colorido e o degrau de quartzo. */
    private void moldura(World w, int x0, int z, Tinta t) {
        int y0 = chao();
        encher(w, x0, y0 + 1, z, x0 + PORTAL - 1, y0 + VAO_ALTURA + 2, z, t.concreto());
        encher(w, x0 + 1, y0 + 1, z, x0 + PORTAL - 2, y0 + VAO_ALTURA, z, Material.AIR);
        encher(w, x0 + 1, y0 + VAO_ALTURA + 1, z, x0 + PORTAL - 2, y0 + VAO_ALTURA + 1, z, t.vidro());
        encher(w, x0 + 1, y0, z, x0 + PORTAL - 2, y0, z, Material.SMOOTH_QUARTZ);
    }

    private static Tinta tinta(Evento e) {
        String cor = e.rotulo();
        return cor.length() > 1 && cor.charAt(0) == '§' ? TINTAS.getOrDefault(cor.charAt(1), BRANCA) : BRANCA;
    }

    // ---------------------------------------------------------------- a cada segundo

    /** Os textos dos portais e o painel, a barra de quem espera e: quem saiu do saguão perde a inscrição. */
    public void tick() {
        World w = mundo();
        if (w == null) return;
        Evento aberto = plugin.eventoAberto();
        if (aberto != null && aberto.getFase() == Evento.Fase.INSCRICAO) {
            for (UUID id : new ArrayList<>(aberto.getParticipantes().keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !dentro(p.getLocation())) {
                    aberto.desinscrever(p);
                    p.sendMessage("§c" + aberto.nome() + ": §7você saiu do saguão dos eventos, e a inscrição foi cancelada.");
                }
            }
        }
        atualizarEspera(aberto);
        Location centro = new Location(w, cx(), chao(), cz());
        if (!w.isChunkLoaded(centro.getBlockX() >> 4, centro.getBlockZ() >> 4)) return;
        List<Evento> eventos = plugin.getEventos();
        for (int i = 0; i < eventos.size(); i++) {
            Evento e = eventos.get(i);
            texto(w, "portal-" + e.id(), new Location(w, xDoPortal(i) + PORTAL / 2.0, chao() + VAO_ALTURA + 3.4, zDosPortais() + 1.2), placa(e));
        }
        texto(w, "volta", new Location(w, cx() + 0.5, chao() + VAO_ALTURA + 3.4, zDaVolta() - 0.2), "§b§lVoltar pro spawn");
        StringBuilder painel = new StringBuilder("§6§l✦ EVENTOS DE HOJE ✦");
        for (String linha : plugin.getAgenda().agenda()) painel.append("\n").append(linha.trim());
        painel.append("\n§8/eventos");
        texto(w, "painel", new Location(w, cx() + MEIO_X - 0.5, chao() + 3.2, cz() + 0.5), painel.toString());
    }

    /** O que vai em cima do portal: o nome, os horários e o que está acontecendo. */
    private static String placa(Evento e) {
        String horarios = String.join(" e ", e.cfg().getStringList("agenda"));
        String agora = e.getFase() == null ? "§7Próximo: §f" + proximo(e)
                : switch (e.getFase()) {
                    case INSCRICAO -> "§a§lINSCRIÇÕES ABERTAS! §f" + e.getParticipantes().size() + " inscrito(s)\n§aEntre no portal";
                    case PREPARACAO, LUTA -> "§c§lROLANDO AGORA\n§7Entre no portal pra assistir";
                    case FIM -> "§7Acabou de terminar";
                };
        return e.rotulo() + "\n§7Todo dia §f" + horarios + "\n" + agora;
    }

    /** "hoje 14:00" ou "amanhã 08:00": o próximo horário da agenda do evento. */
    private static String proximo(Evento e) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime melhor = null;
        for (String s : e.cfg().getStringList("agenda")) {
            try {
                LocalTime h = LocalTime.parse(s.trim());
                LocalDateTime t = agora.toLocalDate().atTime(h);
                if (!t.isAfter(agora)) t = t.plusDays(1);
                if (melhor == null || t.isBefore(melhor)) melhor = t;
            } catch (DateTimeParseException ignorado) {
                // horário errado no config: só não conta
            }
        }
        if (melhor == null) return "sem agenda";
        return (melhor.toLocalDate().equals(agora.toLocalDate()) ? "hoje " : "amanhã ") + melhor.toLocalTime();
    }

    /** O text display de uma chave: cria se não houver (ou se sumiu) e troca o texto. */
    private void texto(World w, String chave, Location onde, String texto) {
        UUID id = displays.get(chave);
        TextDisplay d = id != null && Bukkit.getEntity(id) instanceof TextDisplay td && td.isValid() ? td : null;
        if (d == null) {
            d = w.spawn(onde, TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setPersistent(false);
                t.addScoreboardTag(TAG);
            });
            displays.put(chave, d.getUniqueId());
        }
        d.text(LEGADO.deserialize(texto));
    }

    public void removerDisplays() {
        displays.clear();
        for (World w : Bukkit.getWorlds()) {
            w.getEntitiesByClass(TextDisplay.class).stream().filter(d -> d.getScoreboardTags().contains(TAG)).forEach(TextDisplay::remove);
        }
    }

    /** A barra do alto, pra quem está no saguão, com as inscrições abertas: quanto falta e quantos inscritos. */
    private void atualizarEspera(Evento aberto) {
        boolean mostrar = aberto != null && aberto.getFase() == Evento.Fase.INSCRICAO;
        Set<UUID> agoraVendo = new HashSet<>();
        if (mostrar) {
            long falta = aberto.faltaInscricao();
            long s = (falta + 999) / 1000;
            espera.name(LEGADO.deserialize(aberto.rotulo() + " §7começa em §f" + Formato.tempo(s * 1000) + " §7· §f"
                    + aberto.getParticipantes().size() + " §7inscrito(s)"));
            espera.progress((float) Math.max(0, Math.min(1, aberto.fracaoInscricao())));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!dentro(p.getLocation())) continue;
                agoraVendo.add(p.getUniqueId());
                if (vendoEspera.add(p.getUniqueId())) p.showBossBar(espera);
            }
        }
        for (UUID id : new ArrayList<>(vendoEspera)) {
            if (agoraVendo.contains(id)) continue;
            vendoEspera.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(espera);
        }
    }

    /** A cada 10 ticks: o brilho de cada portal, na cor do evento (só com alguém no saguão). */
    public void particulas() {
        World w = mundo();
        if (w == null || w.getPlayers().stream().noneMatch(p -> dentro(p.getLocation()))) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Evento> eventos = plugin.getEventos();
        for (int i = 0; i < eventos.size(); i++) {
            Tinta t = tinta(eventos.get(i));
            Particle.DustOptions po = new Particle.DustOptions(t.particula(), 1.3f);
            for (int k = 0; k < 6; k++) {
                w.spawnParticle(Particle.DUST, xDoPortal(i) + 1 + r.nextDouble() * 3, chao() + 1 + r.nextDouble() * VAO_ALTURA,
                        zDosPortais() + 0.5, 1, 0, 0, 0, 0, po);
            }
        }
        Particle.DustOptions volta = new Particle.DustOptions(Color.AQUA, 1.3f);
        for (int k = 0; k < 6; k++) {
            w.spawnParticle(Particle.DUST, cx() - 1 + r.nextDouble() * 3, chao() + 1 + r.nextDouble() * VAO_ALTURA, zDaVolta() + 0.5, 1, 0, 0, 0, 0, volta);
        }
    }

    // ---------------------------------------------------------------- os portais

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMover(PlayerMoveEvent evento) {
        Location para = evento.getTo();
        if (evento.getFrom().getBlockX() == para.getBlockX() && evento.getFrom().getBlockZ() == para.getBlockZ()
                && evento.getFrom().getBlockY() == para.getBlockY()) return;
        if (!dentro(para)) return;
        int bx = para.getBlockX(), by = para.getBlockY(), bz = para.getBlockZ();
        if (by < chao() + 1 || by > chao() + VAO_ALTURA) return;
        Player p = evento.getPlayer();
        if (bz == zDaVolta() && bx >= cx() - 1 && bx <= cx() + 1) {
            if (!pode(p)) return;
            voltar(p);
            return;
        }
        if (bz != zDosPortais()) return;
        List<Evento> eventos = plugin.getEventos();
        for (int i = 0; i < eventos.size(); i++) {
            int x0 = xDoPortal(i);
            if (bx < x0 + 1 || bx > x0 + PORTAL - 2) continue;
            if (pode(p)) usar(p, eventos.get(i));
            return;
        }
    }

    /** No máximo um portal a cada 2 s por jogador (ficar parado no vão não repete). */
    private boolean pode(Player p) {
        long agora = System.currentTimeMillis();
        Long antes = ultimoPortal.get(p.getUniqueId());
        if (antes != null && agora - antes < 2000) return false;
        ultimoPortal.put(p.getUniqueId(), agora);
        return true;
    }

    private void usar(Player p, Evento e) {
        if (e.getFase() == Evento.Fase.INSCRICAO) {
            if (e.getParticipantes().containsKey(p.getUniqueId())) {
                p.sendMessage("§7Você já está inscrito " + e.noNome() + ". Espere aqui no saguão.");
            } else {
                String erro = e.entrar(p);
                if (erro == null) {
                    p.sendMessage(e.rotulo() + " §aInscrito! §7Espere aqui no saguão: sair dele cancela a inscrição.");
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
                } else {
                    p.sendMessage("§c" + erro);
                }
            }
        } else if (e.getFase() == Evento.Fase.PREPARACAO || e.getFase() == Evento.Fase.LUTA) {
            if (plugin.eventoLutando(p.getUniqueId()) != null) return;
            e.assistir(p);
            p.sendMessage(e.rotulo() + " §7Na arquibancada. Pra sair: §f/spawn§7.");
            return;
        } else {
            p.sendMessage(e.rotulo() + " §7abre §f" + proximo(e) + "§7. As inscrições ficam abertas por "
                    + Formato.tempo(Math.max(10, e.cfg().getInt("inscricao.duracao-segundos")) * 1000L) + ".");
        }
        // um empurrão pra fora do vão, pra não ficar dentro dele
        p.setVelocity(new Vector(0, 0.2, 0.5));
    }

    /** O portal da frente: de volta pro spawn ({@code saguao.volta}). */
    private void voltar(Player p) {
        List<Double> v = cfg().getDoubleList("volta.posicao");
        World w = Bukkit.getWorld(String.valueOf(cfg().getString("volta.mundo")));
        if (w == null || v.size() < 3) return;
        p.teleport(new Location(w, v.get(0), v.get(1), v.get(2), v.size() > 3 ? v.get(3).floatValue() : 0, 0));
    }

    private static void anel(World w, int ax, int az, int bx, int bz, int y, Material m) {
        encher(w, ax, y, az, bx, y, az, m);
        encher(w, ax, y, bz, bx, y, bz, m);
        encher(w, ax, y, az, ax, y, bz, m);
        encher(w, bx, y, az, bx, y, bz, m);
    }

    private static void encher(World w, int ax, int ay, int az, int bx, int by, int bz, Material m) {
        for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
            for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++) {
                for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) w.getBlockAt(x, y, z).setType(m, false);
            }
        }
    }
}
