package com.example.creditos;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Créditos da loja do Discord. A loja vende créditos (R$ 1 = 10) e entrega com
 * {@code creditos dar <jogador> <quantidade> [motivo]} pelo console (RCON); no jogo, {@code /creditos}
 * mostra o saldo e abre a {@link Loja}, onde eles viram VIP (ativado ou código de presente) e
 * cosméticos ({@link Cosmeticos}). Preço fixo, sem sorteio e sem item que dê vantagem no jogo.
 * <p>
 * A tag de apoiador (quem contribui sem levar nada) dura 30 dias a cada contribuição:
 * {@code apoiador dar <jogador> [dias]}. O VipPlugin monta o chat e a tab e pergunta aqui a tag
 * ({@link #tagApoiador}) e a cor do nome ({@link #corDoNome}).
 */
public class CreditosPlugin extends JavaPlugin {

    private Dados dados;
    private Cosmeticos cosmeticos;
    private Loja loja;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dados = new Dados(this);
        cosmeticos = new Cosmeticos(this);
        loja = new Loja(this);
        getServer().getPluginManager().registerEvents(cosmeticos, this);
        getServer().getPluginManager().registerEvents(loja, this);
        getServer().getScheduler().runTaskTimer(this, cosmeticos::rastros, 20L, 2L);
        // a tag de apoiador vence sozinha: refaz a tab de todo mundo de tempos em tempos
        getServer().getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::refrescarTag), 20L * 60, 20L * 60 * 5);
        getLogger().info("CreditosPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (dados != null) dados.salvar();
    }

    // ---------------------------------------------------------------------
    // Comandos
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("apoiador")) return apoiador(sender, args);

        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (List.of("dar", "tirar", "ver").contains(sub)) {
            if (!sender.hasPermission("creditos.admin")) {
                sender.sendMessage("§cSó a staff (ou a loja do Discord, pelo console).");
                return true;
            }
            return admin(sender, sub, args);
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cUso: creditos <dar|tirar|ver> <jogador> [quantidade] [motivo]");
            return true;
        }
        jogador.sendMessage("§b✦ Você tem §f" + dados.creditos(jogador.getUniqueId()) + " créditos§b. "
                + Loja.cores(getConfig().getString("onde-comprar", "")));
        loja.abrir(jogador, Loja.Tela.INICIO);
        return true;
    }

    /** creditos dar|tirar <jogador> <quantidade> [motivo] | creditos ver <jogador> */
    private boolean admin(CommandSender sender, String sub, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: §fcreditos dar|tirar <jogador> <quantidade> [motivo] §7| §fcreditos ver <jogador>");
            return true;
        }
        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            sender.sendMessage("§cJogador \"" + args[1] + "\" nunca entrou no servidor.");
            return true;
        }
        if (sub.equals("ver")) {
            sender.sendMessage("§b" + alvo.getName() + ": §f" + dados.creditos(alvo.getUniqueId()) + " créditos"
                    + (dados.apoiador(alvo.getUniqueId()) ? " §7| §dapoiador até " + data(dados.apoiadorAte(alvo.getUniqueId())) : ""));
            return true;
        }
        long quantidade;
        try {
            quantidade = Long.parseLong(args.length > 2 ? args[2] : "");
        } catch (NumberFormatException e) {
            quantidade = -1;
        }
        if (quantidade <= 0) {
            sender.sendMessage("§cQuantidade inválida: um número inteiro maior que zero.");
            return true;
        }
        String motivo = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : sender.getName();
        long saldo = dados.mudar(alvo.getUniqueId(), String.valueOf(alvo.getName()), sub.equals("dar") ? quantidade : -quantidade, motivo);
        sender.sendMessage("§a" + (sub.equals("dar") ? "+" : "-") + quantidade + " créditos pra " + alvo.getName() + ". Saldo: " + saldo + ".");
        if (sub.equals("dar") && alvo.getPlayer() != null) {
            alvo.getPlayer().sendMessage("§b§l✦ §bVocê recebeu §f" + quantidade + " créditos§b! Use em §f/creditos§b. Obrigado pelo apoio!");
            alvo.getPlayer().playSound(alvo.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        }
        return true;
    }

    /** apoiador dar <jogador> [dias] | apoiador ver <jogador> */
    private boolean apoiador(CommandSender sender, String[] args) {
        if (!sender.hasPermission("creditos.admin")) {
            sender.sendMessage("§cSó a staff (ou a loja do Discord, pelo console).");
            return true;
        }
        if (args.length < 2 || !List.of("dar", "ver").contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage("§cUso: §fapoiador dar <jogador> [dias] §7| §fapoiador ver <jogador>");
            return true;
        }
        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[1]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            sender.sendMessage("§cJogador \"" + args[1] + "\" nunca entrou no servidor.");
            return true;
        }
        if (args[0].equalsIgnoreCase("ver")) {
            sender.sendMessage(dados.apoiador(alvo.getUniqueId())
                    ? "§d" + alvo.getName() + " é apoiador até " + data(dados.apoiadorAte(alvo.getUniqueId())) + "."
                    : "§7" + alvo.getName() + " não é apoiador agora.");
            return true;
        }
        int dias = getConfig().getInt("apoiador.dias", 30);
        if (args.length > 2) {
            try {
                dias = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignorado) {
                // mantém o padrão
            }
        }
        long ate = dados.renovarApoiador(alvo.getUniqueId(), String.valueOf(alvo.getName()), dias);
        sender.sendMessage("§d" + alvo.getName() + " é apoiador até " + data(ate) + ".");
        if (alvo.getPlayer() != null) {
            alvo.getPlayer().sendMessage("§d§l✦ §dObrigado pelo apoio! §fVocê tem a tag " + Loja.cores(getConfig().getString("apoiador.tag", "&d[Apoiador]"))
                    + " §faté §f" + data(ate) + "§f.");
            refrescarTag(alvo.getPlayer());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("creditos.admin")) {
            opcoes.addAll(command.getName().equalsIgnoreCase("apoiador") ? List.of("dar", "ver") : List.of("dar", "tirar", "ver"));
        } else if (args.length == 2 && sender.hasPermission("creditos.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        }
        String digitado = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }

    // ---------------------------------------------------------------------
    // API pros outros plugins (por reflexão)
    // ---------------------------------------------------------------------

    /** VipPlugin (chat e tab): o código da cor do nome ("§b"), ou null sem cor comprada em uso. */
    public String corDoNome(UUID jogador) {
        var s = cosmeticos.secao(Dados.Categoria.COR, dados.emUso(jogador, Dados.Categoria.COR));
        String cor = s == null ? null : s.getString("cor");
        return cor == null || cor.isBlank() ? null : "§" + cor.charAt(0);
    }

    /** VipPlugin (chat e tab): a tag de apoiador colorida, ou null se não for apoiador agora. */
    public String tagApoiador(UUID jogador) {
        return dados.apoiador(jogador) ? Loja.cores(getConfig().getString("apoiador.tag", "&d[Apoiador]")) : null;
    }

    /** Pede pro VipPlugin refazer o nome na tab (a cor ou a tag de apoiador mudou). */
    void refrescarTag(Player jogador) {
        Plugin vip = getServer().getPluginManager().getPlugin("VipPlugin");
        if (vip == null || !vip.isEnabled()) return;
        try {
            vip.getClass().getMethod("refrescarTagJogador", Player.class).invoke(vip, jogador);
        } catch (ReflectiveOperationException e) {
            getLogger().fine("Não deu pra refazer a tab pelo VipPlugin: " + e.getMessage());
        }
    }

    /**
     * O índice do tier de VIP ativo (0 bronze .. 3 diamante), ou -1 sem VIP. O VIP de evento (prêmio)
     * conta como sem VIP: comprar um VIP de verdade substitui ele.
     */
    int indiceTierAtivo(UUID jogador) {
        Plugin vip = getServer().getPluginManager().getPlugin("VipPlugin");
        if (vip == null || !vip.isEnabled()) return -1;
        try {
            if (Boolean.TRUE.equals(vip.getClass().getMethod("vipDeEvento", UUID.class).invoke(vip, jogador))) return -1;
            Object manager = vip.getClass().getMethod("getVipManager").invoke(vip);
            Object tier = manager.getClass().getMethod("getTierAtivo", UUID.class).invoke(manager, jogador);
            return tier == null ? -1 : Loja.indice(tier.toString());
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** Gera um código de VIP de 30 dias e 1 uso pelo CodeManager do VipPlugin; null se não deu. */
    String gerarCodigoVip(String tier) {
        Plugin vip = getServer().getPluginManager().getPlugin("VipPlugin");
        if (vip == null || !vip.isEnabled()) return null;
        try {
            Class<?> classeTier = vip.getClass().getClassLoader().loadClass("com.example.vip.model.VipTier");
            Object objTier = classeTier.getMethod("fromString", String.class).invoke(null, tier);
            if (objTier == null) return null;
            Object codeManager = vip.getClass().getMethod("getCodeManager").invoke(vip);
            // todo VIP dura 30 dias: gerar(tier, usos, criadoPor, validadeDias)
            Object codigo = codeManager.getClass().getMethod("gerar", classeTier, int.class, String.class, int.class)
                    .invoke(codeManager, objTier, 1, "Loja de créditos", 0);
            return (String) codigo.getClass().getMethod("getCodigo").invoke(codigo);
        } catch (ReflectiveOperationException | ClassCastException e) {
            getLogger().warning("Não foi possível gerar o código de VIP pelo VipPlugin: " + e.getMessage());
            return null;
        }
    }

    private static String data(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(ms));
    }

    public Dados getDados() {
        return dados;
    }

    public Cosmeticos getCosmeticos() {
        return cosmeticos;
    }
}
