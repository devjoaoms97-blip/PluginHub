package com.example.tesouros;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * RankupPlugin (se um bloco é de mina e o Fragmento Arcano), ClasPlugin (mesmo clã), ChequePlugin (o
 * cheque com o dinheiro do tesouro), PixPlugin (o extrato), DiscordPlugin (anúncios) e Vault (coins, sem o
 * ChequePlugin). Os plugins nossos vão por reflexão, sem
 * dependência de compilação: sem o plugin, não faz nada.
 */
public class Integracoes {

    private final JavaPlugin plugin;
    private final Set<String> avisouFalha = new HashSet<>();

    public Integracoes(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** O bloco é de uma mina do rankup? false sem o RankupPlugin. */
    public boolean emMina(Location local) {
        Plugin rankup = habilitado("RankupPlugin");
        if (rankup == null) return false;
        try {
            Object minas = rankup.getClass().getMethod("getMinaManager").invoke(rankup);
            return minas.getClass().getMethod("getMinaEm", Location.class).invoke(minas, local) != null;
        } catch (ReflectiveOperationException e) {
            avisar("RankupPlugin#getMinaManager().getMinaEm", e);
            return false;
        }
    }

    /** Fragmentos Arcanos do RankupPlugin, ou null sem ele. */
    public ItemStack fragmento(int quantidade) {
        Plugin rankup = habilitado("RankupPlugin");
        if (rankup == null) return null;
        try {
            return (ItemStack) rankup.getClass().getMethod("fragmento", int.class).invoke(rankup, quantidade);
        } catch (ReflectiveOperationException | ClassCastException e) {
            avisar("RankupPlugin#fragmento", e);
            return null;
        }
    }

    /** Spawner do SpawnersPlugin daquele tipo (galinha, zumbi, golem...), ou null. */
    public ItemStack spawner(String tipo, int quantidade) {
        return itemDeTipo("SpawnersPlugin", "item", tipo, quantidade);
    }

    /** Máquina do MaquinasPlugin daquele tipo (tear, forja, reator...), nova, ou null. */
    public ItemStack maquina(String tipo) {
        return itemDeTipo("MaquinasPlugin", "maquina", tipo, 0);
    }

    /** Células de Combustível do MaquinasPlugin, ou null. */
    public ItemStack combustivel(int quantidade) {
        Plugin maquinas = habilitado("MaquinasPlugin");
        if (maquinas == null) return null;
        try {
            return (ItemStack) maquinas.getClass().getMethod("combustivel", int.class).invoke(maquinas, quantidade);
        } catch (ReflectiveOperationException | ClassCastException e) {
            avisar("MaquinasPlugin#combustivel", e);
            return null;
        }
    }

    /** {@code getTipos().get(tipo)} do plugin, passado pro método que cria o item. */
    private ItemStack itemDeTipo(String nomePlugin, String metodo, String tipo, int quantidade) {
        Plugin p = habilitado(nomePlugin);
        if (p == null) return null;
        try {
            Object tipos = p.getClass().getMethod("getTipos").invoke(p);
            Object t = tipos instanceof java.util.Map<?, ?> mapa ? mapa.get(tipo) : null;
            if (t == null) {
                plugin.getLogger().warning("loot.yml: " + nomePlugin + " não tem o tipo \"" + tipo + "\"");
                return null;
            }
            return metodo.equals("maquina")
                    ? (ItemStack) p.getClass().getMethod(metodo, t.getClass(), int.class, int.class).invoke(p, t, 0, 0)
                    : (ItemStack) p.getClass().getMethod(metodo, t.getClass(), int.class).invoke(p, t, quantidade);
        } catch (ReflectiveOperationException | ClassCastException e) {
            avisar(nomePlugin + "#" + metodo, e);
            return null;
        }
    }

    /**
     * Um cheque com o valor, emitido pelo servidor (ChequePlugin#chequeDoServidor): o dinheiro só entra
     * na economia quando alguém deposita no Bancário. null sem o ChequePlugin.
     */
    public ItemStack cheque(double valor, String origem) {
        Plugin cheque = habilitado("ChequePlugin");
        if (cheque == null) return null;
        try {
            return (ItemStack) cheque.getClass().getMethod("chequeDoServidor", double.class, String.class).invoke(cheque, valor, origem);
        } catch (ReflectiveOperationException | ClassCastException e) {
            avisar("ChequePlugin#chequeDoServidor", e);
            return null;
        }
    }

    public boolean mesmoCla(UUID a, UUID b) {
        Plugin clas = habilitado("ClasPlugin");
        if (clas == null) return false;
        try {
            return Boolean.TRUE.equals(clas.getClass().getMethod("mesmoCla", UUID.class, UUID.class).invoke(clas, a, b));
        } catch (ReflectiveOperationException e) {
            avisar("ClasPlugin#mesmoCla", e);
            return false;
        }
    }

    /** Deposita coins novas (do servidor) e registra no extrato. false sem Vault. */
    public boolean depositar(OfflinePlayer jogador, double valor, String motivo) {
        if (valor <= 0 || Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        rsp.getProvider().depositPlayer(jogador, valor);
        Plugin pix = habilitado("PixPlugin");
        if (pix != null) {
            try {
                Object charges = pix.getClass().getMethod("getChargeManager").invoke(pix);
                charges.getClass().getMethod("registrarTransacaoComEntidade", UUID.class, String.class, double.class, boolean.class, String.class)
                        .invoke(charges, jogador.getUniqueId(), "Tesouro", valor, true, motivo);
            } catch (ReflectiveOperationException e) {
                avisar("PixPlugin#registrarTransacaoComEntidade", e);
            }
        }
        return true;
    }

    public void discord(String categoria, String mensagem) {
        Plugin discord = habilitado("DiscordPlugin");
        if (discord == null) return;
        try {
            discord.getClass().getMethod("enviar", String.class, String.class).invoke(discord, categoria, mensagem);
        } catch (ReflectiveOperationException e) {
            avisar("DiscordPlugin#enviar", e);
        }
    }

    private static Plugin habilitado(String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nome);
        return p == null || !p.isEnabled() ? null : p;
    }

    private void avisar(String metodo, Exception e) {
        if (avisouFalha.add(metodo)) {
            plugin.getLogger().warning(metodo + " não respondeu (versão incompatível?): " + e.getMessage());
        }
    }
}
