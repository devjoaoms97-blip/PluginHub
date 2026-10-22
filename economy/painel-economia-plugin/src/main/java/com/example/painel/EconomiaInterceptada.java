package com.example.painel;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * Um {@link Economy} que repassa tudo pra economia de verdade (EssentialsX etc.), mas anota
 * cada {@code depositPlayer}/{@code withdrawPlayer} bem-sucedido no {@link FluxoManager},
 * junto com o plugin que fez a chamada.
 *
 * É um {@link Proxy} dinâmico (e não uma classe implementando a interface inteira à mão) pra
 * não depender da lista exata de métodos da VaultAPI. O plugin de origem é descoberto pelo
 * stack de execução: o primeiro frame cuja classe pertence a um plugin que não seja este
 * painel nem o Vault. Assim nenhum outro plugin precisa ser alterado pra aparecer no painel.
 */
public final class EconomiaInterceptada implements InvocationHandler {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final Economy real;
    private final FluxoManager fluxo;
    private final Plugin painel;

    private EconomiaInterceptada(Economy real, FluxoManager fluxo, Plugin painel) {
        this.real = real;
        this.fluxo = fluxo;
        this.painel = painel;
    }

    public static Economy criar(Economy real, FluxoManager fluxo, Plugin painel) {
        return (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[]{Economy.class},
                new EconomiaInterceptada(real, fluxo, painel));
    }

    @Override
    public Object invoke(Object proxy, Method metodo, Object[] args) throws Throwable {
        Object resultado;
        try {
            resultado = metodo.invoke(real, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }

        String nome = metodo.getName();
        if ((nome.equals("depositPlayer") || nome.equals("withdrawPlayer"))
                && resultado instanceof EconomyResponse resposta
                && resposta.transactionSuccess() && resposta.amount > 0) {
            try {
                fluxo.registrar(origem(), nome.equals("depositPlayer") ? resposta.amount : -resposta.amount);
            } catch (RuntimeException e) {
                // o painel nunca pode quebrar uma transação de verdade
                painel.getLogger().warning("Falha ao registrar fluxo: " + e.getMessage());
            }
        }
        return resultado;
    }

    private String origem() {
        Optional<String> nome = WALKER.walk(frames -> frames
                .map(frame -> pluginDe(frame.getDeclaringClass()))
                .filter(p -> p != null && p != painel && !p.getName().equals("Vault"))
                .map(Plugin::getName)
                .findFirst());
        return nome.orElse("Outros");
    }

    private Plugin pluginDe(Class<?> classe) {
        try {
            return JavaPlugin.getProvidingPlugin(classe);
        } catch (RuntimeException e) {
            return null; // classe do servidor/JDK, não de plugin
        }
    }
}
