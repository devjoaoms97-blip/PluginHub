package com.example.shop;

import com.example.shop.command.BuyCommand;
import com.example.shop.command.SellCommand;
import com.example.shop.command.ShopCommand;
import com.example.shop.gui.ShopGUI;
import com.example.shop.gui.ShopGUIListener;
import com.example.shop.manager.EventoMercado;
import com.example.shop.manager.AutoVenda;
import com.example.shop.manager.PriceHistoryManager;
import com.example.shop.manager.PriceRegenTask;
import com.example.shop.manager.ShopManager;
import com.example.shop.manager.VendasDiariasManager;
import com.example.shop.util.MissoesIntegration;
import com.example.shop.util.RankupIntegration;
import com.example.shop.util.PixIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class ShopPlugin extends JavaPlugin {

    private Economy economy;
    private ShopManager shopManager;
    private EventoMercado eventoMercado;
    private ShopGUI shopGUI;
    private PriceHistoryManager priceHistoryManager;
    private VendasDiariasManager vendasDiariasManager;
    private AutoVenda autoVenda;
    private com.example.shop.manager.Precificacao precificacao;
    private com.example.shop.manager.VendedoresManager vendedores;
    private PixIntegration pixIntegration;
    private MissoesIntegration missoesIntegration;
    private RankupIntegration rankupIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("A loja vai continuar ativa, mas ninguém conseguirá comprar/vender até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        this.shopManager = new ShopManager(this);
        this.eventoMercado = new EventoMercado(this);
        this.shopGUI = new ShopGUI(this);
        this.priceHistoryManager = new PriceHistoryManager(this);
        this.vendasDiariasManager = new VendasDiariasManager(this);
        this.autoVenda = new AutoVenda(this);
        this.precificacao = new com.example.shop.manager.Precificacao(this);
        this.vendedores = new com.example.shop.manager.VendedoresManager(this);
        getServer().getPluginManager().registerEvents(vendedores, this);
        vendedores.carregarTodos();
        getServer().getScheduler().runTaskTimer(this, vendedores::conferir, 100L, 100L);
        this.pixIntegration = new PixIntegration(this);
        this.missoesIntegration = new MissoesIntegration(this);
        this.rankupIntegration = new RankupIntegration(this);

        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        BuyCommand buyCommand = new BuyCommand(this);
        getCommand("buy").setExecutor(buyCommand);
        getCommand("buy").setTabCompleter(buyCommand);

        SellCommand sellCommand = new SellCommand(this);
        getCommand("sell").setExecutor(sellCommand);
        getCommand("sell").setTabCompleter(sellCommand);

        getServer().getPluginManager().registerEvents(new ShopGUIListener(this), this);

        int intervaloRegenSegundos = getConfig().getInt("regeneracao.intervalo-segundos", 60);
        long intervaloRegenTicks = intervaloRegenSegundos * 20L;
        new PriceRegenTask(this).runTaskTimer(this, intervaloRegenTicks, intervaloRegenTicks);

        int intervaloHistoricoMinutos = getConfig().getInt("historico.intervalo-minutos", 15);
        long intervaloHistoricoTicks = intervaloHistoricoMinutos * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, priceHistoryManager::registrarSnapshotDeTodos,
                intervaloHistoricoTicks, intervaloHistoricoTicks);

        // eventos de mercado: confere a cada minuto se o atual acabou ou se é hora de sortear outro
        getServer().getScheduler().runTaskTimer(this, eventoMercado::tick, 20L * 60, 20L * 60);

        long cincoMin = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this, vendasDiariasManager::salvarSeMudou, cincoMin, cincoMin);

        getLogger().info("EconomyShopPlugin ativado!");
    }

    @Override
    public void onDisable() {
        // NÃO salvar itens.yml aqui: o arquivo é editável à mão (o dono do servidor
        // substitui direto com a lista de itens). Se salvássemos no desligamento,
        // gravaríamos o estado antigo em memória por cima do arquivo novo, revertendo
        // edições externas. A persistência continua garantida pelo PriceRegenTask
        // (salva a cada 60s quando o preço mexe) e pelos saves de venda/compra.
        // vendas-diarias.yml é só estatística (não é editado à mão), então esse pode.
        if (vendasDiariasManager != null) {
            vendasDiariasManager.salvar();
        }
        if (vendedores != null) {
            vendedores.removerTodos();
        }
        getLogger().info("EconomyShopPlugin desativado!");
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

    /**
     * Desconto de VIP na compra, calculado só por permissão — sem nenhuma dependência do
     * VipPlugin em tempo de compilação. Se o VipPlugin estiver instalado, ele concede o
     * grupo do LuckPerms correspondente ao tier do jogador, e é o próprio LuckPerms quem
     * decide se esse grupo tem a permissão `vip.desconto.<tier>` configurada (veja
     * `vip-descontos.permissoes` no config.yml). Sem VipPlugin/LuckPerms instalado, ou sem
     * nenhuma dessas permissões concedidas, o desconto simplesmente fica em 0%.
     * <p>
     * `vip-descontos.permissoes` é uma LISTA de mapas ({@code permissao}/{@code percentual}),
     * não um mapa direto de "permissao: percentual" — chaves de mapa com ponto (tipo
     * "vip.desconto.diamante") viram seção aninhada quando o Bukkit carrega o YAML, então um
     * mapa quebraria essa leitura silenciosamente.
     */
    public int calcularDescontoVip(Player jogador) {
        if (!getConfig().getBoolean("vip-descontos.ativado", true)) {
            return 0;
        }

        List<?> permissoes = getConfig().getList("vip-descontos.permissoes");
        if (permissoes == null) {
            return 0;
        }

        int maiorDesconto = 0;
        for (Object item : permissoes) {
            if (!(item instanceof Map<?, ?> mapa)) {
                continue;
            }
            Object permissao = mapa.get("permissao");
            Object percentual = mapa.get("percentual");
            if (permissao == null || percentual == null) {
                continue;
            }
            if (jogador.hasPermission(permissao.toString())) {
                maiorDesconto = Math.max(maiorDesconto, ((Number) percentual).intValue());
            }
        }
        return maiorDesconto;
    }

    public com.example.shop.manager.Precificacao getPrecificacao() {
        return precificacao;
    }

    public com.example.shop.manager.VendedoresManager getVendedores() {
        return vendedores;
    }

    public Economy getEconomy() {
        return economy;
    }

    public EventoMercado getEventoMercado() {
        return eventoMercado;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }

    public PriceHistoryManager getPriceHistoryManager() {
        return priceHistoryManager;
    }

    /**
     * API pro RankupPlugin (por reflexão): vende na hora o que o jogador minerou, se a loja compra
     * esse item. Retorna false se não comprou, e aí o item cai no chão normalmente.
     */
    public boolean venderAutomaticamente(Player jogador, ItemStack stack) {
        return autoVenda.vender(jogador, stack);
    }

    public VendasDiariasManager getVendasDiariasManager() {
        return vendasDiariasManager;
    }

    public MissoesIntegration getMissoesIntegration() {
        return missoesIntegration;
    }

    public RankupIntegration getRankupIntegration() {
        return rankupIntegration;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }
}
