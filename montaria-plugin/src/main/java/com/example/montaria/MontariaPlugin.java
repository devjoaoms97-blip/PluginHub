package com.example.montaria;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Montaria do VIP no spawn: {@code /montaria} chama um cavalo (ou outra montaria do config) pra andar
 * mais rápido pelo mundo spawn, onde não dá pra voar. Repetir o comando manda ela embora.
 * <p>
 * É descartável: some quando o jogador desce, sai do servidor, se teleporta, troca de mundo, toma dano
 * ou entra num lugar proibido (os poços das minas do RankupPlugin e as caixas de {@code proibido}, como
 * a arena do boss). Ninguém mais monta nela, não tem baú nem inventário, não toma dano e não fica salva
 * no mundo. O pulo é baixo (sobe um degrau, não pula balcão nem barraca) e a velocidade é
 * {@code velocidade-vezes-corrida} vezes a corrida a pé.
 */
public class MontariaPlugin extends JavaPlugin implements Listener {

    /** Velocidade da corrida a pé, em blocos por segundo. */
    private static final double CORRIDA = 5.6;
    /** O atributo de velocidade do cavalo, em blocos por segundo (o jogo multiplica por ~43,17). */
    private static final double BLOCOS_POR_SEGUNDO_POR_PONTO = 43.17;

    private record Caixa(String nome, String mundo, double[] min, double[] max) {
        boolean contem(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(mundo)) return false;
            return l.getX() >= min[0] && l.getX() <= max[0] + 1 && l.getY() >= min[1] && l.getY() <= max[1] + 1
                    && l.getZ() >= min[2] && l.getZ() <= max[2] + 1;
        }
    }

    private NamespacedKey marca;
    /** jogador → a montaria dele (o id da entidade). */
    private final Map<UUID, UUID> montarias = new HashMap<>();
    private final Map<UUID, Long> ultimoUso = new HashMap<>();
    private Method getMinaManager, getMinaEm;
    private boolean avisouFalhaRankup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        marca = new NamespacedKey(this, "montaria");
        getServer().getPluginManager().registerEvents(this, this);
        // quem entra num lugar proibido desce na hora
        getServer().getScheduler().runTaskTimer(this, this::conferir, 10L, 10L);
        getLogger().info("MontariaPlugin ativado!");
    }

    @Override
    public void onDisable() {
        new HashMap<>(montarias).keySet().forEach(id -> mandarEmbora(id, null));
    }

    // ---------------------------------------------------------------------
    // /montaria
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && jogador.hasPermission("montaria.admin")) {
            reloadConfig();
            jogador.sendMessage("§aConfig da montaria recarregado.");
            return true;
        }
        if (montarias.containsKey(jogador.getUniqueId())) {
            mandarEmbora(jogador.getUniqueId(), "§7Sua montaria foi embora.");
            return true;
        }
        if (!jogador.hasPermission("vip.perk.montaria")) {
            jogador.sendMessage(cores(getConfig().getString("mensagens.sem-permissao", "&cA montaria é um benefício do VIP.")));
            return true;
        }
        String proibido = motivoProibido(jogador.getLocation());
        if (proibido != null) {
            jogador.sendMessage(cores(getConfig().getString("mensagens.lugar-proibido", "&cAqui não dá pra usar a montaria (%lugar%).")
                    .replace("%lugar%", proibido)));
            return true;
        }
        if (jogador.isInsideVehicle() || jogador.isFlying() || jogador.isGliding()) {
            jogador.sendMessage("§cDesça antes de chamar a montaria.");
            return true;
        }
        long recarga = getConfig().getLong("recarga-segundos", 5) * 1000;
        long falta = ultimoUso.getOrDefault(jogador.getUniqueId(), 0L) + recarga - System.currentTimeMillis();
        if (falta > 0) {
            jogador.sendMessage("§cEspere " + (falta + 999) / 1000 + "s pra chamar a montaria de novo.");
            return true;
        }
        chamar(jogador);
        return true;
    }

    private void chamar(Player jogador) {
        EntityType tipo = tipoPara(jogador);
        Location onde = jogador.getLocation();
        Entity entidade = onde.getWorld().spawn(onde, tipo.getEntityClass(), e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setSilent(getConfig().getBoolean("silenciosa", false));
            e.getPersistentDataContainer().set(marca, PersistentDataType.STRING, jogador.getUniqueId().toString());
            e.setCustomName(cores(getConfig().getString("nome", "&6Montaria de %jogador%").replace("%jogador%", jogador.getName())));
            e.setCustomNameVisible(false);
            if (e instanceof AbstractHorse cavalo) {
                cavalo.setTamed(true);
                cavalo.setOwner(jogador);
                cavalo.setAdult();
                cavalo.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                if (cavalo instanceof Horse h) {
                    h.setColor(Horse.Color.CHESTNUT);
                    h.setStyle(Horse.Style.WHITE);
                }
                double velocidade = CORRIDA * getConfig().getDouble("velocidade-vezes-corrida", 1.6) / BLOCOS_POR_SEGUNDO_POR_PONTO;
                definir(cavalo, Attribute.MOVEMENT_SPEED, velocidade);
                definir(cavalo, Attribute.JUMP_STRENGTH, getConfig().getDouble("pulo", 0.5));
            }
        });
        entidade.addPassenger(jogador);
        montarias.put(jogador.getUniqueId(), entidade.getUniqueId());
        ultimoUso.put(jogador.getUniqueId(), System.currentTimeMillis());
        jogador.playSound(onde, Sound.ENTITY_HORSE_SADDLE, 1f, 1f);
    }

    private static void definir(Entity e, Attribute atributo, double valor) {
        if (!(e instanceof org.bukkit.entity.LivingEntity vivo)) return;
        AttributeInstance a = vivo.getAttribute(atributo);
        if (a != null) a.setBaseValue(valor);
    }

    /** A montaria de quem tem a permissão de {@code tipos} (a primeira da lista); sem nenhuma, {@code tipo-padrao}. */
    private EntityType tipoPara(Player jogador) {
        for (Map<?, ?> m : getConfig().getMapList("tipos")) {
            if (m.get("permissao") instanceof String perm && jogador.hasPermission(perm) && m.get("tipo") instanceof String t) {
                EntityType tipo = tipoValido(t);
                if (tipo != null) return tipo;
            }
        }
        EntityType padrao = tipoValido(getConfig().getString("tipo-padrao", "HORSE"));
        return padrao != null ? padrao : EntityType.HORSE;
    }

    private static EntityType tipoValido(String nome) {
        try {
            EntityType t = EntityType.valueOf(nome.toUpperCase(Locale.ROOT));
            return t.getEntityClass() != null && AbstractHorse.class.isAssignableFrom(t.getEntityClass()) ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Some com a montaria do jogador; {@code mensagem} vai pra ele (null = sem mensagem). */
    private void mandarEmbora(UUID jogadorId, String mensagem) {
        UUID id = montarias.remove(jogadorId);
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        Player jogador = Bukkit.getPlayer(jogadorId);
        if (e != null) {
            e.eject();
            e.remove();
        }
        if (jogador != null && mensagem != null) jogador.sendMessage(mensagem);
    }

    private UUID donoDe(Entity e) {
        String dono = e.getPersistentDataContainer().get(marca, PersistentDataType.STRING);
        try {
            return dono == null ? null : UUID.fromString(dono);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Onde pode
    // ---------------------------------------------------------------------

    /** Por que não pode usar a montaria aqui (o nome do lugar), ou null se pode. */
    private String motivoProibido(Location l) {
        List<String> mundos = getConfig().getStringList("mundos");
        if (!mundos.contains(l.getWorld().getName())) return "só no spawn";
        if (emMina(l)) return "dentro da mina";
        for (Map<?, ?> m : getConfig().getMapList("proibido")) {
            Caixa c = caixa(m);
            if (c != null && c.contem(l)) return c.nome();
        }
        return null;
    }

    private static Caixa caixa(Map<?, ?> m) {
        if (!(m.get("min") instanceof List<?> min) || !(m.get("max") instanceof List<?> max) || min.size() != 3 || max.size() != 3) return null;
        double[] a = new double[3], b = new double[3];
        for (int i = 0; i < 3; i++) {
            double x = ((Number) min.get(i)).doubleValue(), y = ((Number) max.get(i)).doubleValue();
            a[i] = Math.min(x, y);
            b[i] = Math.max(x, y);
        }
        return new Caixa(String.valueOf(m.get("nome")), String.valueOf(m.get("mundo")), a, b);
    }

    /** Dentro de uma mina do RankupPlugin? (por reflexão: sem ele, nenhuma mina) */
    private boolean emMina(Location l) {
        Plugin rankup = getServer().getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return false;
        try {
            if (getMinaManager == null) getMinaManager = rankup.getClass().getMethod("getMinaManager");
            Object minas = getMinaManager.invoke(rankup);
            if (getMinaEm == null) getMinaEm = minas.getClass().getMethod("getMinaEm", Location.class);
            return getMinaEm.invoke(minas, l) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!avisouFalhaRankup) {
                getLogger().warning("Não foi possível consultar as minas no RankupPlugin (versão antiga?): " + e.getMessage());
                avisouFalhaRankup = true;
            }
            return false;
        }
    }

    /** A cada meio segundo: a montaria que entrou num lugar proibido (ou ficou sem cavaleiro) some. */
    private void conferir() {
        for (Map.Entry<UUID, UUID> e : new HashMap<>(montarias).entrySet()) {
            Entity montaria = Bukkit.getEntity(e.getValue());
            Player jogador = Bukkit.getPlayer(e.getKey());
            if (montaria == null || jogador == null || !montaria.getPassengers().contains(jogador)) {
                mandarEmbora(e.getKey(), null);
                continue;
            }
            String proibido = motivoProibido(montaria.getLocation());
            if (proibido != null) {
                mandarEmbora(e.getKey(), cores(getConfig().getString("mensagens.saiu", "&7Sua montaria não entra aqui (%lugar%): siga a pé.")
                        .replace("%lugar%", proibido)));
            }
        }
    }

    // ---------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------

    @EventHandler
    public void aoDescer(EntityDismountEvent evento) {
        UUID dono = donoDe(evento.getDismounted());
        if (dono != null && evento.getEntity().getUniqueId().equals(dono)) {
            // um tick depois: descer no meio do evento e remover a entidade junto dá problema
            Bukkit.getScheduler().runTask(this, () -> mandarEmbora(dono, null));
        }
    }

    /** Ninguém mais monta, abre o inventário nem põe guia na montaria de outro. */
    @EventHandler(priority = EventPriority.LOW)
    public void aoInteragir(PlayerInteractEntityEvent evento) {
        UUID dono = donoDe(evento.getRightClicked());
        if (dono != null && !dono.equals(evento.getPlayer().getUniqueId())) evento.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void aoAbrirInventario(InventoryOpenEvent evento) {
        if (evento.getInventory().getHolder() instanceof Entity e && donoDe(e) != null) evento.setCancelled(true);
    }

    /** Tomou dano montado (mob, queda, o que for): desce e a montaria some. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity() instanceof Player p && montarias.containsKey(p.getUniqueId())) {
            mandarEmbora(p.getUniqueId(), "§7Você levou um golpe e desceu da montaria.");
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        mandarEmbora(evento.getPlayer().getUniqueId(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        if (montarias.containsKey(evento.getPlayer().getUniqueId())) mandarEmbora(evento.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void aoTrocarDeMundo(PlayerChangedWorldEvent evento) {
        mandarEmbora(evento.getPlayer().getUniqueId(), null);
    }

    private static String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }
}
