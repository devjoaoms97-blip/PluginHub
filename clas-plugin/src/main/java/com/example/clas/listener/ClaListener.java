package com.example.clas.listener;

import com.example.clas.ClasPlugin;
import com.example.clas.model.Cla;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Tag do clã no chat e na tab, fogo amigo e placar de abates.
 *
 * A tag entra como PREFIXO do formato que já existir: o VipPlugin monta o formato em LOW
 * (com a tag do VIP), e aqui, em NORMAL, a tag do clã vai na frente — os dois convivem.
 *
 * Fogo amigo desligado cobre golpe direto, projétil (inclusive poção de dano), efeito ruim de
 * poção arremessada ou persistente, TNT acesa pelo colega e cristal do End que ele quebrou.
 */
public class ClaListener implements Listener {

    private final ClasPlugin plugin;
    // Quem bateu no cristal do End por último, pra explosão dele contar como ataque dessa pessoa
    private final NamespacedKey chaveQuemQuebrou;

    public ClaListener(ClasPlugin plugin) {
        this.plugin = plugin;
        this.chaveQuemQuebrou = new NamespacedKey(plugin, "cristal_quebrado_por");
    }

    /** MONITOR: depois do VipPlugin (que monta o nome na tab em NORMAL), pra tag do clã entrar junto. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        plugin.atualizarTab(evento.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        Cla cla = plugin.getClaManager().getClaDe(evento.getPlayer().getUniqueId());
        if (cla == null) return;
        evento.setFormat(plugin.tagFormatada(cla) + " " + evento.getFormat());
    }

    /** Sem fogo amigo: membro do mesmo clã não machuca o outro. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAtacar(EntityDamageByEntityEvent evento) {
        if (evento.getEntity() instanceof EnderCrystal cristal) {
            Player quem = jogadorPor(evento.getDamager());
            if (quem != null) {
                cristal.getPersistentDataContainer().set(chaveQuemQuebrou, PersistentDataType.STRING, quem.getUniqueId().toString());
            }
            return;
        }
        if (!(evento.getEntity() instanceof Player vitima)) return;
        if (protegido(jogadorPor(evento.getDamager()), vitima)) {
            evento.setCancelled(true);
        }
    }

    /** Poção arremessada com efeito ruim (veneno, lentidão...) não pega no colega. As boas continuam pegando. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoEspirrarPocao(PotionSplashEvent evento) {
        ThrownPotion pocao = evento.getPotion();
        if (!(pocao.getShooter() instanceof Player atirador) || !temEfeitoRuim(pocao.getEffects())) return;
        for (LivingEntity atingido : evento.getAffectedEntities()) {
            if (atingido instanceof Player vitima && protegido(atirador, vitima)) {
                evento.setIntensity(atingido, 0);
            }
        }
    }

    /** Nuvem de poção persistente (lingering) com efeito ruim também não pega no colega. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAplicarNuvem(AreaEffectCloudApplyEvent evento) {
        AreaEffectCloud nuvem = evento.getEntity();
        if (!(nuvem.getSource() instanceof Player atirador)) return;
        List<PotionEffect> efeitos = new ArrayList<>(nuvem.getCustomEffects());
        if (nuvem.getBasePotionType() != null) efeitos.addAll(nuvem.getBasePotionType().getPotionEffects());
        if (!temEfeitoRuim(efeitos)) return;
        evento.getAffectedEntities().removeIf(atingido -> atingido instanceof Player vitima && protegido(atirador, vitima));
    }

    private boolean temEfeitoRuim(Collection<PotionEffect> efeitos) {
        for (PotionEffect efeito : efeitos) {
            if (efeito.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) return true;
        }
        return false;
    }

    /** O atacante é colega de clã da vítima, e o clã está com fogo amigo desligado? */
    private boolean protegido(Player atacante, Player vitima) {
        if (atacante == null || atacante.equals(vitima)) return false;
        Cla cla = plugin.getClaManager().getClaDe(atacante.getUniqueId());
        if (cla == null || cla.isFogoAmigo()) return false;
        return plugin.getClaManager().mesmoCla(atacante.getUniqueId(), vitima.getUniqueId());
    }

    /** Jogador por trás do dano: golpe direto, projétil, TNT que ele acendeu ou cristal em que ele bateu. */
    private Player jogadorPor(Entity causador) {
        if (causador instanceof Player jogador) return jogador;
        if (causador instanceof Projectile projetil && projetil.getShooter() instanceof Player atirador) {
            return atirador;
        }
        if (causador instanceof TNTPrimed tnt && tnt.getSource() != null && tnt.getSource() != causador) {
            return jogadorPor(tnt.getSource()); // acesa direto, ou por flecha de fogo
        }
        if (causador instanceof EnderCrystal cristal) {
            String id = cristal.getPersistentDataContainer().get(chaveQuemQuebrou, PersistentDataType.STRING);
            return id == null ? null : plugin.getServer().getPlayer(UUID.fromString(id));
        }
        return null;
    }

    /** Abate conta pro clã do matador (e morte pro clã da vítima) — nunca entre colegas nem entre contas do mesmo IP. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoMorrer(PlayerDeathEvent evento) {
        Player vitima = evento.getEntity();
        Player matador = vitima.getKiller();
        if (matador == null || matador.equals(vitima)) return;
        if (plugin.getClaManager().mesmoCla(matador.getUniqueId(), vitima.getUniqueId())) return;
        if (mesmoIp(matador, vitima)) return;

        boolean mudou = false;
        Cla claMatador = plugin.getClaManager().getClaDe(matador.getUniqueId());
        if (claMatador != null) {
            claMatador.setAbates(claMatador.getAbates() + 1);
            mudou = true;
        }
        Cla claVitima = plugin.getClaManager().getClaDe(vitima.getUniqueId());
        if (claVitima != null) {
            claVitima.setMortes(claVitima.getMortes() + 1);
            mudou = true;
        }
        if (mudou) plugin.getClaManager().salvar();
    }

    private boolean mesmoIp(Player a, Player b) {
        InetSocketAddress ipA = a.getAddress();
        InetSocketAddress ipB = b.getAddress();
        return ipA != null && ipB != null && ipA.getAddress() != null && ipA.getAddress().equals(ipB.getAddress());
    }
}
