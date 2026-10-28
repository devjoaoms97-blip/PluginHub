package com.example.oficinas;

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
 * Os NPCs das oficinas, igual aos de dicas e aos vendedores das barracas: um Mannequin que não fica
 * salvo no mundo (nasce quando o chunk carrega e some com ele), não toma dano e, se sumir mesmo assim
 * (um /kill), volta sozinho. Ficam no oficinas.yml.
 */
public class NpcsManager implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();

    private final OficinasPlugin plugin;
    private final File arquivo;
    private final NamespacedKey chave;
    private final Map<String, Oficina> oficinas = new LinkedHashMap<>();
    private final Map<String, Mannequin> entidades = new HashMap<>();

    public NpcsManager(OficinasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "oficinas.yml");
        this.chave = new NamespacedKey(plugin, "oficina");
        carregar();
    }

    public Map<String, Oficina> getOficinas() {
        return oficinas;
    }

    public Oficina get(String id) {
        return oficinas.get(id.toLowerCase(Locale.ROOT));
    }

    /** A oficina dessa entidade, ou null se ela não for um NPC de oficina. */
    public Oficina getOficina(Entity entidade) {
        if (!(entidade instanceof Mannequin)) return null;
        String id = entidade.getPersistentDataContainer().get(chave, PersistentDataType.STRING);
        return id == null ? null : oficinas.get(id);
    }

    /** Cria (ou move, se o id já existe) a oficina e põe o NPC. */
    public Oficina criar(String id, Oficina.Tipo tipo, String alvo, Location local) {
        Oficina antiga = oficinas.remove(id.toLowerCase(Locale.ROOT));
        if (antiga != null) tirar(antiga);
        Oficina o = new Oficina(id.toLowerCase(Locale.ROOT), tipo, alvo, local.getWorld().getName(),
                local.getX(), local.getY(), local.getZ(), local.getYaw());
        oficinas.put(o.getId(), o);
        salvar();
        atualizar(o);
        return o;
    }

    public void remover(Oficina o) {
        oficinas.remove(o.getId());
        tirar(o);
        salvar();
    }

    private void tirar(Oficina o) {
        Mannequin m = entidades.remove(o.getId());
        if (m != null) m.remove();
    }

    /** Põe ou refaz o NPC (se o chunk estiver carregado). */
    public void atualizar(Oficina o) {
        tirar(o);
        Location local = o.getLocal();
        if (local == null || !local.getWorld().isChunkLoaded(local.getBlockX() >> 4, local.getBlockZ() >> 4)) return;
        String base = "tipos." + o.getTipo().chave() + ".";
        String skin = texto(base + "skin", "");
        Mannequin m = local.getWorld().spawn(local, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setImmovable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setCustomNameVisible(true);
            // {mina}: o id da mina do guia, com a primeira letra maiúscula
            String alvo = o.getAlvo() == null ? "" : o.getAlvo().substring(0, 1).toUpperCase(Locale.ROOT) + o.getAlvo().substring(1);
            e.customName(LEGADO.deserialize(texto(base + "nome", o.getTipo().chave()).replace("{mina}", alvo)));
            e.setDescription(LEGADO.deserialize(texto(base + "descricao", "&7Clique").replace("{mina}", alvo)));
            if (!skin.isBlank()) e.setProfile(perfil(skin));
            e.getPersistentDataContainer().set(chave, PersistentDataType.STRING, o.getId());
        });
        entidades.put(o.getId(), m);
    }

    /**
     * O texto do config, caindo no config.yml do jar quando o do servidor não tem a chave (um config
     * antigo não tem os tipos novos, como o Bancário). Passar o padrão direto pro getString pulava o
     * do jar, e o NPC aparecia com o id cru e sem skin.
     */
    private String texto(String caminho, String seNada) {
        String valor = plugin.getConfig().getString(caminho);
        return valor != null ? valor : seNada;
    }

    /**
     * A skin do config: "textura:<hash>" usa direto a textura do textures.minecraft.net (a skin
     * temática, sem depender de um jogador); qualquer outra coisa é o nick de quem empresta a skin.
     * O perfil da textura vai completo (UUID e nome fixos, tirados do hash): sem eles o cliente trata
     * como perfil incompleto e pode mostrar a skin padrão.
     */
    private static ResolvableProfile perfil(String skin) {
        if (skin.startsWith("textura:")) {
            String hash = skin.substring("textura:".length()).trim();
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String valor = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ResolvableProfile.resolvableProfile()
                    .uuid(java.util.UUID.nameUUIDFromBytes(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .name("npc" + hash.substring(0, Math.min(10, hash.length())))
                    .addProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", valor)).build();
        }
        return ResolvableProfile.resolvableProfile().name(skin).build();
    }

    public void carregarTodos() {
        oficinas.values().forEach(this::atualizar);
    }

    public void removerTodos() {
        entidades.values().forEach(Entity::remove);
        entidades.clear();
    }

    /** A cada tanto: um NPC que sumiu (um /kill não passa pelo evento de morte) volta. */
    public void conferir() {
        for (Oficina o : oficinas.values()) {
            Mannequin m = entidades.get(o.getId());
            if (m == null || !m.isValid()) atualizar(o);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (getOficina(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        Oficina o = getOficina(evento.getEntity());
        if (o == null) return;
        evento.getDrops().clear();
        Bukkit.getScheduler().runTask(plugin, () -> atualizar(o));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        Chunk chunk = evento.getChunk();
        for (Oficina o : oficinas.values()) {
            if (o.getMundo().equals(chunk.getWorld().getName())
                    && ((int) Math.floor(o.getX()) >> 4) == chunk.getX() && ((int) Math.floor(o.getZ()) >> 4) == chunk.getZ()) {
                atualizar(o);
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

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("oficinas");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            Oficina.Tipo tipo = s == null ? null : Oficina.Tipo.de(s.getString("tipo", ""));
            if (tipo == null) {
                plugin.getLogger().warning("Oficina inválida ignorada em oficinas.yml: " + id);
                continue;
            }
            oficinas.put(id, new Oficina(id, tipo, s.getString("alvo"), s.getString("mundo", "world"),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw")));
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Oficina o : oficinas.values()) {
            String base = "oficinas." + o.getId();
            yaml.set(base + ".tipo", o.getTipo().chave());
            if (o.getAlvo() != null) yaml.set(base + ".alvo", o.getAlvo());
            yaml.set(base + ".mundo", o.getMundo());
            yaml.set(base + ".x", o.getX());
            yaml.set(base + ".y", o.getY());
            yaml.set(base + ".z", o.getZ());
            yaml.set(base + ".yaw", (double) o.getYaw());
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar oficinas.yml", e);
        }
    }
}
