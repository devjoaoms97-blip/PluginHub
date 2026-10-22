package com.example.barracas.model;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Uma barraca do mercado: uma área (caixa entre dois cantos) construída pela staff no spawn.
 *
 * <ul>
 *   <li><b>Normal</b>: alugada por ordem de chegada, pelo preço de tabela, e pode ser renovada.</li>
 *   <li><b>Especial</b> (maior / mais perto do spawn): vai a leilão toda vez que fica livre, com
 *       lance mínimo igual ao preço das normais, e NÃO renova — senão o dono nunca soltaria e
 *       o leilão não teria sentido. Se um leilão acabar sem lances, ela fica alugável pelo preço
 *       mínimo por ordem de chegada até o próximo ciclo.</li>
 * </ul>
 *
 * {@code blocosDoDono} guarda onde o inquilino colocou baús e placas — é o que sai da barraca
 * quando o aluguel acaba (o conteúdo dos baús vai pra caixa de retirada), deixando a construção
 * da staff intacta.
 */
public class Barraca {

    private final String id;
    private final String mundo;
    private final BlocoPos min;
    private final BlocoPos max;
    private final boolean especial;

    private UUID dono;
    private String nomeDono;
    private long alugadaAte;
    private final Set<BlocoPos> blocosDoDono = new HashSet<>();
    private Leilao leilao;

    // Não persiste: no pior caso, o aviso de vencimento é mandado de novo depois de um restart
    private boolean avisoVencimentoEnviado;

    public Barraca(String id, String mundo, BlocoPos min, BlocoPos max, boolean especial) {
        this.id = id;
        this.mundo = mundo;
        this.min = min;
        this.max = max;
        this.especial = especial;
    }

    public boolean contem(Location local) {
        if (local.getWorld() == null || !local.getWorld().getName().equals(mundo)) {
            return false;
        }
        return contem(local.getBlockX(), local.getBlockY(), local.getBlockZ());
    }

    public boolean contem(int x, int y, int z) {
        return x >= min.x() && x <= max.x()
                && y >= min.y() && y <= max.y()
                && z >= min.z() && z <= max.z();
    }

    public boolean sobrepoe(String outroMundo, BlocoPos outroMin, BlocoPos outroMax) {
        return mundo.equals(outroMundo)
                && min.x() <= outroMax.x() && max.x() >= outroMin.x()
                && min.y() <= outroMax.y() && max.y() >= outroMin.y()
                && min.z() <= outroMax.z() && max.z() >= outroMin.z();
    }

    /** Alugada e dentro do prazo. Entre o fim do prazo e a próxima checagem, conta como fechada. */
    public boolean estaAlugada(long agora) {
        return dono != null && agora < alugadaAte;
    }

    public boolean emLeilao() {
        return leilao != null;
    }

    public String getId() {
        return id;
    }

    public String getMundo() {
        return mundo;
    }

    public BlocoPos getMin() {
        return min;
    }

    public BlocoPos getMax() {
        return max;
    }

    public boolean isEspecial() {
        return especial;
    }

    public UUID getDono() {
        return dono;
    }

    public String getNomeDono() {
        return nomeDono;
    }

    public void setDono(UUID dono, String nomeDono) {
        this.dono = dono;
        this.nomeDono = nomeDono;
    }

    public long getAlugadaAte() {
        return alugadaAte;
    }

    public void setAlugadaAte(long alugadaAte) {
        this.alugadaAte = alugadaAte;
    }

    public Set<BlocoPos> getBlocosDoDono() {
        return blocosDoDono;
    }

    public Leilao getLeilao() {
        return leilao;
    }

    public void setLeilao(Leilao leilao) {
        this.leilao = leilao;
    }

    public boolean isAvisoVencimentoEnviado() {
        return avisoVencimentoEnviado;
    }

    public void setAvisoVencimentoEnviado(boolean avisoVencimentoEnviado) {
        this.avisoVencimentoEnviado = avisoVencimentoEnviado;
    }
}
