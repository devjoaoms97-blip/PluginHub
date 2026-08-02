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
| `/skills admin setlevel <jogador> <skill> <nível>` | (staff) Define manualmente o nível de uma skill de um jogador. Requer permissão `skills.admin` (padrão: op) |

Nomes de skill aceitos no comando admin: `espadas`, `machado`, `arqueiro`, `porradeiro`, `tridente`, `esquiva`, `defesa`, `bloqueio`, `critico`, `regeneracao`, `agilidade`.

Ao usar `setlevel`, o XP acumulado daquela skill é zerado (já que o "degrau" mudou manualmente, não faria sentido manter XP de um nível diferente).

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

1. **Sangramento não credita o atacante na morte**: o dano do sangramento é aplicado via `vitima.damage(dano)` **sem** referência ao atacante, de propósito — isso evita que cada tique de sangramento dispare o `CombatListener` de novo (o que causaria um loop de procs/XP a cada segundo). O efeito colateral é que, se alguém morrer *só* pelo sangramento (sem mais nenhum hit direto), a mensagem de morte do jogo pode não creditar quem aplicou o efeito.

2. **Indicador visual do Sangramento**: como não é um `PotionEffect` de verdade (não aparece nos ícones padrão do inventário), mostro uma mensagem na action bar da vítima ("❤ Sangrando (Xs)") e partículas vermelhas visíveis pra quem estiver por perto, a cada segundo que o efeito estiver ativo.

3. **Duração da Náusea**: ~~o efeito de tela "tremendo" do Minecraft tem uma rampa de intensidade~~ *(essa skill foi substituída por atordoamento real — ver item abaixo)*.

4. **Atordoamento do Marreteiro (stun de verdade)**: implementado cancelando qualquer mudança de posição no `PlayerMoveEvent` enquanto o efeito está ativo — o jogador consegue olhar em volta livremente, mas não anda, não pula, e **não cai por gravidade** (fica "flutuando" no lugar durante o stun, já que qualquer mudança de Y também é bloqueada). Isso só funciona em jogadores (mobs não disparam `PlayerMoveEvent`); contra mobs, aplicamos uma Lentidão bem forte como substituto.

2. **Lança usa o material nativo do jogo** (`_SPEAR`, do drop "Mounts of Mayhem") — não precisou de item marcado tipo a Adaga, já que a lança é uma arma de verdade no jogo agora.

3. **Ordem de aplicação de dano**: aplico os modificadores (bônus de arma, crítico da skill, esquiva, bloqueio, defesa) diretamente em `event.setDamage()`, sem usar o sistema interno de `DamageModifier` do Bukkit (que separa dano base/armadura/encantamento/etc). Isso é mais simples de manter e funciona bem na prática, mas significa que os bônus das skills se aplicam **depois** da armadura/encantamentos vanilla já terem sido calculados pelo jogo.

4. **Tridente**: o dano de arma normal continua igual; o "empurrão" é um efeito adicional na mesma skill, não uma skill nova.

## Ideias para expandir

- Efeito visual diferente pra cada tipo de level up (ex: partícula própria por skill)
- Prestígio: ao chegar no nível 50, permitir "resetar" a skill em troca de um bônus permanente extra
- Scoreboard/placar lateral mostrando XP da skill que está sendo usada no momento
- Skill de "Escudo Perfeito" (parry): se o bloqueio acontecer no exato milissegundo do golpe, anula 100% do dano e atordoa o atacante
