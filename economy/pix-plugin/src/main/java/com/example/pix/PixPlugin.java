package com.example.pix;

import java.util.Locale;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class PixPlugin extends JavaPlugin {

    private Economy economy = null;
    private ChargeManager chargeManager;
    private LimiteManager limiteManager;
    private AgendamentoManager agendamentoManager;
    private ChaveManager chaveManager;

    private final DiscordIntegration discord = new DiscordIntegration(this);

    /**
     * Pix "suspeito" (valor de {@code discord.pix-suspeito-valor} pra cima) vai pro canal da staff
     * no Discord, pra conferir se não é conta roubada sendo esvaziada ou lavagem entre contas.
     */
    public void conferirSuspeito(String de, String para, double valor, String tipo) {
        double limiar = getConfig().getDouble("discord.pix-suspeito-valor"); // padrão do jar em configs antigos
        if (limiar <= 0 || valor < limiar) return;
        discord.enviar("pix", "⚠️ Pix alto: " + de + " → " + para + ": "
                + String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor) + " (" + tipo + ")");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.chargeManager = new ChargeManager(this);
        this.limiteManager = new LimiteManager(this);
        this.agendamentoManager = new AgendamentoManager(this);
        this.chaveManager = new ChaveManager(this);
        // Pix agendado: confere os vencimentos a cada 20 s
        getServer().getScheduler().runTaskTimer(this, agendamentoManager::tick, 400L, 400L);

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O PixPlugin vai continuar ativo, mas ninguém conseguirá usar /pix até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        PixCommand executor = new PixCommand(this);
        getCommand("pix").setExecutor(executor);
        getCommand("pix").setTabCompleter(executor);

        getLogger().info("PixPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (chargeManager != null) {
            chargeManager.salvar();
        }
        if (limiteManager != null) {
            limiteManager.salvar();
        }
        if (agendamentoManager != null) {
            agendamentoManager.salvar();
        }
        getLogger().info("PixPlugin desativado!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    public AgendamentoManager getAgendamentoManager() {
        return agendamentoManager;
    }

    public ChaveManager getChaveManager() {
        return chaveManager;
    }

    /**
     * Título e som pra quem recebeu um Pix (se estiver online), além da mensagem no chat.
     * Desliga em {@code notificacao.titulo} e {@code notificacao.som}.
     */
    public void notificarRecebimento(Player recebedor, double valor, String deNome) {
        if (recebedor == null || !recebedor.isOnline()) return;
        if (getConfig().getBoolean("notificacao.titulo", true)) {
            var legado = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            recebedor.showTitle(net.kyori.adventure.title.Title.title(
                    legado.deserialize("§a+ " + String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor)),
                    legado.deserialize("§7Pix de §f" + deNome),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(250),
                            java.time.Duration.ofMillis(2000), java.time.Duration.ofMillis(500))));
        }
        if (getConfig().getBoolean("notificacao.som", true)) {
            recebedor.playSound(recebedor.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            recebedor.playSound(recebedor.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f);
        }
    }

    public LimiteManager getLimiteManager() {
        return limiteManager;
    }

    /**
     * Calcula a taxa (em valor monetário, não percentual) cobrada sobre uma transação de
     * "valor" entre jogadores, de acordo com {@code taxa.percentual-transacao} e
     * {@code taxa.minimo} no config.yml. A taxa nunca ultrapassa o próprio valor da
     * transação (evita zerar ou inverter o pagamento em transações muito pequenas).
     */
    public double calcularTaxaTransacao(double valor) {
        double percentual = getConfig().getDouble("taxa.percentual-transacao", 1.0);
        double minimo = getConfig().getDouble("taxa.minimo", 0.0);

        double taxa = valor * (percentual / 100.0);
        taxa = Math.max(taxa, minimo);
        return Math.min(taxa, valor);
    }

    /** A taxa de cima com o desconto de VIP de quem paga ({@code taxa.vip-descontos}). */
    public double calcularTaxaTransacao(double valor, Player pagador) {
        return calcularTaxaTransacao(valor) * (1 - descontoTaxaVip(pagador) / 100.0);
    }

    /**
     * Maior desconto (%) na taxa que o jogador tem por permissão. LISTA de {permissao, percentual},
     * igual ao desconto de VIP da loja: chave com ponto viraria seção aninhada num mapa.
     */
    private double descontoTaxaVip(Player pagador) {
        if (!getConfig().getBoolean("taxa.vip-descontos.ativado", true)) return 0;
        List<?> permissoes = getConfig().getList("taxa.vip-descontos.permissoes");
        if (permissoes == null) return 0;
        double maior = 0;
        for (Object item : permissoes) {
            if (item instanceof Map<?, ?> mapa && mapa.get("permissao") instanceof String permissao
                    && mapa.get("percentual") instanceof Number percentual && pagador.hasPermission(permissao)) {
                maior = Math.max(maior, percentual.doubleValue());
            }
        }
        return Math.min(100, maior);
    }
}
