package com.example.itemcleaner;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A limpeza só alcança os chunks carregados. Item num chunk sem ninguém por perto (as minas do fundo da
 * avenida, um terreno vazio) ficava guardado no chunk e voltava inteiro quando alguém chegava, e o
 * relógio de sumiço do próprio jogo não anda com o chunk descarregado.
 * <p>
 * Por isso cada item ganha a hora em que nasceu, e a hora da última limpeza fica salva
 * ({@code limpeza.yml}). Quando um chunk carrega, o item que nasceu antes da última limpeza já devia
 * ter saído: sai agora, pelo mesmo caminho da limpeza (o que tem dono vai pros achados e perdidos).
 * Item sem a hora (de antes desta versão) conta como velho.
 */
public class LimpezaAtrasada implements Listener {

    private final ItemCleanerPlugin plugin;
    private final NamespacedKey nasceu;
    private final File arquivo;
    private long ultimaLimpeza;

    LimpezaAtrasada(ItemCleanerPlugin plugin) {
        this.plugin = plugin;
        this.nasceu = new NamespacedKey(plugin, "nasceu");
        this.arquivo = new File(plugin.getDataFolder(), "limpeza.yml");
        ultimaLimpeza = YamlConfiguration.loadConfiguration(arquivo).getLong("ultima", 0);
    }

    /** Chamado a cada limpeza (automática ou manual). */
    void limpou() {
        ultimaLimpeza = System.currentTimeMillis();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ultima", ultimaLimpeza);
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar limpeza.yml: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoNascer(ItemSpawnEvent evento) {
        evento.getEntity().getPersistentDataContainer().set(nasceu, PersistentDataType.LONG, System.currentTimeMillis());
    }

    @EventHandler
    public void aoCarregar(EntitiesLoadEvent evento) {
        if (ultimaLimpeza == 0 || !plugin.mundoAlvo(evento.getWorld())) return;
        List<Item> velhos = evento.getEntities().stream()
                .filter(e -> e instanceof Item item && nasceuEm(item) < ultimaLimpeza)
                .map(e -> (Item) e).toList();
        if (velhos.isEmpty()) return;
        // um tick depois: tirar entidade no meio do carregamento do chunk não é seguro
        Bukkit.getScheduler().runTask(plugin, () -> {
            int removidos = 0;
            for (Item item : velhos) {
                if (item.isValid() && plugin.limparItem(item)) removidos++;
            }
            if (removidos > 0) plugin.salvarAchados();
        });
    }

    private long nasceuEm(Entity item) {
        Long quando = item.getPersistentDataContainer().get(nasceu, PersistentDataType.LONG);
        return quando == null ? 0 : quando;
    }
}
