package com.example.barracas.manager;

import com.example.barracas.BarracasPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Caixa de retirada: quando o aluguel acaba, o que estava nos baús da barraca vem pra cá, e o
 * dono pega com {@code /barraca retirar}. Cada lote fica guardado por {@code retirada.dias-guardado}
 * (padrão 7) — depois disso é descartado, pra não acumular itens de quem largou o servidor.
 */
public class RetiradaManager {

    private record Entrada(ItemStack item, long expiraEm) {
    }

    private final BarracasPlugin plugin;
    private final File arquivo;
    private final Map<UUID, List<Entrada>> caixas = new HashMap<>();

    public RetiradaManager(BarracasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "retiradas.yml");
        carregar();
    }

    public void adicionar(UUID dono, List<ItemStack> itens) {
        long expiraEm = System.currentTimeMillis() + diasGuardado() * 86_400_000L;
        List<Entrada> caixa = caixas.computeIfAbsent(dono, k -> new ArrayList<>());
        for (ItemStack item : itens) {
            caixa.add(new Entrada(item.clone(), expiraEm));
        }
        salvar();
    }

    /** Quantas pilhas ainda válidas esse jogador tem pra retirar. */
    public int contar(UUID dono) {
        descartarVencidos();
        List<Entrada> caixa = caixas.get(dono);
        return caixa == null ? 0 : caixa.size();
    }

    /**
     * Entrega o que couber no inventário. O que não couber continua na caixa.
     * Retorna {entregues, restantes} em número de pilhas.
     */
    public int[] retirar(Player jogador) {
        descartarVencidos();
        List<Entrada> caixa = caixas.get(jogador.getUniqueId());
        if (caixa == null || caixa.isEmpty()) {
            return new int[]{0, 0};
        }

        int entregues = 0;
        Iterator<Entrada> it = caixa.iterator();
        List<Entrada> sobras = new ArrayList<>();
        while (it.hasNext()) {
            Entrada entrada = it.next();
            Map<Integer, ItemStack> naoCouberam = jogador.getInventory().addItem(entrada.item().clone());
            it.remove();
            if (naoCouberam.isEmpty()) {
                entregues++;
            } else {
                naoCouberam.values().forEach(sobra -> sobras.add(new Entrada(sobra, entrada.expiraEm())));
                break; // inventário cheio: para aqui e guarda o resto
            }
        }
        caixa.addAll(0, sobras);
        if (caixa.isEmpty()) {
            caixas.remove(jogador.getUniqueId());
        }
        salvar();
        return new int[]{entregues, caixa.size()};
    }

    public int diasGuardado() {
        return Math.max(1, plugin.getConfig().getInt("retirada.dias-guardado", 7));
    }

    private void descartarVencidos() {
        long agora = System.currentTimeMillis();
        boolean mudou = false;
        for (Iterator<List<Entrada>> it = caixas.values().iterator(); it.hasNext(); ) {
            List<Entrada> caixa = it.next();
            mudou |= caixa.removeIf(e -> agora >= e.expiraEm());
            if (caixa.isEmpty()) {
                it.remove();
            }
        }
        if (mudou) salvar();
    }

    // ---------------------------------------------------------------------
    // Persistência (retiradas.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("retiradas");
        if (secao == null) return;

        for (String chave : secao.getKeys(false)) {
            ConfigurationSection doJogador = secao.getConfigurationSection(chave);
            if (doJogador == null) continue;
            try {
                List<Entrada> caixa = new ArrayList<>();
                for (String indice : doJogador.getKeys(false)) {
                    ItemStack item = doJogador.getItemStack(indice + ".item");
                    if (item == null) continue;
                    caixa.add(new Entrada(item, doJogador.getLong(indice + ".expira-em")));
                }
                if (!caixa.isEmpty()) {
                    caixas.put(UUID.fromString(chave), caixa);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido ignorado em retiradas.yml: " + chave);
            }
        }
        descartarVencidos();
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Entrada>> entrada : caixas.entrySet()) {
            int i = 0;
            for (Entrada e : entrada.getValue()) {
                String base = "retiradas." + entrada.getKey() + "." + i++;
                yaml.set(base + ".item", e.item());
                yaml.set(base + ".expira-em", e.expiraEm());
            }
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar retiradas.yml", e);
        }
    }
}
