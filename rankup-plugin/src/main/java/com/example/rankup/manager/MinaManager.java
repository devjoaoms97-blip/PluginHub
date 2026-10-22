package com.example.rankup.manager;

import com.example.rankup.RankupPlugin;
import com.example.rankup.model.BlocoPos;
import com.example.rankup.model.Mina;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Minas (em minas.yml) e o reset delas. O reset troca os blocos aos poucos (blocos-por-tick),
 * pra uma mina grande não travar o servidor, e tira antes quem está dentro.
 */
public class MinaManager {

    private final RankupPlugin plugin;
    private final File arquivo;
    private final Map<String, Mina> minas = new LinkedHashMap<>();

    public MinaManager(RankupPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "minas.yml");
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    public Collection<Mina> getMinas() {
        return Collections.unmodifiableCollection(minas.values());
    }

    public Mina get(String id) {
        return id == null ? null : minas.get(id.toLowerCase());
    }

    public Mina getMinaEm(Location local) {
        for (Mina mina : minas.values()) {
            if (mina.contem(local)) return mina;
        }
        return null;
    }

    public boolean sobrepoeAlguma(String mundo, BlocoPos a, BlocoPos b) {
        BlocoPos min = new BlocoPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        BlocoPos max = new BlocoPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        for (Mina mina : minas.values()) {
            if (mina.sobrepoe(mundo, min, max)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Alteração
    // ---------------------------------------------------------------------

    public Mina criar(String id, World mundo, BlocoPos a, BlocoPos b, String rankMinimo) {
        Mina mina = new Mina(id, mundo.getName(), a, b, rankMinimo,
                Math.max(0, plugin.getConfig().getInt("minas.intervalo-minutos-padrao", 15)),
                Math.max(0, plugin.getConfig().getDouble("minas.reset-percentual-padrao", 80)));
        mina.getComposicao().put(Material.STONE, 100.0);
        minas.put(id, mina);
        agendarProximoReset(mina);
        salvar();
        return mina;
    }

    public void remover(Mina mina) {
        minas.remove(mina.getId());
        salvar();
    }

    /** Conta um bloco minerado e reseta se passou da % configurada. */
    public void registrarQuebra(Mina mina) {
        mina.setQuebrados(mina.getQuebrados() + 1);
        if (mina.getResetPercentual() > 0 && !mina.isResetando()
                && mina.getQuebrados() >= mina.getVolume() * mina.getResetPercentual() / 100.0) {
            resetar(mina, String.format("%.0f%% minerada", mina.getResetPercentual()));
        }
    }

    public void agendarProximoReset(Mina mina) {
        mina.setProximoReset(mina.getIntervaloMinutos() > 0
                ? System.currentTimeMillis() + mina.getIntervaloMinutos() * 60_000L : Long.MAX_VALUE);
    }

    // ---------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------

    /** Chamado a cada 1s: reset por tempo e avisos de quem está dentro. */
    public void tick() {
        long agora = System.currentTimeMillis();
        List<Integer> avisos = plugin.getConfig().getIntegerList("minas.aviso-segundos");
        for (Mina mina : minas.values()) {
            if (mina.isResetando() || mina.getProximoReset() == Long.MAX_VALUE) continue;
            long faltam = mina.getProximoReset() - agora;
            if (faltam <= 0) {
                resetar(mina, "tempo");
                continue;
            }
            // Arredonda pra cima: com o tick de 1s, "faltam 9.4s" é o aviso dos 10s
            long segundos = (faltam + 999) / 1000;
            if (avisos.contains((int) segundos)) {
                for (Player jogador : jogadoresDentro(mina)) {
                    jogador.sendMessage("§eA mina §f" + mina.getId() + " §evai ser resetada em §f" + segundos + "s§e.");
                }
            }
        }
    }

    /**
     * Preenche a mina de novo com a composição, aos poucos. Quem está dentro (ou em cima) é
     * levado pro ponto de teleporte da mina antes. Durante o reset, ninguém minera nela.
     */
    public void resetar(Mina mina, String motivo) {
        World mundo = mina.getWorld();
        if (mina.isResetando() || mundo == null) return;
        Material[] materiais = mina.getComposicao().keySet().toArray(new Material[0]);
        if (materiais.length == 0) {
            agendarProximoReset(mina);
            return;
        }
        double[] acumulado = new double[materiais.length];
        double soma = 0;
        for (int i = 0; i < materiais.length; i++) {
            soma += Math.max(0, mina.getComposicao().get(materiais[i]));
            acumulado[i] = soma;
        }
        if (soma <= 0) {
            agendarProximoReset(mina);
            return;
        }
        final double total = soma;

        mina.setResetando(true);
        Location destino = mina.getLocalSpawn();
        for (Player jogador : jogadoresDentro(mina)) {
            if (destino != null) jogador.teleport(destino);
            jogador.sendMessage("§eA mina §f" + mina.getId() + " §eestá sendo resetada §7(" + motivo + ")§e.");
        }

        BlocoPos min = mina.getMin();
        BlocoPos max = mina.getMax();
        int largX = max.x() - min.x() + 1;
        int largZ = max.z() - min.z() + 1;
        long volume = mina.getVolume();
        int porTick = Math.max(100, plugin.getConfig().getInt("minas.blocos-por-tick", 20000));

        new BukkitRunnable() {
            private long indice = 0;

            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    cancel();
                    return;
                }
                ThreadLocalRandom aleatorio = ThreadLocalRandom.current();
                long fim = Math.min(volume, indice + porTick);
                for (; indice < fim; indice++) {
                    // Camada por camada, de baixo pra cima
                    int y = min.y() + (int) (indice / ((long) largX * largZ));
                    int resto = (int) (indice % ((long) largX * largZ));
                    int x = min.x() + resto % largX;
                    int z = min.z() + resto / largX;
                    double sorteio = aleatorio.nextDouble(total);
                    Material material = materiais[materiais.length - 1];
                    for (int i = 0; i < acumulado.length; i++) {
                        if (sorteio < acumulado[i]) {
                            material = materiais[i];
                            break;
                        }
                    }
                    mundo.getBlockAt(x, y, z).setType(material, false);
                }
                if (indice >= volume) {
                    cancel();
                    mina.setQuebrados(0);
                    mina.setResetando(false);
                    agendarProximoReset(mina);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private List<Player> jogadoresDentro(Mina mina) {
        List<Player> dentro = new ArrayList<>();
        World mundo = mina.getWorld();
        if (mundo == null) return dentro;
        for (Player jogador : mundo.getPlayers()) {
            if (mina.contemOuEmCima(jogador.getLocation())) dentro.add(jogador);
        }
        return dentro;
    }

    // ---------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("minas");
        if (secao == null) return;
        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Mina mina = new Mina(id, s.getString("mundo", "world"), BlocoPos.parse(s.getString("min", "")),
                        BlocoPos.parse(s.getString("max", "")), s.getString("rank", ""),
                        Math.max(0, s.getInt("intervalo-minutos", 15)), Math.max(0, s.getDouble("reset-percentual", 80)));
                ConfigurationSection composicao = s.getConfigurationSection("composicao");
                if (composicao != null) {
                    for (String nome : composicao.getKeys(false)) {
                        Material material = Material.matchMaterial(nome);
                        if (material == null || !material.isBlock()) {
                            plugin.getLogger().warning("Bloco inválido na mina " + id + ": " + nome);
                            continue;
                        }
                        mina.getComposicao().put(material, composicao.getDouble(nome));
                    }
                }
                String spawn = s.getString("spawn");
                if (spawn != null && !spawn.isBlank()) {
                    String[] p = spawn.split(",");
                    mina.setSpawn(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]), Double.parseDouble(p[4])});
                }
                agendarProximoReset(mina);
                minas.put(id, mina);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Mina inválida em minas.yml, ignorada: " + id + " (" + e.getMessage() + ")");
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Mina mina : minas.values()) {
            String c = "minas." + mina.getId();
            yaml.set(c + ".mundo", mina.getMundo());
            yaml.set(c + ".min", mina.getMin().toString());
            yaml.set(c + ".max", mina.getMax().toString());
            yaml.set(c + ".rank", mina.getRankMinimo());
            yaml.set(c + ".intervalo-minutos", mina.getIntervaloMinutos());
            yaml.set(c + ".reset-percentual", mina.getResetPercentual());
            double[] spawn = mina.getSpawn();
            if (spawn != null) {
                yaml.set(c + ".spawn", String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                        spawn[0], spawn[1], spawn[2], spawn[3], spawn[4]));
            }
            mina.getComposicao().forEach((material, peso) -> yaml.set(c + ".composicao." + material.name(), peso));
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar minas.yml", e);
        }
    }
}
