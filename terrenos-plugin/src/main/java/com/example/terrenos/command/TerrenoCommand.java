package com.example.terrenos.command;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.manager.TerrenoManager;
import com.example.terrenos.model.Leilao;
import com.example.terrenos.model.Permissao;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.model.TerrenoId;
import com.example.terrenos.util.Formato;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /terreno: compra, imposto, amigos e leilão de imóvel. Staff ({@code terrenos.admin}):
 * /terreno admin dono|remover|leiloar|encerrar|vencer|limpar|reload.
 */
public class TerrenoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("comprar", "auto", "home", "info", "lista", "imposto", "autopagar",
            "amigo", "abandonar", "leiloes", "lance", "divida", "ir");
    private static final List<String> SUBS_ADMIN = List.of("dono", "remover", "leiloar", "encerrar", "vencer", "limpar", "reload");

    private final TerrenosPlugin plugin;

    public TerrenoCommand(TerrenosPlugin plugin) {
        this.plugin = plugin;
    }

    private TerrenoManager terrenos() {
        return plugin.getTerrenoManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "ajuda" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores usam o /terreno. Staff: §f/terreno admin <dono|remover|leiloar|limpar|reload>");
            return true;
        }
        if (plugin.getEconomy() == null && List.of("comprar", "imposto", "lance", "divida").contains(sub)) {
            jogador.sendMessage("§cA economia está indisponível no momento.");
            return true;
        }
        switch (sub) {
            case "comprar" -> comprar(jogador);
            case "auto" -> auto(jogador);
            case "home", "casa" -> home(jogador, args);
            case "info" -> info(jogador, args);
            case "lista" -> lista(jogador);
            case "imposto" -> imposto(jogador, args);
            case "autopagar" -> autoPagar(jogador);
            case "amigo", "amigos" -> amigo(jogador, args);
            case "abandonar" -> abandonar(jogador, args);
            case "leiloes", "leilao" -> leiloes(jogador);
            case "lance" -> lance(jogador, args);
            case "divida" -> divida(jogador, args);
            case "ir", "visitar" -> ir(jogador, args);
            default -> ajuda(jogador);
        }
        return true;
    }

    private void ajuda(Player jogador) {
        jogador.sendMessage("§6§l--- Terrenos ---");
        jogador.sendMessage("§f/terreno auto §7- vai até um terreno à venda");
        jogador.sendMessage("§f/terreno comprar §7- compra o terreno em que você está (" + Formato.valor(terrenos().precoCompra()) + ")");
        jogador.sendMessage("§f/terreno home [n] §7- vai até o seu terreno");
        jogador.sendMessage("§f/terreno info [x;z] §7- dono, imposto e amigos do terreno");
        jogador.sendMessage("§f/terreno lista §7- seus terrenos e o imposto de cada um");
        jogador.sendMessage("§f/terreno imposto [pagar] §7- vê ou paga uma semana de imposto");
        jogador.sendMessage("§f/terreno autopagar §7- liga ou desliga o pagamento automático");
        jogador.sendMessage("§f/terreno amigo add <jogador> [construir] [baus] [usar] §7- o que um amigo pode fazer no terreno em que você está");
        jogador.sendMessage("§f/terreno amigo remover <jogador> §7- tira o amigo");
        jogador.sendMessage("§f/terreno abandonar §7- devolve o terreno (sem reembolso, e ele é limpo)");
        jogador.sendMessage("§f/terreno leiloes §7- leilões de imóvel abertos");
        jogador.sendMessage("§f/terreno lance <x;z> <valor> §7- dá um lance num leilão");
        jogador.sendMessage("§f/terreno ir <x;z> §7- visita um terreno");
    }

    // ---------------------------------------------------------------------
    // Compra
    // ---------------------------------------------------------------------
    private void comprar(Player jogador) {
        TerrenoId id = terrenos().idEm(jogador.getLocation());
        if (id == null) {
            jogador.sendMessage("§cFique dentro de um terreno à venda no mundo de terrenos. §f/terreno auto §cleva você a um.");
            return;
        }
        if (!terrenos().livre(id)) {
            Terreno t = terrenos().get(id);
            jogador.sendMessage(t == null ? "§cEsse terreno não está à venda." : t.emLeilao()
                    ? "§cEsse terreno está em leilão: §f/terreno lance " + id + " <valor>§c."
                    : "§cEsse terreno é de §f" + t.getNomeDono() + "§c.");
            return;
        }
        int limite = plugin.limiteDe(jogador);
        int ocupados = terrenos().ocupados(jogador.getUniqueId());
        if (ocupados >= limite) {
            jogador.sendMessage("§cVocê já tem §f" + ocupados + " §cde §f" + limite + " §cterreno(s)"
                    + " (contando leilões em que está ganhando)." + (limite < 3 ? " §7VIPs podem ter mais: §f/vip tiers§7." : ""));
            return;
        }
        double preco = terrenos().precoCompra();
        if (!plugin.getEconomy().has(jogador, preco)) {
            jogador.sendMessage("§cO terreno custa §f" + Formato.valor(preco) + "§c. Seu saldo: §f"
                    + Formato.valor(plugin.getEconomy().getBalance(jogador)) + "§c.");
            return;
        }
        Terreno t = terrenos().comprar(jogador, id);
        jogador.sendMessage("§a§lTerreno §f" + id + " §a§lcomprado por §f" + Formato.valor(preco) + "§a§l!");
        jogador.sendMessage("§7A primeira semana de imposto já está paga. Depois, são §f"
                + Formato.valor(terrenos().valorImposto(jogador.getUniqueId())) + " §7por semana (muda com o seu rank), pagos"
                + " automaticamente se tiver saldo (§f/terreno autopagar§7). Vence em §f"
                + Formato.tempo(t.getImpostoPagoAte() - System.currentTimeMillis()) + "§7.");
    }

    private void auto(Player jogador) {
        World mundo = plugin.getMundo();
        if (mundo == null) {
            jogador.sendMessage("§cO mundo de terrenos ainda não foi criado.");
            return;
        }
        TerrenoId id = terrenos().livreMaisPerto();
        if (id == null) {
            jogador.sendMessage("§cNão há terrenos à venda agora.");
            return;
        }
        teleportar(jogador, id, true);
        jogador.sendMessage("§aEsse é o terreno §f" + id + "§a, à venda por §f" + Formato.valor(terrenos().precoCompra())
                + "§a. Entre nele e use §f/terreno comprar§a.");
    }

    // ---------------------------------------------------------------------
    // Consultas e teleporte
    // ---------------------------------------------------------------------
    private void home(Player jogador, String[] args) {
        List<Terreno> meus = terrenos().getDe(jogador.getUniqueId());
        if (meus.isEmpty()) {
            jogador.sendMessage("§cVocê não tem terreno. §f/terreno auto §cleva você a um à venda.");
            return;
        }
        int n = 1;
        if (args.length >= 2) {
            try {
                n = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                n = -1;
            }
        }
        if (n < 1 || n > meus.size()) {
            jogador.sendMessage("§cVocê tem §f" + meus.size() + " §cterreno(s): use §f/terreno home 1§c" + (meus.size() > 1 ? " a §f" + meus.size() : "") + "§c.");
            return;
        }
        teleportar(jogador, meus.get(n - 1).getId(), false);
    }

    private void ir(Player jogador, String[] args) {
        TerrenoId id = args.length >= 2 ? TerrenoId.ler(args[1]) : null;
        if (id == null || !terrenos().dentroDaGrade(id)) {
            jogador.sendMessage("§cUso: §f/terreno ir <x;z> §c(ex: §f/terreno ir 0;1§c). Veja os leilões com §f/terreno leiloes§c.");
            return;
        }
        teleportar(jogador, id, true);
    }

    private void teleportar(Player jogador, TerrenoId id, boolean avisar) {
        World mundo = plugin.getMundo();
        if (mundo == null) {
            jogador.sendMessage("§cO mundo de terrenos ainda não foi criado.");
            return;
        }
        double[] p = plugin.getGrade().entrada(id);
        // olhando pro terreno (que fica ao sul da entrada)
        jogador.teleport(new Location(mundo, p[0], p[1], p[2], 0, 10));
        if (avisar) jogador.sendMessage("§7Você está na entrada do terreno §f" + id + "§7.");
    }

    private void info(Player jogador, String[] args) {
        TerrenoId id = args.length >= 2 ? TerrenoId.ler(args[1]) : terrenos().idEm(jogador.getLocation());
        if (id == null) {
            jogador.sendMessage("§cFique dentro de um terreno ou use §f/terreno info <x;z>§c.");
            return;
        }
        Terreno t = terrenos().get(id);
        long agora = System.currentTimeMillis();
        jogador.sendMessage("§6§l--- Terreno " + id + " ---");
        if (t == null) {
            jogador.sendMessage(terrenos().livre(id) ? "§aÀ venda por §f" + Formato.valor(terrenos().precoCompra()) + "§a: §f/terreno comprar"
                    : "§7Fora da área de terrenos, ou sendo limpo.");
            return;
        }
        if (t.emLeilao()) {
            Leilao l = t.getLeilao();
            jogador.sendMessage("§eEm leilão (era de §f" + l.getNomeDonoAnterior() + "§e). Termina em §f" + Formato.tempo(l.getTerminaEm() - agora) + "§e.");
            jogador.sendMessage(l.temLance() ? "§eMaior lance: §f" + Formato.valor(l.getLance()) + " §ede §f" + l.getNomeLicitante()
                    : "§eSem lances. Mínimo: §f" + Formato.valor(terrenos().lanceMinimo()));
            jogador.sendMessage("§eLance: §f/terreno lance " + id + " " + (long) Math.ceil(terrenos().proximoLanceMinimo(l)));
            return;
        }
        jogador.sendMessage("§7Dono: §f" + t.getNomeDono());
        if (t.ehDonoOuAmigo(jogador.getUniqueId()) || plugin.isAdmin(jogador)) {
            jogador.sendMessage("§7Imposto: §f" + Formato.valor(terrenos().valorImposto(t.getDono())) + " §7por semana, "
                    + (terrenos().atrasado(t, agora) ? "§cATRASADO §7(vai a leilão em §f"
                    + Formato.tempo(t.getImpostoPagoAte() + terrenos().toleranciaMs() - agora) + "§7)"
                    : "pago por mais §f" + Formato.tempo(t.getImpostoPagoAte() - agora))
                    + " §7· automático: " + (t.isAutoPagar() ? "§aligado" : "§cdesligado"));
            jogador.sendMessage("§7Amigos: §f" + (t.getAmigos().isEmpty() ? "nenhum" : String.join(", ", nomes(t))));
        }
    }

    private void lista(Player jogador) {
        List<Terreno> meus = terrenos().getDe(jogador.getUniqueId());
        jogador.sendMessage("§6§l--- Seus terrenos (" + meus.size() + " de " + plugin.limiteDe(jogador) + ") ---");
        if (meus.isEmpty()) {
            jogador.sendMessage("§7Nenhum. §f/terreno auto §7leva você a um à venda.");
            return;
        }
        long agora = System.currentTimeMillis();
        for (int i = 0; i < meus.size(); i++) {
            Terreno t = meus.get(i);
            jogador.sendMessage("§f" + (i + 1) + ". §e" + t.getId() + " §7- "
                    + (terrenos().atrasado(t, agora) ? "§cimposto atrasado" : "imposto pago por mais §f" + Formato.tempo(t.getImpostoPagoAte() - agora))
                    + " §7(§f/terreno home " + (i + 1) + "§7)");
        }
    }

    // ---------------------------------------------------------------------
    // Imposto
    // ---------------------------------------------------------------------
    private void imposto(Player jogador, String[] args) {
        List<Terreno> meus = terrenos().getDe(jogador.getUniqueId());
        if (meus.isEmpty()) {
            jogador.sendMessage("§7Você não tem terreno, então não paga imposto.");
            return;
        }
        double valor = terrenos().valorImposto(jogador.getUniqueId());
        if (args.length < 2 || !args[1].equalsIgnoreCase("pagar")) {
            jogador.sendMessage("§7Imposto: §f" + Formato.valor(valor) + " §7por semana, por terreno ("
                    + Formato.numero(plugin.getConfig().getDouble("imposto.percentual-do-rank", 3)) + "% do preço do seu rank atual,"
                    + " no mínimo " + Formato.valor(plugin.getConfig().getDouble("imposto.minimo", 1000)) + ").");
            lista(jogador);
            jogador.sendMessage("§7Pague uma semana de todos com §f/terreno imposto pagar§7 (até §f"
                    + plugin.getConfig().getInt("imposto.semanas-adiantadas-maximo", 4) + " §7semanas adiantadas).");
            return;
        }
        int pagos = 0;
        for (Terreno t : meus) {
            if (terrenos().pagarImposto(t)) pagos++;
        }
        if (pagos == 0) {
            jogador.sendMessage(plugin.getEconomy().has(jogador, valor)
                    ? "§cSeus terrenos já estão com o máximo de semanas adiantadas."
                    : "§cSaldo insuficiente: o imposto é §f" + Formato.valor(valor) + " §cpor terreno.");
            return;
        }
        jogador.sendMessage("§aImposto pago: §f" + pagos + " §aterreno(s), §f" + Formato.valor(valor * pagos) + "§a.");
        if (pagos < meus.size()) jogador.sendMessage("§7Os outros já estavam no máximo adiantado ou faltou saldo.");
    }

    private void autoPagar(Player jogador) {
        List<Terreno> meus = terrenos().getDe(jogador.getUniqueId());
        if (meus.isEmpty()) {
            jogador.sendMessage("§7Você não tem terreno.");
            return;
        }
        boolean ligar = !meus.get(0).isAutoPagar();
        meus.forEach(t -> t.setAutoPagar(ligar));
        terrenos().salvar();
        jogador.sendMessage(ligar ? "§aPagamento automático do imposto ligado: no vencimento, ele sai do seu saldo."
                : "§7Pagamento automático desligado. Pague com §f/terreno imposto pagar§7 antes de vencer.");
    }

    // ---------------------------------------------------------------------
    // Amigos e abandono
    // ---------------------------------------------------------------------
    /** O terreno em que o jogador está, se for dele; senão avisa e retorna null. */
    private Terreno meuTerrenoAqui(Player jogador) {
        Terreno t = terrenos().getEm(jogador.getLocation());
        if (t == null || !jogador.getUniqueId().equals(t.getDono())) {
            jogador.sendMessage("§cFique dentro de um terreno seu.");
            return null;
        }
        return t;
    }

    private void amigo(Player jogador, String[] args) {
        Terreno t = meuTerrenoAqui(jogador);
        if (t == null) return;
        if (args.length < 3) {
            jogador.sendMessage("§7Amigos do terreno §f" + t.getId() + "§7: §f" + (t.getAmigos().isEmpty() ? "nenhum" : String.join(", ", nomes(t))));
            jogador.sendMessage("§7Use §f/terreno amigo add <jogador> [construir] [baus] [usar] §7(sem nada: tudo) ou §f/terreno amigo remover <jogador>§7.");
            return;
        }
        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[2]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            jogador.sendMessage("§cJogador \"" + args[2] + "\" não encontrado.");
            return;
        }
        boolean adicionar = args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("adicionar");
        if (adicionar) {
            if (alvo.getUniqueId().equals(jogador.getUniqueId())) {
                jogador.sendMessage("§cO terreno já é seu.");
                return;
            }
            Set<Permissao> perms = EnumSet.noneOf(Permissao.class);
            for (int i = 3; i < args.length; i++) {
                Permissao p = Permissao.ler(args[i]);
                if (p == null) {
                    jogador.sendMessage("§cPermissão desconhecida: §f" + args[i] + "§c. Use §fconstruir§c, §fbaus §ce §fusar§c.");
                    return;
                }
                perms.add(p);
            }
            if (perms.isEmpty()) perms = Terreno.todas();
            t.getAmigos().put(alvo.getUniqueId(), perms);
            jogador.sendMessage("§a" + alvo.getName() + " agora pode §f" + descrever(perms) + " §ano terreno §f" + t.getId() + "§a.");
        } else if (t.getAmigos().remove(alvo.getUniqueId()) != null) {
            jogador.sendMessage("§7" + alvo.getName() + " não é mais amigo do terreno §f" + t.getId() + "§7.");
        } else {
            jogador.sendMessage("§c" + alvo.getName() + " não era amigo desse terreno.");
            return;
        }
        terrenos().salvar();
    }

    /** "Fulano (construir, baus)" pra cada amigo. */
    private List<String> nomes(Terreno t) {
        List<String> nomes = new ArrayList<>();
        t.getAmigos().forEach((amigo, perms) -> {
            String nome = Bukkit.getOfflinePlayer(amigo).getName();
            nomes.add((nome != null ? nome : amigo.toString().substring(0, 8)) + " §7(" + descrever(perms) + ")§f");
        });
        return nomes;
    }

    private static String descrever(Set<Permissao> perms) {
        if (perms.containsAll(Terreno.todas())) return "tudo";
        return perms.isEmpty() ? "nada" : String.join(", ", perms.stream().map(Permissao::nome).toList());
    }

    private void abandonar(Player jogador, String[] args) {
        Terreno t = meuTerrenoAqui(jogador);
        if (t == null) return;
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            jogador.sendMessage("§c§lAtenção: §cabandonar devolve o terreno §f" + t.getId()
                    + " §csem reembolso, e TUDO o que tem dentro é apagado. Pra confirmar: §f/terreno abandonar confirmar");
            return;
        }
        terrenos().abandonar(t);
        jogador.sendMessage("§7Terreno §f" + t.getId() + " §7abandonado. Ele está sendo limpo e volta à venda.");
    }

    // ---------------------------------------------------------------------
    // Leilão de imóvel
    // ---------------------------------------------------------------------
    private void leiloes(Player jogador) {
        List<Terreno> leiloes = terrenos().getLeiloes();
        if (leiloes.isEmpty()) {
            jogador.sendMessage("§7Nenhum leilão de imóvel aberto.");
            return;
        }
        long agora = System.currentTimeMillis();
        jogador.sendMessage("§6§l--- Leilões de imóvel ---");
        for (Terreno t : leiloes) {
            Leilao l = t.getLeilao();
            jogador.sendMessage("§e" + t.getId() + " §7(era de " + l.getNomeDonoAnterior() + ") - "
                    + (l.temLance() ? "§f" + Formato.valor(l.getLance()) + " §7de " + l.getNomeLicitante() : "§7sem lances")
                    + " §7- termina em §f" + Formato.tempo(l.getTerminaEm() - agora));
        }
        jogador.sendMessage("§7Visite com §f/terreno ir <x;z> §7e dê lance com §f/terreno lance <x;z> <valor>§7.");
    }

    private void lance(Player jogador, String[] args) {
        TerrenoId id = args.length >= 3 ? TerrenoId.ler(args[1]) : null;
        double valor = args.length >= 3 ? Formato.lerPreco(args[2]) : -1;
        if (id == null || valor <= 0) {
            jogador.sendMessage("§cUso: §f/terreno lance <x;z> <valor> §c(ex: §f/terreno lance 0;1 60000§c)");
            return;
        }
        Terreno t = terrenos().get(id);
        if (t == null || !t.emLeilao()) {
            jogador.sendMessage("§cO terreno §f" + id + " §cnão está em leilão. Veja com §f/terreno leiloes§c.");
            return;
        }
        Leilao l = t.getLeilao();
        UUID uuid = jogador.getUniqueId();
        if (uuid.equals(l.getLicitante())) {
            jogador.sendMessage("§cVocê já tem o maior lance desse leilão.");
            return;
        }
        if (uuid.equals(l.getDonoAnterior()) && !l.isDividaPaga()) {
            jogador.sendMessage("§cEsse terreno era seu. Pra dar lance, pague antes a dívida de §f" + Formato.valor(l.getDivida())
                    + " §ccom §f/terreno divida pagar " + id + "§c.");
            return;
        }
        double minimo = terrenos().proximoLanceMinimo(l);
        if (valor < minimo) {
            jogador.sendMessage("§cO lance mínimo agora é §f" + Formato.valor(minimo) + "§c.");
            return;
        }
        int limite = plugin.limiteDe(jogador);
        if (terrenos().ocupados(uuid) >= limite) {
            jogador.sendMessage("§cVocê já tem §f" + limite + " §cterreno(s) (contando leilões em que está ganhando), o seu limite.");
            return;
        }
        if (!plugin.getEconomy().has(jogador, valor)) {
            jogador.sendMessage("§cSaldo insuficiente. Seu saldo: §f" + Formato.valor(plugin.getEconomy().getBalance(jogador)) + "§c.");
            return;
        }
        terrenos().darLance(jogador, t, valor);
        jogador.sendMessage("§aLance de §f" + Formato.valor(valor) + " §adado. O valor fica retido e volta se alguém der mais.");
    }

    private void divida(Player jogador, String[] args) {
        List<Terreno> meusLeiloes = new ArrayList<>();
        for (Terreno t : terrenos().getLeiloes()) {
            if (jogador.getUniqueId().equals(t.getLeilao().getDonoAnterior()) && !t.getLeilao().isDividaPaga()) meusLeiloes.add(t);
        }
        if (meusLeiloes.isEmpty()) {
            jogador.sendMessage("§7Você não tem dívida de imposto de terreno.");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("pagar")) {
            for (Terreno t : meusLeiloes) {
                jogador.sendMessage("§cTerreno §f" + t.getId() + "§c: dívida de §f" + Formato.valor(t.getLeilao().getDivida())
                        + "§c. Pague com §f/terreno divida pagar " + t.getId() + " §cpra poder dar lance no leilão.");
            }
            return;
        }
        TerrenoId id = TerrenoId.ler(args[2]);
        Terreno t = meusLeiloes.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (t == null) {
            jogador.sendMessage("§cVocê não tem dívida no terreno §f" + args[2] + "§c.");
            return;
        }
        double divida = t.getLeilao().getDivida();
        if (!plugin.getEconomy().has(jogador, divida)) {
            jogador.sendMessage("§cA dívida é §f" + Formato.valor(divida) + "§c. Seu saldo: §f" + Formato.valor(plugin.getEconomy().getBalance(jogador)) + "§c.");
            return;
        }
        terrenos().pagarDivida(jogador, t);
        jogador.sendMessage("§aDívida paga. Agora você pode dar lance: §f/terreno lance " + t.getId() + " <valor>§a.");
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(TerrenosPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            plugin.carregarGrade();
            sender.sendMessage("§aConfig recarregado. §7(mudar o tamanho da grade não regenera o mundo já criado.)");
            return;
        }
        TerrenoId id = args.length >= 3 ? TerrenoId.ler(args[2]) : null;
        if (id == null && args.length < 3 && sender instanceof Player p) id = terrenos().idEm(p.getLocation());
        if (!SUBS_ADMIN.contains(sub) || id == null) {
            sender.sendMessage("§cUso: §f/terreno admin dono <x;z> <jogador> §7| §fremover <x;z> §7| §fleiloar <x;z> §7| §fencerrar <x;z>"
                    + " §7| §fvencer <x;z> §7| §flimpar <x;z> §7| §freload");
            return;
        }
        Terreno t = terrenos().get(id);
        switch (sub) {
            case "dono" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUso: §f/terreno admin dono <x;z> <jogador>");
                    return;
                }
                OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[3]);
                terrenos().definirDono(id, alvo);
                sender.sendMessage("§aTerreno §f" + id + " §aagora é de §f" + alvo.getName() + " §a(imposto pago por uma semana).");
            }
            case "remover" -> {
                terrenos().remover(id);
                sender.sendMessage("§aTerreno §f" + id + " §aliberado (os blocos ficam; use §f/terreno admin limpar " + id + "§a pra apagar).");
            }
            case "leiloar" -> {
                if (t == null || !t.temDono()) {
                    sender.sendMessage("§cO terreno §f" + id + " §cnão tem dono.");
                    return;
                }
                terrenos().confiscar(t, "pela staff");
                sender.sendMessage("§aTerreno §f" + id + " §aem leilão.");
            }
            case "encerrar" -> {
                if (t == null || !t.emLeilao()) {
                    sender.sendMessage("§cO terreno §f" + id + " §cnão está em leilão.");
                    return;
                }
                t.getLeilao().setTerminaEm(System.currentTimeMillis());
                sender.sendMessage("§aO leilão do terreno §f" + id + " §atermina agora.");
            }
            case "vencer" -> {
                if (t == null || !t.temDono()) {
                    sender.sendMessage("§cO terreno §f" + id + " §cnão tem dono.");
                    return;
                }
                // como se o dono tivesse passado da tolerância: na próxima verificação (até 30 s), o
                // pagamento automático tenta cobrar e, sem saldo, o terreno vai a leilão
                t.setImpostoPagoAte(System.currentTimeMillis() - terrenos().toleranciaMs() - 1);
                terrenos().salvar();
                sender.sendMessage("§aImposto do terreno §f" + id + " §avencido além da tolerância. Sem pagamento, vai a leilão em até 30 s.");
            }
            case "limpar" -> {
                if (t != null) {
                    sender.sendMessage("§cO terreno §f" + id + " §ctem dono ou está em leilão. Use §f/terreno admin remover " + id + " §cantes.");
                    return;
                }
                plugin.getLimpador().limpar(id);
                sender.sendMessage("§aLimpando o terreno §f" + id + "§a.");
            }
            default -> {
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.addAll(SUBS);
            if (sender.hasPermission(TerrenosPlugin.PERMISSAO_ADMIN)) opcoes.add("admin");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "amigo" -> opcoes.addAll(List.of("add", "remover"));
                case "imposto" -> opcoes.add("pagar");
                case "divida" -> opcoes.add("pagar");
                case "abandonar" -> opcoes.add("confirmar");
                case "lance", "ir", "info" -> terrenos().getLeiloes().forEach(t -> opcoes.add(t.getId().toString()));
                case "admin" -> {
                    if (sender.hasPermission(TerrenosPlugin.PERMISSAO_ADMIN)) opcoes.addAll(SUBS_ADMIN);
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("amigo")) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (args.length >= 4 && args[0].equalsIgnoreCase("amigo") && args[1].equalsIgnoreCase("add")) {
            for (Permissao p : Permissao.values()) opcoes.add(p.nome());
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefixo)).toList();
    }
}
