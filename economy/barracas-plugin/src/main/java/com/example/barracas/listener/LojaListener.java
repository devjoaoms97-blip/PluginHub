package com.example.barracas.listener;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import com.example.barracas.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.UUID;

/**
 * Loja por placa nos baús da barraca.
 *
 * O inquilino coloca um baú com o item, e uma placa nele (na parede do baú ou em cima dele):
 * <pre>
 * [Vender]
 * 64
 * 150        (preço na 3ª ou na 4ª linha)
 * </pre>
 * O plugin reescreve a placa ({@code [Vender] / 64x / diamond / R$ 150,00}) e grava o item, a
 * quantidade, o preço e o dono no PersistentDataContainer da placa — o item vendido fica fixo
 * desde a criação, então ninguém troca o conteúdo do baú depois que o comprador viu o anúncio.
 * A placa é encerada pra ninguém editar o texto.
 *
 * Botão direito na placa compra; botão esquerdo só mostra as informações (e o estoque).
 */
public class LojaListener implements Listener {

    private static final int QUANTIDADE_MAXIMA = 27 * 64; // um baú simples cheio

    private final BarracasPlugin plugin;
    private final NamespacedKey chaveItem;
    private final NamespacedKey chaveQuantidade;
    private final NamespacedKey chavePreco;
    private final NamespacedKey chaveDono;
    private final NamespacedKey chaveBarraca;

    public LojaListener(BarracasPlugin plugin) {
        this.plugin = plugin;
        this.chaveItem = new NamespacedKey(plugin, "loja_item");
        this.chaveQuantidade = new NamespacedKey(plugin, "loja_quantidade");
        this.chavePreco = new NamespacedKey(plugin, "loja_preco");
        this.chaveDono = new NamespacedKey(plugin, "loja_dono");
        this.chaveBarraca = new NamespacedKey(plugin, "loja_barraca");
    }

    // ---------------------------------------------------------------------
    // Criação da placa
    // ---------------------------------------------------------------------
    // HIGHEST (não MONITOR) porque reescreve o texto; roda depois da proteção (HIGH) ter cancelado o que não pode
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoEscreverPlaca(SignChangeEvent evento) {
        String primeira = textoPuro(evento.getLine(0));
        if (!primeira.equalsIgnoreCase("[vender]")) return;

        Player jogador = evento.getPlayer();
        Block bloco = evento.getBlock();
        Barraca barraca = plugin.getBarracaManager().getBarracaEm(bloco);
        if (barraca == null || !jogador.getUniqueId().equals(barraca.getDono())) {
            jogador.sendMessage("§cPlacas §f[Vender] §csó funcionam dentro da sua barraca alugada.");
            return;
        }

        int quantidade;
        try {
            quantidade = Integer.parseInt(textoPuro(evento.getLine(1)).replace("x", "").trim());
        } catch (NumberFormatException e) {
            quantidade = -1;
        }
        if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
            jogador.sendMessage("§cNa 2ª linha coloque a quantidade por venda (1 a " + QUANTIDADE_MAXIMA + ").");
            return;
        }

        double preco = Formato.lerPreco(textoPuro(evento.getLine(3)));
        if (preco <= 0) {
            preco = Formato.lerPreco(textoPuro(evento.getLine(2))); // aceita o preço na 3ª linha também
        }
        if (preco <= 0) {
            jogador.sendMessage("§cNa 3ª ou 4ª linha coloque o preço (ex: §f150§c ou §f150,50§c).");
            return;
        }

        Block bloBau = acharBau(bloco);
        if (bloBau == null || !barraca.getBlocosDoDono().contains(BlocoPos.de(bloBau))
                || !(bloBau.getState() instanceof Container container)) {
            jogador.sendMessage("§cA placa precisa estar presa num baú seu (na lateral ou em cima dele).");
            return;
        }

        ItemStack modelo = primeiroItem(container.getInventory());
        if (modelo == null) {
            jogador.sendMessage("§cColoque o item que quer vender no baú antes de criar a placa.");
            return;
        }

        String nomeItem = Formato.nomeItem(modelo);
        evento.line(0, legado("§1§l[Vender]"));
        evento.line(1, legado("§0" + quantidade + "x"));
        evento.line(2, legado("§0" + (nomeItem.length() > 15 ? nomeItem.substring(0, 15) : nomeItem)));
        evento.line(3, legado("§2" + Formato.valor(preco)));

        // O texto da placa só é aplicado depois do evento; grava os dados no tick seguinte
        int qtdFinal = quantidade;
        double precoFinal = preco;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(bloco.getState() instanceof Sign placa)) return;
            PersistentDataContainer pdc = placa.getPersistentDataContainer();
            pdc.set(chaveItem, PersistentDataType.BYTE_ARRAY, modelo.serializeAsBytes());
            pdc.set(chaveQuantidade, PersistentDataType.INTEGER, qtdFinal);
            pdc.set(chavePreco, PersistentDataType.DOUBLE, precoFinal);
            pdc.set(chaveDono, PersistentDataType.STRING, jogador.getUniqueId().toString());
            pdc.set(chaveBarraca, PersistentDataType.STRING, barraca.getId());
            placa.setWaxed(true);
            placa.update();
        });

        jogador.sendMessage("§aLoja criada: §f" + quantidade + "x " + nomeItem + " §apor §f" + Formato.valor(preco)
                + "§a. Mantenha o baú abastecido com esse mesmo item.");
    }

    // ---------------------------------------------------------------------
    // Clique na placa
    // ---------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarPlaca(PlayerInteractEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND || evento.getClickedBlock() == null) return;
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK && evento.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!(evento.getClickedBlock().getState() instanceof Sign placa)) return;

        PersistentDataContainer pdc = placa.getPersistentDataContainer();
        if (!pdc.has(chaveItem, PersistentDataType.BYTE_ARRAY)) return; // não é placa de loja

        Player jogador = evento.getPlayer();
        UUID dono = UUID.fromString(pdc.get(chaveDono, PersistentDataType.STRING));
        boolean ehDono = dono.equals(jogador.getUniqueId());

        // Botão esquerdo do dono quebra a placa normalmente (pra trocar preço/item)
        if (evento.getAction() == Action.LEFT_CLICK_BLOCK && ehDono) return;
        if (evento.getAction() == Action.RIGHT_CLICK_BLOCK) evento.setCancelled(true); // não abre editor da placa

        ItemStack modelo = ItemStack.deserializeBytes(pdc.get(chaveItem, PersistentDataType.BYTE_ARRAY));
        int quantidade = pdc.getOrDefault(chaveQuantidade, PersistentDataType.INTEGER, 1);
        double preco = pdc.getOrDefault(chavePreco, PersistentDataType.DOUBLE, 0.0);
        Barraca barraca = plugin.getBarracaManager().get(pdc.getOrDefault(chaveBarraca, PersistentDataType.STRING, ""));

        if (barraca == null || !dono.equals(barraca.getDono()) || !barraca.estaAlugada(System.currentTimeMillis())) {
            jogador.sendMessage("§cEssa loja está fechada.");
            return;
        }

        Block bloBau = acharBau(evento.getClickedBlock());
        Inventory estoque = bloBau != null && bloBau.getState() instanceof Container c ? c.getInventory() : null;
        int disponivel = estoque == null ? 0 : contar(estoque, modelo);

        if (evento.getAction() == Action.LEFT_CLICK_BLOCK || ehDono) {
            jogador.sendMessage("§eÀ venda: §f" + quantidade + "x " + Formato.nomeItem(modelo) + " §epor §f"
                    + Formato.valor(preco) + " §7(estoque: " + (disponivel / quantidade) + " venda(s)) §7— loja de §f"
                    + barraca.getNomeDono() + (ehDono ? "" : "§7. Botão direito pra comprar."));
            return;
        }

        comprar(jogador, barraca, estoque, modelo, quantidade, preco, disponivel);
    }

    private void comprar(Player comprador, Barraca barraca, Inventory estoque, ItemStack modelo, int quantidade,
                         double preco, int disponivel) {
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            comprador.sendMessage("§cO sistema de economia (Vault) não está disponível no servidor.");
            return;
        }
        if (estoque == null || disponivel < quantidade) {
            comprador.sendMessage("§cEssa loja está sem estoque no momento.");
            return;
        }
        if (!econ.has(comprador, preco)) {
            comprador.sendMessage("§cSaldo insuficiente. Preço: §f" + Formato.valor(preco)
                    + " §c| Seu saldo: §f" + Formato.valor(econ.getBalance(comprador)));
            return;
        }
        if (espacoLivre(comprador.getInventory(), modelo) < quantidade) {
            comprador.sendMessage("§cVocê não tem espaço no inventário pra §f" + quantidade + "x " + Formato.nomeItem(modelo) + "§c.");
            return;
        }

        UUID dono = barraca.getDono();
        double taxa = preco * plugin.getTaxaVendaPercentual() / 100.0;
        double liquido = preco - taxa;

        econ.withdrawPlayer(comprador, preco);
        econ.depositPlayer(Bukkit.getOfflinePlayer(dono), liquido);

        remover(estoque, modelo, quantidade);
        entregar(comprador, modelo, quantidade);

        plugin.getPixIntegration().registrarEntreJogadores(comprador.getUniqueId(), dono, preco, "Compra na barraca");

        String descricao = quantidade + "x " + Formato.nomeItem(modelo);
        comprador.sendMessage("§aVocê comprou §f" + descricao + " §ade §f" + barraca.getNomeDono()
                + " §apor §f" + Formato.valor(preco) + "§a.");
        comprador.playSound(comprador.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        Player donoOnline = Bukkit.getPlayer(dono);
        if (donoOnline != null) {
            donoOnline.sendMessage("§a" + comprador.getName() + " comprou §f" + descricao + " §ana sua barraca. Você recebeu §f"
                    + Formato.valor(liquido) + (taxa > 0 ? " §7(taxa: " + Formato.valor(taxa) + ")" : "") + "§a.");
            if (contar(estoque, modelo) < quantidade) {
                donoOnline.sendMessage("§eSua loja de §f" + Formato.nomeItem(modelo) + " §eficou sem estoque.");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------

    /** Baú onde a placa está presa (placa de parede) ou logo abaixo/acima dela. */
    private Block acharBau(Block placa) {
        if (placa.getBlockData() instanceof WallSign parede) {
            Block preso = placa.getRelative(parede.getFacing().getOppositeFace());
            return plugin.isBau(preso.getType()) ? preso : null;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.DOWN, BlockFace.UP}) {
            Block vizinho = placa.getRelative(face);
            if (plugin.isBau(vizinho.getType())) return vizinho;
        }
        return null;
    }

    private ItemStack primeiroItem(Inventory inventario) {
        for (ItemStack item : inventario.getContents()) {
            if (item != null && !item.getType().isAir()) {
                ItemStack modelo = item.clone();
                modelo.setAmount(1);
                return modelo;
            }
        }
        return null;
    }

    private int contar(Inventory inventario, ItemStack modelo) {
        int total = 0;
        for (ItemStack item : inventario.getContents()) {
            if (item != null && item.isSimilar(modelo)) total += item.getAmount();
        }
        return total;
    }

    private int espacoLivre(Inventory inventario, ItemStack modelo) {
        int livre = 0;
        int max = modelo.getMaxStackSize();
        ItemStack[] slots = inventario instanceof org.bukkit.inventory.PlayerInventory p
                ? p.getStorageContents() : inventario.getContents();
        for (ItemStack item : slots) {
            if (item == null || item.getType().isAir()) livre += max;
            else if (item.isSimilar(modelo)) livre += Math.max(0, max - item.getAmount());
        }
        return livre;
    }

    private void remover(Inventory inventario, ItemStack modelo, int quantidade) {
        int faltam = quantidade;
        for (int i = 0; i < inventario.getSize() && faltam > 0; i++) {
            ItemStack item = inventario.getItem(i);
            if (item == null || !item.isSimilar(modelo)) continue;
            int tirar = Math.min(faltam, item.getAmount());
            faltam -= tirar;
            if (tirar == item.getAmount()) {
                inventario.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - tirar);
            }
        }
    }

    private void entregar(Player jogador, ItemStack modelo, int quantidade) {
        int faltam = quantidade;
        while (faltam > 0) {
            ItemStack pilha = modelo.clone();
            int qtd = Math.min(faltam, modelo.getMaxStackSize());
            pilha.setAmount(qtd);
            faltam -= qtd;
            // espaço já conferido antes; o que sobrar (não deveria) cai no chão em vez de sumir
            jogador.getInventory().addItem(pilha).values()
                    .forEach(sobra -> jogador.getWorld().dropItemNaturally(jogador.getLocation(), sobra));
        }
    }

    private String textoPuro(String linha) {
        return linha == null ? "" : org.bukkit.ChatColor.stripColor(linha).trim().toLowerCase(Locale.ROOT);
    }

    private net.kyori.adventure.text.Component legado(String texto) {
        return LegacyComponentSerializer.legacySection().deserialize(texto);
    }
}
