package com.example.oficinas;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MissoesPlugin (missões de reparar e de encantar), RankupPlugin (informações das minas pro guia
 * de mina) e ChequePlugin (o balcão do Bancário). Por reflexão, sem dependência de compilação: sem o
 * plugin, não faz nada.
 */
public class Integracoes {

    private final JavaPlugin plugin;
    private final Set<String> avisouFalha = new HashSet<>();

    public Integracoes(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Progresso de missão ("REPARAR", "ENCANTAR"), com o material do item como alvo. */
    public void missao(Player jogador, String tipo, String alvo, int quantidade) {
        Plugin missoes = habilitado("MissoesPlugin");
        if (missoes == null || quantidade <= 0) return;
        try {
            missoes.getClass().getMethod("registrarProgresso", Player.class, String.class, String.class, int.class)
                    .invoke(missoes, jogador, tipo, alvo, quantidade);
        } catch (ReflectiveOperationException e) {
            avisar("MissoesPlugin#registrarProgresso", e);
        }
    }

    /** As linhas de informação da mina pro jogador, ou null (sem o Rankup ou mina que não existe). */
    @SuppressWarnings("unchecked")
    public List<String> infoMina(String mina, UUID jogador) {
        Plugin rankup = habilitado("RankupPlugin");
        if (rankup == null) return null;
        try {
            return (List<String>) rankup.getClass().getMethod("infoMina", String.class, UUID.class).invoke(rankup, mina, jogador);
        } catch (ReflectiveOperationException | ClassCastException e) {
            avisar("RankupPlugin#infoMina", e);
            return null;
        }
    }

    public boolean podeEntrarNaMina(String mina, Player jogador) {
        Plugin rankup = habilitado("RankupPlugin");
        if (rankup == null) return false;
        try {
            return Boolean.TRUE.equals(rankup.getClass().getMethod("podeEntrarNaMina", String.class, Player.class).invoke(rankup, mina, jogador));
        } catch (ReflectiveOperationException e) {
            avisar("RankupPlugin#podeEntrarNaMina", e);
            return false;
        }
    }

    /** Abre o balcão do banco (ChequePlugin#abrirBanco). false sem o ChequePlugin. */
    public boolean abrirBanco(Player jogador) {
        Plugin cheque = habilitado("ChequePlugin");
        if (cheque == null) return false;
        try {
            cheque.getClass().getMethod("abrirBanco", Player.class).invoke(cheque, jogador);
            return true;
        } catch (ReflectiveOperationException e) {
            avisar("ChequePlugin#abrirBanco", e);
            return false;
        }
    }

    private static Plugin habilitado(String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nome);
        return p == null || !p.isEnabled() ? null : p;
    }

    private void avisar(String chave, Exception e) {
        if (avisouFalha.add(chave)) {
            plugin.getLogger().warning("Não deu pra usar " + chave + " (versão antiga do plugin?): " + e);
        }
    }
}
