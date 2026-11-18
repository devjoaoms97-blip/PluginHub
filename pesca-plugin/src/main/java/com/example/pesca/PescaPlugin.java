package com.example.pesca;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Porto da /warp pesca (2026-11-17): barraquinhas alugadas na beira do lago. Na sua barraquinha, a vara
 * do dono puxa sozinha quando o peixe morde e é lançada de novo (dá pra ficar parado), com a espera
 * multiplicada por {@code fator-espera} (rende ~metade da pesca ativa), e o pescado vai pro estoque, o
 * barril da barraquinha. As Skills tratam a pesca dali como normal (sem o desconto da pesca parada):
 * {@link #pescandoNaBarraca(Player)} é a API que elas consultam.
 */
public class PescaPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private final Map<String, Barraca> barracas = new LinkedHashMap<>();
    private final Map<String, UUID> hologramas = new HashMap<>();
    /** Estoque de aluguéis que acabaram, esperando o /pesca retirar: dono → (itens, até quando). */
    private final Map<UUID, List<ItemStack>> retiradas = new HashMap<>();
    private final Map<UUID, Long> retiradasAte = new HashMap<>();
    private final Map<UUID, Long> confirmarSaida = new HashMap<>();

    private Economy economy;
    private PixIntegration pix;
    private File arquivo;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp != null ? rsp.getProvider() : null;
        if (economy == null) getLogger().warning("Sem economia (Vault): ninguém consegue alugar barraquinha.");
        pix = new PixIntegration(this);
        arquivo = new File(getDataFolder(), "barracas.yml");
        carregar();
        getServer().getPluginManager().registerEvents(this, this);
        // aluguéis vencendo e o que sobrou pra retirar
        getServer().getScheduler().runTaskTimer(this, this::vencer, 200L, 400L);
        // hologramas (não ficam salvos no mundo: voltam sozinhos)
        getServer().getScheduler().runTaskTimer(this, this::atualizarHologramas, 40L, 40L);
        getLogger().info("PescaPlugin ativado! " + barracas.size() + " barraquinha(s).");
    }

    @Override
    public void onDisable() {
        salvar();
        for (UUID u : hologramas.values()) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        hologramas.clear();
    }

    // =====================================================================
    // API (SkillsPlugin, por reflexão)
    // =====================================================================

    /** O jogador está pescando na barraquinha dele (alugada)? */
    public boolean pescandoNaBarraca(Player jogador) {
        Barraca b = barracaDoDonoAqui(jogador);
        return b != null;
    }

    // =====================================================================
    // Pesca automática
    // =====================================================================

    /** A barraquinha alugada pelo jogador, se ele estiver dentro dela. */
    private Barraca barracaDoDonoAqui(Player p) {
        for (Barraca b : barracas.values()) {
            if (p.getUniqueId().equals(b.dono) && b.contem(p.getLocation())) return b;
        }
        return null;
    }

    private Barraca barracaDe(UUID dono) {
        for (Barraca b : barracas.values()) if (dono.equals(b.dono)) return b;
        return null;
    }

    private int limiteEstoque() {
        return Math.max(1, Math.min(45, getConfig().getInt("estoque-pilhas", 45)));
    }

    private boolean cheio(Barraca b) {
        return b.pilhas() >= limiteEstoque();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoPescar(PlayerFishEvent evento) {
        Player p = evento.getPlayer();
        Barraca b = barracaDoDonoAqui(p);
        if (b == null) return;
        FishHook anzol = evento.getHook();
        switch (evento.getState()) {
            case FISHING -> esperaDobrada(p, anzol);
            case BITE -> {
                if (cheio(b)) {
                    p.sendActionBar(LEGADO.deserialize("§c🎣 Estoque cheio! §7Esvazie o barril pra vara voltar a puxar sozinha."));
                    return;
                }
                // puxa sozinha, no tick seguinte (o evento da mordida termina antes)
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (anzol.isValid() && p.isOnline() && p.getFishHook() == anzol && barracaDoDonoAqui(p) == b) {
                        anzol.retrieve(maoDaVara(p));
                    }
                }, 2L);
            }
            case CAUGHT_FISH -> {
                if (evento.getCaught() instanceof Item item) guardarPescado(p, b, item);
                relancar(p);
            }
            case REEL_IN, FAILED_ATTEMPT, IN_GROUND -> relancar(p);
            default -> {
            }
        }
    }

    /**
     * O peixe vai pro estoque, e não pro jogador. As Skills trocam o item no tick seguinte (o loot
     * especial), então ele espera uns ticks, sem ninguém poder pegar, e guarda o que estiver lá.
     */
    private void guardarPescado(Player p, Barraca b, Item item) {
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setVelocity(new Vector(0, 0, 0));
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!item.isValid()) return;
            ItemStack resto = b.guardar(item.getItemStack(), limiteEstoque());
            if (resto == null) {
                item.remove();
                p.sendActionBar(LEGADO.deserialize("§b🎣 §f" + nome(item.getItemStack()) + " §7foi pro estoque §8("
                        + b.pilhas() + "/" + limiteEstoque() + ")"));
            } else {
                // cheio: o que não coube fica no chão pro dono pegar
                item.setItemStack(resto);
                item.setPickupDelay(10);
                p.sendActionBar(LEGADO.deserialize("§c🎣 Estoque cheio! §7Esvazie o barril da barraquinha."));
            }
        }, 4L);
    }

    /** Lança a vara de novo, se o dono continua na barraquinha, com a vara na mão e espaço no estoque. */
    private void relancar(Player p) {
        getServer().getScheduler().runTaskLater(this, () -> {
            Barraca b = barracaDoDonoAqui(p);
            if (b == null || !p.isOnline() || p.isDead() || cheio(b) || p.getFishHook() != null) return;
            if (varaNaMao(p) == null) return;
            FishHook anzol = p.launchProjectile(FishHook.class);
            esperaDobrada(p, anzol);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.5f, 0.8f);
        }, Math.max(5, getConfig().getLong("relancar-ticks", 20)));
    }

    /**
     * Espera até morder: a do jogo (5 a 30 s, menos 5 s por nível de Isca da vara), vezes o fator. O
     * anzol lançado pelo plugin não sabe da Isca, então a conta é feita aqui pros dois casos.
     */
    private void esperaDobrada(Player p, FishHook anzol) {
        double fator = Math.max(1, getConfig().getDouble("fator-espera", 2.0));
        ItemStack vara = varaNaMao(p);
        int isca = vara == null ? 0 : vara.getEnchantmentLevel(Enchantment.LURE);
        int min = Math.max(20, 100 - 100 * isca), max = Math.max(40, 600 - 100 * isca);
        anzol.setApplyLure(false);
        anzol.setWaitTime((int) (min * fator), (int) (max * fator));
    }

    private static ItemStack varaNaMao(Player p) {
        ItemStack m = p.getInventory().getItemInMainHand();
        if (m.getType() == Material.FISHING_ROD) return m;
        ItemStack o = p.getInventory().getItemInOffHand();
        return o.getType() == Material.FISHING_ROD ? o : null;
    }

    private static EquipmentSlot maoDaVara(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
    }

    // =====================================================================
    // Barril: estoque, aluguel
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarBarril(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK || evento.getClickedBlock() == null || evento.getHand() != EquipmentSlot.HAND) return;
        Location l = evento.getClickedBlock().getLocation();
        for (Barraca b : barracas.values()) {
            if (!b.ehBarril(l)) continue;
            evento.setCancelled(true);
            Player p = evento.getPlayer();
            if (!b.alugada()) {
                abrirAluguel(p, b);
            } else if (p.getUniqueId().equals(b.dono)) {
                abrirEstoque(p, b);
            } else {
                p.sendMessage("§7Essa é a barraquinha de §f" + b.nomeDono + "§7. Veja as livres em §f/pesca§7.");
            }
            return;
        }
    }

    private record Menu(String barraca, boolean estoque) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static ItemStack icone(Material m, String nome, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.displayName(LEGADO.deserialize("§r" + nome));
        List<Component> linhas = new ArrayList<>();
        for (String l : lore) linhas.add(LEGADO.deserialize("§r" + l));
        meta.lore(linhas);
        i.setItemMeta(meta);
        return i;
    }

    private void abrirAluguel(Player p, Barraca b) {
        Inventory inv = Bukkit.createInventory(new Menu(b.id, false), 27, LEGADO.deserialize("§8Barraquinha de pesca " + b.id));
        inv.setItem(11, icone(Material.FISHING_ROD, "§b§lBarraquinha " + b.id,
                "§7Fique aqui com a vara na mão:", "§7ela puxa e lança sozinha, e o peixe", "§7vai pro estoque (este barril).",
                "§7Rende uns §f50% §7da pesca ativa,", "§7e conta pro Pescador e pras missões."));
        inv.setItem(15, icone(Material.EMERALD, "§a§lAlugar por " + dinheiro(preco()),
                "§7Por §f" + horas() + " h§7. Dá pra renovar.", "§7Uma barraquinha por jogador.", "", "§eClique pra alugar"));
        p.openInventory(inv);
    }

    private void abrirEstoque(Player p, Barraca b) {
        Inventory inv = Bukkit.createInventory(new Menu(b.id, true), 54, LEGADO.deserialize("§8Estoque da barraquinha " + b.id));
        for (int i = 0; i < b.estoque.size() && i < 45; i++) inv.setItem(i, b.estoque.get(i).clone());
        inv.setItem(45, icone(Material.CLOCK, "§e§lAluguel", "§7Acaba em §f" + tempo(b.ate - System.currentTimeMillis()),
                "§7Estoque: §f" + b.pilhas() + "/" + limiteEstoque() + " §7pilhas"));
        inv.setItem(47, icone(Material.HOPPER, "§a§lPegar tudo", "§7Leva o que couber pro inventário.", "§7Clique num item pra pegar só ele."));
        inv.setItem(49, icone(Material.EMERALD, "§a§lRenovar (" + dinheiro(preco()) + ")", "§7Mais §f" + horas() + " h§7, até "
                + getConfig().getInt("periodos-maximos", 3) + " períodos pagos."));
        inv.setItem(51, icone(b.autorenovar ? Material.LIME_DYE : Material.GRAY_DYE, "§e§lRenovar sozinho: " + (b.autorenovar ? "§aligado" : "§cdesligado"),
                "§7Quando o aluguel acabar, renova", "§7se você tiver o dinheiro."));
        inv.setItem(53, icone(Material.BARRIER, "§c§lDevolver a barraquinha", "§7Sem reembolso. O estoque vai", "§7pro §f/pesca retirar§7.", "", "§eClique duas vezes"));
        p.openInventory(inv);
    }

    @EventHandler
    public void aoClicarMenu(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player p)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        Barraca b = barracas.get(menu.barraca());
        if (b == null) return;
        int slot = evento.getRawSlot();
        if (!menu.estoque()) {
            if (slot == 15) {
                p.closeInventory();
                alugar(p, b);
            }
            return;
        }
        if (!p.getUniqueId().equals(b.dono)) {
            p.closeInventory();
            return;
        }
        if (slot < 45) {
            if (slot >= b.estoque.size()) return;
            ItemStack pilha = b.estoque.get(slot);
            Map<Integer, ItemStack> sobra = p.getInventory().addItem(pilha.clone());
            if (sobra.isEmpty()) b.estoque.remove(slot);
            else pilha.setAmount(sobra.values().iterator().next().getAmount());
            if (!sobra.isEmpty()) p.sendMessage("§cSeu inventário está cheio.");
            abrirEstoque(p, b);
        } else if (slot == 47) {
            int antes = b.pilhas();
            List<ItemStack> fica = new ArrayList<>();
            for (ItemStack pilha : b.estoque) fica.addAll(p.getInventory().addItem(pilha.clone()).values());
            b.estoque.clear();
            b.estoque.addAll(fica);
            if (!fica.isEmpty()) p.sendMessage("§cSeu inventário encheu: ficaram §f" + fica.size() + " §cpilha(s) no estoque.");
            else if (antes > 0) p.sendMessage("§aVocê pegou tudo do estoque.");
            abrirEstoque(p, b);
            relancar(p);
        } else if (slot == 49) {
            p.closeInventory();
            renovar(p);
        } else if (slot == 51) {
            b.autorenovar = !b.autorenovar;
            salvar();
            abrirEstoque(p, b);
        } else if (slot == 53) {
            Long antes = confirmarSaida.get(p.getUniqueId());
            if (antes != null && System.currentTimeMillis() - antes < 10_000) {
                confirmarSaida.remove(p.getUniqueId());
                p.closeInventory();
                encerrar(b, "§7Você devolveu a barraquinha §f" + b.id + "§7.");
            } else {
                confirmarSaida.put(p.getUniqueId(), System.currentTimeMillis());
                p.sendMessage("§eClique de novo em §cDevolver §epra confirmar (sem reembolso).");
            }
        }
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    // =====================================================================
    // Aluguel
    // =====================================================================

    private double preco() {
        return getConfig().getDouble("preco", 10000);
    }

    private int horas() {
        return Math.max(1, getConfig().getInt("horas-aluguel", 24));
    }

    private long periodoMs() {
        return horas() * 3_600_000L;
    }

    private boolean cobrar(Player p, String motivo) {
        if (economy == null) {
            p.sendMessage("§cA economia do servidor não está disponível.");
            return false;
        }
        double valor = preco();
        if (economy.getBalance(p) < valor) {
            p.sendMessage("§cVocê precisa de §f" + dinheiro(valor) + " §cpra isso.");
            return false;
        }
        economy.withdrawPlayer(p, valor);
        pix.registrarComServidor(p.getUniqueId(), valor, false, motivo);
        return true;
    }

    private void alugar(Player p, Barraca b) {
        if (b.alugada()) {
            p.sendMessage("§cA barraquinha §f" + b.id + " §cjá está alugada.");
            return;
        }
        Barraca atual = barracaDe(p.getUniqueId());
        if (atual != null) {
            p.sendMessage("§cVocê já tem a barraquinha §f" + atual.id + "§c. É uma por jogador.");
            return;
        }
        if (!cobrar(p, "Aluguel de barraquinha de pesca")) return;
        b.dono = p.getUniqueId();
        b.nomeDono = p.getName();
        b.ate = System.currentTimeMillis() + periodoMs();
        b.autorenovar = false;
        salvar();
        atualizarHologramas();
        p.sendMessage("§a🎣 Você alugou a barraquinha §f" + b.id + " §apor §f" + horas() + " h §7(" + dinheiro(preco())
                + ")§a. Fique nela com a vara na mão: ela puxa e lança sozinha, e o peixe vai pro barril.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    private void renovar(Player p) {
        Barraca b = barracaDe(p.getUniqueId());
        if (b == null) {
            p.sendMessage("§cVocê não tem barraquinha. Veja as livres em §f/pesca§c.");
            return;
        }
        long maximo = System.currentTimeMillis() + periodoMs() * Math.max(1, getConfig().getInt("periodos-maximos", 3));
        if (b.ate + periodoMs() > maximo) {
            p.sendMessage("§cSeu aluguel já vai até o máximo (§f" + getConfig().getInt("periodos-maximos", 3) + " períodos§c).");
            return;
        }
        if (!cobrar(p, "Aluguel de barraquinha de pesca")) return;
        b.ate += periodoMs();
        salvar();
        p.sendMessage("§aAluguel renovado: a barraquinha §f" + b.id + " §aé sua por mais §f" + tempo(b.ate - System.currentTimeMillis()) + "§a.");
    }

    /** Acaba o aluguel: o estoque vai pra retirada do dono. */
    private void encerrar(Barraca b, String aviso) {
        UUID dono = b.dono;
        if (dono != null && !b.estoque.isEmpty()) {
            retiradas.computeIfAbsent(dono, k -> new ArrayList<>()).addAll(b.estoque);
            retiradasAte.put(dono, System.currentTimeMillis() + getConfig().getInt("retirada-dias", 7) * 86_400_000L);
        }
        Player p = dono == null ? null : Bukkit.getPlayer(dono);
        b.liberar();
        salvar();
        atualizarHologramas();
        if (p != null) {
            p.sendMessage(aviso);
            if (retiradas.containsKey(p.getUniqueId())) p.sendMessage("§7O estoque está guardado: §f/pesca retirar§7.");
        }
    }

    private void vencer() {
        long agora = System.currentTimeMillis();
        for (Barraca b : barracas.values()) {
            if (!b.alugada() || b.ate > agora) continue;
            if (b.autorenovar && economy != null) {
                var dono = Bukkit.getOfflinePlayer(b.dono);
                if (economy.getBalance(dono) >= preco()) {
                    economy.withdrawPlayer(dono, preco());
                    pix.registrarComServidor(b.dono, preco(), false, "Aluguel de barraquinha de pesca");
                    b.ate += periodoMs();
                    salvar();
                    Player p = dono.getPlayer();
                    if (p != null) p.sendMessage("§aA barraquinha §f" + b.id + " §arenovou sozinha (" + dinheiro(preco()) + ").");
                    continue;
                }
            }
            encerrar(b, "§eO aluguel da sua barraquinha de pesca §f" + b.id + " §eacabou.");
        }
        boolean mudou = retiradasAte.entrySet().removeIf(e -> {
            if (e.getValue() > agora) return false;
            retiradas.remove(e.getKey());
            return true;
        });
        if (mudou) salvar();
    }

    // =====================================================================
    // Hologramas
    // =====================================================================

    private void atualizarHologramas() {
        long agora = System.currentTimeMillis();
        for (Barraca b : barracas.values()) {
            Location l = b.ondeHolograma();
            if (l == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            String texto = b.alugada()
                    ? "§b§l🎣 Barraquinha " + b.id + "\n§7de §f" + b.nomeDono + "\n§7mais §f" + tempo(b.ate - agora)
                    : "§b§l🎣 Barraquinha " + b.id + "\n§a§lLIVRE\n§7" + dinheiro(preco()) + " por " + horas() + " h §8(clique no barril)";
            UUID u = hologramas.get(b.id);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            TextDisplay holo = e instanceof TextDisplay t && t.isValid() ? t : null;
            if (holo == null) {
                holo = l.getWorld().spawn(l, TextDisplay.class, d -> {
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setPersistent(false);
                    d.setSeeThrough(false);
                    d.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this, "barraca"), PersistentDataType.STRING, b.id);
                });
                hologramas.put(b.id, holo.getUniqueId());
            }
            holo.text(LEGADO.deserialize(texto));
        }
    }

    @EventHandler
    public void aoCarregarChunk(ChunkLoadEvent evento) {
        // os de antes de reiniciar (não deviam existir, são não persistentes) e os órfãos
        for (Entity e : evento.getChunk().getEntities()) {
            if (e instanceof TextDisplay && e.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(this, "barraca"))
                    && !hologramas.containsValue(e.getUniqueId())) {
                e.remove();
            }
        }
    }

    // =====================================================================
    // Comandos
    // =====================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(BR) : "";
        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Só no jogo. Staff: /pesca admin");
            return true;
        }
        switch (sub) {
            case "", "lista" -> listar(p);
            case "alugar" -> {
                Barraca b = args.length > 1 ? barracas.get(args[1]) : null;
                if (b == null) p.sendMessage("§cUso: §f/pesca alugar <número> §c(veja §f/pesca§c)");
                else alugar(p, b);
            }
            case "renovar" -> renovar(p);
            case "autorenovar" -> {
                Barraca b = barracaDe(p.getUniqueId());
                if (b == null) {
                    p.sendMessage("§cVocê não tem barraquinha.");
                } else {
                    b.autorenovar = !b.autorenovar;
                    salvar();
                    p.sendMessage("§aRenovar sozinho: " + (b.autorenovar ? "§aligado" : "§cdesligado") + "§a.");
                }
            }
            case "ir" -> {
                Barraca b = barracaDe(p.getUniqueId());
                if (b == null) p.sendMessage("§cVocê não tem barraquinha.");
                else if (b.entrada() != null) p.teleport(b.entrada());
            }
            case "sair" -> {
                Barraca b = barracaDe(p.getUniqueId());
                if (b == null) p.sendMessage("§cVocê não tem barraquinha.");
                else if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
                    p.sendMessage("§eDevolver a barraquinha §f" + b.id + "§e, sem reembolso? §f/pesca sair confirmar");
                } else encerrar(b, "§7Você devolveu a barraquinha §f" + b.id + "§7.");
            }
            case "retirar" -> retirar(p);
            default -> p.sendMessage("§cUso: §f/pesca §7[alugar <n> | renovar | autorenovar | ir | sair | retirar]");
        }
        return true;
    }

    private void listar(Player p) {
        Barraca minha = barracaDe(p.getUniqueId());
        p.sendMessage("§b§l🎣 Barraquinhas de pesca §7(§f/warp pesca§7) — " + dinheiro(preco()) + " por " + horas() + " h");
        if (minha != null) {
            p.sendMessage(LEGADO.deserialize("§aSua: §f" + minha.id + " §7(mais " + tempo(minha.ate - System.currentTimeMillis())
                    + ", estoque " + minha.pilhas() + "/" + limiteEstoque() + ") ").append(botao("§b[IR]", "/pesca ir", "Vai pra sua barraquinha")));
        }
        Component livres = LEGADO.deserialize("§7Livres: ");
        int n = 0;
        for (Barraca b : barracas.values()) {
            if (b.alugada()) continue;
            livres = livres.append(botao("§a[" + b.id + "] ", "/pesca alugar " + b.id, "Alugar a barraquinha " + b.id + " por " + dinheiro(preco())));
            n++;
        }
        p.sendMessage(n == 0 ? LEGADO.deserialize("§7Nenhuma livre agora.") : livres);
        if (retiradas.containsKey(p.getUniqueId())) p.sendMessage("§eVocê tem estoque guardado: §f/pesca retirar");
    }

    private static Component botao(String texto, String comando, String dica) {
        return LEGADO.deserialize(texto).clickEvent(ClickEvent.runCommand(comando)).hoverEvent(HoverEvent.showText(LEGADO.deserialize("§7" + dica)));
    }

    private void retirar(Player p) {
        List<ItemStack> lista = retiradas.get(p.getUniqueId());
        if (lista == null || lista.isEmpty()) {
            p.sendMessage("§7Nada pra retirar.");
            return;
        }
        List<ItemStack> fica = new ArrayList<>();
        for (ItemStack i : lista) fica.addAll(p.getInventory().addItem(i).values());
        if (fica.isEmpty()) {
            retiradas.remove(p.getUniqueId());
            retiradasAte.remove(p.getUniqueId());
            p.sendMessage("§aVocê pegou tudo o que estava guardado.");
        } else {
            retiradas.put(p.getUniqueId(), fica);
            p.sendMessage("§eSeu inventário encheu: ficaram §f" + fica.size() + " §epilha(s) guardadas.");
        }
        salvar();
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pesca.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(BR) : "";
        switch (sub) {
            // /pesca admin barraca <id> <mundo> <x1> <y1> <z1> <x2> <y2> <z2> <xBarril> <yBarril> <zBarril>
            case "barraca" -> {
                if (args.length < 13) {
                    sender.sendMessage("§cUso: /pesca admin barraca <id> <mundo> <x1 y1 z1> <x2 y2 z2> <barril x y z>");
                    return;
                }
                try {
                    int[] v = new int[9];
                    for (int i = 0; i < 9; i++) v[i] = Integer.parseInt(args[4 + i]);
                    int[] min = {Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5])};
                    int[] max = {Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5])};
                    Barraca antiga = barracas.get(args[2]);
                    Barraca nova = new Barraca(args[2], args[3], min, max, new int[]{v[6], v[7], v[8]});
                    if (antiga != null) { // mantém o aluguel e o estoque de quem já tinha
                        nova.dono = antiga.dono;
                        nova.nomeDono = antiga.nomeDono;
                        nova.ate = antiga.ate;
                        nova.autorenovar = antiga.autorenovar;
                        nova.estoque.addAll(antiga.estoque);
                    }
                    barracas.put(nova.id, nova);
                    salvar();
                    sender.sendMessage("§aBarraquinha " + nova.id + " cadastrada.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cCoordenada inválida.");
                }
            }
            case "remover" -> {
                Barraca b = args.length > 2 ? barracas.remove(args[2]) : null;
                if (b == null) sender.sendMessage("§cNão existe.");
                else {
                    UUID u = hologramas.remove(b.id);
                    if (u != null && Bukkit.getEntity(u) != null) Bukkit.getEntity(u).remove();
                    salvar();
                    sender.sendMessage("§aRemovida.");
                }
            }
            case "liberar", "vencer" -> {
                Barraca b = args.length > 2 ? barracas.get(args[2]) : null;
                if (b == null || !b.alugada()) sender.sendMessage("§cNão existe ou não está alugada.");
                else if (sub.equals("vencer")) {
                    b.ate = System.currentTimeMillis() - 1; // pra testar a renovação automática e o fim
                    vencer();
                    sender.sendMessage("§aAluguel da " + b.id + " vencido agora.");
                } else {
                    encerrar(b, "§eA staff encerrou o aluguel da sua barraquinha §f" + b.id + "§e.");
                    sender.sendMessage("§aLiberada.");
                }
            }
            case "lista" -> {
                sender.sendMessage("--- Barraquinhas (" + barracas.size() + ") ---");
                for (Barraca b : barracas.values()) {
                    sender.sendMessage(b.id + ": " + (b.alugada() ? b.nomeDono + " (" + tempo(b.ate - System.currentTimeMillis()) + ", estoque " + b.pilhas() + ")" : "livre"));
                }
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("§aConfig recarregado.");
            }
            default -> sender.sendMessage("§cUso: /pesca admin <barraca|remover|liberar|vencer|lista|reload>");
        }
    }

    // =====================================================================
    // Arquivo
    // =====================================================================

    private void carregar() {
        barracas.clear();
        retiradas.clear();
        retiradasAte.clear();
        if (!arquivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection s = y.getConfigurationSection("barracas");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                Barraca b = new Barraca(id, c.getString("mundo", "spawn"), ints(c.getIntegerList("min")), ints(c.getIntegerList("max")),
                        ints(c.getIntegerList("barril")));
                String dono = c.getString("dono");
                if (dono != null) {
                    b.dono = UUID.fromString(dono);
                    b.nomeDono = c.getString("nome", "?");
                    b.ate = c.getLong("ate");
                    b.autorenovar = c.getBoolean("autorenovar");
                    for (Object o : c.getList("estoque", List.of())) if (o instanceof ItemStack i) b.estoque.add(i);
                }
                barracas.put(id, b);
            }
        }
        ConfigurationSection r = y.getConfigurationSection("retiradas");
        if (r != null) {
            for (String u : r.getKeys(false)) {
                List<ItemStack> lista = new ArrayList<>();
                for (Object o : r.getList(u + ".itens", List.of())) if (o instanceof ItemStack i) lista.add(i);
                retiradas.put(UUID.fromString(u), lista);
                retiradasAte.put(UUID.fromString(u), r.getLong(u + ".ate"));
            }
        }
    }

    private static int[] ints(List<Integer> l) {
        return l.size() == 3 ? new int[]{l.get(0), l.get(1), l.get(2)} : new int[]{0, 0, 0};
    }

    void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Barraca b : barracas.values()) {
            String p = "barracas." + b.id + ".";
            y.set(p + "mundo", b.mundo);
            y.set(p + "min", List.of(b.min[0], b.min[1], b.min[2]));
            y.set(p + "max", List.of(b.max[0], b.max[1], b.max[2]));
            y.set(p + "barril", List.of(b.barril[0], b.barril[1], b.barril[2]));
            if (b.alugada()) {
                y.set(p + "dono", b.dono.toString());
                y.set(p + "nome", b.nomeDono);
                y.set(p + "ate", b.ate);
                y.set(p + "autorenovar", b.autorenovar);
                y.set(p + "estoque", new ArrayList<>(b.estoque));
            }
        }
        for (Map.Entry<UUID, List<ItemStack>> e : retiradas.entrySet()) {
            y.set("retiradas." + e.getKey() + ".itens", new ArrayList<>(e.getValue()));
            y.set("retiradas." + e.getKey() + ".ate", retiradasAte.getOrDefault(e.getKey(), 0L));
        }
        try {
            y.save(arquivo);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Não foi possível salvar barracas.yml", e);
        }
    }

    // =====================================================================
    // Formato
    // =====================================================================

    static String dinheiro(double v) {
        return String.format(BR, "R$ %,.2f", v);
    }

    static String tempo(long ms) {
        if (ms <= 0) return "0min";
        long min = ms / 60_000, h = min / 60;
        return h > 0 ? h + "h" + String.format("%02d", min % 60) : min + "min";
    }

    private static String nome(ItemStack i) {
        if (i.hasItemMeta() && i.getItemMeta().hasDisplayName()) return LEGADO.serialize(i.getItemMeta().displayName());
        String n = i.getType().name().toLowerCase(BR).replace('_', ' ');
        return (i.getAmount() > 1 ? i.getAmount() + "x " : "") + n;
    }

    @SuppressWarnings("unused")
    private static World mundo(String nome) {
        return Bukkit.getWorld(nome);
    }
}
