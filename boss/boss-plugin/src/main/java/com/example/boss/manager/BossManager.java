package com.example.boss.manager;

import com.example.boss.BossPlugin;
import com.example.boss.model.FaseConfig;
import com.example.boss.model.TipoGatilhoFase;
import com.example.boss.util.BossTagUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BossManager {

    private final BossPlugin plugin;

    private LivingEntity bossAtivo;
    private BossBar bossBar;
    private double vidaMaxima;

    private final List<FaseConfig> fases = new ArrayList<>();
    private final Set<FaseConfig> fasesPercentuaisDisparadas = new HashSet<>();
    private final Map<FaseConfig, Long> proximoDisparoTempo = new HashMap<>();

    private BukkitTask taskMonitoramento;

    public BossManager(BossPlugin plugin) {
        this.plugin = plugin;
        carregarFases();
    }

    public boolean estaAtivo() {
        return bossAtivo != null && bossAtivo.isValid() && !bossAtivo.isDead();
    }

    public LivingEntity getBossAtivo() {
        return bossAtivo;
    }

    public void recarregarFases() {
        carregarFases();
    }

    private void carregarFases() {
        fases.clear();
        List<Map<?, ?>> lista = plugin.getConfig().getMapList("fases");
        for (Map<?, ?> mapaBruto : lista) {
            try {
                fases.add(FaseConfig.deConfig(mapaBruto));
            } catch (Exception e) {
                plugin.getLogger().warning("Fase inválida no config.yml: " + mapaBruto + " (" + e.getMessage() + ")");
            }
        }
    }

    public boolean iniciar(CommandSender iniciador) {
        if (estaAtivo()) {
            if (iniciador != null) iniciador.sendMessage("§cJá existe um boss ativo no momento.");
            return false;
        }

        Location local = plugin.getArenaManager().getLocalizacao();
        if (local == null) {
            if (iniciador != null) iniciador.sendMessage("§cDefina a localização da arena primeiro com /boss setlocal.");
            return false;
        }

        // Mobs hostis não spawnam de jeito nenhum no modo Peaceful (trava do próprio
        // Minecraft, não dá pra contornar via plugin) — corrige automaticamente pra
        // o evento não falhar silenciosamente.
        if (local.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            local.getWorld().setDifficulty(Difficulty.EASY);
            String aviso = "§eO mundo da arena estava em dificuldade Peaceful (impede mobs hostis) — mudei automaticamente para Easy.";
            if (iniciador != null) iniciador.sendMessage(aviso);
            plugin.getLogger().warning("Mundo '" + local.getWorld().getName() + "' estava em Peaceful; mudado para Easy automaticamente pra permitir o spawn do boss.");
        }

        EntityType tipo = resolverTipoMob();
        bossAtivo = (LivingEntity) local.getWorld().spawnEntity(local, tipo);
        configurarAtributos(bossAtivo);

        AttributeInstance vidaMaximaAttr = bossAtivo.getAttribute(Attribute.MAX_HEALTH);
        vidaMaxima = vidaMaximaAttr != null ? vidaMaximaAttr.getValue() : bossAtivo.getHealth();
        bossAtivo.setHealth(vidaMaxima);

        criarBossBar();

        fasesPercentuaisDisparadas.clear();
        proximoDisparoTempo.clear();
        long agora = System.currentTimeMillis();
        for (FaseConfig fase : fases) {
            if (fase.getGatilho() == TipoGatilhoFase.INTERVALO_TEMPO) {
                proximoDisparoTempo.put(fase, agora + (long) (fase.getValorGatilho() * 1000));
            }
        }

        anunciarSpawn();
        iniciarMonitoramento();

        return true;
    }

    private EntityType resolverTipoMob() {
        List<String> tipos = plugin.getConfig().getStringList("mobs-possiveis");
        if (tipos.isEmpty()) return EntityType.ZOMBIE;

        String escolhido = tipos.get(ThreadLocalRandom.current().nextInt(tipos.size()));
        try {
            return EntityType.valueOf(escolhido.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Tipo de mob inválido no config.yml: " + escolhido + ". Usando ZOMBIE.");
            return EntityType.ZOMBIE;
        }
    }

    private void configurarAtributos(LivingEntity boss) {
        String nome = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("nome", "&4&lChefe"));
        boss.setCustomName(nome);
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        double vida = plugin.getConfig().getDouble("vida-maxima", 500);
        double dano = plugin.getConfig().getDouble("dano-ataque", 8);
        double escala = plugin.getConfig().getDouble("escala", 3.0);

        setAtributo(boss, Attribute.MAX_HEALTH, vida);
        setAtributo(boss, Attribute.ATTACK_DAMAGE, dano);
        setAtributo(boss, Attribute.SCALE, escala);
        setAtributo(boss, Attribute.KNOCKBACK_RESISTANCE, 1.0);

        // O Wither nasce com um período de invulnerabilidade vanilla (~11s) onde fica
        // parado e não toma dano — igual ao "nascimento" tradicional dele. Como a gente
        // spawna direto, sem o ritual de areia das almas, esse estado nunca seria
        // encerrado sozinho. Zeramos na hora pra ele já nascer pronto pra lutar.
        if (boss instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
        }

        // Marca o boss pra outros plugins (ex: SkillsPlugin) reconhecerem e ignorarem
        // efeitos de controle (stun/knockback das skills) nele.
        boss.getPersistentDataContainer().set(BossTagUtil.chave(), PersistentDataType.BYTE, (byte) 1);
    }

    private void setAtributo(LivingEntity entidade, Attribute atributo, double valor) {
        AttributeInstance instancia = entidade.getAttribute(atributo);
        if (instancia != null) {
            instancia.setBaseValue(valor);
        }
    }

    private void criarBossBar() {
        BarColor cor = valorEnumOuPadrao(BarColor.class, plugin.getConfig().getString("barra.cor", "RED"), BarColor.RED);
        BarStyle estilo = valorEnumOuPadrao(BarStyle.class, plugin.getConfig().getString("barra.estilo", "SEGMENTED_10"), BarStyle.SOLID);

        bossBar = Bukkit.createBossBar(bossAtivo.getCustomName(), cor, estilo);
        bossBar.setProgress(1.0);
        for (Player jogador : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(jogador);
        }
    }

    private <T extends Enum<T>> T valorEnumOuPadrao(Class<T> tipo, String texto, T padrao) {
        try {
            return Enum.valueOf(tipo, texto.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }

    private void anunciarSpawn() {
        String mensagem = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("mensagem-spawn", "&4&l⚠ Um chefe apareceu! Corram para enfrentá-lo!"));
        Bukkit.broadcastMessage(mensagem);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
    }

    private void iniciarMonitoramento() {
        taskMonitoramento = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 10L);
    }

    private void tick() {
        if (!estaAtivo()) {
            finalizar(false);
            return;
        }

        double vidaAtual = bossAtivo.getHealth();
        double percentual = (vidaAtual / vidaMaxima) * 100.0;
        bossBar.setProgress(Math.max(0, Math.min(1, vidaAtual / vidaMaxima)));

        for (Player p : bossAtivo.getWorld().getPlayers()) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }

        suprimirBarraNativa();

        verificarFasesPercentuais(percentual);
        verificarFasesTempo();
    }

    /**
     * Wither e Ender Dragon vêm com uma boss bar NATIVA própria do jogo (por isso a
     * barra aparecia duplicada). Como já mostramos a nossa customizada, esvaziamos a
     * nativa a cada tick pra ela não competir/duplicar visualmente.
     */
    private void suprimirBarraNativa() {
        if (bossAtivo instanceof Wither wither) {
            wither.getBossBar().removeAll();
        } else if (bossAtivo instanceof EnderDragon dragao) {
            dragao.getBossBar().removeAll();
        }
    }

    private void verificarFasesPercentuais(double percentualAtual) {
        for (FaseConfig fase : fases) {
            if (fase.getGatilho() != TipoGatilhoFase.PERCENTUAL_VIDA) continue;
            if (fasesPercentuaisDisparadas.contains(fase)) continue;

            if (percentualAtual <= fase.getValorGatilho()) {
                fasesPercentuaisDisparadas.add(fase);
                executarAcao(fase);
            }
        }
    }

    private void verificarFasesTempo() {
        long agora = System.currentTimeMillis();
        for (FaseConfig fase : fases) {
            if (fase.getGatilho() != TipoGatilhoFase.INTERVALO_TEMPO) continue;

            Long proximo = proximoDisparoTempo.get(fase);
            if (proximo != null && agora >= proximo) {
                executarAcao(fase);
                proximoDisparoTempo.put(fase, agora + (long) (fase.getValorGatilho() * 1000));
            }
        }
    }

    private void executarAcao(FaseConfig fase) {
        switch (fase.getAcao()) {
            case INVOCAR_MOBS -> invocarMobs(fase);
            case EFEITO_AREA -> aplicarEfeitoArea(fase);
            case BUFF_BOSS -> aplicarBuffBoss(fase);
            case MENSAGEM -> enviarMensagem(fase);
        }
    }

    private void invocarMobs(FaseConfig fase) {
        String mobStr = fase.getParametro("mob", "ZOMBIE");
        int quantidade = (int) fase.getParametroNumero("quantidade", 10);

        EntityType tipo;
        try {
            tipo = EntityType.valueOf(mobStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            tipo = EntityType.ZOMBIE;
        }

        // Mesma trava de dificuldade se aplica aqui pros mobs invocados nas fases
        if (bossAtivo.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            bossAtivo.getWorld().setDifficulty(Difficulty.EASY);
        }

        Location centro = bossAtivo.getLocation();
        for (int i = 0; i < quantidade; i++) {
            double angulo = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double distancia = 3 + ThreadLocalRandom.current().nextDouble() * 4;
            Location spawnLocal = centro.clone().add(Math.cos(angulo) * distancia, 1, Math.sin(angulo) * distancia);
            centro.getWorld().spawnEntity(spawnLocal, tipo);
        }
    }

    private void aplicarEfeitoArea(FaseConfig fase) {
        String efeitoStr = fase.getParametro("efeito", "SLOWNESS");
        int duracaoTicks = (int) (fase.getParametroNumero("duracao-segundos", 5) * 20);
        int amplificador = (int) fase.getParametroNumero("amplificador", 0);
        double raio = fase.getParametroNumero("raio", 10);

        PotionEffectType tipo = PotionEffectType.getByName(efeitoStr.toUpperCase(Locale.ROOT));
        if (tipo == null) {
            plugin.getLogger().warning("Efeito de poção inválido numa fase: " + efeitoStr);
            return;
        }

        for (Player p : bossAtivo.getWorld().getPlayers()) {
            if (p.getLocation().distance(bossAtivo.getLocation()) <= raio) {
                p.addPotionEffect(new PotionEffect(tipo, duracaoTicks, amplificador));
            }
        }
    }

    private void aplicarBuffBoss(FaseConfig fase) {
        String efeitoStr = fase.getParametro("efeito", "SPEED");
        int duracaoTicks = (int) (fase.getParametroNumero("duracao-segundos", 10) * 20);
        int amplificador = (int) fase.getParametroNumero("amplificador", 1);

        PotionEffectType tipo = PotionEffectType.getByName(efeitoStr.toUpperCase(Locale.ROOT));
        if (tipo == null) {
            plugin.getLogger().warning("Efeito de poção inválido numa fase: " + efeitoStr);
            return;
        }

        bossAtivo.addPotionEffect(new PotionEffect(tipo, duracaoTicks, amplificador));
    }

    private void enviarMensagem(FaseConfig fase) {
        String texto = ChatColor.translateAlternateColorCodes('&', fase.getParametro("texto", ""));
        if (!texto.isEmpty()) {
            Bukkit.broadcastMessage(texto);
        }
    }

    public void finalizar(boolean morreu) {
        if (taskMonitoramento != null) {
            taskMonitoramento.cancel();
            taskMonitoramento = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        bossAtivo = null;
    }
}
