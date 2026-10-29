package com.example.tesouros;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uma câmara aberta: a sala enterrada embaixo do X do mapa, os guardiões em ondas e o baú, que só
 * destrava quando o último guardião morre. É o InventoryHolder do baú, que só o dono do mapa abre.
 * <p>
 * A sala é uma caixa de {@code (2 x raio + 3)} de lado: o interior ({@code raio} pra cada lado do X,
 * {@code altura} de alto, o chão {@code profundidade} abaixo da superfície) e uma casca de 1 bloco.
 * Quando fecha, cada bloco da caixa volta pra camada do superplano daquela altura.
 */
public class Camara implements InventoryHolder {

    public enum Estado { LUTANDO, LIBERADO }

    private static final Material[] PAREDE = {Material.STONE_BRICKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS};

    final String id;
    final UUID dono;
    final String nomeDono;
    final Mapas.Mapa mapa;
    final World mundo;
    final int x, z, ySuperficie, yChao, raio, altura;
    final long inicio = System.currentTimeMillis();

    Estado estado = Estado.LUTANDO;
    /** Quem já entrou (e pode sair pelo /tesouro sair depois de liberada). */
    final Set<UUID> participantes = new LinkedHashSet<>();
    final Set<UUID> guardioes = new HashSet<>();
    int onda = -1; // índice da onda atual; ondas.size() = o chefe
    int totalDaOnda;
    long proximaOndaEm;
    long liberadaEm;
    BossBar barra;
    Inventory bau;
    Loot.Premio premio;
    boolean coinsEntregues;

    private final TesourosPlugin plugin;

    Camara(TesourosPlugin plugin, String id, UUID dono, String nomeDono, Mapas.Mapa mapa, World mundo, int ySuperficie) {
        this.plugin = plugin;
        this.id = id;
        this.dono = dono;
        this.nomeDono = nomeDono;
        this.mapa = mapa;
        this.mundo = mundo;
        this.x = mapa.x();
        this.z = mapa.z();
        this.ySuperficie = ySuperficie;
        this.raio = Math.max(3, plugin.getConfig().getInt("camara.raio", 7));
        this.altura = Math.max(3, plugin.getConfig().getInt("camara.altura", 6));
        this.yChao = ySuperficie - Math.max(altura + 3, plugin.getConfig().getInt("camara.profundidade", 18));
    }

    @Override
    public Inventory getInventory() {
        return bau;
    }

    // ---------------------------------------------------------------------
    // Geometria
    // ---------------------------------------------------------------------

    int minX() { return x - raio - 1; }
    int maxX() { return x + raio + 1; }
    int minY() { return yChao - 1; }
    int maxY() { return yChao + altura; }
    int minZ() { return z - raio - 1; }
    int maxZ() { return z + raio + 1; }

    /** Dentro da caixa (casca inclusive). */
    boolean contem(Location l) {
        return l.getWorld() != null && l.getWorld().equals(mundo)
                && l.getBlockX() >= minX() && l.getBlockX() <= maxX()
                && l.getBlockY() >= minY() && l.getBlockY() <= maxY()
                && l.getBlockZ() >= minZ() && l.getBlockZ() <= maxZ();
    }

    /** A caixa encosta na de outra câmara (com folga pra entrada)? */
    boolean perto(int outroX, int outroZ) {
        int distancia = 2 * (raio + 2) + 2;
        return Math.abs(outroX - x) <= distancia && Math.abs(outroZ - z) <= distancia;
    }

    Block blocoBau() {
        return mundo.getBlockAt(x, yChao, z);
    }

    Block buraco() {
        return mundo.getBlockAt(x, ySuperficie, z);
    }

    /** Onde quem entra aparece: encostado na parede norte, olhando pro baú. */
    Location chegada() {
        return new Location(mundo, x + 0.5, yChao, z - raio + 1.5, 0f, 10f);
    }

    /** Em cima do X, na superfície. */
    Location superficie() {
        return new Location(mundo, x + 0.5, ySuperficie + 1, z + 0.5);
    }

    /** Pra onde vai quem sai: ao lado do X, pra não cair de novo no buraco. */
    Location saida() {
        return new Location(mundo, x + 2.5, ySuperficie + 1, z + 0.5, 90f, 0f);
    }

    // ---------------------------------------------------------------------
    // Construir e desfazer
    // ---------------------------------------------------------------------

    void construir() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int bx = minX(); bx <= maxX(); bx++) {
            for (int bz = minZ(); bz <= maxZ(); bz++) {
                for (int by = minY(); by <= maxY(); by++) {
                    boolean casca = bx == minX() || bx == maxX() || bz == minZ() || bz == maxZ() || by == minY() || by == maxY();
                    Material m;
                    if (!casca) m = Material.AIR;
                    else if (by == minY()) m = (bx + bz) % 4 == 0 ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE;
                    else if (by == maxY()) m = (bx - x) % 4 == 0 && (bz - z) % 4 == 0 ? Material.SEA_LANTERN : Material.DEEPSLATE_BRICKS;
                    else m = PAREDE[r.nextInt(PAREDE.length)];
                    mundo.getBlockAt(bx, by, bz).setType(m, false);
                }
            }
        }
        // pilares nos cantos, com lanterna em cima
        int c = raio - 1;
        for (int[] canto : new int[][]{{-c, -c}, {-c, c}, {c, -c}, {c, c}}) {
            for (int by = yChao; by < yChao + altura; by++) {
                mundo.getBlockAt(x + canto[0], by, z + canto[1]).setType(Material.CHISELED_STONE_BRICKS, false);
            }
            mundo.getBlockAt(x + canto[0], yChao, z + canto[1]).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        for (int[] lado : new int[][]{{-c + 1, -c}, {c - 1, c}, {-c, c - 1}, {c, -c + 1}}) {
            mundo.getBlockAt(x + lado[0], yChao, z + lado[1]).setType(Material.SOUL_LANTERN, false);
        }
        // o baú no centro, sobre ouro, virado pra chegada
        Block bau = blocoBau();
        mundo.getBlockAt(x, yChao - 1, z).setType(Material.GILDED_BLACKSTONE, false);
        bau.setType(Material.CHEST, false);
        if (bau.getBlockData() instanceof Directional d) {
            d.setFacing(BlockFace.NORTH);
            bau.setBlockData(d, false);
        }
        buraco().setType(Material.AIR, false);
    }

    void desfazer() {
        // o baú primeiro, pra não derrubar o que tem nele
        if (blocoBau().getState() instanceof org.bukkit.block.Chest chest) chest.getBlockInventory().clear();
        for (int bx = minX(); bx <= maxX(); bx++) {
            for (int bz = minZ(); bz <= maxZ(); bz++) {
                for (int by = minY(); by <= maxY(); by++) {
                    mundo.getBlockAt(bx, by, bz).setType(plugin.camadaEm(mundo, by), false);
                }
            }
        }
        buraco().setType(plugin.camadaEm(mundo, ySuperficie), false);
    }

    // ---------------------------------------------------------------------
    // Guardiões
    // ---------------------------------------------------------------------

    private ConfigurationSection config() {
        return plugin.getConfig().getConfigurationSection("raridades." + mapa.raridade());
    }

    List<String> ondas() {
        ConfigurationSection c = config();
        return c == null ? List.of("ZOMBIE:4") : c.getStringList("ondas");
    }

    boolean temChefe() {
        ConfigurationSection c = config();
        return c != null && c.getString("chefe.tipo", "").length() > 0;
    }

    /** Solta a próxima onda (ou o chefe). false se não tem mais nada: o baú destrava. */
    boolean proximaOnda() {
        onda++;
        guardioes.clear();
        List<String> ondas = ondas();
        ConfigurationSection c = config();
        double vida = c == null ? 1 : c.getDouble("vida", 1);
        double dano = c == null ? 1 : c.getDouble("dano", 1);
        if (onda < ondas.size()) {
            for (String parte : ondas.get(onda).split(",")) {
                String[] tq = parte.trim().split(":");
                EntityType tipo = tipo(tq[0]);
                if (tipo == null) continue;
                int qtd = tq.length > 1 ? Integer.parseInt(tq[1].trim()) : 1;
                for (int i = 0; i < qtd; i++) soltar(tipo, vida, dano, null, 0);
            }
        } else if (onda == ondas.size() && temChefe()) {
            EntityType tipo = tipo(c.getString("chefe.tipo"));
            if (tipo != null) soltar(tipo, 1, dano, plugin.cores(c.getString("chefe.nome", "&cGuardião")), c.getDouble("chefe.vida", 100));
        } else {
            return false;
        }
        totalDaOnda = Math.max(1, guardioes.size());
        if (guardioes.isEmpty()) return proximaOnda(); // onda vazia (tipo errado no config)
        return true;
    }

    int totalDeOndas() {
        return ondas().size() + (temChefe() ? 1 : 0);
    }

    private EntityType tipo(String nome) {
        try {
            EntityType t = EntityType.valueOf(nome.trim().toUpperCase(Locale.ROOT));
            if (t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass())) return t;
        } catch (IllegalArgumentException ignorado) {
        }
        plugin.getLogger().warning("config.yml: mob desconhecido \"" + nome + "\" nas ondas de " + mapa.raridade());
        return null;
    }

    @SuppressWarnings("unchecked")
    private void soltar(EntityType tipo, double multVida, double multDano, String nomeChefe, double vidaChefe) {
        Location onde = lugarLivre();
        NamespacedKey chave = plugin.chaveGuardiao();
        Entity e = mundo.spawn(onde, (Class<Entity>) tipo.getEntityClass(), ent -> {
            ent.setPersistent(false);
            ent.getPersistentDataContainer().set(chave, PersistentDataType.STRING, id);
            if (!(ent instanceof LivingEntity vivo)) return;
            vivo.setRemoveWhenFarAway(false);
            vivo.setCanPickupItems(false);
            if (vivo instanceof Raider raider) raider.setCanJoinRaid(false);
            AttributeInstance vida = vivo.getAttribute(Attribute.MAX_HEALTH);
            if (vida != null) {
                vida.setBaseValue(nomeChefe != null ? vidaChefe : vida.getBaseValue() * multVida);
                vivo.setHealth(vida.getBaseValue());
            }
            AttributeInstance ataque = vivo.getAttribute(Attribute.ATTACK_DAMAGE);
            if (ataque != null) ataque.setBaseValue(ataque.getBaseValue() * multDano);
            if (nomeChefe != null) {
                vivo.setCustomName(nomeChefe);
                vivo.setCustomNameVisible(true);
            }
        });
        guardioes.add(e.getUniqueId());
        if (e instanceof Mob mob) {
            Player alvo = alvoMaisPerto(onde);
            if (alvo != null) mob.setTarget(alvo);
        }
    }

    /** Um ponto do chão longe do baú e de quem está na sala, se der. */
    private Location lugarLivre() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int lim = raio - 2;
        Location melhor = null;
        for (int tentativa = 0; tentativa < 20; tentativa++) {
            Location l = new Location(mundo, x + r.nextInt(-lim, lim + 1) + 0.5, yChao, z + r.nextInt(-lim, lim + 1) + 0.5);
            if (!l.getBlock().isPassable() || l.distanceSquared(blocoBau().getLocation()) < 9) continue;
            melhor = l;
            if (alvoMaisPerto(l) == null || alvoMaisPerto(l).getLocation().distanceSquared(l) >= 16) return l;
        }
        return melhor != null ? melhor : new Location(mundo, x + 0.5, yChao, z + lim + 0.5);
    }

    Player alvoMaisPerto(Location de) {
        Player melhor = null;
        double menor = Double.MAX_VALUE;
        for (Player p : jogadoresDentro()) {
            double d = p.getLocation().distanceSquared(de);
            if (d < menor) {
                menor = d;
                melhor = p;
            }
        }
        return melhor;
    }

    List<Player> jogadoresDentro() {
        List<Player> dentro = new ArrayList<>();
        for (Player p : mundo.getPlayers()) if (contem(p.getLocation()) && !p.isDead()) dentro.add(p);
        return dentro;
    }

    /** Tira da lista os guardiões que morreram ou sumiram. Devolve quantos ainda estão vivos. */
    int vivos() {
        guardioes.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || e.isDead() || !e.isValid();
        });
        return guardioes.size();
    }

    void removerGuardioes() {
        for (UUID u : guardioes) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        guardioes.clear();
    }

    // ---------------------------------------------------------------------
    // Barra
    // ---------------------------------------------------------------------

    void atualizarBarra(long agora, long limiteMs) {
        if (barra == null) barra = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        String rar = plugin.nomeRaridade(mapa.raridade());
        if (estado == Estado.LUTANDO) {
            long resta = Math.max(0, limiteMs - (agora - inicio)) / 1000;
            String tempo = String.format(Locale.ROOT, "%d:%02d", resta / 60, resta % 60);
            if (guardioes.isEmpty()) {
                barra.setTitle("§6✦ Tesouro " + rar + " §7· próxima onda chegando... §8· §f" + tempo);
                barra.setProgress(1);
            } else {
                boolean chefe = onda >= ondas().size();
                barra.setTitle("§6✦ Tesouro " + rar + " §7· " + (chefe ? "§cChefe" : "Onda " + (onda + 1) + "/" + ondas().size())
                        + " §8· §f" + guardioes.size() + " guardião(ões) §8· §f" + tempo);
                barra.setProgress(Math.max(0, Math.min(1, guardioes.size() / (double) totalDaOnda)));
            }
            barra.setColor(BarColor.RED);
        } else {
            long resta = Math.max(0, plugin.getConfig().getInt("camara.tempo-para-pegar-segundos", 90) * 1000L - (agora - liberadaEm)) / 1000;
            barra.setTitle("§a✦ Baú destrancado! §7A câmara desaba em §f" + resta + "s");
            barra.setColor(BarColor.GREEN);
            barra.setProgress(Math.max(0, Math.min(1, resta / (double) Math.max(1, plugin.getConfig().getInt("camara.tempo-para-pegar-segundos", 90)))));
        }
        Set<Player> devem = new HashSet<>(jogadoresDentro());
        for (Player p : new ArrayList<>(barra.getPlayers())) if (!devem.contains(p)) barra.removePlayer(p);
        for (Player p : devem) if (!barra.getPlayers().contains(p)) barra.addPlayer(p);
    }

    void tirarBarra() {
        if (barra != null) barra.removeAll();
    }
}
