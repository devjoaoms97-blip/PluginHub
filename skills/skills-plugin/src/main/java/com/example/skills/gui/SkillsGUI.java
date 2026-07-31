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

        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length; i++) {
            Skill skill = skills[i];
            inv.setItem(i, criarItemSkill(skill, dados, nivelMaximo));
        }

        return inv;
    }

    private ItemStack criarItemSkill(Skill skill, PlayerSkillData dados, int nivelMaximo) {
        ItemStack item = new ItemStack(skill.getIcone());
        ItemMeta meta = item.getItemMeta();

        int nivel = dados.getNivel(skill);
        double xpAtual = dados.getXp(skill);
        boolean maximo = nivel >= nivelMaximo;

        meta.setDisplayName("§e§l" + skill.getNomeExibicao() + " §7- §fNível " + nivel + (maximo ? " §6(MÁXIMO)" : ""));

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
        }

        lore.add("");
        double bonus = plugin.getBonusCalculator().getBonus(dados.getJogadorId(), skill);
        lore.add("§aBônus atual: §f+" + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", bonus * 100) + "%");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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
