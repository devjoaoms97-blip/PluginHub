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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Resource pack do servidor (as texturas próprias: produtos e máquinas). O pacote vem dentro do jar
 * (gerado pelo tools/pacote/gerar.py), é servido por um HTTP embutido ({@code porta}) e mandado pra
 * cada jogador ao entrar, com a hash: quem já tem a versão certa não baixa de novo.
 * <p>
 * No servidor de verdade, {@code url-publica} precisa ser o endereço que os jogadores alcançam
 * (ex: http://meuservidor.com.br:8765/pacote.zip), com a porta liberada.
 */
public class PacotePlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();

    private byte[] pacote;
    private byte[] hash;
    private UUID id;
    private HttpServer http;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try (InputStream in = getResource("pacote.zip")) {
            if (in == null) throw new IOException("pacote.zip não está no jar");
            pacote = in.readAllBytes();
            hash = MessageDigest.getInstance("SHA-1").digest(pacote);
        } catch (IOException | NoSuchAlgorithmException e) {
            getLogger().severe("Sem o pacote: " + e.getMessage());
            return;
        }
        // id estável por conteúdo: o cliente troca o pacote antigo pelo novo em vez de empilhar
        id = UUID.nameUUIDFromBytes(hash);
        int porta = getConfig().getInt("porta", 8765);
        try {
            http = HttpServer.create(new InetSocketAddress(porta), 0);
            http.createContext("/pacote.zip", troca -> {
                troca.getResponseHeaders().add("Content-Type", "application/zip");
                troca.sendResponseHeaders(200, pacote.length);
                try (OutputStream out = troca.getResponseBody()) {
                    out.write(pacote);
                }
            });
            http.setExecutor(Executors.newFixedThreadPool(2));
            http.start();
        } catch (IOException e) {
            getLogger().severe("Não deu pra abrir a porta " + porta + " do pacote: " + e.getMessage());
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Pacote de texturas (" + pacote.length + " bytes, sha1 " + HexFormat.of().formatHex(hash) + ") em " + url());
    }

    @Override
    public void onDisable() {
        if (http != null) http.stop(0);
    }

    private String url() {
        String publica = getConfig().getString("url-publica", "").trim();
        return publica.isEmpty() ? "http://localhost:" + getConfig().getInt("porta", 8765) + "/pacote.zip" : publica;
    }

    private void enviar(Player p) {
        p.setResourcePack(id, url(), hash, LEGADO.deserialize(getConfig().getString("mensagem",
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
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Pacote: " + url() + " (" + (pacote == null ? 0 : pacote.length) + " bytes)");
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
