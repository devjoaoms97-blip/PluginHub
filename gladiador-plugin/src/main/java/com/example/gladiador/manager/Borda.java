package com.example.gladiador.manager;

import com.example.gladiador.GladiadorPlugin;
import com.example.gladiador.model.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Borda que fecha durante a luta do Gladiador, pra ela não se arrastar. O mundo das arenas é
 * dividido com o X1 e o boss, então não dá pra mexer na borda do mundo: cada participante recebe
 * uma borda só dele (o Paper manda pro cliente, que mostra a parede), e o dano de quem fica fora é
 * aplicado aqui. Começa do tamanho da arena e, depois de {@code borda.comeca-depois-segundos} de
 * luta, fecha até {@code borda.tamanho-final} em {@code borda.fecha-em-segundos}.
 * <p>
 * O dano de quem fica fora cresce a cada segundo seguido lá fora (pra regeneração alta não
 * segurar), e volta ao começo quando o jogador entra de novo. Ele sai direto da vida, e a
 * armadura não reduz.
 */
public class Borda {

    private final GladiadorPlugin plugin;
    private boolean avisouQueFecha;
    /** Segundos seguidos que cada participante está fora da borda. */
    private final Map<UUID, Integer> segundosFora = new HashMap<>();

    public Borda(GladiadorPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ativada() {
        return plugin.getConfig().getBoolean("borda.ativado", true);
    }

    private Location centro(Arena arena) {
        Location a = arena.getPos1(), b = arena.getPos2();
        return new Location(a.getWorld(), (a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
    }

    private double tamanhoInicial(Arena arena) {
        return Math.max(Math.abs(arena.getPos1().getX() - arena.getPos2().getX()), Math.abs(arena.getPos1().getZ() - arena.getPos2().getZ())) + 1;
    }

    /** Tamanho da borda com tantos ms de luta. */
    public double tamanho(Arena arena, long msDeLuta) {
        double inicial = tamanhoInicial(arena);
        double fim = Math.min(inicial, Math.max(1, plugin.getConfig().getDouble("borda.tamanho-final", 8)));
        long comeca = Math.max(0, plugin.getConfig().getLong("borda.comeca-depois-segundos", 300)) * 1000L;
        long fecha = Math.max(1, plugin.getConfig().getLong("borda.fecha-em-segundos", 300)) * 1000L;
        if (msDeLuta <= comeca) return inicial;
        double progresso = Math.min(1, (msDeLuta - comeca) / (double) fecha);
        return inicial + (fim - inicial) * progresso;
    }

    /** Começo da luta: cada participante vê a borda do tamanho da arena. */
    public void comecar(Arena arena, Collection<Player> participantes) {
        avisouQueFecha = false;
        segundosFora.clear();
        if (!ativada()) return;
        for (Player p : participantes) aplicar(p, arena, tamanhoInicial(arena));
    }

    /** A cada segundo da luta: encolhe a borda e machuca quem está fora. */
    public void tick(Arena arena, Collection<Player> vivos, long msDeLuta) {
        if (!ativada()) return;
        double tamanho = tamanho(arena, msDeLuta);
        if (!avisouQueFecha && tamanho < tamanhoInicial(arena)) {
            avisouQueFecha = true;
            for (Player p : vivos) p.sendMessage("§c§l⚠ A borda da arena está fechando! §cQuem ficar fora toma dano.");
        }
        Location c = centro(arena);
        for (Player p : vivos) {
            aplicar(p, arena, tamanho);
            Location l = p.getLocation();
            boolean fora = Math.abs(l.getX() - c.getX()) > tamanho / 2 || Math.abs(l.getZ() - c.getZ()) > tamanho / 2;
            if (!fora) {
                segundosFora.remove(p.getUniqueId());
            } else if (!p.isDead()) {
                double dano = dano(segundosFora.merge(p.getUniqueId(), 1, Integer::sum));
                if (dano > 0) machucar(p, dano);
            }
        }
    }

    /**
     * Dano no n-ésimo segundo seguido fora: {@code dano-inicial}, mais {@code aumento-por-segundo}
     * a cada segundo, até {@code dano-maximo} (0 = sem teto). Com o padrão: 2, 3, 4... até 20.
     */
    public double dano(int segundos) {
        double inicial = Math.max(0, plugin.getConfig().getDouble("borda.dano-inicial", 2));
        double aumento = Math.max(0, plugin.getConfig().getDouble("borda.aumento-por-segundo", 1));
        double maximo = plugin.getConfig().getDouble("borda.dano-maximo", 20);
        double dano = inicial + aumento * (segundos - 1);
        return maximo > 0 ? Math.min(maximo, dano) : dano;
    }

    /**
     * Tira o dano direto da vida (primeiro dos corações de absorção), sem passar pela armadura:
     * no 26.2, até o dano de borda do jogo é reduzido por armadura e Proteção, e uma netherite com
     * Proteção IV tomaria um terço do dano.
     */
    private void machucar(Player p, double dano) {
        double restante = dano;
        double absorcao = p.getAbsorptionAmount();
        if (absorcao > 0) {
            double usado = Math.min(absorcao, restante);
            p.setAbsorptionAmount(absorcao - usado);
            restante -= usado;
        }
        p.playHurtAnimation(0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        if (restante > 0) p.setHealth(Math.max(0, p.getHealth() - restante)); // 0 = morte, e a eliminação normal
    }

    private void aplicar(Player p, Arena arena, double tamanho) {
        WorldBorder borda = p.getWorldBorder();
        if (borda == null) {
            borda = Bukkit.createWorldBorder();
            Location c = centro(arena);
            borda.setCenter(c.getX(), c.getZ());
            borda.setWarningDistance(2);
            p.setWorldBorder(borda);
        }
        borda.setSize(tamanho, 1); // 1 s de transição, o mesmo intervalo do tick: fica suave
    }

    /** Fim da luta (ou eliminação): volta a borda normal do mundo. */
    public void tirar(Player p) {
        segundosFora.remove(p.getUniqueId());
        if (p.getWorldBorder() != null) p.setWorldBorder(null);
    }
}
