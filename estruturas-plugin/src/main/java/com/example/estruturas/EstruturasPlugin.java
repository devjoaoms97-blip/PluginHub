package com.example.estruturas;

import com.example.estruturas.command.EstruturaCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class EstruturasPlugin extends JavaPlugin {

    private Construtor construtor;

    @Override
    public void onEnable() {
        this.construtor = new Construtor(this);

        EstruturaCommand comando = new EstruturaCommand(this);
        getCommand("estrutura").setExecutor(comando);
        getCommand("estrutura").setTabCompleter(comando);

        getLogger().info("EstruturasPlugin ativado! " + construtor.getEstruturas().size() + " estrutura(s) disponível(is).");
    }

    public Construtor getConstrutor() {
        return construtor;
    }
}
