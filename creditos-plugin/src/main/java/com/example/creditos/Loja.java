package com.example.creditos;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A loja do /creditos: VIP (ativar pra si ou gerar um código de presente) e os cosméticos de cada
 * categoria. Preço fixo e sem sorteio. Como os créditos são dinheiro de verdade, toda compra pede um
 * segundo clique pra confirmar (em até 10 s). Cosmético comprado fica pra sempre: na loja mesmo o
 * jogador escolhe qual usar, e clicar no que está em uso tira.
 */
public class Loja implements Listener {

    private static final String[] TIERS = {"bronze", "prata", "ouro", "diamante"};
    private static final Material[] ICONE_TIER = {Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND};

    enum Tela { INICIO, VIP, RASTRO, COR, ABATE }

    static class Menu implements InventoryHolder {
        final Tela tela;
        final Map<Integer, String> slotParaId = new HashMap<>();
        Inventory inventario;

        Menu(Tela tela) {
            this.tela = tela;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private record Pendente(String chave, long ate) {}

    private final CreditosPlugin plugin;
    private final Map<UUID, Pendente> confirmando = new HashMap<>();

    public Loja(CreditosPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------------
    // Telas
    // ---------------------------------------------------------------------

    public void abrir(Player jogador, Tela tela) {
        switch (tela) {
            case INICIO -> inicio(jogador);
            case VIP -> vip(jogador);
            default -> cosmeticos(jogador, tela);
        }
    }

    private void inicio(Player jogador) {
        Menu menu = new Menu(Tela.INICIO);
        menu.inventario = Bukkit.createInventory(menu, 27, "§8✦ Loja de créditos");
        menu.inventario.setItem(4, saldo(jogador));
        menu.inventario.setItem(10, icone(Material.DIAMOND, "§b§lVIP", "§7Ativar pra você ou gerar um código", "§7de presente (dá pra vender no jogo).", "§eClique"));
        menu.inventario.setItem(12, icone(Material.BLAZE_POWDER, "§6§lRastros", "§7Partículas nos seus pés enquanto anda.", "§eClique"));
        menu.inventario.setItem(14, icone(Material.NAME_TAG, "§d§lCor do nome", "§7Seu nome colorido no chat e na tab.", "§eClique"));
        menu.inventario.setItem(16, icone(Material.FIREWORK_ROCKET, "§c§lEfeitos de abate", "§7Um efeito ao derrotar outro jogador.", "§eClique"));
        menu.inventario.setItem(22, icone(Material.BOOK, "§fComo conseguir créditos", cores(plugin.getConfig().getString("onde-comprar", "")),
                "§7Os itens do jogo não são vendidos:", "§7só saem jogando."));
        jogador.openInventory(menu.inventario);
    }

    private void vip(Player jogador) {
        Menu menu = new Menu(Tela.VIP);
        menu.inventario = Bukkit.createInventory(menu, 27, "§8✦ Loja de créditos: VIP");
        menu.inventario.setItem(4, saldo(jogador));
        for (int i = 0; i < TIERS.length; i++) {
            long preco = plugin.getConfig().getLong("vip." + TIERS[i], 0);
            if (preco <= 0) continue;
            int slot = 10 + i * 2;
            menu.inventario.setItem(slot, icone(ICONE_TIER[i], "§e§lVIP " + capitalizar(TIERS[i]) + " §7(30 dias)",
                    "§7Preço: §b" + preco + " créditos" + reais(preco),
                    "", "§aClique esquerdo: §fativar pra você",
                    "§aClique direito: §fgerar um código de presente",
                    "§8(o código dá pra usar, dar ou vender: /vip vender)"));
            menu.slotParaId.put(slot, TIERS[i]);
        }
        menu.inventario.setItem(18, icone(Material.ARROW, "§fVoltar"));
        jogador.openInventory(menu.inventario);
    }

    private void cosmeticos(Player jogador, Tela tela) {
        Dados.Categoria categoria = categoria(tela);
        Menu menu = new Menu(tela);
        String titulo = switch (tela) {
            case RASTRO -> "Rastros";
            case COR -> "Cor do nome";
            default -> "Efeitos de abate";
        };
        menu.inventario = Bukkit.createInventory(menu, 36, "§8✦ Loja de créditos: " + titulo);
        menu.inventario.setItem(4, saldo(jogador));
        ConfigurationSection secao = plugin.getConfig().getConfigurationSection("cosmeticos." + Cosmeticos.chave(categoria));
        String usando = plugin.getDados().emUso(jogador.getUniqueId(), categoria);
        int slot = 9;
        if (secao != null) {
            for (String id : secao.getKeys(false)) {
                if (slot >= 27) break;
                ConfigurationSection s = secao.getConfigurationSection(id);
                if (s == null) continue;
                Material material = Material.matchMaterial(s.getString("icone", "PAPER"));
                String chave = chaveCosmetico(categoria, id);
                boolean tem = plugin.getDados().tem(jogador.getUniqueId(), chave);
                boolean emUso = id.equals(usando);
                List<String> lore = new ArrayList<>();
                if (emUso) lore.add("§a✔ Em uso §7(clique pra tirar)");
                else if (tem) lore.add("§aJá é seu §7(clique pra usar)");
                else {
                    long preco = s.getLong("preco");
                    lore.add("§7Preço: §b" + preco + " créditos" + reais(preco));
                    lore.add("§eClique pra comprar");
                }
                if (categoria == Dados.Categoria.ABATE) lore.add("§8Botão direito: ver o efeito");
                ItemStack item = icone(material == null ? Material.PAPER : material, cores(s.getString("nome", id)), lore.toArray(String[]::new));
                if (emUso) brilho(item);
                menu.inventario.setItem(slot, item);
                menu.slotParaId.put(slot, id);
                slot++;
            }
        }
        menu.inventario.setItem(27, icone(Material.ARROW, "§fVoltar"));
        jogador.openInventory(menu.inventario);
    }

    // ---------------------------------------------------------------------
    // Cliques
    // ---------------------------------------------------------------------

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        int slot = evento.getRawSlot();
        switch (menu.tela) {
            case INICIO -> {
                Tela destino = switch (slot) {
                    case 10 -> Tela.VIP;
                    case 12 -> Tela.RASTRO;
                    case 14 -> Tela.COR;
                    case 16 -> Tela.ABATE;
                    default -> null;
                };
                if (destino != null) abrir(jogador, destino);
            }
            case VIP -> {
                if (slot == 18) {
                    abrir(jogador, Tela.INICIO);
                    return;
                }
                String tier = menu.slotParaId.get(slot);
                if (tier == null) return;
                boolean codigo = evento.getClick() == ClickType.RIGHT || evento.getClick() == ClickType.SHIFT_RIGHT;
                comprarVip(jogador, tier, codigo);
            }
            default -> {
                if (slot == 27) {
                    abrir(jogador, Tela.INICIO);
                    return;
                }
                String id = menu.slotParaId.get(slot);
                if (id == null) return;
                Dados.Categoria categoria = categoria(menu.tela);
                if (categoria == Dados.Categoria.ABATE && evento.getClick().isRightClick()) {
                    // amostra do efeito, na frente do jogador
                    ConfigurationSection s = plugin.getCosmeticos().secao(categoria, id);
                    if (s != null) plugin.getCosmeticos().efeito(s.getString("efeito", ""),
                            jogador.getLocation().add(jogador.getLocation().getDirection().setY(0).normalize().multiply(3)));
                    return;
                }
                clicarCosmetico(jogador, categoria, id);
                abrir(jogador, menu.tela);
            }
        }
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    /** Primeiro clique avisa; o segundo (em até 10 s, no mesmo item) confirma. */
    private boolean confirmado(Player jogador, String chave, String oQue, long preco) {
        Pendente p = confirmando.get(jogador.getUniqueId());
        if (p != null && p.chave().equals(chave) && p.ate() > System.currentTimeMillis()) {
            confirmando.remove(jogador.getUniqueId());
            return true;
        }
        confirmando.put(jogador.getUniqueId(), new Pendente(chave, System.currentTimeMillis() + 10_000));
        jogador.sendMessage("§e" + oQue + " por §b" + preco + " créditos§e. §fClique de novo pra confirmar.");
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
        return false;
    }

    private void comprarVip(Player jogador, String tier, boolean codigo) {
        long preco = plugin.getConfig().getLong("vip." + tier, 0);
        if (preco <= 0) return;
        String nomeTier = "VIP " + capitalizar(tier);
        if (!codigo) {
            int atual = plugin.indiceTierAtivo(jogador.getUniqueId());
            int novo = indice(tier);
            if (atual > novo) {
                jogador.sendMessage("§cVocê já tem um VIP maior ativo. Ativar o " + nomeTier
                        + " trocaria pelo menor: gere um código (clique direito) se quiser dar ou vender.");
                return;
            }
            // o mesmo tier já ativo: o /vip add soma os 30 dias aos que faltam
        }
        if (plugin.getDados().creditos(jogador.getUniqueId()) < preco) {
            semSaldo(jogador, preco);
            return;
        }
        if (!confirmado(jogador, "vip:" + tier + ":" + codigo, (codigo ? "Gerar um código de " : "Ativar o ") + nomeTier, preco)) return;

        if (codigo) {
            String gerado = plugin.gerarCodigoVip(tier);
            if (gerado == null) {
                jogador.sendMessage("§cNão deu pra gerar o código agora (VipPlugin fora do ar). Nada foi cobrado.");
                return;
            }
            plugin.getDados().mudar(jogador.getUniqueId(), jogador.getName(), -preco, "código " + nomeTier + " " + gerado);
            jogador.sendMessage("§a✦ Código de " + nomeTier + " (30 dias, 1 uso): §f§l" + gerado);
            jogador.sendMessage("§7Use com §f/vip resgatar " + gerado + "§7, dê pra alguém ou venda com §f/vip vender§7.");
        } else {
            plugin.getDados().mudar(jogador.getUniqueId(), jogador.getName(), -preco, "ativou " + nomeTier);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vip add " + jogador.getName() + " " + tier);
            jogador.sendMessage("§a✦ " + nomeTier + ": +30 dias com " + preco + " créditos (somados aos que faltavam, se já era "
                    + nomeTier + ").");
        }
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        jogador.closeInventory();
    }

    private void clicarCosmetico(Player jogador, Dados.Categoria categoria, String id) {
        UUID uuid = jogador.getUniqueId();
        String chave = chaveCosmetico(categoria, id);
        ConfigurationSection s = plugin.getCosmeticos().secao(categoria, id);
        if (s == null) return;
        String nome = cores(s.getString("nome", id));
        if (plugin.getDados().tem(uuid, chave)) {
            boolean tirar = id.equals(plugin.getDados().emUso(uuid, categoria));
            plugin.getDados().usar(uuid, categoria, tirar ? null : id);
            jogador.sendMessage(tirar ? "§7Você tirou " + nome + "§7." : "§aAgora usando " + nome + "§a.");
        } else {
            long preco = s.getLong("preco");
            if (plugin.getDados().creditos(uuid) < preco) {
                semSaldo(jogador, preco);
                return;
            }
            if (!confirmado(jogador, "cos:" + chave, "Comprar " + nome + "§e", preco)) return;
            plugin.getDados().mudar(uuid, jogador.getName(), -preco, "cosmético " + chave);
            plugin.getDados().darCosmetico(uuid, chave);
            plugin.getDados().usar(uuid, categoria, id);
            jogador.sendMessage("§a✦ Você comprou " + nome + "§a, e ele já está em uso.");
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        if (categoria == Dados.Categoria.COR) plugin.refrescarTag(jogador);
    }

    private void semSaldo(Player jogador, long preco) {
        jogador.sendMessage("§cCréditos insuficientes: custa " + preco + ", você tem " + plugin.getDados().creditos(jogador.getUniqueId()) + ". "
                + cores(plugin.getConfig().getString("onde-comprar", "")));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
    }

    // ---------------------------------------------------------------------

    static String chaveCosmetico(Dados.Categoria c, String id) {
        return c.name().toLowerCase(Locale.ROOT) + ":" + id;
    }

    private static Dados.Categoria categoria(Tela t) {
        return switch (t) {
            case RASTRO -> Dados.Categoria.RASTRO;
            case COR -> Dados.Categoria.COR;
            default -> Dados.Categoria.ABATE;
        };
    }

    static int indice(String tier) {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equalsIgnoreCase(tier)) return i;
        return -1;
    }

    private ItemStack saldo(Player jogador) {
        long c = plugin.getDados().creditos(jogador.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("§7Você tem §b" + c + " créditos" + reais(c));
        if (plugin.getDados().apoiador(jogador.getUniqueId())) lore.add("§d✦ Apoiador §7(obrigado!)");
        return icone(Material.SUNFLOWER, "§b§lSeus créditos", lore.toArray(String[]::new));
    }

    private String reais(long creditos) {
        double porReal = plugin.getConfig().getDouble("creditos-por-real", 10);
        if (porReal <= 0) return "";
        return String.format(Locale.forLanguageTag("pt-BR"), " §8(R$ %,.2f)", creditos / porReal);
    }

    private static void brilho(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    static ItemStack icone(Material material, String nome, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        List<String> linhas = new ArrayList<>();
        for (String l : lore) if (l != null && !l.isEmpty()) linhas.add(l);
        meta.setLore(linhas);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static String capitalizar(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    static String cores(String texto) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', texto);
    }
}
