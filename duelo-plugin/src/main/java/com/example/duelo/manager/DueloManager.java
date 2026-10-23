package com.example.duelo.manager;

import com.example.duelo.DueloPlugin;
import com.example.duelo.model.Arena;
import com.example.duelo.model.Convite;
import com.example.duelo.model.Duelo;
import com.example.duelo.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Convites, duelos em andamento, arenas ({@code arenas.yml}) e ranking ({@code estatisticas.yml}).
 * <p>
 * Ao aceitar, as duas apostas são retidas e os lutadores vão pra uma arena livre, curados e
 * parados durante a contagem. Quem morre perde os itens (o mundo das arenas não guarda
 * inventário) e renasce onde estava antes do duelo. O vencedor leva o pote menos a taxa, tem
 * alguns segundos pra pegar os itens do outro e volta. Desconectar conta como morte. Passado o
 * tempo máximo, é empate: as apostas voltam e os dois saem. Quem assiste fica no modo espectador
 * em volta da arena e volta pra onde estava quando o duelo acaba.
 */
public class DueloManager {

    private final DueloPlugin plugin;
    private final File arquivoArenas;
    private final File arquivoEstatisticas;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    /** Convite pendente, pela UUID do desafiado (um por vez). */
    private final Map<UUID, Convite> convites = new HashMap<>();
    private final List<Duelo> duelos = new ArrayList<>();
    /** Quem morreu num duelo renasce aqui (onde estava antes). */
    private final Map<UUID, Location> renascer = new HashMap<>();
    /** Pode ser teleportado agora (pelo próprio plugin), mesmo em duelo. */
    private final Set<UUID> teleporteLiberado = new HashSet<>();
    private final Map<UUID, int[]> estatisticas = new HashMap<>(); // {vitórias, derrotas}
    /** Quem está assistindo um duelo: o duelo, pra onde volta e o modo de jogo de antes. */
    private final Map<UUID, Espectador> espectadores = new HashMap<>();

    private record Espectador(Duelo duelo, Location retorno, GameMode modoAntes) {
    }
    private final Map<UUID, String> nomes = new HashMap<>();

    public DueloManager(DueloPlugin plugin) {
        this.plugin = plugin;
        this.arquivoArenas = new File(plugin.getDataFolder(), "arenas.yml");
        this.arquivoEstatisticas = new File(plugin.getDataFolder(), "estatisticas.yml");
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------
    public Map<String, Arena> getArenas() {
        return arenas;
    }

    public Arena getArena(String nome) {
        return arenas.get(nome.toLowerCase(Locale.ROOT));
    }

    public Duelo getDuelo(UUID jogador) {
        for (Duelo d : duelos) {
            if (d.participa(jogador)) return d;
        }
        return null;
    }

    public boolean emDuelo(UUID jogador) {
        return getDuelo(jogador) != null;
    }

    public Convite getConvite(UUID desafiado) {
        Convite c = convites.get(desafiado);
        if (c != null && System.currentTimeMillis() > c.expiraEm()) {
            convites.remove(desafiado);
            return null;
        }
        return c;
    }

    /** Já desafiou alguém e o convite ainda vale. */
    public boolean desafiouAlguem(UUID desafiante) {
        long agora = System.currentTimeMillis();
        return convites.values().stream().anyMatch(c -> c.desafiante().equals(desafiante) && agora <= c.expiraEm());
    }

    public Arena arenaLivre() {
        for (Arena a : arenas.values()) {
            if (a.pronta() && !a.isOcupada()) return a;
        }
        return null;
    }

    public boolean teleporteLiberado(UUID jogador) {
        return teleporteLiberado.contains(jogador);
    }

    public boolean assistindo(UUID jogador) {
        return espectadores.containsKey(jogador);
    }

    /** Centro da arena (entre os dois pontos de partida), um pouco acima. */
    public Location centroDaArena(Duelo duelo) {
        Location a = duelo.getArena().getPos1(), b = duelo.getArena().getPos2();
        Location centro = a.clone().add(b).multiply(0.5).add(0, 4, 0);
        centro.setWorld(a.getWorld());
        return centro;
    }

    /** Leva o jogador pra assistir o duelo, no modo espectador. */
    public void assistir(Player jogador, Duelo duelo) {
        espectadores.put(jogador.getUniqueId(), new Espectador(duelo, jogador.getLocation(), jogador.getGameMode()));
        Location centro = centroDaArena(duelo);
        centro.setPitch(60);
        teleportar(jogador, centro);
        jogador.setGameMode(GameMode.SPECTATOR);
        // o Multiverse aplica o modo de jogo do mundo um pouco depois da troca de mundo: reaplica
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (assistindo(jogador.getUniqueId()) && jogador.isOnline()) jogador.setGameMode(GameMode.SPECTATOR);
        }, 5L);
        jogador.sendMessage("§eAssistindo §f" + duelo.nome(duelo.getDesafiante()) + " §7vs §f" + duelo.nome(duelo.getDesafiado())
                + "§e. Pra sair: §f/duelo sair§e.");
    }

    /** Volta o espectador pra onde estava, no modo de jogo de antes. */
    public void pararDeAssistir(Player jogador) {
        Espectador e = espectadores.remove(jogador.getUniqueId());
        if (e == null) return;
        jogador.setGameMode(e.modoAntes());
        teleportar(jogador, e.retorno());
    }

    /** Raio em volta do centro da arena que o espectador pode percorrer. */
    public boolean longeDaArena(Player jogador, Location onde) {
        Espectador e = espectadores.get(jogador.getUniqueId());
        if (e == null) return false;
        Location centro = centroDaArena(e.duelo());
        double raio = Math.max(5, plugin.getConfig().getDouble("espectador.raio", 25));
        return !onde.getWorld().equals(centro.getWorld()) || onde.distanceSquared(centro) > raio * raio;
    }

    public Location tirarRenascer(UUID jogador) {
        return renascer.remove(jogador);
    }

    public int[] getEstatisticas(UUID jogador) {
        return estatisticas.getOrDefault(jogador, new int[2]);
    }

    public List<Map.Entry<UUID, int[]>> ranking(int quantos) {
        List<Map.Entry<UUID, int[]>> lista = new ArrayList<>(estatisticas.entrySet());
        lista.sort((a, b) -> b.getValue()[0] != a.getValue()[0] ? b.getValue()[0] - a.getValue()[0] : a.getValue()[1] - b.getValue()[1]);
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    public String nomeDe(UUID jogador) {
        String nome = nomes.get(jogador);
        return nome != null ? nome : Bukkit.getOfflinePlayer(jogador).getName();
    }

    // ---------------------------------------------------------------------
    // Convite
    // ---------------------------------------------------------------------
    public void convidar(Player desafiante, Player desafiado, double aposta) {
        long expira = System.currentTimeMillis() + Math.max(10, plugin.getConfig().getInt("convite.expira-segundos", 60)) * 1000L;
        convites.put(desafiado.getUniqueId(), new Convite(desafiante.getUniqueId(), desafiante.getName(), desafiado.getUniqueId(), aposta, expira));
    }

    public void recusar(UUID desafiado) {
        convites.remove(desafiado);
    }

    // ---------------------------------------------------------------------
    // Início
    // ---------------------------------------------------------------------
    /** Já conferidos: convite válido, saldos e arena livre. */
    public void iniciar(Convite convite, Player desafiante, Player desafiado, Arena arena) {
        convites.remove(desafiado.getUniqueId());
        reterAposta(desafiante, convite.aposta());
        reterAposta(desafiado, convite.aposta());

        Duelo duelo = new Duelo(desafiante.getUniqueId(), desafiante.getName(), desafiado.getUniqueId(), desafiado.getName(),
                convite.aposta(), arena);
        arena.setOcupada(true);
        duelos.add(duelo);
        duelo.getRetorno().put(desafiante.getUniqueId(), desafiante.getLocation());
        duelo.getRetorno().put(desafiado.getUniqueId(), desafiado.getLocation());

        preparar(desafiante, arena.getPos1());
        preparar(desafiado, arena.getPos2());

        int contagem = Math.max(1, plugin.getConfig().getInt("duelo.contagem-segundos", 5));
        String titulo = "§6§lX1 §f" + desafiante.getName() + " §7vs §f" + desafiado.getName()
                + (convite.aposta() > 0 ? " §7- pote §f" + Formato.valor(convite.aposta() * 2) : " §7(amistoso)");
        desafiante.sendMessage(titulo);
        desafiado.sendMessage(titulo);
        for (int s = contagem; s >= 1; s--) {
            int falta = s;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (duelo.getFase() != Duelo.Fase.CONTAGEM) return;
                for (UUID id : List.of(duelo.getDesafiante(), duelo.getDesafiado())) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§eComeça em §f" + falta + "§e..."));
                }
            }, (long) (contagem - s) * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duelo.getFase() != Duelo.Fase.CONTAGEM) return;
            duelo.setFase(Duelo.Fase.LUTA);
            duelo.setInicioLuta(System.currentTimeMillis());
            for (UUID id : List.of(duelo.getDesafiante(), duelo.getDesafiado())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§c§lLUTEM!"));
            }
        }, contagem * 20L);
    }

    private void reterAposta(Player jogador, double valor) {
        if (valor <= 0) return;
        plugin.getEconomy().withdrawPlayer(jogador, valor);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), valor, false, "Aposta de duelo");
    }

    /** Leva pra arena com vida e fome cheias, sem fogo nem efeitos de antes. */
    private void preparar(Player jogador, Location destino) {
        teleportar(jogador, destino);
        AttributeInstance vidaMaxima = jogador.getAttribute(Attribute.MAX_HEALTH);
        jogador.setHealth(vidaMaxima != null ? vidaMaxima.getValue() : 20);
        jogador.setFoodLevel(20);
        jogador.setSaturation(20);
        jogador.setFireTicks(0);
        jogador.getActivePotionEffects().forEach(e -> jogador.removePotionEffect(e.getType()));
    }

    private void teleportar(Player jogador, Location destino) {
        teleporteLiberado.add(jogador.getUniqueId());
        try {
            jogador.teleport(destino);
        } finally {
            teleporteLiberado.remove(jogador.getUniqueId());
        }
    }

    // ---------------------------------------------------------------------
    // Fim
    // ---------------------------------------------------------------------
    /** Um dos lutadores morreu (ou desconectou, que conta como morte). */
    public void aoMorrer(Player morto) {
        Duelo duelo = getDuelo(morto.getUniqueId());
        if (duelo == null || duelo.getFase() == Duelo.Fase.FIM) return;
        duelo.setFase(Duelo.Fase.FIM);
        renascer.put(morto.getUniqueId(), duelo.getRetorno().get(morto.getUniqueId()));

        UUID vencedorId = duelo.oponente(morto.getUniqueId());
        String nomeVencedor = duelo.nome(vencedorId);
        double pote = duelo.getAposta() * 2;
        double taxa = pote * Math.max(0, Math.min(100, plugin.getConfig().getDouble("duelo.taxa-percentual", 5))) / 100.0;
        double premio = pote - taxa;
        pagarVencedor(vencedorId, premio);
        contar(vencedorId, nomeVencedor, 0);
        contar(morto.getUniqueId(), morto.getName(), 1);
        salvarEstatisticas();

        String mensagem = "§6§l[X1] §f" + nomeVencedor + " §evenceu §f" + morto.getName()
                + (premio > 0 ? " §ee levou §f" + Formato.valor(premio) : "") + "§e!";
        if (duelo.getAposta() >= plugin.getConfig().getDouble("anuncio.aposta-minima", 10000)) {
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
        } else {
            morto.sendMessage(mensagem);
            Player v = Bukkit.getPlayer(vencedorId);
            if (v != null) v.sendMessage(mensagem);
        }

        // o vencedor tem um tempo pra pegar os itens de quem perdeu
        int saque = Math.max(0, plugin.getConfig().getInt("duelo.tempo-pra-pegar-itens-segundos", 20));
        Player vencedor = Bukkit.getPlayer(vencedorId);
        if (vencedor != null && saque > 0) {
            vencedor.sendMessage("§7Você tem §f" + saque + "s §7pra pegar os itens. Depois volta pra onde estava.");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> encerrar(duelo), saque * 20L);
    }

    private void pagarVencedor(UUID vencedor, double premio) {
        if (premio <= 0) return;
        plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(vencedor), premio);
        plugin.getPixIntegration().registrarComServidor(vencedor, premio, true, "Prêmio de duelo");
    }

    /** Tempo esgotado ou staff cancelou: devolve as apostas e manda os dois de volta. */
    public void empatar(Duelo duelo, String motivo) {
        if (duelo.getFase() == Duelo.Fase.FIM) return;
        duelo.setFase(Duelo.Fase.FIM);
        for (UUID id : List.of(duelo.getDesafiante(), duelo.getDesafiado())) {
            devolverAposta(id, duelo.getAposta());
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage("§e§l[X1] §eEmpate (" + motivo + ")." + (duelo.getAposta() > 0 ? " §7A aposta voltou pra sua conta." : ""));
        }
        encerrar(duelo);
    }

    private void devolverAposta(UUID jogador, double valor) {
        if (valor <= 0) return;
        OfflinePlayer p = Bukkit.getOfflinePlayer(jogador);
        plugin.getEconomy().depositPlayer(p, valor);
        plugin.getPixIntegration().registrarComServidor(jogador, valor, true, "Aposta de duelo devolvida");
    }

    /** Tira da arena quem ainda está vivo lá e libera a arena. */
    private void encerrar(Duelo duelo) {
        for (UUID id : List.of(duelo.getDesafiante(), duelo.getDesafiado())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead() && !renascer.containsKey(id)) {
                teleportar(p, duelo.getRetorno().get(id));
            }
        }
        duelo.getArena().setOcupada(false);
        duelos.remove(duelo);
        for (Map.Entry<UUID, Espectador> e : new ArrayList<>(espectadores.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (e.getValue().duelo() == duelo && p != null) {
                pararDeAssistir(p);
                p.sendMessage("§7O duelo acabou.");
            }
        }
    }

    /** A cada segundo: convites vencidos e tempo máximo de luta. */
    public void tick() {
        long agora = System.currentTimeMillis();
        convites.values().removeIf(c -> agora > c.expiraEm());
        long maximo = Math.max(1, plugin.getConfig().getInt("duelo.tempo-maximo-minutos", 5)) * 60_000L;
        for (Duelo d : new ArrayList<>(duelos)) {
            if (d.getFase() == Duelo.Fase.LUTA && agora - d.getInicioLuta() > maximo) empatar(d, "tempo esgotado");
        }
    }

    /** Desligando o servidor: empata tudo (devolve as apostas). */
    public void encerrarTodos() {
        for (Duelo d : new ArrayList<>(duelos)) empatar(d, "servidor reiniciando");
        for (UUID id : new ArrayList<>(espectadores.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) pararDeAssistir(p);
        }
    }

    private void contar(UUID jogador, String nome, int indice) {
        estatisticas.computeIfAbsent(jogador, k -> new int[2])[indice]++;
        nomes.put(jogador, nome);
    }

    // ---------------------------------------------------------------------
    // Arenas (staff)
    // ---------------------------------------------------------------------
    public Arena criarArena(String nome) {
        Arena a = new Arena(nome.toLowerCase(Locale.ROOT));
        arenas.put(a.getNome(), a);
        salvarArenas();
        return a;
    }

    public void removerArena(Arena arena) {
        arenas.remove(arena.getNome());
        salvarArenas();
    }

    // ---------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------
    private void carregar() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoArenas);
        ConfigurationSection secao = yaml.getConfigurationSection("arenas");
        if (secao != null) {
            for (String nome : secao.getKeys(false)) {
                Arena a = new Arena(nome);
                a.setPos1(secao.getLocation(nome + ".pos1"));
                a.setPos2(secao.getLocation(nome + ".pos2"));
                arenas.put(nome, a);
            }
        }
        YamlConfiguration est = YamlConfiguration.loadConfiguration(arquivoEstatisticas);
        for (String chave : est.getKeys(false)) {
            try {
                UUID id = UUID.fromString(chave);
                estatisticas.put(id, new int[]{est.getInt(chave + ".vitorias"), est.getInt(chave + ".derrotas")});
                nomes.put(id, est.getString(chave + ".nome"));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido ignorado em estatisticas.yml: " + chave);
            }
        }
    }

    public void salvarArenas() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            yaml.set("arenas." + a.getNome() + ".pos1", a.getPos1());
            yaml.set("arenas." + a.getNome() + ".pos2", a.getPos2());
        }
        salvar(yaml, arquivoArenas);
    }

    private void salvarEstatisticas() {
        YamlConfiguration yaml = new YamlConfiguration();
        estatisticas.forEach((id, v) -> {
            yaml.set(id + ".nome", nomes.get(id));
            yaml.set(id + ".vitorias", v[0]);
            yaml.set(id + ".derrotas", v[1]);
        });
        salvar(yaml, arquivoEstatisticas);
    }

    private void salvar(YamlConfiguration yaml, File arquivo) {
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar " + arquivo.getName(), e);
        }
    }
}
