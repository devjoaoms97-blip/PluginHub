package com.example.recursos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reset do mundo de recursos: a cada {@code reset.a-cada-dias} dias, no horário
 * {@code reset.hora}, com avisos no chat antes. Quem está lá dentro é levado pra fora, o
 * Multiverse regenera o mundo com uma seed nova (mantendo as regras e a borda), e a borda é
 * reaplicada. Cada reset aumenta o "ciclo"; quem estava no mundo antigo e entra depois vai pra um
 * lugar seguro, em vez de aparecer dentro de uma pedra do mundo novo.
 */
public class ResetManager {

    private static final Pattern OTP = Pattern.compile("confirm (\\d+)");

    private final RecursosPlugin plugin;
    private final File arquivo;
    private long ultimoReset;
    private int ciclo;
    /** Reset marcado pra esse horário (agenda ou staff), ou 0. */
    private long marcadoPara;
    private final Set<Integer> avisosDados = new HashSet<>();

    public ResetManager(RecursosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "dados.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        this.ultimoReset = yaml.getLong("ultimo-reset", System.currentTimeMillis());
        this.ciclo = yaml.getInt("ciclo", 1);
        if (!arquivo.exists()) salvar(); // a contagem começa na instalação
    }

    public int getCiclo() {
        return ciclo;
    }

    public long getMarcadoPara() {
        return marcadoPara;
    }

    /** Próximo reset automático: o dia em que vence o intervalo, no horário do config. */
    public long proximoAutomatico() {
        int dias = Math.max(1, plugin.getConfig().getInt("reset.a-cada-dias", 30));
        LocalTime hora;
        try {
            hora = LocalTime.parse(plugin.getConfig().getString("reset.hora", "05:00"));
        } catch (DateTimeParseException e) {
            hora = LocalTime.of(5, 0);
        }
        ZoneId zona = ZoneId.systemDefault();
        LocalDateTime base = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ultimoReset), zona).plusDays(dias);
        return base.toLocalDate().atTime(hora).atZone(zona).toInstant().toEpochMilli();
    }

    /** Quando acontece o próximo: o marcado pela staff, ou o automático. */
    public long proximo() {
        return marcadoPara > 0 ? marcadoPara : proximoAutomatico();
    }

    public void marcar(long quando) {
        marcadoPara = quando;
        avisosDados.clear();
    }

    public void cancelarMarcado() {
        marcadoPara = 0;
        avisosDados.clear();
    }

    /** A cada segundo: avisos e reset na hora. */
    public void tick() {
        if (!plugin.getConfig().getBoolean("reset.ativado", true) && marcadoPara == 0) return;
        long agora = System.currentTimeMillis();
        long quando = proximo();
        long faltaMin = (quando - agora + 59_999) / 60_000; // arredonda pra cima
        for (int minutos : plugin.getConfig().getIntegerList("reset.avisos-minutos")) {
            if (faltaMin == minutos && quando - agora > 0 && avisosDados.add(minutos)) {
                anunciar("§6§l[Recursos] §eO mundo de recursos vai ser §cresetado §eem §f" + minutos + " min§e. Tudo o que estiver lá "
                        + "(construções, baús, itens no chão) vai sumir. §7Leve o que é seu pro terreno.");
            }
        }
        if (agora >= quando) resetar();
    }

    /** Reseta agora. */
    public boolean resetar() {
        World mundo = plugin.getMundo();
        if (mundo == null) {
            plugin.getLogger().warning("O mundo " + plugin.getNomeMundo() + " não existe; reset pulado.");
            marcadoPara = 0;
            ultimoReset = System.currentTimeMillis();
            salvar();
            return false;
        }
        anunciar("§6§l[Recursos] §cResetando o mundo de recursos agora...");
        World destino = plugin.mundoDeSaida();
        for (Player p : new ArrayList<>(mundo.getPlayers())) {
            p.teleport(destino.getSpawnLocation());
            p.sendMessage("§eVocê foi tirado do mundo de recursos, que está sendo resetado.");
        }

        if (!regenerarPeloMultiverse()) return false;

        ciclo++;
        ultimoReset = System.currentTimeMillis();
        marcadoPara = 0;
        avisosDados.clear();
        salvar();
        World novo = plugin.getMundo();
        if (novo != null) aplicarBorda(novo);
        anunciar("§6§l[Recursos] §aO mundo de recursos foi resetado! §7Vá com §f/recursos§7.");
        plugin.getLogger().info("Mundo de recursos resetado (ciclo " + ciclo + ").");
        return true;
    }

    /**
     * {@code mv regen <mundo> --seed} (seed nova, mantendo regras e borda). O Multiverse pede
     * confirmação com um código que só aparece na resposta do comando, então o comando roda com
     * um remetente que guarda a resposta e confirma com o código.
     */
    private boolean regenerarPeloMultiverse() {
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
            plugin.getLogger().severe("Reset do mundo de recursos precisa do Multiverse-Core.");
            return false;
        }
        List<String> respostas = new ArrayList<>();
        CommandSender remetente = Bukkit.createCommandSender(c -> respostas.add(texto(c)));
        Bukkit.dispatchCommand(remetente, "mv regen " + plugin.getNomeMundo() + " --seed");
        Matcher m = OTP.matcher(String.join(" ", respostas));
        if (!m.find()) {
            plugin.getLogger().severe("O Multiverse não pediu confirmação do regen: " + String.join(" | ", respostas));
            return false;
        }
        respostas.clear();
        Bukkit.dispatchCommand(remetente, "mv confirm " + m.group(1));
        String resultado = String.join(" | ", respostas);
        plugin.getLogger().info("Multiverse: " + resultado);
        return plugin.getMundo() != null;
    }

    private static String texto(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Borda do mundo, pra ele não crescer sem fim (0 = sem borda). */
    public void aplicarBorda(World mundo) {
        double tamanho = plugin.getConfig().getDouble("borda.tamanho", 6000);
        if (tamanho <= 0) return;
        WorldBorder borda = mundo.getWorldBorder();
        borda.setCenter(plugin.getConfig().getDouble("borda.centro-x", 0), plugin.getConfig().getDouble("borda.centro-z", 0));
        borda.setSize(tamanho);
    }

    private static void anunciar(String mensagem) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ultimo-reset", ultimoReset);
        yaml.set("ciclo", ciclo);
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar dados.yml", e);
        }
    }
}
