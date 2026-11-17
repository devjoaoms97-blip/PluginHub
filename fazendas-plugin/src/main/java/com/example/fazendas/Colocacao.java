package com.example.fazendas;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Colocar uma fazenda: clique direito com a Escritura no chão do terreno. O primeiro clique mostra o
 * contorno da área 7x7 (verde se pode, vermelho com o motivo se não pode); o segundo clique no mesmo
 * bloco, enquanto a prévia vale, constrói. A área precisa estar dentro de um terreno onde o jogador
 * constrói, com o chão plano e firme (sem baú e afins) e livre por cima (ar, grama, flor).
 */
final class Colocacao implements Listener {

    private record Previa(String mundo, int x, int y, int z, String tipo, long expira, String erro) {
        boolean mesmoLugar(Previa o) {
            return o != null && mundo.equals(o.mundo) && x == o.x && y == o.y && z == o.z && tipo.equals(o.tipo);
        }
    }

    private static final Particle.DustOptions VERDE = new Particle.DustOptions(Color.fromRGB(80, 220, 80), 1.2f);
    private static final Particle.DustOptions VERMELHO = new Particle.DustOptions(Color.fromRGB(230, 60, 60), 1.2f);

    private final FazendasPlugin plugin;
    private final Map<UUID, Previa> previas = new HashMap<>();

    Colocacao(FazendasPlugin plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                desenhar();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private int alturaLivre() {
        return Math.max(2, plugin.getConfig().getInt("altura-livre", 3));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void aoClicar(PlayerInteractEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = evento.getItem();
        FazendasPlugin.Tipo tipo = plugin.tipoDaEscritura(item);
        if (tipo == null) return;
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK && evento.getAction() != Action.RIGHT_CLICK_AIR) return;
        evento.setCancelled(true);
        Player p = evento.getPlayer();
        Block chao = evento.getClickedBlock();
        if (chao == null) {
            p.sendMessage("§eMire no chão do seu terreno, no bloco que vai ser o centro da fazenda.");
            return;
        }
        int x = chao.getX(), y = chao.getY() + 1, z = chao.getZ();
        String erro = validar(p, chao.getWorld(), x, y, z, tipo);
        long agora = System.currentTimeMillis();
        Previa nova = new Previa(chao.getWorld().getName(), x, y, z, tipo.id(), agora + plugin.getConfig().getLong("previa-segundos", 30) * 1000, erro);
        Previa anterior = previas.get(p.getUniqueId());
        if (erro == null && nova.mesmoLugar(anterior) && anterior.expira() > agora && anterior.erro() == null) {
            previas.remove(p.getUniqueId());
            construir(p, item, tipo, chao.getWorld(), x, y, z);
            return;
        }
        previas.put(p.getUniqueId(), nova);
        if (erro == null) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
            p.sendMessage("§aÁrea livre! §7Clique de novo no mesmo bloco pra construir: " + tipo.nome() + "§7.");
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            p.sendMessage("§c" + erro);
        }
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        previas.remove(evento.getPlayer().getUniqueId());
    }

    /** O motivo de não poder construir aqui, ou null se pode. (x, y, z): centro, na altura dos pés do NPC. */
    private String validar(Player p, World w, int x, int y, int z, FazendasPlugin.Tipo tipo) {
        if (!plugin.getConfig().getStringList("mundos").contains(w.getName())) {
            return "Fazenda só pode ser colocada no seu terreno (/terreno home).";
        }
        if (!plugin.getRankup().podeUsar(p.getUniqueId(), tipo.rank())) {
            return "A fazenda " + org.bukkit.ChatColor.stripColor(tipo.nome()) + " libera no rank " + tipo.rank() + ". Guarde a Escritura até lá, ou negocie.";
        }
        FazendaManager fazendas = plugin.getFazendas();
        if (fazendas.sobrepoe(w.getName(), x, z)) return "Essa área encosta em outra fazenda.";
        TerrenosIntegration terrenos = plugin.getTerrenos();
        String terreno = terrenos.terrenoEm(new Location(w, x, y, z));
        if (terrenos.disponivel()) {
            if (terreno == null) return "A fazenda precisa ficar dentro de um terreno.";
            if (fazendas.noTerreno(terreno) >= plugin.limitePorTerreno()) {
                return "Esse terreno já tem o máximo de fazendas (" + plugin.limitePorTerreno() + "). Recolha uma pra pôr outra.";
            }
        } else if (fazendas.doDono(p.getUniqueId()) >= plugin.limitePorTerreno()) {
            return "Você já tem o máximo de fazendas (" + plugin.limitePorTerreno() + ").";
        }
        int r = Fazenda.RAIO;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Block chao = w.getBlockAt(x + dx, y - 1, z + dz);
                if (terrenos.disponivel()) {
                    if (!Objects.equals(terreno, terrenos.terrenoEm(chao.getLocation()))) return "A área 7x7 sai do terreno: chegue mais pro meio.";
                    if (!terrenos.podeConstruir(p, chao.getLocation())) return "Você não pode construir em toda essa área.";
                }
                if (!chao.getType().isSolid() || !chao.getType().isOccluding() || chao.getState(false) instanceof TileState) {
                    return "O chão precisa ser plano e firme, sem buracos, baús ou blocos especiais.";
                }
                for (int dy = 0; dy < alturaLivre(); dy++) {
                    Block b = w.getBlockAt(x + dx, y + dy, z + dz);
                    if (b.isEmpty()) continue;
                    if (!b.isReplaceable() || b.getState(false) instanceof TileState || b.isLiquid()) {
                        return "A área precisa estar livre: tire os blocos de cima do chão (7x7, " + alturaLivre() + " de altura).";
                    }
                }
            }
        }
        return null;
    }

    private void construir(Player p, ItemStack item, FazendasPlugin.Tipo tipo, World w, int x, int y, int z) {
        // tira a escritura da mão antes (o item pode ter mudado entre os cliques)
        ItemStack naMao = p.getInventory().getItemInMainHand();
        if (plugin.tipoDaEscritura(naMao) != tipo) return;
        int nivelProd = plugin.nivel(naMao, FazendasPlugin.PROD);
        int nivelCofre = plugin.nivel(naMao, FazendasPlugin.COFRE);
        int nivelDespensa = plugin.nivel(naMao, FazendasPlugin.DESPENSA);
        naMao.setAmount(naMao.getAmount() - 1);
        // a frente (a abertura com o NPC) fica do lado de quem colocou: yaw 0 = sul (+z), -90 = leste (+x)
        Location olho = p.getLocation();
        double ox = olho.getX() - (x + 0.5), oz = olho.getZ() - (z + 0.5);
        float yaw = Math.abs(ox) > Math.abs(oz) ? (ox > 0 ? -90 : 90) : (oz >= 0 ? 0 : 180);
        Fazenda f = new Fazenda(UUID.randomUUID().toString(), w.getName(), x, y, z, tipo.id(), p.getUniqueId(), yaw, alturaLivre());
        f.nivelProd = nivelProd;
        f.nivelCofre = nivelCofre;
        f.nivelDespensa = nivelDespensa;
        f.ultima = System.currentTimeMillis();
        Construcao.construir(f, tipo);
        tirarDeDentro(w, x, y, z);
        plugin.getFazendas().adicionar(f);
        w.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 0.5, z + 0.5, 60, 3, 1, 3, 0);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 0.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.sendMessage("§aFazenda construída: " + tipo.nome() + "§a! §7Clique no fazendeiro pra abastecer com "
                + plugin.getInsumos().get(tipo.insumo()).nome() + "§7.");
    }

    /**
     * Quem estava dentro da área (quase sempre quem colocou, que clica de perto) vai pra fora da cerca,
     * do lado em que estava, olhando pra fazenda.
     */
    private void tirarDeDentro(World w, int x, int y, int z) {
        double cx = x + 0.5, cz = z + 0.5;
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(new Location(w, cx, y + 1, cz), Fazenda.RAIO + 0.5, 2.5, Fazenda.RAIO + 0.5)) {
            if (!(e instanceof Player p)) continue;
            Location l = p.getLocation();
            double dx = l.getX() - cx, dz = l.getZ() - cz;
            if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) dz = 1;
            // sai pelo lado mais perto (norte, sul, leste ou oeste), a 1 bloco da cerca
            double fora = Fazenda.RAIO + 1.5;
            double nx, nz;
            if (Math.abs(dx) >= Math.abs(dz)) {
                nx = cx + Math.signum(dx) * fora;
                nz = Math.max(cz - Fazenda.RAIO, Math.min(cz + Fazenda.RAIO, l.getZ()));
            } else {
                nx = Math.max(cx - Fazenda.RAIO, Math.min(cx + Fazenda.RAIO, l.getX()));
                nz = cz + Math.signum(dz) * fora;
            }
            int bx = (int) Math.floor(nx), bz = (int) Math.floor(nz);
            boolean livre = w.getBlockAt(bx, y, bz).isPassable() && w.getBlockAt(bx, y + 1, bz).isPassable();
            int ny = livre ? y : w.getHighestBlockYAt(bx, bz) + 1;
            Location destino = new Location(w, nx, ny, nz);
            destino.setDirection(new org.bukkit.util.Vector(cx - nx, 0, cz - nz));
            p.teleport(destino);
        }
    }

    /** O contorno de cada prévia, só pro dono dela, enquanto ele segura a Escritura. */
    private void desenhar() {
        long agora = System.currentTimeMillis();
        previas.entrySet().removeIf(e -> {
            Player p = plugin.getServer().getPlayer(e.getKey());
            Previa v = e.getValue();
            if (p == null || v.expira() < agora || plugin.tipoDaEscritura(p.getInventory().getItemInMainHand()) == null
                    || !p.getWorld().getName().equals(v.mundo())) return true;
            Particle.DustOptions cor = v.erro() == null ? VERDE : VERMELHO;
            double x0 = v.x() - Fazenda.RAIO, x1 = v.x() + Fazenda.RAIO + 1;
            double z0 = v.z() - Fazenda.RAIO, z1 = v.z() + Fazenda.RAIO + 1;
            double y = v.y() + 0.1;
            for (double t = 0; t <= 7; t += 0.5) {
                ponto(p, x0 + t, y, z0, cor);
                ponto(p, x0 + t, y, z1, cor);
                ponto(p, x0, y, z0 + t, cor);
                ponto(p, x1, y, z0 + t, cor);
            }
            for (double h = 0; h <= alturaLivre(); h += 0.5) {
                ponto(p, x0, y + h, z0, cor);
                ponto(p, x1, y + h, z0, cor);
                ponto(p, x0, y + h, z1, cor);
                ponto(p, x1, y + h, z1, cor);
            }
            // o centro (onde fica o fazendeiro)
            ponto(p, v.x() + 0.5, y + 0.2, v.z() + 0.5, cor);
            return false;
        });
    }

    private static void ponto(Player p, double x, double y, double z, Particle.DustOptions cor) {
        p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, cor);
    }
}
