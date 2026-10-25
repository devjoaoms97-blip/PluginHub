package com.example.vip.manager;

import com.example.vip.VipPlugin;
import com.example.vip.model.VipCode;
import com.example.vip.model.VipTier;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Códigos de resgate de VIP — a peça que conecta a lojinha (fora do Minecraft) e a revenda
 * entre jogadores ao jogo.
 *
 * Um código nasce sempre grátis, via {@link #gerar} (staff confirma uma compra feita fora
 * do jogo e gera um código pro comprador resgatar). Um jogador que ainda não resgatou o
 * próprio código pode colocá-lo à venda com {@link #colocarAVenda}, anexando um preço em
 * coins e quem é o vendedor — o código continua sendo o mesmo, só passa a exigir pagamento
 * de quem for resgatar (menos do próprio vendedor, que não pode comprar de si mesmo). Só dá
 * pra revender um código que **ainda não foi ativado** — depois de resgatado ele some do
 * sistema, então não tem como vender um VIP que já está em uso.
 *
 * {@link #buscar} (consulta) e {@link #consumir} (efetiva o uso) são separados de propósito:
 * quem chama precisa poder conferir se um pagamento é possível ANTES de gastar o código —
 * senão um jogador sem coins suficientes perderia o código à toa numa tentativa frustrada.
 *
 * Validade: um código pode ter prazo pra ser resgatado ({@code expiraEm}); passou do prazo
 * sem ser resgatado, ele some (inclusive se estiver à venda). Códigos antigos, de antes
 * dessa opção existir, não têm prazo — nenhum código já vendido vence de surpresa.
 */
public class CodeManager {

    // Sem 0/O/1/I/L pra reduzir erro de digitação/leitura ao repassar o código pro comprador
    private static final String CARACTERES = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int TAMANHO_CODIGO = 8;

    private final VipPlugin plugin;
    private final File arquivo;
    private final Map<String, VipCode> codigos = new HashMap<>();
    private final Map<UUID, java.util.List<String>> avisosPendentes = new java.util.HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CodeManager(VipPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "codigos.yml");
        carregar();
    }

    /**
     * Código grátis gerado pela staff (compra confirmada fora do jogo). {@code validadeDias}
     * é o prazo pra ser resgatado — 0 = não expira.
     */
    public VipCode gerar(VipTier tier, int usos, String criadoPor, int validadeDias) {
        long expiraEm = validadeDias > 0 ? System.currentTimeMillis() + validadeDias * 86_400_000L : 0;
        return criarESalvar(tier, usos, criadoPor, 0.0, null, expiraEm);
    }

    private VipCode criarESalvar(VipTier tier, int usos, String criadoPor, double preco, UUID vendedorId,
                                 long expiraEm) {
        String codigo;
        do {
            codigo = sortearCodigo();
        } while (codigos.containsKey(codigo));

        VipCode novo = new VipCode(codigo, tier, usos, System.currentTimeMillis(), criadoPor, preco, vendedorId,
                expiraEm);
        codigos.put(codigo, novo);
        salvar();
        return novo;
    }

    /**
     * Coloca um código EXISTENTE e ainda não resgatado à venda por {@code preco} coins —
     * é assim que um jogador revende o código de VIP que recebeu (ex: da lojinha) sem
     * precisar ter ativado o VIP primeiro. Retorna {@code null} se o código não existir
     * (inválido ou já usado) ou já estiver à venda por outra pessoa.
     */
    public VipCode colocarAVenda(String codigoDigitado, double preco, UUID vendedorId) {
        limparExpirados();
        String chave = normalizar(codigoDigitado);
        VipCode atual = codigos.get(chave);
        if (atual == null || atual.getVendedorId() != null) {
            return null;
        }

        VipCode atualizado = new VipCode(atual.getCodigo(), atual.getTier(), atual.getUsosRestantes(),
                atual.getCriadoEm(), atual.getCriadoPor(), preco, vendedorId, atual.getExpiraEm());
        codigos.put(chave, atualizado);
        salvar();
        return atualizado;
    }

    /**
     * Consulta um código (aceita com ou sem o prefixo "VIP-") sem gastar nenhum uso —
     * quem chama deve confirmar que consegue pagar (se {@code getPreco() > 0}) antes de
     * chamar {@link #consumir}.
     */
    public VipCode buscar(String codigoDigitado) {
        limparExpirados();
        return codigos.get(normalizar(codigoDigitado));
    }

    /**
     * Remove os códigos que passaram do prazo sem ser resgatados. Chamado antes de qualquer
     * consulta e também pela tarefa periódica do plugin. Retorna quantos removeu.
     */
    /**
     * O código que estava à venda venceu sem ninguém comprar: avisa o vendedor agora, se estiver
     * online, ou guarda o aviso pra quando ele entrar ({@code avisos-pendentes} no codigos.yml).
     */
    private void avisarVendedor(VipCode code) {
        String msg = "§cSeu código §fVIP-" + code.getCodigo() + " §c(" + plugin.getNomeExibicao(code.getTier())
                + "§c) que estava à venda venceu sem ser comprado e foi apagado.";
        org.bukkit.entity.Player vendedor = org.bukkit.Bukkit.getPlayer(code.getVendedorId());
        if (vendedor != null) {
            vendedor.sendMessage(msg);
        } else {
            avisosPendentes.computeIfAbsent(code.getVendedorId(), k -> new java.util.ArrayList<>()).add(msg);
        }
    }

    /** Entrega os avisos guardados de códigos vencidos (chamado quando o jogador entra). */
    public void entregarAvisos(org.bukkit.entity.Player jogador) {
        java.util.List<String> avisos = avisosPendentes.remove(jogador.getUniqueId());
        if (avisos == null) return;
        avisos.forEach(jogador::sendMessage);
        salvar();
    }

    public int limparExpirados() {
        long agora = System.currentTimeMillis();
        int antes = codigos.size();
        codigos.values().removeIf(code -> {
            if (!code.expirado(agora)) return false;
            if (code.getVendedorId() != null) avisarVendedor(code);
            return true;
        });
        int removidos = antes - codigos.size();
        if (removidos > 0) {
            salvar();
        }
        return removidos;
    }

    /** Consome um uso do código já encontrado via {@link #buscar}, removendo se esgotar. */
    public void consumir(VipCode code) {
        code.consumirUso();
        if (code.esgotado()) {
            codigos.remove(code.getCodigo());
        }
        salvar();
    }

    /** Apaga um código antes de ser usado (ex: gerado por engano). Retorna se realmente existia. */
    public boolean revogar(String codigoDigitado) {
        String chave = normalizar(codigoDigitado);
        boolean removeu = codigos.remove(chave) != null;
        if (removeu) {
            salvar();
        }
        return removeu;
    }

    /**
     * Cancela a venda em aberto de {@code vendedorId} (se houver): o código volta a ser um
     * código normal, sem preço nem vendedor — ainda válido, ainda resgatável de graça por
     * quem souber o texto dele. Retorna o {@link VipCode} já revertido, ou {@code null} se
     * ele não tiver nenhuma venda ativa no momento.
     */
    public VipCode cancelarVenda(UUID vendedorId) {
        String chave = buscarCodigoDeVenda(vendedorId);
        if (chave == null) {
            return null;
        }

        VipCode atual = codigos.get(chave);
        VipCode revertido = new VipCode(atual.getCodigo(), atual.getTier(), atual.getUsosRestantes(),
                atual.getCriadoEm(), atual.getCriadoPor(), 0.0, null, atual.getExpiraEm());
        codigos.put(chave, revertido);
        salvar();
        return revertido;
    }

    /** Código (chave no mapa) da venda em aberto de {@code vendedorId}, ou {@code null}. */
    public String buscarCodigoDeVenda(UUID vendedorId) {
        limparExpirados();
        for (VipCode code : codigos.values()) {
            if (vendedorId.equals(code.getVendedorId())) {
                return code.getCodigo();
            }
        }
        return null;
    }

    public Map<String, VipCode> getTodos() {
        return codigos;
    }

    private String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        String limpo = codigo.trim().toUpperCase(Locale.ROOT);
        if (limpo.startsWith("VIP-")) {
            limpo = limpo.substring("VIP-".length());
        }
        return limpo;
    }

    private String sortearCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Persistência (codigos.yml)
    // ---------------------------------------------------------------------

    private void carregar() {
        if (!arquivo.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        var avisos = yaml.getConfigurationSection("avisos-pendentes");
        if (avisos != null) {
            for (String id : avisos.getKeys(false)) {
                try {
                    avisosPendentes.put(UUID.fromString(id), new java.util.ArrayList<>(avisos.getStringList(id)));
                } catch (IllegalArgumentException ignorado) {
                    // UUID inválido: descarta
                }
            }
        }
        var secao = yaml.getConfigurationSection("codigos");
        if (secao == null) {
            return;
        }

        for (String codigo : secao.getKeys(false)) {
            VipTier tier = VipTier.fromString(secao.getString(codigo + ".tier"));
            if (tier == null) {
                plugin.getLogger().warning("Tier inválido em codigos.yml pro código " + codigo + ", ignorando.");
                continue;
            }
            int usos = secao.getInt(codigo + ".usos-restantes", 1);
            long criadoEm = secao.getLong(codigo + ".criado-em");
            String criadoPor = secao.getString(codigo + ".criado-por", "desconhecido");
            double preco = secao.getDouble(codigo + ".preco", 0.0);
            long expiraEm = secao.getLong(codigo + ".expira-em", 0); // ausente = código antigo, sem prazo

            UUID vendedorId = null;
            String vendedorTexto = secao.getString(codigo + ".vendedor-id");
            if (vendedorTexto != null) {
                try {
                    vendedorId = UUID.fromString(vendedorTexto);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("vendedor-id inválido em codigos.yml pro código " + codigo + ", tratando como código sem vendedor.");
                }
            }

            codigos.put(codigo, new VipCode(codigo, tier, usos, criadoEm, criadoPor, preco, vendedorId, expiraEm));
        }
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, VipCode> entrada : codigos.entrySet()) {
            VipCode code = entrada.getValue();
            String base = "codigos." + entrada.getKey();
            yaml.set(base + ".tier", code.getTier().name());
            yaml.set(base + ".usos-restantes", code.getUsosRestantes());
            yaml.set(base + ".criado-em", code.getCriadoEm());
            yaml.set(base + ".criado-por", code.getCriadoPor());
            yaml.set(base + ".preco", code.getPreco());
            if (code.getVendedorId() != null) {
                yaml.set(base + ".vendedor-id", code.getVendedorId().toString());
            }
            if (code.getExpiraEm() > 0) {
                yaml.set(base + ".expira-em", code.getExpiraEm());
            }
        }
        avisosPendentes.forEach((id, lista) -> yaml.set("avisos-pendentes." + id, lista));
        try {
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar codigos.yml", e);
        }
    }
}
