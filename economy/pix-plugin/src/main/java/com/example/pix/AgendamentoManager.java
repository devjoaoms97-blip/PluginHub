package com.example.pix;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Pix agendado: um pagamento que se repete a cada N dias (aluguel combinado entre jogadores,
 * salário do clã, mesada...). O primeiro sai na hora, com as mesmas regras do /pix pagar (saldo,
 * limite diário e taxa); os próximos saem sozinhos no vencimento, mesmo com os dois offline. Sem
 * saldo ou sem limite, tenta de novo a cada hora; depois de {@code agendamento.falhas-maximas}
 * falhas seguidas, o agendamento é cancelado e os dois são avisados. Salvo em agendamentos.yml.
 */
public class AgendamentoManager {

    public static final class Agendamento {
        final int id;
        final UUID pagador;
        final String nomePagador;
        final UUID recebedor;
        final String nomeRecebedor;
        final double valor;
        final int dias;
        final String motivo;
        long proximo;
        int falhas;

        Agendamento(int id, UUID pagador, String nomePagador, UUID recebedor, String nomeRecebedor, double valor, int dias,
                    String motivo, long proximo, int falhas) {
            this.id = id;
            this.pagador = pagador;
            this.nomePagador = nomePagador;
            this.recebedor = recebedor;
            this.nomeRecebedor = nomeRecebedor;
            this.valor = valor;
            this.dias = dias;
            this.motivo = motivo;
            this.proximo = proximo;
            this.falhas = falhas;
        }

        public int getId() {
            return id;
        }

        public UUID getPagador() {
            return pagador;
        }

        public UUID getRecebedor() {
            return recebedor;
        }

        public String getNomePagador() {
            return nomePagador;
        }

        public String getNomeRecebedor() {
            return nomeRecebedor;
        }

        public double getValor() {
            return valor;
        }

        public int getDias() {
            return dias;
        }

        public String getMotivo() {
            return motivo;
        }

        public long getProximo() {
            return proximo;
        }
    }

    private static final long DIA = 86_400_000L;
    private static final long HORA = 3_600_000L;

    private final PixPlugin plugin;
    private final File arquivo;
    private final Map<Integer, Agendamento> agendamentos = new LinkedHashMap<>();
    private int proximoId = 1;

    public AgendamentoManager(PixPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "agendamentos.yml");
        carregar();
    }

    public List<Agendamento> doJogador(UUID jogador) {
        List<Agendamento> lista = new ArrayList<>();
        for (Agendamento a : agendamentos.values()) {
            if (a.pagador.equals(jogador) || a.recebedor.equals(jogador)) lista.add(a);
        }
        return lista;
    }

    public int quantosPaga(UUID jogador) {
        return (int) agendamentos.values().stream().filter(a -> a.pagador.equals(jogador)).count();
    }

    public Agendamento get(int id) {
        return agendamentos.get(id);
    }

    public int maximoPorJogador() {
        return Math.max(1, plugin.getConfig().getInt("agendamento.maximo-por-jogador", 5));
    }

    /** Cria e já faz o primeiro pagamento (conferido antes: saldo e limite). */
    public Agendamento criar(Player pagador, OfflinePlayer recebedor, double valor, int dias, String motivo) {
        Agendamento a = new Agendamento(proximoId++, pagador.getUniqueId(), pagador.getName(), recebedor.getUniqueId(),
                recebedor.getName(), valor, dias, motivo, System.currentTimeMillis(), 0);
        agendamentos.put(a.id, a);
        executar(a);
        salvar();
        return a;
    }

    /** Staff (pra testar): o agendamento vence agora e sai na próxima verificação. */
    public void vencerAgora(Agendamento a) {
        a.proximo = System.currentTimeMillis();
        salvar();
    }

    public void cancelar(Agendamento a) {
        agendamentos.remove(a.id);
        salvar();
    }

    /** A cada minuto: paga o que venceu. */
    public void tick() {
        long agora = System.currentTimeMillis();
        boolean mudou = false;
        for (Agendamento a : new ArrayList<>(agendamentos.values())) {
            if (agora < a.proximo) continue;
            executar(a);
            mudou = true;
        }
        if (mudou) salvar();
    }

    /** Tenta o pagamento. Sem saldo ou limite, marca uma falha e tenta de novo em 1 h. */
    private void executar(Agendamento a) {
        Economy econ = plugin.getEconomy();
        if (econ == null) return;
        OfflinePlayer pagador = Bukkit.getOfflinePlayer(a.pagador);
        OfflinePlayer recebedor = Bukkit.getOfflinePlayer(a.recebedor);
        Player online = pagador.getPlayer();
        LimiteManager limites = plugin.getLimiteManager();
        // offline não dá pra ver as permissões: sem desconto de VIP na taxa e sujeito ao limite
        boolean dentroDoLimite = online != null ? limites.podeEnviar(online, a.valor)
                : limites.getLimiteDiario() <= 0 || a.valor <= limites.getDisponivelHoje(a.pagador) + 0.000001;
        if (!econ.has(pagador, a.valor) || !dentroDoLimite) {
            falhou(a, !econ.has(pagador, a.valor) ? "saldo insuficiente" : "passaria do limite diário");
            return;
        }
        double taxa = online != null ? plugin.calcularTaxaTransacao(a.valor, online) : plugin.calcularTaxaTransacao(a.valor);
        econ.withdrawPlayer(pagador, a.valor);
        econ.depositPlayer(recebedor, a.valor - taxa);
        plugin.getChargeManager().registrarTransacao(a.pagador, a.recebedor, a.valor, "Pix agendado" + (a.motivo != null ? ": " + a.motivo : ""), taxa);
        limites.registrarEnvio(a.pagador, a.valor);
        plugin.conferirSuspeito(a.nomePagador, a.nomeRecebedor, a.valor, "Pix agendado");
        a.falhas = 0;
        a.proximo = Math.max(a.proximo, System.currentTimeMillis() - DIA) + (long) a.dias * DIA;
        avisar(online, "§aPix agendado #" + a.id + ": você pagou §f" + valor(a.valor) + " §apara §f" + a.nomeRecebedor
                + (taxa > 0 ? " §7(taxa: " + valor(taxa) + ")" : "") + "§a. Próximo em §f" + a.dias + " dia(s)§a.");
        avisar(recebedor.getPlayer(), "§aVocê recebeu §f" + valor(a.valor - taxa) + " §ade §f" + a.nomePagador
                + " §7(Pix agendado" + (a.motivo != null ? ": " + a.motivo : "") + ")§a.");
        plugin.notificarRecebimento(recebedor.getPlayer(), a.valor - taxa, a.nomePagador);
    }

    private void falhou(Agendamento a, String motivo) {
        a.falhas++;
        int maximo = Math.max(1, plugin.getConfig().getInt("agendamento.falhas-maximas", 3));
        Player pagador = Bukkit.getPlayer(a.pagador);
        if (a.falhas >= maximo) {
            agendamentos.remove(a.id);
            avisar(pagador, "§cSeu Pix agendado #" + a.id + " para §f" + a.nomeRecebedor + " §cfoi cancelado: " + motivo
                    + " em " + maximo + " tentativas seguidas.");
            avisar(Bukkit.getPlayer(a.recebedor), "§cO Pix agendado de §f" + a.nomePagador + " §cpara você foi cancelado ("
                    + motivo + " de quem paga).");
            return;
        }
        a.proximo = System.currentTimeMillis() + HORA;
        avisar(pagador, "§cPix agendado #" + a.id + " para §f" + a.nomeRecebedor + " §cnão saiu: " + motivo
                + ". Tento de novo em 1 h (" + a.falhas + "/" + maximo + ").");
    }

    private static void avisar(Player jogador, String mensagem) {
        if (jogador != null) jogador.sendMessage(ChatColor.translateAlternateColorCodes('&', mensagem));
    }

    private static String valor(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }

    // ---------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------
    private void carregar() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        proximoId = Math.max(1, yaml.getInt("proximo-id", 1));
        ConfigurationSection secao = yaml.getConfigurationSection("agendamentos");
        if (secao == null) return;
        for (String chave : secao.getKeys(false)) {
            ConfigurationSection s = secao.getConfigurationSection(chave);
            try {
                Agendamento a = new Agendamento(Integer.parseInt(chave), UUID.fromString(s.getString("pagador")), s.getString("nome-pagador"),
                        UUID.fromString(s.getString("recebedor")), s.getString("nome-recebedor"), s.getDouble("valor"), s.getInt("dias"),
                        s.getString("motivo"), s.getLong("proximo"), s.getInt("falhas"));
                agendamentos.put(a.id, a);
                proximoId = Math.max(proximoId, a.id + 1);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Agendamento inválido ignorado em agendamentos.yml: " + chave);
            }
        }
    }

    public synchronized void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("proximo-id", proximoId);
        for (Agendamento a : agendamentos.values()) {
            String b = "agendamentos." + a.id + ".";
            yaml.set(b + "pagador", a.pagador.toString());
            yaml.set(b + "nome-pagador", a.nomePagador);
            yaml.set(b + "recebedor", a.recebedor.toString());
            yaml.set(b + "nome-recebedor", a.nomeRecebedor);
            yaml.set(b + "valor", a.valor);
            yaml.set(b + "dias", a.dias);
            yaml.set(b + "motivo", a.motivo);
            yaml.set(b + "proximo", a.proximo);
            yaml.set(b + "falhas", a.falhas);
        }
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar agendamentos.yml", e);
        }
    }
}
