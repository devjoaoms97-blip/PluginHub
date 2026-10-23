package com.example.gladiador.manager;

import com.example.gladiador.GladiadorPlugin;
import com.example.gladiador.model.Evento;
import com.example.gladiador.model.Evento.Participante;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/**
 * Os três destaques de cada Gladiador: quem mais matou (Ceifador), quem mais andou na arena
 * (Fujão) e quem mais tirou vida de outros participantes (Top Damage). No fim da luta, eles são
 * anunciados e ganham a tag no chat, que fica até o próximo Gladiador. Salvo em destaques.yml.
 */
public class Destaques implements Listener {

    /** Uma categoria: chave no config, nome no anúncio, unidade e como se mede no participante. */
    private record Categoria(String chave, String emoji, String titulo, String unidade, ToDoubleFunction<Participante> medida) {
    }

    private static final List<Categoria> CATEGORIAS = List.of(
            new Categoria("ceifador", "☠", "Ceifador", "abate(s)", p -> p.getAbates()),
            new Categoria("fujao", "⚡", "Fujão", "blocos", Participante::getDistancia),
            new Categoria("topdamage", "✸", "Top Damage", "de dano", Participante::getDano));

    private record Dono(UUID jogador, String nome) {
    }

    private final GladiadorPlugin plugin;
    private final File arquivo;
    private final Map<String, Dono> donos = new LinkedHashMap<>();

    public Destaques(GladiadorPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "destaques.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        for (Categoria c : CATEGORIAS) {
            String id = yaml.getString(c.chave() + ".uuid");
            if (id == null) continue;
            try {
                donos.put(c.chave(), new Dono(UUID.fromString(id), yaml.getString(c.chave() + ".nome")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em destaques.yml: " + id);
            }
        }
    }

    private boolean ativados() {
        return plugin.getConfig().getBoolean("destaques.ativado", true);
    }

    /**
     * Fim da luta: escolhe o melhor de cada categoria (precisa ter feito alguma coisa; no empate,
     * fica quem se inscreveu primeiro), passa a tag e devolve o anúncio, ou "" sem destaques.
     */
    public String premiar(Evento evento) {
        if (!ativados()) return "";
        List<String> linhas = new ArrayList<>();
        for (Categoria c : CATEGORIAS) {
            UUID melhor = null;
            double maior = 0;
            for (Map.Entry<UUID, Participante> e : evento.getParticipantes().entrySet()) {
                double valor = c.medida().applyAsDouble(e.getValue());
                if (valor > maior) {
                    maior = valor;
                    melhor = e.getKey();
                }
            }
            if (melhor == null) continue;
            String nome = evento.getParticipantes().get(melhor).getNome();
            donos.put(c.chave(), new Dono(melhor, nome));
            linhas.add("§f" + c.emoji() + " " + tag(c.chave()) + " §f" + nome + " §7(" + numero(maior) + " " + c.unidade() + ")");
        }
        salvar();
        return linhas.isEmpty() ? "" : "§6§lDestaques do Gladiador:\n" + String.join("\n", linhas);
    }

    private String tag(String chave) {
        Categoria c = CATEGORIAS.stream().filter(x -> x.chave().equals(chave)).findFirst().orElseThrow();
        String padrao = switch (chave) {
            case "ceifador" -> "&c[Ceifador]";
            case "fujao" -> "&b[Fujão]";
            default -> "&6[Top Damage]";
        };
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("destaques.tags." + c.chave(), padrao));
    }

    /** Tags que esse jogador tem agora, com espaço no fim, ou "". */
    public String tagsDe(UUID jogador) {
        StringBuilder sb = new StringBuilder();
        donos.forEach((chave, dono) -> {
            if (dono.jogador().equals(jogador)) sb.append(tag(chave)).append(' ');
        });
        return sb.toString();
    }

    /** Pro /gladiador top: quem tem cada tag agora. */
    public List<String> resumo() {
        List<String> linhas = new ArrayList<>();
        for (Categoria c : CATEGORIAS) {
            Dono d = donos.get(c.chave());
            linhas.add(c.emoji() + " " + tag(c.chave()) + " §f" + (d != null ? d.nome() : "§7ninguém ainda"));
        }
        return linhas;
    }

    /** A tag vai antes de tudo no chat (depois desta prioridade, ninguém mais mexe no começo). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoFalar(AsyncPlayerChatEvent evento) {
        if (!ativados()) return;
        String tags = tagsDe(evento.getPlayer().getUniqueId());
        if (!tags.isEmpty()) evento.setFormat(tags.replace("%", "%%") + evento.getFormat());
    }

    private static String numero(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", valor);
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        donos.forEach((chave, dono) -> {
            yaml.set(chave + ".uuid", dono.jogador().toString());
            yaml.set(chave + ".nome", dono.nome());
        });
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar destaques.yml", e);
        }
    }
}
