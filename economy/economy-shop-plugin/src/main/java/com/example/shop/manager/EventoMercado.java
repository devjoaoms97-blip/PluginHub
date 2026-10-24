package com.example.shop.manager;

import com.example.shop.ShopPlugin;
import com.example.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evento de mercado ("alta do diamante"): de tempos em tempos, com uma chance, um item sorteado
 * passa a valer mais na venda pra loja por algumas horas. O multiplicador vale no /sell, no menu e
 * na venda automática; o preço dinâmico continua caindo a cada unidade vendida como sempre.
 * Começo e fim vão pro chat. Só um evento por vez; o atual fica salvo no config, então sobrevive
 * a um reinício.
 */
public class EventoMercado {

    private final ShopPlugin plugin;
    private Material material;
    private double multiplicador = 1;
    private long termina;
    private long proximaTentativa;

    public EventoMercado(ShopPlugin plugin) {
        this.plugin = plugin;
        String salvo = plugin.getConfig().getString("eventos-mercado.atual.item");
        long ate = plugin.getConfig().getLong("eventos-mercado.atual.termina", 0);
        if (salvo != null && ate > System.currentTimeMillis()) {
            Material m = Material.matchMaterial(salvo);
            if (m != null) aplicar(m, plugin.getConfig().getDouble("eventos-mercado.atual.multiplicador", 1), ate);
        }
        this.proximaTentativa = System.currentTimeMillis() + intervaloMs();
    }

    public Material getMaterial() {
        return material;
    }

    public double getMultiplicador() {
        return multiplicador;
    }

    public long getTermina() {
        return termina;
    }

    public boolean ativo() {
        return material != null;
    }

    private long intervaloMs() {
        return (long) (Math.max(0.25, plugin.getConfig().getDouble("eventos-mercado.intervalo-horas", 6)) * 3_600_000L);
    }

    /** Roda a cada minuto: termina o evento que venceu e, na hora certa, tenta começar outro. */
    public void tick() {
        long agora = System.currentTimeMillis();
        if (ativo() && agora >= termina) encerrar(true);
        if (ativo() || !plugin.getConfig().getBoolean("eventos-mercado.ativado", true) || agora < proximaTentativa) return;
        proximaTentativa = agora + intervaloMs();
        if (ThreadLocalRandom.current().nextDouble(100) >= plugin.getConfig().getDouble("eventos-mercado.chance", 50)) return;

        List<Material> candidatos = new ArrayList<>();
        for (String entrada : plugin.getConfig().getStringList("eventos-mercado.itens")) {
            Material m = Material.matchMaterial(entrada.split(":")[0].trim());
            if (m != null && plugin.getShopManager().existeItem(m)) candidatos.add(m);
        }
        if (candidatos.isEmpty()) candidatos.addAll(plugin.getShopManager().getTodosItens().keySet());
        if (candidatos.isEmpty()) return;

        double minimo = plugin.getConfig().getDouble("eventos-mercado.multiplicador-minimo", 1.5);
        double maximo = Math.max(minimo, plugin.getConfig().getDouble("eventos-mercado.multiplicador-maximo", 2.0));
        double m = Math.round(ThreadLocalRandom.current().nextDouble(minimo, maximo + 0.0001) * 10) / 10.0;
        double horas = Math.max(0.25, plugin.getConfig().getDouble("eventos-mercado.duracao-horas", 2));
        iniciar(candidatos.get(ThreadLocalRandom.current().nextInt(candidatos.size())), m, horas);
    }

    /** Começa um evento agora (a staff também usa, pelo /shop admin evento). */
    public void iniciar(Material item, double multiplicador, double horas) {
        if (ativo()) encerrar(false);
        aplicar(item, multiplicador, System.currentTimeMillis() + (long) (horas * 3_600_000L));
        salvar();
        String nome = nome(item);
        Bukkit.broadcastMessage("§6§l📈 ALTA " + artigo(nome).toUpperCase(Locale.ROOT) + " " + nome.toUpperCase(Locale.ROOT)
                + "! §eA loja está pagando §6x" + texto(multiplicador) + " §epor " + nome + " pelas próximas §f" + texto(horas)
                + "h§e. Venda com §f/sell§e!");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
    }

    /** Termina o evento atual ({@code anunciar}: avisa no chat). */
    public void encerrar(boolean anunciar) {
        if (!ativo()) return;
        ShopItem item = plugin.getShopManager().getItem(material);
        if (item != null) item.setMultiplicadorEvento(1);
        if (anunciar) {
            String nome = nome(material);
            Bukkit.broadcastMessage("§7A alta " + artigo(nome) + " " + nome + " acabou: o preço voltou ao normal.");
        }
        material = null;
        multiplicador = 1;
        termina = 0;
        salvar();
    }

    private void aplicar(Material item, double mult, long ate) {
        ShopItem shopItem = plugin.getShopManager().getItem(item);
        if (shopItem == null) return;
        this.material = item;
        this.multiplicador = mult;
        this.termina = ate;
        shopItem.setMultiplicadorEvento(mult);
    }

    private void salvar() {
        plugin.getConfig().set("eventos-mercado.atual.item", material == null ? null : material.name());
        plugin.getConfig().set("eventos-mercado.atual.multiplicador", material == null ? null : multiplicador);
        plugin.getConfig().set("eventos-mercado.atual.termina", material == null ? null : termina);
        plugin.saveConfig();
    }

    /** O nome do item no anúncio: o que vem depois do ":" na lista do config, ou o do material. */
    public String nome(Material m) {
        for (String entrada : plugin.getConfig().getStringList("eventos-mercado.itens")) {
            String[] partes = entrada.split(":", 2);
            if (partes.length == 2 && m == Material.matchMaterial(partes[0].trim())) return partes[1].trim();
        }
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** "da" pro nome que termina em "a" (esmeralda, ametista), "do" no resto. */
    private static String artigo(String nome) {
        return nome.toLowerCase(Locale.ROOT).endsWith("a") ? "da" : "do";
    }

    private static String texto(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v)).replace('.', ',');
    }
}
