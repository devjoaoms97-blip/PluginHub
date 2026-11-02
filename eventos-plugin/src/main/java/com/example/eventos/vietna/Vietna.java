package com.example.eventos.vietna;

import com.example.eventos.EventosPlugin;
import com.example.eventos.util.Formato;
import com.example.eventos.vietna.ArenaVietna.Pilar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * O evento Vietnã: inscrição grátis (inventário totalmente vazio e um por IP), cada um num pilar com o
 * mesmo kit, os golpes só empurram (VietnaListener), quem cai na água vai pra arquibancada e o último de
 * pé leva o cheque. Os pilares somem com o tempo pra a luta terminar.
 * <p>
 * Quem está lutando fica em {@code pendentes.yml} (com o lugar de volta) até sair da arena: se o servidor
 * cair ou o jogador desconectar no meio, ao entrar de novo ele perde o kit e volta pra onde estava.
 */
public class Vietna {

    public enum Fase { INSCRICAO, PREPARACAO, LUTA, FIM }

    public static final class Participante {
        final String nome;
        final String ip;
        final Location retorno;
        boolean vivo = true;

        Participante(String nome, String ip, Location retorno) {
            this.nome = nome;
            this.ip = ip;
            this.retorno = retorno;
        }

        public boolean isVivo() {
            return vivo;
        }
    }

    private final EventosPlugin plugin;
    private final File arquivoPendentes;
    private final File arquivoVitorias;
    private ArenaVietna arena;

    private Fase fase;
    private final Map<UUID, Participante> participantes = new LinkedHashMap<>();
    private long fimInscricao, inicioLuta, ultimoReabastecer, proximoPilar;
    private final Set<Integer> avisosDados = new HashSet<>();
    private final List<Pilar> pilaresDePe = new ArrayList<>();
    private Pilar pilarAvisado;
    private long derrubarEm;
    private final Set<UUID> teleporteLiberado = new HashSet<>();
    private final Map<UUID, Integer> vitorias = new HashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();

    public Vietna(EventosPlugin plugin) {
        this.plugin = plugin;
        this.arquivoPendentes = new File(plugin.getDataFolder(), "vietna-pendentes.yml");
        this.arquivoVitorias = new File(plugin.getDataFolder(), "vietna-vitorias.yml");
        recarregar();
        carregarVitorias();
    }

    public void recarregar() {
        this.arena = new ArenaVietna(cfg().getConfigurationSection("arena"));
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("vietna");
    }

    // ---------------------------------------------------------------- consultas

    public ArenaVietna getArena() {
        return arena;
    }

    public Fase getFase() {
        return fase;
    }

    public Map<UUID, Participante> getParticipantes() {
        return participantes;
    }

    /** Participante vivo de um evento em preparação ou luta. */
    public Participante lutando(UUID id) {
        if (fase != Fase.PREPARACAO && fase != Fase.LUTA) return null;
        Participante p = participantes.get(id);
        return p != null && p.vivo ? p : null;
    }

    public boolean teleporteLiberado(UUID id) {
        return teleporteLiberado.contains(id);
    }

    public double premio() {
        return Math.max(0, cfg().getDouble("premio"));
    }

    public List<Map.Entry<UUID, Integer>> ranking(int quantos) {
        List<Map.Entry<UUID, Integer>> lista = new ArrayList<>(vitorias.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    public String nomeDe(UUID id) {
        String nome = nomes.get(id);
        return nome != null ? nome : Bukkit.getOfflinePlayer(id).getName();
    }

    // ---------------------------------------------------------------- inscrição

    public void abrir() {
        long duracao = Math.max(10, cfg().getInt("inscricao.duracao-segundos")) * 1000L;
        fase = Fase.INSCRICAO;
        participantes.clear();
        avisosDados.clear();
        fimInscricao = System.currentTimeMillis() + duracao;
        String abertura = "§2§l☘ VIETNÃ ☘ §ainscrições abertas por §f" + Formato.tempo(duracao)
                + "§a! Derrube os outros dos pilares na água com arco, bola de neve, carga de vento e vara. O último de pé leva um cheque de §f"
                + Formato.valor(premio()) + "§a. Grátis, com o §finventário vazio §a(nem armadura): §f/vietna entrar§a.";
        anunciar(abertura);
        plugin.getDiscord().enviar("eventos", abertura);
    }

    /** null se entrou; senão, o motivo. */
    public String entrar(Player p) {
        if (fase != Fase.INSCRICAO) return "Não há inscrições abertas no Vietnã agora.";
        if (participantes.containsKey(p.getUniqueId())) return "Você já está inscrito. Pra sair: /vietna sair";
        if (!inventarioVazio(p)) return "Pra entrar, o inventário precisa estar totalmente vazio, sem armadura e sem nada na outra mão.";
        if (p.getWorld().equals(arena.mundo())) return "Saia do mundo das arenas pra se inscrever.";
        String ip = ip(p);
        if (!p.hasPermission("eventos.ip.ignorar")) {
            for (Participante outro : participantes.values()) {
                if (outro.ip.equals(ip)) return "Já tem uma inscrição desta conexão (" + outro.nome + "): é uma por IP.";
            }
        }
        participantes.put(p.getUniqueId(), new Participante(p.getName(), ip, p.getLocation()));
        anunciar("§2[Vietnã] §f" + p.getName() + " §aentrou. §7(" + participantes.size() + " inscrito(s))");
        return null;
    }

    public boolean sair(Player p) {
        if (fase != Fase.INSCRICAO || participantes.remove(p.getUniqueId()) == null) return false;
        anunciar("§2[Vietnã] §f" + p.getName() + " §7saiu. (" + participantes.size() + " inscrito(s))");
        return true;
    }

    private static String ip(Player p) {
        return p.getAddress() == null ? "?" : p.getAddress().getAddress().getHostAddress();
    }

    /** Nada em nenhum espaço: mochila, barra, armadura, outra mão e o item no cursor. */
    public static boolean inventarioVazio(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack item : inv.getContents()) if (item != null && !item.getType().isAir()) return false;
        for (ItemStack item : inv.getArmorContents()) if (item != null && !item.getType().isAir()) return false;
        if (!inv.getItemInOffHand().getType().isAir()) return false;
        return p.getItemOnCursor().getType().isAir();
    }

    // ---------------------------------------------------------------- início

    public void iniciar() {
        // quem saiu do servidor, pegou alguma coisa ou entrou nas arenas fica de fora
        for (UUID id : new ArrayList<>(participantes.keySet())) {
            Player p = Bukkit.getPlayer(id);
            String motivo = p == null ? null : !inventarioVazio(p) ? "o inventário não está mais vazio"
                    : p.getWorld().equals(arena.mundo()) ? "você está no mundo das arenas" : null;
            if (p == null || motivo != null) {
                participantes.remove(id);
                if (p != null) p.sendMessage("§c[Vietnã] Você ficou de fora: " + motivo + ".");
            }
        }
        int minimo = Math.max(2, cfg().getInt("minimo-jogadores"));
        if (participantes.size() < minimo) {
            cancelar("só " + participantes.size() + " inscrito(s), precisa de " + minimo);
            return;
        }
        if (arena.mundo() == null) {
            cancelar("o mundo da arena não existe");
            return;
        }
        arena.construir();
        pilaresDePe.clear();
        pilaresDePe.addAll(arena.pilares());
        pilarAvisado = null;

        // cada um num pilar diferente, espalhados
        List<Pilar> sorteio = new ArrayList<>(arena.pilares());
        Collections.shuffle(sorteio);
        int i = 0;
        for (Map.Entry<UUID, Participante> e : participantes.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            teleportar(p, arena.topo(sorteio.get(i++ % sorteio.size()), ThreadLocalRandom.current().nextInt(4) * 90f));
            restaurar(p);
        }
        salvarPendentes();

        fase = Fase.PREPARACAO;
        int preparacao = Math.max(0, cfg().getInt("luta.preparacao-segundos"));
        anunciar("§2§l☘ VIETNÃ ☘ §f" + participantes.size() + " §ajogadores nos pilares! Começa em §f" + preparacao
                + "s§a. Caiu na água, está fora. Quem quiser assistir: §f/vietna assistir§a.");
        Bukkit.getScheduler().runTaskLater(plugin, this::comecarLuta, preparacao * 20L);
    }

    private void comecarLuta() {
        if (fase != Fase.PREPARACAO) return;
        fase = Fase.LUTA;
        long agora = System.currentTimeMillis();
        inicioLuta = agora;
        ultimoReabastecer = agora;
        proximoPilar = agora + Math.max(0, cfg().getInt("pilares-somem.comecar-segundos")) * 1000L;
        for (UUID id : vivos()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            darKit(p);
            p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§a§lDERRUBEM!"));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
        }
        verificarVencedor();
    }

    private void darKit(Player p) {
        ConfigurationSection kit = cfg().getConfigurationSection("kit");
        ItemStack arco = new ItemStack(Material.BOW);
        ItemMeta meta = arco.getItemMeta();
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        if (kit.getInt("arco-impulso") > 0) meta.addEnchant(Enchantment.PUNCH, kit.getInt("arco-impulso"), true);
        meta.setUnbreakable(true);
        arco.setItemMeta(meta);
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, arco);
        if (kit.getInt("bolas-de-neve") > 0) inv.setItem(1, new ItemStack(Material.SNOWBALL, kit.getInt("bolas-de-neve")));
        if (kit.getInt("cargas-de-vento") > 0) inv.setItem(2, new ItemStack(Material.WIND_CHARGE, kit.getInt("cargas-de-vento")));
        if (kit.getBoolean("vara-de-pesca")) {
            ItemStack vara = new ItemStack(Material.FISHING_ROD);
            ItemMeta mv = vara.getItemMeta();
            mv.setUnbreakable(true);
            vara.setItemMeta(mv);
            inv.setItem(3, vara);
        }
        inv.setItem(9, new ItemStack(Material.ARROW));
    }

    /** Soma de volta um pouco de bola de neve e carga de vento, até o que o kit dá. */
    private void reabastecer(Player p) {
        ConfigurationSection kit = cfg().getConfigurationSection("kit");
        completar(p, Material.SNOWBALL, kit.getInt("reabastecer.bolas-de-neve"), kit.getInt("bolas-de-neve"));
        completar(p, Material.WIND_CHARGE, kit.getInt("reabastecer.cargas-de-vento"), kit.getInt("cargas-de-vento"));
    }

    private static void completar(Player p, Material tipo, int quantidade, int maximo) {
        int tem = 0;
        for (ItemStack i : p.getInventory().getContents()) if (i != null && i.getType() == tipo) tem += i.getAmount();
        int dar = Math.min(quantidade, maximo - tem);
        if (dar > 0) p.getInventory().addItem(new ItemStack(tipo, dar));
    }

    private void restaurar(Player p) {
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(vida != null ? vida.getValue() : 20);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
    }

    public void teleportar(Player p, Location destino) {
        if (destino == null || destino.getWorld() == null) return;
        teleporteLiberado.add(p.getUniqueId());
        try {
            p.teleport(destino);
        } finally {
            teleporteLiberado.remove(p.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- luta

    /** Caiu na água, saiu da arena ou desconectou: perde o kit e vai pra arquibancada (ou volta, se saiu). */
    public void eliminar(Player p, String motivo, boolean desconectou) {
        Participante part = lutando(p.getUniqueId());
        if (part == null) return;
        part.vivo = false;
        p.getInventory().clear();
        p.setItemOnCursor(null);
        restaurar(p);
        if (desconectou) {
            teleportar(p, part.retorno);
        } else {
            teleportar(p, arena.arquibancada());
            p.sendMessage("§7Você está na arquibancada. Pra sair: §f/spawn§7.");
        }
        removerPendente(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 0.8f);
        anunciar("§2[Vietnã] §f" + part.nome + " §7" + motivo + ". §aRestam §f" + vivos().size() + "§a.");
        verificarVencedor();
    }

    private void verificarVencedor() {
        if (fase != Fase.LUTA) return;
        List<UUID> vivos = vivos();
        if (vivos.size() <= 1) finalizar(vivos);
    }

    private List<UUID> vivos() {
        return participantes.entrySet().stream().filter(e -> e.getValue().vivo).map(Map.Entry::getKey).toList();
    }

    private void finalizar(List<UUID> ganhadores) {
        fase = Fase.FIM;
        if (ganhadores.isEmpty()) {
            anunciar("§2§l☘ VIETNÃ ☘ §7Ninguém sobrou de pé.");
        } else {
            double parte = premio() / ganhadores.size();
            List<String> nomesGanhadores = new ArrayList<>();
            for (UUID id : ganhadores) {
                Participante part = participantes.get(id);
                nomesGanhadores.add(part.nome);
                vitorias.merge(id, 1, Integer::sum);
                nomes.put(id, part.nome);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.getInventory().clear();
                    p.setItemOnCursor(null);
                    restaurar(p);
                    teleportar(p, part.retorno);
                    removerPendente(id);
                    plugin.getPremio().pagar(p, parte, "Evento Vietnã");
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
            }
            salvarVitorias();
            String resultado = "§2§l☘ VIETNÃ ☘ §f" + String.join(", ", nomesGanhadores) + " §a§l"
                    + (ganhadores.size() > 1 ? "venceram (tempo esgotado)" : "venceu") + "§a! Prêmio: cheque de §f"
                    + Formato.valor(parte) + (ganhadores.size() > 1 ? " §apra cada um" : "") + "§a.";
            anunciar(resultado);
            plugin.getDiscord().enviar("eventos", resultado);
        }
        // a arena volta inteira pro próximo
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fase == Fase.FIM) {
                arena.construir();
                fase = null;
                participantes.clear();
            }
        }, 40L);
    }

    /** Pela staff, por falta de gente ou no desligamento. Quem estava nos pilares volta (sem o kit). */
    public void cancelar(String motivo) {
        if (fase == null) return;
        boolean naArena = fase == Fase.PREPARACAO || fase == Fase.LUTA;
        if (naArena) {
            for (UUID id : vivos()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                p.getInventory().clear();
                p.setItemOnCursor(null);
                restaurar(p);
                teleportar(p, participantes.get(id).retorno);
                removerPendente(id);
            }
        }
        fase = null;
        participantes.clear();
        anunciar("§2§l☘ VIETNÃ ☘ §cCancelado: " + motivo + ".");
        if (naArena) arena.construir();
    }

    /** A cada segundo: avisos e fim das inscrições, reabastecimento, pilares sumindo e tempo máximo. */
    public void tick() {
        if (fase == null) return;
        long agora = System.currentTimeMillis();
        if (fase == Fase.INSCRICAO) {
            long falta = (fimInscricao - agora) / 1000;
            for (int s : cfg().getIntegerList("inscricao.avisos-segundos")) {
                if (falta <= s && falta > s - 2 && avisosDados.add(s)) {
                    anunciar("§2[Vietnã] §aInscrições fecham em §f" + Formato.tempo(s * 1000L) + "§a. §f" + participantes.size()
                            + " §ainscrito(s). Inventário vazio e §f/vietna entrar§a.");
                }
            }
            if (agora >= fimInscricao) iniciar();
            return;
        }
        if (fase != Fase.LUTA) return;

        for (UUID id : vivos()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !arena.naArena(p.getLocation())) eliminar(p, "saiu da arena", false);
        }
        if (fase != Fase.LUTA) return;

        int reabastecer = cfg().getInt("kit.reabastecer.segundos");
        if (reabastecer > 0 && agora - ultimoReabastecer >= reabastecer * 1000L) {
            ultimoReabastecer = agora;
            for (UUID id : vivos()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) reabastecer(p);
            }
        }

        // pilar avisado: some quando der o tempo; sem aviso pendente, escolhe o próximo
        if (pilarAvisado != null && agora >= derrubarEm) {
            arena.derrubar(pilarAvisado);
            pilaresDePe.remove(pilarAvisado);
            pilarAvisado = null;
        }
        int minimo = Math.max(1, cfg().getInt("pilares-somem.minimo"));
        if (pilarAvisado == null && agora >= proximoPilar && pilaresDePe.size() > minimo) {
            pilarAvisado = pilaresDePe.get(ThreadLocalRandom.current().nextInt(pilaresDePe.size()));
            arena.avisar(pilarAvisado);
            derrubarEm = agora + Math.max(1, cfg().getInt("pilares-somem.aviso-segundos")) * 1000L;
            proximoPilar = agora + Math.max(1, cfg().getInt("pilares-somem.intervalo-segundos")) * 1000L;
            Location l = arena.topo(pilarAvisado, 0);
            l.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 0.6f);
        }

        long maximo = Math.max(1, cfg().getInt("luta.tempo-maximo-minutos")) * 60_000L;
        if (agora - inicioLuta > maximo) finalizar(vivos());
    }

    // ---------------------------------------------------------------- assistir e volta

    public void assistir(Player p) {
        teleportar(p, arena.arquibancada());
    }

    /** Ao entrar no servidor: quem estava lutando quando caiu perde o kit e volta pra onde estava. */
    public void aoEntrar(Player p) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoPendentes);
        String chave = p.getUniqueId().toString();
        Location volta = yaml.getLocation(chave);
        if (volta == null || lutando(p.getUniqueId()) != null) return;
        p.getInventory().clear();
        restaurar(p);
        teleportar(p, volta);
        removerPendente(p.getUniqueId());
        p.sendMessage("§7[Vietnã] Você saiu no meio do evento: voltou pra onde estava, sem o kit.");
    }

    private void salvarPendentes() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoPendentes);
        participantes.forEach((id, part) -> yaml.set(id.toString(), part.retorno));
        salvar(yaml, arquivoPendentes);
    }

    private void removerPendente(UUID id) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoPendentes);
        if (!yaml.contains(id.toString())) return;
        yaml.set(id.toString(), null);
        salvar(yaml, arquivoPendentes);
    }

    private void carregarVitorias() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoVitorias);
        for (String chave : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(chave);
                vitorias.put(id, yaml.getInt(chave + ".vitorias"));
                nomes.put(id, yaml.getString(chave + ".nome"));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido ignorado em vietna-vitorias.yml: " + chave);
            }
        }
    }

    private void salvarVitorias() {
        YamlConfiguration yaml = new YamlConfiguration();
        vitorias.forEach((id, v) -> {
            yaml.set(id + ".nome", nomes.get(id));
            yaml.set(id + ".vitorias", v);
        });
        salvar(yaml, arquivoVitorias);
    }

    private void salvar(YamlConfiguration yaml, File arquivo) {
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar " + arquivo.getName(), e);
        }
    }

    private static void anunciar(String mensagem) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
    }
}
