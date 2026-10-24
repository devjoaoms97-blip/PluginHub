package com.example.gladiador.manager;

import com.example.gladiador.GladiadorPlugin;
import com.example.gladiador.model.Arena;
import com.example.gladiador.model.Evento;
import com.example.gladiador.model.Evento.Participante;
import com.example.gladiador.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * O evento Gladiador: inscrições pagas, luta na arena até sobrar um jogador (individual) ou um
 * clã (por clã), prêmio e ranking. Cada um luta com o próprio equipamento e quem morre perde os
 * itens (o mundo das arenas não guarda inventário). Desconectar ou sair da arena elimina.
 * <p>
 * O pote é a soma das inscrições mais o prêmio do servidor. No modo individual vai todo pro
 * vencedor; no modo por clã é dividido igualmente entre quem lutou pelo clã vencedor (vivo ou
 * não). Se o tempo acabar com mais de um lado vivo, os sobreviventes dividem o pote. Uma borda
 * que fecha com o tempo (Borda) empurra todo mundo pro centro, pra luta não se arrastar.
 */
public class EventoManager {

    private final GladiadorPlugin plugin;
    private final File arquivoArena;
    private final File arquivoEstatisticas;
    private final Arena arena = new Arena();
    private final Borda borda;
    private Evento evento;
    /** Quem morreu no evento renasce aqui (onde estava antes de entrar). */
    private final Map<UUID, Location> renascer = new HashMap<>();
    private final Set<UUID> teleporteLiberado = new HashSet<>();
    private final Set<Integer> avisosDados = new HashSet<>();
    private final Map<UUID, Integer> vitorias = new HashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();
    private final Map<String, Integer> vitoriasCla = new HashMap<>();

    public EventoManager(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.arquivoArena = new File(plugin.getDataFolder(), "arena.yml");
        this.arquivoEstatisticas = new File(plugin.getDataFolder(), "estatisticas.yml");
        this.borda = new Borda(plugin);
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------
    public Arena getArena() {
        return arena;
    }

    public Evento getEvento() {
        return evento;
    }

    /** Participante de um evento em preparação ou luta (onde as regras da arena valem). */
    public Participante emCombate(UUID jogador) {
        if (evento == null || !evento.emCombate()) return null;
        return evento.getParticipantes().get(jogador);
    }

    public boolean teleporteLiberado(UUID jogador) {
        return teleporteLiberado.contains(jogador);
    }

    public Location tirarRenascer(UUID jogador) {
        return renascer.remove(jogador);
    }

    public double premioServidor() {
        return Math.max(0, plugin.getConfig().getDouble("premio.servidor", 10000));
    }

    public double pote() {
        return evento == null ? 0 : evento.getInscricao() * evento.getParticipantes().size() + premioServidor();
    }

    public List<Map.Entry<UUID, Integer>> ranking(int quantos) {
        List<Map.Entry<UUID, Integer>> lista = new ArrayList<>(vitorias.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    public List<Map.Entry<String, Integer>> rankingClas(int quantos) {
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(vitoriasCla.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    public String nomeDe(UUID jogador) {
        String nome = nomes.get(jogador);
        return nome != null ? nome : Bukkit.getOfflinePlayer(jogador).getName();
    }

    // ---------------------------------------------------------------------
    // Inscrição
    // ---------------------------------------------------------------------
    public void abrir(Evento.Modo modo, double inscricao) {
        long duracao = Math.max(10, plugin.getConfig().getInt("inscricao.duracao-segundos", 300)) * 1000L;
        evento = new Evento(modo, inscricao, System.currentTimeMillis() + duracao);
        avisosDados.clear();
        String abertura = "§6§l⚔ GLADIADOR " + (modo == Evento.Modo.CLA ? "POR CLÃ" : "INDIVIDUAL") + " ⚔ §einscrições abertas por §f"
                + Formato.tempo(duracao) + "§e! Inscrição: §f" + (inscricao > 0 ? Formato.valor(inscricao) : "grátis")
                + "§e. Prêmio: §f" + Formato.valor(premioServidor()) + " §emais as inscrições. §cQuem morre perde os itens. §eEntre com §f/gladiador entrar§e.";
        anunciar(abertura);
        plugin.getDiscord().enviar("gladiador", abertura);
    }

    /** Já conferido: inscrições abertas, não inscrito, saldo e clã (modo por clã). */
    public void inscrever(Player jogador, String cla) {
        cobrarInscricao(jogador, evento.getInscricao());
        evento.getParticipantes().put(jogador.getUniqueId(), new Participante(jogador.getName(), cla, jogador.getLocation()));
        anunciar("§6[Gladiador] §f" + jogador.getName() + (cla != null ? " §7[" + cla + "]" : "") + " §eentrou. §7("
                + evento.getParticipantes().size() + " inscrito(s), pote de " + Formato.valor(pote()) + ")");
    }

    private void cobrarInscricao(Player jogador, double valor) {
        if (valor <= 0) return;
        plugin.getEconomy().withdrawPlayer(jogador, valor);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), valor, false, "Inscrição no Gladiador");
    }

    public void desinscrever(Player jogador) {
        evento.getParticipantes().remove(jogador.getUniqueId());
        devolverInscricao(jogador.getUniqueId(), evento.getInscricao());
    }

    private void devolverInscricao(UUID jogador, double valor) {
        if (valor <= 0) return;
        plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(jogador), valor);
        plugin.getPixIntegration().registrarComServidor(jogador, valor, true, "Inscrição no Gladiador devolvida");
    }

    // ---------------------------------------------------------------------
    // Início
    // ---------------------------------------------------------------------
    public void iniciar() {
        // quem saiu do servidor durante as inscrições fica de fora, com a inscrição devolvida
        for (UUID id : new ArrayList<>(evento.getParticipantes().keySet())) {
            if (Bukkit.getPlayer(id) == null) {
                evento.getParticipantes().remove(id);
                devolverInscricao(id, evento.getInscricao());
            }
        }
        String falta = faltaGente();
        if (falta != null) {
            cancelar(falta);
            return;
        }
        if (!arena.pronta()) {
            cancelar("a arena não está pronta");
            return;
        }

        // por clã, cada clã sai do mesmo ponto; no individual, cada jogador vai pro próximo ponto
        List<Location> spawns = arena.getSpawns();
        Map<String, Integer> pontoDoCla = new LinkedHashMap<>();
        int proximo = 0;
        for (Map.Entry<UUID, Participante> e : evento.getParticipantes().entrySet()) {
            int indice;
            if (evento.getModo() == Evento.Modo.CLA) {
                indice = pontoDoCla.computeIfAbsent(e.getValue().getCla(), k -> pontoDoCla.size());
            } else {
                indice = proximo++;
            }
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) preparar(p, spawns.get(indice % spawns.size()));
        }

        evento.setFase(Evento.Fase.PREPARACAO);
        int preparacao = Math.max(0, plugin.getConfig().getInt("luta.preparacao-segundos", 15));
        anunciar("§6§l⚔ GLADIADOR ⚔ §f" + evento.getParticipantes().size() + " §egladiadores na arena! A luta começa em §f"
                + preparacao + "s§e. Pote: §f" + Formato.valor(pote()) + "§e.");
        Evento esse = evento;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (evento != esse || esse.getFase() != Evento.Fase.PREPARACAO) return;
            esse.setFase(Evento.Fase.LUTA);
            esse.setInicioLuta(System.currentTimeMillis());
            for (UUID id : esse.getParticipantes().keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§c§lLUTEM!"));
            }
            borda.comecar(arena, jogadoresVivos());
            verificarVencedor();
        }, preparacao * 20L);
    }

    /** Motivo pra não começar (gente ou clãs de menos), ou null. */
    private String faltaGente() {
        if (evento.getModo() == Evento.Modo.CLA) {
            long clas = evento.getParticipantes().values().stream().map(Participante::getCla).distinct().count();
            int minimo = Math.max(2, plugin.getConfig().getInt("minimo.clas", 2));
            return clas < minimo ? "só " + clas + " clã(s) inscrito(s), precisa de " + minimo : null;
        }
        int minimo = Math.max(2, plugin.getConfig().getInt("minimo.jogadores", 4));
        int inscritos = evento.getParticipantes().size();
        return inscritos < minimo ? "só " + inscritos + " inscrito(s), precisa de " + minimo : null;
    }

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
        if (destino == null || destino.getWorld() == null) return;
        teleporteLiberado.add(jogador.getUniqueId());
        try {
            jogador.teleport(destino);
        } finally {
            teleporteLiberado.remove(jogador.getUniqueId());
        }
    }

    // ---------------------------------------------------------------------
    // Luta
    // ---------------------------------------------------------------------
    /** Morreu (ou desconectou, ou saiu da arena): eliminado. */
    public void eliminar(Player jogador, String motivo) {
        Participante p = emCombate(jogador.getUniqueId());
        if (p == null || !p.isVivo()) return;
        p.setVivo(false);
        borda.tirar(jogador);
        renascer.put(jogador.getUniqueId(), p.getRetorno());
        long vivos = evento.getParticipantes().values().stream().filter(Participante::isVivo).count();
        anunciar("§6[Gladiador] §f" + p.getNome() + " §7" + motivo + ". §eRestam §f" + vivos + "§e.");
        verificarVencedor();
    }

    private void verificarVencedor() {
        if (evento == null || evento.getFase() != Evento.Fase.LUTA) return;
        List<UUID> vivos = vivos();
        if (evento.getModo() == Evento.Modo.CLA) {
            List<String> clas = vivos.stream().map(id -> evento.getParticipantes().get(id).getCla()).distinct().toList();
            if (clas.size() > 1) return;
            if (clas.isEmpty()) {
                finalizar(List.of(), null);
                return;
            }
            String cla = clas.get(0);
            List<UUID> doCla = evento.getParticipantes().entrySet().stream()
                    .filter(e -> cla.equals(e.getValue().getCla())).map(Map.Entry::getKey).toList();
            finalizar(doCla, cla);
        } else if (vivos.size() <= 1) {
            finalizar(vivos, null);
        }
    }

    private List<Player> jogadoresVivos() {
        return vivos().stream().map(Bukkit::getPlayer).filter(p -> p != null && !p.isDead()).toList();
    }

    private List<UUID> vivos() {
        return evento.getParticipantes().entrySet().stream().filter(e -> e.getValue().isVivo()).map(Map.Entry::getKey).toList();
    }

    /** Paga o pote dividido entre os ganhadores e encerra. Lista vazia = ninguém venceu. */
    private void finalizar(List<UUID> ganhadores, String cla) {
        Evento esse = evento;
        esse.setFase(Evento.Fase.FIM);
        jogadoresVivos().forEach(borda::tirar);
        if (ganhadores.isEmpty()) {
            esse.getParticipantes().keySet().forEach(id -> devolverInscricao(id, esse.getInscricao()));
            anunciar("§6§l⚔ GLADIADOR ⚔ §7Ninguém sobrou de pé. As inscrições foram devolvidas.");
        } else {
            double pote = pote();
            double parte = pote / ganhadores.size();
            // modo por clã com premio.cla-no-banco: o pote inteiro vai pro banco do clã vencedor
            boolean noBanco = cla != null && plugin.getConfig().getBoolean("premio.cla-no-banco", false)
                    && plugin.getClasIntegration().depositarNoBanco(cla, pote, "Prêmio do Gladiador");
            for (UUID id : ganhadores) {
                if (!noBanco) pagarPremio(id, parte);
                vitorias.merge(id, 1, Integer::sum);
                nomes.put(id, esse.getParticipantes().get(id).getNome());
            }
            if (cla != null) vitoriasCla.merge(cla, 1, Integer::sum);
            salvarEstatisticas();
            String quem = cla != null ? "O clã §f[" + cla + "]"
                    : ganhadores.size() == 1 ? "§f" + esse.getParticipantes().get(ganhadores.get(0)).getNome()
                    : "§f" + String.join(", ", ganhadores.stream().map(id -> esse.getParticipantes().get(id).getNome()).toList());
            String resultado = "§6§l⚔ GLADIADOR ⚔ " + quem + " §6§lvenceu" + (ganhadores.size() > 1 && cla == null ? " (tempo esgotado)" : "")
                    + "§6§l! §ePrêmio: §f" + Formato.valor(pote)
                    + (noBanco ? " §7(direto no banco do clã)"
                    : ganhadores.size() > 1 ? " §7(" + Formato.valor(parte) + " pra cada um)" : "") + "§e.";
            anunciar(resultado);
            plugin.getDiscord().enviar("gladiador", resultado);
        }
        String destaques = plugin.getDestaques().premiar(esse);
        if (!destaques.isEmpty()) {
            anunciar(destaques);
            plugin.getDiscord().enviar("gladiador", destaques);
        }

        // quem ficou de pé tem um tempo pra pegar os itens dos outros
        int saque = Math.max(0, plugin.getConfig().getInt("luta.tempo-pra-pegar-itens-segundos", 30));
        for (UUID id : vivos()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && saque > 0) p.sendMessage("§7Você tem §f" + saque + "s §7pra pegar os itens. Depois volta pra onde estava.");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> encerrar(esse), saque * 20L);
    }

    private void pagarPremio(UUID jogador, double valor) {
        if (valor <= 0) return;
        plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(jogador), valor);
        plugin.getPixIntegration().registrarComServidor(jogador, valor, true, "Prêmio do Gladiador");
    }

    /** Manda de volta quem ainda está de pé na arena e fecha o evento. */
    private void encerrar(Evento esse) {
        for (Map.Entry<UUID, Participante> e : esse.getParticipantes().entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) borda.tirar(p);
            // cancelado nas inscrições, ninguém foi pra arena: não mexe em ninguém
            boolean naArena = p != null && arena.getPos1() != null && p.getWorld().equals(arena.getPos1().getWorld());
            if (naArena && e.getValue().isVivo() && !p.isDead()) teleportar(p, e.getValue().getRetorno());
        }
        if (evento == esse) evento = null;
    }

    /** Cancela o evento (pela staff, por falta de gente ou no desligamento): devolve as inscrições. */
    public void cancelar(String motivo) {
        if (evento == null) return;
        Evento esse = evento;
        esse.setFase(Evento.Fase.FIM);
        esse.getParticipantes().keySet().forEach(id -> devolverInscricao(id, esse.getInscricao()));
        anunciar("§6§l⚔ GLADIADOR ⚔ §cCancelado: " + motivo + ". §7As inscrições foram devolvidas.");
        encerrar(esse);
    }

    /** A cada segundo: avisos e fim das inscrições, quem saiu da arena e tempo máximo da luta. */
    public void tick() {
        if (evento == null) return;
        long agora = System.currentTimeMillis();
        if (evento.getFase() == Evento.Fase.INSCRICAO) {
            long falta = (evento.getFimInscricao() - agora) / 1000;
            for (int s : plugin.getConfig().getIntegerList("inscricao.avisos-segundos")) {
                if (falta <= s && falta > s - 2 && avisosDados.add(s)) {
                    anunciar("§6[Gladiador] §eInscrições fecham em §f" + Formato.tempo(s * 1000L) + "§e. §f"
                            + evento.getParticipantes().size() + " §einscrito(s), pote de §f" + Formato.valor(pote())
                            + "§e. Entre com §f/gladiador entrar§e.");
                }
            }
            if (agora >= evento.getFimInscricao()) iniciar();
            return;
        }
        if (evento.getFase() != Evento.Fase.LUTA) return;
        for (UUID id : vivos()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead() && !arena.contem(p.getLocation())) {
                p.setHealth(0); // sair da arena elimina, e os itens caem como numa morte
            }
        }
        if (evento.getFase() == Evento.Fase.LUTA) borda.tick(arena, jogadoresVivos(), agora - evento.getInicioLuta());
        long maximo = Math.max(1, plugin.getConfig().getInt("luta.tempo-maximo-minutos", 20)) * 60_000L;
        if (evento != null && evento.getFase() == Evento.Fase.LUTA && agora - evento.getInicioLuta() > maximo) {
            finalizar(vivos(), null);
        }
    }

    private static void anunciar(String mensagem) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
    }

    // ---------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------
    private void carregar() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoArena);
        arena.setPos1(yaml.getLocation("pos1"));
        arena.setPos2(yaml.getLocation("pos2"));
        for (Object o : yaml.getList("spawns", List.of())) {
            if (o instanceof Location l) arena.getSpawns().add(l);
        }
        YamlConfiguration est = YamlConfiguration.loadConfiguration(arquivoEstatisticas);
        if (est.isConfigurationSection("jogadores")) {
            for (String chave : est.getConfigurationSection("jogadores").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(chave);
                    vitorias.put(id, est.getInt("jogadores." + chave + ".vitorias"));
                    nomes.put(id, est.getString("jogadores." + chave + ".nome"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID inválido ignorado em estatisticas.yml: " + chave);
                }
            }
        }
        if (est.isConfigurationSection("clas")) {
            for (String tag : est.getConfigurationSection("clas").getKeys(false)) vitoriasCla.put(tag, est.getInt("clas." + tag));
        }
    }

    public void salvarArena() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("pos1", arena.getPos1());
        yaml.set("pos2", arena.getPos2());
        yaml.set("spawns", arena.getSpawns());
        salvar(yaml, arquivoArena);
    }

    private void salvarEstatisticas() {
        YamlConfiguration yaml = new YamlConfiguration();
        vitorias.forEach((id, v) -> {
            yaml.set("jogadores." + id + ".nome", nomes.get(id));
            yaml.set("jogadores." + id + ".vitorias", v);
        });
        vitoriasCla.forEach((tag, v) -> yaml.set("clas." + tag, v));
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
