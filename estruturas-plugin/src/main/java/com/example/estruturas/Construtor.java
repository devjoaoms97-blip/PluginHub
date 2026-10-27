package com.example.estruturas;

import com.example.estruturas.estrutura.ArenaAxePvP;
import com.example.estruturas.estrutura.ArenaMobs;
import com.example.estruturas.estrutura.ArenaColiseu;
import com.example.estruturas.estrutura.ArenaSpringColiseu;
import com.example.estruturas.estrutura.ArenaToxica;
import com.example.estruturas.estrutura.LojaOficial;
import com.example.estruturas.estrutura.OficinasDaMina;
import com.example.estruturas.estrutura.AvenidaDasMinas;
import com.example.estruturas.estrutura.BancoDoSpawn;
import com.example.estruturas.estrutura.BarracasDoSpawn;
import com.example.estruturas.estrutura.Estrutura;
import com.example.estruturas.estrutura.Planta;
import com.example.estruturas.estrutura.PortaoOesteDoSpawn;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coloca as estruturas no mundo aos poucos (um lote por tick, pra não travar o servidor), roda os
 * comandos de integração delas no fim e guarda o que havia antes, pra dar pra desfazer a última
 * construção enquanto o servidor não reinicia. O desfazer só volta os blocos (conteúdo de baús
 * se perde) e não desfaz os comandos de integração (barracas cadastradas, local do boss).
 */
public class Construtor {

    private static final int BLOCOS_POR_TICK = 8000;

    private final EstruturasPlugin plugin;
    private final Map<String, Estrutura> estruturas = new LinkedHashMap<>();

    private String emAndamento;
    private World mundoUltima;
    private List<Planta.Bloco> anterioresUltima;

    public Construtor(EstruturasPlugin plugin) {
        this.plugin = plugin;
        registrar(new ArenaAxePvP());
        registrar(new ArenaToxica());
        registrar(new ArenaColiseu());
        registrar(new ArenaSpringColiseu());
        registrar(new BarracasDoSpawn());
        registrar(new AvenidaDasMinas());
        registrar(new OficinasDaMina());
        registrar(new LojaOficial());
        registrar(new PortaoOesteDoSpawn());
        registrar(new ArenaMobs());
        registrar(new BancoDoSpawn());
    }

    private void registrar(Estrutura estrutura) {
        estruturas.put(estrutura.getId(), estrutura);
    }

    public Map<String, Estrutura> getEstruturas() {
        return estruturas;
    }

    public Estrutura get(String id) {
        return estruturas.get(id.toLowerCase(Locale.ROOT));
    }

    /** O que está sendo construído agora, ou null. */
    public String getEmAndamento() {
        return emAndamento;
    }

    /**
     * Constrói a estrutura com o piso no bloco logo abaixo de (x, y, z) — o lugar dos pés de quem
     * está construindo — e o centro dela em x/z.
     */
    public void construir(CommandSender sender, Estrutura estrutura, World mundo, int x, int y, int z) {
        if (emAndamento != null) {
            sender.sendMessage("§cJá tem uma construção em andamento (" + emAndamento + "), espere terminar.");
            return;
        }

        Planta planta = new Planta();
        estrutura.gerar(planta);

        int oy = y - 1;
        List<Planta.Bloco> blocos = new ArrayList<>();
        for (Planta.Bloco b : planta.getBlocos()) {
            blocos.add(new Planta.Bloco(x + b.x(), oy + b.y(), z + b.z(), b.dados()));
        }

        sender.sendMessage("§eConstruindo §f" + estrutura.getNome() + "§e (" + blocos.size() + " blocos)...");
        colocar(estrutura.getId(), mundo, blocos, sender, true, () -> {
            sender.sendMessage("§a" + estrutura.getNome() + " pronta!");
            rodarComandos(sender, estrutura.getComandos(mundo.getName(), x, oy, z));
            sender.sendMessage("§7Não gostou? §f/estrutura desfazer§7 volta o terreno como estava.");
        });
    }

    /** Roda os comandos de integração no console; os de plugin que não está instalado são pulados. */
    private void rodarComandos(CommandSender sender, List<String> comandos) {
        if (comandos.isEmpty()) return;
        int rodados = 0;
        List<String> faltando = new ArrayList<>();
        for (String comando : comandos) {
            String raiz = comando.split(" ", 2)[0];
            if (Bukkit.getCommandMap().getCommand(raiz) == null) {
                if (!faltando.contains(raiz)) faltando.add(raiz);
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando);
            rodados++;
        }
        if (rodados > 0) sender.sendMessage("§a" + rodados + " comando(s) de integração rodados (veja o console).");
        if (!faltando.isEmpty()) {
            sender.sendMessage("§eSem o plugin do comando, ficou de fora: §f/" + String.join(", /", faltando));
        }
    }

    public void desfazer(CommandSender sender) {
        if (emAndamento != null) {
            sender.sendMessage("§cJá tem uma construção em andamento (" + emAndamento + "), espere terminar.");
            return;
        }
        if (anterioresUltima == null) {
            sender.sendMessage("§cNada pra desfazer (o histórico some quando o servidor reinicia).");
            return;
        }
        List<Planta.Bloco> anteriores = new ArrayList<>(anterioresUltima);
        Collections.reverse(anteriores);
        World mundo = mundoUltima;
        anterioresUltima = null;
        mundoUltima = null;

        sender.sendMessage("§eDesfazendo a última construção (" + anteriores.size() + " blocos)...");
        colocar("desfazer", mundo, anteriores, sender, false, () -> sender.sendMessage(
                "§aConstrução desfeita. §7Cadastros feitos pelos comandos de integração (barracas, boss) continuam."));
    }

    private void colocar(String nome, World mundo, List<Planta.Bloco> blocos, CommandSender sender,
                         boolean guardarDesfazer, Runnable aoTerminar) {
        emAndamento = nome;
        List<Planta.Bloco> anteriores = new ArrayList<>();

        new BukkitRunnable() {
            private int indice;
            private int proximoAviso = 25;

            @Override
            public void run() {
                int fim = Math.min(indice + BLOCOS_POR_TICK, blocos.size());
                for (; indice < fim; indice++) {
                    Planta.Bloco b = blocos.get(indice);
                    Block bloco = mundo.getBlockAt(b.x(), b.y(), b.z());
                    BlockData atual = bloco.getBlockData();
                    if (atual.equals(b.dados())) continue;
                    if (guardarDesfazer) anteriores.add(new Planta.Bloco(b.x(), b.y(), b.z(), atual));
                    bloco.setBlockData(b.dados(), false);
                }

                int progresso = blocos.isEmpty() ? 100 : indice * 100 / blocos.size();
                if (progresso >= proximoAviso && progresso < 100) {
                    sender.sendMessage("§7... " + progresso + "%");
                    while (proximoAviso <= progresso) proximoAviso += 25;
                }

                if (indice >= blocos.size()) {
                    cancel();
                    emAndamento = null;
                    if (guardarDesfazer) {
                        mundoUltima = mundo;
                        anterioresUltima = anteriores;
                    }
                    plugin.getLogger().info("Terminou: " + nome + " (" + blocos.size() + " blocos)");
                    aoTerminar.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
