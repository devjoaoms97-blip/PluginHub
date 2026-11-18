package com.example.caixas;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Uma caixa: o nome, a chave dela, como se consegue a chave e os prêmios (sorteados pelo peso). */
public final class Caixa {

    final String id;
    final String nome;
    final String nomeChave;
    final String modeloChave;
    final String comoConseguir;
    final List<Premio> premios;
    final double pesoTotal;

    Caixa(String id, ConfigurationSection s) {
        this.id = id;
        this.nome = CaixasPlugin.cores(s.getString("nome", id));
        this.nomeChave = CaixasPlugin.cores(s.getString("chave.nome", "&eChave " + id));
        this.modeloChave = s.getString("chave.modelo", "");
        this.comoConseguir = CaixasPlugin.cores(s.getString("como-conseguir", ""));
        List<Premio> l = new ArrayList<>();
        ConfigurationSection ps = s.getConfigurationSection("premios");
        if (ps != null) {
            for (String p : ps.getKeys(false)) {
                ConfigurationSection ps2 = ps.getConfigurationSection(p);
                if (ps2 != null) l.add(new Premio(p, ps2));
            }
        }
        this.premios = Collections.unmodifiableList(l);
        this.pesoTotal = l.stream().mapToDouble(p -> p.peso).sum();
    }

    Premio sortear() {
        double r = ThreadLocalRandom.current().nextDouble() * pesoTotal;
        for (Premio p : premios) {
            r -= p.peso;
            if (r < 0) return p;
        }
        return premios.get(premios.size() - 1);
    }

    double chance(Premio p) {
        return pesoTotal <= 0 ? 0 : p.peso / pesoTotal;
    }
}
