package com.example.vip.command;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipTier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica dos perks de VIP (fly, heal, feed, kit, particula, warp). Não é mais um
 * {@code CommandExecutor} — desde que tudo virou subcomando de {@code /vip}, é só um
 * helper chamado por {@link VipCommand#onCommand}, que já garantiu que quem chamou é um
 * {@link Player}. Continua checando aqui, por dentro, se esse player tem VIP ativo e a
 * permissão do perk específico.
 */
public class PerkCommand {

    private final VipPlugin plugin;
    private final Map<UUID, Long> cooldownHeal = new HashMap<>();
    private final Map<UUID, Long> cooldownFeed = new HashMap<>();
    private final Map<UUID, Long> cooldownKit = new HashMap<>();

    public PerkCommand(VipPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ponto de entrada único, chamado pelo VipCommand pra "fly", "heal", "feed", "kit", "particula" e "warp". */
    public void executar(Player player, String subcomando, String[] args) {
        VipTier tier = plugin.getVipManager().getTierAtivo(player.getUniqueId());
        if (tier == null) {
            player.sendMessage("§cVocê precisa ter um VIP ativo pra usar esse comando. Veja os tiers com §f/vip tiers§c.");
            return;
        }

        switch (subcomando) {
            case "fly" -> tratarFly(player);
            case "heal" -> tratarHeal(player, tier);
            case "feed" -> tratarFeed(player, tier);
            case "kit" -> tratarKit(player, tier);
            case "particula" -> tratarParticula(player, tier, args);
            case "warp" -> tratarWarp(player);
            default -> player.sendMessage("§cPerk não reconhecido.");
        }
    }

    /** Usado pelo tab-complete de {@code /vip particula <tab>}. */
    public List<String> tabCompleteParticula(Player player) {
        VipTier tier = plugin.getVipManager().getTierAtivo(player.getUniqueId());
        if (tier == null) {
            return List.of();
        }
        List<String> opcoes = new ArrayList<>(plugin.getParticulasDisponiveis(tier));
        opcoes.add("off");
        return opcoes;
    }

    // ---------------------------------------------------------------------
    // Perks
    // ---------------------------------------------------------------------

    private void tratarFly(Player player) {
        if (!perkPermitido(player, "fly", "vip.perk.fly")) {
            return;
        }
        boolean novoEstado = !player.getAllowFlight();
        player.setAllowFlight(novoEstado);
        player.setFlying(novoEstado);
        player.sendMessage(novoEstado ? "§aVoo ativado." : "§7Voo desativado.");
    }

    private void tratarHeal(Player player, VipTier tier) {
        if (!perkPermitido(player, "heal", "vip.perk.heal")) {
            return;
        }
        if (!passouCooldown(cooldownHeal, player, plugin.getCooldownHealMinutos(tier))) {
            return;
        }

        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFireTicks(0);
        player.sendMessage("§aVocê foi curado.");
    }

    private void tratarFeed(Player player, VipTier tier) {
        if (!perkPermitido(player, "feed", "vip.perk.feed")) {
            return;
        }
        if (!passouCooldown(cooldownFeed, player, plugin.getCooldownFeedMinutos(tier))) {
            return;
        }

        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.sendMessage("§aFome restaurada.");
    }

    private void tratarKit(Player player, VipTier tier) {
        if (!perkPermitido(player, "kit", "vip.perk.kit")) {
            return;
        }
        if (!passouCooldown(cooldownKit, player, plugin.getCooldownKitHoras(tier) * 60)) {
            return;
        }

        List<String> itens = plugin.getKit(tier);
        if (itens.isEmpty()) {
            player.sendMessage("§7Nenhum kit configurado pro seu tier ainda.");
            return;
        }

        int entregues = 0;
        for (String linha : itens) {
            String[] partes = linha.split(":");
            try {
                Material material = Material.valueOf(partes[0].trim().toUpperCase(Locale.ROOT));
                int quantidade = partes.length > 1 ? Integer.parseInt(partes[1].trim()) : 1;
                ItemStack item = new ItemStack(material, quantidade);
                Map<Integer, ItemStack> sobras = player.getInventory().addItem(item);
                sobras.values().forEach(sobra -> player.getWorld().dropItemNaturally(player.getLocation(), sobra));
                entregues++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Item inválido no kit de " + tier + ": \"" + linha + "\"");
            }
        }
        player.sendMessage("§aKit de VIP " + plugin.getNomeExibicao(tier) + " §aentregue! §7(" + entregues + " itens)");
    }

    private void tratarParticula(Player player, VipTier tier, String[] args) {
        if (!perkPermitido(player, "particulas", "vip.perk.particulas")) {
            return;
        }

        List<String> disponiveis = plugin.getParticulasDisponiveis(tier);

        if (args.length == 0) {
            player.sendMessage("§7Partículas disponíveis pro seu tier: §f" + String.join(", ", disponiveis) + "§7, ou §foff");
            return;
        }

        String escolha = args[0].toUpperCase(Locale.ROOT);
        if (escolha.equals("OFF")) {
            plugin.getParticleTrailListener().limpar(player.getUniqueId());
            player.sendMessage("§7Efeito de partícula desativado.");
            return;
        }

        if (!disponiveis.contains(escolha)) {
            player.sendMessage("§cSeu tier não tem essa partícula. Use §f/vip particula§c sem argumento pra ver as disponíveis.");
            return;
        }

        try {
            Particle particula = Particle.valueOf(escolha);
            plugin.getParticleTrailListener().definir(player.getUniqueId(), particula);
            player.sendMessage("§aEfeito de partícula definido: §f" + escolha);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cPartícula \"" + escolha + "\" inválida (configurada errado no config.yml).");
        }
    }

    private void tratarWarp(Player player) {
        if (!perkPermitido(player, "warp", "vip.perk.warp")) {
            return;
        }

        String mundoNome = plugin.getConfig().getString("warp-vip.mundo", "world");
        World mundo = plugin.getServer().getWorld(mundoNome);
        if (mundo == null) {
            player.sendMessage("§cO mundo do warp de VIP não está configurado corretamente.");
            return;
        }
        double x = plugin.getConfig().getDouble("warp-vip.x");
        double y = plugin.getConfig().getDouble("warp-vip.y");
        double z = plugin.getConfig().getDouble("warp-vip.z");
        player.teleport(new Location(mundo, x, y, z));
        player.sendMessage("§aTeleportado pra área exclusiva de VIP.");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private boolean perkPermitido(Player player, String perkConfigKey, String permissao) {
        if (!plugin.perkAtivo(perkConfigKey)) {
            player.sendMessage("§cEsse perk está desativado no servidor no momento.");
            return false;
        }
        if (!player.hasPermission(permissao)) {
            player.sendMessage("§cSeu tier de VIP não inclui esse perk.");
            return false;
        }
        return true;
    }

    /** {@code minutos <= 0} significa "sem cooldown". Já avisa o jogador quando ainda está em espera. */
    private boolean passouCooldown(Map<UUID, Long> mapa, Player player, int minutos) {
        if (minutos <= 0) {
            return true;
        }
        long agora = System.currentTimeMillis();
        Long ultimoUso = mapa.get(player.getUniqueId());
        long minimoMs = minutos * 60L * 1000;

        if (ultimoUso != null && (agora - ultimoUso) < minimoMs) {
            long restanteSeg = (minimoMs - (agora - ultimoUso)) / 1000;
            player.sendMessage("§cAinda em cooldown. Tente de novo em §f" + restanteSeg + "s§c.");
            return false;
        }

        mapa.put(player.getUniqueId(), agora);
        return true;
    }
}
