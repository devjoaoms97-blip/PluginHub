package com.example.skills.profissao;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.Skill;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * As quatro skills de profissão (ROADMAP, 2026-11-02):
 * <ul>
 *   <li><b>Minerador</b>: XP por bloco quebrado à mão numa mina e, com picareta, chance de quebrar o
 *       3x3 em volta. Os blocos extras passam pelo {@code RankupPlugin#quebrarExtra}, que só vende
 *       (sem evento de quebra: não dão fragmento, mapa, missão nem XP).</li>
 *   <li><b>Agricultor</b>: XP por planta madura colhida com a mão, colheita dupla e bônus de venda no
 *       Fazendeiro (a loja pergunta {@code SkillsPlugin#bonusVenda}). Farm automática não dispara a
 *       quebra por jogador, então não conta.</li>
 *   <li><b>Pescador</b>: XP por pesca, mapa do tesouro e Fragmento Arcano raros. Pesca parada vale.</li>
 *   <li><b>Lenhador</b>: XP por tronco, tora a mais e, nos mundos de recursos, chance de derrubar a
 *       árvore natural inteira.</li>
 * </ul>
 */
public class ProfissoesListener implements Listener {

    private static final Set<Material> PLANTAS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA);
    private static final Set<Material> SEMENTES = Set.of(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS);
    private static final int MAX_COLOCADOS = 100_000;

    private final SkillsPlugin plugin;
    private final Integracoes integracoes;
    /** Troncos colocados por jogador (os mais recentes): não dão XP nem tora a mais. */
    private final Map<String, Boolean> troncosColocados = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> mais) {
            return size() > MAX_COLOCADOS;
        }
    };
    /** Blocos quebrados neste tick que podem ganhar drop a mais (tora ou colheita), até o BlockDropItemEvent. */
    private final Map<String, Skill> dropExtra = new HashMap<>();
    /** Quem já leu a explicação do 3x3 nesta sessão do servidor (depois, só o subtítulo). */
    private final Set<UUID> explicados = new HashSet<>();
    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    public ProfissoesListener(SkillsPlugin plugin, Integracoes integracoes) {
        this.plugin = plugin;
        this.integracoes = integracoes;
    }

    private static String chave(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private double cfg(String caminho) {
        return plugin.getConfig().getDouble(caminho);
    }

    private boolean sorteio(Player jogador, Skill skill, String caminho) {
        return ThreadLocalRandom.current().nextDouble() < plugin.getBonusCalculator().porNivel(jogador.getUniqueId(), skill, caminho);
    }

    private void marcarDropExtra(Block bloco, Skill skill) {
        String k = chave(bloco);
        dropExtra.put(k, skill);
        plugin.getServer().getScheduler().runTask(plugin, () -> dropExtra.remove(k));
    }

    // ---------------------------------------------------------------- quebra

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (Tag.LOGS.isTagged(evento.getBlock().getType())) troncosColocados.put(chave(evento.getBlock()), Boolean.TRUE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Player jogador = evento.getPlayer();
        if (jogador.getGameMode() != GameMode.SURVIVAL) return;
        Block bloco = evento.getBlock();
        Material tipo = bloco.getType();
        String mina = integracoes.minaEm(bloco.getLocation());

        if (mina != null) {
            plugin.getXpManager().adicionarXp(jogador, Skill.MINERADOR, cfg("minerador.xp-por-bloco"));
            plugin.getXpManager().marcarEmUso(jogador.getUniqueId(), Skill.MINERADOR);
            if (ehPicareta(jogador.getInventory().getItemInMainHand()) && sorteio(jogador, Skill.MINERADOR, "minerador.chance-3x3-por-nivel")) {
                quebrar3x3(jogador, bloco);
            }
        }

        if (Tag.LOGS.isTagged(tipo)) {
            if (troncosColocados.remove(chave(bloco)) != null) return;
            plugin.getXpManager().adicionarXp(jogador, Skill.LENHADOR, cfg("lenhador.xp-por-tronco"));
            plugin.getXpManager().marcarEmUso(jogador.getUniqueId(), Skill.LENHADOR);
            if (sorteio(jogador, Skill.LENHADOR, "lenhador.chance-tora-extra-por-nivel")) marcarDropExtra(bloco, Skill.LENHADOR);
            if (mina == null && ehMachado(jogador.getInventory().getItemInMainHand())
                    && plugin.getConfig().getStringList("lenhador.derrubar-mundos").contains(bloco.getWorld().getName())
                    && sorteio(jogador, Skill.LENHADOR, "lenhador.chance-derrubar-por-nivel")) {
                derrubarArvore(jogador, bloco);
            }
            return;
        }

        if (PLANTAS.contains(tipo) && bloco.getBlockData() instanceof Ageable planta && planta.getAge() >= planta.getMaximumAge()) {
            plugin.getXpManager().adicionarXp(jogador, Skill.AGRICULTOR, cfg("agricultor.xp-por-colheita"));
            plugin.getXpManager().marcarEmUso(jogador.getUniqueId(), Skill.AGRICULTOR);
            if (sorteio(jogador, Skill.AGRICULTOR, "agricultor.chance-colheita-dupla-por-nivel")) marcarDropExtra(bloco, Skill.AGRICULTOR);
        }
    }

    /**
     * Antes da venda automática da mina (HIGH): a tora a mais e a colheita dupla aumentam a pilha que
     * já caiu, então na mina ela é vendida junto e fora dela cai no chão como o drop normal.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void aoDropar(BlockDropItemEvent evento) {
        Skill skill = dropExtra.remove(chave(evento.getBlock()));
        if (skill == null) return;
        boolean dobrou = false;
        for (Item item : evento.getItems()) {
            ItemStack stack = item.getItemStack();
            if (skill == Skill.LENHADOR ? !Tag.LOGS.isTagged(stack.getType()) : SEMENTES.contains(stack.getType())) continue;
            // tora a mais: +1; colheita dupla: o dobro do que se colhe
            stack.setAmount(stack.getAmount() + (skill == Skill.LENHADOR ? 1 : stack.getAmount()));
            item.setItemStack(stack);
            dobrou = true;
            if (skill == Skill.LENHADOR) break;
        }
        if (!dobrou) return;
        Location centro = evento.getBlock().getLocation().add(0.5, 0.5, 0.5);
        evento.getBlock().getWorld().spawnParticle(skill == Skill.LENHADOR ? Particle.CHERRY_LEAVES : Particle.HAPPY_VILLAGER,
                centro, 8, 0.3, 0.3, 0.3, 0);
        evento.getPlayer().playSound(centro, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.6f);
    }

    // ---------------------------------------------------------------- Minerador

    private static boolean ehPicareta(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    private static boolean ehMachado(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
    }

    /** Os 8 blocos em volta, no plano de frente pra quem minera (olhando pra cima/baixo: o plano do chão). */
    private void quebrar3x3(Player jogador, Block centro) {
        float pitch = jogador.getLocation().getPitch();
        Vector olhar = jogador.getLocation().getDirection();
        BlockFace a, b;
        if (Math.abs(pitch) > 45) {
            a = BlockFace.EAST;
            b = BlockFace.SOUTH;
        } else if (Math.abs(olhar.getX()) > Math.abs(olhar.getZ())) {
            a = BlockFace.UP;
            b = BlockFace.SOUTH;
        } else {
            a = BlockFace.UP;
            b = BlockFace.EAST;
        }
        int quebrados = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                Block vizinho = centro.getRelative(a, i).getRelative(b, j);
                if (integracoes.quebrarExtra(jogador, vizinho)) quebrados++;
            }
        }
        if (quebrados == 0) return;
        Location l = centro.getLocation().add(0.5, 0.5, 0.5);
        centro.getWorld().spawnParticle(Particle.EXPLOSION, l, 1);
        centro.getWorld().spawnParticle(Particle.WAX_OFF, l, 20, 0.9, 0.9, 0.9, 0);
        jogador.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.6f);
        // a barra de ação é da venda automática ("+R$" a cada bloco): o aviso vai no chat
        jogador.sendMessage("§6⛏ Golpe do Minerador! §e+" + quebrados + " blocos");
        if (explicados.add(jogador.getUniqueId())) {
            jogador.sendMessage("§6⛏ Minerador: §7sua picareta quebrou o 3x3 em volta do bloco. A chance é de §f"
                    + String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f%%",
                    plugin.getBonusCalculator().porNivel(jogador.getUniqueId(), Skill.MINERADOR, "minerador.chance-3x3-por-nivel") * 100)
                    + " §7e cresce com o nível da skill. Os blocos extras são vendidos na hora, como os outros. §8(/skills)");
        }
    }

    // ---------------------------------------------------------------- Lenhador

    /**
     * A árvore natural: os troncos ligados (também na diagonal) ao que foi cortado, até o máximo, e pelo
     * menos uma folha que não foi colocada por jogador encostada neles. Tronco colocado não entra. Os
     * troncos caem onde estava o primeiro, e cada um gasta 1 de durabilidade do machado.
     */
    private void derrubarArvore(Player jogador, Block origem) {
        int maximo = plugin.getConfig().getInt("lenhador.derrubar-max-troncos");
        Set<String> vistos = new HashSet<>();
        vistos.add(chave(origem));
        ArrayDeque<Block> fila = new ArrayDeque<>();
        fila.add(origem);
        List<Block> troncos = new ArrayList<>();
        boolean folhaNatural = false;
        while (!fila.isEmpty() && troncos.size() < maximo) {
            Block atual = fila.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block v = atual.getRelative(dx, dy, dz);
                        if (!vistos.add(chave(v))) continue;
                        BlockData dados = v.getBlockData();
                        if (dados instanceof Leaves folha && !folha.isPersistent()) {
                            folhaNatural = true;
                        } else if (Tag.LOGS.isTagged(v.getType()) && !troncosColocados.containsKey(chave(v))
                                && troncos.size() < maximo) {
                            troncos.add(v);
                            fila.add(v);
                        }
                    }
                }
            }
        }
        if (!folhaNatural || troncos.isEmpty()) return;

        ItemStack machado = jogador.getInventory().getItemInMainHand();
        Location queda = origem.getLocation().add(0.5, 0.5, 0.5);
        for (Block tronco : troncos) {
            for (ItemStack drop : tronco.getDrops(machado, jogador)) origem.getWorld().dropItemNaturally(queda, drop);
            tronco.getWorld().spawnParticle(Particle.BLOCK, tronco.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, tronco.getBlockData());
            tronco.setType(Material.AIR);
            if (machado.getType().isAir()) break;
            machado.damage(1, jogador); // some sozinho se quebrar
            machado = jogador.getInventory().getItemInMainHand();
            if (!ehMachado(machado)) break;
        }
        jogador.playSound(queda, Sound.BLOCK_WOOD_BREAK, 1f, 0.6f);
        jogador.sendActionBar(LEGADO.deserialize("§6🪓 Árvore derrubada pelo Lenhador! §7(" + troncos.size() + " troncos)"));
    }

    // ---------------------------------------------------------------- Pescador

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoPescar(PlayerFishEvent evento) {
        if (evento.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player jogador = evento.getPlayer();
        if (jogador.getGameMode() != GameMode.SURVIVAL) return;
        plugin.getXpManager().adicionarXp(jogador, Skill.PESCADOR, cfg("pescador.xp-por-pesca"));
        plugin.getXpManager().marcarEmUso(jogador.getUniqueId(), Skill.PESCADOR);

        if (sorteio(jogador, Skill.PESCADOR, "pescador.chance-mapa-por-nivel")) {
            String raridade = sortearRaridade(plugin.getSkillDataManager().get(jogador.getUniqueId()).getNivel(Skill.PESCADOR));
            ItemStack mapa = integracoes.mapa(raridade);
            if (mapa != null) {
                entregar(jogador, mapa);
                jogador.sendMessage("§6✦ §eVocê pescou um §fMapa do Tesouro " + integracoes.nomeRaridade(raridade)
                        + "§e! §7Veja as coordenadas e use §f/warp tesouros§7.");
                jogador.playSound(jogador.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
                if ("lendario".equals(raridade)) {
                    plugin.getServer().broadcastMessage("§6✦ §e" + jogador.getName() + " §7pescou um §6Mapa do Tesouro Lendário§7!");
                }
            }
        }
        if (sorteio(jogador, Skill.PESCADOR, "pescador.chance-fragmento-por-nivel")) {
            ItemStack fragmento = integracoes.fragmento();
            if (fragmento != null) {
                entregar(jogador, fragmento);
                jogador.sendMessage("§d§l✦ §dVocê pescou um §fFragmento Arcano§d! §7Troque com o Cambista, no fundo da avenida.");
                jogador.playSound(jogador.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            }
        }
    }

    private static void entregar(Player jogador, ItemStack item) {
        jogador.getInventory().addItem(item).values().forEach(i -> jogador.getWorld().dropItem(jogador.getLocation(), i));
    }

    /** A raridade do mapa pela faixa de nível ({@code pescador.pesos-mapa}: vale a do maior "nivel" alcançado). */
    private String sortearRaridade(int nivel) {
        Map<?, ?> faixa = null;
        int melhor = Integer.MIN_VALUE;
        for (Map<?, ?> m : plugin.getConfig().getMapList("pescador.pesos-mapa")) {
            int desde = m.get("nivel") instanceof Number n ? n.intValue() : 0;
            if (desde <= nivel && desde > melhor) {
                melhor = desde;
                faixa = m;
            }
        }
        String[] raridades = {"comum", "raro", "arcano", "lendario"};
        if (faixa == null) return raridades[0];
        double total = 0;
        for (String r : raridades) total += faixa.get(r) instanceof Number n ? n.doubleValue() : 0;
        double sorteio = ThreadLocalRandom.current().nextDouble() * total;
        for (String r : raridades) {
            sorteio -= faixa.get(r) instanceof Number n ? n.doubleValue() : 0;
            if (sorteio < 0) return r;
        }
        return raridades[0];
    }
}
