package com.example.dicas.manager;

import com.example.dicas.DicasPlugin;
import com.example.dicas.model.NpcDica;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O balão de fala: clique direito no NPC e ele vira pra quem clicou e fala. O balão (um TextDisplay
 * acima da cabeça) cresce, escreve a fala letra por letra com um somzinho, fica um tempo proporcional
 * ao tamanho do texto e some encolhendo. Cada clique mostra a próxima fala, na ordem, por jogador
 * (a ordem recomeça quando ele volta ao servidor).
 *
 * Um NPC fala uma coisa por vez: clicar enquanto ele escreve completa o texto na hora, e clicar
 * depois de completo já passa pra próxima fala.
 */
public class Dialogos implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();
    private static final int TICKS_ANIMACAO = 5;

    private final DicasPlugin plugin;
    // fala atual de cada jogador em cada NPC
    private final Map<UUID, Map<String, Integer>> proxima = new HashMap<>();
    private final Map<String, Fala> falando = new HashMap<>();
    private final Map<UUID, Long> ultimoClique = new HashMap<>();

    public Dialogos(DicasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void aoClicar(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        NpcDica npc = plugin.getNpcs().getNpc(evento.getRightClicked());
        if (npc == null) return;
        evento.setCancelled(true);
        // Um clique pode chegar como dois eventos (o cliente manda "interact" e "interact at", e cada
        // versão manda uma combinação): só o primeiro conta, senão a fala já nasceria completa.
        long agora = System.currentTimeMillis();
        Long anterior = ultimoClique.put(evento.getPlayer().getUniqueId(), agora);
        if (anterior != null && agora - anterior < 200) return;
        if (npc.getFalas().isEmpty()) return;

        Fala atual = falando.get(npc.getId());
        if (atual != null && !atual.completa()) {
            atual.completar();
            return;
        }
        if (atual != null) atual.encerrar();

        Player jogador = evento.getPlayer();
        Map<String, Integer> doJogador = proxima.computeIfAbsent(jogador.getUniqueId(), k -> new HashMap<>());
        int indice = doJogador.getOrDefault(npc.getId(), 0) % npc.getFalas().size();
        doJogador.put(npc.getId(), indice + 1);

        Fala fala = new Fala(npc, (Mannequin) evento.getRightClicked(), jogador, npc.getFalas().get(indice));
        falando.put(npc.getId(), fala);
        fala.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        proxima.remove(evento.getPlayer().getUniqueId());
        ultimoClique.remove(evento.getPlayer().getUniqueId());
    }

    /** Some com todos os balões (ao desligar). */
    public void encerrarTodas() {
        new HashMap<>(falando).values().forEach(Fala::encerrar);
    }

    /** Uma fala em andamento: cresce, escreve, segura e encolhe. */
    private final class Fala extends BukkitRunnable {
        private final NpcDica npc;
        private final Mannequin entidade;
        private final String texto;
        private final int visiveis;
        private final TextDisplay balao;
        private final float yawOriginal;
        private int letras;
        private int segurar = -1;
        private int encolhendo = -1;

        Fala(NpcDica npc, Mannequin entidade, Player ouvinte, String texto) {
            this.npc = npc;
            this.entidade = entidade;
            this.texto = texto;
            this.visiveis = visiveis(texto);
            this.yawOriginal = entidade.getLocation().getYaw();

            // vira o rosto pra quem clicou
            Vector olhar = ouvinte.getEyeLocation().toVector().subtract(entidade.getEyeLocation().toVector());
            Location virado = entidade.getLocation().setDirection(olhar);
            entidade.setRotation(virado.getYaw(), 0);

            Location topo = entidade.getLocation().add(0, plugin.getConfig().getDouble("balao.altura", 2.6), 0);
            int largura = plugin.getConfig().getInt("balao.largura", 180);
            this.balao = topo.getWorld().spawn(topo, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setLineWidth(largura);
                t.setBackgroundColor(Color.fromARGB(plugin.getConfig().getInt("balao.fundo-opacidade", 170), 20, 20, 30));
                t.setShadowed(true);
                t.text(LEGADO.deserialize(""));
                t.setTransformation(escala(0f));
            });
            // no tick seguinte, interpola da escala 0 até a cheia: o balão "abre"
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!balao.isValid()) return;
                balao.setInterpolationDelay(0);
                balao.setInterpolationDuration(TICKS_ANIMACAO);
                balao.setTransformation(escala(1f));
            });
        }

        boolean completa() {
            return letras >= visiveis;
        }

        void completar() {
            letras = visiveis;
            balao.text(LEGADO.deserialize(texto));
        }

        @Override
        public void run() {
            if (!balao.isValid() || !entidade.isValid()) {
                encerrar();
                return;
            }
            if (encolhendo >= 0) {
                if (--encolhendo < 0) encerrar();
                return;
            }
            if (!completa()) {
                letras = Math.min(visiveis, letras + Math.max(1, plugin.getConfig().getInt("balao.letras-por-tick", 1)));
                balao.text(LEGADO.deserialize(prefixo(texto, letras)));
                if (letras % 2 == 0) {
                    float tom = 1.2f + ThreadLocalRandom.current().nextFloat() * 0.6f;
                    entidade.getWorld().playSound(entidade.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, tom);
                }
                return;
            }
            if (segurar < 0) {
                double porLetra = plugin.getConfig().getDouble("balao.ticks-por-letra-lida", 1.2);
                segurar = (int) Math.min(240, 40 + visiveis * porLetra);
            }
            if (--segurar <= 0) {
                encolhendo = TICKS_ANIMACAO;
                balao.setInterpolationDelay(0);
                balao.setInterpolationDuration(TICKS_ANIMACAO);
                balao.setTransformation(escala(0f));
            }
        }

        void encerrar() {
            if (!isCancelled()) cancel();
            if (balao.isValid()) balao.remove();
            if (entidade.isValid()) entidade.setRotation(yawOriginal, 0);
            falando.remove(npc.getId(), this);
        }
    }

    private static Transformation escala(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    /** Quantas letras aparecem (sem contar os códigos de cor &x). */
    private static int visiveis(String texto) {
        int n = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '&' && i + 1 < texto.length()) i++;
            else n++;
        }
        return n;
    }

    /** O começo do texto com {@code letras} letras visíveis, mantendo os códigos de cor pelo caminho. */
    private static String prefixo(String texto, int letras) {
        int n = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '&' && i + 1 < texto.length()) {
                i++;
                continue;
            }
            if (++n == letras) return texto.substring(0, i + 1);
        }
        return texto;
    }
}
