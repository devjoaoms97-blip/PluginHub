package com.example.skills.command;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SkillsCommand implements CommandExecutor, TabCompleter {

    private final SkillsPlugin plugin;

    public SkillsCommand(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return tratarAdmin(sender, args);
        }

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem usar este comando.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("ver")) {
            mostrarResumoNoChat(jogador);
            return true;
        }

        jogador.openInventory(plugin.getSkillsGUI().montar(jogador));
        return true;
    }

    // ---------------------------------------------------------------------
    // /skills admin setlevel <jogador> <skill> <nível>
    // ---------------------------------------------------------------------
    private boolean tratarAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skills.admin")) {
            sender.sendMessage("§cVocê não tem permissão para usar comandos administrativos de skills.");
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("criaradaga")) {
            return tratarCriarAdaga(sender);
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("setlevel")) {
            sender.sendMessage("§cUso: §f/skills admin setlevel <jogador> <skill> <nível>");
            sender.sendMessage("§cOu: §f/skills admin criaradaga §c(transforma a espada na mão em Adaga)");
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage("§cUso: §f/skills admin setlevel <jogador> <skill> <nível>");
            sender.sendMessage("§7Skills disponíveis: §f" + nomesDasSkills());
            return true;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayer(args[2]);
        if (!alvo.hasPlayedBefore() && !alvo.isOnline()) {
            sender.sendMessage("§cJogador \"" + args[2] + "\" não encontrado.");
            return true;
        }

        Skill skill = resolverSkill(args[3]);
        if (skill == null) {
            sender.sendMessage("§cSkill \"" + args[3] + "\" inválida. Skills disponíveis: §f" + nomesDasSkills());
            return true;
        }

        int nivelMaximo = plugin.getXpManager().getFormula().getNivelMaximo();
        int nivel;
        try {
            nivel = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNível inválido. Use um número entre 1 e " + nivelMaximo + ".");
            return true;
        }

        if (nivel < 1 || nivel > nivelMaximo) {
            sender.sendMessage("§cO nível deve estar entre 1 e " + nivelMaximo + ".");
            return true;
        }

        PlayerSkillData dados = plugin.getSkillDataManager().get(alvo.getUniqueId());
        dados.setNivel(skill, nivel);
        dados.setXp(skill, 0); // zera o XP acumulado da skill, já que o "degrau" mudou manualmente
        plugin.getSkillDataManager().salvar(alvo.getUniqueId());

        if (alvo.isOnline() && alvo.getPlayer() != null) {
            plugin.getPassiveSkillsTask().atualizarVelocidade(alvo.getPlayer());
        }

        sender.sendMessage("§aVocê definiu §f" + skill.getNomeExibicao() + " §ade §f" + alvo.getName()
                + " §apara o nível §f" + nivel + "§a.");

        if (alvo.isOnline() && alvo.getPlayer() != null && !alvo.getPlayer().equals(sender)) {
            alvo.getPlayer().sendMessage("§eUm administrador ajustou sua skill §f" + skill.getNomeExibicao()
                    + " §epara o nível §f" + nivel + "§e.");
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // /skills admin criaradaga
    // ---------------------------------------------------------------------
    private boolean tratarCriarAdaga(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem usar este comando (precisa segurar a espada na mão).");
            return true;
        }

        org.bukkit.inventory.ItemStack itemNaMao = jogador.getInventory().getItemInMainHand();

        if (!plugin.getAdagaUtil().ehEspadaValida(itemNaMao.getType())) {
            jogador.sendMessage("§cVocê precisa estar segurando uma espada (qualquer material) pra transformar em Adaga.");
            return true;
        }

        if (plugin.getAdagaUtil().ehAdaga(itemNaMao)) {
            jogador.sendMessage("§cEsse item já é uma Adaga.");
            return true;
        }

        plugin.getAdagaUtil().transformarEmAdaga(itemNaMao);
        jogador.sendMessage("§d§lA espada na sua mão virou uma Adaga! §7Golpes com ela treinam Crítico.");
        return true;
    }

    private Skill resolverSkill(String texto) {
        String normalizado = texto.toLowerCase(Locale.ROOT);
        for (Skill skill : Skill.values()) {
            if (skill.getChaveConfig().equalsIgnoreCase(normalizado) || skill.name().equalsIgnoreCase(normalizado)) {
                return skill;
            }
        }
        return null;
    }

    private String nomesDasSkills() {
        StringBuilder sb = new StringBuilder();
        for (Skill skill : Skill.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(skill.getChaveConfig());
        }
        return sb.toString();
    }

    private void mostrarResumoNoChat(Player jogador) {
        PlayerSkillData dados = plugin.getSkillDataManager().get(jogador.getUniqueId());
        int nivelMaximo = plugin.getXpManager().getFormula().getNivelMaximo();

        jogador.sendMessage("§e§l--- Suas Skills ---");
        for (Skill skill : Skill.values()) {
            int nivel = dados.getNivel(skill);
            double xp = dados.getXp(skill);
            String progresso;
            if (nivel >= nivelMaximo) {
                progresso = "§6MÁXIMO";
            } else {
                double xpNecessario = plugin.getXpManager().getFormula().xpParaProximoNivel(nivel);
                progresso = String.format(Locale.forLanguageTag("pt-BR"), "%.0f/%.0f XP", xp, xpNecessario);
            }
            jogador.sendMessage("§f" + skill.getNomeExibicao() + " §7- §fNível " + nivel + " §7(" + progresso + "§7)");
        }
        jogador.sendMessage("§7Use §f/skills §7para abrir o menu visual.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();

        if (args.length == 1) {
            opcoes.add("ver");
            if (sender.hasPermission("skills.admin")) {
                opcoes.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            opcoes.add("setlevel");
            opcoes.add("criaradaga");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setlevel")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                opcoes.add(p.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setlevel")) {
            for (Skill skill : Skill.values()) {
                opcoes.add(skill.getChaveConfig());
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setlevel")) {
            opcoes.addAll(Arrays.asList("1", "10", "25", "50"));
        }

        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
