package com.example.pacote;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Resource pack do servidor (as texturas próprias: produtos, máquinas e skins). O pacote vem dentro do
 * jar (gerado pelo tools/pacote/gerar.py), é servido por um HTTP embutido ({@code porta}) e mandado pra
 * cada jogador ao entrar, com a hash: quem já tem a versão certa não baixa de novo.
 * <p>
 * Pacotes de outros plugins (os zips de {@code juntar}, como o que o FreeMinecraftModels gera com os
 * modelos dele) entram no mesmo zip: o cliente só aceita um pacote por vez daqui, e o nosso vence
 * quando os dois têm o mesmo arquivo. {@code /pacote recarregar} junta de novo e manda pra quem está online.
 * <p>
 * No servidor de verdade, {@code url-publica} precisa ser o endereço que os jogadores alcançam
 * (ex: http://meuservidor.com.br:8765/pacote.zip), com a porta liberada.
 */
public class PacotePlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();

    /** O pacote servido agora: o zip, a hash e o id (estável por conteúdo). */
    private record Pacote(byte[] zip, byte[] hash, UUID id) {}

    private volatile Pacote pacote;
    private HttpServer http;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!montar()) return;
        int porta = getConfig().getInt("porta", 8765);
        try {
            http = HttpServer.create(new InetSocketAddress(porta), 0);
            http.createContext("/pacote.zip", troca -> {
                byte[] zip = pacote.zip();
                troca.getResponseHeaders().add("Content-Type", "application/zip");
                troca.sendResponseHeaders(200, zip.length);
                try (OutputStream out = troca.getResponseBody()) {
                    out.write(zip);
                }
            });
            http.setExecutor(Executors.newFixedThreadPool(2));
            http.start();
        } catch (IOException e) {
            getLogger().severe("Não deu pra abrir a porta " + porta + " do pacote: " + e.getMessage());
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        // os outros plugins geram o pacote deles depois de ligar (e de novo num reload deles): a cada 5 s,
        // se algum zip de juntar mudou, junta de novo e manda pra quem está online
        datas = datasDosZips();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Map<String, Long> agora = datasDosZips();
            if (agora.equals(datas)) return;
            datas = agora;
            byte[] antes = pacote.hash();
            if (montar() && !MessageDigest.isEqual(antes, pacote.hash())) Bukkit.getOnlinePlayers().forEach(this::enviar);
        }, 100L, 100L);
    }

    private Map<String, Long> datas = Map.of();

    private Map<String, Long> datasDosZips() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String caminho : getConfig().getStringList("juntar")) {
            m.put(caminho, new File(getServer().getWorldContainer(), caminho).lastModified());
        }
        return m;
    }

    @Override
    public void onDisable() {
        if (http != null) http.stop(0);
    }

    /** Monta o pacote: o nosso (do jar) mais os zips de {@code juntar}. Retorna false se nem o nosso deu. */
    private boolean montar() {
        Map<String, byte[]> arquivos = new LinkedHashMap<>();
        try (InputStream in = getResource("pacote.zip")) {
            if (in == null) throw new IOException("pacote.zip não está no jar");
            ler(new ZipInputStream(in), arquivos);
        } catch (IOException e) {
            getLogger().severe("Sem o pacote: " + e.getMessage());
            return false;
        }
        int nossos = arquivos.size();
        for (String caminho : getConfig().getStringList("juntar")) {
            File zip = new File(getServer().getWorldContainer(), caminho);
            if (!zip.isFile()) continue;
            int juntados = 0, repetidos = 0;
            try (ZipFile z = new ZipFile(zip)) {
                for (ZipEntry e : java.util.Collections.list(z.entries())) {
                    String nome = e.getName();
                    // a descrição e o ícone são os nossos; arquivo repetido fica o nosso
                    if (e.isDirectory() || nome.equals("pack.mcmeta") || nome.equals("pack.png")) continue;
                    if (arquivos.containsKey(nome)) {
                        repetidos++;
                        continue;
                    }
                    try (InputStream in = z.getInputStream(e)) {
                        arquivos.put(nome, in.readAllBytes());
                    }
                    juntados++;
                }
            } catch (IOException e) {
                getLogger().warning("Não deu pra juntar " + caminho + ": " + e.getMessage());
                continue;
            }
            getLogger().info("Juntei " + juntados + " arquivos de " + caminho + (repetidos > 0 ? " (" + repetidos + " repetidos ficaram os nossos)" : ""));
        }
        try {
            byte[] zip = arquivos.size() == nossos ? semMudar() : gravar(arquivos);
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(zip);
            pacote = new Pacote(zip, hash, UUID.nameUUIDFromBytes(hash));
            getLogger().info("Pacote de texturas (" + zip.length + " bytes, sha1 " + HexFormat.of().formatHex(hash) + ") em " + url());
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            getLogger().severe("Não deu pra montar o pacote: " + e.getMessage());
            return false;
        }
    }

    /** O pacote do jar do jeito que veio (sem nada pra juntar, a hash é a mesma de sempre). */
    private byte[] semMudar() throws IOException {
        try (InputStream in = getResource("pacote.zip")) {
            return in.readAllBytes();
        }
    }

    private static void ler(ZipInputStream zip, Map<String, byte[]> arquivos) throws IOException {
        for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
            if (!e.isDirectory()) arquivos.put(e.getName(), zip.readAllBytes());
        }
    }

    private static byte[] gravar(Map<String, byte[]> arquivos) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (Map.Entry<String, byte[]> a : arquivos.entrySet()) {
                ZipEntry e = new ZipEntry(a.getKey());
                e.setTime(0);  // data fixa: o mesmo conteúdo dá a mesma hash
                zip.putNextEntry(e);
                zip.write(a.getValue());
                zip.closeEntry();
            }
        }
        return saida.toByteArray();
    }

    private String url() {
        String publica = getConfig().getString("url-publica", "").trim();
        return publica.isEmpty() ? "http://localhost:" + getConfig().getInt("porta", 8765) + "/pacote.zip" : publica;
    }

    private void enviar(Player p) {
        Pacote atual = pacote;
        p.setResourcePack(atual.id(), url(), atual.hash(), LEGADO.deserialize(getConfig().getString("mensagem",
                "&6Texturas do servidor &7(máquinas, produtos...)")), getConfig().getBoolean("obrigatorio", true));
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        if (pacote == null) return;
        Player p = evento.getPlayer();
        // um pouco depois de entrar: o cliente já carregou o mundo
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) enviar(p);
        }, 20L);
    }

    @EventHandler
    public void aoResponder(PlayerResourcePackStatusEvent evento) {
        if (evento.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            getLogger().warning(evento.getPlayer().getName() + " não conseguiu baixar o pacote de " + url());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("recarregar") && sender.hasPermission("pacote.admin")) {
            if (!montar()) {
                sender.sendMessage("§cNão deu pra montar o pacote (veja o console).");
                return true;
            }
            Bukkit.getOnlinePlayers().forEach(this::enviar);
            sender.sendMessage("§aPacote montado de novo (" + pacote.zip().length + " bytes) e mandado pra quem está online.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Pacote: " + url() + " (" + (pacote == null ? 0 : pacote.zip().length) + " bytes)");
            return true;
        }
        if (pacote == null) {
            p.sendMessage("§cO pacote de texturas não está disponível.");
            return true;
        }
        enviar(p);
        p.sendMessage("§7Mandando o pacote de texturas de novo...");
        return true;
    }
}
