package com.example.eventos.spleef;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * O Spleef: pisos de neve (3 andares) sobre a água, todos com a mesma pá. Quebra-se a neve debaixo
 * dos outros (sem bola de neve, sem soco); quem cai de um piso vai pro de baixo, e do último cai na
 * água e está fora. Depois de um tempo, o piso derrete aos poucos onde ainda tem gente, pra a luta
 * terminar. O resto (inscrição, eliminação, prêmio) é do {@link Evento}.
 */
public class Spleef extends Evento {

    private ArenaSpleef arena;
    private long proximoDerreter;
    /** Blocos avisados (gelo), que viram ar quando der o tempo. */
    private final List<Block> derretendo = new ArrayList<>();
    private long derreterEm;

    public Spleef(EventosPlugin plugin) {
        super(plugin, "spleef");
        recarregar();
    }

    @Override
    public String nome() {
        return "Spleef";
    }

    @Override
    protected String titulo() {
        return "§b§l❄ SPLEEF ❄";
    }

    @Override
    protected String cor() {
        return "§b";
    }

    @Override
    public String rotulo() {
        return "§b❄ Spleef";
    }

    @Override
    public String descricao() {
        return "Três pisos de neve sobre a água: com a pá, quebre o chão debaixo dos outros. Caiu de um piso, vai pro de baixo; do último, cai na água e está fora.";
    }

    @Override
    public void recarregar() {
        this.arena = new ArenaSpleef(cfg().getConfigurationSection("arena"));
    }

    public ArenaSpleef getArena() {
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
        derretendo.clear();
        arena.construir();
    }

    @Override
    protected void resetar() {
        derretendo.clear();
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

    @Override
    protected void darKit(Player p) {
        ItemStack pa = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemMeta meta = pa.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, Math.max(1, cfg().getInt("kit.eficiencia")), true);
        meta.setUnbreakable(true);
        pa.setItemMeta(meta);
        p.getInventory().setItem(0, pa);
        p.getInventory().setHeldItemSlot(0);
    }

    /** Só a neve dos pisos. */
    @Override
    public boolean podeQuebrar(Player p, Block bloco) {
        return bloco.getType() == Material.SNOW_BLOCK && arena.ehPiso(bloco);
    }

    @Override
    protected void aoComecarLuta(long agora) {
        derretendo.clear();
        proximoDerreter = agora + Math.max(0, cfg().getInt("derreter.comecar-segundos")) * 1000L;
    }

    /**
     * O derretimento: a cada intervalo, alguns blocos de neve viram gelo (o aviso) e, logo depois, somem.
     * Só nos andares em que ainda tem alguém de pé, pra apertar quem está lá.
     */
    @Override
    protected void tickLuta(long agora) {
        if (!derretendo.isEmpty() && agora >= derreterEm) {
            for (Block b : derretendo) {
                if (b.getType() != Material.PACKED_ICE) continue;
                b.setType(Material.AIR, false);
                b.getWorld().spawnParticle(Particle.SNOWFLAKE, b.getLocation().add(0.5, 0.8, 0.5), 4, 0.3, 0.1, 0.3, 0);
            }
            derretendo.clear();
        }
        if (agora < proximoDerreter || !derretendo.isEmpty()) return;
        proximoDerreter = agora + Math.max(1, cfg().getInt("derreter.intervalo-segundos")) * 1000L;

        Set<Integer> andares = new HashSet<>();
        for (Player p : jogadoresVivos()) {
            int a = arena.andarEm(p.getLocation().getY());
            if (a >= 0) andares.add(a);
        }
        World w = arena.mundo();
        List<Block> candidatos = new ArrayList<>();
        for (int a : andares) {
            int y = arena.yDoPiso(a);
            for (int[] c : arena.colunas()) {
                Block b = w.getBlockAt(c[0], y, c[1]);
                if (b.getType() == Material.SNOW_BLOCK) candidatos.add(b);
            }
        }
        Collections.shuffle(candidatos);
        int quantos = Math.min(candidatos.size(), Math.max(1, cfg().getInt("derreter.blocos-por-vez")));
        for (int i = 0; i < quantos; i++) {
            Block b = candidatos.get(i);
            b.setType(Material.PACKED_ICE, false);
            derretendo.add(b);
        }
        if (quantos > 0) {
            derreterEm = agora + Math.max(0, cfg().getInt("derreter.aviso-segundos")) * 1000L;
            Location l = derretendo.get(0).getLocation();
            l.getWorld().playSound(l, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
        }
    }
}
