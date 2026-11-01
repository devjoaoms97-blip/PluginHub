package com.example.recursos;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quem matou o Dragão do End ganha a tag "[Matador de Dragão]" no chat, até o próximo dragão morrer,
 * e um Mapa do Tesouro direto no inventário (criado pelo TesourosPlugin, {@code drops.dragao}, chamado
 * por reflexão). Sem ninguém com o crédito, o mapa cai do lado do portal.
 * <p>
 * Quem matou é quem deu o último golpe. Se a morte foi indireta (a explosão de um cristal, uma
 * flecha de quem já saiu...), conta o último jogador que acertou o dragão nos
 * {@code end.dragao.matador.janela-segundos} (30) anteriores. Golpe de projétil conta pro atirador, e
 * a explosão de um cristal do End conta pra quem quebrou o cristal.
 */
public class MatadorDoDragao implements Listener {

    private record Golpe(UUID jogador, String nome, long quando) {}

    private final RecursosPlugin plugin;
    private final File arquivo;
    /** Dragão → o último jogador que acertou ele. */
    private final Map<UUID, Golpe> ultimos = new HashMap<>();
    /** Cristal do End → quem quebrou (a explosão dele no dragão conta pra essa pessoa). */
    private final Map<UUID, Golpe> cristais = new HashMap<>();
    private UUID matador;
    private String nomeMatador;

    MatadorDoDragao(RecursosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "dragao.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        String id = yaml.getString("matador.uuid");
        if (id != null) {
            try {
                matador = UUID.fromString(id);
                nomeMatador = yaml.getString("matador.nome");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em dragao.yml: " + id);
            }
        }
    }

    private boolean ativado() {
        return plugin.getConfig().getBoolean("end.dragao.matador.ativado", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoAcertar(EntityDamageByEntityEvent evento) {
        Entity alvo = evento.getEntity() instanceof ComplexEntityPart parte ? parte.getParent() : evento.getEntity();
        Player quem = jogadorPor(evento.getDamager());
        if (alvo instanceof EnderCrystal cristal) {
            if (quem != null) cristais.put(cristal.getUniqueId(), new Golpe(quem.getUniqueId(), quem.getName(), System.currentTimeMillis()));
            return;
        }
        if (!(alvo instanceof EnderDragon dragao)) return;
        Golpe golpe = quem != null ? new Golpe(quem.getUniqueId(), quem.getName(), System.currentTimeMillis())
                : evento.getDamager() instanceof EnderCrystal c ? cristais.remove(c.getUniqueId()) : null;
        if (golpe != null) ultimos.put(dragao.getUniqueId(), new Golpe(golpe.jogador(), golpe.nome(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(EntityDeathEvent evento) {
        if (!(evento.getEntity() instanceof EnderDragon dragao)) return;
        Golpe ultimo = ultimos.remove(dragao.getUniqueId());
        cristais.clear();
        // o responsável vale pro mapa (TesourosPlugin, drops.dragao) mesmo com a tag desligada
        String quemNome;
        UUID quem;
        Player killer = dragao.getKiller();
        long janela = plugin.getConfig().getLong("end.dragao.matador.janela-segundos", 30) * 1000;
        if (killer != null) {
            quem = killer.getUniqueId();
            quemNome = killer.getName();
        } else if (ultimo != null && System.currentTimeMillis() - ultimo.quando() <= janela) {
            quem = ultimo.jogador();
            quemNome = ultimo.nome();
        } else {
            Bukkit.broadcastMessage(cor("&5✦ &dO Dragão do End morreu, mas ninguém levou o crédito."));
            mapaDoTesouro(null, dragao.getWorld());
            return;
        }
        mapaDoTesouro(Bukkit.getPlayer(quem), dragao.getWorld());
        if (!ativado()) return;
        matador = quem;
        nomeMatador = quemNome;
        salvar();
        Bukkit.broadcastMessage(cor(plugin.getConfig().getString("end.dragao.matador.mensagem",
                "&5&l🐉 &f%jogador% &dmatou o Dragão do End e é o novo &5[Matador de Dragão]&d!").replace("%jogador%", quemNome)));
    }

    /** A tag vai no chat de quem matou o último dragão (os prefixos dos canais ficam na frente: ChatPlugin). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        if (!ativado() || matador == null || !matador.equals(evento.getPlayer().getUniqueId())) return;
        String tag = cor(plugin.getConfig().getString("end.dragao.matador.tag", "&5[Matador de Dragão]")) + " ";
        evento.setFormat(tag.replace("%", "%%") + evento.getFormat());
    }

    /** Pede o mapa ao TesourosPlugin (sem ele, não tem mapa). */
    private void mapaDoTesouro(Player jogador, org.bukkit.World mundo) {
        org.bukkit.plugin.Plugin tesouros = Bukkit.getPluginManager().getPlugin("TesourosPlugin");
        if (tesouros == null || !tesouros.isEnabled()) return;
        try {
            tesouros.getClass().getMethod("mapaDoDragao", Player.class, org.bukkit.World.class).invoke(tesouros, jogador, mundo);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Não deu pra entregar o mapa do dragão (TesourosPlugin antigo?): " + e.getMessage());
        }
    }

    private static Player jogadorPor(Entity dano) {
        if (dano instanceof Player p) return p;
        if (dano instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (dano instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("matador.uuid", matador.toString());
        yaml.set("matador.nome", nomeMatador);
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar dragao.yml: " + e.getMessage());
        }
    }

    private static String cor(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto);
    }
}
