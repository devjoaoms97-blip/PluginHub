package com.example.eventos.vietna;

import com.example.eventos.EventosPlugin;
import com.example.eventos.base.Evento;
import com.example.eventos.vietna.ArenaVietna.Pilar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WindCharge;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O Vietnã: pilares de 2x2 sobre a água, todos com o mesmo kit (arco, bola de neve, carga de vento e
 * vara). Os golpes não tiram vida, só empurram; caiu na água, está fora. Os pilares somem com o tempo,
 * pra a luta terminar. O resto (inscrição, eliminação, prêmio) é do {@link Evento}.
 */
public class Vietna extends Evento {

    private ArenaVietna arena;
    private final List<Pilar> pilaresDePe = new ArrayList<>();
    private Pilar pilarAvisado;
    private long derrubarEm, proximoPilar, ultimoReabastecer;

    public Vietna(EventosPlugin plugin) {
        super(plugin, "vietna");
        recarregar();
    }

    @Override
    public String nome() {
        return "Vietnã";
    }

    @Override
    protected String titulo() {
        return "§2§l☘ VIETNÃ ☘";
    }

    @Override
    protected String cor() {
        return "§a";
    }

    @Override
    public String rotulo() {
        return "§a☘ Vietnã";
    }

    @Override
    public String descricao() {
        return "Pilares sobre a água: derrube os outros com arco, bola de neve, carga de vento e vara. Os golpes só empurram; caiu na água, está fora.";
    }

    @Override
    public void recarregar() {
        this.arena = new ArenaVietna(cfg().getConfigurationSection("arena"));
    }

    public ArenaVietna getArena() {
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
    }

    @Override
    protected void resetar() {
        arena.resetar();
    }

    @Override
    public int[][] caixa() {
        return arena.caixa();
    }

    /** Cada um num pilar diferente, virado pra um lado qualquer. */
    @Override
    protected List<Location> pontosDeEntrada(int quantos) {
        List<Location> pontos = new ArrayList<>();
        for (Pilar p : arena.pilares()) pontos.add(arena.topo(p, ThreadLocalRandom.current().nextInt(4) * 90f));
        return pontos;
    }

    @Override
    protected void darKit(Player p) {
        ConfigurationSection kit = cfg().getConfigurationSection("kit");
        ItemStack arco = new ItemStack(Material.BOW);
        ItemMeta meta = arco.getItemMeta();
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        if (kit.getInt("arco-impulso") > 0) meta.addEnchant(Enchantment.PUNCH, kit.getInt("arco-impulso"), true);
        meta.setUnbreakable(true);
        arco.setItemMeta(meta);
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, arco);
        if (kit.getInt("bolas-de-neve") > 0) inv.setItem(1, new ItemStack(Material.SNOWBALL, kit.getInt("bolas-de-neve")));
        if (kit.getInt("cargas-de-vento") > 0) inv.setItem(2, new ItemStack(Material.WIND_CHARGE, kit.getInt("cargas-de-vento")));
        if (kit.getBoolean("vara-de-pesca")) {
            ItemStack vara = new ItemStack(Material.FISHING_ROD);
            ItemMeta mv = vara.getItemMeta();
            mv.setUnbreakable(true);
            vara.setItemMeta(mv);
            inv.setItem(3, vara);
        }
        inv.setItem(9, new ItemStack(Material.ARROW));
    }

    @Override
    protected void aoComecarLuta(long agora) {
        pilaresDePe.clear();
        pilaresDePe.addAll(arena.pilares());
        pilarAvisado = null;
        ultimoReabastecer = agora;
        proximoPilar = agora + Math.max(0, cfg().getInt("pilares-somem.comecar-segundos")) * 1000L;
    }

    @Override
    protected void tickLuta(long agora) {
        int reabastecer = cfg().getInt("kit.reabastecer.segundos");
        if (reabastecer > 0 && agora - ultimoReabastecer >= reabastecer * 1000L) {
            ultimoReabastecer = agora;
            ConfigurationSection kit = cfg().getConfigurationSection("kit");
            for (Player p : jogadoresVivos()) {
                completar(p, Material.SNOWBALL, kit.getInt("reabastecer.bolas-de-neve"), kit.getInt("bolas-de-neve"));
                completar(p, Material.WIND_CHARGE, kit.getInt("reabastecer.cargas-de-vento"), kit.getInt("cargas-de-vento"));
            }
        }

        // pilar avisado: some quando der o tempo; sem aviso pendente, escolhe o próximo
        if (pilarAvisado != null && agora >= derrubarEm) {
            arena.derrubar(pilarAvisado);
            pilaresDePe.remove(pilarAvisado);
            pilarAvisado = null;
        }
        int minimo = Math.max(1, cfg().getInt("pilares-somem.minimo"));
        if (pilarAvisado == null && agora >= proximoPilar && pilaresDePe.size() > minimo) {
            pilarAvisado = pilaresDePe.get(ThreadLocalRandom.current().nextInt(pilaresDePe.size()));
            arena.avisar(pilarAvisado);
            derrubarEm = agora + Math.max(1, cfg().getInt("pilares-somem.aviso-segundos")) * 1000L;
            proximoPilar = agora + Math.max(1, cfg().getInt("pilares-somem.intervalo-segundos")) * 1000L;
            Location l = arena.topo(pilarAvisado, 0);
            l.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 0.6f);
        }
    }

    /** Soma de volta um pouco, até o que o kit dá. */
    private static void completar(Player p, Material tipo, int quantidade, int maximo) {
        int tem = 0;
        for (ItemStack i : p.getInventory().getContents()) if (i != null && i.getType() == tipo) tem += i.getAmount();
        int dar = Math.min(quantidade, maximo - tem);
        if (dar > 0) p.getInventory().addItem(new ItemStack(tipo, dar));
    }

    /** Empurrão na direção do projétil (a flecha conforme a força do tiro). */
    @Override
    public void aoAcertar(Player vitima, Projectile proj) {
        String tipo = proj instanceof AbstractArrow ? "flecha" : proj instanceof Snowball ? "bola-de-neve"
                : proj instanceof WindCharge ? "carga-de-vento" : proj instanceof FishHook ? "vara-de-pesca" : null;
        if (tipo == null) return;
        double forca = cfg().getDouble("empurrao." + tipo);
        if (proj instanceof AbstractArrow) forca *= Math.min(1, proj.getVelocity().length() / 3.0);
        Vector direcao = proj.getVelocity().clone().setY(0);
        if (direcao.lengthSquared() < 1e-6) direcao = vitima.getLocation().toVector().subtract(proj.getLocation().toVector()).setY(0);
        if (direcao.lengthSquared() < 1e-6) return;
        Vector empurrao = direcao.normalize().multiply(forca).setY(cfg().getDouble("empurrao.vertical"));
        // no tick seguinte: o cancelamento do dano já passou e a velocidade fica
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (vitima.isOnline()) vitima.setVelocity(vitima.getVelocity().add(empurrao));
        });
    }
}
