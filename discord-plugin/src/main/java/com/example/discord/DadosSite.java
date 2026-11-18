package com.example.discord;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A foto dos jogadores pro site (GET /site da {@link ApiLocal}): dinheiro (Vault), rank e prestígio
 * (RankupPlugin), skills (SkillsPlugin), clã (ClasPlugin), VIP, apoiador (CreditosPlugin) e votos
 * (VotoPlugin), mais a lista dos clãs e o resumo do voto. Cada plugin entrega a parte dele por
 * {@code dadosDoSite(UUID)}, por reflexão: plugin desligado ou de versão antiga só deixa o campo de fora.
 *
 * A foto é montada FORA da thread do servidor, a cada {@code site.intervalo-minutos}: ler o nome, as
 * datas, o saldo e as skills de quem está offline abre um arquivo por jogador (com 1.600 contas, isso
 * travou o servidor por mais de 10 s na thread principal). Os {@code dadosDoSite} dos outros plugins só
 * leem mapas que já estão na memória. A foto fica pronta em JSON; a API só devolve o texto. O site
 * calcula os rankings em cima dela.
 */
public class DadosSite {

    private final DiscordPlugin plugin;
    private final Vinculos vinculos;
    private final Gson gson = new Gson();
    private volatile String json = "{\"ok\":false,\"erro\":\"a foto do site ainda não foi montada\"}";

    public DadosSite(DiscordPlugin plugin, Vinculos vinculos) {
        this.plugin = plugin;
        this.vinculos = vinculos;
    }

    public void iniciar() {
        long minutos = Math.max(1, plugin.getConfig().getLong("site.intervalo-minutos", 5));
        // Espera uns segundos depois de ligar: todos os plugins já estão de pé.
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::montar, 20L * 10, 20L * 60 * minutos);
    }

    /** A última foto, em JSON. */
    public String json() {
        return json;
    }

    /** Monta a foto agora. Demora (lê arquivos): chamar fora da thread do servidor. */
    public synchronized void montar() {
        long inicio = System.currentTimeMillis();
        Object economia = economia();
        Plugin rankup = ligado("RankupPlugin"), skills = ligado("SkillsPlugin"), clas = ligado("ClasPlugin"),
                voto = ligado("VotoPlugin"), creditos = ligado("CreditosPlugin");

        List<Map<String, Object>> jogadores = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() == null || !(op.hasPlayedBefore() || op.isOnline())) continue;
            UUID id = op.getUniqueId();
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("uuid", id.toString());
            j.put("nick", op.getName());
            j.put("online", op.isOnline());
            j.put("primeiroLogin", op.getFirstPlayed());
            j.put("ultimoLogin", op.isOnline() ? System.currentTimeMillis() : op.getLastSeen());
            Object dinheiro = economia == null ? null : invocar(economia, "getBalance", new Class<?>[]{OfflinePlayer.class}, op);
            if (dinheiro instanceof Number n) j.put("dinheiro", Math.floor(n.doubleValue() * 100) / 100);
            colocar(j, "rank", rankup, id);
            colocar(j, "skills", skills, id);
            colocar(j, "cla", clas, id);
            colocar(j, "votos", voto, id);
            String vip = vinculos.tierVip(id);
            if (vip != null) j.put("vip", vip);
            Object apoiador = creditos == null ? null : invocar(creditos, "tagApoiador", new Class<?>[]{UUID.class}, id);
            if (apoiador != null) j.put("apoiador", true);
            jogadores.add(j);
        }

        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("ok", true);
        foto.put("geradoEm", System.currentTimeMillis());
        foto.put("jogadores", jogadores);
        Object listaClas = clas == null ? null : invocar(clas, "clasDoSite", new Class<?>[0]);
        if (listaClas != null) foto.put("clas", listaClas);
        Object resumoVoto = voto == null ? null : invocar(voto, "resumoDoSite", new Class<?>[0]);
        if (resumoVoto != null) foto.put("voto", resumoVoto);
        json = gson.toJson(foto);
        long levou = System.currentTimeMillis() - inicio;
        if (levou > 2000) plugin.getLogger().info("Foto do site: " + jogadores.size() + " jogadores em " + levou + " ms.");
    }

    /** {@code dadosDoSite(UUID)} do plugin, com as cores (§) tiradas dos textos, menos a "cor". */
    private void colocar(Map<String, Object> j, String campo, Plugin outro, UUID id) {
        if (outro == null) return;
        Object dados = invocar(outro, "dadosDoSite", new Class<?>[]{UUID.class}, id);
        if (dados instanceof Map<?, ?> m && !m.isEmpty()) j.put(campo, limpar(m));
    }

    private static Map<String, Object> limpar(Map<?, ?> m) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s && !"cor".equals(e.getKey())) v = ChatColor.stripColor(s);
            else if (v instanceof Map<?, ?> filho) v = limpar(filho);
            r.put(String.valueOf(e.getKey()), v);
        }
        return r;
    }

    private static Plugin ligado(String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(nome);
        return p != null && p.isEnabled() ? p : null;
    }

    private Object invocar(Object alvo, String metodo, Class<?>[] tipos, Object... args) {
        try {
            Method m = alvo.getClass().getMethod(metodo, tipos);
            return m.invoke(alvo, args);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().fine("Site: " + alvo.getClass().getSimpleName() + "." + metodo + " falhou: " + e);
            return null;
        }
    }

    /** O provedor da economia do Vault, sem depender do Vault pra compilar. */
    private static Object economia() {
        for (Class<?> servico : Bukkit.getServicesManager().getKnownServices()) {
            if (!servico.getName().equals("net.milkbowl.vault.economy.Economy")) continue;
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(servico);
            return rsp == null ? null : rsp.getProvider();
        }
        return null;
    }
}
