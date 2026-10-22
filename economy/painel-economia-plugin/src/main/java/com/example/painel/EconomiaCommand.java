package com.example.painel;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /economia [resumo|dias [n]|top [n]|recalcular] — só staff. */
public class EconomiaCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final PainelEconomiaPlugin plugin;

    public EconomiaCommand(PainelEconomiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.temEconomia()) {
            sender.sendMessage("§cNenhuma economia registrada no Vault — sem dados pra mostrar.");
            return true;
        }

        String sub = args.length == 0 ? "resumo" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "resumo" -> resumo(sender);
            case "dias" -> dias(sender, lerNumero(args, 1, 7, 30));
            case "top" -> top(sender, lerNumero(args, 1, 10, plugin.getConfig().getInt("circulacao.top-maximo", 20)));
            case "recalcular" -> {
                plugin.calcularCirculacao(true);
                sender.sendMessage("§aCirculação recalculada.");
                resumo(sender);
            }
            default -> {
                sender.sendMessage("§e§l--- Painel da economia ---");
                sender.sendMessage("§f/economia §7- resumo: circulação e fluxo de hoje por origem");
                sender.sendMessage("§f/economia dias [n] §7- entrou/saiu/líquido nos últimos n dias");
                sender.sendMessage("§f/economia top [n] §7- maiores saldos");
                sender.sendMessage("§f/economia recalcular §7- recalcula a circulação agora (ignora o cache)");
            }
        }
        return true;
    }

    private void resumo(CommandSender sender) {
        PainelEconomiaPlugin.Circulacao circ = plugin.calcularCirculacao(false);
        LocalDate hoje = LocalDate.now();
        Double ontem = plugin.getFluxoManager().getCirculacao(hoje.minusDays(1));

        sender.sendMessage("§e§l--- Economia ---");
        sender.sendMessage("§7Em circulação: §f" + valor(circ.total())
                + (ontem != null ? " §7(" + variacao(circ.total() - ontem) + " §7desde ontem)" : ""));
        sender.sendMessage("§7Contas com saldo: §f" + circ.contasComSaldo()
                + " §7| Média: §f" + valor(circ.contasComSaldo() > 0 ? circ.total() / circ.contasComSaldo() : 0));

        if (!plugin.estaInterceptando()) {
            sender.sendMessage("§cFluxo por origem indisponível (o painel não conseguiu se colocar na frente da economia — veja o console).");
            return;
        }

        Map<String, double[]> origens = plugin.getFluxoManager().getDia(hoje);
        double[] total = plugin.totalDoDia(hoje);
        sender.sendMessage("§e§lHoje: §a+" + valor(total[FluxoManager.ENTROU]) + " §c-" + valor(total[FluxoManager.SAIU])
                + " §7= " + variacao(total[FluxoManager.ENTROU] - total[FluxoManager.SAIU]));
        if (origens.isEmpty()) {
            sender.sendMessage("§7  Nenhuma movimentação registrada hoje.");
            return;
        }

        List<Map.Entry<String, double[]>> ordenadas = new ArrayList<>(origens.entrySet());
        ordenadas.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) ->
                Math.abs(e.getValue()[FluxoManager.ENTROU] - e.getValue()[FluxoManager.SAIU])).reversed());
        for (Map.Entry<String, double[]> e : ordenadas) {
            double entrou = e.getValue()[FluxoManager.ENTROU];
            double saiu = e.getValue()[FluxoManager.SAIU];
            sender.sendMessage("  §f" + e.getKey() + "§7: §a+" + valor(entrou) + " §c-" + valor(saiu)
                    + " §7= " + variacao(entrou - saiu));
        }
        sender.sendMessage("§8Transferências entre jogadores aparecem nas duas colunas; o líquido mostra o efeito real.");
    }

    private void dias(CommandSender sender, int quantidade) {
        sender.sendMessage("§e§l--- Últimos " + quantidade + " dias ---");
        LocalDate hoje = LocalDate.now();
        for (int i = quantidade - 1; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            double[] total = plugin.totalDoDia(dia);
            Double circulacao = plugin.getFluxoManager().getCirculacao(dia);
            sender.sendMessage("§f" + FORMATO_DIA.format(dia) + "§7: §a+" + valor(total[FluxoManager.ENTROU])
                    + " §c-" + valor(total[FluxoManager.SAIU]) + " §7= "
                    + variacao(total[FluxoManager.ENTROU] - total[FluxoManager.SAIU])
                    + (circulacao != null ? " §8| circulação " + valor(circulacao) : ""));
        }
    }

    private void top(CommandSender sender, int quantidade) {
        PainelEconomiaPlugin.Circulacao circ = plugin.calcularCirculacao(false);
        sender.sendMessage("§e§l--- Maiores saldos ---");
        List<PainelEconomiaPlugin.Fortuna> ranking = circ.ranking();
        for (int i = 0; i < Math.min(quantidade, ranking.size()); i++) {
            PainelEconomiaPlugin.Fortuna f = ranking.get(i);
            double fatia = circ.total() > 0 ? f.saldo() / circ.total() * 100 : 0;
            sender.sendMessage("§f" + (i + 1) + "º " + f.nome() + " §7- §f" + valor(f.saldo())
                    + String.format(Locale.ROOT, " §8(%.1f%% do total)", fatia));
        }
    }

    private int lerNumero(String[] args, int indice, int padrao, int maximo) {
        if (args.length <= indice) return padrao;
        try {
            return Math.max(1, Math.min(maximo, Integer.parseInt(args[indice])));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private String valor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    private String variacao(double valor) {
        return (valor >= 0 ? "§a+" : "§c-") + valor(Math.abs(valor));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opcoes = new ArrayList<>(List.of("resumo", "dias", "top", "recalcular"));
            opcoes.removeIf(o -> !o.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return opcoes;
        }
        return List.of();
    }
}
