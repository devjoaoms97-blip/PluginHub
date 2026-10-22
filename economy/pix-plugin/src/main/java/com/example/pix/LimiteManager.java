package com.example.pix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Limite diário de transferência (anti-fraude/anti-abuso entre contas).
 *
 * Soma tudo que cada jogador ENVIOU no dia (pelo valor bruto, antes da taxa) via
 * /pix pagar e /pix aceitar — /pix qrcode já passa por /pix pagar, então conta também.
 * Compras na loja não entram (não passam pelo Pix). O total zera à meia-noite (horário
 * do servidor) e fica salvo em {@code limites.yml}, pra reiniciar o servidor não zerar o
 * limite de ninguém.
 *
 * {@code limite.diario} = 0 desativa. Quem tem a permissão {@code pix.limite.ignorar}
 * não tem limite.
 */
public class LimiteManager {

    public static final String PERMISSAO_IGNORAR = "pix.limite.ignorar";

    private record Gasto(LocalDate dia, double valor) {
    }

    private final PixPlugin plugin;
    private final File arquivo;
    private final Map<UUID, Gasto> gastos = new ConcurrentHashMap<>();

    public LimiteManager(PixPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "limites.yml");
        carregar();
    }

    public double getLimiteDiario() {
        return Math.max(0, plugin.getConfig().getDouble("limite.diario", 0));
    }

    /** true se esse jogador está sujeito a limite (limite ativado e sem permissão de ignorar). */
    public boolean temLimite(Player jogador) {
        return getLimiteDiario() > 0 && !jogador.hasPermission(PERMISSAO_IGNORAR);
    }

    public double getGastoHoje(UUID jogador) {
        Gasto gasto = gastos.get(jogador);
        return gasto != null && gasto.dia().equals(LocalDate.now()) ? gasto.valor() : 0;
    }

    public double getDisponivelHoje(UUID jogador) {
        return Math.max(0, getLimiteDiario() - getGastoHoje(jogador));
    }

    /** true se o jogador ainda pode enviar {@code valor} hoje sem passar do limite. */
    public boolean podeEnviar(Player jogador, double valor) {
        return !temLimite(jogador) || valor <= getDisponivelHoje(jogador.getUniqueId()) + 0.000001;
    }

    public void registrarEnvio(UUID jogador, double valor) {
        gastos.put(jogador, new Gasto(LocalDate.now(), getGastoHoje(jogador) + valor));
        salvar();
    }

    /** Zera o gasto do dia de um jogador (staff, ex: liberou uma transação legítima grande). */
    public boolean resetar(UUID jogador) {
        boolean tinha = gastos.remove(jogador) != null;
        if (tinha) salvar();
        return tinha;
    }

    // ---------------------------------------------------------------------
    // Persistência (limites.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("gastos");
        if (secao == null) return;

        LocalDate hoje = LocalDate.now();
        for (String chave : secao.getKeys(false)) {
            try {
                LocalDate dia = LocalDate.parse(secao.getString(chave + ".dia"));
                if (!dia.equals(hoje)) continue; // de outro dia, já zerou
                gastos.put(UUID.fromString(chave), new Gasto(dia, secao.getDouble(chave + ".valor")));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Entrada inválida ignorada em limites.yml: " + chave);
            }
        }
    }

    public synchronized void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        LocalDate hoje = LocalDate.now();
        for (Map.Entry<UUID, Gasto> entrada : gastos.entrySet()) {
            Gasto gasto = entrada.getValue();
            if (!gasto.dia().equals(hoje)) continue; // não precisa guardar dia que já passou
            String base = "gastos." + entrada.getKey();
            yaml.set(base + ".dia", gasto.dia().toString());
            yaml.set(base + ".valor", gasto.valor());
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar limites.yml", e);
        }
    }
}
