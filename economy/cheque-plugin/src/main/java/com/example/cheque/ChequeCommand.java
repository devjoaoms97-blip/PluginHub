package com.example.cheque;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChequeCommand implements CommandExecutor {

    private final ChequePlugin plugin;
    // última emissão de cada jogador, pro cooldown
    private final java.util.Map<UUID, Long> ultimaEmissao = new java.util.HashMap<>();

    public ChequeCommand(ChequePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Somente jogadores podem usar este comando.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("cheque.criar")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para criar cheques.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("cancelar")) {
            cancelar(player, args);
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(ChatColor.RED + "Uso correto: /cheque <valor> [jogador]  (com jogador, só ele resgata)");
            player.sendMessage(ChatColor.RED + "Ou: /cheque cancelar [id]  (o cheque da mão, ou pelo ID do lore)");
            return true;
        }

        double valor;
        try {
            valor = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Valor inválido. Use um número, ex: /cheque 100.50");
            return true;
        }

        if (!Double.isFinite(valor) || valor <= 0) { // parseDouble aceita NaN/Infinity, e NaN passa por qualquer comparação com zero
            player.sendMessage(ChatColor.RED + "O valor do cheque deve ser maior que zero.");
            return true;
        }

        // limites: valor máximo por cheque e intervalo entre emissões (cheque.limite.ignorar pula os dois)
        boolean semLimite = player.hasPermission("cheque.limite.ignorar");
        double maximo = plugin.getConfig().getDouble("limite.valor-maximo", 0);
        if (!semLimite && maximo > 0 && valor > maximo) {
            player.sendMessage(ChatColor.RED + "O valor máximo de um cheque é " + formatarValor(maximo) + ".");
            return true;
        }
        long cooldownMs = plugin.getConfig().getLong("limite.cooldown-segundos", 0) * 1000L;
        Long ultima = ultimaEmissao.get(player.getUniqueId());
        if (!semLimite && cooldownMs > 0 && ultima != null && System.currentTimeMillis() - ultima < cooldownMs) {
            long falta = (cooldownMs - (System.currentTimeMillis() - ultima) + 999) / 1000;
            player.sendMessage(ChatColor.RED + "Espere " + falta + "s pra emitir outro cheque.");
            return true;
        }

        OfflinePlayer nominal = null;
        if (args.length == 2) {
            nominal = Bukkit.getOfflinePlayer(args[1]);
            if (!nominal.hasPlayedBefore() && !nominal.isOnline()) {
                player.sendMessage(ChatColor.RED + "Jogador \"" + args[1] + "\" não encontrado.");
                return true;
            }
        }

        ItemStack itemNaMao = player.getInventory().getItemInMainHand();

        if (!plugin.getBanco().ehFolha(itemNaMao)) {
            player.sendMessage(ChatColor.RED + "Você precisa estar segurando uma folha de cheque em branco. Compre com o Bancário.");
            return true;
        }

        // o valor sai do saldo de quem emite: o cheque é dinheiro de verdade passando de mão em mão
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            player.sendMessage(ChatColor.RED + "Não há um plugin de economia (Vault) instalado no servidor.");
            return true;
        }
        if (!econ.has(player, valor)) {
            player.sendMessage(ChatColor.RED + "Saldo insuficiente: o cheque de " + formatarValor(valor)
                    + " sai do seu saldo, e você tem " + formatarValor(econ.getBalance(player)) + ".");
            return true;
        }
        if (!econ.withdrawPlayer(player, valor).transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "Não foi possível tirar o valor do seu saldo.");
            return true;
        }

        // Consome 1 folha da mão
        if (itemNaMao.getAmount() > 1) {
            itemNaMao.setAmount(itemNaMao.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        ItemStack cheque = criarCheque(player, valor, nominal);
        ultimaEmissao.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getPix().registrar(player.getUniqueId(), "Cheque", valor, false,
                "Cheque emitido" + (nominal != null ? " pra " + nominal.getName() : ""));
        plugin.getChequeLog().emitido(player.getUniqueId(), player.getName(), valor,
                cheque.getItemMeta().getPersistentDataContainer().get(plugin.getChequeIdKey(), PersistentDataType.STRING),
                nominal != null ? nominal.getName() : null);

        // Entrega o cheque; se o inventário estiver cheio, dropa no chão
        var sobras = player.getInventory().addItem(cheque);
        sobras.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        player.sendMessage(ChatColor.GREEN + "Cheque no valor de " + ChatColor.GOLD + formatarValor(valor)
                + ChatColor.GREEN + (nominal != null ? " nominal a " + ChatColor.WHITE + nominal.getName() + ChatColor.GREEN : "")
                + " criado com sucesso! O valor saiu do seu saldo.");

        return true;
    }

    private ItemStack criarCheque(Player emissor, double valor, OfflinePlayer nominal) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + "Cheque" + ChatColor.DARK_GRAY + " - " + ChatColor.GOLD + formatarValor(valor));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Emitido por: " + ChatColor.WHITE + emissor.getName());
        lore.add(ChatColor.GRAY + "Data: " + ChatColor.WHITE + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        if (nominal != null) {
            lore.add(ChatColor.GRAY + "Nominal a: " + ChatColor.WHITE + nominal.getName() + ChatColor.GRAY + " (só ele resgata)");
        }
        String id = UUID.randomUUID().toString();
        // os 8 primeiros caracteres do ID: com eles a staff cancela um cheque que já está com outro jogador
        lore.add(ChatColor.DARK_GRAY + "ID: " + id.substring(0, 8));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Clique direito para resgatar");
        meta.setLore(lore);

        // Grava dados reais no item (não confia no texto do lore, que pode ser editado
        // por outros plugins de anvil/renomear). Isso evita duplicação/falsificação.
        meta.getPersistentDataContainer().set(plugin.getChequeValueKey(), PersistentDataType.DOUBLE, valor);
        meta.getPersistentDataContainer().set(plugin.getChequeIdKey(), PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(plugin.getChequeEmissorKey(), PersistentDataType.STRING, emissor.getUniqueId().toString());
        if (nominal != null) {
            meta.getPersistentDataContainer().set(plugin.getChequeNominalKey(), PersistentDataType.STRING, nominal.getUniqueId().toString());
        }

        cheque.setItemMeta(meta);
        return cheque;
    }

    /**
     * /cheque cancelar: anula o cheque da mão (quem emitiu, ou a staff com cheque.admin), que some.
     * /cheque cancelar <id>: anula pelo ID do lore (8 caracteres) ou o inteiro do log, mesmo com o
     * cheque no inventário de outro jogador; só a staff. Quem tentar resgatar depois perde o cheque.
     * O valor volta pra quem emitiu (emitidos.yml); cheque antigo, de antes de sair do saldo, não devolve.
     */
    private void cancelar(Player player, String[] args) {
        boolean admin = player.hasPermission("cheque.admin");
        if (args.length >= 2) {
            if (!admin) {
                player.sendMessage(ChatColor.RED + "Cancelar pelo ID é só pra staff. Segure o cheque e use /cheque cancelar.");
                return;
            }
            String id = args[1].trim().toLowerCase(Locale.ROOT);
            if (id.length() < 8) {
                player.sendMessage(ChatColor.RED + "Use o ID do lore do cheque (8 caracteres) ou o inteiro do cheques.log.");
                return;
            }
            if (plugin.getChequeLog().jaResgatado(id)) {
                player.sendMessage(ChatColor.RED + "Esse cheque já foi resgatado.");
                return;
            }
            if (plugin.getChequeLog().cancelado(id)) {
                player.sendMessage(ChatColor.RED + "Esse cheque já foi cancelado.");
                return;
            }
            var emissao = plugin.getChequeLog().emissao(id);
            plugin.getChequeLog().cancelar(player.getName(), id, emissao != null ? emissao.valor() : null);
            String devolvido = devolver(emissao);
            player.sendMessage(ChatColor.GREEN + "Cheque " + ChatColor.WHITE + id + ChatColor.GREEN + " cancelado: ninguém mais resgata."
                    + devolvido);
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        var pdc = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer() : null;
        String id = pdc == null ? null : pdc.get(plugin.getChequeIdKey(), PersistentDataType.STRING);
        if (id == null) {
            player.sendMessage(ChatColor.RED + "Segure o cheque na mão principal (ou, staff: /cheque cancelar <id>).");
            return;
        }
        String emissor = pdc.get(plugin.getChequeEmissorKey(), PersistentDataType.STRING);
        if (!admin && !player.getUniqueId().toString().equals(emissor)) {
            player.sendMessage(ChatColor.RED + "Só quem emitiu o cheque (ou a staff) pode cancelar.");
            return;
        }
        if (plugin.getChequeLog().cancelado(id) || plugin.getChequeLog().jaResgatado(id)) {
            player.sendMessage(ChatColor.RED + "Esse cheque já foi cancelado ou resgatado (é uma cópia).");
            return;
        }
        Double valor = pdc.get(plugin.getChequeValueKey(), PersistentDataType.DOUBLE);
        var emissao = plugin.getChequeLog().emissao(id);
        plugin.getChequeLog().cancelar(player.getName(), id, valor);
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.sendMessage(ChatColor.GREEN + "Cheque de " + ChatColor.GOLD + formatarValor(valor == null ? 0 : valor)
                + ChatColor.GREEN + " cancelado." + devolver(emissao));
    }

    /** Devolve o valor do cheque cancelado pra quem emitiu. Retorna o pedaço da mensagem pra staff. */
    private String devolver(com.example.cheque.ChequeLog.Emissao emissao) {
        if (emissao == null || plugin.getEconomy() == null) return ChatColor.GRAY + " (cheque antigo: sem valor pra devolver)";
        OfflinePlayer emissor = Bukkit.getOfflinePlayer(emissao.emissor());
        plugin.getEconomy().depositPlayer(emissor, emissao.valor());
        plugin.getPix().registrar(emissao.emissor(), "Cheque", emissao.valor(), true, "Cheque cancelado (devolução)");
        if (emissor.getPlayer() != null) {
            emissor.getPlayer().sendMessage(ChatColor.GREEN + "Seu cheque de " + ChatColor.GOLD + formatarValor(emissao.valor())
                    + ChatColor.GREEN + " foi cancelado, e o valor voltou pro seu saldo.");
        }
        return ChatColor.GREEN + " " + formatarValor(emissao.valor()) + " voltaram pra " + emissor.getName() + ".";
    }

    private String formatarValor(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", valor);
    }
}
