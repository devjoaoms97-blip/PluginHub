package com.example.barracas.listener;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.Oferta;
import com.example.barracas.util.Formato;
import com.example.barracas.util.Itens;
import com.example.barracas.util.MissoesIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loja do NPC da barraca. Clicar no vendedor abre um menu com as ofertas do dono:
 * <ul>
 *   <li><b>Visitante</b>: clique numa oferta de venda compra um lote; numa oferta de compra, vende
 *       um lote pro dono (os itens saem do inventário e o dinheiro sai da conta do dono).</li>
 *   <li><b>Dono</b>: o mesmo menu vira o estoque — esquerdo coloca do inventário, direito retira,
 *       shift + clique remove o anúncio. Os anúncios são criados com /barraca vender e /barraca comprar.</li>
 * </ul>
 * O estoque é virtual (fica na barraca, no barracas.yml). A taxa de venda sai de quem recebe.
 */
public class LojaListener implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final int TAMANHO = 27;
    private static final int SLOT_INFO = 22;

    /** Menu aberto: de qual barraca, se é a visão do dono, e qual oferta está em cada slot. */
    private static final class Menu implements InventoryHolder {
        private final String barraca;
        private final boolean dono;
        private final Map<Integer, Oferta> ofertas = new HashMap<>();
        private Inventory inventario;

        private Menu(String barraca, boolean dono) {
            this.barraca = barraca;
            this.dono = dono;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final BarracasPlugin plugin;
    private final MissoesIntegration missoes;

    public LojaListener(BarracasPlugin plugin) {
        this.plugin = plugin;
        this.missoes = new MissoesIntegration(plugin);
    }

    // ---------------------------------------------------------------------
    // Abrir
    // ---------------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOW)
    public void aoClicarNpc(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        String id = plugin.getNpcs().getBarraca(evento.getRightClicked());
        if (id == null) return;
        evento.setCancelled(true);

        Barraca barraca = plugin.getBarracaManager().get(id);
        if (barraca == null || !barraca.estaAlugada(System.currentTimeMillis())) {
            evento.getPlayer().sendMessage("§cEssa loja está fechada.");
            return;
        }
        abrir(evento.getPlayer(), barraca);
    }

    /** Abre a loja: o dono vê o estoque, os outros veem a vitrine. */
    public void abrir(Player jogador, Barraca barraca) {
        boolean dono = jogador.getUniqueId().equals(barraca.getDono());
        Menu menu = new Menu(barraca.getId(), dono);
        String titulo = dono ? "§8Sua loja (estoque)" : "§8Loja de " + barraca.getNomeDono();
        menu.inventario = Bukkit.createInventory(menu, TAMANHO, LEGADO.deserialize(titulo));
        desenhar(menu, barraca);
        jogador.openInventory(menu.inventario);
    }

    private void desenhar(Menu menu, Barraca barraca) {
        Inventory inv = menu.inventario;
        inv.clear();
        menu.ofertas.clear();

        List<Oferta> ofertas = barraca.getOfertas();
        for (int i = 0; i < ofertas.size() && i < SLOT_INFO; i++) {
            Oferta oferta = ofertas.get(i);
            menu.ofertas.put(i, oferta);
            inv.setItem(i, icone(oferta, menu.dono));
        }

        List<String> info = new ArrayList<>();
        if (menu.dono) {
            info.add("§7Anúncios: §f" + ofertas.size() + "/" + plugin.getMaxOfertas(barraca));
            info.add("§7Novo anúncio, com o item na mão:");
            info.add("§f/barraca vender <quantidade> <preço>");
            info.add("§f/barraca comprar <quantidade> <preço>");
            info.add("§7Taxa de cada venda: §f" + plugin.getTaxaVendaPercentual() + "%");
        } else {
            info.add("§7Dono: §f" + barraca.getNomeDono());
            info.add(ofertas.isEmpty() ? "§7Nenhum anúncio no momento." : "§7Clique num item pra comprar ou vender.");
        }
        inv.setItem(SLOT_INFO, item(Material.OAK_SIGN, (barraca.isEspecial() ? "§6★ " : "§e") + "Barraca " + barraca.getId(), info));
    }

    private ItemStack icone(Oferta oferta, boolean dono) {
        ItemStack icone = oferta.getModelo();
        icone.setAmount(Math.max(1, Math.min(oferta.getQuantidade(), icone.getMaxStackSize())));
        int capacidade = plugin.getEstoqueMaximo();
        int lotes = oferta.getEstoque() / oferta.getQuantidade();

        List<String> lore = new ArrayList<>();
        boolean venda = oferta.getTipo() == Oferta.Tipo.VENDA;
        lore.add(venda ? "§a§lÀ VENDA" : "§d§lCOMPRA-SE");
        lore.add("§f" + oferta.getQuantidade() + "x §7por §f" + Formato.valor(oferta.getPreco()));
        if (dono) {
            if (venda) {
                lore.add("§7Estoque: §f" + oferta.getEstoque() + "/" + capacidade + " §7(" + lotes + " venda(s))");
                lore.add("§eEsquerdo: §7colocar do seu inventário");
                lore.add("§eDireito: §7retirar o estoque");
            } else {
                lore.add("§7Já comprado: §f" + oferta.getEstoque() + "/" + capacidade);
                lore.add("§eDireito: §7retirar os itens comprados");
            }
            lore.add("§cShift + clique: §7remover o anúncio");
        } else if (venda) {
            lore.add(lotes > 0 ? "§7Estoque: §f" + lotes + " venda(s)" : "§cSem estoque");
            lore.add("§eClique pra comprar");
        } else {
            int cabe = (capacidade - oferta.getEstoque()) / oferta.getQuantidade();
            lore.add(cabe > 0 ? "§7Ainda compra: §f" + cabe + " vez(es)" : "§cNão está comprando agora");
            lore.add("§eClique pra vender");
        }

        ItemMeta meta = icone.getItemMeta();
        List<Component> linhas = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        if (!linhas.isEmpty()) linhas.add(Component.empty());
        lore.forEach(l -> linhas.add(LEGADO.deserialize(l)));
        meta.lore(linhas);
        icone.setItemMeta(meta);
        return icone;
    }

    private ItemStack item(Material material, String nome, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGADO.deserialize(nome));
        meta.lore(lore.stream().map(l -> (Component) LEGADO.deserialize(l)).toList());
        item.setItemMeta(meta);
        return item;
    }

    /** Redesenha todos os menus abertos dessa barraca (o estoque mudou pra todo mundo). */
    private void redesenharAbertos(Barraca barraca) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu
                    && menu.barraca.equals(barraca.getId())) {
                desenhar(menu, barraca);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Cliques no menu
    // ---------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void aoClicarMenu(InventoryClickEvent evento) {
        if (!(evento.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true); // nada entra nem sai do menu como item de verdade
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;

        Barraca barraca = plugin.getBarracaManager().get(menu.barraca);
        if (barraca == null || !barraca.estaAlugada(System.currentTimeMillis())) {
            jogador.closeInventory();
            jogador.sendMessage("§cEssa loja fechou.");
            return;
        }
        boolean dono = jogador.getUniqueId().equals(barraca.getDono());
        if (dono != menu.dono) { // a barraca trocou de dono com o menu aberto
            jogador.closeInventory();
            return;
        }

        Oferta oferta = menu.ofertas.get(evento.getSlot());
        if (oferta == null || !barraca.getOfertas().contains(oferta)) {
            desenhar(menu, barraca);
            return;
        }

        if (dono) {
            if (evento.isShiftClick()) removerOferta(jogador, barraca, oferta);
            else if (evento.isLeftClick() && oferta.getTipo() == Oferta.Tipo.VENDA) depositar(jogador, oferta);
            else if (evento.isRightClick()) retirar(jogador, oferta);
        } else if (oferta.getTipo() == Oferta.Tipo.VENDA) {
            comprar(jogador, barraca, oferta);
        } else {
            venderPraLoja(jogador, barraca, oferta);
        }

        plugin.getBarracaManager().salvar();
        redesenharAbertos(barraca);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void aoArrastarMenu(InventoryDragEvent evento) {
        if (evento.getView().getTopInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // Visitante
    // ---------------------------------------------------------------------
    private void comprar(Player comprador, Barraca barraca, Oferta oferta) {
        Economy econ = exigirEconomia(comprador);
        if (econ == null) return;
        int quantidade = oferta.getQuantidade();
        double preco = oferta.getPreco();
        ItemStack modelo = oferta.getModelo();

        if (oferta.getEstoque() < quantidade) {
            comprador.sendMessage("§cEsse item está sem estoque.");
            return;
        }
        if (!econ.has(comprador, preco)) {
            comprador.sendMessage("§cSaldo insuficiente. Preço: §f" + Formato.valor(preco)
                    + " §c| Seu saldo: §f" + Formato.valor(econ.getBalance(comprador)));
            return;
        }
        if (Itens.espacoLivre(comprador, modelo) < quantidade) {
            comprador.sendMessage("§cVocê não tem espaço no inventário pra §f" + quantidade + "x " + Formato.nomeItem(modelo) + "§c.");
            return;
        }

        UUID dono = barraca.getDono();
        double taxa = preco * plugin.getTaxaVendaPercentual() / 100.0;
        double liquido = preco - taxa;

        econ.withdrawPlayer(comprador, preco);
        econ.depositPlayer(Bukkit.getOfflinePlayer(dono), liquido);
        oferta.setEstoque(oferta.getEstoque() - quantidade);
        Itens.entregar(comprador, modelo, quantidade);

        plugin.getPixIntegration().registrarEntreJogadores(comprador.getUniqueId(), dono, preco, "Compra na barraca");

        String descricao = quantidade + "x " + Formato.nomeItem(modelo);
        comprador.sendMessage("§aVocê comprou §f" + descricao + " §ade §f" + barraca.getNomeDono()
                + " §apor §f" + Formato.valor(preco) + "§a.");
        comprador.playSound(comprador.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        Player donoOnline = Bukkit.getPlayer(dono);
        missoes.progresso(donoOnline, "VENDER_BARRACA", modelo.getType().name(), quantidade); // a loja dele vendeu
        if (donoOnline != null) {
            donoOnline.sendMessage("§a" + comprador.getName() + " comprou §f" + descricao + " §ana sua barraca. Você recebeu §f"
                    + Formato.valor(liquido) + (taxa > 0 ? " §7(taxa: " + Formato.valor(taxa) + ")" : "") + "§a.");
            if (oferta.getEstoque() < quantidade) {
                donoOnline.sendMessage("§eSua loja de §f" + Formato.nomeItem(modelo) + " §eficou sem estoque.");
            }
        }
    }

    /** Oferta de compra: o visitante entrega os itens e recebe do dono (menos a taxa). */
    private void venderPraLoja(Player vendedor, Barraca barraca, Oferta oferta) {
        Economy econ = exigirEconomia(vendedor);
        if (econ == null) return;
        int quantidade = oferta.getQuantidade();
        double preco = oferta.getPreco();
        ItemStack modelo = oferta.getModelo();

        if (Itens.contar(vendedor, modelo) < quantidade) {
            vendedor.sendMessage("§cVocê precisa ter §f" + quantidade + "x " + Formato.nomeItem(modelo)
                    + " §cno inventário (o mesmo item, sem diferença de encantamento ou nome).");
            return;
        }
        if (oferta.getEstoque() + quantidade > plugin.getEstoqueMaximo()) {
            vendedor.sendMessage("§cEssa barraca já comprou o máximo desse item e não está comprando agora.");
            return;
        }
        UUID dono = barraca.getDono();
        OfflinePlayer contaDono = Bukkit.getOfflinePlayer(dono);
        if (!econ.has(contaDono, preco)) {
            vendedor.sendMessage("§cO dono dessa barraca está sem saldo pra comprar agora.");
            Player donoOnline = Bukkit.getPlayer(dono);
            if (donoOnline != null) {
                donoOnline.sendMessage("§e" + vendedor.getName() + " tentou vender §f" + quantidade + "x " + Formato.nomeItem(modelo)
                        + " §epra sua barraca, mas você não tem saldo (§f" + Formato.valor(preco) + "§e).");
            }
            return;
        }

        double taxa = preco * plugin.getTaxaVendaPercentual() / 100.0;
        double liquido = preco - taxa;

        econ.withdrawPlayer(contaDono, preco);
        econ.depositPlayer(vendedor, liquido);
        Itens.remover(vendedor, modelo, quantidade);
        oferta.setEstoque(oferta.getEstoque() + quantidade);

        plugin.getPixIntegration().registrarEntreJogadores(dono, vendedor.getUniqueId(), preco, "Venda pra barraca");

        String descricao = quantidade + "x " + Formato.nomeItem(modelo);
        vendedor.sendMessage("§aVocê vendeu §f" + descricao + " §apra barraca de §f" + barraca.getNomeDono()
                + " §apor §f" + Formato.valor(liquido) + (taxa > 0 ? " §7(taxa: " + Formato.valor(taxa) + ")" : "") + "§a.");
        vendedor.playSound(vendedor.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        missoes.progresso(vendedor, "VENDER_BARRACA", modelo.getType().name(), quantidade);

        Player donoOnline = Bukkit.getPlayer(dono);
        if (donoOnline != null) {
            donoOnline.sendMessage("§a" + vendedor.getName() + " vendeu §f" + descricao + " §apra sua barraca. Você pagou §f"
                    + Formato.valor(preco) + "§a. Retire os itens no NPC ou com §f/barraca loja§a.");
        }
    }

    // ---------------------------------------------------------------------
    // Dono
    // ---------------------------------------------------------------------
    /** Move do inventário pro estoque tudo o que for igual ao item anunciado, até a capacidade. */
    public void depositar(Player dono, Oferta oferta) {
        ItemStack modelo = oferta.getModelo();
        int cabe = plugin.getEstoqueMaximo() - oferta.getEstoque();
        int quantidade = Math.min(Itens.contar(dono, modelo), cabe);
        if (quantidade <= 0) {
            dono.sendMessage(cabe <= 0
                    ? "§cO estoque desse anúncio está cheio (§f" + plugin.getEstoqueMaximo() + "§c)."
                    : "§cVocê não tem §f" + Formato.nomeItem(modelo) + " §cigual ao anunciado no inventário.");
            return;
        }
        Itens.remover(dono, modelo, quantidade);
        oferta.setEstoque(oferta.getEstoque() + quantidade);
        dono.sendMessage("§aColocou §f" + quantidade + "x " + Formato.nomeItem(modelo) + " §ano estoque (agora §f"
                + oferta.getEstoque() + "§a).");
    }

    private void retirar(Player dono, Oferta oferta) {
        ItemStack modelo = oferta.getModelo();
        int quantidade = Math.min(oferta.getEstoque(), Itens.espacoLivre(dono, modelo));
        if (quantidade <= 0) {
            dono.sendMessage(oferta.getEstoque() <= 0 ? "§7Não tem nada no estoque desse anúncio." : "§cSeu inventário está cheio.");
            return;
        }
        oferta.setEstoque(oferta.getEstoque() - quantidade);
        Itens.entregar(dono, modelo, quantidade);
        dono.sendMessage("§aRetirou §f" + quantidade + "x " + Formato.nomeItem(modelo) + "§a."
                + (oferta.getEstoque() > 0 ? " §7Ainda ficaram §f" + oferta.getEstoque() + "§7 (inventário cheio)." : ""));
    }

    /** Remove o anúncio: o estoque volta pro inventário, e o que não couber vai pra caixa de retirada. */
    private void removerOferta(Player dono, Barraca barraca, Oferta oferta) {
        ItemStack modelo = oferta.getModelo();
        int proInventario = Math.min(oferta.getEstoque(), Itens.espacoLivre(dono, modelo));
        int praRetirada = oferta.getEstoque() - proInventario;
        Itens.entregar(dono, modelo, proInventario);
        if (praRetirada > 0) {
            plugin.getRetiradaManager().adicionar(dono.getUniqueId(), Itens.pilhas(modelo, praRetirada));
        }
        barraca.getOfertas().remove(oferta);
        dono.sendMessage("§eAnúncio de §f" + Formato.nomeItem(modelo) + " §eremovido."
                + (praRetirada > 0 ? " §7O que não coube (§f" + praRetirada + "§7) está em §f/barraca retirar§7." : ""));
    }

    private Economy exigirEconomia(Player jogador) {
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            jogador.sendMessage("§cO sistema de economia (Vault) não está disponível no servidor.");
        }
        return econ;
    }
}
