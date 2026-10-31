package com.example.ajuda;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /tutorial}: etapas em ordem ({@code tutorial.etapas}), cada uma cumprida no jogo e detectada
 * sozinha, e um prêmio único no fim ({@code tutorial.premio}). Os tipos de etapa:
 * <ul>
 *   <li>{@code minerar-mina}: quebrar {@code quantidade} blocos dentro de uma mina do RankupPlugin;</li>
 *   <li>{@code vender-loja}: vender do inventário pra Loja Oficial (o EconomyShopPlugin avisa);</li>
 *   <li>{@code comando}: usar um dos {@code comandos} (ex.: {@code terreno auto}).</li>
 * </ul>
 * Só conta depois de começar ({@code /tutorial}). O prêmio sai uma vez por jogador (UUID); sem espaço
 * no inventário, fica guardado até ele liberar e usar {@code /tutorial} de novo. Progresso em
 * {@code progresso.yml}.
 */
class Tutorial implements Listener, CommandExecutor, TabCompleter {

    private final AjudaPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Progresso> progresso = new HashMap<>();
    private boolean avisouMina = false, avisouTesouros = false;

    private static final class Progresso {
        int etapa;         // índice da etapa atual; == número de etapas: terminou
        int contador;      // quanto já fez da etapa atual (blocos minerados)
        boolean premiado;  // já recebeu o prêmio
    }

    private record Etapa(String tipo, String titulo, String texto, String dica, int quantidade, List<String> comandos) {}

    Tutorial(AjudaPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "progresso.yml");
        carregar();
    }

    // ---------------------------------------------------------------- dados

    private List<Etapa> etapas() {
        List<Etapa> lista = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("tutorial.etapas")) {
            List<String> comandos = new ArrayList<>();
            if (m.get("comandos") instanceof List<?> l) for (Object o : l) comandos.add(String.valueOf(o).toLowerCase(Locale.ROOT));
            lista.add(new Etapa(String.valueOf(m.get("tipo")), texto(m.get("titulo")), texto(m.get("texto")), texto(m.get("dica")),
                    m.get("quantidade") instanceof Number n ? n.intValue() : 1, comandos));
        }
        return lista;
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void carregar() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        for (String chave : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(chave);
            if (s == null) continue;
            Progresso p = new Progresso();
            p.etapa = s.getInt("etapa");
            p.contador = s.getInt("contador");
            p.premiado = s.getBoolean("premiado");
            try {
                progresso.put(UUID.fromString(chave), p);
            } catch (IllegalArgumentException ignorado) {
                // chave estranha no arquivo: pula
            }
        }
    }

    void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        progresso.forEach((id, p) -> {
            y.set(id + ".etapa", p.etapa);
            y.set(id + ".contador", p.contador);
            y.set(id + ".premiado", p.premiado);
        });
        try {
            y.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar o progresso.yml: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- avanço

    /** Uma ação do jogador; avança se for o que a etapa atual espera. */
    void acao(Player jogador, String tipo, int quanto) {
        Progresso p = progresso.get(jogador.getUniqueId());
        List<Etapa> etapas = etapas();
        if (p == null || p.etapa >= etapas.size()) return;
        Etapa e = etapas.get(p.etapa);
        if (!e.tipo().equals(tipo)) return;
        p.contador += quanto;
        if (p.contador < e.quantidade()) {
            jogador.sendActionBar(AjudaPlugin.texto("&6Tutorial: &f" + e.titulo() + " &7(" + p.contador + "/" + e.quantidade() + ")"));
            return;
        }
        concluirEtapa(jogador, p, etapas);
    }

    private void concluirEtapa(Player jogador, Progresso p, List<Etapa> etapas) {
        Etapa feita = etapas.get(p.etapa);
        p.etapa++;
        p.contador = 0;
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        jogador.sendMessage(AjudaPlugin.texto("&a✔ Tutorial: &f" + feita.titulo() + " &aconcluída!"));
        if (p.etapa >= etapas.size()) {
            jogador.showTitle(Title.title(AjudaPlugin.texto("&6&lTutorial concluído!"), AjudaPlugin.texto("&7Seu prêmio está chegando"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            entregarPremio(jogador, p);
        } else {
            mostrar(jogador, p, etapas);
        }
        salvar();
    }

    private void mostrar(Player jogador, Progresso p, List<Etapa> etapas) {
        Etapa e = etapas.get(p.etapa);
        jogador.sendMessage(Component.empty());
        jogador.sendMessage(AjudaPlugin.texto("&6&l✦ Tutorial &7(" + (p.etapa + 1) + "/" + etapas.size() + ") &f&l" + e.titulo()));
        jogador.sendMessage(AjudaPlugin.texto("&7" + e.texto()));
        if (e.quantidade() > 1) jogador.sendMessage(AjudaPlugin.texto("&7Progresso: &f" + p.contador + "/" + e.quantidade()));
        if (!e.dica().isEmpty()) {
            jogador.sendMessage(Component.text(" ").append(AjudaPlugin.botao("&e&l[▶ " + e.dica() + "]", e.dica(), "&7Clique pra usar &f" + e.dica(), true)));
        }
    }

    private void entregarPremio(Player jogador, Progresso p) {
        if (p.premiado) return;
        String raridade = plugin.getConfig().getString("tutorial.premio.mapa", "");
        ItemStack mapa = raridade.isEmpty() ? null : mapa(raridade);
        if (!raridade.isEmpty() && mapa == null) {
            jogador.sendMessage("§cO prêmio não pôde ser entregue agora. Ele fica guardado: use §f/tutorial §cmais tarde.");
            return;
        }
        if (mapa != null) {
            org.bukkit.inventory.Inventory copia = Bukkit.createInventory(null, 36);
            copia.setContents(jogador.getInventory().getStorageContents());
            if (!copia.addItem(mapa.clone()).isEmpty()) {
                jogador.sendMessage("§eSeu inventário está cheio. Libere um espaço e use §f/tutorial §epra receber o prêmio.");
                return;
            }
            jogador.getInventory().addItem(mapa);
        }
        p.premiado = true;
        salvar();
        for (String c : plugin.getConfig().getStringList("tutorial.premio.comandos")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{jogador}", jogador.getName()));
        }
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        jogador.sendMessage(AjudaPlugin.texto(plugin.getConfig().getString("tutorial.premio.mensagem", "&aVocê recebeu o prêmio do tutorial!")));
    }

    /** O mapa do tesouro do TesourosPlugin, ou null sem ele. */
    private ItemStack mapa(String raridade) {
        Plugin tesouros = Bukkit.getPluginManager().getPlugin("TesourosPlugin");
        if (tesouros == null || !tesouros.isEnabled()) {
            if (!avisouTesouros) plugin.getLogger().warning("Sem o TesourosPlugin: o mapa do prêmio do tutorial não pode ser entregue.");
            avisouTesouros = true;
            return null;
        }
        try {
            Object mapas = tesouros.getClass().getMethod("getMapas").invoke(tesouros);
            return (ItemStack) mapas.getClass().getMethod("criar", String.class).invoke(mapas, raridade);
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Não foi possível criar o mapa do prêmio (TesourosPlugin#getMapas().criar): " + e.getMessage());
            return null;
        }
    }

    /** O lugar fica numa mina do RankupPlugin? */
    private boolean emMina(Location local) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return false;
        try {
            Object minas = rankup.getClass().getMethod("getMinaManager").invoke(rankup);
            return minas.getClass().getMethod("getMinaEm", Location.class).invoke(minas, local) != null;
        } catch (ReflectiveOperationException e) {
            if (!avisouMina) plugin.getLogger().warning("Não foi possível consultar as minas do RankupPlugin: " + e.getMessage());
            avisouMina = true;
            return false;
        }
    }

    // ---------------------------------------------------------------- eventos

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoMinerar(BlockBreakEvent evento) {
        Player p = evento.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        Progresso pr = progresso.get(p.getUniqueId());
        List<Etapa> etapas = etapas();
        if (pr == null || pr.etapa >= etapas.size() || !etapas.get(pr.etapa).tipo().equals("minerar-mina")) return;
        if (emMina(evento.getBlock().getLocation())) acao(p, "minerar-mina", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoComando(PlayerCommandPreprocessEvent evento) {
        Progresso p = progresso.get(evento.getPlayer().getUniqueId());
        List<Etapa> etapas = etapas();
        if (p == null || p.etapa >= etapas.size()) return;
        Etapa e = etapas.get(p.etapa);
        if (!e.tipo().equals("comando")) return;
        String digitado = evento.getMessage().substring(1).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String c : e.comandos()) {
            if (digitado.equals(c) || digitado.startsWith(c + " ")) {
                // depois do comando rodar (as mensagens dele chegam antes da etapa seguinte)
                Bukkit.getScheduler().runTask(plugin, () -> acao(evento.getPlayer(), "comando", e.quantidade()));
                return;
            }
        }
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player j = evento.getPlayer();
        Progresso p = progresso.get(j.getUniqueId());
        if (p != null && p.premiado) return;
        // espera o login (AuthMe) e as mensagens de boas-vindas
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!j.isOnline()) return;
            if (p == null) {
                if (!plugin.getConfig().getBoolean("tutorial.convite-ao-entrar", true)) return;
                j.sendMessage(Component.text(" ")
                        .append(AjudaPlugin.texto("&6✦ &eNovo por aqui? Faça o tutorial e ganhe um prêmio! "))
                        .append(AjudaPlugin.botao("&a&l[COMEÇAR]", "/tutorial", "&7Clique pra começar o tutorial", true)));
            } else if (p.etapa < etapas().size()) {
                j.sendMessage(Component.text(" ")
                        .append(AjudaPlugin.texto("&6✦ &eVocê está no tutorial. "))
                        .append(AjudaPlugin.botao("&a&l[VER ETAPA]", "/tutorial", "&7Clique pra ver a etapa atual", true)));
            } else {
                j.sendMessage("§eO prêmio do tutorial está guardado: libere um espaço e use §f/tutorial§e.");
            }
        }, 20L * plugin.getConfig().getInt("tutorial.atraso-convite-segundos", 8));
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        if (progresso.containsKey(evento.getPlayer().getUniqueId())) salvar();
    }

    // ---------------------------------------------------------------- comando

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) return admin(sender, args);
        if (!(sender instanceof Player j)) {
            sender.sendMessage("§cSó jogadores fazem o tutorial. Staff: /tutorial admin <ver|resetar> <jogador>");
            return true;
        }
        List<Etapa> etapas = etapas();
        Progresso p = progresso.get(j.getUniqueId());
        if (p == null) {
            p = new Progresso();
            progresso.put(j.getUniqueId(), p);
            salvar();
            j.sendMessage(AjudaPlugin.texto("&6&l✦ Tutorial começado! &7São &f" + etapas.size()
                    + " &7etapas; cada uma é detectada sozinha. No fim, você ganha um prêmio."));
        }
        if (p.etapa < etapas.size()) {
            mostrar(j, p, etapas);
        } else if (!p.premiado) {
            entregarPremio(j, p);
        } else {
            j.sendMessage("§aVocê já concluiu o tutorial. Veja os comandos do servidor em §f/comandos§a.");
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ajuda.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§eUso: /tutorial admin <ver|resetar|etapa> <jogador> [número]");
            return true;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[2]);
        Progresso p = progresso.get(alvo.getUniqueId());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "ver" -> sender.sendMessage(p == null ? "§e" + args[2] + " não começou o tutorial."
                    : "§e" + args[2] + ": etapa " + Math.min(p.etapa + 1, etapas().size()) + "/" + etapas().size()
                    + " (contador " + p.contador + ")" + (p.premiado ? ", já premiado" : ""));
            case "resetar" -> {
                progresso.remove(alvo.getUniqueId());
                salvar();
                sender.sendMessage("§aTutorial de " + args[2] + " zerado (pode fazer e ganhar o prêmio de novo).");
            }
            case "etapa" -> {
                if (p == null || args.length < 4) {
                    sender.sendMessage("§cO jogador precisa ter começado, e informe o número da etapa.");
                    return true;
                }
                try {
                    p.etapa = Math.max(0, Math.min(etapas().size() - 1, Integer.parseInt(args[3]) - 1));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNúmero inválido.");
                    return true;
                }
                p.contador = 0;
                salvar();
                sender.sendMessage("§a" + args[2] + " agora está na etapa " + (p.etapa + 1) + ".");
            }
            default -> sender.sendMessage("§eUso: /tutorial admin <ver|resetar|etapa> <jogador> [número]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ajuda.admin")) return List.of();
        if (args.length == 1) return List.of("admin");
        if (args.length == 2) return List.of("ver", "resetar", "etapa");
        if (args.length == 3) return null; // nomes dos jogadores online
        return List.of();
    }
}
