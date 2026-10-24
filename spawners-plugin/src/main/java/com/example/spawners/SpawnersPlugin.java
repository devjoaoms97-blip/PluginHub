package com.example.spawners;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spawners empilhados. Comprados no balcão "Spawners" da Loja Oficial ({@code /spawners loja}, aberto
 * pelo vendedor), colocados nos terrenos. Spawners iguais no mesmo bloco se somam; os mobs que eles
 * geram também se somam numa pilha só em cima do spawner, que o jogador mata na espada. Veja o
 * {@link SpawnerManager}.
 */
public class SpawnersPlugin extends JavaPlugin {

    /** Um tipo de spawner do config: a entidade, o nome, o preço e o rank que libera. */
    public record Tipo(String id, EntityType entidade, String nome, double preco, String rank, Material icone) {}

    private Economy economy;
    private final Map<String, Tipo> tipos = new LinkedHashMap<>();
    private NamespacedKey chaveTipo;
    private SpawnerManager spawners;
    private LojaSpawners loja;
    private PixIntegration pix;
    private RankupIntegration rankup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        chaveTipo = new NamespacedKey(this, "tipo");
        carregarTipos();
        pix = new PixIntegration(this);
        rankup = new RankupIntegration(this);
        spawners = new SpawnerManager(this);
        loja = new LojaSpawners(this);
        getServer().getPluginManager().registerEvents(spawners, this);
        getServer().getPluginManager().registerEvents(loja, this);
        long ciclo = Math.max(1, getConfig().getLong("ciclo-segundos", 20)) * 20L;
        getServer().getScheduler().runTaskTimer(this, spawners::ciclo, ciclo, ciclo);
        getLogger().info("SpawnersPlugin ativado! " + tipos.size() + " tipo(s), " + spawners.quantos() + " spawner(s) colocados.");
    }

    @Override
    public void onDisable() {
        if (spawners != null) spawners.salvar();
    }

    private void carregarTipos() {
        tipos.clear();
        ConfigurationSection secao = getConfig().getConfigurationSection("tipos");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            EntityType entidade;
            try {
                entidade = EntityType.valueOf(s.getString("entidade", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                getLogger().warning("Tipo de spawner com entidade inválida: " + id);
                continue;
            }
            Material ovo = Material.matchMaterial(entidade.name() + "_SPAWN_EGG");
            tipos.put(id.toLowerCase(Locale.ROOT), new Tipo(id.toLowerCase(Locale.ROOT), entidade, cores(s.getString("nome", id)),
                    s.getDouble("preco"), s.getString("rank"), ovo != null ? ovo : Material.SPAWNER));
        }
    }

    // ---------------------------------------------------------------------
    // Item do spawner
    // ---------------------------------------------------------------------

    /** O item "Spawner de X": o bloco já mostra o mob girando, e o tipo vai marcado por dentro. */
    public ItemStack item(Tipo tipo, int quantidade) {
        ItemStack item = new ItemStack(Material.SPAWNER, Math.max(1, Math.min(64, quantidade)));
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner estado = (CreatureSpawner) meta.getBlockState();
        estado.setSpawnedType(tipo.entidade());
        meta.setBlockState(estado);
        meta.setDisplayName("§eSpawner de " + tipo.nome());
        meta.setLore(List.of("§7Coloque no seu terreno.", "§7Com outro igual na mão, clique nele pra empilhar.",
                "§7Os mobs se juntam numa pilha em cima dele."));
        meta.getPersistentDataContainer().set(chaveTipo, PersistentDataType.STRING, tipo.id());
        item.setItemMeta(meta);
        return item;
    }

    /** O tipo de um item de spawner nosso, ou null. */
    public Tipo tipoDoItem(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(chaveTipo, PersistentDataType.STRING);
        return id == null ? null : tipos.get(id);
    }

    // ---------------------------------------------------------------------
    // /spawners
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("loja") || sub.isEmpty()) {
            if (sender instanceof Player p) loja.abrir(p);
            else sender.sendMessage("§cSó jogadores.");
            return true;
        }
        if (!sender.hasPermission("spawners.admin")) {
            sender.sendMessage("§cUso: §f/spawners loja §7(ou o vendedor no balcão Spawners da Loja Oficial)");
            return true;
        }
        switch (sub) {
            case "dar" -> {
                Player alvo = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                Tipo tipo = args.length > 2 ? tipos.get(args[2].toLowerCase(Locale.ROOT)) : null;
                if (alvo == null || tipo == null) {
                    sender.sendMessage("§cUso: §f/spawners dar <jogador> <tipo> [quantidade] §7tipos: " + String.join(", ", tipos.keySet()));
                    return true;
                }
                int q = 1;
                try {
                    if (args.length > 3) q = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ignorado) {
                    // fica 1
                }
                alvo.getInventory().addItem(item(tipo, q)).values().forEach(s -> alvo.getWorld().dropItemNaturally(alvo.getLocation(), s));
                sender.sendMessage("§a" + q + "x Spawner de " + tipo.nome() + " §apra §f" + alvo.getName() + "§a.");
            }
            case "lista" -> {
                sender.sendMessage("§e§l--- Spawners colocados: " + spawners.quantos() + " ---");
                spawners.resumo().forEach(sender::sendMessage);
            }
            case "reload" -> {
                reloadConfig();
                carregarTipos();
                sender.sendMessage("§aConfig recarregado (" + tipos.size() + " tipos).");
            }
            default -> sender.sendMessage("§f/spawners loja §7| §f/spawners dar <jogador> <tipo> [qtd] §7| §f/spawners lista §7| §f/spawners reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.add("loja");
            if (sender.hasPermission("spawners.admin")) opcoes.addAll(List.of("dar", "lista", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            opcoes.addAll(tipos.keySet());
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }

    // ---------------------------------------------------------------------

    public Map<String, Tipo> getTipos() {
        return tipos;
    }

    public Tipo tipo(String id) {
        return id == null ? null : tipos.get(id);
    }

    public Economy getEconomy() {
        return economy;
    }

    PixIntegration getPix() {
        return pix;
    }

    RankupIntegration getRankup() {
        return rankup;
    }

    SpawnerManager getSpawners() {
        return spawners;
    }

    static String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }
}
