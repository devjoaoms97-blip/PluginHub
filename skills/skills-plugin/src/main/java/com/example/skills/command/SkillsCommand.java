package com.example.skills.command;

import com.example.skills.manager.RankingSkills;
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

        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            mostrarTop(sender, args);
            return true;
        }

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores podem usar este comando.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("ver")) {
            mostrarResumoNoChat(jogador);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("prestigio")) {
            tratarPrestigio(jogador, args);
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

        if (args.length >= 2 && args[1].equalsIgnoreCase("xpdobro")) {
            return tratarXpDobro(sender, args);
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

    /**
     * /skills prestigio <skill> [confirmar]: no nível máximo, volta a skill pro nível 1 e ganha um
     * prestígio, que aumenta tudo o que ela dá ({@code prestigio.bonus-por-prestigio} por prestígio).
     * Sem o "confirmar", só explica o que vai acontecer, pra ninguém perder o nível sem querer.
     */
    private void tratarPrestigio(Player jogador, String[] args) {
        Skill skill = args.length >= 2 ? resolverSkill(args[1]) : null;
        if (skill == null) {
            jogador.sendMessage("§cUso: §f/skills prestigio <skill> §7(" + nomesDasSkills() + ")");
            return;
        }
        PlayerSkillData dados = plugin.getSkillDataManager().get(jogador.getUniqueId());
        int nivelMaximo = plugin.getXpManager().getFormula().getNivelMaximo();
        int prestigioMaximo = plugin.getConfig().getInt("prestigio.maximo", 5);
        int prestigio = dados.getPrestigio(skill);
        double porPrestigio = plugin.getConfig().getDouble("prestigio.bonus-por-prestigio", 0.10);

        if (prestigio >= prestigioMaximo) {
            jogador.sendMessage("§c" + skill.getNomeExibicao() + " já está no prestígio máximo (" + prestigioMaximo + ").");
            return;
        }
        if (dados.getNivel(skill) < nivelMaximo) {
            jogador.sendMessage("§cO prestígio só libera no nível " + nivelMaximo + ". §7" + skill.getNomeExibicao()
                    + " está no nível " + dados.getNivel(skill) + ".");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirmar")) {
            jogador.sendMessage("§d§l✦ Prestígio " + (prestigio + 1) + " em " + skill.getNomeExibicao());
            jogador.sendMessage("§7A skill volta pro §fnível 1§7, e tudo o que ela dá fica §f+"
                    + String.format(Locale.forLanguageTag("pt-BR"), "%.0f", (prestigio + 1) * porPrestigio * 100)
                    + "%§7 mais forte pra sempre (contando os prestígios que você já tem).");
            jogador.sendMessage("§7Pra confirmar: §f/skills prestigio " + skill.getChaveConfig() + " confirmar");
            return;
        }

        dados.setPrestigio(skill, prestigio + 1);
        dados.setNivel(skill, 1);
        dados.setXp(skill, 0);
        plugin.getSkillDataManager().salvar(jogador.getUniqueId());
        plugin.getPassiveSkillsTask().atualizarVelocidade(jogador);

        Bukkit.broadcastMessage("§d§l✦ §f" + jogador.getName() + " §dchegou ao prestígio §f" + (prestigio + 1)
                + " §dem §f" + skill.getNomeExibicao() + "§d!");
        jogador.playSound(jogador.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }

    /** /skills top [skill]: top 10 de uma skill, ou da soma dos níveis de todas. */
    private void mostrarTop(CommandSender sender, String[] args) {
        Skill skill = null;
        if (args.length >= 2) {
            skill = resolverSkill(args[1]);
            if (skill == null) {
                sender.sendMessage("§cSkill não encontrada. Use: §f" + nomesDasSkills());
                return;
            }
        }
        List<RankingSkills.Posicao> top = plugin.getRankingSkills().top(skill, 10);
        sender.sendMessage("§e§l--- Top " + (skill == null ? "Skills (soma dos níveis)" : skill.getNomeExibicao()) + " ---");
        if (top.isEmpty()) sender.sendMessage("§7Ninguém no ranking ainda.");
        String[] cores = {"§6", "§f", "§c"};
        for (int i = 0; i < top.size(); i++) {
            RankingSkills.Posicao p = top.get(i);
            String cor = i < cores.length ? cores[i] : "§7";
            String prestigio = p.prestigio() > 0 ? "§d✦" + p.prestigio() + " " : "";
            sender.sendMessage(cor + (i + 1) + "º §f" + p.nome() + " §7- " + prestigio + (skill == null ? "§f" + p.nivel() + " níveis" : "§fnível " + p.nivel()));
        }
        if (skill == null) sender.sendMessage("§7Por skill: §f/skills top <skill>");
    }

    /** /skills admin xpdobro <multiplicador> <horas> | parar */
    private boolean tratarXpDobro(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("parar")) {
            plugin.getEventoXp().ligarManual(1, 0);
            sender.sendMessage("§aEvento de XP manual desligado (os dias fixos do config continuam valendo).");
            return true;
        }
        double multiplicador, horas;
        try {
            multiplicador = Double.parseDouble(args[2].replace(",", "."));
            horas = Double.parseDouble(args[3].replace(",", "."));
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            sender.sendMessage("§cUso: §f/skills admin xpdobro <multiplicador> <horas> §7| §f/skills admin xpdobro parar");
            return true;
        }
        if (!Double.isFinite(multiplicador) || multiplicador < 1 || multiplicador > 10 || !Double.isFinite(horas) || horas <= 0) {
            sender.sendMessage("§cMultiplicador de 1 a 10 e horas maior que zero.");
            return true;
        }
        plugin.getEventoXp().ligarManual(multiplicador, horas);
        sender.sendMessage("§aXP x" + multiplicador + " nas skills pelas próximas " + horas + "h.");
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
            String prestigio = dados.getPrestigio(skill) > 0 ? " §d✦" + dados.getPrestigio(skill) : "";
            jogador.sendMessage("§f" + skill.getNomeExibicao() + prestigio + " §7- §fNível " + nivel + " §7(" + progresso + "§7)");
        }
        jogador.sendMessage("§7Use §f/skills §7para abrir o menu visual.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();

        if (args.length == 1) {
            opcoes.add("ver");
            opcoes.add("top");
            opcoes.add("prestigio");
            if (sender.hasPermission("skills.admin")) {
                opcoes.add("admin");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("prestigio"))) {
            for (Skill skill : Skill.values()) opcoes.add(skill.getChaveConfig());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            opcoes.add("setlevel");
            opcoes.add("criaradaga");
            opcoes.add("xpdobro");
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
