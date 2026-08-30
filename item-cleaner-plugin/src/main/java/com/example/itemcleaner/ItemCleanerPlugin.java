package com.example.itemcleaner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Limpa periodicamente itens dropados (entidades {@link Item}) no chão, pra reduzir lag em
 * servidores com muitos drops acumulados (mobs, farms automáticas, PvP, etc.).
 *
 * Funciona com uma contagem regressiva em segundos: a cada segundo, {@link #tick()} decrementa
 * {@link #segundosRestantes}; quando bate num dos valores de {@code avisos-segundos}, avisa no
 * chat; quando chega a zero, executa a limpeza e reinicia a contagem.
 */
public class ItemCleanerPlugin extends JavaPlugin {

    private int segundosRestantes;
    private BukkitTask tarefa;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ItemCleanerCommand executor = new ItemCleanerCommand(this);
        getCommand("limparchao").setExecutor(executor);
        getCommand("limparchao").setTabCompleter(executor);

        iniciarTarefa();

        getLogger().info("ItemCleanerPlugin ativado!");
    }

    @Override
    public void onDisable() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        getLogger().info("ItemCleanerPlugin desativado!");
    }

    /** Reinicia o temporizador da limpeza automática a partir do valor atual do config.yml. */
    public void iniciarTarefa() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        segundosRestantes = getConfig().getInt("intervalo-segundos", 300);
        tarefa = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    private void tick() {
        segundosRestantes--;

        List<Integer> avisos = getConfig().getIntegerList("avisos-segundos");
        if (segundosRestantes > 0 && avisos.contains(segundosRestantes)) {
            avisar(segundosRestantes);
        }

        if (segundosRestantes <= 0) {
            int removidos = limparItens();
            anunciarExecucao(removidos);
            segundosRestantes = getConfig().getInt("intervalo-segundos", 300);
        }
    }

    /** Avisa no chat e agenda uma limpeza única depois de {@code segundosAviso}. Usado pelo /limparchao manual. */
    public void limparComAviso(int segundosAviso) {
        avisar(segundosAviso);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int removidos = limparItens();
            anunciarExecucao(removidos);
        }, segundosAviso * 20L);
    }

    /**
     * Remove todos os itens no chão que passam pelos filtros configurados (idade mínima,
     * itens nomeados, mundos alvo). Retorna quantos foram removidos.
     */
    public int limparItens() {
        int removidos = 0;
        boolean ignorarNomeados = getConfig().getBoolean("ignorar-itens-nomeados", true);
        long idadeMinimaTicks = getConfig().getInt("idade-minima-segundos", 60) * 20L;

        for (World mundo : mundosAlvo()) {
            for (Entity entidade : mundo.getEntities()) {
                if (!(entidade instanceof Item item)) {
                    continue;
                }
                if (item.getTicksLived() < idadeMinimaTicks) {
                    continue;
                }
                if (ignorarNomeados && item.getItemStack().hasItemMeta()
                        && item.getItemStack().getItemMeta().hasDisplayName()) {
                    continue;
                }
                item.remove();
                removidos++;
            }
        }
        return removidos;
    }

    private List<World> mundosAlvo() {
        String modo = getConfig().getString("mundos.modo", "todos");
        List<String> lista = getConfig().getStringList("mundos.lista");

        if (modo.equalsIgnoreCase("somente-listados")) {
            return Bukkit.getWorlds().stream()
                    .filter(w -> lista.contains(w.getName()))
                    .collect(Collectors.toList());
        }
        if (modo.equalsIgnoreCase("exceto-listados")) {
            return Bukkit.getWorlds().stream()
                    .filter(w -> !lista.contains(w.getName()))
                    .collect(Collectors.toList());
        }
        return Bukkit.getWorlds();
    }

    private void avisar(int segundos) {
        String msg = getConfig().getString("mensagens.aviso", "&e[Limpeza] Itens no chão serão removidos em &f{segundos}s&e!");
        msg = msg.replace("{segundos}", String.valueOf(segundos));
        Bukkit.broadcastMessage(traduzirCores(msg));
    }

    private void anunciarExecucao(int quantidade) {
        if (!getConfig().getBoolean("mensagens.anunciar-execucao", true)) {
            return;
        }
        String msg = getConfig().getString("mensagens.executado", "&a[Limpeza] &f{quantidade} &aitens removidos do chão.");
        msg = msg.replace("{quantidade}", String.valueOf(quantidade));
        Bukkit.broadcastMessage(traduzirCores(msg));
    }

    private String traduzirCores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }

    public int getSegundosRestantes() {
        return segundosRestantes;
    }
}
