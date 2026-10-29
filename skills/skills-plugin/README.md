# SkillsPlugin

Sistema de skills de combate estilo RPG, com 11 skills, progressão por nível, GUI visual e recompensas em dinheiro.

## Skills incluídas

| Skill | Ganha XP com | Bônus no nível 50 |
|---|---|---|
| Espadas | Dano com espada | +25% dano |
| Machado | Dano com machado | +20% dano |
| Arqueiro | Dano com arco/besta | +25% dano |
| Porradeiro | Dano com mão vazia | +30% dano, 10% chance de Lentidão (sem aviso no chat) |
| Tridente | Dano com tridente | +25% dano, 10% chance de Empurrão |
| Lanceiro | Dano com lança (qualquer material) | +25% dano, 10% chance de Sangramento (dano real ao longo do tempo) |
| Marreteiro | Dano com maça | +25% dano, 10% chance de Atordoamento real (trava movimento por 3s) |
| Esquiva | Tomar dano se movendo | 20% chance de esquiva total |
| Defesa | Tomar dano e sobreviver | -15% dano recebido |
| Bloqueio | Bloquear com escudo levantado | -20% a mais no bloqueio |
| Crítico | Golpes usando a **Adaga** (item especial) | 10% de chance de crítico (fixo: +40% de dano quando proca) |
| Regeneração | Tempo em combate recente | +50% velocidade de regen natural |
| Agilidade | Se mover durante combate | +10% velocidade de movimento |

- **PvP conta XP cheio; PvE (mobs) conta metade** — configurável em `xp.multiplicador-pve`
- **Nível máximo: 50** por skill, com curva de XP progressiva — configurável em `config.yml`
- **Persistência real**: os dados ficam salvos em `plugins/SkillsPlugin/playerdata/<uuid>.yml`, sobrevivem a reinício do servidor

## Comandos

| Comando | Ação |
|---|---|
| `/skills` | Abre o menu visual (GUI) com todas as skills e barras de progresso |
| `/skills ver` | Mostra um resumo rápido no chat |
| `/skills top [skill]` | Top 10 de uma skill (prestígio, nível e XP), ou da soma de todas. Funciona no console |
| `/skills prestigio <skill> [confirmar]` | No nível 50, volta a skill pro nível 1 em troca de um prestígio (veja abaixo) |
| `/skills admin xpdobro <multiplicador> <horas>` | (staff) Liga o XP extra agora; `parar` desliga |
| `/skills admin setlevel <jogador> <skill> <nível>` | (staff) Define manualmente o nível de uma skill de um jogador. Requer permissão `skills.admin` (padrão: op) |

Nomes de skill aceitos no comando admin: `espadas`, `machado`, `arqueiro`, `porradeiro`, `tridente`, `esquiva`, `defesa`, `bloqueio`, `critico`, `regeneracao`, `agilidade`.

Ao usar `setlevel`, o XP acumulado daquela skill é zerado (já que o "degrau" mudou manualmente, não faria sentido manter XP de um nível diferente).

## Bônus de XP do VIP

Quem tem VIP ganha XP a mais em todas as skills: +10% no Bronze, +20% no Prata, +30% no Ouro e +50% no Diamante. O bônus vem por permissão (`vip.xp.<tier>`, dada pelos grupos do LuckPerms), configurado em `vip-bonus-xp`. Vale o maior que o jogador tiver, e vale também para o XP das missões.

**XP de bônus.** O bônus de ativação do VIP dá XP pelo `adicionarXpBonus`: sobe os níveis, mas não paga o `recompensa.dinheiro-por-nivel` nem manda o "SKILL UP" de cada nível, e não recebe o bônus de XP do VIP por cima.

## Prestígio

No nível máximo, `/skills prestigio <skill>` explica o que acontece, e `/skills prestigio <skill> confirmar` volta a skill pro nível 1 com o XP zerado e dá um prestígio nela. Cada prestígio deixa **tudo o que a skill dá** (bônus de dano, redução, chances de efeito e de crítico) `prestigio.bonus-por-prestigio` mais forte (10% por padrão), até `prestigio.maximo` (5). Ou seja: logo depois do reset a skill fica mais fraca, e no 50 de novo ela passa do que era.

O prestígio aparece como **✦N** no menu, no `/skills ver`, no `/skills top` (que ordena por prestígio antes do nível) e no placar. O servidor todo é avisado no chat. Fica salvo no `playerdata` (`<skill>.prestigio`).

## Escudo Perfeito (parry)

Levantar o escudo no máximo `parry.janela-ticks` (6 ticks, 0,3 s) antes de um golpe corpo a corpo **anula o dano todo** e atordoa quem bateu por `parry.atordoar-segundos` (1 s): o jogador não anda e nenhum golpe corpo a corpo dele conta; o mob leva Lentidão forte e também não acerta. A janela ganha +1 tick a cada 10 níveis de Bloqueio. Dá XP de Bloqueio (`parry.xp-bloqueio`). Depois de um parry, o próximo só vale depois de `parry.recarga-segundos` (3 s), pra subir e descer o escudo sem parar não virar imunidade. Boss não fica atordoado (o dano é anulado igual). Só vale com escudo, não com o bloqueio da espada do CombatePlugin.

## Skill em uso (pro placar)

O PlacarPlugin mostra a skill em uso pelos métodos `textoSkillEmUso(UUID)` e `progressoSkillEmUso(UUID)`: é a skill da arma do último golpe no último minuto ou, sem nenhum, a da arma na mão (mão vazia sem luta recente não mostra nada).

## XP em dobro e efeito do level up

`xp-dobro` no config: nos dias da lista (`[SABADO, DOMINGO]` por padrão) todo XP de skill vale `multiplicador`, por cima do bônus do VIP (combate, missões e boss; o bônus de ativação do VIP não). A staff também liga na hora com `/skills admin xpdobro`, que vale mais que o dos dias fixos e sobrevive a reinício. O começo e o fim são anunciados no chat, e quem entra durante o evento é avisado.

**Maldição do boss.** Quando um boss vai embora sem ser derrotado, o BossPlugin amaldiçoa o servidor: esse mesmo XP sai multiplicado por `maldicao.xp-skill-multiplicador` do BossPlugin (0,9) até o próximo boss morrer ou passar 24 h. O SkillsPlugin lê isso por reflexão (`fatorXpMaldicao()`); sem o BossPlugin, nada muda.

Ao subir de nível, duas espirais da partícula da skill sobem em volta do jogador (crítico nas Espadas, coração na Regeneração...), e no fim vem a explosão de totem. Todo mundo em volta vê.

O `/skills top` lê o `playerdata` de todos (e o cache de quem está online) e guarda o resultado por um minuto.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia (opcional — sem ele, as skills funcionam, só não dão dinheiro ao subir de nível)

## Como compilar

```bash
cd skills-plugin
mvn clean package
```

O `.jar` sai em `target/SkillsPlugin.jar`.

## A Adaga (item especial pra treinar Crítico)

Diferente das outras skills, o **Crítico não sobe lutando com uma arma normal**. Ele precisa de uma **Adaga**: uma espada marcada de forma especial (mesma técnica de segurança usada no plugin do Cheque — não dá pra falsificar renomeando no bigorna).

- **Como criar:** `/skills admin criaradaga` — pegue qualquer espada na mão e rode o comando; ela vira uma Adaga
- **Enquanto empunhada:** os golpes treinam Crítico em vez de Espadas — é uma escolha estratégica do jogador
- **O bônus da Adaga é passivo e universal:** a chance de proc-ar crítico funciona com **qualquer arma** (não só a Adaga), calculada por cima de todos os outros bônus já aplicados (dano base + bônus de arma + crítico vanilla do pulo, se tiver rolado)

## Mensagens de combate

Golpes especiais (que não acontecem em todo hit, só ocasionalmente) avisam os dois lados no chat:

| Situação | Atacante vê | Atacado vê |
|---|---|---|
| Lentidão (Porradeiro) | *(sem mensagem, de propósito)* | *(sem mensagem, de propósito)* |
| Atordoamento real (Marreteiro) | "Você atordoou o inimigo!" | "Você foi atordoado!" |
| Sangramento (Lanceiro) | "Inimigo perfurado!" | "Você foi perfurado!" |
| Empurrão (Tridente) | "Você empurrou o inimigo!" | "Você foi empurrado!" |
| Esquiva | "Seu ataque foi esquivado!" | "Você esquivou do ataque!" |
| Bloqueio | "Seu ataque foi bloqueado!" | "Você bloqueou o ataque!" |
| Crítico | "Golpe crítico!" (ou "§6§lHEADSHOT!" se foi com arco/besta) | *(sem mensagem — evita spam, já que crítico pode proc bem mais vezes)* |

Bônus de dano "normais" (Espadas, Machado, Arqueiro, Lanceiro, Marreteiro, Tridente, Defesa) **não** geram mensagem — eles acontecem em praticamente todo golpe, e um aviso a cada hit ia virar spam. Só os *procs especiais* (a chance de efeito extra) geram aviso — exceto Porradeiro, que fica silencioso por escolha de design.

## ⚠️ Simplificações técnicas (importante entender)

1. **Sangramento e o crédito do abate**: o dano do sangramento é aplicado via `vitima.damage(dano)` **sem** referência ao atacante, de propósito — isso evita que cada tique de sangramento dispare o `CombatListener` de novo (o que causaria um loop de procs/XP a cada segundo). Pra quem aplicou levar o abate (Bounty, clãs, missões, Gladiador), cada tique marca o atacante como o último jogador que bateu na vítima (`setKiller`). Se o atacante sair do servidor, os tiques seguintes não marcam ninguém.

2. **Indicador visual do Sangramento**: como não é um `PotionEffect` de verdade (não aparece nos ícones padrão do inventário), mostro uma mensagem na action bar da vítima ("❤ Sangrando (Xs)") e partículas vermelhas visíveis pra quem estiver por perto, a cada segundo que o efeito estiver ativo.

3. **Duração da Náusea**: ~~o efeito de tela "tremendo" do Minecraft tem uma rampa de intensidade~~ *(essa skill foi substituída por atordoamento real — ver item abaixo)*.

4. **Atordoamento do Marreteiro (stun de verdade)**: implementado cancelando qualquer mudança de posição no `PlayerMoveEvent` enquanto o efeito está ativo — o jogador consegue olhar em volta livremente, mas não anda, não pula, e **não cai por gravidade** (fica "flutuando" no lugar durante o stun, já que qualquer mudança de Y também é bloqueada). Isso só funciona em jogadores (mobs não disparam `PlayerMoveEvent`); contra mobs, aplicamos uma Lentidão bem forte como substituto.

5. **Quem morre renasce limpo** (`MorteListener`): o Sangramento e o Atordoamento param na hora da morte (as tarefas só conferiam a morte uma vez por segundo, e quem renascia antes seguia sangrando no spawn), e um tique depois de renascer qualquer poção que sobrou sai (a que é aplicada com o jogador já na tela de morte, como o Wither em área do boss, atravessava o renascimento).

2. **Lança usa o material nativo do jogo** (`_SPEAR`, do drop "Mounts of Mayhem") — não precisou de item marcado tipo a Adaga, já que a lança é uma arma de verdade no jogo agora.

3. **Ordem de aplicação de dano**: aplico os modificadores (bônus de arma, crítico da skill, esquiva, bloqueio, defesa) diretamente em `event.setDamage()`, sem usar o sistema interno de `DamageModifier` do Bukkit (que separa dano base/armadura/encantamento/etc). Isso é mais simples de manter e funciona bem na prática, mas significa que os bônus das skills se aplicam **depois** da armadura/encantamentos vanilla já terem sido calculados pelo jogo.

4. **Tridente**: o dano de arma normal continua igual; o "empurrão" é um efeito adicional na mesma skill, não uma skill nova.
