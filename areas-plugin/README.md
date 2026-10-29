# AreasPlugin

Áreas fechadas: lugares de onde só se sai pelo caminho (portões, portais, `/spawn`), pra quem joga e pra quem assiste.

## O que bloqueia

Dentro de uma área fechada:

| Bloqueio | Como |
|---|---|
| Pérola do fim | Não sai e não é gasta. E quem joga de fora não cai lá dentro |
| Elytra | Não plana. Quem entra planando cai, sem dano de queda |
| Voo (`/fly` do VIP) | Não liga. Quem entra voando cai, sem dano de queda |
| Tridente com Correnteza | Não carrega |
| Fruta do coro | Não teleporta |
| Carga de vento | Não sai e não é gasta |
| Construção | Ninguém quebra nem coloca bloco, usa balde, põe fogo, pisoteia plantação nem mexe em moldura, quadro e suporte de armadura. Explosão, fogo que espalha e mob (enderman) também não mexem em nada. As minas do RankupPlugin ficam de fora: lá quem cuida é ele |

**PvP só na arena do boss e, em ciclos, na warp mobs.** No mundo `spawn`, o PvP do Minecraft fica ligado, mas um jogador só machuca outro se os dois estiverem dentro de uma das caixas de `pvp-so-nas-caixas`: a arena do boss, depois do pulo do túnel (PvP sempre; a escadaria e o túnel não têm), e a warp mobs da linha vermelha pra dentro (o vestíbulo nunca tem).

**Nas arenas, PvP só no piso de luta.** No mundo `arenas`, as caixas são o piso do X1 e o do coliseu: lá dentro, o Gladiador e o duelo decidem quem bate em quem. As plateias (a galeria do X1, em y 80, e o deck de vidro do coliseu, em y 77) e o saguão ficam sem PvP.

**Mensagem.** O que é barrado nas áreas fechadas avisa "⛔ Área protegida: aqui não dá pra %coisa%." (`mensagem`).

**Aviso na entrada da arena do boss.** Uma caixa sem ciclo pode ter `paineis` (lista de `[x, y, z]`): hologramas avisando "⚔ PvP LIGADO ⚔ o tempo todo, daqui pra frente". A arena do boss tem um, no fim do túnel da muralha, na borda do pulo: a caixa de PvP começa ali (a escadaria e o túnel não têm PvP). Mudou a posição no config, o `/areas reload` apaga o holograma antigo. O texto muda com `texto-painel` (`%nome%` é o nome da caixa).

**PvP em ciclos.** Uma caixa com `ciclo: {desligado: 900, ligado: 900}` alterna sozinha: 15 min sem PvP, 15 com. A fase sai do relógio, então não muda quando o servidor reinicia. Quem está em `avisos` (o salão, o vestíbulo e a escadaria) vê a barra de boss com a fase e o tempo, o título com som na troca e o aviso no chat 1 minuto antes de ligar. Na parede de cima da entrada, o holograma de `painel` mostra a fase e o tempo, e o concreto da `moldura` fica verde sem PvP e vermelho com. A staff força uma fase com `/areas ciclo mobs ligado|desligado` (o ciclo segue dali), e o `/areas` mostra como ele está.

Com uma config de uma versão antiga, as seções que faltam (como a `pvp-so-nas-caixas`) entram no arquivo quando o plugin liga. Antes, a seção que faltava voltava vazia, e o PvP passava no spawn inteiro. No resto do spawn, na Avenida das Minas e na loja, nada passa: nem golpe, nem flecha, nem poção de arremesso. Por isso o `configurar-mundos.bat` liga o PvP do mundo `spawn`. Mundo que não está na lista segue o PvP do próprio mundo.

**Teleporte por comando (só nas arenas).** No mundo `arenas`, os comandos de `teleporte-por-comando.comandos` (`/tpa`, `/tpahere`, `/tpaccept`, `/back`, `/home`, `/sethome` e os apelidos do EssentialsX) não funcionam. E o teleporte que um deles acabou de pedir, até 10 s antes, não leva ninguém pra lá: nem o `/back` pro lugar em que o jogador morreu na arena, nem uma casa antiga marcada lá, nem o `/tpa` aceito antes de alguém entrar. `/spawn` e `/warp` (a plateia) continuam. As áreas com essa regra ficam em `teleporte-por-comando.areas`.

Cada um liga e desliga em `bloquear`. Os admins (`areas.ignorar`) e quem está no criativo passam. Os jogadores que assistem (plateia do X1 e do Gladiador) seguem as mesmas regras de quem joga.

No modo espectador o jogo sempre voa e atravessa parede, e isso não dá pra desligar: quem assiste duelo assim fica preso pelo DueloPlugin (a até 25 blocos da arena, sem comandos nem teleporte). Quem assiste o Gladiador fica na plateia, no modo sobrevivência, e vale tudo daqui.

## Áreas

Em `areas` no config, cada uma é um mundo inteiro ou uma caixa (`min` e `max`). As que vêm:

- **spawn:** o mundo `spawn` inteiro (o spawn, a avenida das minas, a arena do boss e a Loja Oficial);
- **arenas:** o mundo `arenas` inteiro (o saguão, o X1, o Gladiador e as plateias).

O mundo dos terrenos e o de recursos ficam livres.

## Comandos

| Comando | Ação |
|---|---|
| `/areas` | (staff) Lista as áreas e diz em qual você está |
| `/areas reload` | (staff) Recarrega o config |

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/AreasPlugin.jar`. Requisitos: Paper 26.2.
