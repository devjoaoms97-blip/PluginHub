package com.example.tesouros;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * As câmaras abertas e tudo o que acontece no mundo dos tesouros: cavar o X, entrar pelo buraco,
 * as ondas, o baú, a morte (perde tudo), a saída e a proteção do mundo. Também solta os mapas: o do
 * boss (sempre um, no chão) e o das minas (raro, direto no inventário).
 * <p>
 * Uma câmara aberta fica no {@code camaras.yml} até fechar: se o servidor cair no meio, ela é desfeita
 * quando o plugin liga de novo.
 */
public class Camaras implements Listener {

    /** A marca do boss mundial (BossPlugin), lida sem dependência. */
    private static final NamespacedKey BOSS = new NamespacedKey("pluginhub", "boss_mob");

    private final TesourosPlugin plugin;
    private final Map<String, Camara> ativas = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> convites = new HashMap<>();
    /** Teleportes do próprio plugin, que passam pelas travas de entrada e saída. */
    private final Set<UUID> levando = new HashSet<>();
    private final File arquivo;

    public Camaras(TesourosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "camaras.yml");
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------

    public Camara camaraDe(Location l) {
        for (Camara c : ativas.values()) if (c.contem(l)) return c;
        return null;
    }

    public Set<UUID> convidados(UUID dono) {
        return convites.computeIfAbsent(dono, k -> new HashSet<>());
    }

    public List<String> ids() {
        return new ArrayList<>(ativas.keySet());
    }

    private boolean noMundo(World w) {
        return w != null && w.getName().equals(plugin.getNomeMundo());
    }

    private boolean podeEntrar(Player p, Camara c) {
        return p.getUniqueId().equals(c.dono) || convidados(c.dono).contains(p.getUniqueId())
                || plugin.getIntegracoes().mesmoCla(c.dono, p.getUniqueId()) || p.hasPermission("tesouros.admin");
    }

    private static boolean staffConstruindo(Player p) {
        return p.hasPermission("tesouros.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    private void barra(Player p, String texto) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(texto));
    }

    private void levar(Player p, Location destino) {
        levando.add(p.getUniqueId());
        try {
            p.teleport(destino, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            levando.remove(p.getUniqueId());
        }
    }

    private void avisarDentro(Camara c, String msg) {
        for (Player p : c.jogadoresDentro()) p.sendMessage(msg);
    }

    // ---------------------------------------------------------------------
    // Cavar o X
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void aoQuebrar(BlockBreakEvent evento) {
        Block b = evento.getBlock();
        if (!noMundo(b.getWorld())) return;
        Player p = evento.getPlayer();
        if (staffConstruindo(p)) return;
        evento.setCancelled(true);
        if (b.getY() != plugin.ySuperficie(b.getWorld())) {
            barra(p, "§7Só se cava o chão, no X de um mapa do tesouro.");
            return;
        }
        ItemStack itemMapa = null;
        Mapas.Mapa mapa = null;
        for (ItemStack item : p.getInventory().getContents()) {
            Mapas.Mapa m = plugin.getMapas().ler(item);
            if (m != null && m.x() == b.getX() && m.z() == b.getZ()) {
                itemMapa = item;
                mapa = m;
                break;
            }
        }
        if (mapa == null) {
            barra(p, "§7Nada aqui. Cave exatamente no X do seu mapa do tesouro.");
            return;
        }
        if (plugin.getMapas().usado(mapa.id())) {
            p.sendMessage("§cEsse mapa já foi usado: o tesouro dele não existe mais.");
            return;
        }
        for (Camara outra : ativas.values()) {
            if (outra.perto(mapa.x(), mapa.z())) {
                p.sendMessage("§cOutro tesouro está sendo aberto aqui do lado. Espere a câmara dele fechar.");
                return;
            }
        }
        abrir(p, mapa, itemMapa, b.getWorld());
    }

    private void abrir(Player dono, Mapas.Mapa mapa, ItemStack itemMapa, World mundo) {
        itemMapa.setAmount(itemMapa.getAmount() - 1);
        plugin.getMapas().marcarUsado(mapa.id());

        String id = Long.toString(System.currentTimeMillis() % 1_000_000_000L, 36);
        Camara c = new Camara(plugin, id, dono.getUniqueId(), dono.getName(), mapa, mundo, plugin.ySuperficie(mundo));
        c.construir();
        ativas.put(id, c);
        salvar();

        Location x = c.superficie();
        mundo.playSound(x, Sound.BLOCK_CHEST_LOCKED, 1f, 0.6f);
        mundo.playSound(x, Sound.AMBIENT_CAVE, 1f, 0.8f);
        mundo.spawnParticle(Particle.SMOKE, x, 40, 0.6, 0.4, 0.6, 0.02);
        String rar = plugin.nomeRaridade(mapa.raridade());

        // o dono e quem pode entrar e está perto descem juntos
        double raioPuxar = Math.pow(plugin.getConfig().getDouble("camara.raio-puxar", 6), 2);
        List<Player> descem = new ArrayList<>();
        descem.add(dono);
        for (Player outro : mundo.getPlayers()) {
            if (!outro.equals(dono) && !outro.isDead() && outro.getLocation().distanceSquared(x) <= raioPuxar && podeEntrar(outro, c)) descem.add(outro);
        }
        for (Player p : descem) {
            entrar(p, c);
            p.sendTitle("§6✦ Tesouro " + rar, "§7Derrote os guardiões pra abrir o baú", 10, 50, 15);
        }
        c.proximaOndaEm = System.currentTimeMillis() + 3000;
        dono.sendMessage("§6✦ §eVocê achou um tesouro " + rar + "§e! §7Os guardiões estão acordando...");
        dono.sendMessage("§7Seu clã e seus convidados (§f/tesouro convidar§7) podem entrar pelo buraco. O baú é só seu.");
    }

    private void entrar(Player p, Camara c) {
        c.participantes.add(p.getUniqueId());
        levar(p, c.chegada());
        p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1f, 0.6f);
        if (!p.getUniqueId().equals(c.dono)) {
            p.sendMessage("§6✦ §eVocê entrou na câmara do tesouro de §f" + c.nomeDono + "§e. §7O baú é dele; os guardiões, de todos.");
        }
        p.sendMessage("§c⚠ Quem morre aqui perde tudo. Não dá pra sair até os guardiões caírem.");
    }

    /** Pisar no buraco do X leva pra dentro, se puder. */
    @EventHandler(ignoreCancelled = true)
    public void aoAndar(PlayerMoveEvent evento) {
        Location para = evento.getTo();
        if (!noMundo(para.getWorld()) || ativas.isEmpty()) return;
        Location de = evento.getFrom();
        if (de.getBlockX() == para.getBlockX() && de.getBlockY() == para.getBlockY() && de.getBlockZ() == para.getBlockZ()) return;
        for (Camara c : ativas.values()) {
            if (para.getBlockX() != c.x || para.getBlockZ() != c.z || para.getBlockY() != c.ySuperficie) continue;
            Player p = evento.getPlayer();
            if (!podeEntrar(p, c)) {
                barra(p, "§cEssa câmara é de " + c.nomeDono + ". Só o clã e os convidados dele entram.");
                evento.setTo(c.saida().setDirection(p.getLocation().getDirection()));
                return;
            }
            entrar(p, c);
            return;
        }
    }

    // ---------------------------------------------------------------------
    // Ondas, tempo e barra (a cada segundo)
    // ---------------------------------------------------------------------

    public void tick() {
        long agora = System.currentTimeMillis();
        long limite = plugin.getConfig().getInt("camara.tempo-limite-segundos", 600) * 1000L;
        long pegar = plugin.getConfig().getInt("camara.tempo-para-pegar-segundos", 90) * 1000L;
        long entreOndas = plugin.getConfig().getInt("camara.segundos-entre-ondas", 4) * 1000L;

        for (Camara c : new ArrayList<>(ativas.values())) {
            if (c.estado == Camara.Estado.LUTANDO) {
                if (agora - c.inicio > limite) {
                    falhar(c, "§cO tempo acabou! A câmara desabou e o tesouro se perdeu.");
                    continue;
                }
                if (c.jogadoresDentro().isEmpty() && agora - c.inicio > 5000) {
                    falhar(c, null);
                    continue;
                }
                int vivos = c.vivos();
                if (vivos == 0 && c.proximaOndaEm == 0) {
                    c.proximaOndaEm = agora + entreOndas;
                    if (c.onda >= 0) avisarDentro(c, "§a✔ Onda vencida! §7Prepare-se...");
                }
                if (c.proximaOndaEm > 0 && agora >= c.proximaOndaEm) {
                    c.proximaOndaEm = 0;
                    if (!c.proximaOnda()) {
                        liberar(c);
                    } else {
                        boolean chefe = c.onda >= c.ondas().size();
                        for (Player p : c.jogadoresDentro()) {
                            p.sendTitle(chefe ? "§c☠ O chefe chegou!" : "§cOnda " + (c.onda + 1) + "/" + c.ondas().size(), "", 5, 30, 10);
                            p.playSound(p.getLocation(), chefe ? Sound.ENTITY_RAVAGER_ROAR : Sound.EVENT_RAID_HORN, 0.8f, 1f);
                        }
                    }
                }
                // guardião parado sem alvo vai atrás de quem está mais perto
                for (UUID u : c.guardioes) {
                    if (Bukkit.getEntity(u) instanceof Mob mob && mob.getTarget() == null) {
                        Player alvo = c.alvoMaisPerto(mob.getLocation());
                        if (alvo != null) mob.setTarget(alvo);
                    }
                }
            } else if (agora - c.liberadaEm > pegar) {
                encerrar(c, "§7A câmara desabou. O que ficou no baú foi pro inventário de " + c.nomeDono + ".");
                continue;
            }
            if (ativas.containsKey(c.id)) {
                c.atualizarBarra(agora, limite);
                c.mundo.spawnParticle(Particle.ENCHANT, c.superficie().add(0, 0.5, 0), 15, 0.3, 0.6, 0.3, 0.5);
            }
        }
        orientarQuemTemMapa();
    }

    /** No mundo dos tesouros, com um mapa na mão: quantos blocos faltam até o X. */
    private void orientarQuemTemMapa() {
        World mundo = plugin.getMundo();
        if (mundo == null) return;
        for (Player p : mundo.getPlayers()) {
            Mapas.Mapa m = plugin.getMapas().ler(p.getInventory().getItemInMainHand());
            if (m == null || camaraDe(p.getLocation()) != null) continue;
            int dx = m.x() - p.getLocation().getBlockX();
            int dz = m.z() - p.getLocation().getBlockZ();
            int distancia = (int) Math.round(Math.sqrt((double) dx * dx + dz * dz));
            barra(p, distancia == 0 ? "§6✦ É aqui! Cave o chão embaixo de você."
                    : "§6✦ §eX " + m.x() + ", Z " + m.z() + " §7· faltam §f" + distancia + " blocos");
        }
    }

    private void liberar(Camara c) {
        c.estado = Camara.Estado.LIBERADO;
        c.liberadaEm = System.currentTimeMillis();
        c.premio = plugin.getLoot().sortear(c.mapa.raridade());
        c.bau = Bukkit.createInventory(c, 54, "§8✦ Tesouro " + plugin.nomeRaridade(c.mapa.raridade()));
        for (ItemStack item : c.premio.itens()) c.bau.addItem(item);

        Location bau = c.blocoBau().getLocation().add(0.5, 1, 0.5);
        c.mundo.playSound(bau, Sound.BLOCK_CHEST_OPEN, 1f, 0.8f);
        c.mundo.playSound(bau, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        c.mundo.spawnParticle(Particle.TOTEM_OF_UNDYING, bau, 60, 0.5, 0.8, 0.5, 0.3);
        for (Player p : c.jogadoresDentro()) {
            p.sendTitle("§a✦ Guardiões derrotados!", p.getUniqueId().equals(c.dono) ? "§7Abra o baú" : "§7O baú é de " + c.nomeDono, 10, 50, 15);
        }
        avisarDentro(c, "§a✦ O baú destravou! §7A câmara desaba em §f"
                + plugin.getConfig().getInt("camara.tempo-para-pegar-segundos", 90) + "s§7. Saia antes com §f/tesouro sair§7.");

        if (plugin.getConfig().getStringList("anunciar").contains(c.mapa.raridade())) {
            String msg = "§6✦ §e" + c.nomeDono + " §7abriu um tesouro " + plugin.nomeRaridade(c.mapa.raridade()) + "§7!";
            Bukkit.broadcastMessage(msg);
            plugin.getIntegracoes().discord("tesouros", msg);
        }
    }

    // ---------------------------------------------------------------------
    // O baú
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarBau(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getClickedBlock() == null) return;
        if (!noMundo(evento.getClickedBlock().getWorld())) return;
        for (Camara c : ativas.values()) {
            if (!evento.getClickedBlock().equals(c.blocoBau())) continue;
            evento.setCancelled(true);
            Player p = evento.getPlayer();
            if (c.estado == Camara.Estado.LUTANDO) {
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
                barra(p, "§c🔒 Trancado! Derrote todos os guardiões.");
            } else if (!p.getUniqueId().equals(c.dono)) {
                barra(p, "§cO tesouro é de " + c.nomeDono + ", quem tinha o mapa.");
            } else {
                entregarCoins(c, p);
                p.openInventory(c.bau);
            }
            return;
        }
    }

    private void entregarCoins(Camara c, Player dono) {
        if (c.coinsEntregues || c.premio == null) return;
        c.coinsEntregues = true;
        if (c.premio.coins() > 0 && plugin.getIntegracoes().depositar(dono, c.premio.coins(), "Tesouro " + c.mapa.raridade())) {
            dono.sendMessage("§6✦ §eO baú tinha §f" + plugin.dinheiro(c.premio.coins()) + "§e!");
        }
    }

    /** Do baú só se tira: nada entra nele. */
    @EventHandler(ignoreCancelled = true)
    public void aoMexerNoBau(InventoryClickEvent evento) {
        if (!(evento.getView().getTopInventory().getHolder() instanceof Camara)) return;
        InventoryAction acao = evento.getAction();
        if (evento.getClickedInventory() == evento.getView().getTopInventory()) {
            switch (acao) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, DROP_ALL_SLOT, DROP_ONE_SLOT -> { }
                default -> evento.setCancelled(true);
            }
        } else if (acao == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoArrastarNoBau(InventoryDragEvent evento) {
        if (!(evento.getView().getTopInventory().getHolder() instanceof Camara)) return;
        int topo = evento.getView().getTopInventory().getSize();
        for (int slot : evento.getRawSlots()) {
            if (slot < topo) {
                evento.setCancelled(true);
                return;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Fim: sucesso, falha, saída
    // ---------------------------------------------------------------------

    /** Fecha com sucesso: o resto do tesouro vai pro dono, todo mundo sobe e a sala volta a ser terra. */
    private void encerrar(Camara c, String msg) {
        Player dono = Bukkit.getPlayer(c.dono);
        List<ItemStack> resto = new ArrayList<>();
        if (c.bau != null) {
            for (ItemStack i : c.bau.getContents()) if (i != null && !i.getType().isAir()) resto.add(i);
            c.bau.clear();
            new ArrayList<>(c.bau.getViewers()).forEach(v -> v.closeInventory());
        }
        fechar(c, msg);
        if (dono != null && dono.isOnline() && !dono.isDead()) {
            entregarCoins(c, dono);
            for (ItemStack i : resto) dono.getInventory().addItem(i).values().forEach(s -> dono.getWorld().dropItem(dono.getLocation(), s));
        }
    }

    /** Fecha sem tesouro (morte do dono, tempo, abandono). Quem está vivo sobe com os próprios itens. */
    private void falhar(Camara c, String msg) {
        if (c.bau != null) {
            new ArrayList<>(c.bau.getViewers()).forEach(v -> v.closeInventory());
            c.bau.clear();
        }
        fechar(c, msg);
    }

    private void fechar(Camara c, String msg) {
        ativas.remove(c.id);
        c.removerGuardioes();
        c.tirarBarra();
        for (Player p : c.jogadoresDentro()) {
            levar(p, c.saida());
            if (msg != null) p.sendMessage(msg);
        }
        c.desfazer();
        salvar();
    }

    public void sair(Player p) {
        Camara c = camaraDe(p.getLocation());
        if (c == null) {
            p.sendMessage("§cVocê não está numa câmara.");
            return;
        }
        if (c.estado == Camara.Estado.LUTANDO && !p.hasPermission("tesouros.admin")) {
            p.sendMessage("§cNão dá pra sair com guardiões vivos.");
            return;
        }
        if (p.getUniqueId().equals(c.dono)) {
            encerrar(c, "§7" + c.nomeDono + " saiu com o tesouro, e a câmara desabou.");
        } else {
            levar(p, c.saida());
            p.sendMessage("§7Você voltou pra superfície.");
        }
    }

    /** Quem morre na câmara perde tudo. Se for o dono, o tesouro se perde junto. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoMorrer(PlayerDeathEvent evento) {
        Player p = evento.getEntity();
        Camara c = camaraDe(p.getLocation());
        if (c == null) return;
        evento.setKeepInventory(false);
        evento.setKeepLevel(false);
        evento.getDrops().clear();
        evento.setDroppedExp(0);
        p.sendMessage("§c☠ Você morreu na câmara e perdeu tudo o que carregava.");
        if (p.getUniqueId().equals(c.dono)) {
            // no tick seguinte, fora do evento de morte
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ativas.containsKey(c.id)) falhar(c, "§c☠ " + c.nomeDono + " morreu: a câmara desabou e o tesouro se perdeu.");
            });
        }
    }

    /** Sair do servidor no meio da luta é morrer. Depois da luta, o dono leva o que tinha no baú. */
    @EventHandler(priority = EventPriority.LOW)
    public void aoSair(PlayerQuitEvent evento) {
        Player p = evento.getPlayer();
        convites.remove(p.getUniqueId());
        Camara c = camaraDe(p.getLocation());
        if (c == null) return;
        if (c.estado == Camara.Estado.LUTANDO) {
            p.setHealth(0);
            Bukkit.broadcastMessage("§c☠ " + p.getName() + " fugiu de uma câmara do tesouro e morreu.");
        } else if (p.getUniqueId().equals(c.dono)) {
            encerrar(c, "§7" + c.nomeDono + " saiu do servidor, e a câmara desabou.");
        } else {
            levar(p, c.saida());
        }
    }

    /** Quem entra dentro da terra (câmara que fechou com ele fora do servidor) volta pra superfície. */
    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player p = evento.getPlayer();
        if (!noMundo(p.getWorld())) return;
        int superficie = plugin.ySuperficie(p.getWorld());
        if (p.getLocation().getBlockY() > superficie || camaraDe(p.getLocation()) != null) return;
        Location l = p.getLocation();
        levar(p, new Location(p.getWorld(), l.getBlockX() + 0.5, superficie + 1, l.getBlockZ() + 0.5, l.getYaw(), l.getPitch()));
    }

    /** Sem fugir por teleporte durante a luta, e sem entrar a não ser pelo buraco. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        Player p = evento.getPlayer();
        if (levando.contains(p.getUniqueId()) || p.hasPermission("tesouros.admin")) return;
        Camara de = camaraDe(evento.getFrom());
        Camara para = evento.getTo() == null ? null : camaraDe(evento.getTo());
        if (de != null && de != para && de.estado == Camara.Estado.LUTANDO) {
            evento.setCancelled(true);
            p.sendMessage("§cNão dá pra fugir da câmara enquanto houver guardiões.");
        } else if (para != null && de != para) {
            evento.setCancelled(true);
            p.sendMessage("§cNa câmara só se entra pelo buraco do X.");
        }
    }

    // ---------------------------------------------------------------------
    // Guardiões e drops de mapa
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void aoMorrerMob(EntityDeathEvent evento) {
        if (evento.getEntity().getPersistentDataContainer().has(plugin.chaveGuardiao(), PersistentDataType.STRING)) {
            evento.getDrops().clear(); // o XP fica
            return;
        }
        if (evento.getEntity().getPersistentDataContainer().has(BOSS, PersistentDataType.BYTE) && plugin.getConfig().getBoolean("drops.boss.ativado", true)) {
            String raridade = plugin.getMapas().sortearRaridade(plugin.getConfig().getConfigurationSection("drops.boss.pesos"));
            Location l = evento.getEntity().getLocation();
            l.getWorld().dropItemNaturally(l, plugin.getMapas().criar(raridade));
            Bukkit.broadcastMessage("§6✦ §eO boss deixou cair um §fMapa do Tesouro " + plugin.nomeRaridade(raridade) + "§e!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMinerar(BlockBreakEvent evento) {
        if (!plugin.getConfig().getBoolean("drops.mina.ativado", true)) return;
        Player p = evento.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("drops.mina.chance-por-bloco", 0.00005)) return;
        if (!plugin.getIntegracoes().emMina(evento.getBlock().getLocation())) return;
        String raridade = plugin.getMapas().sortearRaridade(plugin.getConfig().getConfigurationSection("drops.mina.pesos"));
        p.getInventory().addItem(plugin.getMapas().criar(raridade)).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
        p.sendMessage("§6✦ §eVocê achou um §fMapa do Tesouro " + plugin.nomeRaridade(raridade) + "§e! §7Veja as coordenadas e use §f/tesouro ir§7.");
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        if ("lendario".equals(raridade)) Bukkit.broadcastMessage("§6✦ §e" + p.getName() + " §7achou um §6Mapa do Tesouro Lendário §7minerando!");
    }

    // ---------------------------------------------------------------------
    // Proteção do mundo
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (noMundo(evento.getBlock().getWorld()) && !staffConstruindo(evento.getPlayer())) evento.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoDespejar(PlayerBucketEmptyEvent evento) {
        if (noMundo(evento.getBlock().getWorld()) && !staffConstruindo(evento.getPlayer())) evento.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodir(EntityExplodeEvent evento) {
        if (noMundo(evento.getLocation().getWorld())) evento.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        if (noMundo(evento.getBlock().getWorld())) evento.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void aoMobMexerBloco(EntityChangeBlockEvent evento) {
        if (noMundo(evento.getBlock().getWorld()) && !(evento.getEntity() instanceof Player)) evento.setCancelled(true);
    }

    /** No mundo dos tesouros só nascem os guardiões, o que outro plugin soltar e o /summon da staff. */
    @EventHandler(ignoreCancelled = true)
    public void aoNascer(CreatureSpawnEvent evento) {
        if (!noMundo(evento.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason motivo = evento.getSpawnReason();
        if (motivo != CreatureSpawnEvent.SpawnReason.CUSTOM && motivo != CreatureSpawnEvent.SpawnReason.COMMAND) evento.setCancelled(true);
    }

    @EventHandler
    public void aoCarregarMundo(WorldLoadEvent evento) {
        if (noMundo(evento.getWorld())) plugin.prepararMundo(evento.getWorld());
    }

    // ---------------------------------------------------------------------
    // Staff e persistência
    // ---------------------------------------------------------------------

    public void listar(CommandSender sender) {
        if (ativas.isEmpty()) {
            sender.sendMessage("§7Nenhuma câmara aberta.");
            return;
        }
        for (Camara c : ativas.values()) {
            sender.sendMessage("§e" + c.id + "§7: " + c.nomeDono + ", " + plugin.nomeRaridade(c.mapa.raridade()) + "§7, X " + c.x + " Z " + c.z
                    + ", " + (c.estado == Camara.Estado.LUTANDO ? "onda " + (c.onda + 1) + "/" + c.totalDeOndas() : "baú destravado")
                    + ", " + c.jogadoresDentro().size() + " dentro");
        }
    }

    public void encerrarAdmin(CommandSender sender, String id) {
        if (id == null) {
            sender.sendMessage("§cUso: /tesouro encerrar <id|todas> §7(veja /tesouro ativas)");
            return;
        }
        if (id.equalsIgnoreCase("todas")) {
            int n = ativas.size();
            encerrarTodas("§eA staff fechou a câmara.");
            sender.sendMessage("§a" + n + " câmara(s) fechada(s).");
            return;
        }
        Camara c = ativas.get(id);
        if (c == null) {
            sender.sendMessage("§cNão existe câmara aberta com esse id.");
            return;
        }
        falhar(c, "§eA staff fechou a câmara.");
        sender.sendMessage("§aCâmara fechada.");
    }

    /** Fecha todas sem tesouro e sem morte (desligamento ou staff). O mapa já foi gasto. */
    public void encerrarTodas(String msg) {
        for (Camara c : new ArrayList<>(ativas.values())) falhar(c, msg);
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Camara c : ativas.values()) {
            String base = "camaras." + c.id + ".";
            yaml.set(base + "mundo", c.mundo.getName());
            yaml.set(base + "x", c.x);
            yaml.set(base + "z", c.z);
            yaml.set(base + "superficie", c.ySuperficie);
            yaml.set(base + "dono", c.dono.toString());
            yaml.set(base + "raridade", c.mapa.raridade());
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não deu pra salvar o camaras.yml", e);
        }
    }

    /** Câmaras que ficaram abertas porque o servidor caiu: a sala volta a ser terra. */
    public void desfazerPendentes() {
        ConfigurationSection secao = YamlConfiguration.loadConfiguration(arquivo).getConfigurationSection("camaras");
        if (secao == null) return;
        int n = 0;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            World mundo = Bukkit.getWorld(s.getString("mundo", ""));
            if (mundo == null) continue;
            Mapas.Mapa mapa = new Mapas.Mapa(id, s.getString("raridade", "comum"), s.getInt("x"), s.getInt("z"));
            Camara c = new Camara(plugin, id, UUID.fromString(s.getString("dono")), "?", mapa, mundo, s.getInt("superficie"));
            for (int cx = c.minX() >> 4; cx <= c.maxX() >> 4; cx++) {
                for (int cz = c.minZ() >> 4; cz <= c.maxZ() >> 4; cz++) mundo.getChunkAt(cx, cz);
            }
            c.desfazer();
            for (Entity e : mundo.getEntities()) {
                if (id.equals(e.getPersistentDataContainer().get(plugin.chaveGuardiao(), PersistentDataType.STRING))) e.remove();
            }
            n++;
        }
        salvar();
        if (n > 0) plugin.getLogger().info(n + " câmara(s) que ficaram abertas foram desfeitas.");
    }
}
