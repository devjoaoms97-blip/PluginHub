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
 * de um dupe, por exemplo) é recusado.
 */
public class ChequeLog {

    private final ChequePlugin plugin;
    private final File arquivoLog;
    private final File arquivoResgatados;
    private final Set<String> resgatados = new HashSet<>();

    public ChequeLog(ChequePlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.arquivoLog = new File(plugin.getDataFolder(), "cheques.log");
        this.arquivoResgatados = new File(plugin.getDataFolder(), "resgatados.yml");
        resgatados.addAll(YamlConfiguration.loadConfiguration(arquivoResgatados).getStringList("ids"));
    }

    public boolean jaResgatado(String id) {
        return id != null && resgatados.contains(id);
    }

    public void emitido(String emissor, double valor, String id, String nominal) {
        escrever("EMITIDO   " + emissor + " " + valor(valor) + " id=" + id + (nominal != null ? " para=" + nominal : ""));
    }

    public void resgatado(String quem, double valor, String id) {
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
