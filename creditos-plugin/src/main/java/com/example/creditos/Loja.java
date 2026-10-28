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
 * A loja do /creditos: VIP (ativar pra si ou gerar um código de presente), os cosméticos de cada
 * categoria e as skins de armadura, ferramenta e arma ({@link Skins}). Preço fixo e sem sorteio. Como os créditos são dinheiro de verdade, toda compra pede um
 * segundo clique pra confirmar (em até 10 s). Cosmético comprado fica pra sempre: na loja mesmo o
 * jogador escolhe qual usar, e clicar no que está em uso tira.
 */
public class Loja implements Listener {

    private static final String[] TIERS = {"bronze", "prata", "ouro", "diamante"};
    private static final Material[] ICONE_TIER = {Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND};

    enum Tela { INICIO, VIP, RASTRO, COR, ABATE, MONTARIA, ASA, CHAPEUS, SKINS, SKIN_PECAS }

    static class Menu implements InventoryHolder {
        final Tela tela;
        final Map<Integer, String> slotParaId = new HashMap<>();
        /** Na tela das peças: de qual coleção. */
        String colecao;
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
            case SKINS, SKIN_PECAS -> skins(jogador);
            case CHAPEUS -> chapeus(jogador);
            default -> cosmeticos(jogador, tela);
        }
    }

    private void inicio(Player jogador) {
        Menu menu = new Menu(Tela.INICIO);
        menu.inventario = Bukkit.createInventory(menu, 36, "§8✦ Loja de créditos");
        menu.inventario.setItem(4, saldo(jogador));
        menu.inventario.setItem(10, icone(Material.DIAMOND, "§b§lVIP", "§7Ativar pra você ou gerar um código", "§7de presente (dá pra vender no jogo).", "§eClique"));
        menu.inventario.setItem(12, icone(Material.BLAZE_POWDER, "§6§lRastros", "§7Partículas nos seus pés enquanto anda.", "§eClique"));
        menu.inventario.setItem(14, icone(Material.NAME_TAG, "§d§lCor do nome", "§7Seu nome colorido no chat e na tab.", "§eClique"));
        menu.inventario.setItem(16, icone(Material.FIREWORK_ROCKET, "§c§lEfeitos de abate", "§7Um efeito ao derrotar outro jogador.", "§eClique"));
        // asas só aparecem se o config tiver alguma
        ConfigurationSection asas = plugin.getConfig().getConfigurationSection("cosmeticos.asas");
        if (asas != null && !asas.getKeys(false).isEmpty()) {
            menu.inventario.setItem(20, icone(Material.ELYTRA, "§f§lAsas", "§7Asas nas suas costas.", "§eClique"));
        }
        menu.inventario.setItem(22, icone(Material.LEATHER_HELMET, "§6§lChapéus",
                "§7O visual de um chapéu no seu capacete:", "§7ele continua protegendo igual.", "§eClique"));
        menu.inventario.setItem(24, icone(Material.SADDLE, "§a§lSkins de montaria",
                "§7Troque o cavalo do /montaria por outro", "§7bicho. Mesma velocidade: só o visual.", "§eClique"));
        // as coleções de armas e armaduras só aparecem se o config tiver alguma
        ConfigurationSection colecoes = plugin.getConfig().getConfigurationSection("skins");
        if (colecoes != null && !colecoes.getKeys(false).isEmpty()) {
            menu.inventario.setItem(30, icone(Material.NETHERITE_CHESTPLATE, "§e§lSkins de armas e armaduras",
                    "§7O visual de uma coleção na sua armadura,", "§7espada e ferramentas. Só o visual:", "§7o item continua o mesmo.", "§eClique"));
        }
        menu.inventario.setItem(32, icone(Material.BOOK, "§fComo conseguir créditos", cores(plugin.getConfig().getString("onde-comprar", "")),
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
            case MONTARIA -> "Montarias";
            case ASA -> "Asas";
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
                if (categoria == Dados.Categoria.MONTARIA) lore.add("§8No /montaria (benefício do VIP). Mesma velocidade: só o visual.");
                if (s.contains("credito")) lore.add("§8Modelo: " + s.getString("credito"));
                ItemStack item = icone(material == null ? Material.PAPER : material, cores(s.getString("nome", id)), lore.toArray(String[]::new));
                // com "modelo", o ícone é o próprio item do resource pack (a asa, o bicho...)
                if (s.contains("modelo")) modelo(item, new org.bukkit.NamespacedKey("servidor", s.getString("modelo")));
                if (emUso) brilho(item);
                menu.inventario.setItem(slot, item);
                menu.slotParaId.put(slot, id);
                slot++;
            }
        }
        menu.inventario.setItem(27, icone(Material.ARROW, "§fVoltar"));
        jogador.openInventory(menu.inventario);
    }

    private void skins(Player jogador) {
        Menu menu = new Menu(Tela.SKINS);
        menu.inventario = Bukkit.createInventory(menu, 36, "§8✦ Loja de créditos: Skins");
        menu.inventario.setItem(4, saldo(jogador));
        ConfigurationSection secao = plugin.getConfig().getConfigurationSection("skins");
        int slot = 10;
        if (secao != null) {
            for (String id : secao.getKeys(false)) {
                if (slot > 16) break;
                ConfigurationSection s = secao.getConfigurationSection(id);
                if (s == null) continue;
                boolean tem = plugin.getDados().tem(jogador.getUniqueId(), chaveSkin(id));
                List<String> lore = new ArrayList<>();
                lore.add("§7Capacete, peitoral, calça, botas, espada,");
                lore.add("§7machado, picareta, pá e enxada.");
                lore.add("§8Só o visual: o item continua o mesmo.");
                lore.add("");
                if (tem) {
                    lore.add("§aJá é sua.");
                    lore.add("§eClique: §fpôr no item da mão §7(de novo tira)");
                    lore.add("§eShift + clique: §fpôr na armadura vestida");
                } else {
                    long preco = s.getLong("preco");
                    lore.add("§7Preço: §b" + preco + " créditos" + reais(preco));
                    lore.add("§eClique pra comprar §7(pra sempre, em quantos itens quiser)");
                }
                lore.add("§8Botão direito: ver as peças");
                menu.inventario.setItem(slot, modelo(icone(Material.IRON_SWORD, cores(s.getString("nome", id)), lore.toArray(String[]::new)),
                        Skins.modelo(id, "espada")));
                menu.slotParaId.put(slot, id);
                slot++;
            }
        }
        menu.inventario.setItem(27, icone(Material.ARROW, "§fVoltar"));
        menu.inventario.setItem(31, icone(Material.GRINDSTONE, "§fTirar a skin do item da mão", "§7Volta o visual original dele."));
        jogador.openInventory(menu.inventario);
    }

    private void chapeus(Player jogador) {
        Menu menu = new Menu(Tela.CHAPEUS);
        menu.inventario = Bukkit.createInventory(menu, 36, "§8✦ Loja de créditos: Chapéus");
        menu.inventario.setItem(4, saldo(jogador));
        ConfigurationSection secao = plugin.getConfig().getConfigurationSection("chapeus");
        int slot = 10;
        if (secao != null) {
            for (String id : secao.getKeys(false)) {
                if (slot > 16) break;
                ConfigurationSection s = secao.getConfigurationSection(id);
                if (s == null) continue;
                List<String> lore = new ArrayList<>();
                lore.add("§7Vai num capacete seu: ele continua");
                lore.add("§7protegendo igual, só o visual muda.");
                lore.add("");
                if (plugin.getDados().tem(jogador.getUniqueId(), chaveChapeu(id))) {
                    lore.add("§aJá é seu.");
                    lore.add("§eClique: §fpôr no capacete da mão §7(de novo tira)");
                    lore.add("§eShift + clique: §fpôr no capacete vestido");
                } else {
                    long preco = s.getLong("preco");
                    lore.add("§7Preço: §b" + preco + " créditos" + reais(preco));
                    lore.add("§eClique pra comprar §7(pra sempre, em quantos capacetes quiser)");
                }
                menu.inventario.setItem(slot, modelo(icone(Material.LEATHER_HELMET, cores(s.getString("nome", id)), lore.toArray(String[]::new)),
                        new org.bukkit.NamespacedKey("servidor", "chapeu_" + id)));
                menu.slotParaId.put(slot, id);
                slot++;
            }
        }
        menu.inventario.setItem(27, icone(Material.ARROW, "§fVoltar"));
        menu.inventario.setItem(31, icone(Material.GRINDSTONE, "§fTirar o chapéu do capacete da mão", "§7Volta o visual original dele."));
        jogador.openInventory(menu.inventario);
    }

    /** As 9 peças da coleção, pra ver antes de comprar. */
    private void pecas(Player jogador, String colecao) {
        ConfigurationSection s = plugin.getSkins().secao(colecao);
        if (s == null) return;
        Menu menu = new Menu(Tela.SKIN_PECAS);
        menu.colecao = colecao;
        String nome = cores(s.getString("nome", colecao));
        menu.inventario = Bukkit.createInventory(menu, 27, "§8✦ Skins: " + org.bukkit.ChatColor.stripColor(nome));
        String[] pecas = {"capacete", "peitoral", "calca", "botas", "espada", "machado", "picareta", "pa", "enxada"};
        Material[] base = {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.IRON_SWORD,
                Material.IRON_AXE, Material.IRON_PICKAXE, Material.IRON_SHOVEL, Material.IRON_HOE};
        for (int i = 0; i < pecas.length; i++) {
            menu.inventario.setItem(i, modelo(icone(base[i], nome + " §7(" + Skins.nomePeca(pecas[i]) + ")"), Skins.modelo(colecao, pecas[i])));
        }
        boolean tem = plugin.getDados().tem(jogador.getUniqueId(), chaveSkin(colecao));
        long preco = s.getLong("preco");
        menu.inventario.setItem(22, tem ? icone(Material.LIME_DYE, "§aJá é sua", "§7Clique: pôr no item da mão")
                : icone(Material.SUNFLOWER, "§eComprar por §b" + preco + " créditos" + reais(preco), "§7Pra sempre, em quantos itens quiser."));
        menu.inventario.setItem(18, icone(Material.ARROW, "§fVoltar"));
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
                    case 20 -> menu.inventario.getItem(20) != null ? Tela.ASA : null;
                    case 22 -> Tela.CHAPEUS;
                    case 24 -> Tela.MONTARIA;
                    case 30 -> menu.inventario.getItem(30) != null ? Tela.SKINS : null;
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
            case SKINS -> {
                if (slot == 27) {
                    abrir(jogador, Tela.INICIO);
                    return;
                }
                if (slot == 31) {
                    tirarSkin(jogador);
                    return;
                }
                String colecao = menu.slotParaId.get(slot);
                if (colecao == null) return;
                if (evento.getClick().isRightClick()) pecas(jogador, colecao);
                else clicarSkin(jogador, colecao, evento.getClick().isShiftClick());
            }
            case CHAPEUS -> {
                if (slot == 27) {
                    abrir(jogador, Tela.INICIO);
                    return;
                }
                if (slot == 31) {
                    tirarSkin(jogador);
                    return;
                }
                String chapeu = menu.slotParaId.get(slot);
                if (chapeu != null) clicarChapeu(jogador, chapeu, evento.getClick().isShiftClick());
            }
            case SKIN_PECAS -> {
                if (slot == 18) abrir(jogador, Tela.SKINS);
                else if (slot == 22 && menu.colecao != null) clicarSkin(jogador, menu.colecao, false);
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

    /** Ainda não tem: compra (com confirmação). Já tem: põe no item da mão (ou tira, se já está nele) ou na armadura vestida. */
    private void clicarSkin(Player jogador, String colecao, boolean armaduraVestida) {
        UUID uuid = jogador.getUniqueId();
        ConfigurationSection s = plugin.getSkins().secao(colecao);
        if (s == null) return;
        String nome = cores(s.getString("nome", colecao));
        if (!plugin.getDados().tem(uuid, chaveSkin(colecao))) {
            long preco = s.getLong("preco");
            if (plugin.getDados().creditos(uuid) < preco) {
                semSaldo(jogador, preco);
                return;
            }
            if (!confirmado(jogador, "skin:" + colecao, "Comprar " + nome + "§e", preco)) return;
            plugin.getDados().mudar(uuid, jogador.getName(), -preco, "skin " + colecao);
            plugin.getDados().darCosmetico(uuid, chaveSkin(colecao));
            jogador.sendMessage("§a✦ Você comprou " + nome + "§a! Segure um item e clique nela pra pôr a skin.");
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            abrir(jogador, Tela.SKINS);
            return;
        }
        Skins skins = plugin.getSkins();
        if (armaduraVestida) {
            ItemStack[] armadura = jogador.getInventory().getArmorContents();
            int postas = 0;
            for (ItemStack peca : armadura) {
                if (skins.motivoRecusa(peca) != null) continue;
                skins.aplicar(peca, colecao);
                postas++;
            }
            if (postas == 0) {
                jogador.sendMessage("§cVocê não está vestindo nenhuma armadura que aceite skin.");
                return;
            }
            // getArmorContents devolve cópias: põe as peças de volta
            jogador.getInventory().setArmorContents(armadura);
            jogador.sendMessage("§a✦ " + nome + "§a na sua armadura (" + postas + (postas == 1 ? " peça)." : " peças)."));
        } else {
            ItemStack mao = jogador.getInventory().getItemInMainHand();
            String recusa = skins.motivoRecusa(mao);
            if (recusa != null) {
                jogador.sendMessage("§c" + recusa);
                jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                return;
            }
            if (colecao.equals(skins.colecaoDe(mao))) {
                skins.tirar(mao);
                jogador.sendMessage("§7Skin tirada: o item voltou ao visual original.");
            } else {
                skins.aplicar(mao, colecao);
                jogador.sendMessage("§a✦ " + nome + "§a no seu item.");
            }
            jogador.getInventory().setItemInMainHand(mao);
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
    }

    /** Ainda não tem: compra (com confirmação). Já tem: põe no capacete da mão (ou tira, se já está nele) ou no vestido. */
    private void clicarChapeu(Player jogador, String chapeu, boolean vestido) {
        UUID uuid = jogador.getUniqueId();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("chapeus." + chapeu);
        if (s == null) return;
        String nome = cores(s.getString("nome", chapeu));
        if (!plugin.getDados().tem(uuid, chaveChapeu(chapeu))) {
            long preco = s.getLong("preco");
            if (plugin.getDados().creditos(uuid) < preco) {
                semSaldo(jogador, preco);
                return;
            }
            if (!confirmado(jogador, "chapeu:" + chapeu, "Comprar " + nome + "§e", preco)) return;
            plugin.getDados().mudar(uuid, jogador.getName(), -preco, "chapéu " + chapeu);
            plugin.getDados().darCosmetico(uuid, chaveChapeu(chapeu));
            jogador.sendMessage("§a✦ Você comprou " + nome + "§a! Segure um capacete e clique nele pra pôr.");
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            abrir(jogador, Tela.CHAPEUS);
            return;
        }
        Skins skins = plugin.getSkins();
        ItemStack capacete = vestido ? jogador.getInventory().getHelmet() : jogador.getInventory().getItemInMainHand();
        String recusa = skins.motivoRecusa(capacete);
        if (recusa == null && !"capacete".equals(Skins.peca(capacete.getType()))) recusa = "Chapéu só vai em capacete.";
        if (recusa != null) {
            jogador.sendMessage("§c" + (vestido && (capacete == null || capacete.getType().isAir()) ? "Você não está com capacete." : recusa));
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (("chapeu:" + chapeu).equals(skins.colecaoDe(capacete))) {
            skins.tirar(capacete);
            jogador.sendMessage("§7Chapéu tirado: o capacete voltou ao visual original.");
        } else {
            skins.tirar(capacete);
            skins.aplicarChapeu(capacete, chapeu);
            jogador.sendMessage("§a✦ " + nome + "§a no seu capacete.");
        }
        if (vestido) jogador.getInventory().setHelmet(capacete);
        else jogador.getInventory().setItemInMainHand(capacete);
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
    }

    private void tirarSkin(Player jogador) {
        ItemStack mao = jogador.getInventory().getItemInMainHand();
        if (plugin.getSkins().colecaoDe(mao) == null) {
            jogador.sendMessage("§cO item da sua mão não tem skin.");
            return;
        }
        plugin.getSkins().tirar(mao);
        jogador.getInventory().setItemInMainHand(mao);
        jogador.sendMessage("§7Skin tirada: o item voltou ao visual original.");
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
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

    static String chaveSkin(String colecao) {
        return "skin:" + colecao;
    }

    static String chaveChapeu(String chapeu) {
        return "chapeu:" + chapeu;
    }

    private static Dados.Categoria categoria(Tela t) {
        return switch (t) {
            case RASTRO -> Dados.Categoria.RASTRO;
            case COR -> Dados.Categoria.COR;
            case MONTARIA -> Dados.Categoria.MONTARIA;
            case ASA -> Dados.Categoria.ASA;
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

    /** O ícone com o modelo do resource pack (a peça da skin). */
    private static ItemStack modelo(ItemStack item, org.bukkit.NamespacedKey modelo) {
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(modelo);
        item.setItemMeta(meta);
        return item;
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
