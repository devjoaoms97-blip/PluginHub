package com.example.combate;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Combate no estilo da 1.8, só no que foi escolhido: sem recarga de ataque, tempo entre golpes
 * configurável, bloqueio com a espada (sem escudo na outra mão) e golpe com a arma da segunda
 * mão. O resto (ataque em área, repulsão, regeneração, maçãs, ender pearl, escudo e machado)
 * continua como no jogo atual.
 */
public class CombatePlugin extends JavaPlugin {

    private SemRecarga semRecarga;
    private BloqueioEspada bloqueioEspada;
    private Invulnerabilidade invulnerabilidade;
    private DuasMaos duasMaos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getBoolean("sem-recarga.ativado", true)) {
            semRecarga = new SemRecarga(this);
            getServer().getPluginManager().registerEvents(semRecarga, this);
            getServer().getOnlinePlayers().forEach(semRecarga::aplicar); // /reload com gente online
        }
        invulnerabilidade = new Invulnerabilidade(this);
        getServer().getPluginManager().registerEvents(invulnerabilidade, this);
        CombateCommand comando = new CombateCommand(this);
        getCommand("combate").setExecutor(comando);
        getCommand("combate").setTabCompleter(comando);
        if (getConfig().getBoolean("bloqueio-espada.ativado", true)) {
            bloqueioEspada = new BloqueioEspada(this);
            getServer().getPluginManager().registerEvents(bloqueioEspada, this);
            getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player p : getServer().getOnlinePlayers()) bloqueioEspada.atualizar(p);
            }, 2L, 2L);
        }
        if (getConfig().getBoolean("duas-maos.ativado", true)) {
            duasMaos = new DuasMaos(this);
            getServer().getPluginManager().registerEvents(duasMaos, this);
        }
    }

    /** O golpe em andamento é o da segunda mão (DuasMaos). */
    public boolean golpeandoComASegundaMao(Player jogador) {
        return duasMaos != null && duasMaos.golpeandoComASegunda(jogador);
    }

    public Invulnerabilidade getInvulnerabilidade() {
        return invulnerabilidade;
    }

    @Override
    public void onDisable() {
        if (semRecarga != null) getServer().getOnlinePlayers().forEach(semRecarga::remover);
        if (bloqueioEspada != null) bloqueioEspada.limparTodos();
    }
}
