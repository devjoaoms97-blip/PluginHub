package com.example.cheque;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Mine-sena: a loteria do Bancário. O jogador escolhe um número de 1 a {@code minesena.numero-maximo}
 * (100) e compra o bilhete no balcão, por {@code minesena.preco-bilhete}. Todo dia no
 * {@code minesena.horario} (18:00, hora do servidor) sai o número premiado, e o pote inteiro (todas
 * as apostas acumuladas) é dividido entre os bilhetes com esse número: quem tem 2 leva 2 partes. Sem
 * ganhador, o pote acumula pro sorteio seguinte.
 * <p>
 * O bilhete fica registrado no nome do jogador (não é item: não dá pra duplicar nem perder) e vale pro
 * próximo sorteio. Se o servidor estava desligado no horário, o sorteio sai assim que ele liga. A
 * aposta e o prêmio aparecem no extrato do Bancário, o resultado vai pro chat e pro Discord (categoria
 * "minesena"), e quem ganhou offline é avisado ao entrar. Tudo fica no minesena.yml.
 */
public class MineSena implements Listener, CommandExecutor {

    private static final int POR_PAGINA = 45;
    private static final int SLOT_VOLTAR = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_AVANCAR = 53;

    static class Menu implements InventoryHolder {
        final int pagina;
        Inventory inventario;

        Menu(int pagina) {
            this.pagina = pagina;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final ChequePlugin plugin;
    private final File arquivo;
    private double pote;
    /** jogador → número → quantos bilhetes, pro próximo sorteio. */
    private final Map<UUID, Map<Integer, Integer>> apostas = new LinkedHashMap<>();
    private LocalDate ultimoSorteio;
    private int ultimoNumero;
    private String ultimoResultado = "";
    /** Quem ganhou estando offline: o aviso sai quando entrar. */
    private final Map<UUID, String> avisos = new HashMap<>();
    private boolean avisouFalhaDiscord;

    public MineSena(ChequePlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "minesena.yml");
        carregar();
    }

    private int numeroMaximo() {
        return Math.max(2, plugin.getConfig().getInt("minesena.numero-maximo", 100));
    }

    private double preco() {
        return Math.max(0, plugin.getConfig().getDouble("minesena.preco-bilhete", 500));
    }

    private LocalTime horario() {
        try {
            return LocalTime.parse(plugin.getConfig().getString("minesena.horario", "18:00"));
        } catch (DateTimeParseException e) {
            return LocalTime.of(18, 0);
        }
    }

    public double getPote() {
        return pote;
    }

    // ---------------------------------------------------------------------
    // Sorteio
    // ---------------------------------------------------------------------

    /** A cada minuto (e ao ligar): passou do horário e ainda não sorteou hoje? Sorteia. */
    public void conferir() {
        if (!plugin.getConfig().getBoolean("minesena.ativado", true)) return;
        LocalDateTime agora = LocalDateTime.now();
        if (agora.toLocalTime().isBefore(horario())) return;
        if (agora.toLocalDate().equals(ultimoSorteio)) return;
        if (apostas.isEmpty() && pote <= 0) {
            // ninguém jogou e não tem pote: só marca o dia, sem anunciar um sorteio vazio
            ultimoSorteio = agora.toLocalDate();
            salvar();
            return;
        }
        sortear(ThreadLocalRandom.current().nextInt(1, numeroMaximo() + 1));
    }

    /** Sorteia o {@code numero}: paga os bilhetes premiados ou acumula. */
    public void sortear(int numero) {
        Economy econ = plugin.getEconomy();
        int premiados = 0;
        Map<UUID, Integer> ganhadores = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<Integer, Integer>> e : apostas.entrySet()) {
            int qtd = e.getValue().getOrDefault(numero, 0);
            if (qtd > 0) {
                ganhadores.put(e.getKey(), qtd);
                premiados += qtd;
            }
        }
        String data = String.format("%02d/%02d", LocalDate.now().getDayOfMonth(), LocalDate.now().getMonthValue());
        String anuncio;
        if (premiados == 0 || econ == null) {
            anuncio = "§6§l🎟 MINE-SENA " + data + ": §fo número sorteado foi §e§l" + numero + "§f. Ninguém acertou: o pote de §a"
                    + dinheiro(pote) + " §facumula pro próximo sorteio!";
            ultimoResultado = "nº " + numero + ", sem ganhador (acumulou " + dinheiro(pote) + ")";
        } else {
            double porBilhete = Math.floor(pote / premiados * 100) / 100.0;
            List<String> nomes = new ArrayList<>();
            for (Map.Entry<UUID, Integer> g : ganhadores.entrySet()) {
                double premio = porBilhete * g.getValue();
                OfflinePlayer jogador = Bukkit.getOfflinePlayer(g.getKey());
                econ.depositPlayer(jogador, premio);
                plugin.getPix().registrar(g.getKey(), "Mine-sena", premio, true, "Prêmio da Mine-sena (nº " + numero + ")");
                nomes.add(jogador.getName() + (g.getValue() > 1 ? " (" + g.getValue() + " bilhetes)" : ""));
                String aviso = "§6§l🎟 Você ganhou na Mine-sena! §fO número §e" + numero + "§f deu §a" + dinheiro(premio) + "§f.";
                if (jogador.getPlayer() != null) {
                    jogador.getPlayer().sendMessage(aviso);
                    jogador.getPlayer().playSound(jogador.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                } else {
                    avisos.put(g.getKey(), aviso);
                }
            }
            anuncio = "§6§l🎟 MINE-SENA " + data + ": §fo número sorteado foi §e§l" + numero + "§f! §a" + dinheiro(pote)
                    + " §fpra §e" + String.join(", ", nomes) + (premiados > 1 ? " §7(dividido em " + premiados + " bilhetes)" : "") + "§f.";
            ultimoResultado = "nº " + numero + ", " + dinheiro(pote) + " pra " + String.join(", ", nomes);
            pote = 0;
        }
        apostas.clear();
        ultimoSorteio = LocalDate.now();
        ultimoNumero = numero;
        salvar();
        Bukkit.broadcastMessage(anuncio);
        enviarDiscord(anuncio);
        plugin.getLogger().info("Mine-sena: " + ChatColor.stripColor(ultimoResultado));
    }

    // ---------------------------------------------------------------------
    // Compra (menu do Bancário)
    // ---------------------------------------------------------------------

    public void abrir(Player jogador, int pagina) {
        int paginas = (numeroMaximo() + POR_PAGINA - 1) / POR_PAGINA;
        pagina = Math.max(0, Math.min(paginas - 1, pagina));
        Menu menu = new Menu(pagina);
        menu.inventario = Bukkit.createInventory(menu, 54, "§8🎟 Mine-sena " + (pagina + 1) + "/" + paginas);
        Map<Integer, Integer> meus = apostas.getOrDefault(jogador.getUniqueId(), Map.of());
        for (int i = 0; i < POR_PAGINA; i++) {
            int numero = pagina * POR_PAGINA + i + 1;
            if (numero > numeroMaximo()) break;
            int tenho = meus.getOrDefault(numero, 0);
            ItemStack bola = icone(tenho > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    "§e§lNº " + numero, "§7Bilhete: §a" + dinheiro(preco()),
                    tenho > 0 ? "§aVocê tem " + tenho + " bilhete(s) nesse número" : "", "§eClique pra comprar");
            bola.setAmount(Math.min(64, Math.max(1, tenho)));
            menu.inventario.setItem(i, bola);
        }
        if (pagina > 0) menu.inventario.setItem(SLOT_VOLTAR, icone(Material.ARROW, "§fPágina anterior"));
        if (pagina < paginas - 1) menu.inventario.setItem(SLOT_AVANCAR, icone(Material.ARROW, "§fPróxima página"));
        menu.inventario.setItem(SLOT_INFO, icone(Material.GOLD_BLOCK, "§6§lPote: §a" + dinheiro(pote), infoLinhas(jogador)));
        jogador.openInventory(menu.inventario);
    }

    private String[] infoLinhas(Player jogador) {
        List<String> l = new ArrayList<>();
        l.add("§7Sorteio todo dia às §f" + horario() + "§7.");
        l.add("§7O pote inteiro vai pra quem acertar; mais de um");
        l.add("§7ganhador divide. Sem ganhador, acumula.");
        int meus = apostas.getOrDefault(jogador.getUniqueId(), Map.of()).values().stream().mapToInt(Integer::intValue).sum();
        l.add("§7Seus bilhetes pro próximo: §f" + meus);
        if (!ultimoResultado.isEmpty()) l.add("§7Último: §f" + ultimoResultado);
        return l.toArray(String[]::new);
    }

    private void comprar(Player jogador, int numero) {
        int maximo = plugin.getConfig().getInt("minesena.maximo-bilhetes-por-jogador", 20);
        int meus = apostas.getOrDefault(jogador.getUniqueId(), Map.of()).values().stream().mapToInt(Integer::intValue).sum();
        if (maximo > 0 && meus >= maximo) {
            jogador.sendMessage("§cVocê já tem " + meus + " bilhetes pro próximo sorteio (máximo " + maximo + ").");
            return;
        }
        Economy econ = plugin.getEconomy();
        double preco = preco();
        if (econ == null || !econ.has(jogador, preco)) {
            jogador.sendMessage("§cSaldo insuficiente: o bilhete custa " + dinheiro(preco) + ".");
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        econ.withdrawPlayer(jogador, preco);
        pote += preco;
        apostas.computeIfAbsent(jogador.getUniqueId(), k -> new HashMap<>()).merge(numero, 1, Integer::sum);
        salvar();
        plugin.getPix().registrar(jogador.getUniqueId(), "Mine-sena", preco, false, "Bilhete nº " + numero);
        jogador.sendMessage("§a🎟 Bilhete da Mine-sena no número §e" + numero + "§a comprado por " + dinheiro(preco)
                + ". §7Pote agora: §f" + dinheiro(pote) + "§7. Sorteio às " + horario() + ".");
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    @EventHandler
    public void aoClicar(InventoryClickEvent evento) {
        if (!(evento.getInventory().getHolder() instanceof Menu menu)) return;
        evento.setCancelled(true);
        if (!(evento.getWhoClicked() instanceof Player jogador)) return;
        if (evento.getClickedInventory() != evento.getView().getTopInventory()) return;
        int slot = evento.getRawSlot();
        if (slot == SLOT_VOLTAR) abrir(jogador, menu.pagina - 1);
        else if (slot == SLOT_AVANCAR) abrir(jogador, menu.pagina + 1);
        else if (slot < POR_PAGINA) {
            int numero = menu.pagina * POR_PAGINA + slot + 1;
            if (numero > numeroMaximo()) return;
            comprar(jogador, numero);
            abrir(jogador, menu.pagina);
        }
    }

    @EventHandler
    public void aoArrastar(InventoryDragEvent evento) {
        if (evento.getInventory().getHolder() instanceof Menu) evento.setCancelled(true);
    }

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        String aviso = avisos.remove(evento.getPlayer().getUniqueId());
        if (aviso == null) return;
        salvar();
        Bukkit.getScheduler().runTaskLater(plugin, () -> evento.getPlayer().sendMessage(aviso), 40L);
    }

    // ---------------------------------------------------------------------
    // /minesena
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("sortear")) {
            if (!sender.hasPermission("cheque.admin")) {
                sender.sendMessage("§cSó a staff sorteia na hora.");
                return true;
            }
            int numero;
            try {
                numero = args.length > 1 ? Integer.parseInt(args[1]) : ThreadLocalRandom.current().nextInt(1, numeroMaximo() + 1);
            } catch (NumberFormatException e) {
                numero = -1;
            }
            if (numero < 1 || numero > numeroMaximo()) {
                sender.sendMessage("§cUso: §f/minesena sortear [1-" + numeroMaximo() + "] §7(sem número, sorteia de verdade)");
                return true;
            }
            plugin.getLogger().info(sender.getName() + " sorteou a Mine-sena na hora (nº " + numero + ").");
            sortear(numero);
            return true;
        }
        sender.sendMessage("§6§l🎟 Mine-sena");
        sender.sendMessage("§7Pote: §a" + dinheiro(pote) + " §7| sorteio todo dia às §f" + horario()
                + " §7| bilhete: §f" + dinheiro(preco()) + " §7(compre com o §6Bancário§7)");
        if (sender instanceof Player p) {
            Map<Integer, Integer> meus = apostas.getOrDefault(p.getUniqueId(), Map.of());
            if (meus.isEmpty()) sender.sendMessage("§7Você não tem bilhete pro próximo sorteio.");
            else {
                List<String> partes = new ArrayList<>();
                meus.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(e -> partes.add("§e" + e.getKey() + (e.getValue() > 1 ? "§7 (x" + e.getValue() + ")" : "")));
                sender.sendMessage("§7Seus números: " + String.join("§7, ", partes));
            }
        }
        if (!ultimoResultado.isEmpty()) sender.sendMessage("§7Último sorteio" + (ultimoSorteio != null ? " (" + ultimoSorteio + ")" : "")
                + ": §f" + ultimoResultado);
        return true;
    }

    // ---------------------------------------------------------------------

    private void enviarDiscord(String mensagem) {
        Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordPlugin");
        if (discord == null || !discord.isEnabled()) return;
        try {
            discord.getClass().getMethod("enviar", String.class, String.class).invoke(discord, "minesena", mensagem);
        } catch (ReflectiveOperationException e) {
            if (!avisouFalhaDiscord) {
                plugin.getLogger().warning("DiscordPlugin encontrado, mas não foi possível anunciar a Mine-sena: " + e.getMessage());
                avisouFalhaDiscord = true;
            }
        }
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        pote = yaml.getDouble("pote", 0);
        String data = yaml.getString("ultimo-sorteio");
        ultimoSorteio = data == null ? null : LocalDate.parse(data);
        ultimoNumero = yaml.getInt("ultimo-numero", 0);
        ultimoResultado = yaml.getString("ultimo-resultado", "");
        ConfigurationSection s = yaml.getConfigurationSection("apostas");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                Map<Integer, Integer> nums = new HashMap<>();
                ConfigurationSection n = s.getConfigurationSection(id);
                if (n != null) for (String k : n.getKeys(false)) nums.put(Integer.parseInt(k), n.getInt(k));
                apostas.put(UUID.fromString(id), nums);
            }
        }
        ConfigurationSection a = yaml.getConfigurationSection("avisos");
        if (a != null) for (String id : a.getKeys(false)) avisos.put(UUID.fromString(id), a.getString(id));
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("pote", pote);
        yaml.set("ultimo-sorteio", ultimoSorteio == null ? null : ultimoSorteio.toString());
        yaml.set("ultimo-numero", ultimoNumero);
        yaml.set("ultimo-resultado", ultimoResultado);
        apostas.forEach((id, nums) -> nums.forEach((n, q) -> yaml.set("apostas." + id + "." + n, q)));
        avisos.forEach((id, msg) -> yaml.set("avisos." + id, msg));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar minesena.yml", e);
        }
    }

    static ItemStack icone(Material material, String nome, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nome);
        List<String> linhas = new ArrayList<>();
        for (String l : lore) if (!l.isEmpty()) linhas.add(l);
        meta.setLore(linhas);
        item.setItemMeta(meta);
        return item;
    }

    private static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }
}
