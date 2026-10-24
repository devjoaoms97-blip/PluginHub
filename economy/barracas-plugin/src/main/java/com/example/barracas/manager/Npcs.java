package com.example.barracas.manager;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * O vendedor de cada barraca alugada: um Mannequin com a skin do dono, parado no ponto que a
 * staff marcou com /barraca npc. Barraca livre não tem NPC (quem anuncia é o holograma de entrada).
 * Igual aos hologramas, não fica salvo no mundo: nasce quando o chunk carrega e some com ele.
 * Não toma dano de nada (o invulnerável do Minecraft não segura jogador no criativo), e se sumir
 * mesmo assim (um /kill, por exemplo) volta no tick seguinte. O clique nele é tratado no LojaListener.
 */
public class Npcs implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final BarracasPlugin plugin;
    private final NamespacedKey chaveBarraca;
    private final Map<String, Mannequin> npcs = new HashMap<>();

    public Npcs(BarracasPlugin plugin) {
        this.plugin = plugin;
        this.chaveBarraca = new NamespacedKey(plugin, "barraca_npc");
    }

    /** Id da barraca desse NPC, ou null se a entidade não for um vendedor. */
    public String getBarraca(Entity entidade) {
        if (!(entidade instanceof Mannequin)) return null;
        return entidade.getPersistentDataContainer().get(chaveBarraca, PersistentDataType.STRING);
    }

    /** Põe, tira ou atualiza o NPC conforme o estado da barraca (alugada e com ponto marcado). */
    public void atualizar(Barraca barraca) {
        Mannequin atual = npcs.get(barraca.getId());
        BlocoPos pos = barraca.getNpc();
        World mundo = Bukkit.getWorld(barraca.getMundo());
        boolean deveExistir = pos != null && mundo != null && barraca.estaAlugada(System.currentTimeMillis());

        if (!deveExistir) {
            if (atual != null) npcs.remove(barraca.getId()).remove();
            return;
        }
        if (!mundo.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return;

        Location local = new Location(mundo, pos.x() + 0.5, pos.y(), pos.z() + 0.5, barraca.getNpcYaw(), 0);
        String nomeDono = barraca.getNomeDono();
        if (atual != null && (!atual.isValid() || !mesmoLugar(atual.getLocation(), local)
                || !nomeDono.equals(atual.getProfile().name()))) {
            atual.remove(); // sumiu, a staff mudou o ponto, ou trocou o dono
            atual = null;
        }
        if (atual == null) {
            atual = criar(local, barraca.getId(), nomeDono);
            npcs.put(barraca.getId(), atual);
        }
        atual.customName(LEGADO.deserialize((barraca.isEspecial() ? "§6★ " : "§e") + "Loja de " + nomeDono));
    }

    private boolean mesmoLugar(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) < 0.01
                && Math.abs(Location.normalizeYaw(a.getYaw() - b.getYaw())) < 1;
    }

    private Mannequin criar(Location local, String id, String nomeDono) {
        return local.getWorld().spawn(local, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setCustomNameVisible(true);
            // Só o nome: o cliente busca a skin do dono sozinho
            m.setProfile(ResolvableProfile.resolvableProfile().name(nomeDono).build());
            m.setDescription(LEGADO.deserialize("§7Clique para abrir a loja"));
            m.getPersistentDataContainer().set(chaveBarraca, PersistentDataType.STRING, id);
        });
    }

    /** Ao ligar: as barracas cujo chunk já está carregado. */
    public void carregarTodos() {
        plugin.getBarracaManager().getBarracas().values().forEach(this::atualizar);
    }

    public void removerTodos() {
        npcs.values().forEach(Entity::remove);
        npcs.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (getBarraca(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        String id = getBarraca(evento.getEntity());
        if (id == null) return;
        evento.getDrops().clear();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Barraca barraca = plugin.getBarracaManager().get(id);
            if (barraca != null) atualizar(barraca); // o NPC antigo não é mais válido: nasce outro
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        Chunk chunk = evento.getChunk();
        for (Barraca b : plugin.getBarracaManager().getBarracas().values()) {
            BlocoPos pos = b.getNpc();
            if (pos != null && pos.x() >> 4 == chunk.getX() && pos.z() >> 4 == chunk.getZ()
                    && b.getMundo().equals(chunk.getWorld().getName())) {
                atualizar(b);
            }
        }
    }

    @EventHandler
    public void aoDescarregar(ChunkUnloadEvent evento) {
        Chunk chunk = evento.getChunk();
        npcs.values().removeIf(npc -> {
            Location l = npc.getLocation();
            if (!chunk.getWorld().equals(l.getWorld()) || l.getBlockX() >> 4 != chunk.getX() || l.getBlockZ() >> 4 != chunk.getZ()) return false;
            npc.remove();
            return true;
        });
    }
}
