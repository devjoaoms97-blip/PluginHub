package com.example.rankup.manager;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.Rank;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Cada rankup comprado vira uma linha em {@code rankups.csv}, com as horas de jogo do jogador
 * naquele momento (a estatística do próprio Minecraft). É o dado que responde "quanto tempo
 * leva cada rank", pra calibrar os preços. O /ranks admin set não entra: não foi pago.
 */
public class RegistroRankups {

    private static final String CABECALHO = "data,uuid,jogador,de,para,preco,horas_jogadas\n";

    private final RankupPlugin plugin;
    private final Path arquivo;

    public RegistroRankups(RankupPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = plugin.getDataFolder().toPath().resolve("rankups.csv");
    }

    public void registrar(Player jogador, Rank de, Rank para) {
        double horas = jogador.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600; // a estatística é em ticks
        String linha = String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%.2f,%.2f\n",
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), jogador.getUniqueId(), jogador.getName(),
                de.id(), para.id(), para.preco(), horas);
        try {
            if (!Files.exists(arquivo)) {
                Files.createDirectories(arquivo.getParent());
                Files.writeString(arquivo, CABECALHO, StandardCharsets.UTF_8);
            }
            Files.writeString(arquivo, linha, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar rankups.csv", e);
        }
    }
}
