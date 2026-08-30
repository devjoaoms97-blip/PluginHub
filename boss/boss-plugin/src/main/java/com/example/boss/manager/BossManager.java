package com.example.boss.manager;

import com.example.boss.BossPlugin;
import com.example.boss.model.BossProfile;
import com.example.boss.model.FaseConfig;
import com.example.boss.model.TipoGatilhoFase;
import com.example.boss.util.BossTagUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BossManager {

    private final BossPlugin plugin;

    private LivingEntity bossAtivo;
    private BossProfile perfilAtivo;
    private BossBar bossBar;
    private double vidaMaxima;

    private final Map<String, BossProfile> perfis = new LinkedHashMap<>();
    private final Set<FaseConfig> fasesPercentuaisDisparadas = new HashSet<>();
    private final Map<FaseConfig, Long> proximoDisparoTempo = new HashMap<>();
    private long proximoAtaqueAleatorio;

    private BukkitTask taskMonitoramento;

    public BossManager(BossPlugin plugin) {
        this.plugin = plugin;
        carregarPerfis();
    }

    public boolean estaAtivo() {
        return bossAtivo != null && bossAtivo.isValid() && !bossAtivo.isDead();
    }

    public LivingEntity getBossAtivo() {
        return bossAtivo;
    }

    public BossProfile getPerfilAtivo() {
        return perfilAtivo;
    }

    public Map<String, BossProfile> getPerfis() {
        return perfis;
    }

    public void recarregarPerfis() {
        carregarPerfis();
    }

    // ---------------------------------------------------------------------
    // Carregamento dos perfis (um por mob configurado em "bosses:" no config.yml)
    // ---------------------------------------------------------------------
    private void carregarPerfis() {
        perfis.clear();
        ConfigurationSection secaoBosses = plugin.getConfig().getConfigurationSection("bosses");
        if (secaoBosses == null) {
            plugin.getLogger().warning("Nenhuma seção 'bosses' encontrada no config.yml — nenhum boss vai poder ser spawnado.");
            return;
        }

        for (String id : secaoBosses.getKeys(false)) {
            ConfigurationSection secaoBoss = secaoBosses.getConfigurationSection(id);
            if (secaoBoss == null) continue;

            try {
                EntityType mob = EntityType.valueOf(secaoBoss.getString("mob", "ZOMBIE").toUpperCase(Locale.ROOT));
                String nome = ChatColor.translateAlternateColorCodes('&', secaoBoss.getString("nome", "&4&lChefe"));
                boolean especial = secaoBoss.getBoolean("especial", false);

                Double vidaOverride = secaoBoss.contains("vida-maxima") ? secaoBoss.getDouble("vida-maxima") : null;
                Double danoOverride = secaoBoss.contains("dano-ataque") ? secaoBoss.getDouble("dano-ataque") : null;
                Double escalaOverride = secaoBoss.contains("escala") ? secaoBoss.getDouble("escala") : null;

                List<FaseConfig> fases = new ArrayList<>();
                for (Map<?, ?> mapaBruto : secaoBoss.getMapList("fases")) {
                    try {
                        fases.add(FaseConfig.deConfig(mapaBruto));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Fase inválida no boss '" + id + "': " + mapaBruto + " (" + e.getMessage() + ")");
                    }
                }

                List<FaseConfig> ataquesAleatorios = new ArrayList<>();
                for (Map<?, ?> mapaBruto : secaoBoss.getMapList("ataques-aleatorios")) {
                    try {
                        ataquesAleatorios.add(FaseConfig.deConfig(mapaBruto));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ataque aleatório inválido no boss '" + id + "': " + mapaBruto + " (" + e.getMessage() + ")");
                    }
                }

                perfis.put(id, new BossProfile(id, mob, nome, especial, vidaOverride, danoOverride, escalaOverride, fases, ataquesAleatorios));
            } catch (Exception e) {
                plugin.getLogger().warning("Perfil de boss inválido: '" + id + "' (" + e.getMessage() + ")");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Início da luta
    // ---------------------------------------------------------------------

    /** Inicia um boss aleatório entre os perfis NÃO especiais (usado pela agenda e por /boss start sem argumento). */
    public boolean iniciarAleatorio(CommandSender iniciador) {
        List<BossProfile> candidatos = perfis.values().stream().filter(p -> !p.isEspecial()).toList();
        if (candidatos.isEmpty()) {
            if (iniciador != null) iniciador.sendMessage("§cNenhum boss normal configurado em 'bosses' no config.yml.");
            return false;
        }
        BossProfile escolhido = candidatos.get(ThreadLocalRandom.current().nextInt(candidatos.size()));
        return iniciar(escolhido, iniciador);
    }

    /** Inicia um perfil específico pelo id (ex: "wither", pra forçar o boss especial). */
    public boolean iniciarPorId(String id, CommandSender iniciador) {
        BossProfile perfil = perfis.get(id.toLowerCase(Locale.ROOT));
        if (perfil == null) {
            if (iniciador != null) {
                iniciador.sendMessage("§cBoss \"" + id + "\" não encontrado. Perfis disponíveis: §f" + String.join(", ", perfis.keySet()));
            }
            return false;
        }
        return iniciar(perfil, iniciador);
    }

    private boolean iniciar(BossProfile perfil, CommandSender iniciador) {
        if (estaAtivo()) {
            if (iniciador != null) iniciador.sendMessage("§cJá existe um boss ativo no momento.");
            return false;
        }

        Location local = plugin.getArenaManager().getLocalizacao();
        if (local == null) {
            if (iniciador != null) iniciador.sendMessage("§cDefina a localização da arena primeiro com /boss setlocal.");
            return false;
        }

        if (local.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            local.getWorld().setDifficulty(Difficulty.EASY);
            String aviso = "§eO mundo da arena estava em dificuldade Peaceful — mudei automaticamente para Easy.";
            if (iniciador != null) iniciador.sendMessage(aviso);
            plugin.getLogger().warning("Mundo '" + local.getWorld().getName() + "' estava em Peaceful; mudado para Easy automaticamente.");
        }

        this.perfilAtivo = perfil;
        bossAtivo = (LivingEntity) local.getWorld().spawnEntity(local, perfil.getMob());
        configurarAtributos(bossAtivo, perfil);

        AttributeInstance vidaMaximaAttr = bossAtivo.getAttribute(Attribute.MAX_HEALTH);
        vidaMaxima = vidaMaximaAttr != null ? vidaMaximaAttr.getValue() : bossAtivo.getHealth();
        bossAtivo.setHealth(vidaMaxima);

        criarBossBar();

        fasesPercentuaisDisparadas.clear();
        proximoDisparoTempo.clear();
        long agora = System.currentTimeMillis();
        for (FaseConfig fase : perfil.getFases()) {
            if (fase.getGatilho() == TipoGatilhoFase.INTERVALO_TEMPO) {
                proximoDisparoTempo.put(fase, agora + (long) (fase.getValorGatilho() * 1000));
            }
        }

        int intervaloAtaqueSegundos = plugin.getConfig().getInt("dificuldade.intervalo-ataque-aleatorio-segundos", 20);
        proximoAtaqueAleatorio = agora + intervaloAtaqueSegundos * 1000L;

        anunciarSpawn();
        iniciarMonitoramento();

        return true;
    }

    private void configurarAtributos(LivingEntity boss, BossProfile perfil) {
        boss.setCustomName(perfil.getNome());
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        double vida = perfil.getVidaMaximaOverride() != null ? perfil.getVidaMaximaOverride() : plugin.getConfig().getDouble("vida-maxima", 500);
        double dano = perfil.getDanoAtaqueOverride() != null ? perfil.getDanoAtaqueOverride() : plugin.getConfig().getDouble("dano-ataque", 18);
        double escala = perfil.getEscalaOverride() != null ? perfil.getEscalaOverride() : plugin.getConfig().getDouble("escala", 3.0);

        setAtributo(boss, Attribute.MAX_HEALTH, vida);
        setAtributo(boss, Attribute.ATTACK_DAMAGE, dano);
        setAtributo(boss, Attribute.SCALE, escala);
        setAtributo(boss, Attribute.KNOCKBACK_RESISTANCE, 1.0);

        // Velocidade acima do padrão do mob (evita boss "manco" fácil de despistar)
        double multiplicadorVelocidade = plugin.getConfig().getDouble("dificuldade.velocidade-multiplicador", 1.5);
        AttributeInstance velocidadeAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (velocidadeAttr != null) {
            velocidadeAttr.setBaseValue(velocidadeAttr.getBaseValue() * multiplicadorVelocidade);
        }

        // Alcance de perseguição maior, pra não perder o alvo fácil
        double alcancePerseguicao = plugin.getConfig().getDouble("dificuldade.alcance-perseguicao", 48);
        setAtributo(boss, Attribute.FOLLOW_RANGE, alcancePerseguicao);

        if (boss instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
        }

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
        verificarAtaqueAleatorio();
    }

    private void suprimirBarraNativa() {
        if (bossAtivo instanceof Wither wither) {
            wither.getBossBar().removeAll();
        } else if (bossAtivo instanceof EnderDragon dragao) {
            dragao.getBossBar().removeAll();
        }
    }

    private void verificarFasesPercentuais(double percentualAtual) {
        for (FaseConfig fase : perfilAtivo.getFases()) {
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
        for (FaseConfig fase : perfilAtivo.getFases()) {
            if (fase.getGatilho() != TipoGatilhoFase.INTERVALO_TEMPO) continue;

            Long proximo = proximoDisparoTempo.get(fase);
            if (proximo != null && agora >= proximo) {
                executarAcao(fase);
                proximoDisparoTempo.put(fase, agora + (long) (fase.getValorGatilho() * 1000));
            }
        }
    }

    /** Dispara periodicamente um ataque SORTEADO do arsenal do perfil ativo, pra dar perigo constante. */
    private void verificarAtaqueAleatorio() {
        long agora = System.currentTimeMillis();
        if (agora < proximoAtaqueAleatorio) return;

        List<FaseConfig> arsenal = perfilAtivo.getAtaquesAleatorios();
        int intervaloSegundos = plugin.getConfig().getInt("dificuldade.intervalo-ataque-aleatorio-segundos", 20);
        proximoAtaqueAleatorio = agora + intervaloSegundos * 1000L;

        if (arsenal.isEmpty()) return;
        FaseConfig escolhido = arsenal.get(ThreadLocalRandom.current().nextInt(arsenal.size()));
        executarAcao(escolhido);
    }

    private void executarAcao(FaseConfig fase) {
        switch (fase.getAcao()) {
            case INVOCAR_MOBS -> invocarMobs(fase);
            case EFEITO_AREA -> aplicarEfeitoArea(fase);
            case BUFF_BOSS -> aplicarBuffBoss(fase);
            case MENSAGEM -> enviarMensagem(fase);
            case INVESTIDA -> executarInvestida(fase);
            case CHUVA_PROJETEIS -> executarChuvaProjeteis(fase);
            case ONDA_DE_CHOQUE -> executarOndaDeChoque(fase);
            case CURAR -> executarCurar(fase);
            case TELEPORTE_ATAQUE -> executarTeleporteAtaque(fase);
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

    /** Avança rápido na direção do jogador mais próximo, com um empurrão no impacto. */
    private void executarInvestida(FaseConfig fase) {
        Player alvo = jogadorMaisProximo();
        if (alvo == null) return;

        double forca = fase.getParametroNumero("forca", 2.2);
        Vector direcao = alvo.getLocation().toVector().subtract(bossAtivo.getLocation().toVector()).normalize();
        direcao.setY(0.35);
        bossAtivo.setVelocity(direcao.multiply(forca));
    }

    /** Faz "flechas" caírem do céu ao redor de jogadores próximos. */
    private void executarChuvaProjeteis(FaseConfig fase) {
        int quantidade = (int) fase.getParametroNumero("quantidade", 6);
        double raio = fase.getParametroNumero("raio", 15);
        double danoPorProjetil = fase.getParametroNumero("dano-por-projetil", 4);

        for (Player p : bossAtivo.getWorld().getPlayers()) {
            if (p.getLocation().distance(bossAtivo.getLocation()) > raio) continue;

            for (int i = 0; i < quantidade; i++) {
                double offsetX = ThreadLocalRandom.current().nextDouble(-3, 3);
                double offsetZ = ThreadLocalRandom.current().nextDouble(-3, 3);
                Location origem = p.getLocation().clone().add(offsetX, 15, offsetZ);

                org.bukkit.entity.Arrow flecha = p.getWorld().spawn(origem, org.bukkit.entity.Arrow.class);
                flecha.setVelocity(new Vector(0, -1.2, 0));
                flecha.setShooter(bossAtivo);
                flecha.setDamage(danoPorProjetil);
            }
        }
    }

    /** Empurra e causa dano em todo mundo num raio ao redor do boss. */
    private void executarOndaDeChoque(FaseConfig fase) {
        double raio = fase.getParametroNumero("raio", 8);
        double dano = fase.getParametroNumero("dano", 6);
        double forca = fase.getParametroNumero("forca", 1.5);

        for (Player p : bossAtivo.getWorld().getPlayers()) {
            double distancia = p.getLocation().distance(bossAtivo.getLocation());
            if (distancia > raio) continue;

            p.damage(dano, bossAtivo);
            Vector direcao = p.getLocation().toVector().subtract(bossAtivo.getLocation().toVector());
            if (direcao.lengthSquared() < 0.0001) direcao = new Vector(1, 0, 0);
            direcao.normalize().setY(0.4);
            p.setVelocity(direcao.multiply(forca));
        }

        bossAtivo.getWorld().spawnParticle(Particle.EXPLOSION, bossAtivo.getLocation(), 1);
        bossAtivo.getWorld().playSound(bossAtivo.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
    }

    /** O boss recupera uma % da vida máxima — mecânica de "fúria/desespero". */
    private void executarCurar(FaseConfig fase) {
        double percentual = fase.getParametroNumero("percentual", 10);
        double cura = vidaMaxima * (percentual / 100.0);
        double novaVida = Math.min(vidaMaxima, bossAtivo.getHealth() + cura);
        bossAtivo.setHealth(novaVida);

        Bukkit.broadcastMessage("§c§l" + ChatColor.stripColor(bossAtivo.getCustomName()) + " §crecuperou parte da vida!");
        bossAtivo.getWorld().spawnParticle(Particle.HEART, bossAtivo.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5);
    }

    /** Teleporta o boss pra perto de um jogador aleatório e desfere um golpe surpresa. */
    private void executarTeleporteAtaque(FaseConfig fase) {
        List<Player> jogadores = bossAtivo.getWorld().getPlayers();
        if (jogadores.isEmpty()) return;

        Player alvo = jogadores.get(ThreadLocalRandom.current().nextInt(jogadores.size()));
        double distanciaTeleporte = fase.getParametroNumero("distancia", 2.0);
        double danoBonus = fase.getParametroNumero("dano-bonus", 6);

        double angulo = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        Location destino = alvo.getLocation().clone().add(Math.cos(angulo) * distanciaTeleporte, 0, Math.sin(angulo) * distanciaTeleporte);
        destino.setDirection(alvo.getLocation().subtract(destino).toVector());

        bossAtivo.getWorld().spawnParticle(Particle.PORTAL, bossAtivo.getLocation(), 30, 0.5, 0.5, 0.5);
        bossAtivo.teleport(destino);
        bossAtivo.getWorld().spawnParticle(Particle.PORTAL, destino, 30, 0.5, 0.5, 0.5);
        bossAtivo.getWorld().playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        alvo.damage(danoBonus, bossAtivo);
    }

    private Player jogadorMaisProximo() {
        Player maisProximo = null;
        double menorDistancia = Double.MAX_VALUE;
        for (Player p : bossAtivo.getWorld().getPlayers()) {
            double distancia = p.getLocation().distance(bossAtivo.getLocation());
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                maisProximo = p;
            }
        }
        return maisProximo;
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
        perfilAtivo = null;
    }
}
