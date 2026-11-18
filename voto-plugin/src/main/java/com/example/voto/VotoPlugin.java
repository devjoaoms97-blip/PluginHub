package com.example.voto;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Voto com recompensa (2026-11-18). O Votifier avisa cada voto de site de lista; o voto dá uma Chave de Voto
 * (CaixasPlugin), o primeiro do dia paga o bônus da sequência de dias seguidos (com chave extra no último
 * dia), cada N votos do servidor fazem a Festa do Voto (chave pra todo mundo online), e no começo do mês os que
 * mais votaram no mês anterior ganham chaves. Quem entra sem ter votado no dia recebe um lembrete clicável.
 *
 * <p>O evento do Votifier é escutado por reflexão (sem dependência de compilação): sem o Votifier, o plugin
 * só avisa no console, e a staff ainda pode simular votos com {@code /votar admin simular}.
 */
public class VotoPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    private Economy economia;
    private File arquivo;
    private YamlConfiguration dados;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economia = rsp == null ? null : rsp.getProvider();
        arquivo = new File(getDataFolder(), "votos.yml");
        dados = YamlConfiguration.loadConfiguration(arquivo);
        getServer().getPluginManager().registerEvents(this, this);
        escutarVotifier();
        Bukkit.getScheduler().runTaskTimer(this, this::checarMes, 100L, 20L * 60 * 10);
    }

    @Override
    public void onDisable() {
        salvar();
    }

    private void salvar() {
        try {
            dados.save(arquivo);
        } catch (IOException e) {
            getLogger().warning("Não salvou votos.yml: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void escutarVotifier() {
        Class<? extends Event> classe;
        try {
            classe = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
        } catch (ClassNotFoundException e) {
            getLogger().warning("Votifier não encontrado: os votos dos sites não chegam (só o /votar admin simular).");
            return;
        }
        getServer().getPluginManager().registerEvent(classe, this, EventPriority.NORMAL, (l, evento) -> {
            if (!classe.isInstance(evento)) return;
            try {
                Object voto = evento.getClass().getMethod("getVote").invoke(evento);
                String nome = (String) voto.getClass().getMethod("getUsername").invoke(voto);
                String site = (String) voto.getClass().getMethod("getServiceName").invoke(voto);
                // o evento pode vir fora da thread principal
                Bukkit.getScheduler().runTask(this, () -> votou(nome, site));
            } catch (ReflectiveOperationException e) {
                getLogger().warning("Voto do Votifier que não deu pra ler: " + e.getMessage());
            }
        }, this);
        getLogger().info("Escutando os votos do Votifier.");
    }

    // ------------------------------------------------------------------ utilidades

    private static String cores(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private String msg(String chave) {
        return cores(getConfig().getString("mensagens." + chave, ""));
    }

    private ZoneId fuso() {
        try {
            return ZoneId.of(getConfig().getString("fuso", "America/Sao_Paulo"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private long hoje() {
        return LocalDate.now(fuso()).toEpochDay();
    }

    private static String dinheiro(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,.0f", v);
    }

    private Plugin ativo(String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nome);
        return p != null && p.isEnabled() ? p : null;
    }

    private void darChaves(UUID jogador, String caixa, int qtd, String motivo) {
        Plugin caixas = ativo("CaixasPlugin");
        if (caixas == null || qtd <= 0) return;
        try {
            caixas.getClass().getMethod("darChave", UUID.class, String.class, int.class, String.class).invoke(caixas, jogador, caixa, qtd, motivo);
        } catch (ReflectiveOperationException e) {
            getLogger().warning("CaixasPlugin#darChave falhou: " + e.getMessage());
        }
    }

    private double precoProximoRank(UUID jogador) {
        Plugin rankup = ativo("RankupPlugin");
        if (rankup == null) return -1;
        try {
            return ((Number) rankup.getClass().getMethod("precoProximoRank", UUID.class).invoke(rankup, jogador)).doubleValue();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return -1;
        }
    }

    private void extrato(UUID jogador, double valor, String motivo) {
        Plugin pix = ativo("PixPlugin");
        if (pix == null) return;
        try {
            Object charges = pix.getClass().getMethod("getChargeManager").invoke(pix);
            charges.getClass().getMethod("registrarTransacaoComEntidade", UUID.class, String.class, double.class, boolean.class, String.class)
                    .invoke(charges, jogador, "Votos", valor, true, motivo);
        } catch (ReflectiveOperationException e) {
            // sem extrato, sem problema
        }
    }

    private void discord(String mensagem) {
        Plugin d = ativo("DiscordPlugin");
        if (d == null) return;
        try {
            d.getClass().getMethod("enviar", String.class, String.class).invoke(d, "votos", mensagem);
        } catch (ReflectiveOperationException ignored) {
            // opcional
        }
    }

    // ------------------------------------------------------------------ o voto

    /** Um voto do jogador (pelo nome) num site. Público pra outros plugins e pro /votar admin simular. */
    public boolean votou(String nome, String site) {
        Player online = Bukkit.getPlayerExact(nome);
        OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayerIfCached(nome);
        if (op == null || (!op.isOnline() && !op.hasPlayedBefore())) {
            getLogger().info("Voto de \"" + nome + "\" (" + site + ") ignorado: nunca entrou no servidor.");
            return false;
        }
        UUID id = op.getUniqueId();
        String base = "jogadores." + id;
        String nomeCerto = op.getName() != null ? op.getName() : nome;
        long hoje = hoje();
        long ultimo = dados.getLong(base + ".ultimo-dia", Long.MIN_VALUE);
        List<Double> percentuais = getConfig().getDoubleList("sequencia.percentual-do-proximo-rank");
        int dias = Math.max(1, percentuais.size());
        int seq = dados.getInt(base + ".sequencia", 0);
        boolean primeiroDoDia = ultimo != hoje;
        if (primeiroDoDia) {
            seq = (ultimo == hoje - 1 && seq < dias) ? seq + 1 : 1;
            dados.set(base + ".ultimo-dia", hoje);
            dados.set(base + ".sequencia", seq);
        }
        dados.set(base + ".nome", nomeCerto);
        dados.set(base + ".mes", dados.getInt(base + ".mes") + 1);
        dados.set(base + ".total", dados.getInt(base + ".total") + 1);

        darChaves(id, getConfig().getString("chave.caixa", "voto"), getConfig().getInt("chave.quantidade", 1), "voto em " + site);
        if (online != null) {
            online.sendMessage(msg("prefixo") + msg("obrigado").replace("%site%", site).replace("%dia%", String.valueOf(seq))
                    .replace("%dias%", String.valueOf(dias)));
        }
        if (primeiroDoDia) {
            double preco = precoProximoRank(id);
            double pct = percentuais.isEmpty() ? 0 : percentuais.get(seq - 1);
            double valor = Math.round(Math.max(getConfig().getDouble("sequencia.minimo", 0), preco > 0 ? preco * pct / 100.0 : 0));
            if (economia != null && valor > 0) {
                economia.depositPlayer(op, valor);
                extrato(id, valor, "Voto: dia " + seq + " da sequência");
                if (online != null) online.sendMessage(msg("prefixo") + msg("bonus").replace("%valor%", dinheiro(valor)).replace("%dia%", String.valueOf(seq)));
            }
            if (seq == dias) {
                darChaves(id, getConfig().getString("chave.caixa", "voto"), getConfig().getInt("sequencia.chave-extra", 0),
                        dias + " dias seguidos votando");
            }
        }
        if (getConfig().getBoolean("anunciar-votos", true)) {
            String a = msg("anuncio").replace("%jogador%", nomeCerto);
            Bukkit.broadcastMessage(a);
            discord(a);
        }
        festa();
        salvar();
        return true;
    }

    private void festa() {
        int meta = Math.max(1, getConfig().getInt("festa.votos", 50));
        int contador = dados.getInt("festa.contador") + 1;
        if (contador >= meta) {
            contador = 0;
            String caixa = getConfig().getString("festa.caixa", "voto");
            int chaves = getConfig().getInt("festa.chaves", 1);
            for (Player p : Bukkit.getOnlinePlayers()) darChaves(p.getUniqueId(), caixa, chaves, "Festa do Voto");
            String a = msg("festa").replace("%votos%", String.valueOf(meta)).replace("%chaves%", String.valueOf(chaves));
            Bukkit.broadcastMessage(a);
            discord(a);
        } else if (getConfig().getIntegerList("festa.avisos").contains(meta - contador)) {
            Bukkit.broadcastMessage(msg("festa-falta").replace("%falta%", String.valueOf(meta - contador)));
        }
        dados.set("festa.contador", contador);
    }

    // ------------------------------------------------------------------ ranking do mês

    /** Os jogadores com votos no mês, do que mais votou pro que menos. */
    private List<Map.Entry<String, Integer>> rankingDoMes() {
        List<Map.Entry<String, Integer>> l = new ArrayList<>();
        ConfigurationSection s = dados.getConfigurationSection("jogadores");
        if (s == null) return l;
        for (String id : s.getKeys(false)) {
            int n = s.getInt(id + ".mes");
            if (n > 0) l.add(Map.entry(id, n));
        }
        l.sort((a, b) -> b.getValue() - a.getValue());
        return l;
    }

    /** API pública (DiscordPlugin, pro site): votos do mês, total e sequência de dias; null se nunca votou. */
    public Map<String, Object> dadosDoSite(UUID jogador) {
        String base = "jogadores." + jogador;
        if (!dados.isConfigurationSection(base)) return null;
        long ultimo = dados.getLong(base + ".ultimo-dia", Long.MIN_VALUE);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("mes", dados.getInt(base + ".mes"));
        m.put("total", dados.getInt(base + ".total"));
        m.put("sequencia", ultimo >= hoje() - 1 ? dados.getInt(base + ".sequencia") : 0);
        m.put("votouHoje", ultimo == hoje());
        return m;
    }

    /** API pública (DiscordPlugin, pro site): a festa do voto e os links de votação. */
    public Map<String, Object> resumoDoSite() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("festa", dados.getInt("festa.contador"));
        m.put("festaMeta", getConfig().getInt("festa.votos", 50));
        m.put("mes", dados.getString("mes"));
        List<Map<String, Object>> sites = new ArrayList<>();
        for (Map<?, ?> s : getConfig().getMapList("sites")) {
            Map<String, Object> site = new java.util.LinkedHashMap<>();
            site.put("nome", String.valueOf(s.get("nome")));
            site.put("url", String.valueOf(s.get("url")));
            sites.add(site);
        }
        m.put("sites", sites);
        return m;
    }

    private String nomeDe(String uuid) {
        return dados.getString("jogadores." + uuid + ".nome", uuid.substring(0, 8));
    }

    /** Virou o mês: paga o ranking do mês que acabou e zera. */
    private void checarMes() {
        String atual = YearMonth.now(fuso()).toString();
        String salvo = dados.getString("mes");
        if (salvo == null) {
            dados.set("mes", atual);
            salvar();
            return;
        }
        if (salvo.equals(atual)) return;
        pagarRanking(salvo);
        dados.set("mes", atual);
        salvar();
    }

    private void pagarRanking(String mes) {
        List<Integer> premios = getConfig().getIntegerList("ranking-mes.premios");
        String caixa = getConfig().getString("ranking-mes.caixa", "boss");
        List<Map.Entry<String, Integer>> top = rankingDoMes();
        StringBuilder lista = new StringBuilder();
        for (int i = 0; i < Math.min(premios.size(), top.size()); i++) {
            String id = top.get(i).getKey();
            darChaves(UUID.fromString(id), caixa, premios.get(i), (i + 1) + "º no ranking de votos de " + mes);
            if (!lista.isEmpty()) lista.append("§7, ");
            lista.append("§e").append(i + 1).append("º §f").append(nomeDe(id)).append(" §7(").append(top.get(i).getValue())
                    .append(" votos, ").append(premios.get(i)).append(" chaves)");
        }
        ConfigurationSection s = dados.getConfigurationSection("jogadores");
        if (s != null) for (String id : s.getKeys(false)) s.set(id + ".mes", 0);
        if (!lista.isEmpty()) {
            String a = msg("ranking-pago").replace("%mes%", mes).replace("%lista%", lista.toString());
            Bukkit.broadcastMessage(a);
            discord(a);
        }
    }

    // ------------------------------------------------------------------ lembrete e /votar

    @EventHandler
    public void aoEntrar(PlayerJoinEvent evento) {
        if (!getConfig().getBoolean("lembrete.ativado", true)) return;
        Player p = evento.getPlayer();
        if (dados.getLong("jogadores." + p.getUniqueId() + ".ultimo-dia", Long.MIN_VALUE) == hoje()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || dados.getLong("jogadores." + p.getUniqueId() + ".ultimo-dia", Long.MIN_VALUE) == hoje()) return;
            p.sendMessage(LEGADO.deserialize(msg("prefixo") + msg("lembrete")).append(botao("[VOTAR]", "/votar", "Ver os links pra votar")));
        }, 20L * Math.max(0, getConfig().getInt("lembrete.atraso-segundos", 8)));
    }

    private static Component botao(String texto, String comando, String dica) {
        return Component.text(texto, NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand(comando))
                .hoverEvent(HoverEvent.showText(Component.text(dica)));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("admin") && s.hasPermission("voto.admin")) return admin(s, a);
        if (a.length >= 1 && a[0].equalsIgnoreCase("top")) {
            s.sendMessage(msg("prefixo") + "§fQuem mais votou em " + YearMonth.now(fuso()) + ":");
            List<Map.Entry<String, Integer>> top = rankingDoMes();
            for (int i = 0; i < Math.min(10, top.size()); i++) {
                s.sendMessage("§e " + (i + 1) + "º §f" + nomeDe(top.get(i).getKey()) + " §7" + top.get(i).getValue() + " votos");
            }
            if (top.isEmpty()) s.sendMessage("§7 Ninguém votou ainda este mês.");
            return true;
        }
        s.sendMessage("§b§l✦ Vote no servidor! §7Cada voto: §b1 Chave de Voto §7(abra na Praça das Caixas do spawn).");
        List<Map<?, ?>> sites = getConfig().getMapList("sites");
        if (sites.isEmpty()) {
            s.sendMessage("§7 Os sites de votação ainda vão ser cadastrados. Fique de olho!");
        } else {
            Component linha = Component.text(" ");
            for (Map<?, ?> site : sites) {
                String nome = String.valueOf(site.get("nome")), url = String.valueOf(site.get("url"));
                linha = linha.append(Component.text("[" + nome + "]", NamedTextColor.GREEN).clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("Abrir " + url)))).append(Component.text("  "));
            }
            s.sendMessage(linha);
        }
        int dias = Math.max(1, getConfig().getDoubleList("sequencia.percentual-do-proximo-rank").size());
        if (s instanceof Player p) {
            String base = "jogadores." + p.getUniqueId();
            long ultimo = dados.getLong(base + ".ultimo-dia", Long.MIN_VALUE);
            int seq = ultimo >= hoje() - 1 ? dados.getInt(base + ".sequencia") : 0;
            boolean votouHoje = ultimo == hoje();
            s.sendMessage("§7 Sequência: §f" + seq + "/" + dias + " dias" + (votouHoje ? " §a(votou hoje)" : " §e(ainda não votou hoje)")
                    + "§7. Votando " + dias + " dias seguidos, chave extra.");
            s.sendMessage("§7 Seus votos este mês: §f" + dados.getInt(base + ".mes"));
        }
        int meta = Math.max(1, getConfig().getInt("festa.votos", 50));
        s.sendMessage("§7 Festa do Voto: faltam §e" + (meta - dados.getInt("festa.contador")) + " votos §7(todo mundo online ganha chave).");
        List<Map.Entry<String, Integer>> top = rankingDoMes();
        if (!top.isEmpty()) {
            StringBuilder sb = new StringBuilder("§7 Top do mês: ");
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                if (i > 0) sb.append("§7, ");
                sb.append("§e").append(i + 1).append("º §f").append(nomeDe(top.get(i).getKey())).append(" §7(").append(top.get(i).getValue()).append(")");
            }
            s.sendMessage(sb + " §8/votar top");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "simular" -> {
                if (a.length < 3) {
                    s.sendMessage("§cUso: /votar admin simular <jogador> [site]");
                    return true;
                }
                boolean ok = votou(a[2], a.length >= 4 ? a[3] : "teste");
                s.sendMessage(ok ? "§aVoto simulado de " + a[2] + "." : "§cJogador nunca entrou: " + a[2]);
            }
            case "festa" -> {
                if (a.length >= 3) {
                    dados.set("festa.contador", Integer.parseInt(a[2]));
                    salvar();
                }
                s.sendMessage("§aContador da festa: " + dados.getInt("festa.contador") + "/" + getConfig().getInt("festa.votos", 50));
            }
            case "fimdomes" -> {
                pagarRanking(dados.getString("mes", YearMonth.now(fuso()).toString()));
                salvar();
                s.sendMessage("§aRanking do mês pago e zerado.");
            }
            case "sequencia" -> {
                // /votar admin sequencia <jogador> <dia> [ontem]: põe a sequência (pra teste)
                OfflinePlayer op = a.length >= 4 ? Bukkit.getOfflinePlayerIfCached(a[2]) : null;
                if (op == null) {
                    s.sendMessage("§cUso: /votar admin sequencia <jogador> <dia> (o último voto fica ontem)");
                    return true;
                }
                dados.set("jogadores." + op.getUniqueId() + ".sequencia", Integer.parseInt(a[3]));
                dados.set("jogadores." + op.getUniqueId() + ".ultimo-dia", hoje() - 1);
                salvar();
                s.sendMessage("§aSequência de " + op.getName() + ": dia " + a[3] + ", último voto ontem.");
            }
            case "reload" -> {
                reloadConfig();
                s.sendMessage("§aVotoPlugin recarregado.");
            }
            default -> s.sendMessage("§7/votar admin simular <jogador> [site] | festa [n] | fimdomes | sequencia <jogador> <dia> | reload");
        }
        return true;
    }
}
