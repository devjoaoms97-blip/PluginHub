package com.example.eventos.corrida;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import com.example.eventos.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Corrida Maluca: uma pista de obstáculos sem kit, só correr e pular. Quem cai na água volta pro último
 * checkpoint (ninguém sai por cair); os 3 primeiros a cruzar a chegada levam os cheques de {@code premios}.
 * Acaba quando o pódio fecha, quando todos chegam ou no tempo máximo (o prêmio de quem não chegou fica sem
 * pagar). Os obstáculos (ver {@link ArenaCorrida}) são todos do plugin, sem redstone: as portas que abrem
 * são sorteadas a cada corrida, as plataformas somem em ciclo, o trampolim e o tapete mudam a velocidade
 * de quem pisa, as barras são blocos de exibição que giram e empurram, e os canhões soltam carga de vento.
 */
public class CorridaMaluca extends Evento {

    private static final String TAG = "eventos-corrida";
    /** Meia espessura da barra e meia largura do jogador. */
    private static final double MEIA_BARRA = 0.25, MEIO = 0.3;
    /** A barra fica um pouco acima do piso, com essa espessura: é na altura do pé, dá pra pular. */
    private static final float ALTURA_BARRA = 0.15f, ESPESSURA = 0.5f;

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
    private final Material[] estadoGrupo = new Material[ArenaCorrida.GRUPOS.length];
    private long ticksRapidos;

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
        return "Uma pista de obstáculos no parque: portas falsas, trampolim, gelo, plataformas que somem, tapete rolante, barras giratórias e canhões de vento. Caiu na água, volta pro último checkpoint.";
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
        return chegada.size() + " na chegada";
    }

    @Override
    public void recarregar() {
        this.arena = new ArenaCorrida(cfg().getConfigurationSection("arena"));
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

    @Override
    public boolean caiu(Location l) {
        return arena.caiu(l);
    }

    @Override
    public void construir() {
        arena.construir();
        resetar();
    }

    /** O portão, as portas (com um sorteio novo) e as plataformas de novo; as barras paradas no começo. */
    @Override
    protected void resetar() {
        chegada.clear();
        checkpoints.clear();
        esperaEmpurrao.clear();
        esperaAviso.clear();
        abertas.clear();
        arena.refazer();
        java.util.Arrays.fill(estadoGrupo, null);
        sortearPortas();
        removerBarras();
        criarBarras();
    }

    @Override
    public int[][] caixa() {
        return arena.caixa();
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
        finalizarPorColocacao(new ArrayList<>(chegada), premios());
    }

    @Override
    protected void aoComecarLuta(long agora) {
        ticksRapidos = 0;
        arena.abrirPortao();
        for (Player p : jogadoresVivos()) p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
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

    // ---------------------------------------------------------------- a cada 2 ticks

    @Override
    protected void tickRapidoLuta(long agora) {
        ticksRapidos++;
        girarBarras();
        int aCada = Math.max(1, cfg().getInt("tapete.a-cada-ticks") / 2);
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
                barra(p, "§a✔ Checkpoint " + cp + "!");
                p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
            Block embaixo = l.clone().subtract(0, 0.2, 0).getBlock();
            if (arena.ehTrampolim(embaixo) && pode(esperaEmpurrao, p, 500)) {
                p.setVelocity(new Vector(0, cfg().getDouble("trampolim.impulso-cima"), cfg().getDouble("trampolim.impulso-frente")));
                p.playSound(l, Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 1.4f);
            } else if (arena.ehTapete(embaixo) && ticksRapidos % aCada == 0 && p.isOnGround()) {
                p.setVelocity(new Vector(0, 0, -cfg().getDouble("tapete.empurrao")));
            }
            portas(p, l);
            barras(p, l);
        }
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

    // ---------------------------------------------------------------- barras giratórias

    private void criarBarras() {
        World w = mundo();
        if (w == null) return;
        List<Location> pivos = arena.pivos();
        List<Integer> bracos = cfg().getIntegerList("barras.bracos");
        angulos = new double[pivos.size()];
        barras.clear();
        pivoDaBarra.clear();
        bracoDaBarra.clear();
        for (int i = 0; i < pivos.size(); i++) {
            int n = Math.max(1, i < bracos.size() ? bracos.get(i) : 1);
            for (int k = 0; k < n; k++) {
                int pivo = i, braco = k;
                BlockDisplay d = w.spawn(pivos.get(i), BlockDisplay.class, e -> {
                    e.setBlock((braco % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE).createBlockData());
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setPersistent(false);
                    e.addScoreboardTag(TAG);
                });
                barras.add(d);
                pivoDaBarra.add(pivo);
                bracoDaBarra.add(braco);
            }
        }
        atualizarBarras(0);
    }

    private void removerBarras() {
        barras.forEach(BlockDisplay::remove);
        barras.clear();
        World w = mundo();
        if (w != null) w.getEntitiesByClass(BlockDisplay.class).stream().filter(d -> d.getScoreboardTags().contains(TAG)).forEach(BlockDisplay::remove);
    }

    private int bracos(int pivo) {
        List<Integer> bracos = cfg().getIntegerList("barras.bracos");
        return Math.max(1, pivo < bracos.size() ? bracos.get(pivo) : 1);
    }

    /** Quanto cada pivô gira em 2 ticks (radianos). */
    private double passo(int pivo) {
        List<Integer> graus = cfg().getIntegerList("barras.graus-por-segundo");
        return Math.toRadians(pivo < graus.size() ? graus.get(pivo) : 60) / 10;
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
            BlockDisplay d = barras.get(b);
            if (!d.isValid()) continue;
            Quaternionf giro = new Quaternionf().rotateY((float) angulo(pivoDaBarra.get(b), bracoDaBarra.get(b)));
            Vector3f deslocamento = giro.transform(new Vector3f(-raio, ALTURA_BARRA, -ESPESSURA / 2));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(deslocamento, giro, new Vector3f(2 * raio, ESPESSURA, ESPESSURA), new Quaternionf()));
        }
    }

    /**
     * Uma barra pegou o jogador (no ângulo de agora ou no do meio do passo, pra não atravessar ninguém entre
     * um tique e outro) com o pé abaixo do topo dela: empurrado pro lado em que ela gira, e pra cima.
     */
    private void barras(Player p, Location l) {
        List<Location> pivos = arena.pivos();
        for (int i = 0; i < pivos.size() && i < angulos.length; i++) {
            Location c = pivos.get(i);
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
    }

    /** Os dois grupos de plataformas alternam: firme, aviso (vermelho) e sumida, meio ciclo um do outro. */
    private void plataformas(long s) {
        int firme = Math.max(1, cfg().getInt("plataformas.firme-segundos")), aviso = Math.max(0, cfg().getInt("plataformas.aviso-segundos"));
        int ciclo = firme + aviso + Math.max(1, cfg().getInt("plataformas.sumida-segundos"));
        for (int g = 0; g < estadoGrupo.length; g++) {
            long t = (s + (long) g * ciclo / 2) % ciclo;
            Material m = t < firme ? ArenaCorrida.GRUPOS[g] : t < firme + aviso ? ArenaCorrida.AVISO : Material.AIR;
            if (m == estadoGrupo[g]) continue;
            estadoGrupo[g] = m;
            arena.plataformas(g, m);
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
    }

    private static boolean pode(Map<UUID, Long> espera, Player p, long ms) {
        long agora = System.currentTimeMillis();
        if (agora - espera.getOrDefault(p.getUniqueId(), 0L) < ms) return false;
        espera.put(p.getUniqueId(), agora);
        return true;
    }

    private static void barra(Player p, String texto) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(texto));
    }
}
