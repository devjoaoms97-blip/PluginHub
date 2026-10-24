package com.example.barracas.manager;

import com.example.barracas.BarracasPlugin;
import com.example.barracas.model.Barraca;
import com.example.barracas.model.BlocoPos;
import com.example.barracas.model.Leilao;
import com.example.barracas.model.Oferta;
import com.example.barracas.util.Formato;
import com.example.barracas.util.Itens;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Estado de todas as barracas: quem alugou, até quando, o que o inquilino colocou e os leilões
 * das especiais. Tudo salvo em {@code barracas.yml} a cada mudança — inclusive os lances
 * retidos, pra um restart no meio do leilão não sumir com o dinheiro de ninguém.
 *
 * {@link #tick()} roda a cada segundo: renova automaticamente quem pediu, vence aluguéis, manda o
 * aviso de vencimento, encerra leilões e (a cada 30s) atualiza os hologramas de entrada.
 */
public class BarracaManager {

    private final BarracasPlugin plugin;
    private final File arquivo;
    private final Map<String, Barraca> barracas = new LinkedHashMap<>();
    private int segundosDesdeHologramas;

    public BarracaManager(BarracasPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "barracas.yml");
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------
    public Map<String, Barraca> getBarracas() {
        return barracas;
    }

    public Barraca get(String id) {
        return barracas.get(id.toLowerCase(Locale.ROOT));
    }

    public Barraca getBarracaEm(Location local) {
        for (Barraca b : barracas.values()) {
            if (b.contem(local)) return b;
        }
        return null;
    }

    public Barraca getBarracaEm(Block bloco) {
        return getBarracaEm(bloco.getLocation());
    }

    /** A barraca que esse jogador tem alugada agora (só pode ter uma), ou null. */
    public Barraca getBarracaDe(UUID jogador) {
        for (Barraca b : barracas.values()) {
            if (jogador.equals(b.getDono())) return b;
        }
        return null;
    }

    /** O leilão em que esse jogador está com o maior lance, ou null. */
    public Barraca getLeilaoLideradoPor(UUID jogador) {
        for (Barraca b : barracas.values()) {
            if (b.emLeilao() && jogador.equals(b.getLeilao().getLicitante())) return b;
        }
        return null;
    }

    public boolean sobrepoeAlguma(String mundo, BlocoPos min, BlocoPos max) {
        for (Barraca b : barracas.values()) {
            if (b.sobrepoe(mundo, min, max)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Criar / remover (staff)
    // ---------------------------------------------------------------------
    public Barraca criar(String id, World mundo, BlocoPos a, BlocoPos b, boolean especial) {
        BlocoPos min = new BlocoPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        BlocoPos max = new BlocoPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        Barraca barraca = new Barraca(id.toLowerCase(Locale.ROOT), mundo.getName(), min, max, especial);
        barracas.put(barraca.getId(), barraca);
        if (especial) {
            iniciarLeilao(barraca);
        } else {
            salvar();
        }
        return barraca;
    }

    /** Apaga a barraca: itens do inquilino vão pra retirada e o lance retido (se houver) é devolvido. */
    public void remover(Barraca barraca) {
        if (barraca.getDono() != null) {
            UUID dono = barraca.getDono();
            int pilhas = liberar(barraca, false);
            notificar(dono, "§cSua barraca §f" + barraca.getId() + " §cfoi removida pela staff."
                    + avisoRetirada(pilhas));
        }
        if (barraca.emLeilao()) {
            devolverLance(barraca.getLeilao(), "§eO leilão da barraca §f" + barraca.getId()
                    + " §efoi cancelado. Seu lance de §f%valor% §efoi devolvido.");
        }
        barracas.remove(barraca.getId());
        salvar();
    }

    // ---------------------------------------------------------------------
    // Aluguel
    // ---------------------------------------------------------------------
    public void ocupar(Barraca barraca, UUID dono, String nomeDono, long ate) {
        barraca.setDono(dono, nomeDono);
        barraca.setAlugadaAte(ate);
        barraca.setAvisoVencimentoEnviado(false);
        salvar();
    }

    public void estender(Barraca barraca, long novoFim) {
        barraca.setAlugadaAte(novoFim);
        barraca.setAvisoVencimentoEnviado(false);
        salvar();
    }

    /**
     * Devolve a barraca: o estoque da loja vai pra caixa de retirada, os anúncios somem e, se for
     * especial e {@code abrirLeilao}, ela volta a leilão. Barracas da época dos baús também têm os
     * baús e placas do inquilino tirados (com o conteúdo indo pra retirada). Retorna quantas
     * pilhas foram pra retirada.
     */
    public int liberar(Barraca barraca, boolean abrirLeilao) {
        UUID dono = barraca.getDono();
        List<ItemStack> itens = new ArrayList<>();
        for (Oferta oferta : barraca.getOfertas()) {
            itens.addAll(Itens.pilhas(oferta.getModelo(), oferta.getEstoque()));
        }
        barraca.getOfertas().clear();

        World mundo = Bukkit.getWorld(barraca.getMundo());
        if (mundo != null) {
            List<Block> blocos = new ArrayList<>();
            for (BlocoPos pos : barraca.getBlocosDoDono()) {
                blocos.add(mundo.getBlockAt(pos.x(), pos.y(), pos.z()));
            }
            // Placas primeiro: se o baú sumisse antes, a placa de parede cairia como item no chão
            blocos.sort(Comparator.comparing(bloco -> !Tag.ALL_SIGNS.isTagged(bloco.getType())));

            for (Block bloco : blocos) {
                BlockState estado = bloco.getState();
                if (estado instanceof Chest bau) {
                    coletar(bau.getBlockInventory(), itens); // só a metade desse bloco, se for baú duplo
                } else if (estado instanceof Container container) {
                    coletar(container.getInventory(), itens);
                }
                if (plugin.isBau(bloco.getType()) || Tag.ALL_SIGNS.isTagged(bloco.getType())) {
                    bloco.setType(Material.AIR, false);
                }
            }
        }

        barraca.getBlocosDoDono().clear();
        barraca.setDono(null, null);
        barraca.setAlugadaAte(0);
        barraca.setAvisoVencimentoEnviado(false);
        barraca.setRenovacaoAutomatica(false);

        if (dono != null && !itens.isEmpty()) {
            plugin.getRetiradaManager().adicionar(dono, itens);
        }

        if (abrirLeilao && barraca.isEspecial()) {
            iniciarLeilao(barraca);
        } else {
            salvar();
        }
        return itens.size();
    }

    private void coletar(Inventory inventario, List<ItemStack> destino) {
        for (ItemStack item : inventario.getContents()) {
            if (item != null && !item.getType().isAir()) {
                destino.add(item.clone());
            }
        }
        inventario.clear();
    }

    // ---------------------------------------------------------------------
    // Leilão (só barracas especiais)
    // ---------------------------------------------------------------------
    public void iniciarLeilao(Barraca barraca) {
        long duracaoMs = Math.max(1, plugin.getConfig().getInt("leilao.duracao-minutos", 10)) * 60_000L;
        barraca.setLeilao(new Leilao(System.currentTimeMillis() + duracaoMs));
        salvar();

        anunciarLeilao("§6§l⚖ Leilão! §6A barraca especial §f" + barraca.getId()
                + " §6está em leilão por §f" + Formato.tempo(duracaoMs) + "§6. Lance mínimo: §f"
                + Formato.valor(plugin.getPrecoAluguel(barraca)) + "§6. Use §f/barraca lance " + barraca.getId() + " <valor>");
    }

    public double lanceMinimo(Barraca barraca) {
        Leilao leilao = barraca.getLeilao();
        if (!leilao.temLance()) {
            return plugin.getPrecoAluguel(barraca);
        }
        double incremento = Math.max(0, plugin.getConfig().getDouble("leilao.incremento-minimo-percentual", 5));
        return leilao.getLance() * (1 + incremento / 100.0);
    }

    /** Tenta dar um lance. Retorna a mensagem de erro, ou null se o lance foi aceito. */
    public String darLance(Player jogador, Barraca barraca, double valor) {
        Leilao leilao = barraca.getLeilao();
        if (leilao == null) {
            return "§cA barraca §f" + barraca.getId() + " §cnão está em leilão.";
        }
        UUID id = jogador.getUniqueId();
        if (getBarracaDe(id) != null) {
            return "§cVocê já tem uma barraca alugada. Só dá pra ter uma por vez.";
        }
        Barraca outroLeilao = getLeilaoLideradoPor(id);
        if (outroLeilao != null && outroLeilao != barraca) {
            return "§cVocê já está com o maior lance no leilão da barraca §f" + outroLeilao.getId() + "§c.";
        }

        double minimo = lanceMinimo(barraca);
        if (valor + 0.000001 < minimo) {
            return "§cO lance mínimo agora é §f" + Formato.valor(minimo) + "§c.";
        }

        Economy econ = plugin.getEconomy();
        boolean jaLidera = id.equals(leilao.getLicitante());
        double aCobrar = jaLidera ? valor - leilao.getLance() : valor; // subindo o próprio lance: paga só a diferença
        if (!econ.has(jogador, aCobrar)) {
            return "§cSaldo insuficiente. Você precisa de §f" + Formato.valor(aCobrar) + "§c.";
        }

        econ.withdrawPlayer(jogador, aCobrar);
        if (!jaLidera && leilao.temLance()) {
            devolverLance(leilao, "§eSeu lance na barraca §f" + barraca.getId() + " §efoi superado. §f%valor% §efoi devolvido.");
        }
        leilao.registrarLance(id, jogador.getName(), valor);

        // Anti-snipe: lance no finalzinho estende o leilão, pra todo mundo ter chance de responder
        long antiSnipeMs = Math.max(0, plugin.getConfig().getInt("leilao.anti-snipe-segundos", 30)) * 1000L;
        long agora = System.currentTimeMillis();
        if (leilao.getTerminaEm() - agora < antiSnipeMs) {
            leilao.setTerminaEm(agora + antiSnipeMs);
        }
        salvar();

        Bukkit.broadcastMessage("§6⚖ §f" + jogador.getName() + " §6deu um lance de §f" + Formato.valor(valor)
                + " §6na barraca §f" + barraca.getId() + "§6. Termina em §f"
                + Formato.tempo(leilao.getTerminaEm() - agora) + "§6.");
        return null;
    }

    private void devolverLance(Leilao leilao, String mensagem) {
        if (!leilao.temLance()) return;
        Economy econ = plugin.getEconomy();
        OfflinePlayer anterior = Bukkit.getOfflinePlayer(leilao.getLicitante());
        if (econ != null) {
            econ.depositPlayer(anterior, leilao.getLance());
        } else {
            plugin.getLogger().severe("Economia indisponível: não deu pra devolver " + leilao.getLance()
                    + " pro licitante " + leilao.getLicitante() + " — devolva manualmente.");
        }
        notificar(leilao.getLicitante(), mensagem.replace("%valor%", Formato.valor(leilao.getLance())));
    }

    private void finalizarLeilao(Barraca barraca) {
        Leilao leilao = barraca.getLeilao();
        barraca.setLeilao(null);

        if (!leilao.temLance()) {
            salvar();
            anunciarLeilao("§6⚖ §6O leilão da barraca §f" + barraca.getId()
                    + " §6acabou sem lances. Ela fica livre pra alugar por §f" + Formato.valor(plugin.getPrecoAluguel(barraca))
                    + " §6com §f/barraca alugar " + barraca.getId() + "§6.");
            return;
        }

        // O valor já foi retido no lance — aqui ele só "vai pro servidor" (some da economia)
        ocupar(barraca, leilao.getLicitante(), leilao.getNomeLicitante(), System.currentTimeMillis() + plugin.getDuracaoAluguelMs());
        plugin.getPixIntegration().registrarComServidor(leilao.getLicitante(), leilao.getLance(), false, "Leilão de barraca");

        anunciarLeilao("§6⚖ §f" + leilao.getNomeLicitante() + " §6venceu o leilão da barraca §f"
                + barraca.getId() + " §6por §f" + Formato.valor(leilao.getLance()) + "§6!");
        notificar(leilao.getLicitante(), "§aA barraca §f" + barraca.getId() + " §aé sua por §f"
                + Formato.tempo(plugin.getDuracaoAluguelMs()) + "§a. Monte a loja com o item na mão: §f/barraca vender <quantidade> <preço>§a.");
    }

    // ---------------------------------------------------------------------
    // Tarefa periódica
    // ---------------------------------------------------------------------
    public void tick() {
        long agora = System.currentTimeMillis();
        long avisoMs = Math.max(0, plugin.getConfig().getInt("aluguel.aviso-minutos-antes", 30)) * 60_000L;

        for (Barraca barraca : new ArrayList<>(barracas.values())) {
            if (barraca.getDono() != null && barraca.isRenovacaoAutomatica()
                    && barraca.getAlugadaAte() - agora <= 60_000) {
                renovarAutomaticamente(barraca);
            }
            if (barraca.getDono() != null) {
                long restante = barraca.getAlugadaAte() - agora;
                if (restante <= 0) {
                    UUID dono = barraca.getDono();
                    int pilhas = liberar(barraca, true);
                    notificar(dono, "§eO aluguel da sua barraca §f" + barraca.getId() + " §eacabou." + avisoRetirada(pilhas));
                } else if (avisoMs > 0 && restante <= avisoMs && !barraca.isAvisoVencimentoEnviado()) {
                    barraca.setAvisoVencimentoEnviado(true);
                    String dica;
                    if (barraca.isEspecial()) {
                        dica = "§7Barracas especiais não renovam: ela volta a leilão.";
                    } else if (barraca.isRenovacaoAutomatica()) {
                        dica = "§7A renovação automática vai cobrar §f" + Formato.valor(precoComDesconto(barraca))
                                + " §7(deixe o saldo na conta).";
                    } else {
                        dica = "§7Use §f/barraca renovar §7pra estender (ou §f/barraca autorenovar§7).";
                    }
                    notificar(barraca.getDono(), "§eSua barraca §f" + barraca.getId() + " §evence em §f"
                            + Formato.tempo(restante) + "§e. " + dica);
                }
            }

            if (barraca.emLeilao() && agora >= barraca.getLeilao().getTerminaEm()) {
                finalizarLeilao(barraca);
            }
        }

        if (++segundosDesdeHologramas >= 30) {
            segundosDesdeHologramas = 0;
            barracas.values().forEach(plugin.getHologramas()::atualizar); // o tempo restante muda sozinho
            barracas.values().forEach(plugin.getNpcs()::atualizar); // e um NPC que sumiu volta
        }
    }

    /** Preço da renovação: o da barraca, com o desconto de VIP do dono (ou o último que ele teve, se offline). */
    public double precoComDesconto(Barraca barraca) {
        Player online = Bukkit.getPlayer(barraca.getDono());
        int desconto = online != null ? plugin.calcularDescontoVip(online) : barraca.getUltimoDesconto();
        return plugin.getPrecoAluguel(barraca) * (1 - desconto / 100.0);
    }

    /**
     * Renovação automática (só normais), feita no último minuto do aluguel. Sem saldo, ela é
     * desligada e o aluguel vence normalmente — o dono é avisado.
     */
    private void renovarAutomaticamente(Barraca barraca) {
        UUID dono = barraca.getDono();
        Economy econ = plugin.getEconomy();
        double preco = precoComDesconto(barraca);
        OfflinePlayer conta = Bukkit.getOfflinePlayer(dono);
        if (barraca.isEspecial() || econ == null || !econ.has(conta, preco)) {
            barraca.setRenovacaoAutomatica(false);
            salvar();
            notificar(dono, "§cA renovação automática da barraca §f" + barraca.getId() + " §cnão conseguiu cobrar §f"
                    + Formato.valor(preco) + "§c (saldo insuficiente) e foi desligada.");
            return;
        }
        econ.withdrawPlayer(conta, preco);
        long novoFim = Math.max(barraca.getAlugadaAte(), System.currentTimeMillis()) + plugin.getDuracaoAluguelMs();
        estender(barraca, novoFim);
        plugin.getPixIntegration().registrarComServidor(dono, preco, false, "Renovação automática de barraca");
        notificar(dono, "§aBarraca §f" + barraca.getId() + " §arenovada automaticamente por §f" + Formato.valor(preco)
                + "§a. Vale por mais §f" + Formato.tempo(novoFim - System.currentTimeMillis()) + "§a.");
    }

    public String avisoRetirada(int pilhas) {
        if (pilhas <= 0) return "";
        return " §7Os itens da loja (§f" + pilhas + " pilha(s)§7) estão em §f/barraca retirar §7por §f"
                + plugin.getRetiradaManager().diasGuardado() + " dias§7.";
    }

    private void notificar(UUID jogador, String mensagem) {
        Player online = Bukkit.getPlayer(jogador);
        if (online != null) {
            online.sendMessage(mensagem);
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
    }

    // ---------------------------------------------------------------------
    // Persistência (barracas.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("barracas");
        if (secao == null) return;

        for (String id : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Barraca barraca = new Barraca(id, s.getString("mundo"), BlocoPos.parse(s.getString("min")),
                        BlocoPos.parse(s.getString("max")), s.getBoolean("especial"));

                String dono = s.getString("dono");
                if (dono != null) {
                    barraca.setDono(UUID.fromString(dono), s.getString("nome-dono", "?"));
                    barraca.setAlugadaAte(s.getLong("alugada-ate"));
                }
                for (String pos : s.getStringList("blocos")) {
                    barraca.getBlocosDoDono().add(BlocoPos.parse(pos));
                }

                if (s.contains("preco")) barraca.setPreco(s.getDouble("preco"));
                // "placa" é das versões em que a entrada era uma placa
                String entrada = s.getString("entrada", s.getString("placa"));
                if (entrada != null) barraca.setEntrada(BlocoPos.parse(entrada));
                String npc = s.getString("npc");
                if (npc != null) barraca.setNpc(BlocoPos.parse(npc), (float) s.getDouble("npc-yaw"));
                ConfigurationSection ofertas = s.getConfigurationSection("ofertas");
                if (ofertas != null) {
                    for (String indice : ofertas.getKeys(false)) {
                        ConfigurationSection o = ofertas.getConfigurationSection(indice);
                        ItemStack item = o == null ? null : o.getItemStack("item");
                        if (item == null) continue;
                        barraca.getOfertas().add(new Oferta(Oferta.Tipo.valueOf(o.getString("tipo", "VENDA")), item,
                                Math.max(1, o.getInt("quantidade")), o.getDouble("preco"), o.getInt("estoque")));
                    }
                }
                barraca.setRenovacaoAutomatica(s.getBoolean("renovacao-automatica"));
                barraca.setUltimoDesconto(s.getInt("ultimo-desconto"));

                ConfigurationSection l = s.getConfigurationSection("leilao");
                if (l != null) {
                    String licitante = l.getString("licitante");
                    barraca.setLeilao(licitante == null
                            ? new Leilao(l.getLong("termina-em"))
                            : new Leilao(l.getLong("termina-em"), l.getDouble("lance"), UUID.fromString(licitante),
                            l.getString("nome-licitante", "?")));
                }
                barracas.put(id, barraca);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Barraca inválida ignorada em barracas.yml: " + id + " (" + e.getMessage() + ")");
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Barraca b : barracas.values()) {
            String base = "barracas." + b.getId();
            yaml.set(base + ".mundo", b.getMundo());
            yaml.set(base + ".min", b.getMin().toString());
            yaml.set(base + ".max", b.getMax().toString());
            yaml.set(base + ".especial", b.isEspecial());
            yaml.set(base + ".preco", b.getPreco());
            yaml.set(base + ".entrada", b.getEntrada() == null ? null : b.getEntrada().toString());
            if (b.getNpc() != null) {
                yaml.set(base + ".npc", b.getNpc().toString());
                yaml.set(base + ".npc-yaw", (double) b.getNpcYaw());
            }
            for (int i = 0; i < b.getOfertas().size(); i++) {
                Oferta o = b.getOfertas().get(i);
                String chave = base + ".ofertas." + i;
                yaml.set(chave + ".tipo", o.getTipo().name());
                yaml.set(chave + ".item", o.getModelo());
                yaml.set(chave + ".quantidade", o.getQuantidade());
                yaml.set(chave + ".preco", o.getPreco());
                yaml.set(chave + ".estoque", o.getEstoque());
            }
            if (b.isRenovacaoAutomatica()) yaml.set(base + ".renovacao-automatica", true);
            if (b.getUltimoDesconto() > 0) yaml.set(base + ".ultimo-desconto", b.getUltimoDesconto());
            if (b.getDono() != null) {
                yaml.set(base + ".dono", b.getDono().toString());
                yaml.set(base + ".nome-dono", b.getNomeDono());
                yaml.set(base + ".alugada-ate", b.getAlugadaAte());
            }
            if (!b.getBlocosDoDono().isEmpty()) {
                yaml.set(base + ".blocos", b.getBlocosDoDono().stream().map(BlocoPos::toString).toList());
            }
            Leilao leilao = b.getLeilao();
            if (leilao != null) {
                yaml.set(base + ".leilao.termina-em", leilao.getTerminaEm());
                if (leilao.temLance()) {
                    yaml.set(base + ".leilao.lance", leilao.getLance());
                    yaml.set(base + ".leilao.licitante", leilao.getLicitante().toString());
                    yaml.set(base + ".leilao.nome-licitante", leilao.getNomeLicitante());
                }
            }
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar barracas.yml", e);
        }
        // toda mudança de estado passa por aqui: o holograma de entrada e o NPC acompanham na hora
        barracas.values().forEach(plugin.getHologramas()::atualizar);
        barracas.values().forEach(plugin.getNpcs()::atualizar);
    }

    /** Anuncia no chat e no Discord (categoria "leiloes"). */
    private void anunciarLeilao(String mensagem) {
        Bukkit.broadcastMessage(mensagem);
        plugin.getDiscord().enviar("leiloes", mensagem);
    }
}
