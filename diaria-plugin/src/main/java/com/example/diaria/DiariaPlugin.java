package com.example.diaria;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Recompensa diária de login: /diaria uma vez por dia, numa sequência de 7 dias que cresce até o
 * 7º. Perder um dia volta pro dia 1; depois do 7º, recomeça. O valor de cada dia é uma % do preço
 * do próximo rank do jogador (RankupPlugin), com um mínimo, pra pesar parecido em qualquer fase
 * do jogo. É dinheiro novo na economia: aparece no painel como "Recompensa diária".
 */
public class DiariaPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private record Registro(LocalDate ultimo, int sequencia) {
    }

    private Economy economy;
    private PixIntegration pixIntegration;
    private File arquivo;
    private final Map<UUID, Registro> registros = new HashMap<>();
    private boolean avisouFalhaRankup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp == null ? null : rsp.getProvider();
        if (economy == null) getLogger().warning("Vault (ou um plugin de economia) não foi encontrado! O /diaria não vai pagar nada.");
        pixIntegration = new PixIntegration(this);
        arquivo = new File(getDataFolder(), "diaria.yml");
        carregar();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("diaria").setExecutor(this);
    }

    // ---------------------------------------------------------------------
    // Sequência
    // ---------------------------------------------------------------------
    private List<Double> percentuais() {
        List<Double> lista = getConfig().getDoubleList("dias.percentual-do-proximo-rank");
        return lista.isEmpty() ? List.of(0.5, 0.5, 0.75, 0.75, 1.0, 1.0, 2.5) : lista;
    }

    /** Que dia da sequência o jogador resgata hoje (1 a 7), ou 0 se já resgatou hoje. */
    private int diaDeHoje(UUID jogador) {
        Registro r = registros.get(jogador);
        LocalDate hoje = LocalDate.now();
        if (r == null) return 1;
        if (r.ultimo().equals(hoje)) return 0;
        boolean seguido = r.ultimo().equals(hoje.minusDays(1));
        return seguido ? r.sequencia() % percentuais().size() + 1 : 1;
    }

    /** Valor do dia: % do preço do próximo rank, com mínimo (ou um valor fixo sem o RankupPlugin). */
    private double valor(UUID jogador, int dia) {
        double minimo = Math.max(0, getConfig().getDouble("dias.minimo", 200));
        double preco = precoProximoRank(jogador);
        double base = preco < 0 ? getConfig().getDouble("dias.sem-rankup", 1000) : preco * percentuais().get(dia - 1) / 100.0;
        double bonusDia7 = dia == percentuais().size() ? Math.max(1, getConfig().getDouble("dias.minimo-multiplicador-ultimo-dia", 3)) : 1;
        return Math.max(minimo * bonusDia7, base);
    }

    private double precoProximoRank(UUID jogador) {
        Plugin rankup = Bukkit.getPluginManager().getPlugin("RankupPlugin");
        if (rankup == null || !rankup.isEnabled()) return -1;
        try {
            Object v = rankup.getClass().getMethod("precoProximoRank", UUID.class).invoke(rankup, jogador);
            return v instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : -1;
        } catch (ReflectiveOperationException e) {
            if (!avisouFalhaRankup) {
                getLogger().warning("RankupPlugin encontrado, mas sem o preço do próximo rank (versão antiga?): " + e.getMessage());
                avisouFalhaRankup = true;
            }
            return -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores resgatam a recompensa diária.");
            return true;
        }
        int dia = diaDeHoje(jogador.getUniqueId());
        if (dia == 0) {
            Registro r = registros.get(jogador.getUniqueId());
            jogador.sendMessage("§7Você já resgatou hoje (dia §f" + r.sequencia() + "§7 de " + percentuais().size()
                    + "). Volte amanhã pro dia §f" + (r.sequencia() % percentuais().size() + 1) + "§7.");
            return true;
        }
        if (economy == null) {
            jogador.sendMessage("§cA economia está indisponível no momento.");
            return true;
        }
        double valor = resgatar(jogador, dia);
        jogador.sendMessage("§a§lRecompensa diária! §aDia §f" + dia + " §ade " + percentuais().size() + ": §f" + formatar(valor)
                + (dia == percentuais().size() ? " §6(bônus do último dia!)" : "") + "§a.");
        jogador.sendMessage("§7Volte amanhã pro dia §f" + (dia % percentuais().size() + 1) + "§7. Se perder um dia, a sequência recomeça.");
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        return true;
    }

    /** Paga e grava. Separado pro painel da economia mostrar como "Recompensa diária". */
    private double resgatar(Player jogador, int dia) {
        double valor = valor(jogador.getUniqueId(), dia);
        economy.depositPlayer(jogador, valor);
        registros.put(jogador.getUniqueId(), new Registro(LocalDate.now(), dia));
        salvar();
        pixIntegration.registrarComServidor(jogador.getUniqueId(), valor, true, "Recompensa diária (dia " + dia + ")");
        return valor;
    }

    /** Staff (pra testar): /diaria admin ontem|anteontem <jogador> volta a data do último resgate. */
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("diaria.admin")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        int dias = args.length >= 2 ? switch (args[1].toLowerCase(Locale.ROOT)) {
            case "ontem" -> 1;
            case "anteontem" -> 2;
            default -> -1;
        } : -1;
        Player alvo = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        Registro r = alvo == null ? null : registros.get(alvo.getUniqueId());
        if (dias < 0 || r == null) {
            sender.sendMessage("§cUso: §f/diaria admin <ontem|anteontem> <jogador online que já resgatou>");
            return;
        }
        registros.put(alvo.getUniqueId(), new Registro(LocalDate.now().minusDays(dias), r.sequencia()));
        salvar();
        sender.sendMessage("§aO último resgate de §f" + alvo.getName() + " §avoltou pra " + args[1].toLowerCase(Locale.ROOT) + ".");
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        Player p = evento.getPlayer();
        int dia = diaDeHoje(p.getUniqueId());
        if (dia == 0) return;
        // 2 s depois, pra não se perder entre as mensagens de entrada
        getServer().getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) p.sendMessage("§6§l★ §eSua recompensa diária está disponível (dia §f" + dia + "§e de "
                    + percentuais().size() + "): §f/diaria");
        }, 40L);
    }

    private static String formatar(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    // ---------------------------------------------------------------------
    // Persistência (diaria.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (String chave : yaml.getKeys(false)) {
            try {
                registros.put(UUID.fromString(chave), new Registro(LocalDate.parse(yaml.getString(chave + ".ultimo")), yaml.getInt(chave + ".sequencia")));
            } catch (RuntimeException e) {
                getLogger().warning("Registro inválido ignorado em diaria.yml: " + chave);
            }
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        registros.forEach((id, r) -> {
            yaml.set(id + ".ultimo", r.ultimo().toString());
            yaml.set(id + ".sequencia", r.sequencia());
        });
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Não foi possível salvar diaria.yml", e);
        }
    }
}
