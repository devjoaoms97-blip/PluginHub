package com.example.ajuda;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ajuda pra quem está começando: o {@code /tutorial} (etapas com um prêmio único no fim), o
 * {@code /comandos} (páginas de comandos por tema), o botão de aceitar ou recusar o {@code /tpa} com
 * um clique e a lista dos comandos que os jogadores veem e usam ({@link ComandosPermitidos}).
 */
public class AjudaPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacyAmpersand();

    private Tutorial tutorial;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tutorial = new Tutorial(this);
        getServer().getPluginManager().registerEvents(tutorial, this);
        getCommand("tutorial").setExecutor(tutorial);
        getCommand("tutorial").setTabCompleter(tutorial);
        Comandos comandos = new Comandos(this);
        getCommand("comandos").setExecutor(comandos);
        getCommand("comandos").setTabCompleter(comandos);
        new TpaClique(this).registrar();
        getServer().getPluginManager().registerEvents(new ComandosPermitidos(this), this);
    }

    @Override
    public void onDisable() {
        if (tutorial != null) tutorial.salvar();
    }

    /**
     * Outro plugin avisa de uma ação do jogador que uma etapa do tutorial espera (a Loja Oficial manda
     * "vender-loja"). Chamado por reflexão.
     */
    public void registrarAcao(Player jogador, String tipo) {
        if (tutorial != null) tutorial.acao(jogador, tipo, 1);
    }

    /** Texto do config, com cores {@code &}. */
    static Component texto(String s) {
        return LEGADO.deserialize(s == null ? "" : s);
    }

    /** Um botão: texto que, clicado, roda (ou sugere) o comando, com a explicação ao passar o mouse. */
    static Component botao(String rotulo, String comando, String dica, boolean rodar) {
        return texto(rotulo)
                .clickEvent(rodar ? ClickEvent.runCommand(comando) : ClickEvent.suggestCommand(comando))
                .hoverEvent(HoverEvent.showText(texto(dica)));
    }
}
