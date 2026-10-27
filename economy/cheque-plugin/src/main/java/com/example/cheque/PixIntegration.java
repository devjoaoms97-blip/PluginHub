package com.example.cheque;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O extrato (histórico de transações) fica no PixPlugin: aqui o banco registra o que mexe no dinheiro
 * (folha, cheque emitido, cancelado e depositado, Mine-sena) e lê o extrato que o Bancário entrega.
 * Por reflexão, sem dependência de compilação: sem o PixPlugin, não registra nada e o extrato fica vazio.
 */
public class PixIntegration {

    private final JavaPlugin plugin;
    private final Set<String> avisouFalha = new HashSet<>();

    public PixIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Movimento entre o jogador e algo que não é jogador ("Bancário", "Mine-sena", "Cheque de Fulano"). */
    public void registrar(UUID jogador, String entidade, double valor, boolean jogadorRecebeu, String tipo) {
        Plugin pix = pix();
        if (pix == null) return;
        try {
            Object charges = pix.getClass().getMethod("getChargeManager").invoke(pix);
            charges.getClass().getMethod("registrarTransacaoComEntidade", UUID.class, String.class, double.class, boolean.class, String.class)
                    .invoke(charges, jogador, entidade, valor, jogadorRecebeu, tipo);
        } catch (ReflectiveOperationException e) {
            avisar("registrarTransacaoComEntidade", e);
        }
    }

    /** As últimas transações (PixPlugin#extrato): {data, "+"/"-", valor, com quem, tipo, taxa}. Vazio sem o Pix. */
    @SuppressWarnings("unchecked")
    public List<String[]> extrato(UUID jogador, int quantidade) {
        Plugin pix = pix();
        if (pix == null) return List.of();
        try {
            return (List<String[]>) pix.getClass().getMethod("extrato", UUID.class, int.class).invoke(pix, jogador, quantidade);
        } catch (ReflectiveOperationException | ClassCastException e) {
            avisar("extrato", e);
            return List.of();
        }
    }

    public boolean disponivel() {
        return pix() != null;
    }

    private static Plugin pix() {
        Plugin p = Bukkit.getPluginManager().getPlugin("PixPlugin");
        return p == null || !p.isEnabled() ? null : p;
    }

    private void avisar(String metodo, Exception e) {
        if (avisouFalha.add(metodo)) {
            plugin.getLogger().warning("PixPlugin encontrado, mas não foi possível usar " + metodo + " (versão antiga?): " + e.getMessage());
        }
    }
}
