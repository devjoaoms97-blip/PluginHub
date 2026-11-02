package com.example.skills.gui;

import com.example.skills.SkillsPlugin;
import com.example.skills.skill.PlayerSkillData;
import com.example.skills.skill.Skill;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SkillsGUI {

    public static final String TITULO = "§8§lSuas Skills";

    private final SkillsPlugin plugin;

    public SkillsGUI(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory montar(Player jogador) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 27, TITULO);

        PlayerSkillData dados = plugin.getSkillDataManager().get(jogador.getUniqueId());
        int nivelMaximo = plugin.getXpManager().getFormula().getNivelMaximo();

        // combate nas duas primeiras linhas, profissões na última
        int combate = 0;
        int profissao = 18;
        for (Skill skill : Skill.values()) {
            inv.setItem(skill.isProfissao() ? profissao++ : combate++, criarItemSkill(skill, dados, nivelMaximo));
        }

        return inv;
    }

    private ItemStack criarItemSkill(Skill skill, PlayerSkillData dados, int nivelMaximo) {
        ItemStack item = new ItemStack(skill.getIcone());
        ItemMeta meta = item.getItemMeta();

        int nivel = dados.getNivel(skill);
        double xpAtual = dados.getXp(skill);
        boolean maximo = nivel >= nivelMaximo;

        int prestigio = dados.getPrestigio(skill);
        String marca = prestigio > 0 ? " §d✦" + prestigio : "";
        meta.setDisplayName("§e§l" + skill.getNomeExibicao() + marca + " §7- §fNível " + nivel + (maximo ? " §6(MÁXIMO)" : ""));

        List<String> lore = new ArrayList<>();
        lore.add("§7" + skill.getDescricaoXp());
        lore.add("");

        if (!maximo) {
            double xpNecessario = plugin.getXpManager().getFormula().xpParaProximoNivel(nivel);
            double progresso = Math.min(1.0, xpAtual / xpNecessario);
            lore.add("§7Progresso: " + barraDeProgresso(progresso));
            lore.add("§7" + formatarNumero(xpAtual) + " §8/ §7" + formatarNumero(xpNecessario) + " XP");
        } else {
            lore.add(barraDeProgresso(1.0));
            lore.add("§6Nível máximo atingido!");
            if (prestigio < plugin.getConfig().getInt("prestigio.maximo", 5)) {
                lore.add("§dPrestígio liberado: §f/skills prestigio " + skill.getChaveConfig());
            }
        }
        if (prestigio > 0) {
            lore.add("§dPrestígio " + prestigio + ": §f+" + String.format(Locale.forLanguageTag("pt-BR"), "%.0f",
                    prestigio * plugin.getConfig().getDouble("prestigio.bonus-por-prestigio", 0.10) * 100) + "% §7em tudo o que a skill dá");
        }

        lore.add("");
        if (skill.isProfissao()) {
            lore.addAll(efeitosProfissao(skill, dados));
        } else if (skill == Skill.CRITICO) {
            double chance = plugin.getBonusCalculator().getChanceCritico(dados.getJogadorId());
            double multiplicador = plugin.getBonusCalculator().getMultiplicadorCritico();
            lore.add("§aChance de crítico: §f" + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", chance * 100) + "%");
            lore.add("§aDano do crítico: §f+" + String.format(Locale.forLanguageTag("pt-BR"), "%.0f", multiplicador * 100) + "%");
        } else {
            double bonus = plugin.getBonusCalculator().getBonus(dados.getJogadorId(), skill);
            lore.add("§aBônus atual: §f+" + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", bonus * 100) + "%");

            // Skills de arma que aplicam efeitos: mostra a chance do efeito procar
            String efeito = null;
            double chanceEfeito = 0.0;
            switch (skill) {
                case PORRADEIRO -> {
                    efeito = "Lentidão";
                    chanceEfeito = plugin.getBonusCalculator().getChanceAtordoar(dados.getJogadorId());
                }
                case TRIDENTE -> {
                    efeito = "Empurrão";
                    chanceEfeito = plugin.getBonusCalculator().getChanceEmpurrao(dados.getJogadorId());
                }
                case LANCEIRO -> {
                    efeito = "Sangramento";
                    chanceEfeito = plugin.getBonusCalculator().getChanceSangramento(dados.getJogadorId());
                }
                case MARRETEIRO -> {
                    efeito = "Atordoamento";
                    chanceEfeito = plugin.getBonusCalculator().getChanceAtordoarMarreteiro(dados.getJogadorId());
                }
                default -> { }
            }

            if (efeito != null) {
                lore.add("§aChance de " + efeito.toLowerCase() + ": §f" + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", chanceEfeito * 100) + "%");
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** O que a profissão dá agora (as chances crescem com o nível e o prestígio). */
    private List<String> efeitosProfissao(Skill skill, PlayerSkillData dados) {
        var bonus = plugin.getBonusCalculator();
        java.util.UUID id = dados.getJogadorId();
        List<String> linhas = new ArrayList<>();
        switch (skill) {
            case MINERADOR -> linhas.add("§aQuebrar 3x3 com a picareta: §f" + pct(bonus.porNivel(id, skill, "minerador.chance-3x3-por-nivel")));
            case AGRICULTOR -> {
                linhas.add("§aVenda no Fazendeiro: §f+" + pct(bonus.porNivel(id, skill, "agricultor.bonus-venda-por-nivel")));
                linhas.add("§aColheita dupla: §f" + pct(bonus.porNivel(id, skill, "agricultor.chance-colheita-dupla-por-nivel")));
            }
            case PESCADOR -> {
                linhas.add("§aMapa do tesouro: §f" + pct(bonus.porNivel(id, skill, "pescador.chance-mapa-por-nivel")) + " §7por pesca");
                linhas.add("§aFragmento Arcano: §f" + pct(bonus.porNivel(id, skill, "pescador.chance-fragmento-por-nivel")) + " §7por pesca");
            }
            case LENHADOR -> {
                linhas.add("§aTora a mais: §f" + pct(bonus.porNivel(id, skill, "lenhador.chance-tora-extra-por-nivel")));
                linhas.add("§aDerrubar a árvore: §f" + pct(bonus.porNivel(id, skill, "lenhador.chance-derrubar-por-nivel"))
                        + " §7(machado, mundo de recursos)");
            }
            default -> { }
        }
        return linhas;
    }

    private static String pct(double fracao) {
        return String.format(Locale.forLanguageTag("pt-BR"), fracao < 0.01 ? "%.2f%%" : "%.1f%%", fracao * 100);
    }

    private String barraDeProgresso(double fracao) {
        int total = 20;
        int preenchido = (int) Math.round(total * fracao);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < total; i++) {
            if (i == preenchido) sb.append("§7");
            sb.append(i < preenchido ? "■" : "■");
        }
        return sb.toString();
    }

    private String formatarNumero(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,.0f", valor);
    }
}
