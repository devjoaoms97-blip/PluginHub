package com.example.boss.manager;

import com.example.boss.BossPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Atribui a tag de campeão (um grupo do LuckPerms com prefixo, configurado pelo admin
 * direto no LuckPerms) a quem der o último hit no boss. Só existe UM campeão por vez —
 * ao coroar alguém novo, o grupo é removido de quem tinha antes.
 */
public class ChampionManager {

    private final BossPlugin plugin;
    private LuckPerms luckPerms;
    private UUID campeaoAtual;

    public ChampionManager(BossPlugin plugin) {
        this.plugin = plugin;
        conectarLuckPerms();
        carregarCampeaoAtual();
    }

    private void conectarLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().warning("LuckPerms não encontrado — a tag de campeão não vai funcionar até ele ser instalado.");
            return;
        }
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("LuckPerms ainda não terminou de carregar — tente recarregar o BossPlugin depois.");
        }
    }

    private void carregarCampeaoAtual() {
        String uuidSalvo = plugin.getConfig().getString("campeao-atual-uuid", null);
        if (uuidSalvo != null) {
            try {
                campeaoAtual = UUID.fromString(uuidSalvo);
            } catch (IllegalArgumentException ignored) {
                // valor corrompido no config, ignora e segue sem campeão salvo
            }
        }
    }

    private void salvarCampeaoAtual() {
        plugin.getConfig().set("campeao-atual-uuid", campeaoAtual == null ? null : campeaoAtual.toString());
        plugin.saveConfig();
    }

    public void coroarNovoCampeao(Player novoCampeao) {
        if (luckPerms == null) {
            conectarLuckPerms();
            if (luckPerms == null) return;
        }

        String grupo = plugin.getConfig().getString("campeao.grupo-luckperms", "boss_campeao");

        if (campeaoAtual != null && !campeaoAtual.equals(novoCampeao.getUniqueId())) {
            removerGrupo(campeaoAtual, grupo);
        }

        adicionarGrupo(novoCampeao.getUniqueId(), grupo);

        campeaoAtual = novoCampeao.getUniqueId();
        salvarCampeaoAtual();
    }

    private void adicionarGrupo(UUID jogadorId, String grupo) {
        luckPerms.getUserManager().modifyUser(jogadorId, user ->
                user.data().add(InheritanceNode.builder(grupo).build()));
    }

    private void removerGrupo(UUID jogadorId, String grupo) {
        luckPerms.getUserManager().modifyUser(jogadorId, user ->
                user.data().remove(InheritanceNode.builder(grupo).build()));
    }

    public UUID getCampeaoAtual() {
        return campeaoAtual;
    }
}
