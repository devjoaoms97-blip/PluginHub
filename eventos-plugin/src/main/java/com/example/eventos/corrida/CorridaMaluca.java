package com.example.eventos.corrida;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import com.example.eventos.util.Formato;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Corrida Maluca: uma pista de obstáculos sem kit, só correr e pular. São 5 mapas (ver {@link MapasCorrida}),
 * e a cada corrida um é sorteado. Quem cai na água volta pro último checkpoint (ninguém sai por cair); os 3
 * primeiros a cruzar a chegada levam os cheques de {@code premios} e vão pra arquibancada da chegada,
 * assistir quem vem atrás. Acaba quando o pódio fecha, quando todos chegam ou no tempo máximo (o prêmio de
 * quem não chegou fica sem pagar); o tempo que falta fica numa barra no alto da tela. Na preparação, uma
 * contagem regressiva (com som) até o portão de vidro abrir. Os obstáculos (ver {@link ArenaCorrida}) são
 * todos do plugin, sem redstone: as portas que abrem são sorteadas a cada corrida, as plataformas somem em
 * ciclo, o trampolim, o pula-pula, o tapete e o carrossel mudam a velocidade de quem pisa, as barras e os
 * martelos são blocos de exibição que giram (ou balançam) e empurram, e os canhões soltam carga de vento.
 */
public class CorridaMaluca extends Evento {

    private static final String TAG = "eventos-corrida";
    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    /** Meia espessura da barra e meia largura do jogador. */
    private static final double MEIA_BARRA = 0.25, MEIO = 0.3;
    /** A barra fica um pouco acima do piso, com essa espessura: é na altura do pé, dá pra pular. */
    private static final float ALTURA_BARRA = 0.15f, ESPESSURA = 0.5f;
    /** A cabeça do martelo: meia largura (x, y) e meia espessura (z). */
    private static final double MEIA_CABECA = 1, MEIA_CABECA_Z = 0.75;
    /** Os últimos segundos da contagem, que aparecem em número grande na tela. */
    private static final int CONTAGEM_GRANDE = 5;

    /** Os mapas construídos (um por mapa ligado em "mapas") e o da corrida de agora. */
    private final List<ArenaCorrida> arenas = new ArrayList<>();
    private ArenaCorrida arena;
    private final List<UUID> chegada = new ArrayList<>();
    private final Map<UUID, Integer> checkpoints = new HashMap<>();
    private final Map<UUID, Long> esperaEmpurrao = new HashMap<>();
    private final Map<UUID, Long> esperaAviso = new HashMap<>();
    /** As portas que abrem nesta corrida (sorteadas) e as que já abriram. */
    private final Set<ArenaCorrida.Porta> verdadeiras = new HashSet<>(), abertas = new HashSet<>();
    /** As barras: uma por braço de cada pivô. */
    private final List<BlockDisplay> barras = new ArrayList<>();
    private final List<Integer> pivoDaBarra = new ArrayList<>(), bracoDaBarra = new ArrayList<>();
    private double[] angulos = new double[0];
    /** Os martelos (o cabo e a cabeça de cada um) e os raios de cada carrossel. */
    private final List<BlockDisplay[]> martelos = new ArrayList<>();
    private final List<BlockDisplay[]> raios = new ArrayList<>();
    private double[] angulosCarrossel = new double[0];
    /** O estado de cada grupo de plataformas, por fase. */
    private Material[][] estadoGrupo = new Material[0][0];
    private long ticksRapidos;
    private BukkitTask contagem;
    /** A barra do tempo que falta, e quem está vendo. */
    private final BossBar tempo = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> vendoTempo = new HashSet<>();

    public CorridaMaluca(EventosPlugin plugin) {
        super(plugin, "corrida");
        recarregar();
    }

    @Override
    public String nome() {
        return "Corrida Maluca";
    }

    @Override
    public boolean feminino() {
        return true;
    }

    @Override
    protected String titulo() {
        return "§e§l⚑ CORRIDA MALUCA ⚑";
    }

    @Override
    protected String cor() {
        return "§e";
    }

    @Override
    public String rotulo() {
        return "§e⚑ Corrida Maluca";
    }

    @Override
    public String descricao() {
        return "Uma pista de obstáculos em 3 fases, num dos " + arenas.size() + " mapas (sorteado a cada corrida): portas falsas, trampolim, gelo, plataformas que somem, tapete rolante, pula-pula, barras giratórias, martelos, carrossel, parkour e canhões de vento. Caiu na água, volta pro último checkpoint.";
    }

    private List<Double> premios() {
        return cfg().getDoubleList("premios");
    }

    @Override
    public double premio() {
        return premios().stream().mapToDouble(v -> Math.max(0, v)).sum();
    }

    @Override
    public String textoPremio() {
        List<String> valores = premios().stream().map(Formato::valor).toList();
        String lista = valores.size() <= 1 ? String.join("", valores)
                : String.join(", ", valores.subList(0, valores.size() - 1)) + " e " + valores.get(valores.size() - 1);
        return "Os " + valores.size() + " primeiros a chegar levam cheques de §f" + lista + "§7.";
    }

    @Override
    public String detalheLuta() {
        return chegada.size() + " na chegada, no mapa " + arena.nome();
    }

    /**
     * Os mapas ligados em "mapas" (todos, se a lista estiver vazia), cada um no seu lugar. Com a corrida
     * rolando, o mapa dela continua (o sorteio fica pra próxima).
     */
    @Override
    public void recarregar() {
        String atual = arena == null || (getFase() != Fase.PREPARACAO && getFase() != Fase.LUTA) ? null : arena.id();
        List<String> ligados = cfg().getStringList("mapas");
        arenas.clear();
        for (int i = 0; i < MapasCorrida.TODOS.size(); i++) {
            MapasCorrida.Mapa m = MapasCorrida.TODOS.get(i);
            if (ligados.isEmpty() || ligados.contains(m.id()) || m.id().equals(atual)) {
                arenas.add(new ArenaCorrida(cfg().getConfigurationSection("arena"), m, i));
            }
        }
        if (arenas.isEmpty()) arenas.add(new ArenaCorrida(cfg().getConfigurationSection("arena"), MapasCorrida.TODOS.get(0), 0));
        arena = null;
        if (atual != null) for (ArenaCorrida a : arenas) if (a.id().equals(atual)) arena = a;
        if (arena == null) sortearMapa();
    }

    /** O mapa da próxima corrida: o "mapa-fixo" (pros testes) ou um sorteado. */
    private void sortearMapa() {
        String fixo = cfg().getString("mapa-fixo", "");
        ArenaCorrida escolhida = null;
        for (ArenaCorrida a : arenas) if (a.id().equalsIgnoreCase(fixo)) escolhida = a;
        arena = escolhida != null ? escolhida : arenas.get(ThreadLocalRandom.current().nextInt(arenas.size()));
    }

    public ArenaCorrida getArena() {
        return arena;
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

    /** Quem cruza a chegada vai pra arquibancada da chegada, assistir quem vem atrás. */
    @Override
    protected Location localDeQuemSai() {
        return arena.chegados();
    }

    @Override
    public boolean caiu(Location l) {
        return arena.caiu(l);
    }

    /**
     * Os 5 mapas (os ligados), um do lado do outro: um a cada 2 segundos (todos de uma vez travariam o
     * servidor por muito tempo), com um aviso no console a cada um.
     */
    @Override
    public void construir() {
        List<ArenaCorrida> fila = new ArrayList<>(arenas);
        for (int i = 0; i < fila.size(); i++) {
            ArenaCorrida a = fila.get(i);
            int n = i + 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                a.construir();
                plugin.getLogger().info("Corrida Maluca: mapa " + a.nome() + " construído (" + n + " de " + fila.size() + ").");
                if (n == fila.size() && getFase() != Fase.PREPARACAO && getFase() != Fase.LUTA) resetar();
            }, i * 40L);
        }
    }

    /**
     * Sorteia o mapa da próxima corrida e deixa ele pronto: o portão, as portas (com um sorteio novo) e as
     * plataformas de novo; sem as peças que se mexem e sem a barra do tempo.
     */
    @Override
    protected void resetar() {
        pararContagem();
        esconderTempo();
        chegada.clear();
        checkpoints.clear();
        esperaEmpurrao.clear();
        esperaAviso.clear();
        abertas.clear();
        removerPecas();
        arena.segurarChunks(plugin, false);
        sortearMapa();
        arena.refazer();
        estadoGrupo = new Material[arena.fases()][ArenaCorrida.GRUPOS.length];
        sortearPortas();
    }

    /** A caixa de todos os mapas juntos (pro AreasPlugin). */
    @Override
    public int[][] caixa() {
        int[][] c = arenas.get(0).caixa();
        for (ArenaCorrida a : arenas) {
            int[][] d = a.caixa();
            for (int i = 0; i < 3; i++) {
                c[0][i] = Math.min(c[0][i], d[0][i]);
                c[1][i] = Math.max(c[1][i], d[1][i]);
            }
        }
        return c;
    }

    @Override
    protected List<Location> pontosDeEntrada(int quantos) {
        return arena.pontosDeEntrada();
    }

    /** Sem kit: só correr e pular. */
    @Override
    protected void darKit(Player p) {
    }

    /** Ninguém sai por cair: a corrida acaba no pódio, com todos na chegada ou no tempo. */
    @Override
    protected boolean acabaComUmDePe() {
        return false;
    }

    /** Pelo tempo ou porque todos chegaram: paga quem cruzou a chegada, na ordem. */
    @Override
    protected void finalizar(List<UUID> ganhadores) {
        esconderTempo();
        finalizarPorColocacao(new ArrayList<>(chegada), premios());
    }

    // ---------------------------------------------------------------- largada

    /**
     * Todos na largada: a pista inteira fica carregada, as barras, os martelos e o carrossel aparecem
     * (parados), o mapa é anunciado e começa a contagem regressiva até o portão abrir.
     */
    @Override
    protected void aoComecarPreparacao(int segundos) {
        ticksRapidos = 0;
        arena.segurarChunks(plugin, true);
        removerPecas();
        criarBarras();
        criarMartelos();
        criarCarrosseis();
        anunciar(prefixo() + " " + cor() + "Mapa: §f" + arena.nome() + cor() + ". Os 3 primeiros levam os cheques!");
        pararContagem();
        int[] falta = {segundos};
        contagem = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (getFase() != Fase.PREPARACAO || falta[0] <= 0) {
                pararContagem();
                return;
            }
            contar(falta[0]--);
        }, 0L, 20L);
    }

    /**
     * Um segundo da contagem: antes dos últimos, na barra de ação, com um tique; nos últimos, o número
     * grande na tela e um "plim" que fica mais agudo a cada segundo.
     */
    private void contar(int n) {
        for (Player p : jogadoresVivos()) {
            if (n > CONTAGEM_GRANDE) {
                barra(p, cor() + "O portão abre em §f" + n + cor() + " s");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1f);
                continue;
            }
            String numero = (n == 1 ? "§c" : n <= 3 ? "§6" : "§e") + "§l" + n;
            p.showTitle(Title.title(LEGADO.deserialize(numero), LEGADO.deserialize("§7Prepare-se!"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.7f + 0.15f * (CONTAGEM_GRANDE - n));
        }
    }

    private void pararContagem() {
        if (contagem != null) contagem.cancel();
        contagem = null;
    }

    /** O "JÁ!": o portão de vidro abre, com fogos, e aparece a barra do tempo. */
    @Override
    protected void aoComecarLuta(long agora) {
        pararContagem();
        ticksRapidos = 0;
        arena.abrirPortao();
        for (Player p : jogadoresVivos()) {
            p.showTitle(Title.title(LEGADO.deserialize("§a§lJÁ!"), LEGADO.deserialize("§fCorra!"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
        }
        atualizarTempo(agora);
    }

    /** Caiu na água: volta pro último checkpoint que passou. */
    @Override
    public void aoCair(Player p) {
        if (lutando(p.getUniqueId()) == null) return;
        teleportar(p, arena.checkpoint(checkpoints.getOrDefault(p.getUniqueId(), 0)));
        p.setVelocity(new Vector());
        p.setFallDistance(0);
        p.setFireTicks(0);
        barra(p, "§cCaiu! §7De volta ao checkpoint.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1f);
    }

    // ---------------------------------------------------------------- o tempo que falta

    /**
     * A barra no alto da tela, pra quem corre, quem já chegou e quem assiste: o tempo que falta (esvaziando)
     * e quantos já chegaram; no último minuto, vermelha.
     */
    private void atualizarTempo(long agora) {
        long maximo = Math.max(1, cfg().getLong("luta.tempo-maximo-minutos")) * 60_000L;
        long falta = Math.max(0, maximo - (agora - inicioLuta()));
        long s = (falta + 999) / 1000;
        tempo.name(LEGADO.deserialize(cor() + "⚑ Corrida Maluca §7· §f" + s / 60 + ":" + String.format("%02d", s % 60) + " §7restantes · §f"
                + chegada.size() + "/" + premios().size() + " §7na chegada"));
        tempo.progress((float) Math.max(0, Math.min(1, (double) falta / maximo)));
        tempo.color(falta <= 60_000 ? BossBar.Color.RED : BossBar.Color.YELLOW);
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

    // ---------------------------------------------------------------- config por fase

    /** O valor da fase (a posição na lista do config), ou o último da lista, ou o número sozinho. */
    private double valor(String caminho, int fase, double padrao) {
        if (!cfg().isList(caminho)) return cfg().getDouble(caminho, padrao);
        List<Double> lista = cfg().getDoubleList(caminho);
        return lista.isEmpty() ? padrao : lista.get(Math.max(0, Math.min(fase, lista.size() - 1)));
    }

    // ---------------------------------------------------------------- a cada 2 ticks

    @Override
    protected void tickRapidoLuta(long agora) {
        ticksRapidos++;
        girarBarras();
        balancarMartelos();
        girarCarrosseis();
        for (Player p : jogadoresVivos()) {
            Location l = p.getLocation();
            if (arena.chegou(l)) {
                chegar(p, agora);
                if (getFase() != Fase.LUTA) return;
                continue;
            }
            int cp = arena.checkpointEm(l);
            if (cp > checkpoints.getOrDefault(p.getUniqueId(), 0) && l.getY() >= arena.piso() + 0.5) {
                checkpoints.put(p.getUniqueId(), cp);
                barra(p, "§a✔ Checkpoint " + cp + " de " + arena.checkpoints() + "!");
                p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
            Block embaixo = l.clone().subtract(0, 0.2, 0).getBlock();
            TracadoCorrida.Tapete tapete = arena.tapete(embaixo);
            double trampolim = arena.alvoDoTrampolim(embaixo), quica = arena.alvoDoQuica(embaixo);
            if (!Double.isNaN(trampolim) && pode(esperaEmpurrao, p, 500)) {
                pular(p, l, trampolim, "trampolim");
                p.playSound(l, Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 1.4f);
            } else if (!Double.isNaN(quica) && pode(esperaEmpurrao, p, 500)) {
                pular(p, l, quica, "pula-pula");
                p.playSound(l, Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 1.8f);
            } else if (tapete != null && p.isOnGround()
                    && ticksRapidos % Math.max(1, (int) valor("tapete.a-cada-ticks", tapete.fase(), 10) / 2) == 0) {
                p.setVelocity(new Vector(0, 0, -valor("tapete.empurrao", tapete.fase(), 0.3)));
            }
            portas(p, l);
            barras(p, l);
            martelos(p, l);
            carrosseis(p, l);
        }
    }

    /**
     * O slime joga pra frente mirando: com o mesmo impulso pra cima, o pulo vai longe na proporção da
     * velocidade pra frente (a distância que falta até o alvo vezes "frente-por-bloco"). Com força fixa, o
     * pulo variava conforme o ponto do slime em que o jogador encostava.
     */
    private void pular(Player p, Location l, double alvo, String secao) {
        double falta = Math.max(1, alvo - l.getZ());
        p.setVelocity(new Vector(0, cfg().getDouble(secao + ".impulso-cima"), falta * cfg().getDouble(secao + ".frente-por-bloco", 0.105)));
    }

    /** Encostou numa porta: a verdadeira some (pra todos); a falsa só faz barulho. */
    private void portas(Player p, Location l) {
        for (ArenaCorrida.Porta porta : arena.portas()) {
            if (abertas.contains(porta) || l.getZ() < porta.z() - 0.6 || l.getZ() >= porta.z() + 1
                    || l.getX() < porta.x0() - MEIO || l.getX() > porta.x0() + 3 + MEIO || l.getY() >= arena.piso() + 4) continue;
            if (verdadeiras.contains(porta)) {
                abertas.add(porta);
                arena.porta(porta, Material.AIR);
                l.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            } else if (pode(esperaAviso, p, 1000)) {
                p.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                barra(p, "§cPorta falsa! §7Tente outra.");
            }
        }
    }

    private void sortearPortas() {
        verdadeiras.clear();
        int abrem = Math.max(1, cfg().getInt("portas.abertas-por-fileira"));
        Map<Integer, List<ArenaCorrida.Porta>> porFileira = new HashMap<>();
        for (ArenaCorrida.Porta p : arena.portas()) porFileira.computeIfAbsent(p.fileira(), f -> new ArrayList<>()).add(p);
        for (List<ArenaCorrida.Porta> fileira : porFileira.values()) {
            Collections.shuffle(fileira);
            verdadeiras.addAll(fileira.subList(0, Math.min(abrem, fileira.size())));
        }
    }

    // ---------------------------------------------------------------- peças que se mexem

    private BlockDisplay peca(Location l, Material m) {
        return l.getWorld().spawn(l, BlockDisplay.class, e -> {
            e.setBlock(m.createBlockData());
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
        });
    }

    private void removerPecas() {
        barras.forEach(BlockDisplay::remove);
        barras.clear();
        pivoDaBarra.clear();
        bracoDaBarra.clear();
        for (BlockDisplay[] m : martelos) for (BlockDisplay d : m) d.remove();
        martelos.clear();
        for (BlockDisplay[] r : raios) for (BlockDisplay d : r) d.remove();
        raios.clear();
        angulos = new double[0];
        angulosCarrossel = new double[0];
        World w = mundo();
        if (w != null) w.getEntitiesByClass(BlockDisplay.class).stream().filter(d -> d.getScoreboardTags().contains(TAG)).forEach(BlockDisplay::remove);
    }

    /** Um bloco esticado, girado em volta do ponto da entidade: o canto já vai girado. */
    private static void posicionar(BlockDisplay d, Quaternionf giro, Vector3f canto, Vector3f tamanho, int ticks) {
        if (!d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(new Transformation(giro.transform(new Vector3f(canto)), giro, tamanho, new Quaternionf()));
    }

    // ---------------------------------------------------------------- barras giratórias

    private void criarBarras() {
        if (mundo() == null) return;
        List<TracadoCorrida.Pivo> pivos = arena.pivos();
        angulos = new double[pivos.size()];
        for (int i = 0; i < pivos.size(); i++) {
            for (int k = 0; k < bracos(i); k++) {
                barras.add(peca(arena.centro(pivos.get(i)), k % 2 == 0 ? arena.tema().pecaA() : arena.tema().pecaB()));
                pivoDaBarra.add(i);
                bracoDaBarra.add(k);
            }
        }
        atualizarBarras(0);
    }

    private int bracos(int pivo) {
        return Math.max(1, (int) valor("barras.bracos", arena.pivos().get(pivo).fase(), 1));
    }

    /** Quanto cada pivô gira em 2 ticks (radianos); o segundo de um par gira pro outro lado. */
    private double passo(int pivo) {
        TracadoCorrida.Pivo p = arena.pivos().get(pivo);
        return Math.toRadians(valor("barras.graus-por-segundo", p.fase(), 60)) / 10 * (p.inverso() ? -1 : 1);
    }

    private void girarBarras() {
        for (int i = 0; i < angulos.length; i++) angulos[i] += passo(i);
        atualizarBarras(2);
    }

    /** O ângulo de um braço: os braços de um pivô ficam igualmente espaçados (2 = uma cruz). */
    private double angulo(int pivo, int braco) {
        return angulos[pivo] + braco * Math.PI / bracos(pivo);
    }

    /** Cada barra é um bloco esticado (2 x raio), centrado no pivô e girado; o cliente suaviza o giro. */
    private void atualizarBarras(int ticks) {
        float raio = ArenaCorrida.RAIO;
        for (int b = 0; b < barras.size(); b++) {
            Quaternionf giro = new Quaternionf().rotateY((float) angulo(pivoDaBarra.get(b), bracoDaBarra.get(b)));
            posicionar(barras.get(b), giro, new Vector3f(-raio, ALTURA_BARRA, -ESPESSURA / 2), new Vector3f(2 * raio, ESPESSURA, ESPESSURA), ticks);
        }
    }

    /**
     * Uma barra pegou o jogador (no ângulo de agora ou no do meio do passo, pra não atravessar ninguém entre
     * um tique e outro) com o pé abaixo do topo dela: empurrado pro lado em que ela gira, e pra cima.
     */
    private void barras(Player p, Location l) {
        List<TracadoCorrida.Pivo> pivos = arena.pivos();
        for (int i = 0; i < pivos.size() && i < angulos.length; i++) {
            Location c = arena.centro(pivos.get(i));
            double rx = l.getX() - c.getX(), rz = l.getZ() - c.getZ();
            double base = c.getY() + ALTURA_BARRA;
            if (Math.hypot(rx, rz) > ArenaCorrida.RAIO + MEIO || l.getY() >= base + ESPESSURA || l.getY() + 1.8 <= base) continue;
            for (int k = 0; k < bracos(i); k++) {
                for (double recuo : new double[]{0, passo(i) / 2}) {
                    double a = angulo(i, k) - recuo, dx = Math.cos(a), dz = -Math.sin(a);
                    double ao = rx * dx + rz * dz, perto = Math.abs(rx * dz - rz * dx);
                    if (Math.abs(ao) > ArenaCorrida.RAIO + MEIO || perto > MEIA_BARRA + MEIO || !pode(esperaEmpurrao, p, 500)) continue;
                    // a ponta da barra anda em (-sen, -cos) quando o ângulo cresce; do outro lado do pivô, ao contrário
                    Vector lado = new Vector(-Math.sin(a), 0, -Math.cos(a)).multiply(Math.signum(ao) * Math.signum(passo(i)));
                    p.setVelocity(lado.multiply(cfg().getDouble("barras.forca")).setY(0.5));
                    p.playSound(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- martelos de pêndulo

    private void criarMartelos() {
        if (mundo() == null) return;
        for (TracadoCorrida.Martelo m : arena.martelos()) {
            Location eixo = arena.eixo(m);
            martelos.add(new BlockDisplay[]{peca(eixo, arena.tema().pecaB()), peca(eixo, arena.tema().pecaA())});
        }
        balancarMartelos();
    }

    /** O ângulo do martelo nesse tick (0 = pendurado reto; positivo = a cabeça pro leste, +x). */
    private double anguloMartelo(TracadoCorrida.Martelo m, long ticks) {
        double periodo = Math.max(0.5, valor("martelos.periodo-segundos", m.fase(), 3)) * 20;
        return Math.toRadians(cfg().getDouble("martelos.angulo-graus", 55)) * Math.sin(2 * Math.PI * ticks / periodo + m.desencontro());
    }

    private void balancarMartelos() {
        float braco = (float) ArenaCorrida.BRACO_MARTELO;
        List<TracadoCorrida.Martelo> lista = arena.martelos();
        for (int i = 0; i < lista.size() && i < martelos.size(); i++) {
            Quaternionf giro = new Quaternionf().rotateZ((float) anguloMartelo(lista.get(i), ticksRapidos * 2));
            posicionar(martelos.get(i)[0], giro, new Vector3f(-0.15f, -braco, -0.15f), new Vector3f(0.3f, braco, 0.3f), 2);
            posicionar(martelos.get(i)[1], giro, new Vector3f(-1f, -braco - 1f, -0.75f), new Vector3f(2f, 2f, 1.5f), 2);
        }
    }

    /** A cabeça de um martelo pegou o jogador (agora ou um tique antes): vai pro lado em que ela balança. */
    private void martelos(Player p, Location l) {
        for (TracadoCorrida.Martelo m : arena.martelos()) {
            Location eixo = arena.eixo(m);
            if (Math.abs(l.getZ() - eixo.getZ()) > MEIA_CABECA_Z + MEIO) continue;
            for (long t : new long[]{ticksRapidos * 2, ticksRapidos * 2 - 1}) {
                double a = anguloMartelo(m, t);
                double hx = eixo.getX() + ArenaCorrida.BRACO_MARTELO * Math.sin(a), hy = eixo.getY() - ArenaCorrida.BRACO_MARTELO * Math.cos(a);
                if (Math.abs(l.getX() - hx) > MEIA_CABECA + MEIO || l.getY() > hy + MEIA_CABECA || l.getY() + 1.8 < hy - MEIA_CABECA
                        || !pode(esperaEmpurrao, p, 500)) continue;
                double indo = Math.signum(anguloMartelo(m, t + 1) - a);
                if (indo == 0) indo = Math.signum(l.getX() - hx);
                p.setVelocity(new Vector(indo * cfg().getDouble("martelos.forca", 1.0), 0.4, 0));
                p.playSound(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- carrossel

    private void criarCarrosseis() {
        if (mundo() == null) return;
        List<TracadoCorrida.Carrossel> lista = arena.carrosseis();
        angulosCarrossel = new double[lista.size()];
        for (TracadoCorrida.Carrossel c : lista) {
            Location centro = arena.centro(c);
            raios.add(new BlockDisplay[]{peca(centro, arena.tema().pisos()[2]), peca(centro, arena.tema().pisos()[0])});
        }
        posicionarRaios(0);
    }

    /** O giro do carrossel em 2 ticks (radianos). */
    private double passoCarrossel(TracadoCorrida.Carrossel c) {
        return Math.toRadians(valor("carrossel.graus-por-segundo", c.fase(), 45)) / 10;
    }

    private void girarCarrosseis() {
        List<TracadoCorrida.Carrossel> lista = arena.carrosseis();
        for (int i = 0; i < angulosCarrossel.length && i < lista.size(); i++) angulosCarrossel[i] += passoCarrossel(lista.get(i));
        posicionarRaios(2);
    }

    /** Os raios (enfeite, rentes ao piso) giram junto, pra mostrar pra onde o carrossel leva. */
    private void posicionarRaios(int ticks) {
        float raio = ArenaCorrida.RAIO;
        for (int i = 0; i < raios.size() && i < angulosCarrossel.length; i++) {
            for (int k = 0; k < raios.get(i).length; k++) {
                Quaternionf giro = new Quaternionf().rotateY((float) (angulosCarrossel[i] + k * Math.PI / 2));
                posicionar(raios.get(i)[k], giro, new Vector3f(-raio, 0.01f, -0.2f), new Vector3f(2 * raio, 0.06f, 0.4f), ticks);
            }
        }
    }

    /**
     * Em cima de um carrossel (no chão): leva o jogador junto no giro e o empurra um pouco pra fora. A cada 2
     * ticks, a velocidade da volta naquele raio (com o atrito do chão, o empurrão de 1,29x dá o passo certo).
     */
    private void carrosseis(Player p, Location l) {
        if (!p.isOnGround()) return;
        for (TracadoCorrida.Carrossel car : arena.carrosseis()) {
            Location c = arena.centro(car);
            double rx = l.getX() - c.getX(), rz = l.getZ() - c.getZ(), r = Math.hypot(rx, rz);
            if (r > ArenaCorrida.RAIO + MEIO || r < 0.5 || l.getY() < c.getY() - 0.2 || l.getY() > c.getY() + 0.6) continue;
            double porTick = passoCarrossel(car) / 2;
            // um ponto em (cos a, -sen a) * r anda em (rz, -rx) * a' quando o ângulo cresce (como as barras)
            Vector giro = new Vector(rz, 0, -rx).multiply(1.29 * porTick);
            Vector fora = new Vector(rx / r, 0, rz / r).multiply(valor("carrossel.para-fora", car.fase(), 0.04));
            p.setVelocity(giro.add(fora));
            return;
        }
    }

    // ---------------------------------------------------------------- a cada segundo

    @Override
    protected void tickLuta(long agora) {
        long s = (agora - inicioLuta()) / 1000;
        plataformas(s);
        int intervalo = Math.max(1, cfg().getInt("vento.a-cada-segundos"));
        List<ArenaCorrida.Canhao> canhoes = arena.canhoes();
        for (int i = 0; i < canhoes.size(); i++) {
            if ((s + i) % intervalo == 0) atirar(canhoes.get(i));
        }
        atualizarTempo(agora);
    }

    /**
     * Os dois grupos de plataformas de cada fase alternam: firme, aviso (vermelho) e sumida, meio ciclo um
     * do outro. Cada fase tem o seu tempo.
     */
    private void plataformas(long s) {
        for (int f = 0; f < estadoGrupo.length; f++) {
            int firme = Math.max(1, (int) valor("plataformas.firme-segundos", f, 3));
            int aviso = Math.max(0, (int) valor("plataformas.aviso-segundos", f, 1));
            int ciclo = firme + aviso + Math.max(1, (int) valor("plataformas.sumida-segundos", f, 2));
            for (int g = 0; g < estadoGrupo[f].length; g++) {
                long t = (s + (long) g * ciclo / 2) % ciclo;
                Material m = t < firme ? ArenaCorrida.GRUPOS[g] : t < firme + aviso ? ArenaCorrida.AVISO : Material.AIR;
                if (m == estadoGrupo[f][g]) continue;
                estadoGrupo[f][g] = m;
                arena.plataformas(f, g, m);
            }
        }
    }

    private void atirar(ArenaCorrida.Canhao canhao) {
        Location origem = canhao.origem();
        if (origem.getWorld() == null) return;
        origem.getWorld().spawn(origem, WindCharge.class, c -> {
            c.setVelocity(canhao.direcao().clone().multiply(cfg().getDouble("vento.velocidade")));
            c.addScoreboardTag(TAG);
        });
        origem.getWorld().playSound(origem, Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
    }

    // ---------------------------------------------------------------- chegada

    private void chegar(Player p, long agora) {
        UUID id = p.getUniqueId();
        if (chegada.contains(id)) return;
        chegada.add(id);
        anunciar(prefixo() + " §f" + p.getName() + " " + cor() + "cruzou a chegada em §f" + chegada.size() + "º" + cor() + "! §7("
                + Formato.tempo(agora - inicioLuta()) + ")");
        retirar(p);
        if (getFase() == Fase.LUTA && chegada.size() >= premios().size()) finalizar(List.of());
        else if (getFase() == Fase.LUTA) atualizarTempo(agora);
    }

    private static boolean pode(Map<UUID, Long> espera, Player p, long ms) {
        long agora = System.currentTimeMillis();
        if (agora - espera.getOrDefault(p.getUniqueId(), 0L) < ms) return false;
        espera.put(p.getUniqueId(), agora);
        return true;
    }

    private static void barra(Player p, String texto) {
        p.sendActionBar(LEGADO.deserialize(texto));
    }
}
