package com.example.eventos.correoucai;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * O Corre ou Cai: três pisos coloridos sobre a água, sem kit. O bloco em que alguém pisa fica vermelho e
 * some meio segundo depois ({@code chao.some-em-ticks}): quem para, cai. Quem cai de um piso vai pro de
 * baixo; do último, cai na água e está fora. Nos primeiros segundos ({@code chao.graca-segundos}) o chão
 * não some, pra ninguém cair na largada. O resto (inscrição, eliminação, prêmio) é do {@link Evento}.
 */
public class CorreOuCai extends Evento {

    /** Metade da largura do jogador: o pé pode estar em cima de até 4 blocos. */
    private static final double MEIO = 0.3;

    private ArenaCorreOuCai arena;
    /** Os blocos já marcados (vermelhos), pra não agendar duas vezes. */
    private final Set<Block> marcados = new HashSet<>();

    public CorreOuCai(EventosPlugin plugin) {
        super(plugin, "correoucai");
        recarregar();
    }

    @Override
    public String nome() {
        return "Corre ou Cai";
    }

    @Override
    protected String titulo() {
        return "§d§l✦ CORRE OU CAI ✦";
    }

    @Override
    protected String cor() {
        return "§d";
    }

    @Override
    public String rotulo() {
        return "§d✦ Corre ou Cai";
    }

    @Override
    public String descricao() {
        return "Três pisos coloridos sobre a água: o bloco em que você pisa some logo depois, então não pare de correr! Caiu de um piso, vai pro de baixo; do último, cai na água e está fora.";
    }

    @Override
    public void recarregar() {
        this.arena = new ArenaCorreOuCai(cfg().getConfigurationSection("arena"));
    }

    public ArenaCorreOuCai getArena() {
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
        marcados.clear();
        arena.construir();
    }

    @Override
    protected void resetar() {
        marcados.clear();
        arena.refazerPisos();
    }

    @Override
    public int[][] caixa() {
        return arena.caixa();
    }

    @Override
    protected List<Location> pontosDeEntrada(int quantos) {
        return arena.pontosDeEntrada(quantos);
    }

    /** Sem kit: só correr. */
    @Override
    protected void darKit(Player p) {
    }

    /** O chão debaixo de cada um (quem está parado também), depois da graça do começo. */
    @Override
    protected void tickRapidoLuta(long agora) {
        if (agora - inicioLuta() < Math.max(0, cfg().getInt("chao.graca-segundos")) * 1000L) return;
        long ticks = Math.max(1, cfg().getInt("chao.some-em-ticks"));
        for (Player p : jogadoresVivos()) {
            Location pe = p.getLocation();
            int y = (int) Math.floor(pe.getY() - 0.1);
            for (double dx : new double[]{-MEIO, MEIO}) {
                for (double dz : new double[]{-MEIO, MEIO}) {
                    Block b = pe.getWorld().getBlockAt((int) Math.floor(pe.getX() + dx), y, (int) Math.floor(pe.getZ() + dz));
                    if (b.getType().isAir() || b.getType() == Material.RED_CONCRETE || !arena.ehPiso(b) || !marcados.add(b)) continue;
                    b.setType(Material.RED_CONCRETE, false);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (marcados.remove(b) && b.getType() == Material.RED_CONCRETE) b.setType(Material.AIR, false);
                    }, ticks);
                }
            }
        }
    }
}
