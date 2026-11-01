package com.example.chat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * O chat do servidor: canal local (o mundo) e global ({@code /g}), em {@link Canais}, e o anti-flood,
 * em {@link AntiFlood}. As tags (VIP, clã, rank, Gladiador) continuam nos plugins delas.
 */
public class ChatPlugin extends JavaPlugin {

    private AntiFlood antiFlood;
    private Canais canais;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        canais = new Canais(this);
        antiFlood = new AntiFlood(this);
        getServer().getPluginManager().registerEvents(antiFlood, this);
        getServer().getPluginManager().registerEvents(canais, this);
        getCommand("g").setExecutor(canais);
        getLogger().info("ChatPlugin ativado!");
    }

    Canais getCanais() {
        return canais;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            antiFlood.recarregar();
            sender.sendMessage("§aConfig do chat recarregado.");
            return true;
        }
        sender.sendMessage("§cUso: §f/antiflood reload");
        return true;
    }
}
