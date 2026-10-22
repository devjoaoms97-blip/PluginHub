package com.example.painel;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

        // Grava o fluxo a cada 5 min e tira a "foto" diária da circulação quando o dia virar
        long cincoMin = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this, () -> {
            fluxoManager.salvarSeMudou();
            if (real != null && fluxoManager.getCirculacao(LocalDate.now()) == null) {
                calcularCirculacao(true);
            }
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
     * lê o arquivo de cada um). {@code forcar} ignora o cache. Também grava a foto do dia.
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
        int topGuardado = Math.max(1, getConfig().getInt("circulacao.top-maximo", 20));
        List<Fortuna> ranking = new ArrayList<>(fortunas.subList(0, Math.min(topGuardado, fortunas.size())));

        cacheCirculacao = new Circulacao(total, contas, ranking, System.currentTimeMillis());
        fluxoManager.registrarCirculacao(LocalDate.now(), total);
        return cacheCirculacao;
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
