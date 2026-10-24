package com.example.vip.command;

import com.example.vip.VipPlugin;
import com.example.vip.manager.CooldownManager;
import com.example.vip.manager.CooldownManager.Perk;
import com.example.vip.model.VipTier;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lógica dos perks de VIP (fly, heal, feed, kit, kitmensal, particula, warp). Não é mais um
 * {@code CommandExecutor} — desde que tudo virou subcomando de {@code /vip}, é só um
 * helper chamado por {@link VipCommand#onCommand}, que já garantiu que quem chamou é um
 * {@link Player}. Continua checando aqui, por dentro, se esse player tem VIP ativo e a
 * permissão do perk específico.
 */
public class PerkCommand {

    private final VipPlugin plugin;

    public PerkCommand(VipPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ponto de entrada único, chamado pelo VipCommand pra "fly", "heal", "feed", "kit", "kitmensal", "particula" e "warp". */
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
            case "kitmensal" -> tratarKitMensal(player, tier);
            case "particula" -> tratarParticula(player, tier, args);
            case "warp" -> tratarWarp(player, tier);
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
        if (!passouCooldown(Perk.HEAL, player, plugin.getCooldownHealMinutos(tier))) {
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
        if (!passouCooldown(Perk.FEED, player, plugin.getCooldownFeedMinutos(tier))) {
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
        List<String> itens = plugin.getKit(tier);
        if (itens.isEmpty()) {
            player.sendMessage("§7Nenhum kit configurado pro seu tier ainda.");
            return;
        }
        if (!passouCooldown(Perk.KIT, player, plugin.getCooldownKitHoras(tier) * 60)) {
            return;
        }
        int entregues = entregarItens(player, tier, itens);
        player.sendMessage("§aKit de VIP " + plugin.getNomeExibicao(tier) + " §aentregue! §7(" + entregues + " itens)");
    }

    /** Kit com ferramentas encantadas, uma vez a cada {@code cooldown-kit-mensal-dias}. Usa a mesma permissão do kit. */
    private void tratarKitMensal(Player player, VipTier tier) {
        if (!perkPermitido(player, "kit", "vip.perk.kit")) {
            return;
        }
        List<String> itens = plugin.getKitMensal(tier);
        if (itens.isEmpty()) {
            player.sendMessage("§7Seu tier de VIP não tem kit mensal.");
            return;
        }
        if (!passouCooldown(Perk.KIT_MENSAL, player, plugin.getCooldownKitMensalDias(tier) * 24 * 60)) {
            return;
        }
        int entregues = entregarItens(player, tier, itens);
        player.sendMessage("§aKit mensal de VIP " + plugin.getNomeExibicao(tier) + " §aentregue! §7(" + entregues + " itens)");
    }

    /**
     * Cada linha é {@code MATERIAL[:quantidade[:encantamento=nivel,...]]}, por exemplo
     * {@code NETHERITE_PICKAXE:1:fortune=5,efficiency=5}. Os encantamentos usam o nome do Minecraft
     * e podem passar do nível máximo normal (Fortuna V). O que não cabe no inventário cai no chão.
     */
    private int entregarItens(Player player, VipTier tier, List<String> itens) {
        int entregues = 0;
        for (String linha : itens) {
            String[] partes = linha.split(":");
            try {
                Material material = Material.valueOf(partes[0].trim().toUpperCase(Locale.ROOT));
                int quantidade = partes.length > 1 ? Integer.parseInt(partes[1].trim()) : 1;
                ItemStack item = new ItemStack(material, quantidade);
                if (partes.length > 2) {
                    encantar(item, partes[2]);
                }
                Map<Integer, ItemStack> sobras = player.getInventory().addItem(item);
                sobras.values().forEach(sobra -> player.getWorld().dropItemNaturally(player.getLocation(), sobra));
                entregues++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Item inválido no kit de " + tier + ": \"" + linha + "\" (" + e.getMessage() + ")");
            }
        }
        return entregues;
    }

    private void encantar(ItemStack item, String encantamentos) {
        var registro = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (String par : encantamentos.split(",")) {
            String[] nomeNivel = par.split("=");
            String nome = nomeNivel[0].trim().toLowerCase(Locale.ROOT);
            Enchantment encantamento = registro.get(NamespacedKey.minecraft(nome));
            if (encantamento == null) {
                throw new IllegalArgumentException("encantamento desconhecido: " + nome);
            }
            int nivel = nomeNivel.length > 1 ? Integer.parseInt(nomeNivel[1].trim()) : 1;
            item.addUnsafeEnchantment(encantamento, nivel);
        }
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

    /** Vai pra área do tier ({@code warp-vip.tiers.<tier>}), ou pra área VIP geral se o tier não tiver uma. */
    private void tratarWarp(Player player, VipTier tier) {
        if (!perkPermitido(player, "warp", "vip.perk.warp")) {
            return;
        }

        String doTier = "warp-vip.tiers." + tier.name().toLowerCase(Locale.ROOT);
        String base = plugin.getConfig().isConfigurationSection(doTier) ? doTier : "warp-vip";
        String mundoNome = plugin.getConfig().getString(base + ".mundo", "world");
        World mundo = plugin.getServer().getWorld(mundoNome);
        if (mundo == null) {
            player.sendMessage("§cO mundo do warp de VIP não está configurado corretamente.");
            return;
        }
        double x = plugin.getConfig().getDouble(base + ".x");
        double y = plugin.getConfig().getDouble(base + ".y");
        double z = plugin.getConfig().getDouble(base + ".z");
        float yaw = (float) plugin.getConfig().getDouble(base + ".yaw", 0);
        player.teleport(new Location(mundo, x, y, z, yaw, 0));
        player.sendMessage("§aTeleportado pra área exclusiva " + (base.equals(doTier) ? "do VIP " + plugin.getNomeExibicao(tier) : "de VIP") + "§a.");
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

    /**
     * {@code minutos <= 0} significa "sem cooldown". Já avisa o jogador quando ainda está em espera.
     * O último uso fica no {@link CooldownManager} (salvo em disco), então sobrevive a reinícios.
     */
    private boolean passouCooldown(Perk perk, Player player, int minutos) {
        if (minutos <= 0) {
            return true;
        }
        long restanteMs = plugin.getCooldownManager().restanteMs(perk, player.getUniqueId(), minutos);
        if (restanteMs > 0) {
            player.sendMessage("§cAinda em cooldown. Tente de novo em §f" + formatarTempo(restanteMs / 1000) + "§c.");
            return false;
        }

        plugin.getCooldownManager().registrarUso(perk, player.getUniqueId());
        return true;
    }

    /** "45s", "12min 3s", "5h 20min" — cooldown de kit em horas ficava ilegível só em segundos. */
    private String formatarTempo(long segundos) {
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long seg = segundos % 60;
        if (horas > 0) return horas + "h " + minutos + "min";
        if (minutos > 0) return minutos + "min " + seg + "s";
        return seg + "s";
    }
}
