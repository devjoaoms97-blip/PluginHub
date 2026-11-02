package com.example.skills.profissao;

import com.example.skills.SkillsPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * As skills de profissão conversam com o RankupPlugin (minas, quebra do 3x3 e Fragmento Arcano) e com
 * o TesourosPlugin (mapas), por reflexão, como as outras integrações: sem o plugin, aquela parte só
 * não acontece.
 */
public class Integracoes {

    private final SkillsPlugin plugin;
    private boolean avisou;

    public Integracoes(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    private Plugin ativo(String nome) {
        Plugin p = plugin.getServer().getPluginManager().getPlugin(nome);
        return p != null && p.isEnabled() ? p : null;
    }

    private Object chamar(String nomePlugin, String metodo, Class<?>[] tipos, Object... args) {
        Plugin alvo = ativo(nomePlugin);
        if (alvo == null) return null;
        try {
            return alvo.getClass().getMethod(metodo, tipos).invoke(alvo, args);
        } catch (ReflectiveOperationException e) {
            if (!avisou) {
                plugin.getLogger().warning(nomePlugin + " encontrado, mas sem " + metodo + " (versão antiga?): " + e.getMessage());
                avisou = true;
            }
            return null;
        }
    }

    /** O id da mina nesse lugar, ou null (fora das minas, ou sem o RankupPlugin). */
    public String minaEm(Location local) {
        return chamar("RankupPlugin", "minaEm", new Class<?>[]{Location.class}, local) instanceof String id ? id : null;
    }

    /** Quebra um bloco extra do 3x3: só a venda automática da mina, sem evento de quebra. */
    public boolean quebrarExtra(Player jogador, Block bloco) {
        return Boolean.TRUE.equals(chamar("RankupPlugin", "quebrarExtra", new Class<?>[]{Player.class, Block.class}, jogador, bloco));
    }

    public ItemStack fragmento() {
        return chamar("RankupPlugin", "fragmento", new Class<?>[]{int.class}, 1) instanceof ItemStack item ? item : null;
    }

    /** Um mapa do tesouro dessa raridade (comum, raro, arcano ou lendario), ou null sem o TesourosPlugin. */
    public ItemStack mapa(String raridade) {
        Object mapas = chamar("TesourosPlugin", "getMapas", new Class<?>[0]);
        if (mapas == null) return null;
        try {
            return mapas.getClass().getMethod("criar", String.class).invoke(mapas, raridade) instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("TesourosPlugin sem Mapas#criar (versão antiga?): " + e.getMessage());
            return null;
        }
    }

    public String nomeRaridade(String raridade) {
        return chamar("TesourosPlugin", "nomeRaridade", new Class<?>[]{String.class}, raridade) instanceof String nome ? nome : raridade;
    }
}
