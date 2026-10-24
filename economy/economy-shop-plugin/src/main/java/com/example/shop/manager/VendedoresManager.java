package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Os vendedores da Loja Oficial: um NPC por categoria (minerador, construtor, fazendeiro...). Clique
 * direito abre o menu só da categoria dele, onde se compra e vende. Um vendedor com {@code comando} no
 * config (ex: "spawners loja") abre esse comando em nome do jogador: é assim que os balcões de outros
 * plugins (spawners, máquinas) usam o mesmo NPC. Nome, categoria e skin vêm de
 * {@code vendedores.<id>} no config; o lugar de cada um fica em vendedores.yml
 * ({@code /shop admin npc <id> ...}). Mesmo esquema dos outros NPCs: Mannequin que não fica salvo
 * no mundo, não toma dano e volta sozinho.
 */
public class VendedoresManager implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();

    /** Onde o NPC fica. */
    public record Lugar(String mundo, double x, double y, double z, float yaw) {
        Location local() {
            World w = Bukkit.getWorld(mundo);
            return w == null ? null : new Location(w, x, y, z, yaw, 0);
        }
    }

    private final ShopPlugin plugin;
    private final File arquivo;
    private final NamespacedKey chave;
    private final Map<String, Lugar> lugares = new LinkedHashMap<>();
    private final Map<String, Mannequin> entidades = new HashMap<>();

    public VendedoresManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "vendedores.yml");
        this.chave = new NamespacedKey(plugin, "vendedor");
        carregar();
    }

    public Map<String, Lugar> getLugares() {
        return lugares;
    }

    /** A categoria da loja que esse vendedor atende (do config), ou null. */
    public String categoriaDe(String id) {
        return plugin.getConfig().getString("vendedores." + id + ".categoria");
    }

    public boolean existeNoConfig(String id) {
        return plugin.getConfig().isConfigurationSection("vendedores." + id.toLowerCase(Locale.ROOT));
    }

    public void colocar(String id, Location local) {
        id = id.toLowerCase(Locale.ROOT);
        lugares.put(id, new Lugar(local.getWorld().getName(), local.getX(), local.getY(), local.getZ(), local.getYaw()));
        salvar();
        atualizar(id);
    }

    public boolean remover(String id) {
        id = id.toLowerCase(Locale.ROOT);
        if (lugares.remove(id) == null) return false;
        tirar(id);
        salvar();
        return true;
    }

    private void tirar(String id) {
        Mannequin m = entidades.remove(id);
        if (m != null) m.remove();
    }

    public void atualizar(String id) {
        tirar(id);
        Lugar lugar = lugares.get(id);
        Location local = lugar == null ? null : lugar.local();
        if (local == null || !local.getWorld().isChunkLoaded(local.getBlockX() >> 4, local.getBlockZ() >> 4)) return;
        String base = "vendedores." + id + ".";
        String skin = plugin.getConfig().getString(base + "skin", "");
        Mannequin m = local.getWorld().spawn(local, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setImmovable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setCustomNameVisible(true);
            e.customName(LEGADO.deserialize(plugin.getConfig().getString(base + "nome", id)));
            e.setDescription(LEGADO.deserialize(plugin.getConfig().getString(base + "descricao", "&7Clique pra comprar e vender")));
            if (!skin.isBlank()) e.setProfile(perfil(skin));
            e.getPersistentDataContainer().set(chave, PersistentDataType.STRING, id);
        });
        entidades.put(id, m);
    }

    /** "textura:<hash>" usa a textura do textures.minecraft.net direto; o resto é o nick de quem empresta. */
    private static ResolvableProfile perfil(String skin) {
        if (skin.startsWith("textura:")) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                    + skin.substring("textura:".length()).trim() + "\"}}}";
            return ResolvableProfile.resolvableProfile().addProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)))).build();
        }
        return ResolvableProfile.resolvableProfile().name(skin).build();
    }

    public void carregarTodos() {
        lugares.keySet().forEach(this::atualizar);
    }

    public void removerTodos() {
        entidades.values().forEach(Entity::remove);
        entidades.clear();
    }

    public void conferir() {
        for (String id : lugares.keySet()) {
            Mannequin m = entidades.get(id);
            if (m == null || !m.isValid()) atualizar(id);
        }
    }

    private String idDe(Entity e) {
        return e instanceof Mannequin ? e.getPersistentDataContainer().get(chave, PersistentDataType.STRING) : null;
    }

    @EventHandler
    public void aoClicar(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        String id = idDe(evento.getRightClicked());
        if (id == null) return;
        evento.setCancelled(true);
        // vendedor de outro plugin (spawners, máquinas): abre o comando dele em nome do jogador
        String comando = plugin.getConfig().getString("vendedores." + id + ".comando");
        if (comando != null && !comando.isBlank()) {
            evento.getPlayer().performCommand(comando);
            return;
        }
        String categoria = categoriaDe(id);
        if (categoria == null) {
            evento.getPlayer().sendMessage("§cEsse vendedor está sem categoria no config. Avise a staff.");
            return;
        }
        evento.getPlayer().openInventory(plugin.getShopGUI().montarItens(categoria, 0, evento.getPlayer(), true));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (idDe(evento.getEntity()) != null) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        String id = idDe(evento.getEntity());
        if (id == null) return;
        evento.getDrops().clear();
        Bukkit.getScheduler().runTask(plugin, () -> atualizar(id));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        Chunk c = evento.getChunk();
        lugares.forEach((id, l) -> {
            if (l.mundo().equals(c.getWorld().getName()) && ((int) Math.floor(l.x()) >> 4) == c.getX()
                    && ((int) Math.floor(l.z()) >> 4) == c.getZ()) {
                atualizar(id);
            }
        });
    }

    @EventHandler
    public void aoDescarregar(ChunkUnloadEvent evento) {
        Chunk c = evento.getChunk();
        entidades.values().removeIf(m -> {
            Location l = m.getLocation();
            if (!c.getWorld().equals(l.getWorld()) || l.getBlockX() >> 4 != c.getX() || l.getBlockZ() >> 4 != c.getZ()) return false;
            m.remove();
            return true;
        });
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("vendedores");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            if (s == null) continue;
            lugares.put(id, new Lugar(s.getString("mundo", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw")));
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        lugares.forEach((id, l) -> {
            String base = "vendedores." + id;
            yaml.set(base + ".mundo", l.mundo());
            yaml.set(base + ".x", l.x());
            yaml.set(base + ".y", l.y());
            yaml.set(base + ".z", l.z());
            yaml.set(base + ".yaw", (double) l.yaw());
        });
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar vendedores.yml", e);
        }
    }
}
