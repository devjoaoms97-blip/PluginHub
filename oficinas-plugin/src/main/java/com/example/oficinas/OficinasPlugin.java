package com.example.oficinas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Oficinas da mina: NPCs de serviço nas barraquinhas da avenida das minas — Ferreiro (reparo pago),
 * Bruxa (mesa de encantamento no máximo), Picareteiro (ferramentas), Sábio (XP), Compactador
 * (blocos de minério) e Cambista (fragmentos por encantamentos acima do máximo) — e o guia na
 * entrada de cada mina. A staff põe cada NPC com /oficina criar.
 */
public class OficinasPlugin extends JavaPlugin implements Listener {

    private Economy economy;
    private NpcsManager npcs;
    private Servicos servicos;
    private PixIntegration pix;
    private Integracoes integracoes;
    private Cambista cambista;
    private Compactador compactador;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        if (economy == null) getLogger().warning("Vault (ou um plugin de economia) não foi encontrado: os serviços pagos não vão funcionar.");

        npcs = new NpcsManager(this);
        servicos = new Servicos(this);
        pix = new PixIntegration(this);
        integracoes = new Integracoes(this);
        cambista = new Cambista(this);
        compactador = new Compactador(this);
        getServer().getPluginManager().registerEvents(npcs, this);
        getServer().getPluginManager().registerEvents(servicos, this);
        getServer().getPluginManager().registerEvents(cambista, this);
        getServer().getPluginManager().registerEvents(compactador, this);
        getServer().getPluginManager().registerEvents(this, this);
        npcs.carregarTodos();
        getServer().getScheduler().runTaskTimer(this, npcs::conferir, 100L, 100L);
        getLogger().info("OficinasPlugin ativado! " + npcs.getOficinas().size() + " oficina(s).");
    }

    @Override
    public void onDisable() {
        if (npcs != null) npcs.removerTodos();
    }

    @EventHandler
    public void aoClicarNpc(PlayerInteractEntityEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        Oficina o = npcs.getOficina(evento.getRightClicked());
        if (o == null) return;
        evento.setCancelled(true);
        if (o.getTipo() == Oficina.Tipo.MINA) mostrarMina(evento.getPlayer(), o.getAlvo());
        else servicos.abrir(evento.getPlayer(), o.getTipo());
    }

    /** Guia de mina: as informações dela no chat e, pra quem tem o rank, o botão de ir até ela. */
    private void mostrarMina(Player jogador, String mina) {
        List<String> linhas = mina == null ? null : integracoes.infoMina(mina, jogador.getUniqueId());
        if (linhas == null) {
            jogador.sendMessage("§cEssa mina não está aberta agora. Avise a staff.");
            return;
        }
        jogador.sendMessage("");
        linhas.forEach(jogador::sendMessage);
        if (integracoes.podeEntrarNaMina(mina, jogador)) {
            jogador.sendMessage(Component.text("[Clique aqui pra entrar na mina]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/mina " + mina))
                    .hoverEvent(HoverEvent.showText(Component.text("Vai pro ponto da mina " + mina, NamedTextColor.GRAY))));
        }
        jogador.playSound(jogador.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 1f);
    }

    // ---------------------------------------------------------------------
    // /oficina (staff)
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "criar" -> criar(sender, args);
            case "remover" -> {
                Oficina o = args.length < 2 ? null : npcs.get(args[1]);
                if (o == null) {
                    sender.sendMessage("§cUso: §f/oficina remover <id>");
                    return true;
                }
                npcs.remover(o);
                sender.sendMessage("§aOficina §f" + o.getId() + " §aremovida.");
            }
            case "lista" -> {
                sender.sendMessage("§e§l--- Oficinas ---");
                if (npcs.getOficinas().isEmpty()) sender.sendMessage("§7Nenhuma ainda.");
                for (Oficina o : npcs.getOficinas().values()) {
                    sender.sendMessage("§f" + o.getId() + " §7(" + o.getTipo().chave() + (o.getAlvo() != null ? " " + o.getAlvo() : "") + ") em §f" + o.getMundo() + " "
                            + (int) Math.floor(o.getX()) + " " + (int) Math.floor(o.getY()) + " " + (int) Math.floor(o.getZ()));
                }
            }
            case "mesa" -> mesa(sender, args);
            case "abrir" -> {
                // teste da staff: abre o serviço sem precisar do NPC
                Oficina.Tipo tipo = args.length < 2 ? null : Oficina.Tipo.de(args[1]);
                if (!(sender instanceof Player p) || tipo == null) {
                    sender.sendMessage("§cUso: §f/oficina abrir <ferreiro|bruxa|picareteiro|sabio|armeiro|compactador|cambista|bancario>");
                    return true;
                }
                servicos.abrir(p, tipo);
            }
            case "reload" -> {
                reloadConfig();
                npcs.carregarTodos();
                sender.sendMessage("§aConfig recarregado (nomes e skins dos NPCs atualizados).");
            }
            default -> {
                sender.sendMessage("§e§l--- Oficinas ---");
                sender.sendMessage("§f/oficina criar <id> <tipo> [mundo x y z yaw] §7- põe o NPC (onde você está, sem coordenadas)");
                sender.sendMessage("§7  tipos: ferreiro, bruxa, picareteiro, sabio, armeiro, compactador, cambista, bancario, mina:<id da mina>");
                sender.sendMessage("§f/oficina mesa [mundo x y z] §7- onde fica a mesa de encantamento da bruxa");
                sender.sendMessage("§f/oficina remover <id> §7| §f/oficina lista §7| §f/oficina abrir <tipo> §7| §f/oficina reload");
            }
        }
        return true;
    }

    private void criar(CommandSender sender, String[] args) {
        // guia de mina: "mina:<id da mina>"
        String tipoTexto = args.length < 3 ? "" : args[2];
        String alvo = null;
        if (tipoTexto.toLowerCase(Locale.ROOT).startsWith("mina:")) {
            alvo = tipoTexto.substring(5).toLowerCase(Locale.ROOT);
            tipoTexto = "mina";
        }
        Oficina.Tipo tipo = Oficina.Tipo.de(tipoTexto);
        if (tipo == null || (tipo == Oficina.Tipo.MINA && (alvo == null || alvo.isBlank()))) {
            sender.sendMessage("§cUso: §f/oficina criar <id> <ferreiro|bruxa|picareteiro|sabio|armeiro|compactador|cambista|bancario|mina:<mina>> [mundo x y z yaw]");
            return;
        }
        Location local;
        if (args.length >= 7) {
            World w = Bukkit.getWorld(args[3]);
            try {
                local = new Location(w, Double.parseDouble(args[4]), Double.parseDouble(args[5]), Double.parseDouble(args[6]),
                        args.length >= 8 ? Float.parseFloat(args[7]) : 0, 0);
            } catch (NumberFormatException e) {
                local = null;
            }
            if (w == null || local == null) {
                sender.sendMessage("§cMundo ou coordenadas inválidos.");
                return;
            }
        } else if (sender instanceof Player p) {
            local = p.getLocation();
            local.setPitch(0);
        } else {
            sender.sendMessage("§cDo console, passe o mundo e as coordenadas.");
            return;
        }
        Oficina o = npcs.criar(args[1], tipo, alvo, local);
        sender.sendMessage("§aOficina §f" + o.getId() + " §a(" + tipo.chave() + (alvo != null ? " " + alvo : "") + ") criada.");
    }

    private void mesa(CommandSender sender, String[] args) {
        Location l;
        if (args.length >= 5) {
            World w = Bukkit.getWorld(args[1]);
            try {
                l = w == null ? null : new Location(w, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                l = null;
            }
        } else {
            l = sender instanceof Player p ? p.getLocation().getBlock().getLocation() : null;
        }
        if (l == null) {
            sender.sendMessage("§cUso: §f/oficina mesa [mundo x y z]");
            return;
        }
        getConfig().set("bruxa.mesa.mundo", l.getWorld().getName());
        getConfig().set("bruxa.mesa.x", l.getBlockX());
        getConfig().set("bruxa.mesa.y", l.getBlockY());
        getConfig().set("bruxa.mesa.z", l.getBlockZ());
        saveConfig();
        sender.sendMessage("§aMesa da bruxa em §f" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                + "§a. Ela precisa de estantes em volta (a 2 blocos) pra chegar no nível 30.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) opcoes.addAll(List.of("criar", "remover", "lista", "mesa", "abrir", "reload"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("remover")) opcoes.addAll(npcs.getOficinas().keySet());
        else if ((args.length == 3 && args[0].equalsIgnoreCase("criar")) || (args.length == 2 && args[0].equalsIgnoreCase("abrir"))) {
            Arrays.stream(Oficina.Tipo.values()).map(t -> t == Oficina.Tipo.MINA ? "mina:" : t.chave()).forEach(opcoes::add);
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.startsWith(digitado));
        return opcoes;
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    /** Maior desconto (%) de VIP do jogador ({@code vip-descontos}, LISTA como na loja). */
    double descontoVip(Player jogador) {
        double maior = 0;
        for (Map<?, ?> item : getConfig().getMapList("vip-descontos")) {
            if (item.get("permissao") instanceof String perm && item.get("percentual") instanceof Number n && jogador.hasPermission(perm)) {
                maior = Math.max(maior, n.doubleValue());
            }
        }
        return Math.min(100, maior);
    }

    String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }

    String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    public Economy getEconomy() {
        return economy;
    }

    PixIntegration getPix() {
        return pix;
    }

    Integracoes getIntegracoes() {
        return integracoes;
    }

    Servicos getServicos() {
        return servicos;
    }

    Cambista getCambista() {
        return cambista;
    }

    Compactador getCompactador() {
        return compactador;
    }
}
