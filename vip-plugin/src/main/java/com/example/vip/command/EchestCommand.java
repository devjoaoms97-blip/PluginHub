package com.example.vip.command;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipTier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /echest (pedido de 2026-11-07): um baú pessoal que abre de qualquer lugar, separado do baú do fim do
 * jogo. O tamanho vem do VIP ({@code echest.tamanhos}): 9 espaços pra todo mundo, mais no VIP. Fica em
 * {@code echest/<uuid>.yml}, salvo a cada mudança (um tique depois do clique) e ao fechar: se o servidor
 * cair com ele aberto, o arquivo e o inventário do jogador não ficam com o mesmo item. Se o VIP acaba e o baú diminui, o que passou do novo
 * tamanho fica guardado (não some) e volta quando o VIP voltar; o jogador é avisado.
 * <p>
 * Em combate não abre ({@code comandos} do CombatePlugin), nem nas arenas dos eventos (só os comandos do
 * evento valem lá).
 */
public class EchestCommand implements CommandExecutor, Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    /** O baú aberto de um jogador: quem é o dono e os itens que passaram do tamanho atual. */
    private static final class Bau implements InventoryHolder {
        final UUID dono;
        final List<ItemStack> guardados;
        Inventory inventario;

        Bau(UUID dono, List<ItemStack> guardados) {
            this.dono = dono;
            this.guardados = guardados;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final VipPlugin plugin;
    private final File pasta;

    public EchestCommand(VipPlugin plugin) {
        this.plugin = plugin;
        this.pasta = new File(plugin.getDataFolder(), "echest");
    }

    /** Quantos espaços o jogador tem: o do tier do VIP, ou o padrão. Múltiplo de 9, de 9 a 54. */
    int tamanho(Player p) {
        VipTier tier = plugin.getVipManager().getTierAtivo(p.getUniqueId());
        String chave = tier == null ? "padrao" : tier.name().toLowerCase(Locale.ROOT);
        int n = plugin.getConfig().getInt("echest.tamanhos." + chave, plugin.getConfig().getInt("echest.tamanhos.padrao", 9));
        return Math.max(9, Math.min(54, (n + 8) / 9 * 9));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSó jogadores.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("echest.ativado", true)) {
            p.sendMessage("§cO /echest está desligado.");
            return true;
        }
        abrir(p);
        return true;
    }

    private void abrir(Player p) {
        int tamanho = tamanho(p);
        List<ItemStack> itens = carregar(p.getUniqueId());
        List<ItemStack> guardados = new ArrayList<>();
        Bau bau = new Bau(p.getUniqueId(), guardados);
        bau.inventario = Bukkit.createInventory(bau, tamanho, LEGADO.deserialize("§5Baú pessoal §8(" + tamanho + " espaços)"));
        for (int i = 0; i < itens.size(); i++) {
            ItemStack item = itens.get(i);
            if (item == null) continue;
            if (i < tamanho) bau.inventario.setItem(i, item);
            else guardados.add(item);
        }
        p.openInventory(bau.inventario);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f);
        if (!guardados.isEmpty()) {
            p.sendMessage("§e" + guardados.size() + " item(ns) do seu baú estão guardados além dos seus " + tamanho
                    + " espaços: voltam quando o seu VIP voltar (§f/vip tiers§e).");
        }
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (evento.getView().getTopInventory().getHolder() instanceof Bau bau) depois(bau);
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getView().getTopInventory().getHolder() instanceof Bau bau) depois(bau);
    }

    /** Salva no tique seguinte, quando o clique já mexeu nos itens. */
    private void depois(Bau bau) {
        Bukkit.getScheduler().runTask(plugin, () -> gravar(bau));
    }

    @EventHandler
    public void aoFechar(InventoryCloseEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Bau bau)) return;
        gravar(bau);
        if (evento.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6f, 1f);
    }

    private void gravar(Bau bau) {
        List<ItemStack> itens = new ArrayList<>();
        for (ItemStack item : bau.inventario.getContents()) itens.add(item);
        // o que estava além do tamanho volta depois, na ordem
        itens.addAll(bau.guardados);
        salvar(bau.dono, itens);
    }

    private File arquivo(UUID dono) {
        return new File(pasta, dono + ".yml");
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> carregar(UUID dono) {
        File f = arquivo(dono);
        if (!f.exists()) return new ArrayList<>();
        List<?> lista = YamlConfiguration.loadConfiguration(f).getList("itens");
        List<ItemStack> itens = new ArrayList<>();
        if (lista != null) for (Object o : lista) itens.add(o instanceof ItemStack i ? i : null);
        return itens;
    }

    private void salvar(UUID dono, List<ItemStack> itens) {
        // tira os vazios do fim (o arquivo não cresce à toa)
        int fim = itens.size();
        while (fim > 0 && (itens.get(fim - 1) == null || itens.get(fim - 1).getType().isAir())) fim--;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("itens", new ArrayList<>(itens.subList(0, fim)));
        try {
            if (!pasta.exists()) pasta.mkdirs();
            yaml.save(arquivo(dono));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar o baú pessoal de " + dono, e);
        }
    }
}
