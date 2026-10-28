package com.example.montaria;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
 * <p>
 * Skin de montaria (comprada com créditos no CreditosPlugin, que diz qual está em uso): o cavalo
 * continua sendo a montaria (mesma velocidade, pulo e regras), mas fica invisível, com a sela escondida
 * (o equipamento "invisivel" do resource pack), e leva junto um item display com o modelo 3D do bicho
 * (resource pack, tools/pacote/montarias.py). A cada tick o modelo vira pro lado do cavalo e, andando,
 * troca de quadro (pernas e asas).
 * <p>
 * Skin com {@code fmm-modelo} no config: em vez do nosso modelo, o FreeMinecraftModels desenha um modelo
 * animado do Blockbench em cima do cavalo (DynamicEntity, por reflexão: sem o FMM, a skin sai sem modelo).
 * O cavalo continua sendo a montaria, com as mesmas regras; o dano por contato do modelo fica desligado.
 */
public class MontariaPlugin extends JavaPlugin implements Listener {

    /** Velocidade da corrida a pé, em blocos por segundo. */
    private static final double CORRIDA = 5.6;
    /** O atributo de velocidade do cavalo, em blocos por segundo (o jogo multiplica por ~43,17). */
    private static final double BLOCOS_POR_SEGUNDO_POR_PONTO = 43.17;
    /** Quanto o modelo do bicho foi encolhido no resource pack (montarias.py): o display aumenta de novo. */
    private static final float ESCALA_MODELO = 3f;
    /** No modelo, o chão fica tantos blocos abaixo do centro do display (antes da escala: -16 de -0.5..0.5 → -1,5). */
    private static final float CHAO_DO_MODELO = 1.5f * ESCALA_MODELO;
    /** Altura do assento do cavalo (ponto de passageiro), em blocos, na escala 1. */
    private static final double ASSENTO_DO_CAVALO = 1.31875;

    /** O modelo em cima de uma montaria com skin, e o quadro da animação. */
    private static final class Visual {
        final UUID display;
        final String skin;
        final ItemStack[] quadros = new ItemStack[5];  // parado, 0..3
        Location ultima;
        int tick;
        int quadro = -1;

        Visual(UUID display, String skin) {
            this.display = display;
            this.skin = skin;
        }
    }

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
    /** jogador → o modelo da skin em cima da montaria dele. */
    private final Map<UUID, Visual> visuais = new HashMap<>();
    /** jogador → o modelo do FreeMinecraftModels (DynamicEntity) na montaria dele. */
    private final Map<UUID, Object> modelosFmm = new HashMap<>();
    private boolean avisouFalhaFmm;
    private Method getMinaManager, getMinaEm, montariaEmUso;
    private boolean avisouFalhaRankup, avisouFalhaCreditos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        marca = new NamespacedKey(this, "montaria");
        getServer().getPluginManager().registerEvents(this, this);
        // quem entra num lugar proibido desce na hora
        getServer().getScheduler().runTaskTimer(this, this::conferir, 10L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::animar, 1L, 1L);
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
        String skin = skinDe(jogador);
        // com skin, sempre o cavalo: o modelo foi feito pra altura do assento dele
        EntityType tipo = skin != null ? EntityType.HORSE : tipoPara(jogador);
        double escala = skin != null ? getConfig().getDouble("skins." + skin + ".escala-do-cavalo", 1.0) : 1.0;
        Location onde = jogador.getLocation();
        Entity entidade = onde.getWorld().spawn(onde, tipo.getEntityClass(), e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setSilent(skin != null || getConfig().getBoolean("silenciosa", false));
            e.getPersistentDataContainer().set(marca, PersistentDataType.STRING, jogador.getUniqueId().toString());
            e.setCustomName(cores(getConfig().getString("nome", "&6Montaria de %jogador%").replace("%jogador%", jogador.getName())));
            e.setCustomNameVisible(false);
            if (e instanceof AbstractHorse cavalo) {
                cavalo.setTamed(true);
                cavalo.setOwner(jogador);
                cavalo.setAdult();
                cavalo.getInventory().setSaddle(skin != null ? selaInvisivel() : new ItemStack(Material.SADDLE));
                if (cavalo instanceof Horse h) {
                    h.setColor(Horse.Color.CHESTNUT);
                    h.setStyle(Horse.Style.WHITE);
                }
                double velocidade = CORRIDA * getConfig().getDouble("velocidade-vezes-corrida", 3.0) / BLOCOS_POR_SEGUNDO_POR_PONTO;
                definir(cavalo, Attribute.MOVEMENT_SPEED, velocidade);
                definir(cavalo, Attribute.JUMP_STRENGTH, getConfig().getDouble("pulo", 0.5));
                if (skin != null) {
                    cavalo.setInvisible(true);
                    definir(cavalo, Attribute.SCALE, escala);
                }
            }
        });
        entidade.addPassenger(jogador);
        montarias.put(jogador.getUniqueId(), entidade.getUniqueId());
        if (skin != null) {
            String fmm = getConfig().getString("skins." + skin + ".fmm-modelo");
            if (fmm != null && entidade instanceof LivingEntity cavalo) vestirFmm(jogador, cavalo, fmm);
            else vestir(jogador, entidade, skin, escala);
        }
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

    // ---------------------------------------------------------------------
    // Skin (o modelo do bicho em cima do cavalo invisível)
    // ---------------------------------------------------------------------

    /** A skin de montaria em uso no CreditosPlugin (por reflexão: sem ele, nenhuma), se o pacote tem esse bicho. */
    private String skinDe(Player jogador) {
        Plugin creditos = getServer().getPluginManager().getPlugin("CreditosPlugin");
        if (creditos == null || !creditos.isEnabled()) return null;
        try {
            if (montariaEmUso == null) montariaEmUso = creditos.getClass().getMethod("montariaEmUso", UUID.class);
            Object skin = montariaEmUso.invoke(creditos, jogador.getUniqueId());
            return skin instanceof String s && !s.isEmpty() ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!avisouFalhaCreditos) {
                getLogger().warning("Não foi possível consultar a skin de montaria no CreditosPlugin (versão antiga?): " + e.getMessage());
                avisouFalhaCreditos = true;
            }
            return null;
        }
    }

    /** Põe o modelo animado do FreeMinecraftModels no cavalo (por reflexão). */
    private void vestirFmm(Player jogador, LivingEntity cavalo, String modelo) {
        Plugin fmm = getServer().getPluginManager().getPlugin("FreeMinecraftModels");
        if (fmm == null || !fmm.isEnabled()) return;
        try {
            Class<?> dinamica = Class.forName("com.magmaguy.freeminecraftmodels.customentity.DynamicEntity", true, fmm.getClass().getClassLoader());
            Object m = dinamica.getMethod("createWithInvisibility", String.class, LivingEntity.class).invoke(null, modelo, cavalo);
            if (m == null) {
                getLogger().warning("O FreeMinecraftModels não tem o modelo '" + modelo + "' (skins no config).");
                return;
            }
            dinamica.getMethod("setDamagesOnContact", boolean.class).invoke(m, false);
            modelosFmm.put(jogador.getUniqueId(), m);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!avisouFalhaFmm) {
                getLogger().warning("Não foi possível pôr o modelo do FreeMinecraftModels (versão diferente?): " + e);
                avisouFalhaFmm = true;
            }
        }
    }

    /** Sela com o equipamento "invisivel" do resource pack (sem camada nenhuma): funciona, mas não aparece. */
    private static ItemStack selaInvisivel() {
        ItemStack sela = new ItemStack(Material.SADDLE);
        Equippable atual = sela.getData(DataComponentTypes.EQUIPPABLE);
        if (atual != null) {
            sela.setData(DataComponentTypes.EQUIPPABLE, atual.toBuilder().assetId(new NamespacedKey("servidor", "invisivel")).build());
        }
        return sela;
    }

    private static ItemStack quadro(String skin, String sufixo) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey("servidor", "montaria_" + skin + sufixo));
        return item;
    }

    /** Põe o modelo do bicho como segundo passageiro da montaria (o primeiro, o jogador, é quem guia). */
    private void vestir(Player jogador, Entity montaria, String skin, double escala) {
        float altura = (float) (CHAO_DO_MODELO - ASSENTO_DO_CAVALO * escala + getConfig().getDouble("skins." + skin + ".ajuste-altura", 0));
        ItemStack parado = quadro(skin, "");
        ItemDisplay display = montaria.getWorld().spawn(montaria.getLocation(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(marca, PersistentDataType.STRING, jogador.getUniqueId().toString());
            d.setItemStack(parado);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0, altura, 0), new AxisAngle4f(),
                    new Vector3f(ESCALA_MODELO, ESCALA_MODELO, ESCALA_MODELO), new AxisAngle4f()));
            d.setTeleportDuration(2);
            d.setRotation(rumo(montaria), 0);
        });
        montaria.addPassenger(display);
        Visual v = new Visual(display.getUniqueId(), skin);
        v.quadros[0] = parado;
        for (int i = 1; i < 5; i++) v.quadros[i] = quadro(skin, "_" + (i - 1));
        v.ultima = montaria.getLocation();
        visuais.put(jogador.getUniqueId(), v);
    }

    /** Pra onde o modelo olha: o corpo do cavalo, mais o giro do config (o item display desenha o item virado). */
    private float rumo(Entity montaria) {
        float corpo = montaria instanceof LivingEntity vivo ? vivo.getBodyYaw() : montaria.getLocation().getYaw();
        return corpo + (float) getConfig().getDouble("skins-giro", 180);
    }

    /** A cada tick: o modelo vira com o cavalo e, andando (ou sempre, no tapete), troca de quadro. */
    private void animar() {
        for (Map.Entry<UUID, Visual> e : visuais.entrySet()) {
            UUID montariaId = montarias.get(e.getKey());
            Entity montaria = montariaId == null ? null : Bukkit.getEntity(montariaId);
            Visual v = e.getValue();
            if (!(Bukkit.getEntity(v.display) instanceof ItemDisplay display) || montaria == null) continue;
            display.setRotation(rumo(montaria), 0);
            Location agora = montaria.getLocation();
            boolean andando = v.ultima != null && v.ultima.getWorld().equals(agora.getWorld())
                    && (agora.getX() - v.ultima.getX()) * (agora.getX() - v.ultima.getX())
                    + (agora.getZ() - v.ultima.getZ()) * (agora.getZ() - v.ultima.getZ()) > 0.0025;
            v.ultima = agora;
            String base = "skins." + v.skin + ".";
            boolean sempre = getConfig().getBoolean(base + "anima-parado", false);
            int quadro;
            if (andando || sempre) {
                int ritmo = Math.max(1, getConfig().getInt(base + (andando ? "ticks-por-quadro" : "ticks-por-quadro-parado"), andando ? 2 : 5));
                v.tick++;
                quadro = 1 + (v.tick / ritmo) % 4;
            } else {
                v.tick = 0;
                quadro = 0;
            }
            if (quadro != v.quadro) {
                v.quadro = quadro;
                display.setItemStack(v.quadros[quadro]);
            }
            String particula = getConfig().getString(base + "particula");
            if (particula != null && v.tick % 3 == 0) {
                try {
                    Particle p = Particle.valueOf(particula.toUpperCase(Locale.ROOT));
                    if (p.getDataType() == Void.class) agora.getWorld().spawnParticle(p, agora.clone().add(0, 0.3, 0), 2, 0.5, 0.1, 0.8, 0.01);
                } catch (IllegalArgumentException ignorada) {
                    // partícula com nome errado no config: só não sai
                }
            }
        }
    }

    /** Some com a montaria do jogador; {@code mensagem} vai pra ele (null = sem mensagem). */
    private void mandarEmbora(UUID jogadorId, String mensagem) {
        Object modelo = modelosFmm.remove(jogadorId);
        if (modelo != null) {
            try {
                modelo.getClass().getMethod("remove").invoke(modelo);
            } catch (ReflectiveOperationException | RuntimeException e) {
                getLogger().warning("Não foi possível tirar o modelo do FreeMinecraftModels: " + e);
            }
        }
        Visual visual = visuais.remove(jogadorId);
        if (visual != null && Bukkit.getEntity(visual.display) instanceof Entity display) display.remove();
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
