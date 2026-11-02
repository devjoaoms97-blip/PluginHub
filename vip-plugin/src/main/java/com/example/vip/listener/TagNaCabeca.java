package com.example.vip.listener;

import com.example.vip.VipPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A tag acima da cabeça: com rank, clã, VIP e apoiador, o nome ficava enorme. Na tab e no chat as tags
 * continuam todas; acima da cabeça vai só UMA, que o jogador escolhe no {@code /tag} (ou nenhuma). Sem
 * escolha (ou se a escolhida sumiu, como o VIP que venceu), vale a primeira de {@code tag-na-cabeca.padrao}
 * que ele tem. Quem desenha o nome é o PlacarPlugin, que lê {@link #nomeNaCabeca} por reflexão.
 */
public class TagNaCabeca implements TabExecutor, Listener {

    static final List<String> TIPOS = List.of("vip", "apoiador", "cla", "rank", "nenhuma");
    private static final Map<String, String> NOMES = Map.of(
            "vip", "VIP", "apoiador", "Apoiador", "cla", "Clã", "rank", "Rank", "nenhuma", "Nenhuma");

    private static class Menu implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventario;

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final VipPlugin plugin;
    private final ChatTagListener tags;
    private final File arquivo;
    private final Map<UUID, String> escolhas = new HashMap<>();

    public TagNaCabeca(VipPlugin plugin, ChatTagListener tags) {
        this.plugin = plugin;
        this.tags = tags;
        this.arquivo = new File(plugin.getDataFolder(), "tags-cabeca.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String id : yaml.getKeys(false)) {
            try {
                escolhas.put(UUID.fromString(id), yaml.getString(id));
            } catch (IllegalArgumentException ignorado) {
                // UUID inválido: descarta
            }
        }
    }

    /** As tags que o jogador tem agora, na ordem do menu (tipo → texto colorido). "nenhuma" sempre. */
    Map<String, String> disponiveis(UUID jogador) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String tipo : TIPOS) {
            String texto = switch (tipo) {
                case "vip" -> tags.tagVip(jogador);
                case "apoiador" -> tags.tagApoiador(jogador);
                case "cla" -> tags.tagCla(jogador);
                case "rank" -> tags.tagRank(jogador);
                default -> "";
            };
            if (tipo.equals("nenhuma") || !texto.isEmpty()) mapa.put(tipo, texto);
        }
        return mapa;
    }

    /** O tipo que aparece: o escolhido, se o jogador ainda tem; senão o primeiro do padrão que ele tem. */
    String escolhida(UUID jogador) {
        Map<String, String> tem = disponiveis(jogador);
        String escolha = escolhas.get(jogador);
        if (escolha != null && tem.containsKey(escolha)) return escolha;
        for (String tipo : plugin.getConfig().getStringList("tag-na-cabeca.padrao")) {
            if (tem.containsKey(tipo.toLowerCase(Locale.ROOT))) return tipo.toLowerCase(Locale.ROOT);
        }
        return "nenhuma";
    }

    /**
     * API (PlacarPlugin, por reflexão): {prefixo acima da cabeça, código da cor do nome}. O prefixo já
     * vem com o espaço antes do nome ("§b[Diamante] "), ou "" sem tag.
     */
    public String[] nomeNaCabeca(Player jogador) {
        String texto = disponiveis(jogador.getUniqueId()).getOrDefault(escolhida(jogador.getUniqueId()), "");
        String cor = tags.corDoNome(jogador.getUniqueId());
        return new String[]{texto.isEmpty() ? "" : texto + " ", cor.length() >= 2 ? cor.substring(1, 2) : "f"};
    }

    // ---------------------------------------------------------------------
    // /tag
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores escolhem a tag.");
            return true;
        }
        if (args.length == 0) {
            abrir(jogador);
            return true;
        }
        escolher(jogador, args[0].toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !(sender instanceof Player jogador)) return List.of();
        List<String> lista = new ArrayList<>();
        for (String tipo : disponiveis(jogador.getUniqueId()).keySet()) {
            if (tipo.startsWith(args[0].toLowerCase(Locale.ROOT))) lista.add(tipo);
        }
        return lista;
    }

    private void abrir(Player jogador) {
        Menu menu = new Menu();
        menu.inventario = Bukkit.createInventory(menu, 9, "§8Tag acima da cabeça");
        String atual = escolhida(jogador.getUniqueId());
        String cor = tags.corDoNome(jogador.getUniqueId());
        int slot = 0;
        for (Map.Entry<String, String> e : disponiveis(jogador.getUniqueId()).entrySet()) {
            boolean nenhuma = e.getKey().equals("nenhuma");
            ItemStack item = new ItemStack(nenhuma ? Material.BARRIER : Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((e.getKey().equals(atual) ? "§a✔ " : "§e") + NOMES.get(e.getKey()));
            meta.setLore(List.of("§7Fica assim:", "§f" + (nenhuma ? "" : e.getValue() + " ") + cor + jogador.getName(), "",
                    e.getKey().equals(atual) ? "§aÉ a que aparece agora." : "§eClique pra usar essa."));
            item.setItemMeta(meta);
            int onde = nenhuma ? 8 : slot; // "nenhuma" sempre no fim da linha
            menu.inventario.setItem(onde, item);
            menu.slots.put(onde, e.getKey());
            if (!nenhuma) slot += 2;
        }
        jogador.openInventory(menu.inventario);
    }

    private void escolher(Player jogador, String tipo) {
        if (!TIPOS.contains(tipo)) {
            jogador.sendMessage("§cTag desconhecida. Use §f/tag §cpra escolher no menu.");
            return;
        }
        if (!disponiveis(jogador.getUniqueId()).containsKey(tipo)) {
            jogador.sendMessage("§cVocê não tem tag de " + NOMES.get(tipo) + ".");
            return;
        }
        escolhas.put(jogador.getUniqueId(), tipo);
        salvar();
        jogador.sendMessage(tipo.equals("nenhuma") ? "§aPronto: nenhuma tag acima da sua cabeça."
                : "§aPronto: acima da sua cabeça aparece a tag de §f" + NOMES.get(tipo) + "§a.");
        jogador.playSound(jogador.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        escolhas.forEach((id, tipo) -> yaml.set(id.toString(), tipo));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar tags-cabeca.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        String tipo = menu.slots.get(evento.getRawSlot());
        if (tipo == null) return;
        jogador.closeInventory();
        escolher(jogador, tipo);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }
}
