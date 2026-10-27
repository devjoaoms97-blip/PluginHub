package com.example.cheque;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Log dos cheques: cada emissão e resgate vira uma linha em cheques.log (data, quem, valor, ID,
 * nominal), e os IDs já resgatados ficam em resgatados.yml. Um cheque com ID já resgatado (cópia
 * de um dupe, por exemplo) é recusado. Os cancelados (/cheque cancelar) ficam em cancelados.yml, pelo
 * ID inteiro ou pelos 8 primeiros caracteres (o que aparece no lore), e também são recusados.
 * Os cheques em aberto (emitidos e ainda não resgatados nem cancelados) ficam em emitidos.yml, com
 * quem emitiu e o valor: é por ele que o cancelamento devolve o dinheiro.
 */
public class ChequeLog {

    private final ChequePlugin plugin;
    private final File arquivoLog;
    private final File arquivoResgatados;
    private final Set<String> resgatados = new HashSet<>();
    private final File arquivoCancelados;
    private final Set<String> cancelados = new HashSet<>();
    private final File arquivoEmitidos;
    /** id → {emissor, valor} dos cheques em aberto. */
    private final java.util.Map<String, Emissao> emitidos = new java.util.HashMap<>();

    /** Um cheque em aberto: quem pagou por ele e quanto. */
    public record Emissao(String id, java.util.UUID emissor, double valor) {}

    public ChequeLog(ChequePlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.arquivoLog = new File(plugin.getDataFolder(), "cheques.log");
        this.arquivoResgatados = new File(plugin.getDataFolder(), "resgatados.yml");
        resgatados.addAll(YamlConfiguration.loadConfiguration(arquivoResgatados).getStringList("ids"));
        this.arquivoCancelados = new File(plugin.getDataFolder(), "cancelados.yml");
        cancelados.addAll(YamlConfiguration.loadConfiguration(arquivoCancelados).getStringList("ids"));
        this.arquivoEmitidos = new File(plugin.getDataFolder(), "emitidos.yml");
        YamlConfiguration yamlEmitidos = YamlConfiguration.loadConfiguration(arquivoEmitidos);
        for (String id : yamlEmitidos.getKeys(false)) {
            try {
                emitidos.put(id, new Emissao(id, java.util.UUID.fromString(yamlEmitidos.getString(id + ".emissor", "")),
                        yamlEmitidos.getDouble(id + ".valor")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Cheque inválido ignorado em emitidos.yml: " + id);
            }
        }
    }

    /** O cheque em aberto com esse ID (inteiro ou os 8 primeiros caracteres), ou null. */
    public Emissao emissao(String idOuComeco) {
        if (idOuComeco == null) return null;
        Emissao exata = emitidos.get(idOuComeco);
        if (exata != null) return exata;
        for (Emissao e : emitidos.values()) {
            if (idOuComeco.length() >= 8 && e.id().startsWith(idOuComeco)) return e;
        }
        return null;
    }

    private void fechar(String id) {
        if (id != null && emitidos.remove(id) != null) salvarEmitidos();
    }

    private void salvarEmitidos() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Emissao e : emitidos.values()) {
            yaml.set(e.id() + ".emissor", e.emissor().toString());
            yaml.set(e.id() + ".valor", e.valor());
        }
        try {
            yaml.save(arquivoEmitidos);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar emitidos.yml", ex);
        }
    }

    /** Cancelado pelo ID inteiro ou pelo começo dele (os 8 caracteres do lore). */
    public boolean cancelado(String id) {
        return id != null && (cancelados.contains(id) || (id.length() >= 8 && cancelados.contains(id.substring(0, 8))));
    }

    /** Anula o cheque (ID inteiro, ou os 8 primeiros caracteres): ninguém mais resgata. */
    public void cancelar(String quem, String id, Double valor) {
        cancelados.add(id);
        Emissao aberto = emissao(id);
        if (aberto != null) fechar(aberto.id());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ids", cancelados.stream().toList());
        try {
            yaml.save(arquivoCancelados);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar cancelados.yml", e);
        }
        escrever("CANCELADO " + quem + (valor != null ? " " + valor(valor) : "") + " id=" + id);
    }

    public boolean jaResgatado(String id) {
        return id != null && resgatados.contains(id);
    }

    public void emitido(java.util.UUID emissorId, String emissor, double valor, String id, String nominal) {
        if (id != null) {
            emitidos.put(id, new Emissao(id, emissorId, valor));
            salvarEmitidos();
        }
        escrever("EMITIDO   " + emissor + " " + valor(valor) + " id=" + id + (nominal != null ? " para=" + nominal : ""));
    }

    public void resgatado(String quem, double valor, String id) {
        fechar(id);
        if (id != null) {
            resgatados.add(id);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("ids", resgatados.stream().toList());
            try {
                yaml.save(arquivoResgatados);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar resgatados.yml", e);
            }
        }
        escrever("RESGATADO " + quem + " " + valor(valor) + " id=" + id);
    }

    public void recusado(String quem, double valor, String id, String motivo) {
        escrever("RECUSADO  " + quem + " " + valor(valor) + " id=" + id + " (" + motivo + ")");
    }

    private void escrever(String linha) {
        String data = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PrintWriter out = new PrintWriter(new FileWriter(arquivoLog, StandardCharsets.UTF_8, true))) {
            out.println("[" + data + "] " + linha);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível escrever em cheques.log", e);
        }
    }

    private static String valor(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
