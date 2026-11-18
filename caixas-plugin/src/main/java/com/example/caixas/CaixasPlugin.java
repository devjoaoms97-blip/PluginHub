package com.example.caixas;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Caixas com chave (2026-11-18). Cada caixa (voto, mina, boss, tesouro...) é um bloco na Praça das Caixas do
 * spawn, com um holograma em cima. Clique esquerdo mostra os prêmios e as chances; clique direito com a chave
 * dela na mão abre uma roleta; agachado + clique direito abre todas as chaves da mão de uma vez.
 *
 * <p>As chaves são itens (gancho de armadilha com a marca {@code caixasplugin:chave}): não se colocam, não
 * entram em receita e a loja não compra. Quem dá: {@code /caixas dar} (console também) e a API
 * {@link #darChave}. Offline ou com o inventário cheio, a chave fica guardada e chega no {@code /caixas
 * resgatar} (ou sozinha, quando ele entra). A chave da Mina cai sozinha minerando.
 */
public class CaixasPlugin extends JavaPlugin implements Listener, TabCompleter {

    private static final String TAG_HOLO = "caixas-holo";
    private static final List<String> ORDEM = List.of("lendario", "epico", "raro", "comum");

    private Economy economia;
    private Integracoes integracoes;
    private NamespacedKey chaveKey;
    private final Map<String, Caixa> caixas = new LinkedHashMap<>();
    private final Map<String, Location> locais = new LinkedHashMap<>();
    private final Map<UUID, Roleta> roletas = new HashMap<>();
    private File arquivoLocais, arquivoPendentes;
    private YamlConfiguration pendentes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chaveKey = new NamespacedKey(this, "chave");
        integracoes = new Integracoes(this);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economia = rsp == null ? null : rsp.getProvider();
        if (economia == null) getLogger().warning("Vault sem economia: os prêmios em coins ficam de fora.");
        arquivoLocais = new File(getDataFolder(), "locais.yml");
        arquivoPendentes = new File(getDataFolder(), "pendentes.yml");
        pendentes = YamlConfiguration.loadConfiguration(arquivoPendentes);
        carregar();
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("caixas");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskLater(this, this::hologramas, 40L);
    }

    @Override
    public void onDisable() {
        for (Roleta r : new ArrayList<>(roletas.values())) r.entregar();
        roletas.clear();
        salvarPendentes();
    }

    private void carregar() {
        reloadConfig();
        caixas.clear();
        ConfigurationSection s = getConfig().getConfigurationSection("caixas");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(id);
                if (c != null) caixas.put(id.toLowerCase(Locale.ROOT), new Caixa(id.toLowerCase(Locale.ROOT), c));
            }
        }
        locais.clear();
        YamlConfiguration l = YamlConfiguration.loadConfiguration(arquivoLocais);
        for (String id : l.getKeys(false)) {
            World w = Bukkit.getWorld(l.getString(id + ".mundo", ""));
            if (w != null) locais.put(id, new Location(w, l.getInt(id + ".x"), l.getInt(id + ".y"), l.getInt(id + ".z")));
        }
    }

    private void salvarLocais() {
        YamlConfiguration l = new YamlConfiguration();
        locais.forEach((id, loc) -> {
            l.set(id + ".mundo", loc.getWorld().getName());
            l.set(id + ".x", loc.getBlockX());
            l.set(id + ".y", loc.getBlockY());
            l.set(id + ".z", loc.getBlockZ());
        });
        try {
            l.save(arquivoLocais);
        } catch (IOException e) {
            getLogger().warning("Não salvou locais.yml: " + e.getMessage());
        }
    }

    private void salvarPendentes() {
        try {
            pendentes.save(arquivoPendentes);
        } catch (IOException e) {
            getLogger().warning("Não salvou pendentes.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ utilidades

    public Integracoes getIntegracoes() {
        return integracoes;
    }

    static String cores(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    static String dinheiro(double v) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return f.format(v);
    }

    static String numero(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }

    private static final Map<String, String> ENCANTAMENTOS = Map.of("efficiency", "Eficiência", "fortune", "Fortuna",
            "unbreaking", "Inquebrável", "sharpness", "Afiação", "protection", "Proteção", "mending", "Remendo",
            "silk_touch", "Toque Suave", "looting", "Saque", "power", "Força");
    private static final String[] ROMANOS = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** O nome de um item sem nome próprio: o livro com o encantamento ("Livro Eficiência III") ou o material. */
    static String nomeDoItem(ItemStack item) {
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta livro && livro.hasStoredEnchants()) {
            StringBuilder sb = new StringBuilder("Livro");
            livro.getStoredEnchants().forEach((e, nv) -> sb.append(' ')
                    .append(ENCANTAMENTOS.getOrDefault(e.getKey().getKey(), e.getKey().getKey().replace('_', ' ')))
                    .append(' ').append(nv < ROMANOS.length ? ROMANOS[nv] : String.valueOf(nv)));
            return sb.toString();
        }
        return nomeDoMaterial(item.getType());
    }

    static String nomeDoMaterial(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String msg(String chave) {
        return cores(getConfig().getString("mensagens." + chave, ""));
    }

    private String prefixo() {
        return msg("prefixo");
    }

    String corDa(String raridade) {
        return cores(getConfig().getString("raridades." + raridade + ".cor", "&f"));
    }

    String nomeDa(String raridade) {
        return corDa(raridade) + getConfig().getString("raridades." + raridade + ".nome", raridade);
    }

    Material vidroDa(String raridade) {
        Material m = Material.matchMaterial(getConfig().getString("raridades." + raridade + ".vidro", ""));
        return m != null ? m : Material.WHITE_STAINED_GLASS_PANE;
    }

    // ------------------------------------------------------------------ API das chaves

    /** A chave da caixa (null se a caixa não existe). */
    public ItemStack chave(String caixa, int qtd) {
        Caixa c = caixas.get(caixa.toLowerCase(Locale.ROOT));
        if (c == null) return null;
        ItemStack i = new ItemStack(Material.TRIPWIRE_HOOK, Math.max(1, qtd));
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(c.nomeChave);
        meta.setLore(List.of(cores("&7Abre a " + c.nome + "&7 na Praça das Caixas do spawn."),
                cores("&7Clique direito na caixa com ela na mão."), "", c.comoConseguir));
        meta.setEnchantmentGlintOverride(true);
        if (!c.modeloChave.isBlank()) {
            NamespacedKey modelo = NamespacedKey.fromString(c.modeloChave);
            if (modelo != null) meta.setItemModel(modelo);
        }
        meta.getPersistentDataContainer().set(chaveKey, PersistentDataType.STRING, c.id);
        i.setItemMeta(meta);
        return i;
    }

    /** De que caixa é a chave (null se não é chave). */
    public String idDaChave(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(chaveKey, PersistentDataType.STRING);
    }

    /**
     * Dá chaves a um jogador. Online, vão pro inventário; o que não couber (ou offline), fica guardado pro
     * /caixas resgatar. false se a caixa não existe.
     */
    public boolean darChave(UUID jogador, String caixa, int qtd, String motivo) {
        Caixa c = caixas.get(caixa.toLowerCase(Locale.ROOT));
        if (c == null || qtd <= 0) return false;
        Player p = Bukkit.getPlayer(jogador);
        if (p == null) {
            guardar(jogador, c.id, qtd);
            return true;
        }
        int sobrou = 0;
        for (ItemStack resto : p.getInventory().addItem(chave(c.id, qtd)).values()) sobrou += resto.getAmount();
        if (qtd - sobrou > 0) {
            p.sendMessage(prefixo() + msg("chave-recebida").replace("%qtd%", String.valueOf(qtd - sobrou))
                    .replace("%chave%", c.nomeChave).replace("%motivo%", motivo == null ? "" : motivo));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        if (sobrou > 0) {
            guardar(jogador, c.id, sobrou);
            p.sendMessage(prefixo() + msg("chave-guardada").replace("%qtd%", String.valueOf(sobrou)).replace("%chave%", c.nomeChave));
        }
        return true;
    }

    private void guardar(UUID jogador, String caixa, int qtd) {
        String k = jogador + "." + caixa;
        pendentes.set(k, pendentes.getInt(k) + qtd);
        salvarPendentes();
    }

    /** Entrega as chaves guardadas que couberem. Devolve quantas entregou. */
    private int resgatar(Player p) {
        ConfigurationSection s = pendentes.getConfigurationSection(p.getUniqueId().toString());
        if (s == null) return 0;
        int entregues = 0;
        for (String caixa : new ArrayList<>(s.getKeys(false))) {
            int qtd = s.getInt(caixa);
            if (qtd <= 0 || !caixas.containsKey(caixa)) continue;
            int sobrou = 0;
            for (ItemStack resto : p.getInventory().addItem(chave(caixa, qtd)).values()) sobrou += resto.getAmount();
            entregues += qtd - sobrou;
            if (qtd - sobrou > 0) {
                p.sendMessage(prefixo() + msg("chave-recebida").replace("%qtd%", String.valueOf(qtd - sobrou))
                        .replace("%chave%", caixas.get(caixa).nomeChave).replace("%motivo%", "guardada"));
            }
            s.set(caixa, sobrou > 0 ? sobrou : null);
        }
        if (s.getKeys(false).isEmpty()) pendentes.set(p.getUniqueId().toString(), null);
        salvarPendentes();
        return entregues;
    }

    private int guardadas(UUID jogador) {
        ConfigurationSection s = pendentes.getConfigurationSection(jogador.toString());
        if (s == null) return 0;
        int n = 0;
        for (String k : s.getKeys(false)) n += s.getInt(k);
        return n;
    }

    // ------------------------------------------------------------------ prêmios

    /** Entrega um prêmio e devolve a descrição dele. {@code avisar}: mensagem, som e anúncio. */
    String entregar(Player p, Caixa caixa, Premio premio, boolean avisar) {
        ItemStack entregue = null;
        double valor = 0;
        switch (premio.tipo) {
            case COINS -> {
                valor = premio.valor(this, p);
                if (economia != null && valor > 0) {
                    economia.depositPlayer(p, valor);
                    integracoes.extrato(p.getUniqueId(), valor, ChatColor.stripColor(caixa.nome));
                }
            }
            case ITEM -> {
                entregue = integracoes.item(premio.item, premio.sortearQtd());
                if (entregue != null) {
                    for (ItemStack resto : p.getInventory().addItem(entregue.clone()).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), resto);
                    }
                }
            }
            case COMANDO -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), premio.comando.replace("%jogador%", p.getName()));
        }
        String desc = premio.descricao(this, entregue, valor);
        if (avisar) {
            p.sendMessage(prefixo() + msg("ganhou").replace("%premio%", desc).replace("%raridade%", nomeDa(premio.raridade)));
            boolean grande = ORDEM.indexOf(premio.raridade) <= 1;
            p.playSound(p.getLocation(), grande ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
        }
        if (getConfig().getStringList("anunciar").contains(premio.raridade)) {
            String anuncio = msg("anuncio").replace("%jogador%", p.getName()).replace("%premio%", desc)
                    .replace("%caixa%", caixa.nome).replace("%raridade%", nomeDa(premio.raridade));
            Bukkit.broadcastMessage(anuncio);
            integracoes.discord(anuncio);
        }
        return desc;
    }

    private void abrir(Player p, Caixa caixa, boolean todas) {
        if (roletas.containsKey(p.getUniqueId())) {
            p.sendMessage(prefixo() + msg("ocupado"));
            return;
        }
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (!caixa.id.equals(idDaChave(mao))) {
            p.sendMessage(prefixo() + msg("sem-chave").replace("%caixa%", caixa.nome).replace("%chave%", caixa.nomeChave)
                    .replace("%como%", caixa.comoConseguir));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
            return;
        }
        if (caixa.premios.isEmpty()) return;
        if (todas) {
            int n = mao.getAmount();
            p.getInventory().setItemInMainHand(null);
            Map<String, Integer> resumo = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) resumo.merge(entregar(p, caixa, caixa.sortear(), false), 1, Integer::sum);
            p.sendMessage(prefixo() + "§aVocê abriu §f" + n + "x §a" + ChatColor.stripColor(caixa.nome) + ":");
            resumo.forEach((d, q) -> p.sendMessage("§8 • " + (q > 1 ? "§f" + q + "× " : "") + d));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
            return;
        }
        mao.setAmount(mao.getAmount() - 1);
        p.getInventory().setItemInMainHand(mao.getAmount() > 0 ? mao : null);
        Roleta r = new Roleta(this, p, caixa, caixa.sortear());
        roletas.put(p.getUniqueId(), r);
        r.comecar();
    }

    /** Vitrine: os prêmios da caixa, da raridade mais alta pra mais baixa, com a chance de cada um. */
    private void mostrar(Player p, Caixa caixa) {
        List<Premio> l = new ArrayList<>(caixa.premios);
        l.sort(Comparator.comparingInt((Premio x) -> ORDEM.indexOf(x.raridade)).thenComparingDouble(x -> -x.peso));
        Inventory inv = Bukkit.createInventory(new Vitrine(), 54, caixa.nome + " §8(prêmios)");
        for (int i = 0; i < Math.min(45, l.size()); i++) {
            Premio pr = l.get(i);
            ItemStack icone = pr.icone(this, p);
            ItemMeta meta = icone.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(nomeDa(pr.raridade));
            lore.add("§7Chance: §f" + String.format(Locale.ROOT, "%.2f", caixa.chance(pr) * 100).replace('.', ',') + "%");
            if (pr.tipo == Premio.Tipo.COINS) lore.add("§8(" + numero(pr.coins) + "% do preço do seu próximo rank)");
            meta.setLore(lore);
            icone.setItemMeta(meta);
            inv.setItem(i, icone);
        }
        ItemStack info = chave(caixa.id, 1);
        ItemMeta meta = info.getItemMeta();
        meta.setLore(List.of(cores("&7Como conseguir:"), caixa.comoConseguir, "",
                cores("&7Clique direito na caixa com a chave: abre."), cores("&7Agachado: abre todas da mão.")));
        info.setItemMeta(meta);
        inv.setItem(49, info);
        p.openInventory(inv);
    }

    /** Holder da vitrine (só pra reconhecer e travar os cliques). */
    static final class Vitrine implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // ------------------------------------------------------------------ hologramas

    private void hologramas() {
        locais.forEach((id, loc) -> {
            Caixa c = caixas.get(id);
            Chunk ch = loc.getChunk();
            ch.load();
            for (Entity e : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 1.5, 0.5), 2, 3, 2)) {
                if (e.getScoreboardTags().contains(TAG_HOLO)) e.remove();
            }
            if (c == null) return;
            TextDisplay t = loc.getWorld().spawn(loc.clone().add(0.5, 1.35, 0.5), TextDisplay.class, d -> {
                d.setText(c.nome + "\n§7Esquerdo: §fprêmios\n§7Direito com a chave: §fabrir");
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.addScoreboardTag(TAG_HOLO);
                d.setPersistent(true);
            });
            t.setViewRange(0.5f);
        });
    }

    // ------------------------------------------------------------------ eventos

    private Caixa caixaEm(Location bloco) {
        for (Map.Entry<String, Location> e : locais.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld().equals(bloco.getWorld()) && l.getBlockX() == bloco.getBlockX() && l.getBlockY() == bloco.getBlockY()
                    && l.getBlockZ() == bloco.getBlockZ()) return caixas.get(e.getKey());
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoClicar(PlayerInteractEvent evento) {
        if (evento.getClickedBlock() == null || evento.getHand() != EquipmentSlot.HAND) return;
        Caixa c = caixaEm(evento.getClickedBlock().getLocation());
        if (c == null) return;
        evento.setCancelled(true);
        Player p = evento.getPlayer();
        if (evento.getAction() == Action.LEFT_CLICK_BLOCK) mostrar(p, c);
        else if (evento.getAction() == Action.RIGHT_CLICK_BLOCK) abrir(p, c, p.isSneaking());
    }

    @EventHandler(ignoreCancelled = true)
    public void aoClicarInventario(InventoryClickEvent evento) {
        InventoryHolder h = evento.getView().getTopInventory().getHolder();
        if (h instanceof Roleta || h instanceof Vitrine) evento.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoArrastar(InventoryDragEvent evento) {
        InventoryHolder h = evento.getView().getTopInventory().getHolder();
        if (h instanceof Roleta || h instanceof Vitrine) evento.setCancelled(true);
    }

    @EventHandler
    public void aoFechar(InventoryCloseEvent evento) {
        if (evento.getInventory().getHolder() instanceof Roleta r) {
            r.entregar();
            roletas.remove(evento.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        Roleta r = roletas.remove(evento.getPlayer().getUniqueId());
        if (r != null) r.entregar();
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player p = evento.getPlayer();
        if (guardadas(p.getUniqueId()) > 0) Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) resgatar(p);
        }, 60L);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (idDaChave(evento.getItemInHand()) != null) evento.setCancelled(true);
    }

    @EventHandler
    public void aoMontarReceita(PrepareItemCraftEvent evento) {
        for (ItemStack i : evento.getInventory().getMatrix()) {
            if (idDaChave(i) != null) {
                evento.getInventory().setResult(null);
                return;
            }
        }
    }

    /** A chave da Mina: cai sozinha, rara, de quem quebra à mão numa mina do RankupPlugin. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMinerar(BlockBreakEvent evento) {
        double chance = getConfig().getDouble("mina.chance-por-bloco", 0);
        if (chance <= 0 || evento.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        if (integracoes.minaEm(evento.getBlock().getLocation()) == null) return;
        darChave(evento.getPlayer().getUniqueId(), getConfig().getString("mina.caixa", "mina"), 1, "achou minerando");
    }

    // ------------------------------------------------------------------ comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        boolean admin = s.hasPermission("caixas.admin");
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "resgatar" -> {
                if (!(s instanceof Player p)) return true;
                if (guardadas(p.getUniqueId()) == 0) p.sendMessage(prefixo() + msg("nada-pra-resgatar"));
                else if (resgatar(p) == 0) p.sendMessage(prefixo() + "§cSem espaço no inventário.");
                return true;
            }
            case "dar" -> {
                if (!admin) break;
                if (a.length < 3) {
                    s.sendMessage("§cUso: /caixas dar <jogador> <caixa> [qtd]");
                    return true;
                }
                OfflinePlayer alvo = Bukkit.getOfflinePlayer(a[1]);
                int qtd = a.length >= 4 ? parse(a[3]) : 1;
                if (!darChave(alvo.getUniqueId(), a[2], Math.max(1, qtd), "staff")) {
                    s.sendMessage("§cCaixa desconhecida: " + a[2] + ". Caixas: " + String.join(", ", caixas.keySet()));
                } else {
                    s.sendMessage("§a" + qtd + " chave(s) " + a[2] + " pra " + alvo.getName() + (alvo.isOnline() ? "" : " (guardada, offline)") + ".");
                }
                return true;
            }
            case "local" -> {
                if (!admin) break;
                if (a.length < 2 || !caixas.containsKey(a[1].toLowerCase(Locale.ROOT))) {
                    s.sendMessage("§cUso: /caixas local <caixa> [mundo x y z]. Caixas: " + String.join(", ", caixas.keySet()));
                    return true;
                }
                Location loc;
                if (a.length >= 6) {
                    World w = Bukkit.getWorld(a[2]);
                    if (w == null) {
                        s.sendMessage("§cMundo desconhecido.");
                        return true;
                    }
                    loc = new Location(w, parse(a[3]), parse(a[4]), parse(a[5]));
                } else if (s instanceof Player p && p.getTargetBlockExact(6) != null) {
                    loc = p.getTargetBlockExact(6).getLocation();
                } else {
                    s.sendMessage("§cOlhe pro bloco da caixa ou passe mundo x y z.");
                    return true;
                }
                locais.put(a[1].toLowerCase(Locale.ROOT), loc);
                salvarLocais();
                hologramas();
                s.sendMessage("§aCaixa " + a[1] + " em " + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + ".");
                return true;
            }
            case "simular" -> {
                // /caixas simular <caixa> [n]: sorteia n prêmios (sem entregar) e mostra quantos de cada raridade
                if (!admin) break;
                Caixa c = a.length >= 2 ? caixas.get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (c == null || c.premios.isEmpty()) {
                    s.sendMessage("§cUso: /caixas simular <caixa> [n]");
                    return true;
                }
                int n = a.length >= 3 ? Math.max(1, parse(a[2])) : 1000;
                Map<String, Integer> cont = new LinkedHashMap<>();
                for (String r : ORDEM) cont.put(r, 0);
                for (int i = 0; i < n; i++) cont.merge(c.sortear().raridade, 1, Integer::sum);
                StringBuilder sb = new StringBuilder("§e" + c.id + " em " + n + ":");
                cont.forEach((r, q) -> sb.append(" §f").append(r).append("=").append(q));
                s.sendMessage(sb.toString());
                return true;
            }
            case "reload" -> {
                if (!admin) break;
                carregar();
                hologramas();
                s.sendMessage("§aCaixasPlugin recarregado: " + caixas.size() + " caixas, " + locais.size() + " no mapa.");
                return true;
            }
            case "lista" -> {
                if (!admin) break;
                caixas.forEach((id, c) -> {
                    Location l = locais.get(id);
                    s.sendMessage("§e" + id + "§7: " + c.premios.size() + " prêmios, " + (l == null ? "§csem lugar"
                            : "§f" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
                });
                return true;
            }
            default -> {
            }
        }
        // /caixas: as caixas e como conseguir cada chave
        s.sendMessage(prefixo() + "§fNa §ePraça das Caixas§f do spawn. Clique esquerdo mostra os prêmios; direito com a chave, abre.");
        for (Caixa c : caixas.values()) s.sendMessage("§8 • " + c.nome + "§7: " + c.comoConseguir);
        if (s instanceof Player p && guardadas(p.getUniqueId()) > 0) {
            s.sendMessage("§eVocê tem " + guardadas(p.getUniqueId()) + " chave(s) guardada(s): §f/caixas resgatar");
        }
        if (admin) s.sendMessage("§7Staff: /caixas dar | local | simular | lista | reload");
        return true;
    }

    private static int parse(String s) {
        try {
            return (int) Math.floor(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        if (a.length == 1) {
            op.add("resgatar");
            if (s.hasPermission("caixas.admin")) op.addAll(List.of("dar", "local", "simular", "lista", "reload"));
        } else if (s.hasPermission("caixas.admin")) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2 && sub.equals("dar")) Bukkit.getOnlinePlayers().forEach(p -> op.add(p.getName()));
            else if ((a.length == 3 && sub.equals("dar")) || (a.length == 2 && (sub.equals("local") || sub.equals("simular")))) op.addAll(caixas.keySet());
        }
        String ult = a[a.length - 1].toLowerCase(Locale.ROOT);
        return op.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(ult)).toList();
    }
}
