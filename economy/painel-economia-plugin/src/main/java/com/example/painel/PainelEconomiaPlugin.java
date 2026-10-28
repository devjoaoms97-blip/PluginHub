package com.example.painel;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Painel da economia pra staff: dinheiro em circulação, quanto entrou e saiu por dia (separado
 * por plugin) e maiores fortunas.
 *
 * Funciona colocando um intermediário ({@link EconomiaInterceptada}) na frente da economia do
 * Vault, com prioridade máxima. Pra os outros plugins pegarem esse intermediário (e não a
 * economia direto), este plugin precisa ligar ANTES deles — por isso o {@code loadbefore} no
 * plugin.yml. Plugins que não estão nessa lista podem pegar a economia direto e não aparecer
 * no painel (mas o total em circulação continua certo).
 */
public class PainelEconomiaPlugin extends JavaPlugin {

    public record Fortuna(String nome, double saldo) {
    }

    public record Circulacao(double total, int contasComSaldo, List<Fortuna> ranking, long calculadoEm) {
    }

    private Economy real;
    private Economy interceptada;
    private FluxoManager fluxoManager;
    private Circulacao cacheCirculacao;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.fluxoManager = new FluxoManager(this);

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Nenhuma economia registrada no Vault — o painel fica sem dados até ter uma (Vault + EssentialsX, por exemplo).");
        } else {
            this.real = rsp.getProvider();
            interceptar(rsp);
        }

        EconomiaCommand comando = new EconomiaCommand(this);
        getCommand("economia").setExecutor(comando);
        getCommand("economia").setTabCompleter(comando);

        // Quem entra já conta como ativo no dia, mesmo que saia antes da próxima contagem
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void aoEntrar(PlayerJoinEvent evento) {
                fluxoManager.registrarAtividade(List.of(evento.getPlayer().getUniqueId()), 0);
            }
        }, this);

        // Grava o fluxo a cada 5 min, soma 5 min de jogo pra quem está online, tira a "foto"
        // diária da circulação quando o dia virar e confere se o dia já passou do limite do alerta
        long cincoMin = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this, () -> {
            List<UUID> online = getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            if (!online.isEmpty()) {
                fluxoManager.registrarAtividade(online, 5);
            }
            if (real != null && fluxoManager.getCirculacao(LocalDate.now()) == null) {
                calcularCirculacao(true);
            }
            verificarAlerta();
            fluxoManager.salvarSeMudou();
        }, 20L * 30, cincoMin);

        getLogger().info("PainelEconomiaPlugin ativado!");
    }

    private void interceptar(RegisteredServiceProvider<Economy> atual) {
        if (atual.getPriority() == ServicePriority.Highest) {
            getLogger().warning("A economia '" + real.getName() + "' já está registrada com prioridade máxima no Vault; "
                    + "o painel não consegue se colocar na frente dela. Só o total em circulação vai funcionar.");
            return;
        }
        this.interceptada = EconomiaInterceptada.criar(real, fluxoManager, this);
        getServer().getServicesManager().register(Economy.class, interceptada, this, ServicePriority.Highest);

        RegisteredServiceProvider<Economy> agora = getServer().getServicesManager().getRegistration(Economy.class);
        if (agora == null || agora.getProvider() != interceptada) {
            getLogger().warning("Não foi possível se colocar na frente da economia do Vault — entradas/saídas por plugin não serão registradas.");
        } else {
            getLogger().info("Acompanhando a economia '" + real.getName() + "'.");
        }
    }

    @Override
    public void onDisable() {
        if (interceptada != null) {
            getServer().getServicesManager().unregister(Economy.class, interceptada);
        }
        if (fluxoManager != null) {
            fluxoManager.salvar();
        }
        getLogger().info("PainelEconomiaPlugin desativado!");
    }

    public FluxoManager getFluxoManager() {
        return fluxoManager;
    }

    public boolean temEconomia() {
        return real != null;
    }

    public boolean estaInterceptando() {
        return interceptada != null;
    }

    /**
     * Soma o saldo de todo jogador que já entrou no servidor. Usa cache de alguns minutos:
     * com muitos jogadores, consultar o saldo de cada conta offline pode ser lento (o EssentialsX
     * lê o arquivo de cada um). {@code forcar} ignora o cache. Também grava a foto do dia: o
     * total e a distribuição (mediana, fatia das 10 maiores e Gini, só das contas com saldo).
     */
    public Circulacao calcularCirculacao(boolean forcar) {
        long cacheMs = Math.max(0, getConfig().getInt("circulacao.cache-minutos", 5)) * 60_000L;
        if (!forcar && cacheCirculacao != null && System.currentTimeMillis() - cacheCirculacao.calculadoEm() < cacheMs) {
            return cacheCirculacao;
        }

        double total = 0;
        int contas = 0;
        List<Fortuna> fortunas = new ArrayList<>();
        for (OfflinePlayer jogador : Bukkit.getOfflinePlayers()) {
            if (!real.hasAccount(jogador)) continue;
            double saldo = real.getBalance(jogador);
            if (saldo <= 0) continue;
            total += saldo;
            contas++;
            fortunas.add(new Fortuna(jogador.getName() != null ? jogador.getName() : jogador.getUniqueId().toString(), saldo));
        }
        fortunas.sort(Comparator.comparingDouble(Fortuna::saldo).reversed());
        fluxoManager.registrarDistribuicao(LocalDate.now(), distribuicao(fortunas, total));
        int topGuardado = Math.max(1, getConfig().getInt("circulacao.top-maximo", 20));
        List<Fortuna> ranking = new ArrayList<>(fortunas.subList(0, Math.min(topGuardado, fortunas.size())));

        cacheCirculacao = new Circulacao(total, contas, ranking, System.currentTimeMillis());
        fluxoManager.registrarCirculacao(LocalDate.now(), total);
        return cacheCirculacao;
    }

    /** {@code fortunas} em ordem decrescente de saldo. */
    private static FluxoManager.Distribuicao distribuicao(List<Fortuna> fortunas, double total) {
        int n = fortunas.size();
        if (n == 0 || total <= 0) return new FluxoManager.Distribuicao(0, 0, 0, 0);
        double mediana = n % 2 == 1 ? fortunas.get(n / 2).saldo()
                : (fortunas.get(n / 2 - 1).saldo() + fortunas.get(n / 2).saldo()) / 2;
        double top10 = 0;
        for (int i = 0; i < Math.min(10, n); i++) {
            top10 += fortunas.get(i).saldo();
        }
        // Gini com os saldos em ordem crescente (i de 1 a n): 2 * soma(i * x_i) / (n * total) - (n + 1) / n
        double ponderada = 0;
        for (int i = 0; i < n; i++) {
            ponderada += (i + 1) * fortunas.get(n - 1 - i).saldo();
        }
        double gini = 2 * ponderada / (n * total) - (n + 1.0) / n;
        return new FluxoManager.Distribuicao(n, mediana, top10 / total, gini);
    }

    /**
     * Avisa a staff (uma vez por dia) quando o líquido de hoje passa de {@code alerta.percentual}% da
     * circulação de ontem e também de {@code alerta.minimo}. Usa o fluxo registrado, e não um novo
     * cálculo da circulação, que pode ser lento. Sem a foto de ontem (primeiro dia), não alerta.
     */
    private void verificarAlerta() {
        if (!getConfig().getBoolean("alerta.ativado", true) || interceptada == null) return;
        LocalDate hoje = LocalDate.now();
        Double ontem = fluxoManager.getCirculacao(hoje.minusDays(1));
        if (ontem == null || ontem <= 0) return;

        double[] total = totalDoDia(hoje);
        double liquido = total[FluxoManager.ENTROU] - total[FluxoManager.SAIU];
        double percentual = liquido / ontem * 100;
        if (liquido < getConfig().getDouble("alerta.minimo", 50000)
                || percentual < getConfig().getDouble("alerta.percentual", 10)) {
            return;
        }
        if (!fluxoManager.marcarAlerta(hoje)) return;

        List<Map.Entry<String, double[]>> origens = new ArrayList<>(fluxoManager.getDia(hoje).entrySet());
        origens.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) ->
                e.getValue()[FluxoManager.ENTROU] - e.getValue()[FluxoManager.SAIU]).reversed());
        StringBuilder maiores = new StringBuilder();
        for (int i = 0; i < Math.min(3, origens.size()); i++) {
            double[] v = origens.get(i).getValue();
            if (v[FluxoManager.ENTROU] - v[FluxoManager.SAIU] <= 0) break;
            if (!maiores.isEmpty()) maiores.append("§7, ");
            maiores.append("§f").append(origens.get(i).getKey()).append(" §a+")
                    .append(valor(v[FluxoManager.ENTROU] - v[FluxoManager.SAIU]));
        }

        String linha1 = String.format(Locale.ROOT, "§c§l[Economia] §cA circulação cresceu §f%s §c(%.1f%%) hoje.",
                valor(liquido), percentual);
        String linha2 = "§7Maiores entradas: " + maiores + " §8— /economia tipos <plugin>";
        getLogger().warning(ChatColor.stripColor(linha1 + " " + linha2));
        for (Player staff : getServer().getOnlinePlayers()) {
            if (staff.hasPermission("painel.economia")) {
                staff.sendMessage(linha1);
                staff.sendMessage(linha2);
            }
        }
        enviarDiscord(ChatColor.stripColor(linha1) + "\n" + ChatColor.stripColor(linha2));
    }

    /** Alerta pra categoria "economia" do DiscordPlugin (canal da staff), por reflexão; sem ele, nada. */
    private void enviarDiscord(String mensagem) {
        Plugin discord = getServer().getPluginManager().getPlugin("DiscordPlugin");
        if (discord == null || !discord.isEnabled()) return;
        try {
            discord.getClass().getMethod("enviar", String.class, String.class).invoke(discord, "economia", mensagem);
        } catch (ReflectiveOperationException e) {
            getLogger().warning("DiscordPlugin encontrado, mas não foi possível mandar o alerta: " + e.getMessage());
        }
    }

    private static String valor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    /** Soma de todas as origens de um dia: [entrou, saiu]. */
    public double[] totalDoDia(LocalDate dia) {
        double[] soma = new double[2];
        for (Map.Entry<String, double[]> entrada : fluxoManager.getDia(dia).entrySet()) {
            soma[FluxoManager.ENTROU] += entrada.getValue()[FluxoManager.ENTROU];
            soma[FluxoManager.SAIU] += entrada.getValue()[FluxoManager.SAIU];
        }
        return soma;
    }
}
