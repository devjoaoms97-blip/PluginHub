package com.example.terrenos.manager;

import com.example.terrenos.TerrenosPlugin;
import com.example.terrenos.model.Leilao;
import com.example.terrenos.model.Permissao;
import com.example.terrenos.model.Terreno;
import com.example.terrenos.model.TerrenoId;
import com.example.terrenos.util.Formato;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Estado dos terrenos: donos, amigos, imposto pago e leilões de imóvel, salvo em
 * {@code terrenos.yml} a cada mudança (inclusive os lances retidos, pra um restart no meio do
 * leilão não sumir com o dinheiro de ninguém).
 * <p>
 * O imposto é semanal e vale por terreno: uma % do preço do rank atual do dono, com um mínimo.
 * Quem deixa o pagamento automático ligado paga sozinho no vencimento, se tiver saldo. Passado o
 * vencimento, o dono tem alguns dias de tolerância; depois disso o terreno é confiscado e vai a
 * leilão com tudo o que tem dentro. O lance vencedor some da economia.
 */
public class TerrenoManager {

    private static final long DIA = 86_400_000L;
    private static final long HORA = 3_600_000L;

    private final TerrenosPlugin plugin;
    private final File arquivo;
    private final Map<TerrenoId, Terreno> terrenos = new LinkedHashMap<>();
    /** Último aviso de imposto atrasado de cada terreno, pra não repetir a cada verificação. */
    private final Map<TerrenoId, Long> ultimoAviso = new HashMap<>();
    private int segundos;

    public TerrenoManager(TerrenosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "terrenos.yml");
        carregar();
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------
    public Terreno get(TerrenoId id) {
        return terrenos.get(id);
    }

    /** O terreno nessa posição (com dono ou em leilão), ou null se for rua ou terreno livre. */
    public Terreno getEm(Location local) {
        TerrenoId id = idEm(local);
        return id == null ? null : terrenos.get(id);
    }

    /** A posição na grade, ou null se for rua ou outro mundo. */
    public TerrenoId idEm(Location local) {
        if (local == null || local.getWorld() == null || !local.getWorld().equals(plugin.getMundo())) return null;
        return plugin.getGrade().terrenoEm(local.getBlockX(), local.getBlockZ());
    }

    /** Terrenos de um jogador, do mais antigo pro mais novo (a ordem do /terreno home). */
    public List<Terreno> getDe(UUID jogador) {
        List<Terreno> lista = new ArrayList<>();
        for (Terreno t : terrenos.values()) {
            if (jogador.equals(t.getDono())) lista.add(t);
        }
        lista.sort(Comparator.comparingLong(Terreno::getCompradoEm));
        return lista;
    }

    public List<Terreno> getLeiloes() {
        List<Terreno> lista = new ArrayList<>();
        for (Terreno t : terrenos.values()) {
            if (t.emLeilao()) lista.add(t);
        }
        lista.sort(Comparator.comparingLong(t -> t.getLeilao().getTerminaEm()));
        return lista;
    }

    /** Terrenos que o jogador tem mais os leilões em que está ganhando: conta pro limite. */
    public int ocupados(UUID jogador) {
        int total = getDe(jogador).size();
        for (Terreno t : terrenos.values()) {
            if (t.emLeilao() && jogador.equals(t.getLeilao().getLicitante())) total++;
        }
        return total;
    }

    /** Livre pra compra: dentro da grade, sem dono, sem leilão e sem limpeza em andamento. */
    public boolean livre(TerrenoId id) {
        return id != null && dentroDaGrade(id) && !terrenos.containsKey(id) && !plugin.getLimpador().ocupado(id);
    }

    public boolean dentroDaGrade(TerrenoId id) {
        return id.anel() <= plugin.getConfig().getInt("grade.raio-maximo", 50);
    }

    /** O terreno livre mais perto do centro da grade, ou null se a grade estiver cheia. */
    public TerrenoId livreMaisPerto() {
        int raio = plugin.getConfig().getInt("grade.raio-maximo", 50);
        for (int anel = 0; anel <= raio; anel++) {
            for (int x = -anel; x <= anel; x++) {
                for (int z = -anel; z <= anel; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != anel) continue;
                    TerrenoId id = new TerrenoId(x, z);
                    if (livre(id)) return id;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Imposto
    // ---------------------------------------------------------------------
    public long semanaMs() {
        return Math.max(1, plugin.getConfig().getInt("imposto.periodo-dias", 7)) * DIA;
    }

    public long toleranciaMs() {
        return Math.max(0, plugin.getConfig().getInt("imposto.tolerancia-dias", 7)) * DIA;
    }

    /** Imposto semanal de cada terreno desse dono: % do preço do rank atual, com mínimo. */
    public double valorImposto(UUID dono) {
        double minimo = Math.max(0, plugin.getConfig().getDouble("imposto.minimo", 1000));
        double precoRank = plugin.getRankupIntegration().precoRankAtual(dono);
        if (precoRank < 0) return Math.max(minimo, plugin.getConfig().getDouble("imposto.sem-rankup", 5000));
        double percentual = Math.max(0, plugin.getConfig().getDouble("imposto.percentual-do-rank", 3));
        return Math.max(minimo, precoRank * percentual / 100.0);
    }

    public boolean atrasado(Terreno t, long agora) {
        return t.temDono() && agora >= t.getImpostoPagoAte();
    }

    /**
     * Paga uma semana de imposto desse terreno. Retorna false (sem cobrar) se o dono não tem
     * saldo ou se já pagou o máximo de semanas adiantadas.
     */
    public boolean pagarImposto(Terreno t) {
        Economy econ = plugin.getEconomy();
        if (econ == null || !t.temDono()) return false;
        long agora = System.currentTimeMillis();
        int maxAdiantadas = Math.max(1, plugin.getConfig().getInt("imposto.semanas-adiantadas-maximo", 4));
        if (t.getImpostoPagoAte() - agora >= (long) maxAdiantadas * semanaMs()) return false;

        OfflinePlayer dono = Bukkit.getOfflinePlayer(t.getDono());
        double valor = valorImposto(t.getDono());
        if (!econ.has(dono, valor)) return false;
        econ.withdrawPlayer(dono, valor);
        // em dia: soma a partir do vencimento; atrasado: a semana conta a partir de agora
        t.setImpostoPagoAte(Math.max(t.getImpostoPagoAte(), agora) + semanaMs());
        plugin.getPixIntegration().registrarComServidor(t.getDono(), valor, false, "Imposto do terreno " + t.getId());
        salvar();
        return true;
    }

    // ---------------------------------------------------------------------
    // Compra, abandono e amigos
    // ---------------------------------------------------------------------
    public double precoCompra() {
        return Math.max(0, plugin.getConfig().getDouble("compra.preco", 50000));
    }

    /** Já conferido: terreno livre, limite e saldo. Cobra e registra. */
    public Terreno comprar(Player jogador, TerrenoId id) {
        double preco = precoCompra();
        plugin.getEconomy().withdrawPlayer(jogador, preco);
        Terreno t = novoDono(id, jogador.getUniqueId(), jogador.getName(), preco);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), preco, false, "Compra do terreno " + id);
        salvar();
        plugin.getPlacas().atualizar(id);
        return t;
    }

    private Terreno novoDono(TerrenoId id, UUID dono, String nome, double preco) {
        long agora = System.currentTimeMillis();
        Terreno t = new Terreno(id);
        t.setDono(dono, nome);
        t.setCompradoEm(agora);
        t.setUltimoPreco(preco);
        t.setImpostoPagoAte(agora + semanaMs()); // a primeira semana vem com a compra
        terrenos.put(id, t);
        return t;
    }

    /** Devolve o terreno pro servidor, sem reembolso, e limpa o que tem dentro. */
    public void abandonar(Terreno t) {
        terrenos.remove(t.getId());
        plugin.getLimpador().limpar(t.getId());
        salvar();
        plugin.getPlacas().atualizar(t.getId());
    }

    // ---------------------------------------------------------------------
    // Leilão de imóvel
    // ---------------------------------------------------------------------
    /** Tira o terreno do dono (com a dívida de imposto) e abre o leilão. */
    public void confiscar(Terreno t, String motivo) {
        long agora = System.currentTimeMillis();
        // a semana que venceu e mais uma por semana inteira de atraso (com a tolerância de 7 dias, 2)
        long atraso = Math.max(0, agora - t.getImpostoPagoAte());
        long semanas = 1 + atraso / semanaMs();
        double divida = valorImposto(t.getDono()) * semanas;
        long duracao = Math.max(1, plugin.getConfig().getInt("leilao.duracao-horas", 48)) * HORA;

        Player dono = Bukkit.getPlayer(t.getDono());
        if (dono != null) {
            dono.sendMessage("§c§lSeu terreno §f" + t.getId() + " §c§lfoi confiscado " + motivo
                    + "§c. Ele vai a leilão com tudo o que tem dentro. Pra dar lance, pague antes a dívida de §f"
                    + Formato.valor(divida) + " §ccom §f/terreno divida pagar " + t.getId() + "§c.");
        }
        t.setLeilao(new Leilao(agora + duracao, t.getDono(), t.getNomeDono(), divida));
        t.setDono(null, null);
        t.getAmigos().clear();
        salvar();
        plugin.getPlacas().atualizar(t.getId());

        anunciarLeilao("§6§l[Leilão de imóvel] §eO terreno §f" + t.getId() + " §ede §f" + t.getLeilao().getNomeDonoAnterior()
                + " §evai a leilão com tudo o que tem dentro, por §f" + Formato.tempo(duracao)
                + "§e. Lance mínimo: §f" + Formato.valor(lanceMinimo()) + "§e. Veja com §f/terreno ir " + t.getId()
                + "§e e dê lance com §f/terreno lance " + t.getId() + " <valor>§e.");
    }

    public double lanceMinimo() {
        return Math.max(1, plugin.getConfig().getDouble("leilao.lance-minimo", 50000));
    }

    /** Menor valor aceito agora: o mínimo, ou o lance atual mais o incremento. */
    public double proximoLanceMinimo(Leilao leilao) {
        if (!leilao.temLance()) return lanceMinimo();
        double incremento = Math.max(0, plugin.getConfig().getDouble("leilao.incremento-minimo-percentual", 5));
        return leilao.getLance() * (1 + incremento / 100.0);
    }

    /** Já conferido: leilão aberto, valor, limite, dívida e saldo. Retém o valor e devolve o anterior. */
    public void darLance(Player jogador, Terreno t, double valor) {
        Leilao leilao = t.getLeilao();
        plugin.getEconomy().withdrawPlayer(jogador, valor);
        if (leilao.temLance()) devolverLance(leilao);
        leilao.registrarLance(jogador.getUniqueId(), jogador.getName(), valor);

        long antiSnipe = Math.max(0, plugin.getConfig().getInt("leilao.anti-snipe-minutos", 5)) * 60_000L;
        long agora = System.currentTimeMillis();
        if (antiSnipe > 0 && leilao.getTerminaEm() - agora < antiSnipe) leilao.setTerminaEm(agora + antiSnipe);
        salvar();
        plugin.getPlacas().atualizar(t.getId());

        anunciar("§6[Leilão de imóvel] §f" + jogador.getName()
                + " §edeu §f" + Formato.valor(valor) + " §eno terreno §f" + t.getId() + "§e. Termina em §f"
                + Formato.tempo(leilao.getTerminaEm() - agora) + "§e.");
    }

    private void devolverLance(Leilao leilao) {
        OfflinePlayer anterior = Bukkit.getOfflinePlayer(leilao.getLicitante());
        plugin.getEconomy().depositPlayer(anterior, leilao.getLance());
        Player online = anterior.getPlayer();
        if (online != null) {
            online.sendMessage("§eSeu lance de §f" + Formato.valor(leilao.getLance()) + " §efoi superado. O valor voltou pra sua conta.");
        }
    }

    /** O dono antigo quita a dívida de imposto pra poder dar lance. Já conferido o saldo. */
    public void pagarDivida(Player jogador, Terreno t) {
        Leilao leilao = t.getLeilao();
        plugin.getEconomy().withdrawPlayer(jogador, leilao.getDivida());
        leilao.setDividaPaga(true);
        plugin.getPixIntegration().registrarComServidor(jogador.getUniqueId(), leilao.getDivida(), false,
                "Dívida de imposto do terreno " + t.getId());
        salvar();
    }

    private void encerrarLeilao(Terreno t) {
        Leilao leilao = t.getLeilao();
        if (!leilao.temLance()) {
            terrenos.remove(t.getId());
            plugin.getLimpador().limpar(t.getId());
            salvar();
            plugin.getPlacas().atualizar(t.getId());
            anunciarLeilao("§6[Leilão de imóvel] §7O terreno §f" + t.getId()
                    + " §7não recebeu lances. Foi limpo e voltou à venda.");
            return;
        }
        // o lance já foi retirado no darLance: ele simplesmente não volta (some da economia)
        Terreno novo = novoDono(t.getId(), leilao.getLicitante(), leilao.getNomeLicitante(), leilao.getLance());
        plugin.getPixIntegration().registrarComServidor(leilao.getLicitante(), leilao.getLance(), false,
                "Leilão do terreno " + t.getId());
        salvar();
        plugin.getPlacas().atualizar(t.getId());
        anunciarLeilao("§6§l[Leilão de imóvel] §f" + leilao.getNomeLicitante()
                + " §earrematou o terreno §f" + novo.getId() + " §epor §f" + Formato.valor(leilao.getLance()) + "§e!");
        Player vencedor = Bukkit.getPlayer(leilao.getLicitante());
        if (vencedor != null) {
            vencedor.sendMessage("§aO terreno §f" + novo.getId() + " §aé seu, com tudo o que tem dentro. Vá até ele com §f/terreno home§a.");
        }
    }

    // ---------------------------------------------------------------------
    // Tick (1x por segundo)
    // ---------------------------------------------------------------------
    public void tick() {
        long agora = System.currentTimeMillis();
        for (Terreno t : new ArrayList<>(terrenos.values())) {
            if (t.emLeilao()) {
                if (agora >= t.getLeilao().getTerminaEm()) encerrarLeilao(t);
            }
        }
        // imposto: a cada 30 s basta
        if (++segundos % 30 != 0) return;
        for (Terreno t : new ArrayList<>(terrenos.values())) {
            if (!atrasado(t, agora)) continue;
            if (t.isAutoPagar() && pagarImposto(t)) {
                Player dono = Bukkit.getPlayer(t.getDono());
                if (dono != null) {
                    dono.sendMessage("§7Imposto do terreno §f" + t.getId() + " §7pago automaticamente: §f"
                            + Formato.valor(valorImposto(t.getDono())) + "§7.");
                }
                continue;
            }
            if (agora >= t.getImpostoPagoAte() + toleranciaMs()) {
                confiscar(t, "por imposto atrasado");
                continue;
            }
            avisarAtraso(t, agora, false);
        }
    }

    /** Lembrete de imposto atrasado (no login, e de tempos em tempos pra quem está online). */
    public void avisarAtraso(Terreno t, long agora, boolean forcar) {
        Player dono = Bukkit.getPlayer(t.getDono());
        if (dono == null) return;
        long intervalo = Math.max(1, plugin.getConfig().getInt("imposto.aviso-horas", 1)) * HORA;
        Long ultimo = ultimoAviso.get(t.getId());
        if (!forcar && ultimo != null && agora - ultimo < intervalo) return;
        ultimoAviso.put(t.getId(), agora);
        long resta = t.getImpostoPagoAte() + toleranciaMs() - agora;
        dono.sendMessage("§c§lImposto atrasado! §cO terreno §f" + t.getId() + " §cdeve §f"
                + Formato.valor(valorImposto(t.getDono())) + "§c. Pague com §f/terreno imposto pagar§c em até §f"
                + Formato.tempo(resta) + "§c, ou ele vai a leilão com tudo o que tem dentro.");
    }

    private static void anunciar(String mensagem) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(mensagem));
    }

    /** Anuncia no chat e no Discord (categoria "leiloes"). */
    private void anunciarLeilao(String mensagem) {
        anunciar(mensagem);
        plugin.getDiscord().enviar("leiloes", mensagem);
    }

    // ---------------------------------------------------------------------
    // Persistência (terrenos.yml)
    // ---------------------------------------------------------------------
    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection secao = yaml.getConfigurationSection("terrenos");
        if (secao == null) return;
        for (String chave : secao.getKeys(false)) {
            TerrenoId id = TerrenoId.ler(chave);
            ConfigurationSection s = secao.getConfigurationSection(chave);
            if (id == null || s == null) {
                plugin.getLogger().warning("Terreno inválido ignorado em terrenos.yml: " + chave);
                continue;
            }
            try {
                Terreno t = new Terreno(id);
                if (s.isString("dono")) t.setDono(UUID.fromString(s.getString("dono")), s.getString("nome-dono"));
                if (s.isList("amigos")) {
                    // formato antigo (só a lista): amigo podia tudo
                    for (String amigo : s.getStringList("amigos")) t.getAmigos().put(UUID.fromString(amigo), Terreno.todas());
                } else if (s.isConfigurationSection("amigos")) {
                    for (String amigo : s.getConfigurationSection("amigos").getKeys(false)) {
                        Set<Permissao> perms = EnumSet.noneOf(Permissao.class);
                        for (String nome : s.getStringList("amigos." + amigo)) {
                            Permissao p = Permissao.ler(nome);
                            if (p != null) perms.add(p);
                        }
                        t.getAmigos().put(UUID.fromString(amigo), perms);
                    }
                }
                t.setImpostoPagoAte(s.getLong("imposto-pago-ate"));
                t.setAutoPagar(s.getBoolean("auto-pagar", true));
                t.setCompradoEm(s.getLong("comprado-em"));
                t.setUltimoPreco(s.getDouble("ultimo-preco"));
                ConfigurationSection l = s.getConfigurationSection("leilao");
                if (l != null) {
                    String anterior = l.getString("dono-anterior");
                    Leilao leilao = new Leilao(l.getLong("termina-em"), anterior == null ? null : UUID.fromString(anterior),
                            l.getString("nome-dono-anterior"), l.getDouble("divida"));
                    leilao.setDividaPaga(l.getBoolean("divida-paga"));
                    if (l.isString("licitante")) {
                        leilao.registrarLance(UUID.fromString(l.getString("licitante")), l.getString("nome-licitante"), l.getDouble("lance"));
                    }
                    t.setLeilao(leilao);
                }
                if (t.temDono() || t.emLeilao()) terrenos.put(id, t);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Terreno " + chave + " com UUID inválido em terrenos.yml, ignorado.");
            }
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Terreno t : terrenos.values()) {
            String base = "terrenos." + t.getId() + ".";
            if (t.temDono()) {
                yaml.set(base + "dono", t.getDono().toString());
                yaml.set(base + "nome-dono", t.getNomeDono());
                t.getAmigos().forEach((amigo, perms) ->
                        yaml.set(base + "amigos." + amigo, perms.stream().map(Permissao::nome).toList()));
                yaml.set(base + "imposto-pago-ate", t.getImpostoPagoAte());
                yaml.set(base + "auto-pagar", t.isAutoPagar());
                yaml.set(base + "comprado-em", t.getCompradoEm());
                yaml.set(base + "ultimo-preco", t.getUltimoPreco());
            }
            Leilao l = t.getLeilao();
            if (l != null) {
                yaml.set(base + "leilao.termina-em", l.getTerminaEm());
                if (l.getDonoAnterior() != null) yaml.set(base + "leilao.dono-anterior", l.getDonoAnterior().toString());
                yaml.set(base + "leilao.nome-dono-anterior", l.getNomeDonoAnterior());
                yaml.set(base + "leilao.divida", l.getDivida());
                yaml.set(base + "leilao.divida-paga", l.isDividaPaga());
                if (l.temLance()) {
                    yaml.set(base + "leilao.licitante", l.getLicitante().toString());
                    yaml.set(base + "leilao.nome-licitante", l.getNomeLicitante());
                    yaml.set(base + "leilao.lance", l.getLance());
                }
            }
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar terrenos.yml", e);
        }
    }

    /** Usado pela staff: passa o terreno pra um dono, encerrando o leilão (e devolvendo o lance). */
    public Terreno definirDono(TerrenoId id, OfflinePlayer jogador) {
        Terreno atual = terrenos.get(id);
        if (atual != null && atual.emLeilao() && atual.getLeilao().temLance()) devolverLance(atual.getLeilao());
        Terreno t = novoDono(id, jogador.getUniqueId(), jogador.getName(), 0);
        salvar();
        plugin.getPlacas().atualizar(id);
        return t;
    }

    /** Usado pela staff: tira o terreno de quem tem (ou encerra o leilão), sem limpar. */
    public void remover(TerrenoId id) {
        Terreno atual = terrenos.remove(id);
        if (atual != null && atual.emLeilao() && atual.getLeilao().temLance()) devolverLance(atual.getLeilao());
        salvar();
        plugin.getPlacas().atualizar(id);
    }
}
