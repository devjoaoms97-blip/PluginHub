package com.example.discord;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Entrega das compras da loja do Discord (o bot confere o pagamento no Mercado Pago e pede a
 * entrega pela API local). Roda os comandos de {@code loja.comandos} e anota o ID do pagamento em
 * entregas.yml: o mesmo pagamento nunca é entregue duas vezes, mesmo que o bot peça de novo (caiu
 * antes de anotar a resposta, por exemplo).
 * <p>
 * Roda comandos: chamar na thread do servidor.
 */
public class Entregas {

    public record Resultado(boolean ok, String erro, String nick, boolean repetida) {
        static Resultado falha(String erro) {
            return new Resultado(false, erro, null, false);
        }
    }

    private final DiscordPlugin plugin;
    private final File arquivo;
    private final YamlConfiguration yaml;

    public Entregas(DiscordPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "entregas.yml");
        this.yaml = YamlConfiguration.loadConfiguration(arquivo);
    }

    public Resultado entregar(String pagamento, String pedido, UUID uuid, long creditos) {
        if (pagamento == null || !pagamento.matches("[A-Za-z0-9_-]{1,40}")) return Resultado.falha("ID do pagamento inválido.");
        ConfigurationSection feita = yaml.getConfigurationSection("pagamentos." + pagamento);
        if (feita != null) return new Resultado(true, null, feita.getString("nick"), true);
        if (uuid == null || creditos <= 0) return Resultado.falha("Jogador ou quantidade inválidos.");
        if (!Bukkit.getPluginManager().isPluginEnabled("CreditosPlugin")) return Resultado.falha("CreditosPlugin desligado.");
        OfflinePlayer jogador = Bukkit.getOfflinePlayer(uuid);
        String nick = jogador.getName();
        if (nick == null || (!jogador.hasPlayedBefore() && !jogador.isOnline())) return Resultado.falha("Jogador " + uuid + " nunca entrou no servidor.");

        String motivo = "loja " + (pedido == null ? "" : pedido + " ") + "(pagamento " + pagamento + ")";
        List<String> comandos = plugin.getConfig().getStringList("loja.comandos");
        if (comandos.isEmpty()) comandos = List.of("creditos dar %jogador% %creditos% %motivo%");
        for (String comando : comandos) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando.replace("%jogador%", nick)
                    .replace("%creditos%", String.valueOf(creditos)).replace("%motivo%", motivo));
        }
        String base = "pagamentos." + pagamento + ".";
        yaml.set(base + "pedido", pedido);
        yaml.set(base + "uuid", uuid.toString());
        yaml.set(base + "nick", nick);
        yaml.set(base + "creditos", creditos);
        yaml.set(base + "quando", System.currentTimeMillis());
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar entregas.yml", e);
        }
        plugin.getLogger().info("Loja: " + creditos + " créditos entregues a " + nick + " (" + motivo + ").");
        return new Resultado(true, null, nick, false);
    }
}
