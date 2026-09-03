package com.example.vip.manager;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipAtivo;
import com.example.vip.model.VipTier;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dono da lista de VIPs ativos: quem tem, qual tier, e quando expira. Persiste em
 * {@code vips.yml} (separado do config.yml, que é só configuração estática dos tiers).
 *
 * Sincroniza com o LuckPerms adicionando/removendo o jogador do grupo correspondente ao
 * tier (o grupo em si, e o que ele concede de permissão, é configurado direto no LuckPerms
 * pelo admin — este plugin só gerencia a *matrícula* no grupo).
 */
public class VipManager {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VipPlugin plugin;
    private final File arquivo;
    private final Map<UUID, VipAtivo> ativos = new HashMap<>();
    private LuckPerms luckPerms;

    public VipManager(VipPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "vips.yml");
        conectarLuckPerms();
        carregar();
    }

    private void conectarLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().warning("LuckPerms não encontrado — o VipPlugin vai controlar prazos normalmente, "
                    + "mas não vai conseguir aplicar os grupos de permissão até o LuckPerms ser instalado.");
            return;
        }
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("LuckPerms ainda não terminou de carregar — tente /vip reload em alguns segundos.");
        }
    }

    // ---------------------------------------------------------------------
    // Adicionar / renovar / remover
    // ---------------------------------------------------------------------

    /**
     * Concede (ou renova/troca) um VIP pra um jogador, por {@code dias} a partir de agora.
     * Se o jogador já tinha um tier diferente, o grupo antigo é removido do LuckPerms antes
     * de aplicar o novo. Retorna a data de expiração já formatada, pra mensagens de chat.
     */
    public String adicionar(UUID jogadorId, VipTier tier, int dias) {
        if (luckPerms == null) {
            conectarLuckPerms();
        }

        VipAtivo atual = ativos.get(jogadorId);
        if (atual != null && atual.tier() != tier && luckPerms != null) {
            removerGrupo(jogadorId, atual.tier());
        }

        long expiraEm = System.currentTimeMillis() + (dias * 24L * 60 * 60 * 1000);
        ativos.put(jogadorId, new VipAtivo(jogadorId, tier, expiraEm));

        if (luckPerms != null) {
            adicionarGrupo(jogadorId, tier);
        }

        salvar();
        return formatarData(expiraEm);
    }

    /** Remove o VIP de um jogador na hora (independente de quando expiraria). */
    public boolean remover(UUID jogadorId) {
        VipAtivo atual = ativos.remove(jogadorId);
        if (atual == null) {
            return false;
        }
        if (luckPerms != null) {
            removerGrupo(jogadorId, atual.tier());
        }
        salvar();
        return true;
    }

    /** Retorna o VIP ativo do jogador, ou {@code null} se não tiver nenhum (ou já expirou). */
    public VipAtivo getVipAtivo(UUID jogadorId) {
        VipAtivo atual = ativos.get(jogadorId);
        if (atual == null || atual.expirado()) {
            return null;
        }
        return atual;
    }

    public VipTier getTierAtivo(UUID jogadorId) {
        VipAtivo atual = getVipAtivo(jogadorId);
        return atual == null ? null : atual.tier();
    }

    public Map<UUID, VipAtivo> getTodosAtivos() {
        return ativos;
    }

    // ---------------------------------------------------------------------
    // Varredura de expiração
    // ---------------------------------------------------------------------

    /**
     * Roda periodicamente (ver {@code verificacao-expiracao-minutos} no config.yml): remove
     * do LuckPerms e da lista quem já passou da data, avisando no chat se estiver online.
     */
    public void verificarExpirados() {
        if (luckPerms == null) {
            conectarLuckPerms();
        }

        boolean mudou = false;
        Iterator<Map.Entry<UUID, VipAtivo>> iterador = ativos.entrySet().iterator();
        while (iterador.hasNext()) {
            VipAtivo vip = iterador.next().getValue();
            if (!vip.expirado()) {
                continue;
            }

            if (luckPerms != null) {
                removerGrupo(vip.jogadorId(), vip.tier());
            }

            Player online = Bukkit.getPlayer(vip.jogadorId());
            if (online != null) {
                String msg = plugin.getConfig()
                        .getString("mensagens.vip-expirado", "&cSeu VIP &f{tier}&c expirou.")
                        .replace("{tier}", plugin.getNomeExibicao(vip.tier()));
                online.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                plugin.refrescarTagJogador(online);
            }

            iterador.remove();
            mudou = true;
        }

        if (mudou) {
            salvar();
        }
    }

    // ---------------------------------------------------------------------
    // LuckPerms
    // ---------------------------------------------------------------------

    private void adicionarGrupo(UUID jogadorId, VipTier tier) {
        String grupo = plugin.getGrupoLuckPerms(tier);
        luckPerms.getUserManager().modifyUser(jogadorId, user ->
                user.data().add(InheritanceNode.builder(grupo).build()));
    }

    private void removerGrupo(UUID jogadorId, VipTier tier) {
        String grupo = plugin.getGrupoLuckPerms(tier);
        luckPerms.getUserManager().modifyUser(jogadorId, user ->
                user.data().remove(InheritanceNode.builder(grupo).build()));
    }

    // ---------------------------------------------------------------------
    // Persistência (vips.yml)
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        var secao = yaml.getConfigurationSection("vips");
        if (secao == null) {
            return;
        }

        for (String chaveUuid : secao.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(chaveUuid);
                VipTier tier = VipTier.fromString(secao.getString(chaveUuid + ".tier"));
                long expiraEm = secao.getLong(chaveUuid + ".expira-em");
                if (tier == null) {
                    plugin.getLogger().warning("Tier inválido em vips.yml pro jogador " + chaveUuid + ", ignorando.");
                    continue;
                }
                ativos.put(uuid, new VipAtivo(uuid, tier, expiraEm));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em vips.yml: " + chaveUuid);
            }
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, VipAtivo> entrada : ativos.entrySet()) {
            String base = "vips." + entrada.getKey();
            yaml.set(base + ".tier", entrada.getValue().tier().name());
            yaml.set(base + ".expira-em", entrada.getValue().expiraEm());
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar vips.yml", e);
        }
    }

    private String formatarData(long epochMillis) {
        return FORMATO_DATA.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
