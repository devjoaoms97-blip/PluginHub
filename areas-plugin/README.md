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
