package com.example.dicas.manager;

import com.example.dicas.DicasPlugin;
import com.example.dicas.model.NpcDica;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Os NPCs de dicas e as entidades deles. Igual aos vendedores das barracas: um Mannequin que não fica
 * salvo no mundo (nasce quando o chunk carrega e some com ele), não toma dano de nada e, se sumir
 * mesmo assim (um /kill), volta no tick seguinte. Tudo fica no npcs.yml.
 */
public class NpcsManager implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();

    private final DicasPlugin plugin;
    private final File arquivo;
    private final NamespacedKey chave;
    private final Map<String, NpcDica> npcs = new LinkedHashMap<>();
    private final Map<String, Mannequin> entidades = new HashMap<>();

    public NpcsManager(DicasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "npcs.yml");
        this.chave = new NamespacedKey(plugin, "npc_dica");
        carregar();
    }

    public Map<String, NpcDica> getNpcs() {
        return npcs;
    }

    public NpcDica get(String id) {
        return npcs.get(id.toLowerCase(Locale.ROOT));
    }

    /** O NPC dessa entidade, ou null se ela não for um NPC de dicas. */
    public NpcDica getNpc(Entity entidade) {
        if (!(entidade instanceof Mannequin)) return null;
        String id = entidade.getPersistentDataContainer().get(chave, PersistentDataType.STRING);
        return id == null ? null : npcs.get(id);
    }

    public Mannequin getEntidade(NpcDica npc) {
        Mannequin m = entidades.get(npc.getId());
        return m != null && m.isValid() ? m : null;
    }

    public NpcDica criar(String id, String nome, Location local) {
        NpcDica npc = new NpcDica(id.toLowerCase(Locale.ROOT), nome, local);
        npcs.put(npc.getId(), npc);
        salvar();
        return npc;
    }

    public void remover(NpcDica npc) {
        npcs.remove(npc.getId());
        Mannequin m = entidades.remove(npc.getId());
        if (m != null) m.remove();
        salvar();
    }

    // ---------------------------------------------------------------------
    // Entidades
    // ---------------------------------------------------------------------

    /** Põe ou refaz a entidade do NPC (se o chunk estiver carregado). Chamar depois de mudar nome, skin ou lugar. */
    public void atualizar(NpcDica npc) {
        Mannequin atual = entidades.remove(npc.getId());
        if (atual != null) atual.remove();
        Location local = npc.getLocal();
        if (local == null || !local.getWorld().isChunkLoaded(local.getBlockX() >> 4, local.getBlockZ() >> 4)) return;

        Mannequin m = local.getWorld().spawn(local, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setImmovable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setCustomNameVisible(true);
            e.customName(LEGADO.deserialize(npc.getNome()));
            e.setDescription(LEGADO.deserialize(plugin.getConfig().getString("descricao", "&7Clique pra conversar")));
            if (npc.getSkin() != null) e.setProfile(ResolvableProfile.resolvableProfile().name(npc.getSkin()).build());
            e.getPersistentDataContainer().set(chave, PersistentDataType.STRING, npc.getId());
        });
        entidades.put(npc.getId(), m);
    }

    public void carregarTodos() {
        npcs.values().forEach(this::atualizar);
    }

    public void removerTodos() {
        entidades.values().forEach(Entity::remove);
        entidades.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (getNpc(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        NpcDica npc = getNpc(evento.getEntity());
        if (npc == null) return;
        evento.getDrops().clear();
        Bukkit.getScheduler().runTask(plugin, () -> atualizar(npc));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        Chunk chunk = evento.getChunk();
        for (NpcDica npc : npcs.values()) {
            if (npc.getMundo().equals(chunk.getWorld().getName())
                    && ((int) Math.floor(npc.getX()) >> 4) == chunk.getX() && ((int) Math.floor(npc.getZ()) >> 4) == chunk.getZ()) {
                atualizar(npc);
            }
        }
    }

    @EventHandler
    public void aoDescarregar(ChunkUnloadEvent evento) {
        Chunk chunk = evento.getChunk();
        entidades.values().removeIf(m -> {
            Location l = m.getLocation();
            if (!chunk.getWorld().equals(l.getWorld()) || l.getBlockX() >> 4 != chunk.getX() || l.getBlockZ() >> 4 != chunk.getZ()) return false;
            m.remove();
            return true;
        });
    }

    /** A cada tanto: um NPC que sumiu (um /kill não passa pelo evento de morte) volta. */
    public void conferir() {
        for (NpcDica npc : npcs.values()) {
            Mannequin m = entidades.get(npc.getId());
            if (m == null || !m.isValid()) atualizar(npc);
        }
    }

    // ---------------------------------------------------------------------
    // npcs.yml
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("npcs");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            if (s == null) continue;
            NpcDica npc = new NpcDica(id, s.getString("nome", id), s.getString("mundo", "world"),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"));
            npc.setSkin(s.getString("skin"));
            npc.getFalas().addAll(s.getStringList("falas"));
            npcs.put(id, npc);
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (NpcDica npc : npcs.values()) {
            String base = "npcs." + npc.getId();
            yaml.set(base + ".nome", npc.getNome());
            yaml.set(base + ".mundo", npc.getMundo());
            yaml.set(base + ".x", npc.getX());
            yaml.set(base + ".y", npc.getY());
            yaml.set(base + ".z", npc.getZ());
            yaml.set(base + ".yaw", (double) npc.getYaw());
            yaml.set(base + ".skin", npc.getSkin());
            yaml.set(base + ".falas", npc.getFalas());
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar npcs.yml", e);
        }
    }
}
