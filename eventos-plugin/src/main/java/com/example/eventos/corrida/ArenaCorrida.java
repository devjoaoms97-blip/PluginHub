package com.example.eventos.corrida;

import com.example.eventos.corrida.TracadoCorrida.Bloco;
import com.example.eventos.corrida.TracadoCorrida.Carrossel;
import com.example.eventos.corrida.TracadoCorrida.Gelo;
import com.example.eventos.corrida.TracadoCorrida.Martelo;
import com.example.eventos.corrida.TracadoCorrida.Pivo;
import com.example.eventos.corrida.TracadoCorrida.Plataforma;
import com.example.eventos.corrida.TracadoCorrida.Ponte;
import com.example.eventos.corrida.TracadoCorrida.Quica;
import com.example.eventos.corrida.TracadoCorrida.Tapete;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Um mapa da Corrida Maluca construído no mundo: um pavilhão comprido e fechado, com a pista de 13 blocos de
 * largura sobre uma piscina, de sul a norte (+z) a partir de {@code inicio} (em x, deslocado pro lado conforme
 * o mapa). O traçado ({@link TracadoCorrida}) diz onde fica cada obstáculo e o tema ({@link TemaCorrida}) as
 * cores. Quem cai na água volta pro checkpoint. Quem assiste fica nas passarelas dos dois lados, em cima das
 * paredes, atrás de um vidro até o teto; quem já chegou, na arquibancada depois da chegada, de frente pra linha.
 * <p>
 * {@link #construir()} faz o pavilhão inteiro (uma vez, pela staff); {@link #refazer()} só o que muda numa
 * corrida (o portão, as portas e as plataformas).
 */
public class ArenaCorrida {

    /** A metade da largura da pista (de -6 a 6). */
    static final int LARGURA = 6;
    /** O raio das barras giratórias, dos discos e do carrossel. */
    static final int RAIO = 6;
    /** O comprimento do braço do martelo (do eixo, no alto, ao meio da cabeça). */
    static final double BRACO_MARTELO = 7.5;

    static final Material[] GRUPOS = {Material.LIME_CONCRETE, Material.PINK_CONCRETE};
    static final Material AVISO = Material.RED_CONCRETE;

    /** Uma porta (3 x 3) de uma fileira: as que abrem somem quando alguém encosta. */
    record Porta(int fileira, int x0, int z) {
    }

    /** Um canhão de vento na parede: de onde sai a carga e pra onde vai. */
    record Canhao(Location origem, Vector direcao) {
    }

    private final MapasCorrida.Mapa mapa;
    private final TemaCorrida tema;
    private final TracadoCorrida t;
    private final String mundo;
    /** O meio da pista em x (o bloco do meio) e o começo em z. */
    private final int cx, z0, agua, piso, galeria, teto;

    public ArenaCorrida(ConfigurationSection cfg, MapasCorrida.Mapa mapa, int indice) {
        this.mapa = mapa;
        this.tema = mapa.tema();
        this.t = mapa.tracado().get();
        this.mundo = cfg.getString("mundo");
        List<Integer> inicio = cfg.getIntegerList("inicio");
        this.cx = (inicio.size() > 0 ? inicio.get(0) : 0) + indice * MapasCorrida.ESPACO;
        this.z0 = inicio.size() > 1 ? inicio.get(1) : 0;
        this.agua = cfg.getInt("agua-y");
        this.piso = agua + 6;
        this.galeria = piso + 10;
        this.teto = galeria + 7;
    }

    public String id() {
        return mapa.id();
    }

    public String nome() {
        return tema.nome();
    }

    public World mundo() {
        return Bukkit.getWorld(mundo);
    }

    public int piso() {
        return piso;
    }

    private int z(int dz) {
        return z0 + dz;
    }

    private int x(int dx) {
        return cx + dx;
    }

    /** O x do meio da pista (o meio do bloco do meio). */
    private double meio() {
        return cx + 0.5;
    }

    // ---------------------------------------------------------------- consultas

    /** A pista, do começo até o vidro da arquibancada da chegada. */
    public boolean naArena(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x(-LARGURA) && l.getX() < x(LARGURA + 1) && l.getZ() >= z0 && l.getZ() < z(t.chegada + TracadoCorrida.ATE_O_VIDRO)
                && l.getY() >= agua - 4 && l.getY() < galeria;
    }

    /** As passarelas dos dois lados, por cima das paredes, e a arquibancada de quem já chegou. */
    public boolean naArquibancada(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
        double x = l.getX(), z = l.getZ(), y = l.getY();
        boolean lado = (x >= x(LARGURA + 2) && x < x(LARGURA + 7)) || (x >= x(-LARGURA - 6) && x < x(-LARGURA - 1));
        if (lado && z >= z0 - 1 && z < z(t.fim) + 1 && y >= galeria && y < teto) return true;
        return naChegada(l);
    }

    /** A arquibancada de quem chegou: depois do vidro, até o fim do pavilhão. */
    public boolean naChegada(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(mundo)
                && l.getX() >= x(-LARGURA) && l.getX() < x(LARGURA + 1)
                && l.getZ() >= z(t.chegada + TracadoCorrida.ATE_O_VIDRO + 1) && l.getZ() < z(t.fim)
                && l.getY() >= piso && l.getY() < galeria;
    }

    public Location arquibancada() {
        return new Location(mundo(), x(LARGURA + 4) + 0.5, galeria + 1, z(20) + 0.5, 90, 30);
    }

    /** Onde vai quem cruza a chegada: no alto da arquibancada, olhando pra linha. */
    public Location chegados() {
        int x = ThreadLocalRandom.current().nextInt(-4, 5);
        return new Location(mundo(), x(x) + 0.5, piso + 5, z(t.chegada + 7) + 0.5, 180, 20);
    }

    public boolean caiu(Location l) {
        return l.getY() < agua + 1.2;
    }

    public int[][] caixa() {
        return new int[][]{{x(-LARGURA - 1), agua - 4, z0 - 1}, {x(LARGURA + 1), teto, z(t.fim)}};
    }

    /** Começo: espalhados na largada, de frente pra pista. */
    public List<Location> pontosDeEntrada() {
        List<Location> pontos = new ArrayList<>();
        for (int x = -5; x <= 5; x += 2) {
            for (int dz = 1; dz <= 5; dz += 2) pontos.add(new Location(mundo(), x(x) + 0.5, piso + 1, z(dz) + 0.5, 0, 0));
        }
        Collections.shuffle(pontos);
        return pontos;
    }

    /** Quantos checkpoints (sem contar a largada). */
    public int checkpoints() {
        return t.checkpoints.size() - 1;
    }

    /** Onde quem caiu volta: no checkpoint, num lugar sorteado da largura. */
    public Location checkpoint(int i) {
        int dz = t.checkpoints.get(Math.max(0, Math.min(i, t.checkpoints.size() - 1))) + (i == 0 ? 3 : 1);
        int x = ThreadLocalRandom.current().nextInt(-4, 5);
        return new Location(mundo(), x(x) + 0.5, piso + 1, z(dz) + 0.5, 0, 0);
    }

    /** O último checkpoint que esse lugar já passou (0 = a largada). */
    public int checkpointEm(Location l) {
        int dz = (int) Math.floor(l.getZ()) - z0;
        for (int i = t.checkpoints.size() - 1; i > 0; i--) if (dz >= t.checkpoints.get(i)) return i;
        return 0;
    }

    public boolean chegou(Location l) {
        return l.getZ() >= z(t.chegada) && l.getY() >= piso + 0.5;
    }

    /** O z do meio do chão depois do vão em que o trampolim desse bloco joga, ou NaN se não é trampolim. */
    public double alvoDoTrampolim(Block b) {
        if (b.getType() != Material.SLIME_BLOCK || b.getY() != piso || !t.trampolins.contains(b.getZ() - z0)) return Double.NaN;
        return b.getZ() + TracadoCorrida.VAO_DO_TRAMPOLIM + 2.5;
    }

    /** O z do meio do lugar em que o pula-pula desse bloco joga, ou NaN se não é do pula-pula. */
    public double alvoDoQuica(Block b) {
        if (b.getType() != Material.SLIME_BLOCK || b.getY() != piso) return Double.NaN;
        int dz = b.getZ() - z0;
        for (Quica q : t.quicas) if (dz >= q.dz0() && dz <= q.dz1()) return z(q.alvo()) + 0.5;
        return Double.NaN;
    }

    /** O tapete rolante desse bloco, ou null. */
    public Tapete tapete(Block b) {
        if (b.getType() != tema.tapete() || b.getY() != piso) return null;
        int dz = b.getZ() - z0;
        for (Tapete tp : t.tapetes) if (dz >= tp.dz0() && dz <= tp.dz1()) return tp;
        return null;
    }

    public List<Porta> portas() {
        List<Porta> portas = new ArrayList<>();
        for (int f = 0; f < t.fileiras.size(); f++) {
            for (int x0 = -5; x0 <= 3; x0 += 4) portas.add(new Porta(f, x(x0), z(t.fileiras.get(f))));
        }
        return portas;
    }

    public List<Pivo> pivos() {
        return t.pivos;
    }

    /** O centro do pivô, no nível de cima do piso. */
    public Location centro(Pivo p) {
        return new Location(mundo(), meio(), piso + 1, z(p.dz()) + 0.5);
    }

    public List<Carrossel> carrosseis() {
        return t.carrosseis;
    }

    /** O centro do carrossel, no nível de cima do piso. */
    public Location centro(Carrossel c) {
        return new Location(mundo(), meio(), piso + 1, z(c.dz()) + 0.5);
    }

    public List<Martelo> martelos() {
        return t.martelos;
    }

    /** O eixo de um martelo, no alto do poço da pista. */
    public Location eixo(Martelo m) {
        return new Location(mundo(), meio(), piso + 1 + BRACO_MARTELO + 1, z(m.dz()) + 0.5);
    }

    public TemaCorrida tema() {
        return tema;
    }

    /** Os canhões, alternando as paredes (o primeiro na do leste), na altura do peito. */
    public List<Canhao> canhoes() {
        List<Canhao> lista = new ArrayList<>();
        for (int i = 0; i < t.canhoes.size(); i++) {
            int lado = i % 2 == 0 ? 1 : -1;
            Vector direcao = new Vector(-lado, 0, 0);
            Location origem = new Location(mundo(), meio() + lado * (LARGURA + 0.1), piso + 1.6, z(t.canhoes.get(i)) + 0.5);
            origem.setDirection(direcao);
            lista.add(new Canhao(origem, direcao));
        }
        return lista;
    }

    /**
     * Segura (ou solta) os chunks da pista carregados: a pista é comprida, e longe dos jogadores as barras,
     * os martelos e o carrossel (entidades que não ficam salvas) sumiriam com o chunk.
     */
    public void segurarChunks(Plugin plugin, boolean segurar) {
        World w = mundo();
        if (w == null) return;
        int fora = LARGURA + 10;
        for (int cxc = x(-fora) >> 4; cxc <= x(fora) >> 4; cxc++) {
            for (int czc = (z0 - 5) >> 4; czc <= (z(t.fim) + 5) >> 4; czc++) {
                if (segurar) w.addPluginChunkTicket(cxc, czc, plugin);
                else w.removePluginChunkTicket(cxc, czc, plugin);
            }
        }
    }

    // ---------------------------------------------------------------- o que muda numa corrida

    /** O portão fechado, todas as portas no lugar e as plataformas firmes. */
    public void refazer() {
        World w = mundo();
        if (w == null) return;
        encher(w, x(-LARGURA), piso + 1, z(TracadoCorrida.PORTAO), x(LARGURA), piso + 3, z(TracadoCorrida.PORTAO), Material.LIGHT_BLUE_STAINED_GLASS);
        for (Porta p : portas()) porta(p, tema.porta());
        for (int f = 0; f < fases(); f++) {
            for (int g = 0; g < GRUPOS.length; g++) plataformas(f, g, GRUPOS[g]);
        }
    }

    /** Quantas fases têm plataformas (o índice da maior + 1). */
    public int fases() {
        return t.plataformas.stream().mapToInt(Plataforma::fase).max().orElse(-1) + 1;
    }

    public void abrirPortao() {
        World w = mundo();
        if (w != null) encher(w, x(-LARGURA), piso + 1, z(TracadoCorrida.PORTAO), x(LARGURA), piso + 3, z(TracadoCorrida.PORTAO), Material.AIR);
    }

    public void porta(Porta p, Material m) {
        World w = mundo();
        if (w != null) encher(w, p.x0(), piso + 1, p.z(), p.x0() + 2, piso + 3, p.z(), m);
    }

    /** As plataformas de um grupo de uma fase, de um material (AIR = sumidas). */
    public void plataformas(int fase, int grupo, Material m) {
        World w = mundo();
        if (w == null) return;
        for (Plataforma p : t.plataformas) {
            if (p.fase() == fase && p.grupo() == grupo) encher(w, x(p.x0()), piso, z(p.dz0()), x(p.x0() + 2), piso, z(p.dz0() + 2), m);
        }
    }

    // ---------------------------------------------------------------- o pavilhão

    private Material vidro(int i) {
        return Material.valueOf(tema.vidros()[Math.floorMod(i, tema.vidros().length)].name().replace("_PANE", ""));
    }

    private Material piso(int i) {
        return tema.pisos()[Math.floorMod(i, tema.pisos().length)];
    }

    public void construir() {
        World w = mundo();
        if (w == null) return;
        int fora = LARGURA + 7, base = agua - 3, zi = z0 - 2, zf = z(t.fim) + 1;
        encher(w, x(-fora - 3), agua - 4, zi - 3, x(fora + 3), teto + 4, zf + 3, Material.AIR);

        // corpo maciço até a passarela, com o poço da pista escavado e a piscina no fundo
        encher(w, x(-fora), base, zi, x(fora), galeria - 1, zf, tema.corpo());
        encher(w, x(-LARGURA), agua - 2, z0, x(LARGURA), galeria - 1, z(t.fim) - 1, Material.AIR);
        encher(w, x(-LARGURA), agua - 2, z0, x(LARGURA), agua, z(t.fim) - 1, Material.WATER);
        for (int x = -LARGURA; x <= LARGURA; x += 4) {
            for (int z = z0 + 1; z < z(t.fim); z += 4) w.getBlockAt(x(x), base, z).setType(Material.SEA_LANTERN, false);
        }

        // paredes de dentro: listras, uma faixa no alto e luzes
        for (int z = z0; z < z(t.fim); z++) {
            Material listra = ((z - z0) / 2) % 2 == 0 ? tema.listraA() : tema.listraB();
            for (int y = agua + 1; y < galeria - 3; y++) {
                w.getBlockAt(x(-LARGURA - 1), y, z).setType(listra, false);
                w.getBlockAt(x(LARGURA + 1), y, z).setType(listra, false);
            }
            boolean luz = (z - z0) % 6 == 3;
            for (int y = galeria - 3; y < galeria; y++) {
                Material m = luz && y == galeria - 2 ? tema.luzFaixa() : tema.faixa();
                w.getBlockAt(x(-LARGURA - 1), y, z).setType(m, false);
                w.getBlockAt(x(LARGURA + 1), y, z).setType(m, false);
            }
            if (luz) {
                w.getBlockAt(x(-LARGURA - 1), piso + 4, z).setType(Material.SEA_LANTERN, false);
                w.getBlockAt(x(LARGURA + 1), piso + 4, z).setType(Material.SEA_LANTERN, false);
            }
        }
        for (int x = -LARGURA; x <= LARGURA; x++) {
            for (int y = agua + 1; y < galeria; y++) {
                Material listra = ((x + LARGURA) / 2) % 2 == 0 ? tema.listraA() : tema.listraB();
                w.getBlockAt(x(x), y, z0 - 1).setType(listra, false);
                w.getBlockAt(x(x), y, z(t.fim)).setType(listra, false);
            }
        }
        // por fora: faixas coloridas
        for (int y = base; y < galeria; y += 3) anel(w, x(-fora), zi, x(fora), zf, y, piso((y - base) / 3));

        // passarelas: piso, vidro até o teto do lado da pista, paredes com janelas coloridas
        encher(w, x(LARGURA + 2), galeria, zi + 1, x(fora - 1), galeria, zf - 1, tema.passarela());
        encher(w, x(-fora + 1), galeria, zi + 1, x(-LARGURA - 2), galeria, zf - 1, tema.passarela());
        for (int y = galeria; y < teto; y++) {
            encher(w, x(LARGURA + 1), y, z0 - 1, x(LARGURA + 1), y, z(t.fim), Material.GLASS);
            encher(w, x(-LARGURA - 1), y, z0 - 1, x(-LARGURA - 1), y, z(t.fim), Material.GLASS);
            encher(w, x(-LARGURA), y, z0 - 1, x(LARGURA), y, z0 - 1, Material.GLASS);
            encher(w, x(-LARGURA), y, z(t.fim), x(LARGURA), y, z(t.fim), Material.GLASS);
            anel(w, x(-fora), zi, x(fora), zf, y, tema.corpo());
        }
        int k = 0;
        for (int z = zi + 2; z < zf; z += 4) {
            Material v = vidro(k++);
            w.getBlockAt(x(fora), galeria + 2, z).setType(v, false);
            w.getBlockAt(x(-fora), galeria + 2, z).setType(v, false);
        }
        for (int z = zi + 3; z < zf; z += 5) {
            w.getBlockAt(x(fora - 1), galeria, z).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(x(-fora + 1), galeria, z).setType(Material.SEA_LANTERN, false);
        }

        // teto com claraboias coloridas e bandeirinhas atravessando a pista
        encher(w, x(-fora), teto, zi, x(fora), teto, zf, tema.corpo());
        k = 0;
        for (int z = z0 + 4; z < z(t.fim); z += 8) {
            for (int x = -3; x <= 3; x += 3) w.getBlockAt(x(x), teto, z).setType(vidro(k++), false);
        }
        Material[] paineis = tema.vidros();
        for (int z = z0 + 6; z < z(t.fim); z += 10) {
            for (int x = -LARGURA; x <= LARGURA; x++) w.getBlockAt(x(x), galeria + 4, z).setType(paineis[Math.floorMod(x + LARGURA + z, paineis.length)], false);
        }
        anel(w, x(-fora - 1), zi - 1, x(fora + 1), zf + 1, teto, tema.borda());

        pista(w);
        refazer();
    }

    /** O chão de cada trecho (o que não muda numa corrida). */
    private void pista(World w) {
        // o piso listrado em tudo; depois, cada obstáculo por cima (ou tirando o piso, onde é água)
        for (int dz = TracadoCorrida.PORTAO; dz < t.chegada; dz++) linha(w, dz, piso(dz / 2));
        // largada em xadrez
        for (int dz = 0; dz < TracadoCorrida.PORTAO; dz++) {
            for (int x = -LARGURA; x <= LARGURA; x++) {
                w.getBlockAt(x(x), piso, z(dz)).setType(Math.floorMod(x + dz, 2) == 0 ? tema.largadaA() : tema.largadaB(), false);
            }
        }
        // as paredes das portas: colunas entre as portas e uma viga em cima
        for (int dz : t.fileiras) {
            for (int x : new int[]{-6, -2, 2, 6}) encher(w, x(x), piso + 1, z(dz), x(x), piso + 3, z(dz), tema.colunaPorta());
            encher(w, x(-LARGURA), piso + 4, z(dz), x(LARGURA), piso + 4, z(dz), tema.vigaPorta());
        }
        // os vãos de água (trampolim, pula-pula, parkour)
        for (int[] v : t.vaos) agua(w, v[0], v[1]);
        for (int dz : t.trampolins) linha(w, dz, Material.SLIME_BLOCK);
        // gelo com buracos
        for (Gelo g : t.gelos) {
            for (int dz = g.dz0(); dz <= g.dz1(); dz++) linha(w, dz, Material.BLUE_ICE);
            for (int[] b : g.buracos()) encher(w, x(b[1]), piso, z(b[0]), x(b[1] + 1), piso, z(b[0] + 1), Material.AIR);
        }
        // as plataformas: só água em volta delas (refazer() põe as plataformas)
        for (int f = 0; f < fases(); f++) {
            int f0 = f;
            List<Plataforma> daFase = t.plataformas.stream().filter(p -> p.fase() == f0).toList();
            int de = Integer.MAX_VALUE, ate = Integer.MIN_VALUE;
            for (Plataforma p : daFase) {
                de = Math.min(de, p.dz0());
                ate = Math.max(ate, p.dz0() + 2);
            }
            // cada fase tem um trecho só de plataformas (fases sem plataformas ficam de fora)
            if (!daFase.isEmpty()) agua(w, de, ate);
        }
        // tapetes rolantes, estreitos, com as setas pra trás (sobre a água)
        for (Tapete tp : t.tapetes) {
            agua(w, tp.dz0(), tp.dz1());
            for (int dz = tp.dz0(); dz <= tp.dz1(); dz++) {
                for (int x = -tp.meia(); x <= tp.meia(); x++) {
                    Block b = w.getBlockAt(x(x), piso, z(dz));
                    b.setType(tema.tapete(), false);
                    if (b.getBlockData() instanceof Directional d) {
                        d.setFacing(BlockFace.NORTH);
                        b.setBlockData(d, false);
                    }
                }
            }
        }
        // o pula-pula e o parkour (com um pilar até a água)
        for (Quica q : t.quicas) encher(w, x(-2), piso, z(q.dz0()), x(2), piso, z(q.dz1()), Material.SLIME_BLOCK);
        for (int i = 0; i < t.blocos.size(); i++) {
            Bloco b = t.blocos.get(i);
            encher(w, x(b.x0()), agua + 1, z(b.dz0()), x(b.x0() + 1), piso + b.altura() - 1, z(b.dz0() + 1), tema.corpo());
            encher(w, x(b.x0()), piso + b.altura(), z(b.dz0()), x(b.x0() + 1), piso + b.altura(), z(b.dz0() + 1), piso(i));
        }
        // pontes estreitas sobre a água (martelos, vento, as do carrossel)
        for (Ponte p : t.pontes) {
            agua(w, p.dz0(), p.dz1());
            for (int dz = p.dz0(); dz <= p.dz1(); dz++) {
                for (int x = -p.meia(); x <= p.meia(); x++) w.getBlockAt(x(x), piso, z(dz)).setType(dz % 2 == 0 ? tema.ponteA() : tema.ponteB(), false);
            }
        }
        // os discos das barras e os carrosséis, em anéis coloridos, com o poste no meio
        for (int p = 0; p < t.pivos.size(); p++) disco(w, t.pivos.get(p).dz(), p);
        for (Carrossel c : t.carrosseis) disco(w, c.dz(), 3);
        // entre dois discos encostados (o par), uma pontinha
        for (int p = 1; p < t.pivos.size(); p++) {
            if (!t.pivos.get(p).inverso()) continue;
            int a = t.pivos.get(p - 1).dz(), b = t.pivos.get(p).dz();
            agua(w, a + RAIO + 1, b - RAIO - 1);
            encher(w, x(-1), piso, z(a + RAIO), x(1), piso, z(b - RAIO), tema.ponteA());
        }
        // os canhões (enfeite) nas paredes
        for (Canhao c : canhoes()) {
            int lado = c.direcao().getX() < 0 ? 1 : -1;
            Block b = w.getBlockAt(x(lado * (LARGURA + 1)), piso + 1, c.origem().getBlockZ());
            b.setType(Material.DISPENSER, false);
            if (b.getBlockData() instanceof Directional d) {
                d.setFacing(lado > 0 ? BlockFace.WEST : BlockFace.EAST);
                b.setBlockData(d, false);
            }
        }
        for (int i = 1; i < t.checkpoints.size(); i++) checkpoint(w, t.checkpoints.get(i));
        chegada(w);
    }

    /**
     * A chegada: a linha quadriculada com um arco, o vidro e a arquibancada de quem chegou (degraus subindo
     * pro fundo, com o topo largo), de frente pra linha.
     */
    private void chegada(World w) {
        int c = t.chegada;
        linha(w, c - 1, Material.YELLOW_CONCRETE);
        for (int dz = c; dz <= c + 1; dz++) {
            for (int x = -LARGURA; x <= LARGURA; x++) {
                w.getBlockAt(x(x), piso, z(dz)).setType(Math.floorMod(x + dz, 2) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE, false);
            }
        }
        for (int y = piso + 1; y <= piso + 5; y++) {
            w.getBlockAt(x(-LARGURA), y, z(c)).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(x(LARGURA), y, z(c)).setType(Material.GOLD_BLOCK, false);
        }
        for (int x = -LARGURA; x <= LARGURA; x++) {
            w.getBlockAt(x(x), piso + 5, z(c)).setType(Math.floorMod(x + LARGURA, 2) == 0 ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE, false);
        }
        int vidro = c + TracadoCorrida.ATE_O_VIDRO;
        linha(w, vidro, Material.YELLOW_CONCRETE);
        encher(w, x(-LARGURA), piso + 1, z(vidro), x(LARGURA), galeria - 1, z(vidro), Material.GLASS);
        // degraus: 1, 2 e 3 acima do piso, e o topo (4) até o fundo
        for (int dz = vidro + 1; dz < t.fim; dz++) {
            int altura = Math.min(4, dz - vidro);
            encher(w, x(-LARGURA), piso, z(dz), x(LARGURA), piso + altura - 1, z(dz), tema.corpo());
            linha(w, dz, piso + altura, altura < 4 ? piso(dz) : tema.passarela());
        }
    }

    /** Um disco redondo de raio RAIO sobre a água, em anéis coloridos, com um poste no meio. */
    private void disco(World w, int pz, int cor) {
        agua(w, pz - RAIO, pz + RAIO);
        for (int dz = -RAIO; dz <= RAIO; dz++) {
            for (int x = -RAIO; x <= RAIO; x++) {
                double r = Math.sqrt(x * x + dz * dz);
                if (r > RAIO + 0.3) continue;
                w.getBlockAt(x(x), piso, z(pz + dz)).setType(piso((int) r / 2 + cor), false);
            }
        }
        w.getBlockAt(x(0), piso + 1, z(pz)).setType(tema.pecaA(), false);
        w.getBlockAt(x(0), piso + 2, z(pz)).setType(tema.pecaB(), false);
        w.getBlockAt(x(0), piso + 3, z(pz)).setType(tema.pecaA(), false);
    }

    /** Tira o piso (água embaixo) de dz0 a dz1. */
    private void agua(World w, int dz0, int dz1) {
        encher(w, x(-LARGURA), piso, z(dz0), x(LARGURA), piso, z(dz1), Material.AIR);
    }

    private void linha(World w, int dz, Material m) {
        linha(w, dz, piso, m);
    }

    private void linha(World w, int dz, int y, Material m) {
        encher(w, x(-LARGURA), y, z(dz), x(LARGURA), y, z(dz), m);
    }

    private void checkpoint(World w, int dz) {
        for (int i = 0; i < 4; i++) linha(w, dz + i, i == 0 ? Material.GOLD_BLOCK : Material.YELLOW_CONCRETE);
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
