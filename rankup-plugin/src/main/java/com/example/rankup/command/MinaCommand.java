package com.example.rankup.command;

import com.example.rankup.RankupPlugin;
import com.example.rankup.manager.MinaManager;
import com.example.rankup.model.BlocoPos;
import com.example.rankup.model.Mina;
import com.example.rankup.model.Rank;
import com.example.rankup.util.Formato;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /mina — lista as minas. /mina &lt;id&gt; — teleporta (se tiver o rank).
 * Staff (rankup.admin): /mina admin pos1|pos2|criar|deletar|bloco|spawn|rank|intervalo|percentual|resetar|info.
 */
public class MinaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS_ADMIN = List.of("pos1", "pos2", "criar", "deletar", "bloco", "spawn",
            "rank", "intervalo", "percentual", "resetar", "info");

    private final RankupPlugin plugin;
    // Seleção de área da staff
    private final Map<UUID, World> mundoSelecao = new HashMap<>();
    private final Map<UUID, BlocoPos> pos1 = new HashMap<>();
    private final Map<UUID, BlocoPos> pos2 = new HashMap<>();

    public MinaCommand(RankupPlugin plugin) {
        this.plugin = plugin;
    }

    private MinaManager minas() {
        return plugin.getMinaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            listar(sender);
        } else if (args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
        } else {
            teleportar(sender, args[0]);
        }
        return true;
    }

    private void listar(CommandSender sender) {
        if (minas().getMinas().isEmpty()) {
            sender.sendMessage("§7Nenhuma mina criada ainda.");
            return;
        }
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : null;
        sender.sendMessage("§6§l--- Minas ---");
        for (Mina mina : minas().getMinas()) {
            Rank rank = plugin.getRankManager().getRank(mina.getRankMinimo());
            boolean acesso = uuid == null || plugin.getRankManager().temAcesso(uuid, mina.getRankMinimo());
            sender.sendMessage((acesso ? "§a✔ " : "§c✖ ") + "§f" + mina.getId()
                    + " §7(rank " + (rank != null ? rank.nomeColorido() : "§fqualquer") + "§7)"
                    + (mina.isResetando() ? " §e[resetando]" : ""));
        }
        sender.sendMessage("§7Use §f/mina <id> §7pra ir até uma mina.");
    }

    private void teleportar(CommandSender sender, String id) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem ir pras minas.");
            return;
        }
        Mina mina = minas().get(id);
        if (mina == null) {
            jogador.sendMessage("§cMina §f" + id + " §cnão existe. Veja §f/mina§c.");
            return;
        }
        if (!plugin.getRankManager().temAcesso(jogador.getUniqueId(), mina.getRankMinimo())
                && !jogador.hasPermission(RankupPlugin.PERMISSAO_ADMIN)) {
            Rank rank = plugin.getRankManager().getRank(mina.getRankMinimo());
            jogador.sendMessage("§cA mina §f" + mina.getId() + " §cé a partir do rank " + rank.nomeColorido() + "§c.");
            return;
        }
        Location destino = mina.getLocalSpawn();
        if (destino == null) {
            jogador.sendMessage("§cO mundo dessa mina não está carregado.");
            return;
        }
        jogador.teleport(destino);
        jogador.sendMessage("§aVocê foi pra mina §f" + mina.getId() + "§a.");
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(RankupPlugin.PERMISSAO_ADMIN)) {
            sender.sendMessage("§cVocê não tem permissão.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!SUBS_ADMIN.contains(sub)) {
            sender.sendMessage("§cUso: §f/mina admin <" + String.join("|", SUBS_ADMIN) + ">");
            return;
        }
        if (sub.equals("pos1") || sub.equals("pos2") || sub.equals("criar") || sub.equals("spawn")) {
            if (!(sender instanceof Player jogador)) {
                sender.sendMessage("§cEsse comando precisa ser usado no jogo.");
                return;
            }
            switch (sub) {
                case "pos1", "pos2" -> marcarPosicao(jogador, sub);
                case "criar" -> criar(jogador, args);
                default -> definirSpawn(jogador, args);
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUso: §f/mina admin " + sub + " <id>" + parametros(sub));
            return;
        }
        Mina mina = minas().get(args[2]);
        if (mina == null) {
            sender.sendMessage("§cMina §f" + args[2] + " §cnão existe.");
            return;
        }
        switch (sub) {
            case "deletar" -> {
                minas().remover(mina);
                sender.sendMessage("§aMina §f" + mina.getId() + " §aremovida. Os blocos ficam onde estão.");
            }
            case "bloco" -> bloco(sender, mina, args);
            case "rank" -> {
                Rank rank = args.length > 3 ? plugin.getRankManager().getRank(args[3]) : null;
                if (rank == null) {
                    sender.sendMessage("§cUso: §f/mina admin rank <id> <rank> §c(veja /ranks)");
                    return;
                }
                mina.setRankMinimo(rank.id());
                minas().salvar();
                sender.sendMessage("§aA mina §f" + mina.getId() + " §aagora é a partir do rank " + rank.nomeColorido() + "§a.");
            }
            case "intervalo" -> {
                Integer minutos = args.length > 3 ? inteiro(args[3]) : null;
                if (minutos == null || minutos < 0) {
                    sender.sendMessage("§cUso: §f/mina admin intervalo <id> <minutos> §c(0 = só por %)");
                    return;
                }
                mina.setIntervaloMinutos(minutos);
                minas().agendarProximoReset(mina);
                minas().salvar();
                sender.sendMessage("§aA mina §f" + mina.getId() + " §areseta " + (minutos > 0 ? "a cada §f" + minutos + " min" : "só por %") + "§a.");
            }
            case "percentual" -> {
                Integer pct = args.length > 3 ? inteiro(args[3]) : null;
                if (pct == null || pct < 0 || pct > 100) {
                    sender.sendMessage("§cUso: §f/mina admin percentual <id> <0-100> §c(0 = só por tempo)");
                    return;
                }
                mina.setResetPercentual(pct);
                minas().salvar();
                sender.sendMessage("§aA mina §f" + mina.getId() + " §areseta "
                        + (pct > 0 ? "quando §f" + pct + "% §afor minerada" : "só por tempo") + "§a.");
            }
            case "resetar" -> {
                if (mina.isResetando()) {
                    sender.sendMessage("§eA mina já está sendo resetada.");
                    return;
                }
                minas().resetar(mina, "staff");
                sender.sendMessage("§aResetando a mina §f" + mina.getId() + "§a.");
            }
            case "info" -> info(sender, mina);
            default -> {
            }
        }
    }

    private String parametros(String sub) {
        return switch (sub) {
            case "bloco" -> " <bloco> <peso>";
            case "rank" -> " <rank>";
            case "intervalo" -> " <minutos>";
            case "percentual" -> " <0-100>";
            default -> "";
        };
    }

    private void marcarPosicao(Player jogador, String qual) {
        // Bloco pra onde está olhando; sem alvo, o bloco dos pés
        Block alvo = jogador.getTargetBlockExact(8);
        BlocoPos pos = BlocoPos.de(alvo != null ? alvo : jogador.getLocation().getBlock());
        World mundoAnterior = mundoSelecao.put(jogador.getUniqueId(), jogador.getWorld());
        if (mundoAnterior != null && !mundoAnterior.equals(jogador.getWorld())) {
            // trocou de mundo no meio da seleção: descarta o outro canto
            (qual.equals("pos1") ? pos2 : pos1).remove(jogador.getUniqueId());
        }
        (qual.equals("pos1") ? pos1 : pos2).put(jogador.getUniqueId(), pos);
        jogador.sendMessage("§a" + qual + " marcada em §f" + pos + "§a. "
                + "§7Marque dois cantos opostos (do fundo até o topo da mina).");
    }

    private void criar(Player jogador, String[] args) {
        if (args.length < 4) {
            jogador.sendMessage("§cUso: §f/mina admin criar <id> <rank>");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,24}") || id.equals("admin")) {
            jogador.sendMessage("§cId inválido: use letras minúsculas, números, - ou _ (até 24 caracteres).");
            return;
        }
        if (minas().get(id) != null) {
            jogador.sendMessage("§cJá existe uma mina §f" + id + "§c.");
            return;
        }
        Rank rank = plugin.getRankManager().getRank(args[3]);
        if (rank == null) {
            jogador.sendMessage("§cRank §f" + args[3] + " §cnão existe. Veja /ranks.");
            return;
        }
        UUID uuid = jogador.getUniqueId();
        BlocoPos a = pos1.get(uuid);
        BlocoPos b = pos2.get(uuid);
        World mundo = mundoSelecao.get(uuid);
        if (a == null || b == null || mundo == null) {
            jogador.sendMessage("§cMarque os dois cantos antes, com §f/mina admin pos1 §ce §f/mina admin pos2§c.");
            return;
        }
        if (minas().sobrepoeAlguma(mundo.getName(), a, b)) {
            jogador.sendMessage("§cEssa área sobrepõe outra mina.");
            return;
        }
        Mina mina = minas().criar(id, mundo, a, b, rank.id());
        pos1.remove(uuid);
        pos2.remove(uuid);
        jogador.sendMessage("§aMina §f" + id + " §acriada (§f" + mina.getVolume() + " blocos§a), a partir do rank "
                + rank.nomeColorido() + "§a. Por enquanto ela é só de pedra: defina os blocos com §f/mina admin bloco "
                + id + " <bloco> <peso>§a e depois §f/mina admin resetar " + id + "§a.");
    }

    private void definirSpawn(Player jogador, String[] args) {
        if (args.length < 3) {
            jogador.sendMessage("§cUso: §f/mina admin spawn <id> §7(marca onde você está)");
            return;
        }
        Mina mina = minas().get(args[2]);
        if (mina == null) {
            jogador.sendMessage("§cMina §f" + args[2] + " §cnão existe.");
            return;
        }
        if (!jogador.getWorld().getName().equals(mina.getMundo())) {
            jogador.sendMessage("§cO ponto de teleporte precisa ser no mesmo mundo da mina.");
            return;
        }
        if (mina.contem(jogador.getLocation())) {
            jogador.sendMessage("§cO ponto de teleporte não pode ser dentro da mina (o reset soterraria quem chegar).");
            return;
        }
        mina.setSpawn(jogador.getLocation());
        minas().salvar();
        jogador.sendMessage("§aPonto de teleporte da mina §f" + mina.getId() + " §amarcado aqui.");
    }

    private void bloco(CommandSender sender, Mina mina, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUso: §f/mina admin bloco <id> <bloco> <peso> §c(peso 0 tira o bloco)");
            return;
        }
        Material material = Material.matchMaterial(args[3]);
        if (material == null || !material.isBlock() || material.isAir()) {
            sender.sendMessage("§cBloco inválido: §f" + args[3]);
            return;
        }
        double peso;
        try {
            peso = Double.parseDouble(args[4].replace(',', '.'));
        } catch (NumberFormatException e) {
            peso = -1;
        }
        if (!Double.isFinite(peso) || peso < 0) {
            sender.sendMessage("§cPeso inválido: §f" + args[4] + " §c(um número, ex: 30)");
            return;
        }
        if (peso == 0) {
            mina.getComposicao().remove(material);
        } else {
            mina.getComposicao().put(material, peso);
        }
        minas().salvar();
        sender.sendMessage("§aComposição da mina §f" + mina.getId() + "§a: " + composicao(mina));
        sender.sendMessage("§7Vale no próximo reset (§f/mina admin resetar " + mina.getId() + "§7 pra ver agora).");
    }

    private void info(CommandSender sender, Mina mina) {
        Rank rank = plugin.getRankManager().getRank(mina.getRankMinimo());
        sender.sendMessage("§6§l--- Mina " + mina.getId() + " ---");
        sender.sendMessage("§7Mundo: §f" + mina.getMundo() + " §7| de §f" + mina.getMin() + " §7até §f" + mina.getMax()
                + " §7(" + mina.getVolume() + " blocos)");
        sender.sendMessage("§7Rank mínimo: " + (rank != null ? rank.nomeColorido() : "§c" + mina.getRankMinimo() + " (não existe)"));
        sender.sendMessage("§7Blocos: " + composicao(mina));
        long restante = mina.getProximoReset() == Long.MAX_VALUE ? -1 : mina.getProximoReset() - System.currentTimeMillis();
        sender.sendMessage("§7Reset: " + (mina.getIntervaloMinutos() > 0 ? "a cada §f" + mina.getIntervaloMinutos() + " min" : "§fsem tempo")
                + (mina.getResetPercentual() > 0 ? " §7ou com §f" + (int) mina.getResetPercentual() + "% §7minerada" : "")
                + (restante >= 0 ? " §7| próximo em §f" + Formato.tempo(restante) : ""));
        sender.sendMessage(String.format(Locale.forLanguageTag("pt-BR"), "§7Minerado desde o último reset: §f%d §7(%.1f%%)",
                mina.getQuebrados(), mina.getQuebrados() * 100.0 / Math.max(1, mina.getVolume())));
        sender.sendMessage("§7Teleporte: " + (mina.getSpawn() != null ? "§fmarcado" : "§fem cima do centro") + " §7(/mina admin spawn)");
    }

    private String composicao(Mina mina) {
        double soma = mina.getComposicao().values().stream().mapToDouble(Double::doubleValue).sum();
        if (soma <= 0) return "§cvazia";
        List<String> partes = new ArrayList<>();
        mina.getComposicao().forEach((material, peso) -> partes.add(String.format(Locale.forLanguageTag("pt-BR"),
                "§f%s §7%.1f%%", Formato.nomeMaterial(material), peso * 100 / soma)));
        return String.join("§7, ", partes);
    }

    private Integer inteiro(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(RankupPlugin.PERMISSAO_ADMIN);
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            minas().getMinas().forEach(m -> opcoes.add(m.getId()));
            if (admin) opcoes.add("admin");
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
                opcoes.addAll(SUBS_ADMIN);
            } else if (args.length == 3 && !sub.equals("pos1") && !sub.equals("pos2") && !sub.equals("criar")) {
                minas().getMinas().forEach(m -> opcoes.add(m.getId()));
            } else if (args.length == 4 && (sub.equals("criar") || sub.equals("rank"))) {
                plugin.getRankManager().getRanks().forEach(r -> opcoes.add(r.id()));
            } else if (args.length == 4 && sub.equals("bloco")) {
                for (Material material : Material.values()) {
                    if (material.isBlock() && !material.isAir() && !material.isLegacy()) {
                        opcoes.add(material.name().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcoes.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefixo)).limit(80).toList();
    }
}
