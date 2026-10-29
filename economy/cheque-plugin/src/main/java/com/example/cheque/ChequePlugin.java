package com.example.cheque;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChequePlugin extends JavaPlugin {

    private Economy economy = null;

    // Chaves usadas para gravar dados no item (PersistentDataContainer)
    private NamespacedKey chequeValueKey;
    private NamespacedKey chequeIdKey;
    private NamespacedKey chequeNominalKey;
    private NamespacedKey chequeEmissorKey;
    private NamespacedKey folhaKey;
    private NamespacedKey chequeOrigemKey;
    private Banco banco;
    private MineSena mineSena;
    private PixIntegration pix;
    private ChequeLog chequeLog;

    @Override
    public void onEnable() {
        this.chequeValueKey = new NamespacedKey(this, "cheque_valor");
        this.chequeIdKey = new NamespacedKey(this, "cheque_id");
        this.chequeNominalKey = new NamespacedKey(this, "cheque_nominal");
        this.chequeEmissorKey = new NamespacedKey(this, "cheque_emissor");
        this.folhaKey = new NamespacedKey(this, "folha");
        this.chequeOrigemKey = new NamespacedKey(this, "cheque_origem");
        saveDefaultConfig();
        this.chequeLog = new ChequeLog(this);

        if (!setupEconomy()) {
            getLogger().warning("Vault (ou um plugin de economia) não foi encontrado!");
            getLogger().warning("O plugin vai continuar ativo, mas ninguém conseguirá resgatar cheques até o Vault ser instalado.");
        } else {
            getLogger().info("Economia via Vault conectada com sucesso: " + economy.getName());
        }

        getCommand("cheque").setExecutor(new ChequeCommand(this));
        this.pix = new PixIntegration(this);
        this.banco = new Banco(this);
        this.mineSena = new MineSena(this);
        getServer().getPluginManager().registerEvents(new ChequeListener(this), this);
        getServer().getPluginManager().registerEvents(banco, this);
        getServer().getPluginManager().registerEvents(mineSena, this);
        getCommand("minesena").setExecutor(mineSena);
        // sorteio da Mine-sena: confere a cada minuto (e logo ao ligar, se o horário já passou)
        getServer().getScheduler().runTaskTimer(this, mineSena::conferir, 20L * 10, 20L * 60);

        getLogger().info("ChequePlugin ativado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChequePlugin desativado!");
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

    public NamespacedKey getChequeValueKey() {
        return chequeValueKey;
    }

    public NamespacedKey getChequeNominalKey() {
        return chequeNominalKey;
    }

    public NamespacedKey getChequeEmissorKey() {
        return chequeEmissorKey;
    }

    public NamespacedKey getFolhaKey() {
        return folhaKey;
    }

    public Banco getBanco() {
        return banco;
    }

    public MineSena getMineSena() {
        return mineSena;
    }

    public PixIntegration getPix() {
        return pix;
    }

    /** API pública (OficinasPlugin, NPC Bancário, por reflexão): abre o balcão do banco. */
    public void abrirBanco(org.bukkit.entity.Player jogador) {
        banco.abrir(jogador);
    }

    public ChequeLog getChequeLog() {
        return chequeLog;
    }

    /**
     * Cheque emitido pelo servidor (o prêmio de um tesouro, por exemplo), sem tirar de ninguém: o
     * dinheiro só entra na economia quando alguém deposita no Bancário. Vale pra quem estiver com ele
     * (não é nominal) e, cancelado pela staff, não devolve nada (ninguém pagou). Os outros plugins
     * chamam por reflexão: {@code chequeDoServidor(double, String)}.
     *
     * @param origem quem aparece como emissor e no extrato ("Tesouro")
     */
    public ItemStack chequeDoServidor(double valor, String origem) {
        ItemStack cheque = montarCheque(origem, null, valor, null);
        ItemMeta meta = cheque.getItemMeta();
        meta.getPersistentDataContainer().set(chequeOrigemKey, PersistentDataType.STRING, origem);
        cheque.setItemMeta(meta);
        chequeLog.emitidoPeloServidor(origem, valor, meta.getPersistentDataContainer().get(chequeIdKey, PersistentDataType.STRING));
        return cheque;
    }

    /** De onde veio o cheque emitido pelo servidor, ou null (cheque de jogador). */
    public String origemDoCheque(ItemStack cheque) {
        return cheque == null || !cheque.hasItemMeta() ? null
                : cheque.getItemMeta().getPersistentDataContainer().get(chequeOrigemKey, PersistentDataType.STRING);
    }

    /**
     * O item do cheque. O valor, o ID, o emissor e o nominal ficam gravados no item (não no lore, que
     * pode ser editado), o que evita falsificação e cópia.
     *
     * @param emissor quem emitiu (null no cheque do servidor: ninguém cancela pelo item, nem recebe de volta)
     */
    public ItemStack montarCheque(String nomeEmissor, UUID emissor, double valor, OfflinePlayer nominal) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Cheque" + ChatColor.DARK_GRAY + " - " + ChatColor.GOLD + formatarValor(valor));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Emitido por: " + ChatColor.WHITE + nomeEmissor);
        lore.add(ChatColor.GRAY + "Data: " + ChatColor.WHITE + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        if (nominal != null) {
            lore.add(ChatColor.GRAY + "Nominal a: " + ChatColor.WHITE + nominal.getName() + ChatColor.GRAY + " (só ele resgata)");
        }
        String id = UUID.randomUUID().toString();
        // os 8 primeiros caracteres do ID: com eles a staff cancela um cheque que já está com outro jogador
        lore.add(ChatColor.DARK_GRAY + "ID: " + id.substring(0, 8));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Deposite no Bancário");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(chequeValueKey, PersistentDataType.DOUBLE, valor);
        meta.getPersistentDataContainer().set(chequeIdKey, PersistentDataType.STRING, id);
        if (emissor != null) meta.getPersistentDataContainer().set(chequeEmissorKey, PersistentDataType.STRING, emissor.toString());
        if (nominal != null) meta.getPersistentDataContainer().set(chequeNominalKey, PersistentDataType.STRING, nominal.getUniqueId().toString());
        cheque.setItemMeta(meta);
        return cheque;
    }

    public static String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }

    public NamespacedKey getChequeIdKey() {
        return chequeIdKey;
    }
}
