package com.example.eventos.base;

import com.example.eventos.EventosPlugin;
import com.example.eventos.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
import java.util.logging.Level;

/**
 * O que todo evento de arena tem em comum (Vietnã, Spleef...): inscrição grátis com o inventário
 * totalmente vazio e uma por IP, mínimo de jogadores, preparação, luta com o mesmo kit pra todos, quem
 * cai vai pra arquibancada sem o kit, o último de pé leva o cheque, tempo máximo e agenda diária.
 * <p>
 * Cada evento diz a arena (onde é a luta, a arquibancada, o que é "cair"), como constrói, onde cada um
 * começa, o kit e o que acontece a cada segundo da luta. As regras de comando, teleporte, dano e
 * quebra de bloco ficam no {@link EventosListener}.
 * <p>
 * Quem está lutando fica em {@code <id>-pendentes.yml} (com o lugar de volta) até sair da arena: se o
 * servidor cair ou o jogador desconectar no meio, ao entrar de novo ele perde o kit e volta pra onde estava.
 */
public abstract class Evento {

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

    protected final EventosPlugin plugin;
    private final String id;
    private final File arquivoPendentes;
    private final File arquivoVitorias;

    private Fase fase;
    private final Map<UUID, Participante> participantes = new LinkedHashMap<>();
    private long fimInscricao, inicioLuta;
    private final Set<Integer> avisosDados = new HashSet<>();
    private final Set<UUID> teleporteLiberado = new HashSet<>();
    private final Set<UUID> renascerNaGaleria = new HashSet<>();
    private final Map<UUID, Integer> vitorias = new HashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();

    protected Evento(EventosPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
        this.arquivoPendentes = new File(plugin.getDataFolder(), id + "-pendentes.yml");
        this.arquivoVitorias = new File(plugin.getDataFolder(), id + "-vitorias.yml");
        carregarVitorias();
    }

    // ---------------------------------------------------------------- o que cada evento define

    /** "Vietnã". */
    public abstract String nome();

    /** O nome é feminino ("a Sobrevivência", "a Corrida Maluca")? Pros textos: "no"/"na", "do"/"da". */
    public boolean feminino() {
        return false;
    }

    /** "o Vietnã", "a Corrida Maluca". */
    public String oNome() {
        return (feminino() ? "a " : "o ") + nome();
    }

    /** "no Vietnã", "na Corrida Maluca". */
    public String noNome() {
        return (feminino() ? "na " : "no ") + nome();
    }

    /** "do Vietnã", "da Corrida Maluca". */
    public String doNome() {
        return (feminino() ? "da " : "do ") + nome();
    }

    /** O título dos anúncios ("§2§l☘ VIETNÃ ☘"). */
    protected abstract String titulo();

    /** A cor das mensagens ("§a"). */
    protected abstract String cor();

    /** O nome com cor e símbolo, pra agenda do /eventos ("§a☘ Vietnã"). */
    public abstract String rotulo();

    /** O que se faz no evento, pro anúncio da abertura e pro /<id> info. */
    public abstract String descricao();

    /** Recarrega a arena do config. */
    public abstract void recarregar();

    public abstract World mundo();

    /** Onde se luta (pra quem sai, é eliminado). */
    public abstract boolean naArena(Location l);

    public abstract boolean naArquibancada(Location l);

    public abstract Location arquibancada();

    /** Caiu (na água): ver {@link #aoCair}. */
    public abstract boolean caiu(Location l);

    /** Quem luta caiu na água: nos eventos de "último de pé", está fora. A Corrida devolve pro checkpoint. */
    public void aoCair(Player p) {
        eliminar(p, "caiu na água", false);
    }

    /** Constrói a arena inteira (o prédio todo: uma vez, pela staff ou pelo montar-mapa). */
    public abstract void construir();

    /** Deixa a arena pronta pra outra luta (os pilares ou os pisos de novo), sem refazer o prédio. */
    protected abstract void resetar();

    /** [min, max] da caixa pro AreasPlugin. */
    public abstract int[][] caixa();

    /** Um lugar de começo pra cada participante (pode repetir se faltar). */
    protected abstract List<Location> pontosDeEntrada(int quantos);

    protected abstract void darKit(Player p);

    /** Quando todos já estão na arena, antes dos segundos de preparação (a Corrida: a contagem regressiva). */
    protected void aoComecarPreparacao(int segundos) {
    }

    /** Quando a luta começa (depois do kit). */
    protected void aoComecarLuta(long agora) {
    }

    /** A cada segundo da luta (o que some com o tempo, reabastecer...). */
    protected void tickLuta(long agora) {
    }

    /** A cada 2 ticks da luta (o Corre ou Cai: o chão debaixo de cada um). */
    protected void tickRapidoLuta(long agora) {
    }

    /** Chamado pelo plugin a cada 2 ticks. */
    public void tickRapido() {
        if (fase == Fase.LUTA) tickRapidoLuta(System.currentTimeMillis());
    }

    /** Um projétil de outro participante acertou: o dano já foi cancelado (o Vietnã empurra). */
    public void aoAcertar(Player vitima, Projectile projetil) {
    }

    /** Um detalhe da luta pro /<evento> (a Sobrevivência: a onda), ou null. */
    public String detalheLuta() {
        return null;
    }

    /** Os mobs machucam quem luta de verdade (a Sobrevivência)? Nos outros, ninguém perde vida. */
    public boolean danoDeMobs() {
        return false;
    }

    /** Quem luta pode acertar essa entidade (que não é participante)? (a Sobrevivência: os mobs dela) */
    public boolean podeAcertar(org.bukkit.entity.Entity alvo) {
        return false;
    }

    /**
     * Acaba quando sobra um de pé? (os eventos de "último de pé"). Na Sobrevivência, que é de todos
     * juntos, não: ela acaba quando a última onda cai ou quando todos morrem.
     */
    protected boolean acabaComUmDePe() {
        return true;
    }

    /** Quem luta pode quebrar esse bloco? (o Spleef: a neve do piso) */
    public boolean podeQuebrar(Player p, org.bukkit.block.Block bloco) {
        return false;
    }

    // ---------------------------------------------------------------- consultas

    public String id() {
        return id;
    }

    public ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection(id);
    }

    public Fase getFase() {
        return fase;
    }

    public Map<UUID, Participante> getParticipantes() {
        return participantes;
    }

    /** Participante vivo de um evento em preparação ou luta. */
    public Participante lutando(UUID jogador) {
        if (fase != Fase.PREPARACAO && fase != Fase.LUTA) return null;
        Participante p = participantes.get(jogador);
        return p != null && p.vivo ? p : null;
    }

    public boolean teleporteLiberado(UUID jogador) {
        return teleporteLiberado.contains(jogador);
    }

    public double premio() {
        return Math.max(0, cfg().getDouble("premio"));
    }

    /** O prêmio, pro /<evento> info. */
    public String textoPremio() {
        return "O último de pé leva um cheque de §f" + Formato.valor(premio()) + "§7.";
    }

    protected long inicioLuta() {
        return inicioLuta;
    }

    public List<Map.Entry<UUID, Integer>> ranking(int quantos) {
        List<Map.Entry<UUID, Integer>> lista = new ArrayList<>(vitorias.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        return lista.subList(0, Math.min(quantos, lista.size()));
    }

    /** As vitórias de cada um neste evento (pro ranking geral). */
    public Map<UUID, Integer> vitorias() {
        return Map.copyOf(vitorias);
    }

    public String nomeDe(UUID jogador) {
        String nome = nomes.get(jogador);
        return nome != null ? nome : Bukkit.getOfflinePlayer(jogador).getName();
    }

    protected String prefixo() {
        return cor() + "[" + nome() + "]";
    }

    // ---------------------------------------------------------------- inscrição

    public void abrir() {
        long duracao = Math.max(10, cfg().getInt("inscricao.duracao-segundos")) * 1000L;
        fase = Fase.INSCRICAO;
        participantes.clear();
        avisosDados.clear();
        fimInscricao = System.currentTimeMillis() + duracao;
        // curto: como funciona fica no /<evento> e no tutorial do Discord
        String abertura = titulo() + " " + cor() + "começa em §f" + Formato.tempo(duracao) + cor() + "! Prêmio: §f"
                + Formato.valor(premio()) + cor() + ". Inventário vazio e §f/" + id + " entrar §7(como funciona: /" + id + ")";
        anunciar(abertura);
        plugin.getDiscord().enviar("eventos", abertura);
    }

    /** null se entrou; senão, o motivo. */
    public String entrar(Player p) {
        if (fase != Fase.INSCRICAO) return "Não há inscrições abertas " + noNome() + " agora.";
        if (participantes.containsKey(p.getUniqueId())) return "Você já está inscrito. Pra sair: /" + id + " sair";
        Evento outro = plugin.eventoInscrito(p.getUniqueId());
        if (outro != null && outro != this) return "Você já está " + outro.noNome() + ".";
        if (!inventarioVazio(p)) return "Pra entrar, o inventário precisa estar totalmente vazio, sem armadura e sem nada na outra mão.";
        if (p.getWorld().equals(mundo())) return "Saia do mundo das arenas pra se inscrever.";
        String ip = ip(p);
        if (!p.hasPermission("eventos.ip.ignorar")) {
            for (Participante part : participantes.values()) {
                if (part.ip.equals(ip)) return "Já tem uma inscrição desta conexão (" + part.nome + "): é uma por IP.";
            }
        }
        participantes.put(p.getUniqueId(), new Participante(p.getName(), ip, p.getLocation()));
        anunciar(prefixo() + " §f" + p.getName() + " " + cor() + "entrou. §7(" + participantes.size() + " inscrito(s))");
        return null;
    }

    public boolean sair(Player p) {
        if (fase != Fase.INSCRICAO || participantes.remove(p.getUniqueId()) == null) return false;
        anunciar(prefixo() + " §f" + p.getName() + " §7saiu. (" + participantes.size() + " inscrito(s))");
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
        for (UUID jogador : new ArrayList<>(participantes.keySet())) {
            Player p = Bukkit.getPlayer(jogador);
            String motivo = p == null ? null : !inventarioVazio(p) ? "o inventário não está mais vazio"
                    : p.getWorld().equals(mundo()) ? "você está no mundo das arenas" : null;
            if (p == null || motivo != null) {
                participantes.remove(jogador);
                if (p != null) p.sendMessage("§c" + prefixo() + " §cVocê ficou de fora: " + motivo + ".");
            }
        }
        // 1 só serve pra testar sozinho (servidor de teste); em produção, 3 ou mais
        int minimo = Math.max(1, cfg().getInt("minimo-jogadores"));
        if (participantes.size() < minimo) {
            cancelar("só " + participantes.size() + " inscrito(s), precisa de " + minimo);
            return;
        }
        if (mundo() == null) {
            cancelar("o mundo da arena não existe");
            return;
        }
        resetar();
        List<Location> pontos = new ArrayList<>(pontosDeEntrada(participantes.size()));
        Collections.shuffle(pontos);
        int i = 0;
        for (UUID jogador : participantes.keySet()) {
            Player p = Bukkit.getPlayer(jogador);
            if (p == null) continue;
            teleportar(p, pontos.get(i++ % pontos.size()));
            restaurar(p);
        }
        salvarPendentes();

        fase = Fase.PREPARACAO;
        int preparacao = Math.max(0, cfg().getInt("luta.preparacao-segundos"));
        anunciar(titulo() + " " + cor() + "Começou com §f" + participantes.size() + " " + cor() + "jogador(es)! Assista: §f/" + id + " assistir");
        aoComecarPreparacao(preparacao);
        Bukkit.getScheduler().runTaskLater(plugin, this::comecarLuta, preparacao * 20L);
    }

    private void comecarLuta() {
        if (fase != Fase.PREPARACAO) return;
        fase = Fase.LUTA;
        long agora = System.currentTimeMillis();
        inicioLuta = agora;
        for (UUID jogador : vivos()) {
            Player p = Bukkit.getPlayer(jogador);
            if (p == null) continue;
            darKit(p);
            p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(cor() + "§lVALENDO!"));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
        }
        aoComecarLuta(agora);
        verificarVencedor();
    }

    protected static void restaurar(Player p) {
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

    private static void tirarKit(Player p) {
        p.getInventory().clear();
        p.setItemOnCursor(null);
        restaurar(p);
    }

    // ---------------------------------------------------------------- luta

    /** Caiu, saiu da arena ou desconectou: perde o kit e vai pra arquibancada (ou volta, se desconectou). */
    public void eliminar(Player p, String motivo, boolean desconectou) {
        Participante part = lutando(p.getUniqueId());
        if (part == null) return;
        part.vivo = false;
        tirarKit(p);
        if (desconectou) {
            teleportar(p, part.retorno);
        } else {
            teleportar(p, arquibancada());
            p.sendMessage("§7Você está na arquibancada. Pra sair: §f/spawn§7.");
        }
        removerPendente(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 0.8f);
        anunciar(prefixo() + " §f" + part.nome + " §7" + motivo + ". " + cor() + "Restam §f" + vivos().size() + cor() + ".");
        verificarVencedor();
    }

    /** Morreu na luta (a Sobrevivência): está fora, e renasce na galeria (o kit não cai: ver EventosListener). */
    public void eliminarPorMorte(Player p) {
        Participante part = lutando(p.getUniqueId());
        if (part == null) return;
        part.vivo = false;
        renascerNaGaleria.add(p.getUniqueId());
        removerPendente(p.getUniqueId());
        anunciar(prefixo() + " §f" + part.nome + " §7morreu. " + cor() + "Restam §f" + vivos().size() + cor() + ".");
        verificarVencedor();
    }

    /** Onde quem morreu na luta renasce (a galeria), uma vez; null se não é o caso. */
    public Location tirarRenascer(UUID jogador) {
        return renascerNaGaleria.remove(jogador) ? arquibancada() : null;
    }

    /** Pra onde vai quem sai da pista sem perder (retirar): a arquibancada; na Corrida, a da chegada. */
    protected Location localDeQuemSai() {
        return arquibancada();
    }

    /** Saiu da luta sem ser eliminado (a Corrida: cruzou a chegada): perde o kit e vai pra {@link #localDeQuemSai()}. */
    protected void retirar(Player p) {
        Participante part = lutando(p.getUniqueId());
        if (part == null) return;
        part.vivo = false;
        tirarKit(p);
        teleportar(p, localDeQuemSai());
        removerPendente(p.getUniqueId());
        verificarVencedor();
    }

    private void verificarVencedor() {
        if (fase != Fase.LUTA) return;
        List<UUID> vivos = vivos();
        // sozinho (mínimo 1, no teste), não vence na hora: joga até cair ou o tempo acabar
        if (vivos.isEmpty() || (acabaComUmDePe() && vivos.size() == 1 && participantes.size() > 1)) finalizar(vivos);
    }

    protected List<UUID> vivos() {
        return participantes.entrySet().stream().filter(e -> e.getValue().vivo).map(Map.Entry::getKey).toList();
    }

    protected List<Player> jogadoresVivos() {
        return vivos().stream().map(Bukkit::getPlayer).filter(p -> p != null).toList();
    }

    /** Termina e paga o prêmio dividido entre os ganhadores (vazio = ninguém). */
    protected void finalizar(List<UUID> ganhadores) {
        fase = Fase.FIM;
        if (ganhadores.isEmpty()) {
            anunciar(titulo() + " §7Ninguém sobrou de pé.");
        } else {
            double parte = premio() / ganhadores.size();
            List<String> nomesGanhadores = new ArrayList<>();
            for (UUID jogador : ganhadores) {
                Participante part = participantes.get(jogador);
                nomesGanhadores.add(part.nome);
                vitorias.merge(jogador, 1, Integer::sum);
                nomes.put(jogador, part.nome);
                Player p = Bukkit.getPlayer(jogador);
                if (p != null) {
                    tirarKit(p);
                    teleportar(p, part.retorno);
                    removerPendente(jogador);
                    plugin.getPremio().pagar(p, parte, "Evento " + nome());
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
            }
            salvarVitorias();
            String resultado = titulo() + " §f" + String.join(", ", nomesGanhadores) + " " + cor() + "§l"
                    + (ganhadores.size() > 1 ? (acabaComUmDePe() ? "venceram (tempo esgotado)" : "sobreviveram") : (acabaComUmDePe() ? "venceu" : "sobreviveu")) + cor() + "! Prêmio: cheque de §f"
                    + Formato.valor(parte) + (ganhadores.size() > 1 ? " " + cor() + "pra cada um" : "") + cor() + ".";
            anunciar(resultado);
            plugin.getDiscord().enviar("eventos", resultado);
        }
        // a arena volta inteira pro próximo
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fase == Fase.FIM) {
                resetar();
                fase = null;
                participantes.clear();
            }
        }, 40L);
    }

    /**
     * Termina pagando por colocação (a Corrida): o 1º da lista leva o 1º prêmio, e assim por diante; só o 1º
     * conta vitória. Quem ainda estava na luta volta sem o kit. Quem saiu do servidor recebe na conta.
     */
    protected void finalizarPorColocacao(List<UUID> colocados, List<Double> premios) {
        fase = Fase.FIM;
        for (UUID jogador : vivos()) {
            Player p = Bukkit.getPlayer(jogador);
            if (p == null) continue;
            tirarKit(p);
            teleportar(p, participantes.get(jogador).retorno);
            removerPendente(jogador);
        }
        if (colocados.isEmpty()) {
            anunciar(titulo() + " §7Ninguém chegou ao fim.");
        } else {
            List<String> podio = new ArrayList<>();
            for (int i = 0; i < colocados.size(); i++) {
                UUID jogador = colocados.get(i);
                Participante part = participantes.get(jogador);
                double valor = i < premios.size() ? Math.max(0, premios.get(i)) : 0;
                podio.add("§f" + (i + 1) + "º " + part.nome + (valor > 0 ? " §7(" + Formato.valor(valor) + ")" : ""));
                if (i == 0) {
                    vitorias.merge(jogador, 1, Integer::sum);
                    nomes.put(jogador, part.nome);
                }
                Player p = Bukkit.getPlayer(jogador);
                if (p != null) {
                    if (naArquibancada(p.getLocation())) teleportar(p, part.retorno);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
                plugin.getPremio().pagar(jogador, valor, "Evento " + nome());
            }
            salvarVitorias();
            String resultado = titulo() + " " + String.join(cor() + ", ", podio) + cor() + "! Prêmios em cheque.";
            anunciar(resultado);
            plugin.getDiscord().enviar("eventos", resultado);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fase == Fase.FIM) {
                resetar();
                fase = null;
                participantes.clear();
            }
        }, 40L);
    }

    /** Pela staff, por falta de gente ou no desligamento. Quem estava na arena volta (sem o kit). */
    public void cancelar(String motivo) {
        if (fase == null) return;
        boolean naArena = fase == Fase.PREPARACAO || fase == Fase.LUTA;
        if (naArena) {
            for (UUID jogador : vivos()) {
                Player p = Bukkit.getPlayer(jogador);
                if (p == null) continue;
                tirarKit(p);
                teleportar(p, participantes.get(jogador).retorno);
                removerPendente(jogador);
            }
        }
        fase = null;
        participantes.clear();
        anunciar(titulo() + " §cCancelado: " + motivo + ".");
        if (naArena) resetar();
    }

    /** A cada segundo: avisos e fim das inscrições, quem saiu da arena, o que é do evento e o tempo máximo. */
    public void tick() {
        if (fase == null) return;
        long agora = System.currentTimeMillis();
        if (fase == Fase.INSCRICAO) {
            long falta = (fimInscricao - agora) / 1000;
            for (int s : cfg().getIntegerList("inscricao.avisos-segundos")) {
                if (falta <= s && falta > s - 2 && avisosDados.add(s)) {
                    anunciar(prefixo() + " " + cor() + "Começa em §f" + Formato.tempo(s * 1000L) + cor() + " (§f"
                            + participantes.size() + " " + cor() + "inscrito(s)): §f/" + id + " entrar");
                }
            }
            if (agora >= fimInscricao) iniciar();
            return;
        }
        if (fase != Fase.LUTA) return;

        for (Player p : jogadoresVivos()) {
            if (!naArena(p.getLocation())) eliminar(p, "saiu da arena", false);
        }
        if (fase != Fase.LUTA) return;
        tickLuta(agora);
        long maximo = Math.max(1, cfg().getInt("luta.tempo-maximo-minutos")) * 60_000L;
        if (fase == Fase.LUTA && agora - inicioLuta > maximo) finalizar(vivos());
    }

    // ---------------------------------------------------------------- assistir e volta

    public void assistir(Player p) {
        teleportar(p, arquibancada());
    }

    /** Ao entrar no servidor: quem estava lutando quando caiu perde o kit e volta pra onde estava. */
    public void aoEntrar(Player p) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoPendentes);
        Location volta = yaml.getLocation(p.getUniqueId().toString());
        if (volta == null || lutando(p.getUniqueId()) != null) return;
        tirarKit(p);
        teleportar(p, volta);
        removerPendente(p.getUniqueId());
        p.sendMessage("§7" + prefixo() + " §7Você saiu no meio do evento: voltou pra onde estava, sem o kit.");
    }

    private void salvarPendentes() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoPendentes);
        participantes.forEach((jogador, part) -> yaml.set(jogador.toString(), part.retorno));
        salvar(yaml, arquivoPendentes);
    }

    private void removerPendente(UUID jogador) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoPendentes);
        if (!yaml.contains(jogador.toString())) return;
        yaml.set(jogador.toString(), null);
        salvar(yaml, arquivoPendentes);
    }

    private void carregarVitorias() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoVitorias);
        for (String chave : yaml.getKeys(false)) {
            try {
                UUID jogador = UUID.fromString(chave);
                vitorias.put(jogador, yaml.getInt(chave + ".vitorias"));
                nomes.put(jogador, yaml.getString(chave + ".nome"));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido ignorado em " + arquivoVitorias.getName() + ": " + chave);
            }
        }
    }

    private void salvarVitorias() {
        YamlConfiguration yaml = new YamlConfiguration();
        vitorias.forEach((jogador, v) -> {
            yaml.set(jogador + ".nome", nomes.get(jogador));
            yaml.set(jogador + ".vitorias", v);
        });
        salvar(yaml, arquivoVitorias);
        plugin.getRanking().atualizarHolograma();
    }

    private void salvar(YamlConfiguration yaml, File arquivo) {
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar " + arquivo.getName(), e);
        }
    }

    protected static void anunciar(String mensagem) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
    }
}
