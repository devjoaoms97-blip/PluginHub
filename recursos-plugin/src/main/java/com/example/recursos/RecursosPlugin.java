package com.example.recursos;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Mundos de recursos: o normal ({@code /recursos}), o Nether ({@code /nether}) e o End
 * ({@code /end}). Cada comando leva a um ponto seguro do mundo, e cada mundo é resetado uma vez por
 * mês, num dia diferente ({@link ResetManager}). Quem saiu do servidor num desses mundos e volta
 * depois do reset vai pra um lugar seguro do mundo novo. Os portais não funcionam nesses mundos (só
 * se entra por comando); o portal de saída do End leva pro spawn. {@code /mapa} dá um mapa de
 * localização de cada mundo ({@link Mapas}), e o centro do End tem as regras dele ({@link CentroDoEnd}).
 */
public class RecursosPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** mundo → reset dele, na ordem do config. */
    private final Map<String, ResetManager> resets = new LinkedHashMap<>();
    private Teleporte teleporte;
    private CentroDoEnd centroDoEnd;
    private Mapas mapas;
    private File arquivoDados;
    private NamespacedKey chaveCiclo;
    private NamespacedKey chaveCicloAntiga;
    private final Map<UUID, Long> ultimoTeleporte = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        atualizarConfig();
        this.chaveCiclo = new NamespacedKey(this, "ultimo-mundo");
        this.chaveCicloAntiga = new NamespacedKey(this, "ciclo-recursos");
        this.arquivoDados = new File(getDataFolder(), "dados.yml");
        this.teleporte = new Teleporte(this);
        this.centroDoEnd = new CentroDoEnd(this);
        carregarDados();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(centroDoEnd, this);
        for (String comando : List.of("recursos", "nether", "end", "mapa")) {
            getCommand(comando).setExecutor(this);
            getCommand(comando).setTabCompleter(this);
        }
        getServer().getScheduler().runTaskTimer(this, () -> {
            resets.values().forEach(ResetManager::tick);
            centroDoEnd.tick();
        }, 20L, 20L);
        // os mundos podem ter sido carregados antes deste plugin
        getServer().getScheduler().runTask(this, () -> {
            for (ResetManager m : resets.values()) {
                World w = m.getWorld();
                if (w == null) {
                    getLogger().warning("O mundo \"" + m.getMundo() + "\" não existe. Crie com o configurar-mundos.ps1 do servidor de teste.");
                } else {
                    prepararMundo(w);
                }
            }
        });
    }

    /**
     * Config de antes do Nether e do End: entram as seções novas ({@code mundos}, {@code end}) com os
     * valores padrão, e saem as chaves que não valem mais (o mundo único, o reset a cada N dias, a
     * borda e o raio, que agora são de cada mundo).
     */
    private void atualizarConfig() {
        if (getConfig().contains("mundos", true) && getConfig().contains("end", true)) return; // true: sem olhar os padrões do jar
        for (String velha : List.of("mundo", "reset.a-cada-dias", "borda", "teleporte.raio")) getConfig().set(velha, null);
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig(); // senão a seção que só existia nos padrões volta vazia
        getLogger().info("config.yml atualizado pros mundos de recursos, Nether e End.");
    }

    /** Lê os mundos do config e o estado de cada um (último reset, ciclo) do {@code dados.yml}. */
    private void carregarDados() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivoDados);
        boolean novo = !arquivoDados.exists();
        resets.clear();
        ConfigurationSection mundos = getConfig().getConfigurationSection("mundos");
        if (mundos != null) {
            for (String nome : mundos.getKeys(false)) {
                // dados.yml de antes do Nether e do End: o estado ficava na raiz, e era do "recursos"
                boolean antigo = nome.equals("recursos") && yaml.contains("ultimo-reset") && !yaml.contains("mundos.recursos");
                String base = antigo ? "" : "mundos." + nome + ".";
                resets.put(nome, new ResetManager(this, nome, yaml.getLong(base + "ultimo-reset", System.currentTimeMillis()),
                        yaml.getInt(base + "ciclo", 1)));
            }
        }
        Map<String, Integer> ids = new HashMap<>();
        ConfigurationSection secaoMapas = yaml.getConfigurationSection("mapas");
        if (secaoMapas != null) for (String nome : secaoMapas.getKeys(false)) ids.put(nome, secaoMapas.getInt(nome));
        this.mapas = new Mapas(this, ids);
        if (novo || yaml.contains("ultimo-reset")) salvarDados(); // a contagem começa na instalação
    }

    public void salvarDados() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ResetManager m : resets.values()) {
            yaml.set("mundos." + m.getMundo() + ".ultimo-reset", m.getUltimoReset());
            yaml.set("mundos." + m.getMundo() + ".ciclo", m.getCiclo());
        }
        if (mapas != null) mapas.getIds().forEach((mundo, id) -> yaml.set("mapas." + mundo, id));
        try {
            yaml.save(arquivoDados);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Não foi possível salvar dados.yml", e);
        }
    }

    /** Borda e, no End, o centro. Quando liga, depois de cada reset e quando o Multiverse carrega o mundo. */
    public void prepararMundo(World w) {
        ResetManager m = resets.get(w.getName());
        if (m == null) return;
        m.aplicarBorda(w);
        centroDoEnd.preparar(w);
    }

    @EventHandler
    public void aoCarregarMundo(WorldLoadEvent evento) {
        prepararMundo(evento.getWorld());
    }

    public Map<String, ResetManager> getResets() {
        return resets;
    }

    public CentroDoEnd getCentroDoEnd() {
        return centroDoEnd;
    }

    /** Pra onde vai quem é tirado de um mundo de recursos: {@code saida.mundo}, ou o mundo principal. */
    public World mundoDeSaida() {
        World saida = Bukkit.getWorld(getConfig().getString("saida.mundo", ""));
        return saida != null ? saida : Bukkit.getWorlds().get(0);
    }

    private ResetManager porComando(String comando) {
        for (ResetManager m : resets.values()) if (m.getComando().equalsIgnoreCase(comando)) return m;
        return null;
    }

    private ResetManager porNome(String nome) {
        if (nome == null) return null;
        ResetManager m = resets.get(nome);
        return m != null ? m : porComando(nome);
    }

    // ---------------------------------------------------------------------
    // Quem estava no mundo antigo
    // ---------------------------------------------------------------------
    /**
     * Quem sai do servidor dentro de um mundo de recursos fica marcado com o mundo e o ciclo dele.
     * Depois de um reset, o servidor não reconhece mais aquele mundo e põe o jogador no mundo
     * principal, nas mesmas coordenadas (que podem ser dentro de uma montanha): por isso o resgate
     * vale onde quer que ele tenha aparecido.
     */
    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        Player p = evento.getPlayer();
        p.getPersistentDataContainer().remove(chaveCicloAntiga);
        ResetManager m = resets.get(p.getWorld().getName());
        if (m != null) {
            p.getPersistentDataContainer().set(chaveCiclo, PersistentDataType.STRING, m.getMundo() + ":" + m.getCiclo());
        } else {
            p.getPersistentDataContainer().remove(chaveCiclo);
        }
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player p = evento.getPlayer();
        String marca = p.getPersistentDataContainer().get(chaveCiclo, PersistentDataType.STRING);
        Integer antiga = p.getPersistentDataContainer().get(chaveCicloAntiga, PersistentDataType.INTEGER);
        if (marca == null && antiga != null) marca = "recursos:" + antiga; // marca de antes do Nether e do End
        p.getPersistentDataContainer().remove(chaveCiclo);
        p.getPersistentDataContainer().remove(chaveCicloAntiga);
        if (marca == null || !marca.contains(":")) return;
        ResetManager m = resets.get(marca.substring(0, marca.lastIndexOf(':')));
        if (m == null || m.getWorld() == null || marca.endsWith(":" + m.getCiclo())) return;
        teleporte.levar(p, m).thenAccept(ok -> {
            if (!ok) p.teleport(mundoDeSaida().getSpawnLocation());
        });
        // 1 s depois, pra não se perder entre as mensagens de entrada
        getServer().getScheduler().runTaskLater(this, () -> p.sendMessage(
                "§eO " + m.getNome() + " foi resetado enquanto você estava fora, então você foi levado pra um lugar seguro dele."), 20L);
    }

    // ---------------------------------------------------------------------
    // Portais: só se entra por comando
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void aoUsarPortal(PlayerPortalEvent evento) {
        World de = evento.getFrom().getWorld();
        if (de == null || !resets.containsKey(de.getName())) return;
        if (de.getEnvironment() == World.Environment.THE_END && evento.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            evento.setTo(mundoDeSaida().getSpawnLocation()); // o portal de saída do End
            return;
        }
        evento.setCancelled(true);
        evento.getPlayer().sendMessage("§cOs portais não funcionam aqui. Use §f/nether §cou §f/end§c.");
    }

    @EventHandler(ignoreCancelled = true)
    public void aoEntidadeUsarPortal(EntityPortalEvent evento) {
        World de = evento.getFrom().getWorld();
        if (de != null && resets.containsKey(de.getName())) evento.setCancelled(true);
    }

    // ---------------------------------------------------------------------
    // /recursos, /nether, /end, /mapa
    // ---------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("mapa")) {
            mapa(sender, args);
            return true;
        }
        ResetManager m = porComando(cmd);
        String sub = args.length == 0 ? "ir" : args[0].toLowerCase(Locale.ROOT);
        if (cmd.equals("recursos") && sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (m == null) {
            sender.sendMessage("§cEsse mundo não está no config.");
            return true;
        }
        if (sub.equals("info")) info(sender, m);
        else ir(sender, m);
        return true;
    }

    private void ir(CommandSender sender, ResetManager m) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSó jogadores vão pro " + m.getNome() + ".");
            return;
        }
        if (m.getWorld() == null) {
            p.sendMessage("§cO " + m.getNome() + " não existe no momento.");
            return;
        }
        long espera = Math.max(0, getConfig().getInt("teleporte.cooldown-segundos", 30)) * 1000L;
        Long ultimo = ultimoTeleporte.get(p.getUniqueId());
        long agora = System.currentTimeMillis();
        if (!p.hasPermission("recursos.admin") && ultimo != null && agora - ultimo < espera) {
            p.sendMessage("§cEspere §f" + ((espera - (agora - ultimo)) / 1000 + 1) + "s §cpra usar de novo.");
            return;
        }
        ultimoTeleporte.put(p.getUniqueId(), agora);
        p.sendMessage("§7Procurando um lugar seguro no " + m.getNome() + "...");
        teleporte.levar(p, m).thenAccept(ok -> {
            if (ok) {
                p.sendMessage("§aBem-vindo ao " + m.getNome() + "! §7Ele é resetado todo mês: §f/" + m.getComando() + " info§7. "
                        + "Pra se localizar: §f/mapa§7.");
            } else {
                ultimoTeleporte.remove(p.getUniqueId());
                p.sendMessage("§cNão achei um lugar seguro. Tente de novo.");
            }
        });
    }

    private void info(CommandSender sender, ResetManager m) {
        long proximo = m.proximo();
        sender.sendMessage("§6§l--- " + Character.toUpperCase(m.getNome().charAt(0)) + m.getNome().substring(1) + " ---");
        sender.sendMessage("§7Próximo reset: §f" + DATA.format(Instant.ofEpochMilli(proximo).atZone(ZoneId.systemDefault()))
                + " §7(em " + tempo(proximo - System.currentTimeMillis()) + ")"
                + (m.getMarcadoPara() > 0 ? " §e(marcado pela staff)" : ""));
        sender.sendMessage("§7No reset, tudo o que estiver lá some. Construa no seu terreno (§f/terreno§7).");
    }

    private void mapa(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSó jogadores pegam o mapa.");
            return;
        }
        ResetManager m = args.length > 0 ? porNome(args[0].toLowerCase(Locale.ROOT)) : resets.get(p.getWorld().getName());
        if (m == null) {
            p.sendMessage("§cUso: §f/mapa <recursos|nether|end>");
            return;
        }
        if (mapas.tem(p, m.getMundo())) {
            p.sendMessage("§eVocê já tem o mapa do " + m.getNome() + ". §7Segure ele na mão pra ver onde está.");
            return;
        }
        ItemStack item = mapas.item(m);
        p.getInventory().addItem(item).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
        p.sendMessage("§aVocê pegou o mapa do " + m.getNome() + ". §7Segure ele na mão, lá dentro, pra ver onde está.");
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("recursos.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String acao = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        // o mundo é opcional: /recursos admin resetar [mundo] [minutos|agora]
        int i = 2;
        ResetManager m = args.length > i ? porNome(args[i].toLowerCase(Locale.ROOT)) : null;
        if (m != null) i++;
        else m = resets.get("recursos") != null ? resets.get("recursos") : resets.values().stream().findFirst().orElse(null);
        if (m == null && !acao.equals("reload")) {
            sender.sendMessage("§cNenhum mundo no config.");
            return;
        }
        switch (acao) {
            case "resetar" -> {
                if (args.length > i && args[i].equalsIgnoreCase("agora")) {
                    sender.sendMessage(m.resetar() ? "§aReset do " + m.getMundo() + " começou: o aviso sai no chat quando terminar." : "§cO reset não começou; veja o console.");
                    return;
                }
                int minutos = 5;
                if (args.length > i) {
                    try {
                        minutos = Math.max(1, Integer.parseInt(args[i]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cUso: §f/recursos admin resetar [mundo] [minutos|agora]");
                        return;
                    }
                }
                m.marcar(System.currentTimeMillis() + minutos * 60_000L);
                sender.sendMessage("§aReset do " + m.getMundo() + " marcado pra daqui a §f" + minutos + " min§a, com os avisos no chat.");
            }
            case "cancelar" -> {
                m.cancelarMarcado();
                sender.sendMessage("§aReset marcado do " + m.getMundo() + " cancelado. §7O automático continua.");
            }
            case "levar" -> {
                // pro portal do spawn: um bloco de comando roda "recursos admin levar @p[distance=..2]"
                if (args.length <= i) {
                    sender.sendMessage("§cUso: §f/recursos admin levar [mundo] <jogador|seletor>");
                    return;
                }
                List<Player> alvos;
                try {
                    alvos = Bukkit.selectEntities(sender, args[i]).stream().filter(e -> e instanceof Player).map(e -> (Player) e).toList();
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cJogador ou seletor inválido: §f" + args[i]);
                    return;
                }
                for (Player alvo : alvos) {
                    ultimoTeleporte.remove(alvo.getUniqueId());
                    ir(alvo, m);
                }
            }
            case "dragao" -> {
                World w = null;
                for (ResetManager r : resets.values()) {
                    World rw = r.getWorld();
                    if (rw != null && rw.getEnvironment() == World.Environment.THE_END) w = rw;
                }
                String r = w == null ? "Nenhum End carregado." : centroDoEnd.renascerDragao(w, true);
                sender.sendMessage("§e" + r);
            }
            case "reload" -> {
                reloadConfig();
                carregarDados();
                for (ResetManager r : resets.values()) if (r.getWorld() != null) prepararMundo(r.getWorld());
                sender.sendMessage("§aConfig recarregado e mundos preparados de novo (borda e centro do End).");
            }
            default -> sender.sendMessage("§cUso: §f/recursos admin resetar [mundo] [minutos|agora] §7| §fcancelar [mundo] §7| "
                    + "§flevar [mundo] <jogador> §7| §fdragao §7| §freload");
        }
    }

    private static String tempo(long ms) {
        long min = Math.max(0, ms / 60_000);
        if (min >= 1440) return min / 1440 + "d " + (min % 1440) / 60 + "h";
        if (min >= 60) return min / 60 + "h " + min % 60 + "min";
        return min + "min";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        List<String> opcoes = new ArrayList<>();
        boolean admin = sender.hasPermission("recursos.admin");
        if (cmd.equals("mapa")) {
            if (args.length == 1) resets.values().forEach(m -> opcoes.add(m.getComando()));
        } else if (args.length == 1) {
            opcoes.addAll(List.of("ir", "info"));
            if (admin && cmd.equals("recursos")) opcoes.add("admin");
        } else if (admin && cmd.equals("recursos") && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) opcoes.addAll(List.of("resetar", "cancelar", "levar", "dragao", "reload"));
            else if (args.length == 3) {
                resets.values().forEach(m -> opcoes.add(m.getComando()));
                if (args[1].equalsIgnoreCase("resetar")) opcoes.addAll(List.of("agora", "5", "30"));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("resetar")) opcoes.addAll(List.of("agora", "5", "30"));
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.startsWith(prefixo)).toList();
    }
}
