package com.example.recursos;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mundo de recursos: /recursos leva a um ponto aleatório seguro, e o mundo é resetado de tempos
 * em tempos (ResetManager). Quem saiu do servidor lá dentro e volta depois de um reset vai pra um
 * lugar seguro do mundo novo.
 */
public class RecursosPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ResetManager resetManager;
    private Teleporte teleporte;
    private NamespacedKey chaveCiclo;
    private final Map<UUID, Long> ultimoTeleporte = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.chaveCiclo = new NamespacedKey(this, "ciclo-recursos");
        this.resetManager = new ResetManager(this);
        this.teleporte = new Teleporte(this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("recursos").setExecutor(this);
        getCommand("recursos").setTabCompleter(this);
        getServer().getScheduler().runTaskTimer(this, resetManager::tick, 20L, 20L);
        // a borda do config vale desde já (o mundo pode ter sido criado antes do plugin)
        getServer().getScheduler().runTask(this, () -> {
            World mundo = getMundo();
            if (mundo == null) {
                getLogger().warning("O mundo \"" + getNomeMundo() + "\" não existe. Crie com: mv create " + getNomeMundo() + " normal");
            } else {
                resetManager.aplicarBorda(mundo);
            }
        });
    }

    public String getNomeMundo() {
        return getConfig().getString("mundo", "recursos");
    }

    public World getMundo() {
        return Bukkit.getWorld(getNomeMundo());
    }

    /** Pra onde vai quem é tirado do mundo de recursos: {@code saida.mundo}, ou o mundo principal. */
    public World mundoDeSaida() {
        World saida = Bukkit.getWorld(getConfig().getString("saida.mundo", ""));
        return saida != null ? saida : Bukkit.getWorlds().get(0);
    }

    // ---------------------------------------------------------------------
    // Quem estava no mundo antigo
    // ---------------------------------------------------------------------
    /**
     * Quem sai do servidor dentro do mundo de recursos fica marcado com o ciclo. Depois de um reset,
     * o servidor não reconhece mais aquele mundo e põe o jogador no mundo principal, nas mesmas
     * coordenadas (que podem ser dentro de uma montanha): por isso o resgate vale onde quer que ele
     * tenha aparecido.
     */
    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        Player p = evento.getPlayer();
        if (p.getWorld().equals(getMundo())) {
            p.getPersistentDataContainer().set(chaveCiclo, PersistentDataType.INTEGER, resetManager.getCiclo());
        } else {
            p.getPersistentDataContainer().remove(chaveCiclo);
        }
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player p = evento.getPlayer();
        Integer cicloQueSaiu = p.getPersistentDataContainer().get(chaveCiclo, PersistentDataType.INTEGER);
        if (cicloQueSaiu == null) return;
        p.getPersistentDataContainer().remove(chaveCiclo);
        if (cicloQueSaiu == resetManager.getCiclo() || getMundo() == null) return;
        teleporte.levar(p).thenAccept(ok -> {
            if (!ok) p.teleport(mundoDeSaida().getSpawnLocation());
        });
        // 1 s depois, pra não se perder entre as mensagens de entrada
        getServer().getScheduler().runTaskLater(this, () -> p.sendMessage(
                "§eO mundo de recursos foi resetado enquanto você estava fora, então você foi levado pra um lugar seguro dele."), 20L);
    }

    // ---------------------------------------------------------------------
    // /recursos
    // ---------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "ir" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "admin" -> admin(sender, args);
            default -> ir(sender);
        }
        return true;
    }

    private void ir(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSó jogadores vão pro mundo de recursos.");
            return;
        }
        if (getMundo() == null) {
            p.sendMessage("§cO mundo de recursos não existe no momento.");
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
        p.sendMessage("§7Procurando um lugar seguro no mundo de recursos...");
        teleporte.levar(p).thenAccept(ok -> {
            if (ok) {
                p.sendMessage("§aBem-vindo ao mundo de recursos! §7Ele é resetado de tempos em tempos: §f/recursos info§7.");
            } else {
                ultimoTeleporte.remove(p.getUniqueId());
                p.sendMessage("§cNão achei um lugar seguro. Tente de novo.");
            }
        });
    }

    private void info(CommandSender sender) {
        long proximo = resetManager.proximo();
        sender.sendMessage("§6§l--- Mundo de recursos ---");
        sender.sendMessage("§7Próximo reset: §f" + DATA.format(Instant.ofEpochMilli(proximo).atZone(ZoneId.systemDefault()))
                + " §7(em " + tempo(proximo - System.currentTimeMillis()) + ")"
                + (resetManager.getMarcadoPara() > 0 ? " §e(marcado pela staff)" : ""));
        sender.sendMessage("§7No reset, tudo o que estiver lá some. Construa no seu terreno (§f/terreno§7).");
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("recursos.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String acao = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (acao) {
            case "resetar" -> {
                if (args.length >= 3 && args[2].equalsIgnoreCase("agora")) {
                    sender.sendMessage(resetManager.resetar() ? "§aMundo de recursos resetado." : "§cO reset falhou; veja o console.");
                    return;
                }
                int minutos = 5;
                if (args.length >= 3) {
                    try {
                        minutos = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cUso: §f/recursos admin resetar [minutos|agora]");
                        return;
                    }
                }
                resetManager.marcar(System.currentTimeMillis() + minutos * 60_000L);
                sender.sendMessage("§aReset marcado pra daqui a §f" + minutos + " min§a, com os avisos no chat.");
            }
            case "cancelar" -> {
                resetManager.cancelarMarcado();
                sender.sendMessage("§aReset marcado cancelado. §7O automático continua: §f/recursos info");
            }
            case "levar" -> {
                // pro portal do spawn: um bloco de comando roda "recursos admin levar @p[distance=..2]"
                if (args.length < 3) {
                    sender.sendMessage("§cUso: §f/recursos admin levar <jogador|seletor>");
                    return;
                }
                List<Player> alvos;
                try {
                    alvos = Bukkit.selectEntities(sender, args[2]).stream().filter(e -> e instanceof Player).map(e -> (Player) e).toList();
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cJogador ou seletor inválido: §f" + args[2]);
                    return;
                }
                for (Player alvo : alvos) {
                    ultimoTeleporte.remove(alvo.getUniqueId());
                    ir(alvo);
                }
            }
            case "reload" -> {
                reloadConfig();
                World mundo = getMundo();
                if (mundo != null) resetManager.aplicarBorda(mundo);
                sender.sendMessage("§aConfig recarregado e borda reaplicada.");
            }
            default -> sender.sendMessage("§cUso: §f/recursos admin resetar [minutos|agora] §7| §fcancelar §7| §flevar <jogador> §7| §freload");
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
        List<String> opcoes = args.length == 1 ? (sender.hasPermission("recursos.admin") ? List.of("ir", "info", "admin") : List.of("ir", "info"))
                : args.length == 2 && args[0].equalsIgnoreCase("admin") ? List.of("resetar", "cancelar", "levar", "reload")
                : args.length == 3 && args[1].equalsIgnoreCase("resetar") ? List.of("agora", "5", "30") : List.of();
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.startsWith(prefixo)).toList();
    }
}
