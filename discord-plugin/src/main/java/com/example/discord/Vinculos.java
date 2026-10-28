package com.example.discord;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Vínculo entre a conta do Minecraft e a do Discord. O jogador gera um código no jogo
 * ({@code /vincular}) e o bot confirma com ele (API local), então só quem está logado na conta do
 * Minecraft consegue vincular. Uma conta do Discord por conta do Minecraft, e vice-versa.
 * <p>
 * A recompensa ({@code vinculo.recompensa}, comandos de console) sai uma vez por conta do Minecraft
 * e uma vez por conta do Discord, e só se o bot disser que a conta do Discord é antiga o bastante:
 * desvincular e vincular de novo não dá outra. Tudo fica em vinculos.yml.
 * <p>
 * Os métodos mexem em jogador e rodam comandos: chamar na thread do servidor.
 */
public class Vinculos {

    private static final String LETRAS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // sem 0/O, 1/I/L
    private static final SecureRandom SORTEIO = new SecureRandom();

    public record Vinculo(UUID uuid, String nick, String discordId, String discordNome, long desde) {}

    public record Resultado(boolean ok, String erro, Vinculo vinculo, boolean recompensado) {
        static Resultado falha(String erro) {
            return new Resultado(false, erro, null, false);
        }
    }

    private record Pendente(UUID uuid, String nick, long expira) {}

    private final DiscordPlugin plugin;
    private final File arquivo;
    private final Map<String, Pendente> codigos = new HashMap<>();
    private final Map<UUID, Vinculo> porJogador = new LinkedHashMap<>();
    private final Map<String, UUID> porDiscord = new HashMap<>();
    private final Set<UUID> recompensadosJogador = new HashSet<>();
    private final Set<String> recompensadosDiscord = new HashSet<>();

    public Vinculos(DiscordPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "vinculos.yml");
        carregar();
    }

    /** Novo código pro jogador (o anterior dele deixa de valer). */
    public String gerarCodigo(Player jogador) {
        codigos.values().removeIf(p -> p.uuid().equals(jogador.getUniqueId()) || p.expira() < System.currentTimeMillis());
        String codigo;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(LETRAS.charAt(SORTEIO.nextInt(LETRAS.length())));
            codigo = sb.toString();
        } while (codigos.containsKey(codigo));
        long minutos = Math.max(1, plugin.getConfig().getInt("vinculo.codigo-minutos", 10));
        codigos.put(codigo, new Pendente(jogador.getUniqueId(), jogador.getName(), System.currentTimeMillis() + minutos * 60_000));
        return codigo;
    }

    public long minutosDoCodigo() {
        return Math.max(1, plugin.getConfig().getInt("vinculo.codigo-minutos", 10));
    }

    /** O bot confirmou um código: vincula, dá a recompensa (se couber) e avisa o jogador. */
    public Resultado confirmar(String codigo, String discordId, String discordNome, boolean contaElegivel) {
        String chave = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT).replace("-", "");
        Pendente p = codigos.get(chave);
        if (p == null || p.expira() < System.currentTimeMillis()) {
            codigos.remove(chave);
            return Resultado.falha("Código inválido ou vencido. Gere outro no jogo com /vincular.");
        }
        if (discordId == null || !discordId.matches("\\d{5,25}")) return Resultado.falha("ID do Discord inválido.");
        UUID jaDoDiscord = porDiscord.get(discordId);
        if (jaDoDiscord != null && !jaDoDiscord.equals(p.uuid())) {
            return Resultado.falha("Este Discord já está vinculado a " + porJogador.get(jaDoDiscord).nick() + ". Peça à staff pra desvincular.");
        }
        Vinculo atual = porJogador.get(p.uuid());
        if (atual != null && !atual.discordId().equals(discordId)) {
            return Resultado.falha("A conta " + p.nick() + " já está vinculada a outro Discord. Peça à staff pra desvincular.");
        }
        codigos.remove(chave);

        Vinculo v = new Vinculo(p.uuid(), p.nick(), discordId, discordNome, atual != null ? atual.desde() : System.currentTimeMillis());
        porJogador.put(p.uuid(), v);
        porDiscord.put(discordId, p.uuid());

        boolean recompensa = contaElegivel && !recompensadosJogador.contains(p.uuid()) && !recompensadosDiscord.contains(discordId);
        if (recompensa) {
            recompensadosJogador.add(p.uuid());
            recompensadosDiscord.add(discordId);
            for (String comando : plugin.getConfig().getStringList("vinculo.recompensa")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando.replace("%jogador%", p.nick()));
            }
        }
        salvar();
        plugin.getLogger().info(p.nick() + " vinculou a conta ao Discord " + discordNome + " (" + discordId + ")"
                + (recompensa ? ", com recompensa." : "."));

        Player online = Bukkit.getPlayer(p.uuid());
        if (online != null) {
            online.sendMessage("§a§l✔ §aConta vinculada ao Discord §f" + discordNome + "§a!");
            if (recompensa) online.sendMessage("§a" + plugin.getConfig().getString("vinculo.mensagem-recompensa",
                    "Presente pelo vínculo: 5 bilhetes grátis da Mine-sena!"));
        }
        return new Resultado(true, null, v, recompensa);
    }

    /** Desfaz o vínculo pelo jogador ou pelo Discord. Retorna o vínculo desfeito, ou null. */
    public Vinculo remover(UUID uuid, String discordId) {
        UUID alvo = uuid != null ? uuid : discordId == null ? null : porDiscord.get(discordId);
        if (alvo == null) return null;
        Vinculo v = porJogador.remove(alvo);
        if (v == null) return null;
        porDiscord.remove(v.discordId());
        salvar();
        plugin.getLogger().info("Vínculo de " + v.nick() + " com o Discord " + v.discordNome() + " desfeito.");
        return v;
    }

    public Vinculo doJogador(UUID uuid) {
        return porJogador.get(uuid);
    }

    public List<Vinculo> todos() {
        return new ArrayList<>(porJogador.values());
    }

    /** Tier do VIP ativo (BRONZE, PRATA...), pelo VipPlugin por reflexão; null sem VIP ou sem o plugin. */
    public String tierVip(UUID uuid) {
        Plugin vip = Bukkit.getPluginManager().getPlugin("VipPlugin");
        if (vip == null || !vip.isEnabled()) return null;
        try {
            Object manager = vip.getClass().getMethod("getVipManager").invoke(vip);
            Object tier = manager.getClass().getMethod("getTierAtivo", UUID.class).invoke(manager, uuid);
            return tier == null ? null : tier.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** O nick pode ter mudado desde o vínculo: atualiza quando o jogador entra. */
    public void atualizarNick(Player jogador) {
        Vinculo v = porJogador.get(jogador.getUniqueId());
        if (v == null || v.nick().equals(jogador.getName())) return;
        porJogador.put(v.uuid(), new Vinculo(v.uuid(), jogador.getName(), v.discordId(), v.discordNome(), v.desde()));
        salvar();
    }

    public OfflinePlayer acharJogador(String nome) {
        return Bukkit.getOfflinePlayerIfCached(nome);
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection s = yaml.getConfigurationSection("vinculos");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection v = s.getConfigurationSection(id);
                if (v == null) continue;
                UUID uuid = UUID.fromString(id);
                Vinculo vinculo = new Vinculo(uuid, v.getString("nick", "?"), v.getString("discord-id", ""),
                        v.getString("discord-nome", "?"), v.getLong("desde"));
                porJogador.put(uuid, vinculo);
                porDiscord.put(vinculo.discordId(), uuid);
            }
        }
        for (String id : yaml.getStringList("recompensados.jogadores")) recompensadosJogador.add(UUID.fromString(id));
        recompensadosDiscord.addAll(yaml.getStringList("recompensados.discord"));
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Vinculo v : porJogador.values()) {
            String base = "vinculos." + v.uuid() + ".";
            yaml.set(base + "nick", v.nick());
            yaml.set(base + "discord-id", v.discordId());
            yaml.set(base + "discord-nome", v.discordNome());
            yaml.set(base + "desde", v.desde());
        }
        yaml.set("recompensados.jogadores", recompensadosJogador.stream().map(UUID::toString).toList());
        yaml.set("recompensados.discord", new ArrayList<>(recompensadosDiscord));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar vinculos.yml", e);
        }
    }

    /** Limpa códigos vencidos (chamado de vez em quando). */
    public void limparCodigos() {
        long agora = System.currentTimeMillis();
        for (Iterator<Pendente> it = codigos.values().iterator(); it.hasNext(); ) {
            if (it.next().expira() < agora) it.remove();
        }
    }
}
