package com.example.rankup.command;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * /kit iniciante: o kit de quem está começando (ferramentas de madeira, pão, tochas), a cada
 * kit-iniciante.cooldown-horas, só no primeiro rank e sem prestígio. Os itens não vendem na loja, pra
 * não virar fonte de dinheiro. A última retirada fica em kit-iniciante.yml. Os kits do VIP ficam no /vip kit.
 */
public class KitCommand implements CommandExecutor, TabCompleter, Listener {

    private final RankupPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Long> ultimas = new ConcurrentHashMap<>();

    public KitCommand(RankupPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "kit-iniciante.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String chave : yaml.getKeys(false)) {
            try {
                ultimas.put(UUID.fromString(chave), yaml.getLong(chave));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em kit-iniciante.yml: " + chave);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores pegam kits.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("iniciante")) {
            jogador.sendMessage("§6§l--- Kits ---");
            jogador.sendMessage("§f/kit iniciante §7- ferramentas, comida e tochas, a cada §f" + Formato.tempo(cooldown())
                    + "§7, pra quem é " + plugin.getRankManager().getPrimeiro().nomeColorido());
            jogador.sendMessage("§f/vip kit §7- os kits do VIP");
            return true;
        }
        String motivo = podePegar(jogador);
        if (motivo != null) {
            jogador.sendMessage("§c" + motivo);
            return true;
        }
        List<ItemStack> itens = itens();
        if (vazios(jogador) < itens.size()) {
            jogador.sendMessage("§cAbra espaço no inventário: o kit precisa de §f" + itens.size() + " §cespaços livres.");
            return true;
        }
        for (ItemStack item : itens) jogador.getInventory().addItem(item);
        ultimas.put(jogador.getUniqueId(), System.currentTimeMillis());
        salvar();
        jogador.sendMessage("§a§l✔ Kit de iniciante recebido! §7Dá pra pegar de novo em §f" + Formato.tempo(cooldown())
                + "§7, enquanto você for " + plugin.getRankManager().getPrimeiro().nomeColorido() + "§7.");
        return true;
    }

    /** null se pode; senão, o motivo. */
    private String podePegar(Player jogador) {
        Rank primeiro = plugin.getRankManager().getPrimeiro();
        if (!plugin.getRankManager().getRankDe(jogador.getUniqueId()).equals(primeiro)
                || plugin.getPrestigioManager().nivel(jogador.getUniqueId()) > 0) {
            return "O kit de iniciante é só pra quem está no rank " + primeiro.nomeColorido() + "§c.";
        }
        long falta = ultimas.getOrDefault(jogador.getUniqueId(), 0L) + cooldown() - System.currentTimeMillis();
        if (falta > 0) return "Você já pegou o kit. Volte em §f" + Formato.tempo(falta) + "§c.";
        return null;
    }

    /** Na primeira entrada, avisa que o kit existe. */
    @EventHandler
    public void aoEntrar(PlayerJoinEvent e) {
        if (e.getPlayer().hasPlayedBefore()) return;
        e.getPlayer().sendMessage("§a§l✦ Bem-vindo! §7Pegue seu kit com §f/kit iniciante§7: ferramentas, comida e tochas.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("iniciante") : List.of();
    }

    private long cooldown() {
        return (long) (Math.max(0, plugin.getConfig().getDouble("kit-iniciante.cooldown-horas", 2)) * 3_600_000L);
    }

    /** Os itens do config: "MATERIAL:quantidade". */
    private List<ItemStack> itens() {
        List<ItemStack> itens = new ArrayList<>();
        for (String linha : plugin.getConfig().getStringList("kit-iniciante.itens")) {
            String[] partes = linha.split(":");
            Material material = Material.matchMaterial(partes[0].trim());
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Item inválido em kit-iniciante.itens: " + linha);
                continue;
            }
            int quantidade = partes.length > 1 ? Integer.parseInt(partes[1].trim()) : 1;
            itens.add(new ItemStack(material, quantidade));
        }
        return itens;
    }

    private int vazios(Player jogador) {
        int n = 0;
        for (ItemStack item : jogador.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) n++;
        }
        return n;
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        ultimas.forEach((id, quando) -> yaml.set(id.toString(), quando));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar kit-iniciante.yml", e);
        }
    }
}
