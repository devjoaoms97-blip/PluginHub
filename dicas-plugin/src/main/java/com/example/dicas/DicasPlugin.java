package com.example.dicas;

import com.example.dicas.command.DicaCommand;
import com.example.dicas.manager.Dialogos;
import com.example.dicas.manager.NpcsManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DicasPlugin extends JavaPlugin {

    public static final String PERMISSAO_ADMIN = "dicas.admin";

    private NpcsManager npcs;
    private Dialogos dialogos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.npcs = new NpcsManager(this);
        this.dialogos = new Dialogos(this);

        getServer().getPluginManager().registerEvents(npcs, this);
        getServer().getPluginManager().registerEvents(dialogos, this);

        DicaCommand comando = new DicaCommand(this);
        getCommand("dica").setExecutor(comando);
        getCommand("dica").setTabCompleter(comando);

        getServer().getScheduler().runTask(this, npcs::carregarTodos);
        // NPC que sumiu sem passar pelo evento de morte (um /kill) volta
        getServer().getScheduler().runTaskTimer(this, npcs::conferir, 20L * 30, 20L * 30);

        getLogger().info("DicasPlugin ativado! " + npcs.getNpcs().size() + " NPC(s) de dicas.");
    }

    @Override
    public void onDisable() {
        if (dialogos != null) dialogos.encerrarTodas();
        if (npcs != null) npcs.removerTodos();
    }

    public NpcsManager getNpcs() {
        return npcs;
    }
}
