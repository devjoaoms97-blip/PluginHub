package com.example.terrenos.mundo;

import com.example.terrenos.TerrenosPlugin;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Põe a camada de bedrock do fundo ({@code grade.fundo}) nos chunks gerados antes dela existir (ou
 * antes de o fundo mudar no config). Cada chunk guarda a altura em que já recebeu a camada, então
 * isso roda uma vez só por chunk. O que estiver nessa camada (pedra, ou algo que o jogador cavou
 * e pôs lá) vira bedrock; o que fica embaixo dela não dá mais pra alcançar.
 */
public class FundoDeBedrock implements Listener {

    private final TerrenosPlugin plugin;
    private final NamespacedKey chaveFundo;

    public FundoDeBedrock(TerrenosPlugin plugin) {
        this.plugin = plugin;
        this.chaveFundo = new NamespacedKey(plugin, "fundo");
    }

    /** Ao ligar: os chunks que já estão carregados. */
    public void conferirCarregados() {
        World mundo = plugin.getMundo();
        if (mundo == null) return;
        for (Chunk chunk : mundo.getLoadedChunks()) conferir(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoCarregar(ChunkLoadEvent evento) {
        if (evento.getWorld().equals(plugin.getMundo())) conferir(evento.getChunk());
    }

    private void conferir(Chunk chunk) {
        int fundo = plugin.getGrade().fundo(chunk.getWorld().getMinHeight());
        Integer feito = chunk.getPersistentDataContainer().get(chaveFundo, PersistentDataType.INTEGER);
        if (feito != null && feito == fundo) return;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                if (chunk.getBlock(dx, fundo, dz).getType() != Material.BEDROCK) {
                    chunk.getBlock(dx, fundo, dz).setType(Material.BEDROCK, false);
                }
            }
        }
        chunk.getPersistentDataContainer().set(chaveFundo, PersistentDataType.INTEGER, fundo);
    }
}
