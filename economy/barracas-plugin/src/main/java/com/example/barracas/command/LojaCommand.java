package com.example.barracas.command;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import com.example.barracas.model.Oferta;
import com.example.barracas.util.Formato;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * As lojas das barracas alugadas, como destino público:
 * <ul>
 *   <li>{@code /loja} lista as barracas alugadas (clicar no nome leva até ela);</li>
 *   <li>{@code /loja <jogador>} leva até a frente do NPC da barraca dele, olhando pro vendedor;</li>
 *   <li>{@code /loja anunciar [mensagem]} (dono) anuncia a barraca no chat, com os itens à venda ao
 *       passar o mouse e um clique que leva até ela. Um anúncio a cada {@code loja.anuncio-intervalo-minutos}, por jogador.</li>
 * </ul>
 * O intervalo do anúncio vale pra todo mundo, staff também.
 * O destino existe só enquanto a barraca está alugada: quando o aluguel acaba, o {@code /loja} do dono
 * deixa de levar a ela. O teleporte é na hora, como o {@code /mina}, e o CombatePlugin barra o
 * {@code /loja} de quem está em combate (está em {@code em-combate.comandos}).
 */
public class LojaCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private final BarracasPlugin plugin;
    private final Map<UUID, Long> ultimoAnuncio = new HashMap<>();

    public LojaCommand(BarracasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage("§cSó jogadores.");
            return true;
        }
        if (args.length == 0) {
            listar(jogador);
        } else if (args[0].equalsIgnoreCase("anunciar")) {
            anunciar(jogador, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
        } else {
            ir(jogador, args[0]);
        }
        return true;
    }

    /** As barracas alugadas agora, em ordem de dono. */
    private List<Barraca> alugadas() {
        long agora = System.currentTimeMillis();
        List<Barraca> lista = new ArrayList<>();
        for (Barraca b : plugin.getBarracaManager().getBarracas().values()) {
            if (b.estaAlugada(agora)) lista.add(b);
        }
        lista.sort(Comparator.comparing(b -> b.getNomeDono() == null ? "" : b.getNomeDono().toLowerCase(Locale.ROOT)));
        return lista;
    }

    private void listar(Player jogador) {
        List<Barraca> lista = alugadas();
        if (lista.isEmpty()) {
            jogador.sendMessage("§7Nenhuma barraca alugada agora. Alugue a sua: §f/barraca lista");
            return;
        }
        jogador.sendMessage("§6§lLojas abertas §7(clique pra ir):");
        for (Barraca b : lista) {
            int vendas = (int) b.getOfertas().stream().filter(o -> o.getTipo() == Oferta.Tipo.VENDA && o.getEstoque() > 0).count();
            jogador.sendMessage(LEGADO.deserialize("  §e" + b.getNomeDono() + " §7- " + (vendas == 0 ? "sem itens à venda" : vendas + " item(ns) à venda"))
                    .clickEvent(ClickEvent.runCommand("/loja " + b.getNomeDono()))
                    .hoverEvent(HoverEvent.showText(itens(b))));
        }
    }

    private void ir(Player jogador, String nome) {
        Barraca barraca = null;
        long agora = System.currentTimeMillis();
        for (Barraca b : plugin.getBarracaManager().getBarracas().values()) {
            if (b.estaAlugada(agora) && b.getNomeDono() != null && b.getNomeDono().equalsIgnoreCase(nome)) {
                barraca = b;
                break;
            }
        }
        if (barraca == null) {
            jogador.sendMessage("§c" + nome + " não tem barraca alugada agora. Veja as abertas em §f/loja§c.");
            return;
        }
        Location destino = frenteDoNpc(barraca);
        if (destino == null) {
            jogador.sendMessage("§cA barraca de " + barraca.getNomeDono() + " ainda não tem vendedor marcado. Avise a staff.");
            return;
        }
        jogador.teleport(destino, PlayerTeleportEvent.TeleportCause.COMMAND);
        jogador.sendMessage("§aVocê está na loja de §f" + barraca.getNomeDono() + "§a. Clique no vendedor pra comprar.");
    }

    /**
     * Onde o visitante chega: na frente do NPC (pra onde ele olha), no primeiro lugar livre fora da
     * área da barraca, virado pro vendedor. Sem lugar livre, logo à frente dele.
     */
    private Location frenteDoNpc(Barraca barraca) {
        BlocoPos npc = barraca.getNpc();
        World mundo = Bukkit.getWorld(barraca.getMundo());
        if (npc == null || mundo == null) return null;
        double rad = Math.toRadians(barraca.getNpcYaw());
        int dx = (int) Math.round(-Math.sin(rad)), dz = (int) Math.round(Math.cos(rad));
        float olhando = Location.normalizeYaw(barraca.getNpcYaw() + 180);
        for (int passo = 1; passo <= 5; passo++) {
            int x = npc.x() + dx * passo, z = npc.z() + dz * passo;
            if (barraca.contem(x, npc.y(), z)) continue;
            for (int y = npc.y() + 1; y >= npc.y() - 2; y--) {
                Block chao = mundo.getBlockAt(x, y - 1, z);
                if (chao.getType().isSolid() && mundo.getBlockAt(x, y, z).isPassable() && mundo.getBlockAt(x, y + 1, z).isPassable()
                        && chao.getType() != Material.LAVA) {
                    return new Location(mundo, x + 0.5, y, z + 0.5, olhando, 0);
                }
            }
        }
        return new Location(mundo, npc.x() + dx + 0.5, npc.y(), npc.z() + dz + 0.5, olhando, 0);
    }

    private void anunciar(Player jogador, String mensagem) {
        Barraca barraca = plugin.getBarracaManager().getBarracaDe(jogador.getUniqueId());
        if (barraca == null || !barraca.estaAlugada(System.currentTimeMillis())) {
            jogador.sendMessage("§cVocê não tem barraca alugada. Alugue uma: §f/barraca lista");
            return;
        }
        if (barraca.getOfertas().stream().noneMatch(o -> o.getTipo() == Oferta.Tipo.VENDA && o.getEstoque() > 0)) {
            jogador.sendMessage("§cSua loja não tem nada à venda. Anuncie um item com §f/barraca vender <quantidade> <preço>§c.");
            return;
        }
        long intervalo = (long) (plugin.getConfig().getDouble("loja.anuncio-intervalo-minutos", 10) * 60_000);
        long falta = ultimoAnuncio.getOrDefault(jogador.getUniqueId(), 0L) + intervalo - System.currentTimeMillis();
        if (falta > 0) { // vale pra staff também: um op anunciando sem parar inundava o chat
            jogador.sendMessage("§cVocê pode anunciar de novo em §f" + Formato.tempo(falta) + "§c.");
            return;
        }
        int maximo = plugin.getConfig().getInt("loja.anuncio-max-caracteres", 60);
        if (mensagem != null && mensagem.length() > maximo) mensagem = mensagem.substring(0, maximo);
        ultimoAnuncio.put(jogador.getUniqueId(), System.currentTimeMillis());

        Component botao = LEGADO.deserialize("§a§l[VISITAR]")
                .clickEvent(ClickEvent.runCommand("/loja " + jogador.getName()))
                .hoverEvent(HoverEvent.showText(itens(barraca)));
        Component texto = LEGADO.deserialize("§6[Loja] §e" + jogador.getName() + "§6: "
                + (mensagem != null ? "§f" + mensagem.replace('§', ' ') : "§fpassa na minha barraca!") + " ");
        Bukkit.broadcast(texto.append(botao)
                .append(LEGADO.deserialize(" §8(passe o mouse pra ver os itens, ou §7/loja " + jogador.getName() + "§8)")));
    }

    /** O texto do mouse: os anúncios de venda com estoque (e os de compra), com preço e quantidade. */
    private Component itens(Barraca barraca) {
        StringBuilder sb = new StringBuilder("§6Loja de " + barraca.getNomeDono());
        int mostrados = 0;
        for (Oferta o : barraca.getOfertas()) {
            if (o.getTipo() == Oferta.Tipo.VENDA && o.getEstoque() <= 0) continue;
            sb.append("\n").append(o.getTipo() == Oferta.Tipo.VENDA ? "§aVende " : "§bCompra ")
                    .append("§f").append(o.getQuantidade()).append("x ").append(Formato.nomeItem(o.getModelo()))
                    .append(" §7por §f").append(Formato.valor(o.getPreco()));
            if (o.getTipo() == Oferta.Tipo.VENDA) sb.append(" §8(estoque ").append(o.getEstoque()).append(")");
            mostrados++;
        }
        if (mostrados == 0) sb.append("\n§7Nada à venda agora");
        sb.append("\n\n§eClique pra ir até a barraca");
        return LEGADO.deserialize(sb.toString());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opcoes = new ArrayList<>();
        if (args.length == 1) {
            opcoes.add("anunciar");
            for (Barraca b : alugadas()) opcoes.add(b.getNomeDono());
        }
        String digitado = args[args.length - 1].toLowerCase(Locale.ROOT);
        opcoes.removeIf(o -> o == null || !o.toLowerCase(Locale.ROOT).startsWith(digitado));
        return opcoes;
    }
}
