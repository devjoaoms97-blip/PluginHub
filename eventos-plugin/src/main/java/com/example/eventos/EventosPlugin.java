package com.example.eventos;

import com.example.eventos.util.DiscordIntegration;
import com.example.eventos.util.Premio;
import com.example.eventos.vietna.Vietna;
import com.example.eventos.vietna.VietnaCommand;
import com.example.eventos.vietna.VietnaListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Eventos do servidor. O primeiro é o Vietnã ({@link Vietna}): pilares sobre a água, todos com o mesmo
 * kit, e o último de pé leva um cheque. Abre sozinho nos horários de {@code vietna.agenda}.
 */
public class EventosPlugin extends JavaPlugin {

    private Vietna vietna;
    private DiscordIntegration discord;
    private Premio premio;
    private String ultimoMinutoAgenda = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discord = new DiscordIntegration(this);
        this.premio = new Premio(this);
        this.vietna = new Vietna(this);

        VietnaCommand comando = new VietnaCommand(this);
        getCommand("vietna").setExecutor(comando);
        getCommand("vietna").setTabCompleter(comando);
        getServer().getPluginManager().registerEvents(new VietnaListener(this), this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // um /reload com gente que tinha caído no meio do evento: volta pra onde estava
        getServer().getOnlinePlayers().forEach(vietna::aoEntrar);
        getLogger().info("EventosPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (vietna != null) vietna.cancelar("o servidor está desligando");
    }

    private void tick() {
        agenda();
        vietna.tick();
    }

    /** Abre o Vietnã nos horários do dia ({@code vietna.agenda: ["16:00", "21:00"]}), uma vez por minuto agendado. */
    private void agenda() {
        LocalDateTime agora = LocalDateTime.now();
        String minuto = agora.toLocalDate() + " " + agora.getHour() + ":" + agora.getMinute();
        if (minuto.equals(ultimoMinutoAgenda)) return;
        for (String horario : getConfig().getStringList("vietna.agenda")) {
            try {
                LocalTime h = LocalTime.parse(horario.trim());
                if (h.getHour() != agora.getHour() || h.getMinute() != agora.getMinute()) continue;
            } catch (DateTimeParseException e) {
                continue;
            }
            ultimoMinutoAgenda = minuto;
            if (vietna.getFase() != null) getLogger().info("Vietnã agendado pulado: já há um aberto.");
            else vietna.abrir();
            return;
        }
    }

    public boolean isAdmin(Player p) {
        return p.hasPermission("eventos.admin");
    }

    public Vietna getVietna() {
        return vietna;
    }

    public DiscordIntegration getDiscord() {
        return discord;
    }

    public Premio getPremio() {
        return premio;
    }
}
