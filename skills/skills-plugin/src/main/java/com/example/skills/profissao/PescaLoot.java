package com.example.skills.profissao;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O loot próprio da pesca (2026-11-10), em {@code pescador.loot} do config. A cada pesca, uma chance
 * ({@code chance-base} + {@code chance-por-nivel} × nível do Pescador, com o prestígio) de a pesca vir
 * especial: a raridade sai pelos pesos da faixa de nível ({@code pesos-raridade}) e o item, pelo peso,
 * da lista daquela raridade. O item especial troca o que o anzol puxou.
 * <ul>
 *   <li><b>Peixes raros</b> ({@code peixes}): marcados, valem coins com {@code /peixes vender}. A
 *       loja não compra (é item com marca de plugin).</li>
 *   <li><b>Baú do Mar</b> ({@code bau}): clique direito abre, dá coins e itens sorteados.</li>
 *   <li><b>Vara do Pescador</b> ({@code vara}): encantada e com mais chance de pesca especial.</li>
 *   <li>Itens de progressão e equipamento: fragmento, combustível, spawner, máquina, livro, Adaga.</li>
 * </ul>
 * <b>Pesca parada</b>: quem pesca do mesmo bloco e sem mexer a câmera há mais de {@code afk.minutos}
 * tem a chance vezes {@code afk.fator}. Mexer a câmera ou andar zera a conta.
 */
public class PescaLoot implements Listener {

    public static final String[] RARIDADES = {"comum", "raro", "epico", "lendario"};
    private static final Map<String, String> NOMES_RARIDADE = Map.of(
            "comum", "§fComum", "raro", "§9Raro", "epico", "§5Épico", "lendario", "§6§lLendário");

    private final SkillsPlugin plugin;
    private final Integracoes integracoes;
    private final NamespacedKey chavePeixe;
    private final NamespacedKey chaveBau;
    private final NamespacedKey chaveVara;
    /** Onde e pra onde cada jogador olhava na última pesca, e desde quando está assim. */
    private final Map<UUID, Parado> parados = new HashMap<>();

    private record Parado(String bloco, float yaw, float pitch, long desde) {}

    public PescaLoot(SkillsPlugin plugin, Integracoes integracoes) {
        this.plugin = plugin;
        this.integracoes = integracoes;
        this.chavePeixe = new NamespacedKey(plugin, "peixe");
        this.chaveBau = new NamespacedKey(plugin, "bau_mar");
        this.chaveVara = new NamespacedKey(plugin, "vara_pescador");
    }

    /**
     * A seção {@code pescador.loot}: a do config do servidor ou, se ele não tem, a do jar. Pelo get(): o
     * getConfigurationSection() criaria uma seção vazia no config, e as listas de itens sumiriam.
     */
    private ConfigurationSection cfg() {
        return plugin.getConfig().get("pescador.loot") instanceof ConfigurationSection s ? s : plugin.getConfig().createSection("pescador.loot");
    }

    private static String cor(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    // ---------------------------------------------------------------- a pesca

    /** Chamado a cada pesca: sorteia se veio especial e, se veio, troca o item do anzol (no tick seguinte). */
    public void aoPescar(Player jogador, Item fisgado) {
        if (!cfg().getBoolean("ativado", true)) return;
        double chance = chance(jogador);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            trocarLixo(jogador, fisgado);
            return;
        }
        String raridade = sortearRaridade(plugin.getSkillDataManager().get(jogador.getUniqueId()).getNivel(Skill.PESCADOR));
        ItemStack premio = sortear(cfg().getConfigurationSection("raridades." + raridade));
        if (premio == null) return;
        // no tick seguinte: as missões de pescar (MissoesPlugin) contam o peixe que o jogo deu
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (fisgado != null && fisgado.isValid()) fisgado.setItemStack(premio);
            else jogador.getInventory().addItem(premio).values().forEach(i -> jogador.getWorld().dropItem(jogador.getLocation(), i));
        });
        String nome = nomeDoItem(premio);
        jogador.sendMessage("§b🎣 Pesca " + NOMES_RARIDADE.getOrDefault(raridade, raridade) + "§b! §7Você fisgou "
                + nome + (premio.getAmount() > 1 ? " §7x" + premio.getAmount() : "") + "§7.");
        jogador.playSound(jogador.getLocation(), raridade.equals("lendario") ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP,
                0.8f, raridade.equals("comum") ? 1.6f : 1.2f);
        if (raridade.equals("lendario")) {
            plugin.getServer().broadcastMessage("§b🎣 §e" + jogador.getName() + " §7fisgou uma pesca §6§lLendária§7: " + nome + "§7!");
        }
    }

    /** O lixo do vanilla (o que não é peixe nem tesouro). A vara e a bota do lixo vêm sem encantamento. */
    private static final Set<Material> LIXO = EnumSet.of(Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS,
            Material.ROTTEN_FLESH, Material.STICK, Material.STRING, Material.POTION, Material.BONE, Material.INK_SAC,
            Material.TRIPWIRE_HOOK, Material.BAMBOO, Material.FISHING_ROD);

    /**
     * Pesca normal que veio lixo: com {@code lixo-por-nivel} × nível de Pescador de chance (nível 25 com
     * 0,02: metade), vira peixe, na proporção do vanilla (bacalhau 60, salmão 25, baiacu 13, palhaço 2).
     */
    private void trocarLixo(Player jogador, Item fisgado) {
        if (fisgado == null) return;
        ItemStack item = fisgado.getItemStack();
        if (!LIXO.contains(item.getType()) || !item.getEnchantments().isEmpty()) return;
        int nivel = plugin.getSkillDataManager().get(jogador.getUniqueId()).getNivel(Skill.PESCADOR);
        if (ThreadLocalRandom.current().nextDouble() >= nivel * cfg().getDouble("lixo-por-nivel", 0.02)) return;
        int r = ThreadLocalRandom.current().nextInt(100);
        Material peixe = r < 60 ? Material.COD : r < 85 ? Material.SALMON : r < 98 ? Material.PUFFERFISH : Material.TROPICAL_FISH;
        // no tick seguinte, como o loot especial (as missões contam o que o jogo deu)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (fisgado.isValid()) fisgado.setItemStack(new ItemStack(peixe));
        });
    }

    /** A chance de a pesca vir especial, com a Vara do Pescador e o desconto da pesca parada. */
    private double chance(Player jogador) {
        double chance = chanceAtiva(jogador);
        if (parado(jogador)) chance *= cfg().getDouble("afk.fator", 0.25);
        return chance;
    }

    /** A chance de quem pesca ativo (sem o desconto da pesca parada), com a Vara do Pescador na mão. */
    public double chanceAtiva(Player jogador) {
        double chance = cfg().getDouble("chance-base", 0.03)
                + plugin.getBonusCalculator().porNivel(jogador.getUniqueId(), Skill.PESCADOR, "pescador.loot.chance-por-nivel");
        if (ehVara(jogador.getInventory().getItemInMainHand()) || ehVara(jogador.getInventory().getItemInOffHand())) {
            chance *= 1 + cfg().getDouble("vara.bonus-chance", 0.25);
        }
        return chance;
    }

    /** Atualiza a conta da pesca parada e diz se o jogador está parado há mais de {@code afk.minutos}. */
    private boolean parado(Player jogador) {
        Location l = jogador.getLocation();
        String bloco = l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
        long agora = System.currentTimeMillis();
        Parado antes = parados.get(jogador.getUniqueId());
        if (antes == null || !antes.bloco().equals(bloco)
                || Math.abs(antes.yaw() - l.getYaw()) > 0.5f || Math.abs(antes.pitch() - l.getPitch()) > 0.5f) {
            parados.put(jogador.getUniqueId(), new Parado(bloco, l.getYaw(), l.getPitch(), agora));
            return false;
        }
        return agora - antes.desde() > cfg().getDouble("afk.minutos", 10) * 60_000;
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        parados.remove(evento.getPlayer().getUniqueId());
    }

    /** A raridade pela faixa de nível ({@code pesos-raridade}: vale a do maior "nivel" alcançado). */
    private String sortearRaridade(int nivel) {
        Map<?, ?> faixa = null;
        int melhor = Integer.MIN_VALUE;
        for (Map<?, ?> m : cfg().getMapList("pesos-raridade")) {
            int desde = m.get("nivel") instanceof Number n ? n.intValue() : 0;
            if (desde <= nivel && desde > melhor) {
                melhor = desde;
                faixa = m;
            }
        }
        if (faixa == null) return RARIDADES[0];
        double total = 0;
        for (String r : RARIDADES) total += faixa.get(r) instanceof Number n ? n.doubleValue() : 0;
        double sorteio = ThreadLocalRandom.current().nextDouble() * total;
        for (String r : RARIDADES) {
            sorteio -= faixa.get(r) instanceof Number n ? n.doubleValue() : 0;
            if (sorteio < 0) return r;
        }
        return RARIDADES[0];
    }

    // ---------------------------------------------------------------- as listas de itens

    /** Um item da lista {@code itens} da seção, pelo peso (null sem lista). */
    private ItemStack sortear(ConfigurationSection secao) {
        List<ItemStack> um = sortear(secao, 1);
        return um.isEmpty() ? null : um.get(0);
    }

    /** {@code rolagens} itens da lista {@code itens}, pelo peso, sem repetir. */
    private List<ItemStack> sortear(ConfigurationSection secao, int rolagens) {
        List<ConfigurationSection> entradas = new ArrayList<>();
        double total = 0;
        ConfigurationSection itens = secao == null ? null : secao.getConfigurationSection("itens");
        if (itens != null) {
            for (String chave : itens.getKeys(false)) {
                ConfigurationSection e = itens.getConfigurationSection(chave);
                if (e == null || e.getDouble("peso", 0) <= 0) continue;
                entradas.add(e);
                total += e.getDouble("peso");
            }
        }
        List<ItemStack> sorteados = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < rolagens && !entradas.isEmpty() && total > 0; i++) {
            double sorteio = r.nextDouble(total);
            for (int j = 0; j < entradas.size(); j++) {
                ConfigurationSection e = entradas.get(j);
                sorteio -= e.getDouble("peso");
                if (sorteio < 0 || j == entradas.size() - 1) {
                    List<Integer> qtd = e.getIntegerList("qtd");
                    int quantidade = qtd.size() >= 2 ? r.nextInt(qtd.get(0), Math.max(qtd.get(0), qtd.get(1)) + 1) : 1;
                    ItemStack item = criar(e.getString("item", ""), quantidade);
                    if (item != null) sorteados.add(item);
                    entradas.remove(j);
                    total -= e.getDouble("peso");
                    break;
                }
            }
        }
        return sorteados;
    }

    /**
     * Um item pelo nome do config: {@code peixe:<id>}, {@code bau:<raridade>}, {@code vara}, {@code adaga},
     * {@code fragmento}, {@code combustivel}, {@code spawner:<tipo>}, {@code maquina:<tipo>},
     * {@code livro:<encantamento>:<nível>} ou um MATERIAL. null se não der (plugin desligado, nome errado).
     */
    public ItemStack criar(String nome, int quantidade) {
        String n = nome.toLowerCase(Locale.ROOT);
        int qtd = Math.max(1, quantidade);
        ItemStack item;
        if (n.startsWith("peixe:")) item = peixe(n.substring(6));
        else if (n.startsWith("bau:")) item = bau(n.substring(4));
        else if (n.equals("vara")) item = vara();
        else if (n.equals("adaga")) item = adaga();
        else if (n.equals("fragmento")) item = integracoes.fragmento();
        else if (n.equals("combustivel")) return integracoes.combustivel(qtd);
        else if (n.startsWith("spawner:")) return integracoes.spawner(n.substring(8), qtd);
        else if (n.startsWith("maquina:")) return integracoes.maquina(n.substring(8));
        else if (n.startsWith("livro:")) item = livro(n);
        else {
            Material material = Material.matchMaterial(nome);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("pescador.loot: item desconhecido \"" + nome + "\"");
                return null;
            }
            item = new ItemStack(material);
        }
        if (item != null) item.setAmount(Math.min(qtd, item.getMaxStackSize()));
        return item;
    }

    private ItemStack livro(String nome) {
        String[] partes = nome.split(":");
        Enchantment ench = partes.length >= 2 ? Registry.ENCHANTMENT.get(NamespacedKey.minecraft(partes[1])) : null;
        if (ench == null) {
            plugin.getLogger().warning("pescador.loot: encantamento desconhecido em \"" + nome + "\"");
            return null;
        }
        ItemStack livro = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) livro.getItemMeta();
        meta.addStoredEnchant(ench, partes.length >= 3 ? Integer.parseInt(partes[2]) : 1, true);
        livro.setItemMeta(meta);
        return livro;
    }

    // ---------------------------------------------------------------- peixes raros

    /** O peixe raro {@code peixes.<id>}, ou null se não existe. */
    public ItemStack peixe(String id) {
        ConfigurationSection p = cfg().getConfigurationSection("peixes." + id);
        if (p == null) {
            plugin.getLogger().warning("pescador.loot: peixe desconhecido \"" + id + "\"");
            return null;
        }
        Material material = Material.matchMaterial(p.getString("material", "COD"));
        ItemStack item = new ItemStack(material != null ? material : Material.COD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(cor(p.getString("nome", id)));
        List<String> lore = new ArrayList<>();
        for (String linha : p.getStringList("descricao")) lore.add(cor(linha));
        lore.add("");
        lore.add("§7Vale §a" + dinheiro(p.getDouble("preco")) + "§7: venda com §f/peixes vender§7.");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(chavePeixe, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /** O id do peixe raro, ou null (não é peixe raro). */
    public String idPeixe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(chavePeixe, PersistentDataType.STRING);
    }

    /** O preço de um peixe raro (o do config agora, não o da descrição), ou 0. */
    public double precoPeixe(String id) {
        return id == null ? 0 : cfg().getDouble("peixes." + id + ".preco", 0);
    }

    public double fatorParado() {
        return cfg().getDouble("afk.fator", 0.25);
    }

    public double minutosParado() {
        return cfg().getDouble("afk.minutos", 10);
    }

    public ConfigurationSection secaoPeixes() {
        return cfg().getConfigurationSection("peixes");
    }

    /** Vende os peixes raros do inventário. Devolve o total pago (0 sem nenhum ou sem economia). */
    public double venderPeixes(Player jogador) {
        Economy econ = plugin.getEconomy();
        if (econ == null) return 0;
        double total = 0;
        int quantos = 0;
        ItemStack[] conteudo = jogador.getInventory().getStorageContents();
        for (int i = 0; i < conteudo.length; i++) {
            double preco = precoPeixe(idPeixe(conteudo[i]));
            if (preco <= 0) continue;
            total += preco * conteudo[i].getAmount();
            quantos += conteudo[i].getAmount();
            conteudo[i] = null;
        }
        if (total <= 0) return 0;
        jogador.getInventory().setStorageContents(conteudo);
        econ.depositPlayer(jogador, total);
        integracoes.extrato(jogador, total, "Venda de " + quantos + " peixe(s) raro(s)");
        return total;
    }

    // ---------------------------------------------------------------- Baú do Mar

    public ItemStack bau(String raridade) {
        ConfigurationSection b = cfg().getConfigurationSection("bau." + raridade);
        if (b == null) {
            plugin.getLogger().warning("pescador.loot: baú desconhecido \"" + raridade + "\"");
            return null;
        }
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(cor(b.getString("nome", "&bBaú do Mar")));
        List<Double> coins = b.getDoubleList("coins");
        List<String> lore = new ArrayList<>();
        lore.add("§7Pescado do fundo do mar.");
        if (coins.size() >= 2) lore.add("§7Coins: §a" + dinheiro(coins.get(0)) + " a " + dinheiro(coins.get(1)));
        lore.add("§7Itens: §f" + b.getInt("rolagens", 1));
        lore.add("");
        lore.add("§eClique direito pra abrir.");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(chaveBau, PersistentDataType.STRING, raridade);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoAbrirBau(PlayerInteractEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        if (evento.getAction() != Action.RIGHT_CLICK_AIR && evento.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack mao = evento.getItem();
        if (mao == null || !mao.hasItemMeta()) return;
        String raridade = mao.getItemMeta().getPersistentDataContainer().get(chaveBau, PersistentDataType.STRING);
        if (raridade == null) return;
        // nunca coloca o baú no chão
        evento.setCancelled(true);
        Player jogador = evento.getPlayer();
        ConfigurationSection b = cfg().getConfigurationSection("bau." + raridade);
        if (b == null) return;
        mao.setAmount(mao.getAmount() - 1);

        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Double> faixa = b.getDoubleList("coins");
        double coins = faixa.size() >= 2 ? r.nextLong(faixa.get(0).longValue(), (long) Math.max(faixa.get(0), faixa.get(1)) + 1) : 0;
        List<ItemStack> itens = sortear(b, b.getInt("rolagens", 1));

        jogador.sendMessage("§b🎣 Você abriu o " + cor(b.getString("nome", "&bBaú do Mar")) + "§b:");
        Economy econ = plugin.getEconomy();
        if (coins > 0 && econ != null) {
            econ.depositPlayer(jogador, coins);
            integracoes.extrato(jogador, coins, "Baú do Mar");
            jogador.sendMessage("§8- §a" + dinheiro(coins));
        }
        for (ItemStack item : itens) {
            jogador.sendMessage("§8- §f" + nomeDoItem(item) + (item.getAmount() > 1 ? " §7x" + item.getAmount() : ""));
            jogador.getInventory().addItem(item).values().forEach(i -> jogador.getWorld().dropItem(jogador.getLocation(), i));
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    // ---------------------------------------------------------------- Vara do Pescador e Adaga

    public ItemStack vara() {
        ConfigurationSection v = cfg().getConfigurationSection("vara");
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(cor(v != null ? v.getString("nome", "&bVara do Pescador") : "&bVara do Pescador"));
        ConfigurationSection encantos = v == null ? null : v.getConfigurationSection("encantamentos");
        if (encantos != null) {
            for (String chave : encantos.getKeys(false)) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(chave.toLowerCase(Locale.ROOT)));
                if (ench != null) meta.addEnchant(ench, encantos.getInt(chave), true);
            }
        }
        int bonus = (int) Math.round(cfg().getDouble("vara.bonus-chance", 0.25) * 100);
        meta.setLore(List.of("§7Feita pra pescar o que ninguém pesca.", "§b+" + bonus + "% §7de chance de pesca especial."));
        meta.getPersistentDataContainer().set(chaveVara, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean ehVara(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(chaveVara, PersistentDataType.BYTE);
    }

    /** A Adaga de netherite encantada, a mesma do chão do boss e do baú lendário dos tesouros. */
    private ItemStack adaga() {
        ItemStack espada = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = espada.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        espada.setItemMeta(meta);
        return plugin.getAdagaUtil().transformarEmAdaga(espada);
    }

    // ---------------------------------------------------------------- textos

    public static String nomeDoItem(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) return item.getItemMeta().getDisplayName();
        if (item.getItemMeta() instanceof EnchantmentStorageMeta livro && livro.hasStoredEnchants()) {
            StringBuilder sb = new StringBuilder("§dLivro");
            livro.getStoredEnchants().forEach((e, nv) -> sb.append(" ").append(e.getKey().getKey().replace('_', ' ')).append(" ").append(nv));
            return sb.toString();
        }
        String nome = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return "§f" + Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
    }

    public static String dinheiro(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.0f", valor);
    }
}
