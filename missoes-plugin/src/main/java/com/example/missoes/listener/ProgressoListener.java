package com.example.missoes.listener;

import com.example.missoes.MissoesPlugin;
import com.example.missoes.model.TipoMissao;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Conta o progresso das missões.
 *
 * Anti-farm:
 * <ul>
 *   <li><b>Minerar</b>: bloco colocado por jogador não conta (senão era só colocar e quebrar
 *       pedra no mesmo lugar). A lista de blocos colocados fica em memória, limitada aos
 *       mais recentes — depois de um restart, blocos colocados antes contam de novo.</li>
 *   <li><b>Colher</b>: só planta madura (idade máxima) conta. Replantar e esperar crescer é
 *       justamente o que a missão pede.</li>
 *   <li><b>Matar</b>: mob nascido de spawner não conta (configurável), pra não virar farm AFK.</li>
 * </ul>
 *
 * A missão de vender é contada pelo EconomyShopPlugin (ele chama o MissoesPlugin); a de dano no
 * boss é contada aqui, reconhecendo o boss pela chave compartilhada {@code pluginhub:boss_mob}.
 */
public class ProgressoListener implements Listener {

    private static final int MAX_BLOCOS_COLOCADOS = 100_000;

    private final MissoesPlugin plugin;
    private final NamespacedKey chaveSpawner;
    private final NamespacedKey chaveBoss = new NamespacedKey("pluginhub", "boss_mob");
    // dano no boss que ainda não completou 1 ponto inteiro, por jogador (o golpe vem quebrado: 7,5...)
    private final Map<UUID, Double> sobraDanoBoss = new HashMap<>();

    // "mundo:x:y:z" -> true, do mais antigo pro mais novo (o mais antigo sai quando passa do limite)
    private final Map<String, Boolean> blocosColocados = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> maisAntigo) {
            return size() > MAX_BLOCOS_COLOCADOS;
        }
    };

    public ProgressoListener(MissoesPlugin plugin) {
        this.plugin = plugin;
        this.chaveSpawner = new NamespacedKey(plugin, "de_spawner");
    }

    private String chave(Block bloco) {
        UUID mundo = bloco.getWorld().getUID();
        return mundo + ":" + bloco.getX() + ":" + bloco.getY() + ":" + bloco.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        blocosColocados.put(chave(evento.getBlock()), Boolean.TRUE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Block bloco = evento.getBlock();
        boolean colocadoPorJogador = blocosColocados.remove(chave(bloco)) != null;
        Player jogador = evento.getPlayer();
        String material = bloco.getType().name();

        if (bloco.getBlockData() instanceof Ageable planta) {
            if (planta.getAge() >= planta.getMaximumAge()) {
                plugin.getMissaoManager().registrar(jogador, TipoMissao.COLHER, material, 1);
            }
            return; // planta não conta como mineração, madura ou não
        }

        if (!colocadoPorJogador) {
            plugin.getMissaoManager().registrar(jogador, TipoMissao.MINERAR, material, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoNascer(CreatureSpawnEvent evento) {
        if (evento.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            evento.getEntity().getPersistentDataContainer().set(chaveSpawner, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        LivingEntity morto = evento.getEntity();
        Player matador = morto.getKiller();
        if (matador == null || morto instanceof Player) return;

        if (plugin.getConfig().getBoolean("anti-farm.ignorar-mobs-de-spawner", true)
                && morto.getPersistentDataContainer().has(chaveSpawner, PersistentDataType.BYTE)) {
            return;
        }
        plugin.getMissaoManager().registrar(matador, TipoMissao.MATAR, morto.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoPescar(PlayerFishEvent evento) {
        if (evento.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(evento.getCaught() instanceof Item item)) return;
        // 1 por pescaria (a missão conta "vezes que pescou", não itens)
        plugin.getMissaoManager().registrar(evento.getPlayer(), TipoMissao.PESCAR,
                item.getItemStack().getType().name(), 1);
    }

    /** Mesmo critério do ranking do BossPlugin: só o dano que entrou, limitado à vida que o boss tinha. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoDanificarBoss(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof LivingEntity boss)) return;
        if (!boss.getPersistentDataContainer().has(chaveBoss, PersistentDataType.BYTE)) return;
        Player atacante = jogadorPor(evento.getDamager());
        if (atacante == null) return;

        double dano = Math.min(evento.getFinalDamage(), boss.getHealth());
        if (dano <= 0) return;
        double total = sobraDanoBoss.getOrDefault(atacante.getUniqueId(), 0.0) + dano;
        int inteiro = (int) total;
        sobraDanoBoss.put(atacante.getUniqueId(), total - inteiro);
        plugin.getMissaoManager().registrar(atacante, TipoMissao.DANO_BOSS, boss.getType().name(), inteiro);
    }

    private Player jogadorPor(Entity causador) {
        if (causador instanceof Player jogador) return jogador;
        if (causador instanceof Projectile projetil && projetil.getShooter() instanceof Player atirador) return atirador;
        return null;
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        plugin.getMissaoManager().esquecerAviso(evento.getPlayer().getUniqueId());
        sobraDanoBoss.remove(evento.getPlayer().getUniqueId());
    }
}
