package com.example.placar;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scoreboard lateral ("telinha" no canto da tela) com saldo, rank e progresso até o próximo,
 * clã, missões do dia, skill em uso, próximo boss e jogadores online. As linhas vêm do config com
 * placeholders; cada informação é lida dos outros plugins por reflexão (sem o plugin, a linha
 * mostra "—"). /placar liga e desliga pra quem quiser a tela limpa.
 */
public class PlacarPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private Economy economy;
    private final Map<UUID, Scoreboard> placares = new HashMap<>();
    private final Map<UUID, Integer> linhasUsadas = new HashMap<>();
    private final Set<UUID> desligados = new HashSet<>();
    private final Set<String> avisouFalha = new HashSet<>();
    private File arquivoDesligados;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();

        arquivoDesligados = new File(getDataFolder(), "desligados.yml");
        for (String id : YamlConfiguration.loadConfiguration(arquivoDesligados).getStringList("jogadores")) {
            try {
                desligados.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignorado) {
                // UUID inválido: descarta
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        long intervalo = Math.max(10, getConfig().getLong("intervalo-ticks", 40));
        getServer().getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::atualizar), 20L, intervalo);
        Bukkit.getOnlinePlayers().forEach(this::atualizar);
        getLogger().info("PlacarPlugin ativado!");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (placares.containsKey(p.getUniqueId())) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        placares.clear();
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        // um tick depois: os outros plugins já carregaram os dados do jogador (rank, missões)
        Bukkit.getScheduler().runTaskLater(this, () -> atualizar(evento.getPlayer()), 5L);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        placares.remove(evento.getPlayer().getUniqueId());
        linhasUsadas.remove(evento.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("placar.admin")) {
            reloadConfig();
            placares.clear();
            linhasUsadas.clear();
            Bukkit.getOnlinePlayers().forEach(this::atualizar);
            sender.sendMessage("§aConfig do placar recarregado.");
            return true;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("ver") && sender.hasPermission("placar.admin")) {
            // staff: as linhas como aparecem pra esse jogador agora
            Player alvo = Bukkit.getPlayerExact(args[1]);
            if (alvo == null) {
                sender.sendMessage("§cJogador offline.");
                return true;
            }
            sender.sendMessage(cores(getConfig().getString("titulo", "")));
            for (String linha : getConfig().getStringList("linhas")) sender.sendMessage(cores(preencher(linha, alvo)));
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores.");
            return true;
        }
        UUID id = jogador.getUniqueId();
        if (desligados.remove(id)) {
            jogador.sendMessage("§aPlacar ligado.");
            atualizar(jogador);
        } else {
            desligados.add(id);
            placares.remove(id);
            linhasUsadas.remove(id);
            jogador.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            jogador.sendMessage("§7Placar desligado. §f/placar §7liga de novo.");
        }
        salvarDesligados();
        return true;
    }

    private void salvarDesligados() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jogadores", desligados.stream().map(UUID::toString).toList());
        try {
            yaml.save(arquivoDesligados);
        } catch (IOException e) {
            getLogger().warning("Não foi possível salvar desligados.yml: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Montagem
    // ---------------------------------------------------------------------

    private void atualizar(Player jogador) {
        if (!jogador.isOnline() || desligados.contains(jogador.getUniqueId())) return;
        if (getConfig().getStringList("mundos-sem-placar").contains(jogador.getWorld().getName())) {
            if (placares.remove(jogador.getUniqueId()) != null) {
                linhasUsadas.remove(jogador.getUniqueId());
                jogador.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }
        Scoreboard placar = placares.computeIfAbsent(jogador.getUniqueId(), k -> novoPlacar());
        if (jogador.getScoreboard() != placar) jogador.setScoreboard(placar);
        Objective objetivo = placar.getObjective("placar");
        if (objetivo == null) return;

        List<String> linhas = getConfig().getStringList("linhas");
        int total = Math.min(15, linhas.size());
        for (int i = 0; i < total; i++) {
            var score = objetivo.getScore("l" + i);
            score.setScore(total - i);
            score.customName(LEGADO.deserialize(cores(preencher(linhas.get(i), jogador))));
        }
        // linhas que sobraram de um config maior (depois de um reload)
        int antes = linhasUsadas.getOrDefault(jogador.getUniqueId(), 0);
        for (int i = total; i < antes; i++) placar.resetScores("l" + i);
        linhasUsadas.put(jogador.getUniqueId(), total);
    }

    private Scoreboard novoPlacar() {
        Scoreboard placar = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objetivo = placar.registerNewObjective("placar", Criteria.DUMMY,
                LEGADO.deserialize(cores(getConfig().getString("titulo", "&6&lSERVIDOR"))));
        objetivo.setDisplaySlot(DisplaySlot.SIDEBAR);
        objetivo.numberFormat(NumberFormat.blank()); // sem os números vermelhos à direita
        return placar;
    }

    /** Troca os placeholders da linha. Cada um só é calculado se aparecer nela. */
    private String preencher(String linha, Player jogador) {
        UUID id = jogador.getUniqueId();
        if (linha.contains("{jogador}")) linha = linha.replace("{jogador}", jogador.getName());
        if (linha.contains("{saldo}")) linha = linha.replace("{saldo}", economy == null ? "—" : dinheiro(economy.getBalance(jogador)));
        if (linha.contains("{rank}")) linha = linha.replace("{rank}", texto(chamar("RankupPlugin", "tagDoRank", id), "—"));
        if (linha.contains("{proximo-rank}")) {
            linha = linha.replace("{proximo-rank}", texto(chamar("RankupPlugin", "nomeProximoRank", id), "§6máximo"));
        }
        if (linha.contains("{progresso}")) linha = linha.replace("{progresso}", progresso(jogador));
        if (linha.contains("{cla}")) {
            Object tag = chamar("ClasPlugin", "tagDoCla", id);
            linha = linha.replace("{cla}", tag == null ? getConfig().getString("textos.sem-cla", "&7nenhum") : "&b[" + tag + "]");
        }
        if (linha.contains("{missoes}")) {
            Object r = chamar("MissoesPlugin", "missoesDoDia", id);
            linha = linha.replace("{missoes}", r instanceof int[] m && m.length == 2
                    ? (m[0] >= m[1] && m[1] > 0 ? "&a" : "&f") + m[0] + "/" + m[1] + (m[0] >= m[1] && m[1] > 0 ? " ✔" : "") : "—");
        }
        if (linha.contains("{skill}")) {
            linha = linha.replace("{skill}", texto(chamar("SkillsPlugin", "textoSkillEmUso", id), getConfig().getString("textos.sem-skill", "&7—")));
        }
        if (linha.contains("{skill-progresso}")) {
            linha = linha.replace("{skill-progresso}", texto(chamar("SkillsPlugin", "progressoSkillEmUso", id), ""));
        }
        if (linha.contains("{boss}")) {
            Object b = chamarSemArgumento("BossPlugin", "textoProximoBoss");
            linha = linha.replace("{boss}", b == null ? "—" : "AGORA!".equals(b) ? "&c&lAGORA!" : b.toString());
        }
        if (linha.contains("{online}")) linha = linha.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        if (linha.contains("{max}")) linha = linha.replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
        return linha;
    }

    /** Barra até o próximo rank: saldo / preço do próximo, em 10 blocos, com a %. */
    private String progresso(Player jogador) {
        Object proximo = chamar("RankupPlugin", "nomeProximoRank", jogador.getUniqueId());
        if (proximo == null || economy == null) return "&6✦ rank máximo";
        Object preco = chamar("RankupPlugin", "precoProximoRank", jogador.getUniqueId());
        if (!(preco instanceof Number n) || n.doubleValue() <= 0) return "—";
        double fracao = Math.max(0, Math.min(1, economy.getBalance(jogador) / n.doubleValue()));
        int cheios = (int) Math.floor(fracao * 10);
        return "&a" + "■".repeat(cheios) + "&8" + "■".repeat(10 - cheios) + " &f" + (int) Math.floor(fracao * 100) + "%";
    }

    private Object chamar(String plugin, String metodo, UUID jogador) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        if (p == null || !p.isEnabled()) return null;
        try {
            return p.getClass().getMethod(metodo, UUID.class).invoke(p, jogador);
        } catch (ReflectiveOperationException | RuntimeException e) {
            avisar(plugin + "#" + metodo, e);
            return null;
        }
    }

    private Object chamarSemArgumento(String plugin, String metodo) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        if (p == null || !p.isEnabled()) return null;
        try {
            return p.getClass().getMethod(metodo).invoke(p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            avisar(plugin + "#" + metodo, e);
            return null;
        }
    }

    private void avisar(String chave, Exception e) {
        if (avisouFalha.add(chave)) {
            getLogger().warning("Não deu pra ler " + chave + " (versão antiga do plugin?): " + e);
        }
    }

    private static String texto(Object valor, String padrao) {
        return valor == null ? padrao : valor.toString();
    }

    /** R$ 1.234 até 100 mil; acima disso, resumido (R$ 1,2 mi), pra caber na largura. */
    private static String dinheiro(double v) {
        Locale br = Locale.forLanguageTag("pt-BR");
        if (v >= 1_000_000_000) return String.format(br, "R$ %.1f bi", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format(br, "R$ %.1f mi", v / 1_000_000);
        if (v >= 100_000) return String.format(br, "R$ %.0f mil", Math.floor(v / 1_000));
        return String.format(br, "R$ %,.0f", Math.floor(v));
    }

    private static String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }
}
