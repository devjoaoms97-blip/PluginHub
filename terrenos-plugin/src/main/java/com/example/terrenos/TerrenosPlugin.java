package com.example.terrenos;

import com.example.terrenos.command.TerrenoCommand;
import com.example.terrenos.listener.JogadorListener;
import com.example.terrenos.listener.ProtecaoListener;
import com.example.terrenos.manager.Limpador;
import com.example.terrenos.manager.Placas;
import com.example.terrenos.manager.TerrenoManager;
import com.example.terrenos.mundo.GeradorTerrenos;
import com.example.terrenos.mundo.Grade;
import com.example.terrenos.util.DiscordIntegration;
import com.example.terrenos.util.PixIntegration;
import com.example.terrenos.util.RankupIntegration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TerrenosPlugin extends JavaPlugin {

    public static final String PERMISSAO_ADMIN = "terrenos.admin";

    private Economy economy;
    private Grade grade;
    private TerrenoManager terrenoManager;
    private Limpador limpador;
    private Placas placas;
    private PixIntegration pixIntegration;
    private RankupIntegration rankupIntegration;
    private DiscordIntegration discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discord = new DiscordIntegration(this);
        carregarGrade();

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O TerrenosPlugin vai continuar ativo, mas ninguém conseguirá comprar, pagar imposto nem dar lance até o Vault ser instalado.");
        }

        this.limpador = new Limpador(this);
        this.placas = new Placas(this);
        this.terrenoManager = new TerrenoManager(this);
        this.pixIntegration = new PixIntegration(this);
        this.rankupIntegration = new RankupIntegration(this);

        TerrenoCommand comando = new TerrenoCommand(this);
        getCommand("terreno").setExecutor(comando);
        getCommand("terreno").setTabCompleter(comando);

        getServer().getPluginManager().registerEvents(new ProtecaoListener(this), this);
        getServer().getPluginManager().registerEvents(new JogadorListener(this), this);
        getServer().getPluginManager().registerEvents(placas, this);

        getServer().getScheduler().runTaskTimer(this, terrenoManager::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, limpador::tick, 1L, 1L);

        if (getMundo() == null) {
            getLogger().warning("O mundo \"" + getNomeMundo() + "\" não existe ainda. Crie com: mv create "
                    + getNomeMundo() + " normal --generator TerrenosPlugin");
        }
    }

    @Override
    public void onDisable() {
        if (terrenoManager != null) terrenoManager.salvar();
    }

    /** Gerador do mundo de terrenos, usado pelo Multiverse ({@code --generator TerrenosPlugin}). */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String mundo, String id) {
        if (grade == null) carregarGrade();
        return new GeradorTerrenos(() -> grade);
    }

    public void carregarGrade() {
        this.grade = new Grade(
                getConfig().getInt("grade.tamanho", 32),
                getConfig().getInt("grade.rua", 5),
                getConfig().getInt("grade.altura-chao", 64),
                material("grade.blocos.chao", Material.GRASS_BLOCK),
                material("grade.blocos.terra", Material.DIRT),
                material("grade.blocos.calcamento", Material.STONE_BRICKS),
                material("grade.blocos.borda", Material.STONE_BRICK_SLAB));
    }

    private Material material(String caminho, Material padrao) {
        String nome = getConfig().getString(caminho);
        Material m = nome == null ? null : Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) {
            if (nome != null) getLogger().warning("Bloco inválido em " + caminho + ": " + nome + ". Usando " + padrao + ".");
            return padrao;
        }
        return m;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    // ---------------------------------------------------------------------

    public Economy getEconomy() {
        return economy;
    }

    public Grade getGrade() {
        return grade;
    }

    public TerrenoManager getTerrenoManager() {
        return terrenoManager;
    }

    public Limpador getLimpador() {
        return limpador;
    }

    public Placas getPlacas() {
        return placas;
    }

    public PixIntegration getPixIntegration() {
        return pixIntegration;
    }

    public RankupIntegration getRankupIntegration() {
        return rankupIntegration;
    }

    public String getNomeMundo() {
        return getConfig().getString("mundo", "terrenos");
    }

    public World getMundo() {
        return Bukkit.getWorld(getNomeMundo());
    }

    public boolean isAdmin(Player jogador) {
        return jogador.hasPermission(PERMISSAO_ADMIN);
    }

    /**
     * Quantos terrenos o jogador pode ter: o padrão, ou o maior valor das permissões em
     * {@code limite.permissoes} (LISTA de {permissao, quantidade}: chave com ponto viraria seção).
     */
    public int limiteDe(Player jogador) {
        int limite = Math.max(0, getConfig().getInt("limite.padrao", 1));
        List<?> permissoes = getConfig().getList("limite.permissoes");
        if (permissoes == null) return limite;
        for (Object item : permissoes) {
            if (item instanceof Map<?, ?> mapa && mapa.get("permissao") instanceof String permissao
                    && mapa.get("quantidade") instanceof Number quantidade && jogador.hasPermission(permissao)) {
                limite = Math.max(limite, quantidade.intValue());
            }
        }
        return limite;
    }

    public DiscordIntegration getDiscord() {
        return discord;
    }
}
