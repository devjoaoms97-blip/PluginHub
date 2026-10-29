package com.example.recursos;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reset de um mundo de recursos: todo mês no dia {@code dia-do-mes} do mundo (num mês mais curto, o
 * último dia), no horário {@code reset.hora}, com avisos no chat antes. Quem está lá dentro é levado
 * pra fora, o Multiverse regenera o mundo com uma seed nova (mantendo as regras), e a borda é
 * reaplicada. Cada reset aumenta o "ciclo" do mundo; quem estava no mundo antigo e entra depois vai
 * pra um lugar seguro, em vez de aparecer dentro de uma pedra do mundo novo.
 */
public class ResetManager {

    private final RecursosPlugin plugin;
    /** O nome do mundo (Multiverse) e a chave dele em {@code mundos} e no {@code dados.yml}. */
    private final String mundo;
    private long ultimoReset;
    private int ciclo;
    /** Reset marcado pela staff pra esse horário, ou 0. */
    private long marcadoPara;
    private final Set<Integer> avisosDados = new HashSet<>();
    /** Esperando o Multiverse refazer o mundo. */
    private boolean regenerando;

    public ResetManager(RecursosPlugin plugin, String mundo, long ultimoReset, int ciclo) {
        this.plugin = plugin;
        this.mundo = mundo;
        this.ultimoReset = ultimoReset;
        this.ciclo = ciclo;
    }

    public String getMundo() {
        return mundo;
    }

    public World getWorld() {
        return Bukkit.getWorld(mundo);
    }

    private ConfigurationSection config() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("mundos." + mundo);
        return s != null ? s : plugin.getConfig().createSection("mundos." + mundo);
    }

    /** Como aparece nas mensagens: "mundo de recursos", "Nether", "End". */
    public String getNome() {
        return config().getString("nome", mundo);
    }

    public String getComando() {
        return config().getString("comando", mundo);
    }

    public int getRaio() {
        return config().getInt("raio", 2500);
    }

    public long getUltimoReset() {
        return ultimoReset;
    }

    public int getCiclo() {
        return ciclo;
    }

    public long getMarcadoPara() {
        return marcadoPara;
    }

    /** Próximo reset automático: o primeiro dia do mês do mundo, no horário do config, depois do último reset. */
    public long proximoAutomatico() {
        int dia = Math.max(1, Math.min(31, config().getInt("dia-do-mes", 30)));
        LocalTime hora;
        try {
            hora = LocalTime.parse(plugin.getConfig().getString("reset.hora", "05:00"));
        } catch (DateTimeParseException e) {
            hora = LocalTime.of(5, 0);
        }
        ZoneId zona = ZoneId.systemDefault();
        LocalDateTime ultimo = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ultimoReset), zona);
        LocalDate mes = ultimo.toLocalDate().withDayOfMonth(1);
        for (int i = 0; i < 3; i++, mes = mes.plusMonths(1)) {
            LocalDateTime candidato = mes.withDayOfMonth(Math.min(dia, mes.lengthOfMonth())).atTime(hora);
            if (candidato.isAfter(ultimo)) return candidato.atZone(zona).toInstant().toEpochMilli();
        }
        return Long.MAX_VALUE; // não acontece: em 3 meses sempre há um dia depois do último reset
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
                anunciar("§6§l[Recursos] §eO " + getNome() + " vai ser §cresetado §eem §f" + minutos + " min§e. Tudo o que estiver lá "
                        + "(construções, baús, itens no chão) vai sumir. §7Leve o que é seu pro terreno.");
            }
        }
        if (agora >= quando && !regenerando) resetar();
    }

    /**
     * Começa o reset agora: tira quem está no mundo e pede o {@code mv regen <mundo> --seed} (seed nova,
     * mantendo as regras do mundo) pelo console. O Multiverse refaz o mundo um instante depois, então o
     * fim (ciclo, borda, centro do End, aviso) espera o mundo novo aparecer, até 30 s. Devolve false se
     * nem deu pra começar.
     * <p>
     * O console precisa do {@code confirm-mode: disable_console} do Multiverse (o configurar-mundos.ps1
     * liga): senão o regen pede um {@code /mv confirm} que ninguém dá.
     */
    public boolean resetar() {
        if (regenerando) return false;
        World w = getWorld();
        if (w == null) {
            plugin.getLogger().warning("O mundo " + mundo + " não existe; reset pulado.");
            marcadoPara = 0;
            ultimoReset = System.currentTimeMillis();
            plugin.salvarDados();
            return false;
        }
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
            plugin.getLogger().severe("O reset dos mundos de recursos precisa do Multiverse-Core.");
            return false;
        }
        anunciar("§6§l[Recursos] §cResetando o " + getNome() + " agora...");
        World destino = plugin.mundoDeSaida();
        for (Player p : new ArrayList<>(w.getPlayers())) {
            p.teleport(destino.getSpawnLocation());
            p.sendMessage("§eVocê foi tirado do " + getNome() + ", que está sendo resetado.");
        }
        UUID idAntes = w.getUID();
        regenerando = true;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv regen " + mundo + " --seed");
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                World novo = getWorld();
                if (novo != null && !novo.getUID().equals(idAntes)) {
                    cancel();
                    concluir(novo);
                } else if (++ticks > 600) {
                    cancel();
                    regenerando = false;
                    marcadoPara = 0;
                    plugin.getLogger().severe("O regen de " + mundo + " não aconteceu em 30 s. Confira o \"confirm-mode: disable_console\" "
                            + "no config do Multiverse (mv config confirm-mode disable_console).");
                    anunciar("§6§l[Recursos] §cO reset do " + getNome() + " falhou. A staff foi avisada.");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void concluir(World novo) {
        regenerando = false;
        ciclo++;
        ultimoReset = System.currentTimeMillis();
        marcadoPara = 0;
        avisosDados.clear();
        plugin.salvarDados();
        plugin.prepararMundo(novo);
        anunciar("§6§l[Recursos] §aO " + getNome() + " foi resetado! §7Vá com §f/" + getComando() + "§7.");
        plugin.getLogger().info(mundo + " resetado (ciclo " + ciclo + ").");
    }

    /** Borda do mundo, centrada no 0, 0 (0 = sem borda). */
    public void aplicarBorda(World w) {
        double tamanho = config().getDouble("borda", 0);
        if (tamanho <= 0) return;
        WorldBorder borda = w.getWorldBorder();
        borda.setCenter(0, 0);
        borda.setSize(tamanho);
    }

    private static void anunciar(String mensagem) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
    }
}
