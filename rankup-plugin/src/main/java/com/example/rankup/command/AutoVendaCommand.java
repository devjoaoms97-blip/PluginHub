package com.example.rankup.command;

import com.example.rankup.listener.ColetaMinaListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /autovenda liga e desliga a venda automática do que se minera nas minas (benefício de VIP). */
public class AutoVendaCommand implements CommandExecutor {

    private final ColetaMinaListener listener;

    public AutoVendaCommand(ColetaMinaListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSomente jogadores usam a venda automática.");
            return true;
        }
        if (!jogador.hasPermission(ColetaMinaListener.PERMISSAO_AUTOVENDA)) {
            jogador.sendMessage("§cA venda automática é um benefício de VIP. Veja os tiers com §f/vip tiers§c.");
            return true;
        }
        boolean ligada = listener.alternarAutoVenda(jogador);
        jogador.sendMessage(ligada
                ? "§aVenda automática ligada: o que você minerar nas minas é vendido na hora."
                : "§7Venda automática desligada: o minério vai pro inventário, pra vender com §f/sell§7.");
        return true;
    }
}
