package com.example.tesouros;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mapas do tesouro: o boss deixa um no chão quando morre e as minas dão um de vez em quando. O mapa
 * tem as coordenadas de um ponto do mundo superplano dos tesouros; quem cava o chão ali abre uma
 * câmara enterrada com guardiões em ondas e um baú que só destrava quando o último morre. O clã e os
 * convidados do dono do mapa podem entrar e ajudar, mas o tesouro é só de quem cavou. Quem morre na
 * câmara perde tudo.
 */
public class TesourosPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Integracoes integracoes;
    private Mapas mapas;
    private Loot loot;
    private Camaras camaras;
    private NamespacedKey chaveGuardiao;
    private final List<Material> camadas = new ArrayList<>();
    private final List<Integer> alturas = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lerCamadas();
        this.chaveGuardiao = new NamespacedKey(this, "guardiao");
        this.integracoes = new Integracoes(this);
        this.mapas = new Mapas(this);
        this.loot = new Loot(this);
        this.camaras = new Camaras(this);
        getServer().getPluginManager().registerEvents(camaras, this);
        getCommand("tesouro").setExecutor(this);
        getCommand("tesouro").setTabCompleter(this);
        getServer().getScheduler().runTaskTimer(this, camaras::tick, 20L, 20L);
        // o mundo pode ter sido carregado pelo Multiverse depois deste plugin
        getServer().getScheduler().runTask(this, () -> {
            World mundo = getMundo();
            if (mundo == null) {
                getLogger().warning("O mundo \"" + getNomeMundo() + "\" não existe. Crie com o configurar-mundos.ps1 do servidor de teste.");
                return;
            }
            prepararMundo(mundo);
        });
    }

    /** Borda e câmaras que ficaram abertas: quando liga, ou quando o mundo é criado depois. */
    public void prepararMundo(World mundo) {
        int borda = getConfig().getInt("coordenadas.raio-maximo", 1500) + 100;
        mundo.getWorldBorder().setCenter(0, 0);
        mundo.getWorldBorder().setSize(borda * 2);
        camaras.desfazerPendentes();
    }

    @Override
    public void onDisable() {
        if (camaras != null) camaras.encerrarTodas("§eO servidor vai reiniciar: a câmara desabou e você voltou pra superfície.");
    }

    private void lerCamadas() {
        camadas.clear();
        alturas.clear();
        for (String c : getConfig().getStringList("camadas")) {
            String[] partes = c.split(":");
            Material m = Material.matchMaterial(partes[0].trim());
            if (m == null || !m.isBlock()) {
                getLogger().warning("config.yml: camada com bloco desconhecido \"" + c + "\"");
                continue;
            }
            camadas.add(m);
            alturas.add(partes.length > 1 ? Integer.parseInt(partes[1].trim()) : 1);
        }
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    public Integracoes getIntegracoes() {
        return integracoes;
    }

    public Mapas getMapas() {
        return mapas;
    }

    public Loot getLoot() {
        return loot;
    }

    public NamespacedKey chaveGuardiao() {
        return chaveGuardiao;
    }

    public String getNomeMundo() {
        return getConfig().getString("mundo", "tesouros");
    }

    public World getMundo() {
        return Bukkit.getWorld(getNomeMundo());
    }

    /** O bloco que o superplano tem naquela altura. */
    public Material camadaEm(World mundo, int y) {
        int topo = mundo.getMinHeight();
        for (int i = 0; i < camadas.size(); i++) {
            topo += alturas.get(i);
            if (y < topo) return camadas.get(i);
        }
        return Material.AIR;
    }

    /** A altura do bloco de cima do superplano (o que se cava). */
    public int ySuperficie(World mundo) {
        int total = 0;
        for (int a : alturas) total += a;
        return mundo.getMinHeight() + total - 1;
    }

    public String nomeRaridade(String raridade) {
        return cores(getConfig().getString("raridades." + raridade + ".nome", raridade));
    }

    public String cores(String texto) {
        return ChatColor.translateAlternateColorCodes('&', texto == null ? "" : texto);
    }

    public String dinheiro(double valor) {
        return "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.0f", valor);
    }

    // ---------------------------------------------------------------------
    // /tesouro
    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "ajuda" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("tesouros.admin");
        switch (sub) {
            case "ir" -> ir(sender);
            case "convidar" -> convidar(sender, args, true);
            case "desconvidar" -> convidar(sender, args, false);
            case "sair" -> {
                if (sender instanceof Player p) camaras.sair(p);
            }
            case "dar" -> {
                if (admin) dar(sender, args);
                else ajuda(sender);
            }
            case "ativas" -> {
                if (admin) camaras.listar(sender);
                else ajuda(sender);
            }
            case "encerrar" -> {
                if (admin) camaras.encerrarAdmin(sender, args.length > 1 ? args[1] : null);
                else ajuda(sender);
            }
            case "loot" -> {
                if (admin) loot(sender, args);
                else ajuda(sender);
            }
            case "reload" -> {
                if (!admin) {
                    ajuda(sender);
                    break;
                }
                reloadConfig();
                lerCamadas();
                loot.carregar();
                sender.sendMessage("§aConfig e loot recarregados.");
            }
            default -> ajuda(sender);
        }
        return true;
    }

    private void ajuda(CommandSender sender) {
        sender.sendMessage("§6✦ Mapas do Tesouro");
        sender.sendMessage("§7O boss sempre deixa um mapa quando morre, e as minas dão um de vez em quando.");
        sender.sendMessage("§7Vá até o X do mapa no mundo dos tesouros, cave o chão e derrote os guardiões.");
        sender.sendMessage("§e/tesouro ir §7- vai pro mundo dos tesouros");
        sender.sendMessage("§e/tesouro convidar <jogador> §7- deixa um amigo entrar na sua câmara (o clã já pode)");
        sender.sendMessage("§e/tesouro desconvidar <jogador> §7- tira o convite");
        sender.sendMessage("§e/tesouro sair §7- sai da câmara depois que o baú destrava");
        sender.sendMessage("§cQuem morre na câmara perde tudo: o mapa, o tesouro e os itens.");
        if (sender.hasPermission("tesouros.admin")) {
            sender.sendMessage("§8Staff: §7/tesouro dar <jogador> <raridade> [x z] · ativas · encerrar [id|todas]"
                    + " · loot <raridade> · loot add <raridade> <peso> [min max] · loot remover <raridade> <chave> · reload");
        }
    }

    private void ir(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSó jogadores vão pro mundo dos tesouros.");
            return;
        }
        World mundo = getMundo();
        if (mundo == null) {
            p.sendMessage("§cO mundo dos tesouros não existe no momento.");
            return;
        }
        if (camaras.camaraDe(p.getLocation()) != null) {
            p.sendMessage("§cVocê está numa câmara. Use §f/tesouro sair§c depois que o baú destravar.");
            return;
        }
        // como COMMAND, pra o CombatePlugin barrar quem está em combate
        p.teleport(mundo.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.COMMAND);
        p.sendMessage("§6✦ §eBem-vindo ao mundo dos tesouros! §7Ande até o X do seu mapa e cave o chão.");
    }

    private void convidar(CommandSender sender, String[] args, boolean convidar) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            List<String> nomes = new ArrayList<>();
            for (UUID u : camaras.convidados(p.getUniqueId())) nomes.add(Bukkit.getOfflinePlayer(u).getName());
            p.sendMessage("§7Uso: §f/tesouro " + (convidar ? "convidar" : "desconvidar") + " <jogador>§7. Convidados: §f"
                    + (nomes.isEmpty() ? "ninguém" : String.join(", ", nomes)) + "§7 (seu clã já pode entrar).");
            return;
        }
        Player alvo = Bukkit.getPlayerExact(args[1]);
        if (alvo == null || alvo.equals(p)) {
            p.sendMessage("§cJogador não encontrado (precisa estar online).");
            return;
        }
        if (convidar) {
            camaras.convidados(p.getUniqueId()).add(alvo.getUniqueId());
            p.sendMessage("§a" + alvo.getName() + " pode entrar nas suas câmaras até você sair do servidor.");
            alvo.sendMessage("§6✦ §e" + p.getName() + " §7te convidou pra câmara do tesouro dele. Entre pelo buraco que ele cavar.");
        } else {
            camaras.convidados(p.getUniqueId()).remove(alvo.getUniqueId());
            p.sendMessage("§e" + alvo.getName() + " não está mais convidado.");
        }
    }

    private void dar(CommandSender sender, String[] args) {
        if (args.length < 3 || !Mapas.RARIDADES.contains(args[2].toLowerCase(Locale.ROOT))) {
            sender.sendMessage("§cUso: /tesouro dar <jogador> <" + String.join("|", Mapas.RARIDADES) + "> [x z]");
            return;
        }
        Player alvo = Bukkit.getPlayerExact(args[1]);
        if (alvo == null) {
            sender.sendMessage("§cJogador não encontrado.");
            return;
        }
        String raridade = args[2].toLowerCase(Locale.ROOT);
        ItemStack mapa;
        try {
            mapa = args.length >= 5 ? mapas.criar(raridade, Integer.parseInt(args[3]), Integer.parseInt(args[4])) : mapas.criar(raridade);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cCoordenadas inválidas.");
            return;
        }
        Map<Integer, ItemStack> sobra = alvo.getInventory().addItem(mapa);
        sobra.values().forEach(i -> alvo.getWorld().dropItem(alvo.getLocation(), i));
        Mapas.Mapa lido = mapas.ler(mapa);
        sender.sendMessage("§aMapa " + nomeRaridade(raridade) + " §adado a " + alvo.getName() + " §7(X " + lido.x() + ", Z " + lido.z() + ")§a.");
    }

    private void loot(CommandSender sender, String[] args) {
        if (args.length >= 2 && Mapas.RARIDADES.contains(args[1].toLowerCase(Locale.ROOT))) {
            String r = args[1].toLowerCase(Locale.ROOT);
            sender.sendMessage("§6✦ Loot " + nomeRaridade(r) + "§6:");
            loot.descrever(r).forEach(sender::sendMessage);
            return;
        }
        if (args.length >= 4 && args[1].equalsIgnoreCase("add") && sender instanceof Player p) {
            String r = args[2].toLowerCase(Locale.ROOT);
            ItemStack mao = p.getInventory().getItemInMainHand();
            if (!Mapas.RARIDADES.contains(r) || mao.getType().isAir()) {
                p.sendMessage("§cUso: /tesouro loot add <raridade> <peso> [min max], com o item na mão.");
                return;
            }
            try {
                double peso = Double.parseDouble(args[3]);
                int min = args.length >= 5 ? Integer.parseInt(args[4]) : mao.getAmount();
                int max = args.length >= 6 ? Integer.parseInt(args[5]) : min;
                String chave = loot.adicionar(r, mao, peso, min, max);
                p.sendMessage("§aItem acrescentado ao loot " + nomeRaridade(r) + " §acomo §f" + chave + "§a.");
            } catch (NumberFormatException e) {
                p.sendMessage("§cNúmero inválido.");
            }
            return;
        }
        if (args.length >= 4 && args[1].equalsIgnoreCase("remover")) {
            boolean ok = loot.remover(args[2].toLowerCase(Locale.ROOT), args[3]);
            sender.sendMessage(ok ? "§aItem removido do loot." : "§cNão achei esse item (veja /tesouro loot <raridade>).");
            return;
        }
        sender.sendMessage("§cUso: /tesouro loot <raridade> · loot add <raridade> <peso> [min max] · loot remover <raridade> <chave>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        boolean admin = sender.hasPermission("tesouros.admin");
        if (args.length == 1) {
            opcoes.addAll(List.of("ir", "convidar", "desconvidar", "sair", "ajuda"));
            if (admin) opcoes.addAll(List.of("dar", "ativas", "encerrar", "loot", "reload"));
        } else if (args.length == 2 && List.of("convidar", "desconvidar", "dar").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> opcoes.add(p.getName()));
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            opcoes.addAll(Mapas.RARIDADES);
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("loot")) {
            opcoes.addAll(Mapas.RARIDADES);
            opcoes.addAll(List.of("add", "remover"));
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("loot")) {
            opcoes.addAll(Mapas.RARIDADES);
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("encerrar")) {
            opcoes.add("todas");
            opcoes.addAll(camaras.ids());
        }
        String prefixo = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefixo));
        return opcoes;
    }
}
