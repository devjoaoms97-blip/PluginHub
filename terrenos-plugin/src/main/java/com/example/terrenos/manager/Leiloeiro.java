package com.example.terrenos.manager;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.Leilao;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.model.TerrenoId;
import com.example.terrenos.mundo.Grade;
import com.example.terrenos.util.Formato;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O Leiloeiro: um NPC na rua, na frente da entrada de cada terreno em leilão (no lugar do holograma), atrás de um atril, que, a cada clique,
 * fala a próxima frase num balão, como os NPCs de dicas do spawn: o lance, de quem era o terreno,
 * quantos itens estão guardados (nunca quais), quantas máquinas e quantos spawners tem, o lance
 * mínimo e o tempo que falta. As falas são montadas na hora do clique, com os números do momento.
 * <p>
 * Como o holograma, o NPC não fica salvo no mundo: nasce com o chunk e some com ele, ou quando o leilão
 * acaba. O atril é um bloco de verdade (com um livro que ninguém tira): sai quando o leilão acaba, e
 * clicar nele faz o Leiloeiro falar. As máquinas e os spawners vêm do MaquinasPlugin e do SpawnersPlugin (por reflexão).
 */
public class Leiloeiro implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();
    private static final int TICKS_ANIMACAO = 5;
    /** A contagem do que está guardado carrega os chunks do terreno: vale por um minuto. */
    private static final long RECONTAR_MS = 60_000L;
    /** Recipientes que o Leiloeiro chama de "baús" (com outros, diz "recipientes"). */
    private static final Set<Material> BAUS = Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);

    private record Contagem(long itens, int recipientes, boolean soBaus, int maquinas, int spawners, long em) {
    }

    private final TerrenosPlugin plugin;
    private final NamespacedKey chave;
    private final Map<TerrenoId, Mannequin> npcs = new HashMap<>();
    private final Map<TerrenoId, Contagem> contagens = new HashMap<>();
    private final Map<UUID, Map<TerrenoId, Integer>> proxima = new HashMap<>();
    private final Map<TerrenoId, Fala> falando = new HashMap<>();
    private final Map<UUID, Long> ultimoClique = new HashMap<>();
    private final Set<String> avisouFalha = new HashSet<>();

    public Leiloeiro(TerrenosPlugin plugin) {
        this.plugin = plugin;
        this.chave = new NamespacedKey(plugin, "leiloeiro");
    }

    boolean ativado() {
        return plugin.getConfig().getBoolean("leiloeiro.ativado", true);
    }

    /**
     * Põe o Leiloeiro ao lado da entrada se o terreno está em leilão, e tira se não está. Chamado pelos
     * {@link Hologramas} sempre que o holograma do terreno é refeito (com o chunk já carregado).
     */
    void atualizar(TerrenoId id, Block entrada) {
        Terreno t = plugin.getTerrenoManager().get(id);
        Mannequin atual = npcs.get(id);
        Block atril = atril(entrada);
        if (t == null || !t.emLeilao() || !ativado()) {
            if (atual != null) atual.remove();
            npcs.remove(id);
            contagens.remove(id);
            if (atril.getType() == Material.LECTERN) atril.setType(Material.AIR, false);
            return;
        }
        if (atril.getType() != Material.LECTERN) colocarAtril(atril);
        if (atual != null && atual.isValid()) return;
        // na rua, colado na borda da entrada, virado pro norte (de onde se chega), com o atril à frente
        Location local = lugarDoNpc(entrada);
        local.setYaw(180);
        Mannequin m = local.getWorld().spawn(local, Mannequin.class, e -> {
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setImmovable(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setCustomNameVisible(true);
            String nome = plugin.getConfig().getString("leiloeiro.nome");
            e.customName(LEGADO.deserialize(cores(nome == null ? "&6&lLeiloeiro" : nome)));
            e.setDescription(LEGADO.deserialize("§7Clique pra saber do leilão"));
            // sem padrão no getString: assim vale o do config do jar quando o do servidor não tem a chave
            String skin = plugin.getConfig().getString("leiloeiro.skin");
            if (skin != null && !skin.isBlank()) e.setProfile(perfil(skin));
            e.getPersistentDataContainer().set(chave, PersistentDataType.STRING, id.toString());
        });
        npcs.put(id, m);
    }

    /** O NPC fica na rua, no bloco colado na borda da entrada (mais longe, encostava no terreno da frente). */
    private static Location lugarDoNpc(Block entrada) {
        return entrada.getLocation().add(0.5, 0, -0.5);
    }

    /** O atril, na frente do NPC (mais um bloco ao norte). */
    private static Block atril(Block entrada) {
        return entrada.getRelative(0, 0, -2);
    }

    /** Atril com um livro, virado pro NPC (quem lê fica do lado sul). */
    private void colocarAtril(Block bloco) {
        bloco.setType(Material.LECTERN, false);
        if (bloco.getBlockData() instanceof org.bukkit.block.data.type.Lectern dados) {
            dados.setFacing(org.bukkit.block.BlockFace.SOUTH);
            bloco.setBlockData(dados, false);
        }
        if (bloco.getState() instanceof org.bukkit.block.Lectern estado) {
            estado.getInventory().setItem(0, new ItemStack(Material.WRITABLE_BOOK));
        }
    }

    /** O terreno do atril do Leiloeiro nesse bloco, ou null (não é um atril de leilão). */
    private TerrenoId terrenoDoAtril(Block bloco) {
        if (bloco.getType() != Material.LECTERN || !bloco.getWorld().equals(plugin.getMundo())) return null;
        Block entrada = bloco.getRelative(0, 0, 2);
        TerrenoId id = plugin.getGrade().terrenoEm(entrada.getX(), entrada.getZ() + 1);
        if (id == null) return null;
        Grade g = plugin.getGrade();
        boolean ehEntrada = entrada.getX() == g.minX(id) + g.getTamanho() / 2 && entrada.getZ() == g.minZ(id) - 1;
        Terreno t = plugin.getTerrenoManager().get(id);
        return ehEntrada && t != null && t.emLeilao() ? id : null;
    }

    /** Clicar no atril: ninguém tira o livro, e o Leiloeiro fala como se tivessem clicado nele. */
    @EventHandler(priority = EventPriority.LOW)
    public void aoUsarAtril(org.bukkit.event.player.PlayerInteractEvent evento) {
        if (evento.getClickedBlock() == null || evento.getHand() != EquipmentSlot.HAND) return;
        TerrenoId id = terrenoDoAtril(evento.getClickedBlock());
        if (id == null) return;
        evento.setCancelled(true);
        if (evento.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Mannequin npc = npcs.get(id);
        if (npc != null && npc.isValid()) falar(evento.getPlayer(), npc, id);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoTirarLivro(org.bukkit.event.player.PlayerTakeLecternBookEvent evento) {
        if (terrenoDoAtril(evento.getLectern().getBlock()) != null) evento.setCancelled(true);
    }

    /** Some com os NPCs do chunk que descarregou. */
    void descarregar(Chunk chunk) {
        npcs.entrySet().removeIf(e -> {
            Location l = e.getValue().getLocation();
            if (l.getBlockX() >> 4 != chunk.getX() || l.getBlockZ() >> 4 != chunk.getZ()) return false;
            e.getValue().remove();
            return true;
        });
    }

    public void removerTodos() {
        new ArrayList<>(falando.values()).forEach(Fala::encerrar);
        npcs.values().forEach(Entity::remove);
        npcs.clear();
    }

    // ---------------------------------------------------------------------
    // Falas
    // ---------------------------------------------------------------------

    /** As falas do Leiloeiro sobre o terreno, com os números de agora. */
    private List<String> falas(Terreno t) {
        Leilao l = t.getLeilao();
        TerrenoManager terrenos = plugin.getTerrenoManager();
        Contagem c = contar(t.getId());
        String id = t.getId().toString();
        List<String> falas = new ArrayList<>();
        falas.add("§6Dou-lhe uma! §fO terreno §e" + id + " §festá em leilão" + (l.temLance()
                ? ", e o lance mais alto é de §a" + Formato.valor(l.getLance()) + "§f, do §e" + l.getNomeLicitante() + "§f!"
                : ", e ainda está §esem lances§f!"));
        if (l.getNomeDonoAnterior() != null) {
            falas.add("§fEra do §e" + l.getNomeDonoAnterior() + "§f, que não pagou o imposto. Quem arrematar leva §etudo §fo que está aqui dentro!");
        }
        if (c.itens() > 0) {
            falas.add("§fLá dentro tem §e" + numero(c.itens()) + " ite" + (c.itens() == 1 ? "m" : "ns") + " §fguardado" + (c.itens() == 1 ? "" : "s")
                    + " em §e" + c.recipientes() + " " + (c.soBaus() ? "baú" + (c.recipientes() == 1 ? "" : "s") : "recipiente" + (c.recipientes() == 1 ? "" : "s"))
                    + "§f. O que são? Só arrematando pra saber!");
        } else {
            falas.add("§fNão tem nenhum item guardado: só o terreno e o que foi construído nele.");
        }
        List<String> extras = new ArrayList<>();
        if (c.maquinas() > 0) extras.add("§e" + c.maquinas() + " máquina" + (c.maquinas() == 1 ? "" : "s"));
        if (c.spawners() > 0) extras.add("§e" + c.spawners() + " spawner" + (c.spawners() == 1 ? "" : "s"));
        if (!extras.isEmpty()) {
            boolean uma = c.maquinas() + c.spawners() == 1;
            falas.add("§fE tem mais: " + String.join(" §fe ", extras) + "§f, pronto" + (uma ? "" : "s") + " pra trabalhar!");
        }
        falas.add("§f" + (l.temLance() ? "Pra passar na frente, o lance precisa ser de pelo menos " : "Lance mínimo de ")
                + "§a" + Formato.valor(terrenos.proximoLanceMinimo(l)) + "§f! Dê o seu com §e/terreno lance " + id + " <valor>§f.");
        falas.add("§fFaltam §e" + Formato.tempo(l.getTerminaEm() - System.currentTimeMillis()) + " §fpro martelo bater. §6Dou-lhe duas...");
        return falas;
    }

    /** Itens guardados, recipientes, máquinas e spawners do terreno (refeito a cada minuto, no máximo). */
    private Contagem contar(TerrenoId id) {
        Contagem antiga = contagens.get(id);
        if (antiga != null && System.currentTimeMillis() - antiga.em() < RECONTAR_MS) return antiga;
        World mundo = plugin.getMundo();
        Grade g = plugin.getGrade();
        int minX = g.minX(id), minZ = g.minZ(id), maxX = minX + g.getTamanho() - 1, maxZ = minZ + g.getTamanho() - 1;
        long itens = 0;
        // recipiente → itens dentro; o baú duplo é um recipiente só (as duas metades têm a mesma chave)
        Map<String, Long> porRecipiente = new HashMap<>();
        boolean soBaus = true;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                Chunk chunk = mundo.getChunkAt(cx, cz);
                for (BlockState estado : chunk.getTileEntities()) {
                    if (!(estado instanceof Container recipiente) || !dentro(estado.getX(), estado.getZ(), minX, minZ, maxX, maxZ)) continue;
                    // o inventário do próprio bloco: cada metade do baú duplo soma só a sua parte
                    long aqui = contar(recipiente.getSnapshotInventory());
                    porRecipiente.merge(chaveDoRecipiente(estado), aqui, Long::sum);
                    if (aqui == 0) continue;
                    itens += aqui;
                    soBaus &= BAUS.contains(estado.getType());
                }
                for (Entity entidade : chunk.getEntities()) {
                    // carrinho e bote com baú, burro, lhama...
                    if (entidade instanceof Player || !(entidade instanceof InventoryHolder dono)) continue;
                    Location l = entidade.getLocation();
                    if (!dentro(l.getBlockX(), l.getBlockZ(), minX, minZ, maxX, maxZ)) continue;
                    long aqui = contar(dono.getInventory());
                    if (aqui == 0) continue;
                    itens += aqui;
                    porRecipiente.put(entidade.getUniqueId().toString(), aqui);
                    soBaus = false;
                }
            }
        }
        int recipientes = (int) porRecipiente.values().stream().filter(n -> n > 0).count();
        Contagem nova = new Contagem(itens, recipientes, soBaus, deOutro("MaquinasPlugin", "maquinasNoTerreno", id),
                deOutro("SpawnersPlugin", "spawnersNoTerreno", id), System.currentTimeMillis());
        contagens.put(id, nova);
        return nova;
    }

    /** Quantos o outro plugin conta no terreno (por reflexão); 0 sem o plugin. */
    private int deOutro(String nomePlugin, String metodo, TerrenoId id) {
        Plugin outro = plugin.getServer().getPluginManager().getPlugin(nomePlugin);
        if (outro == null || !outro.isEnabled()) return 0;
        try {
            return outro.getClass().getMethod(metodo, String.class).invoke(outro, id.toString()) instanceof Integer n ? n : 0;
        } catch (ReflectiveOperationException e) {
            if (avisouFalha.add(nomePlugin)) plugin.getLogger().warning(nomePlugin + " sem " + metodo + " (versão antiga?): " + e.getMessage());
            return 0;
        }
    }

    /** O baú duplo tem um inventário só pras duas metades: a chave é o lugar dele (o meio); o resto, o próprio bloco. */
    private static String chaveDoRecipiente(BlockState estado) {
        if (estado instanceof org.bukkit.block.Chest bau && bau.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest duplo) {
            Location l = duplo.getLocation();
            return "duplo " + l.getX() + " " + l.getY() + " " + l.getZ();
        }
        return estado.getX() + " " + estado.getY() + " " + estado.getZ();
    }

    private static boolean dentro(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Itens do inventário, contando também o que está dentro de shulkers e bundles. */
    private static long contar(Inventory inventario) {
        long total = 0;
        for (ItemStack item : inventario.getContents()) total += contar(item);
        return total;
    }

    private static long contar(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        long total = item.getAmount();
        if (item.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState() && meta.getBlockState() instanceof ShulkerBox caixa) {
            total += item.getAmount() * contar(caixa.getInventory());
        } else if (item.getItemMeta() instanceof BundleMeta bundle) {
            for (ItemStack dentro : bundle.getItems()) total += item.getAmount() * contar(dentro);
        }
        return total;
    }

    private static String numero(long n) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,d", n);
    }

    private static String cores(String texto) {
        return texto == null ? "" : texto.replace('&', '§');
    }

    /** A skin: "textura:<hash>" (textures.minecraft.net) ou o nick de quem empresta a skin. */
    private static ResolvableProfile perfil(String skin) {
        if (skin.startsWith("textura:")) {
            String hash = skin.substring("textura:".length()).trim();
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String valor = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return ResolvableProfile.resolvableProfile()
                    .uuid(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)))
                    .name("npc" + hash.substring(0, Math.min(10, hash.length())))
                    .addProperty(new ProfileProperty("textures", valor)).build();
        }
        return ResolvableProfile.resolvableProfile().name(skin).build();
    }

    // ---------------------------------------------------------------------
    // Clique e balão
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void aoClicar(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND || !(evento.getRightClicked() instanceof Mannequin npc)) return;
        TerrenoId id = TerrenoId.ler(npc.getPersistentDataContainer().get(chave, PersistentDataType.STRING));
        if (id == null) return;
        evento.setCancelled(true);
        falar(evento.getPlayer(), npc, id);
    }

    /** A próxima fala pro jogador (ou completa a que está sendo escrita). */
    private void falar(Player jogador, Mannequin npc, TerrenoId id) {
        // um clique pode chegar como dois eventos ("interact" e "interact at"): só o primeiro conta
        long agora = System.currentTimeMillis();
        Long anterior = ultimoClique.put(jogador.getUniqueId(), agora);
        if (anterior != null && agora - anterior < 200) return;
        Terreno t = plugin.getTerrenoManager().get(id);
        if (t == null || !t.emLeilao()) return;

        Fala atual = falando.get(id);
        if (atual != null && !atual.completa()) {
            atual.completar();
            return;
        }
        if (atual != null) atual.encerrar();

        List<String> falas = falas(t);
        Map<TerrenoId, Integer> doJogador = proxima.computeIfAbsent(jogador.getUniqueId(), k -> new HashMap<>());
        int indice = doJogador.getOrDefault(id, 0) % falas.size();
        doJogador.put(id, indice + 1);

        Fala fala = new Fala(id, npc, jogador, falas.get(indice));
        falando.put(id, fala);
        fala.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity().getPersistentDataContainer().has(chave)) evento.setCancelled(true);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        proxima.remove(evento.getPlayer().getUniqueId());
        ultimoClique.remove(evento.getPlayer().getUniqueId());
    }

    /** Uma fala em andamento: o balão cresce, a fala é escrita letra por letra, segura e encolhe. */
    private final class Fala extends BukkitRunnable {
        private final TerrenoId id;
        private final Mannequin entidade;
        private final String texto;
        private final int visiveis;
        private final TextDisplay balao;
        private final float yawOriginal;
        private int letras;
        private int segurar = -1;
        private int encolhendo = -1;

        Fala(TerrenoId id, Mannequin entidade, Player ouvinte, String texto) {
            this.id = id;
            this.entidade = entidade;
            this.texto = texto;
            this.visiveis = visiveis(texto);
            this.yawOriginal = entidade.getLocation().getYaw();

            // vira o rosto pra quem clicou
            Vector olhar = ouvinte.getEyeLocation().toVector().subtract(entidade.getEyeLocation().toVector());
            entidade.setRotation(entidade.getLocation().setDirection(olhar).getYaw(), 0);

            Location topo = entidade.getLocation().add(0, 2.6, 0);
            this.balao = topo.getWorld().spawn(topo, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setLineWidth(180);
                t.setBackgroundColor(Color.fromARGB(170, 20, 20, 30));
                t.setShadowed(true);
                t.text(LEGADO.deserialize(""));
                t.setTransformation(escala(0f));
            });
            // no tick seguinte, interpola da escala 0 até a cheia: o balão "abre"
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!balao.isValid()) return;
                balao.setInterpolationDelay(0);
                balao.setInterpolationDuration(TICKS_ANIMACAO);
                balao.setTransformation(escala(1f));
            });
        }

        boolean completa() {
            return letras >= visiveis;
        }

        void completar() {
            letras = visiveis;
            balao.text(LEGADO.deserialize(texto));
        }

        @Override
        public void run() {
            if (!balao.isValid() || !entidade.isValid()) {
                encerrar();
                return;
            }
            if (encolhendo >= 0) {
                if (--encolhendo < 0) encerrar();
                return;
            }
            if (!completa()) {
                letras = Math.min(visiveis, letras + 1);
                balao.text(LEGADO.deserialize(prefixo(texto, letras)));
                if (letras % 2 == 0) {
                    float tom = 1.2f + ThreadLocalRandom.current().nextFloat() * 0.6f;
                    entidade.getWorld().playSound(entidade.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, tom);
                }
                return;
            }
            if (segurar < 0) segurar = (int) Math.min(240, 40 + visiveis * 1.2);
            if (--segurar <= 0) {
                encolhendo = TICKS_ANIMACAO;
                balao.setInterpolationDelay(0);
                balao.setInterpolationDuration(TICKS_ANIMACAO);
                balao.setTransformation(escala(0f));
            }
        }

        void encerrar() {
            if (!isCancelled()) cancel();
            if (balao.isValid()) balao.remove();
            if (entidade.isValid()) entidade.setRotation(yawOriginal, 0);
            falando.remove(id, this);
        }
    }

    private static Transformation escala(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    /** Quantas letras aparecem (sem contar os códigos de cor §x). */
    private static int visiveis(String texto) {
        int n = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '§' && i + 1 < texto.length()) i++;
            else n++;
        }
        return n;
    }

    /** O começo do texto com {@code letras} letras visíveis, mantendo os códigos de cor pelo caminho. */
    private static String prefixo(String texto, int letras) {
        int n = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '§' && i + 1 < texto.length()) {
                i++;
                continue;
            }
            if (++n == letras) return texto.substring(0, i + 1);
        }
        return texto;
    }
}
