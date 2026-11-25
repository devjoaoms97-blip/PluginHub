# CombatePlugin

Combate no estilo da 1.8, só no que foi escolhido. Vale no servidor todo.

| Mecânica | Como fica |
|---|---|
| Recarga do ataque | **Não existe.** Todo golpe sai com o dano inteiro, por mais rápido que se clique |
| Tempo entre golpes | **Configurável.** Aqui é 0,1 s: até 10 golpes cheios por segundo. Dá para voltar ao do jogo (0,5 s) ou diminuir até 0, quando todo clique conta |
| Bloqueio com a espada | **Volta.** Segurar o botão direito com a espada reduz o dano pela metade, de qualquer lado. Só vale **sem escudo na segunda mão**; com escudo, quem bloqueia é o escudo |
| Besta ou arco com espada | **Carregar trava a espada.** Enquanto a besta carrega (ou o arco é puxado), e por 0,5 s depois de disparar, nenhum golpe corpo a corpo conta. Bater e depois carregar continua livre |
| Ataque em área, repulsão, regeneração, maçãs, ender pearl, escudo e machado | Como no jogo atual |

## Como funciona

- **Sem recarga.** O plugin soma 1.000 à velocidade de ataque de cada jogador, e a barra de recarga fica sempre cheia. O modificador não fica salvo no jogador, então tirar o plugin desfaz.
- **Tempo entre golpes.** Depois de um golpe, o alvo fica um tempo sem tomar outro golpe cheio. No jogo e na 1.8, são 10 ticks (0,5 s), o que limita o dano a dois golpes por segundo. Aqui esse tempo é `invulnerabilidade.ticks-entre-golpes`, e a staff muda com o servidor ligado: `/combate intervalo <ticks>`, onde 20 ticks = 1 s.
  - Com 2 (o padrão daqui, 0,1 s, decisão de 2026-11-25), cabem dez golpes por segundo. De 2026-11-17 a 2026-11-25 foi 5 (0,25 s).
  - Com 0, todo clique conta, e o dano por segundo depende de quantas vezes o jogador clica.
  - Só vale para golpe corpo a corpo de jogador. Lava, fogo, cacto, explosão e flecha seguem o tempo do jogo; com 0 para tudo, a lava mataria quase na hora.
- **Bloqueio com a espada.** A espada da mão principal ganha o mesmo componente que faz o escudo bloquear (`blocks_attacks`):
  - a animação de bloqueio e o "está bloqueando" do jogo funcionam;
  - a skill Bloqueio, das Skills, também vale para a espada;
  - o bloqueio tira 50% do dano, de qualquer direção, sem gastar a espada, e o machado não desarma;
  - com escudo na segunda mão, o componente sai da espada em até 2 ticks;
  - quando a espada sai da mão (troca de slot, troca de mão ou é jogada no chão), o componente sai junto.
- **Em combate** (`em-combate`). Quem bate ou apanha de outro jogador (golpe ou flecha) fica **5 s** em combate, VIP ou não, com aviso na barra de ação. Só conta o golpe que valeu: no spawn, fora das caixas de PvP, nada marca.
  - Em combate, os comandos de teleporte da lista (`/spawn`, `/warp`, `/home`, `/tpa`, `/tpaccept`, `/back`, `/mina`, `/vip warp`...) não funcionam, e nenhum teleporte por comando leva o jogador (o `/tpaccept` de outro, o fim da espera do Essentials).
  - **Quem desloga em combate morre na hora**, com os itens no chão (e o abate fica pra quem bateu por último), e o chat avisa. Expulsão e desligamento do servidor não matam. No mundo `arenas` não vale: o Gladiador e o duelo já tratam a desconexão.
  - O tempo de espera do teleporte pra quem não é VIP (3 s, pode andar, só dano cancela) é do Essentials (`teleport-delay`), com `essentials.teleport.timer.move` no grupo `default` e `essentials.teleport.timer.bypass` nos grupos VIP.

## Config

```yaml
sem-recarga:
  ativado: true
invulnerabilidade:
  ticks-entre-golpes: 2    # 20 = 1 s; 10 = jogo e 1.8; 0 = todo clique conta
bloqueio-espada:
  ativado: true
  reducao-percentual: 50   # quanto do dano o bloqueio tira
  angulo: 180              # 180 = de qualquer lado (o escudo usa 90)
```

## Comandos

| Comando | O que faz |
|---|---|
| `/combate intervalo [ticks]` | Mostra ou muda o tempo entre golpes, e salva no config (staff, `combate.admin`) |
| `/combate reload` | Recarrega o config. Ligar ou desligar uma mecânica inteira pede reinício |

## Para acompanhar

- **Golpe com a segunda mão: removido (2026-11-17).** Existiu o botão direito num inimigo batendo com a arma ou o soco da segunda mão, com o tempo contado por mão. Saiu por decisão do dono do servidor; o tempo entre golpes passou de 0,5 s por mão para 0,25 s no geral (0,1 s desde 2026-11-25).

- **Itens fortes sem recarga.** A maça, a lança e o machado do kit mensal do VIP ficam mais fortes sem recarga. A decisão (2026-10-23) foi testar jogando e ajustar depois.
- **Visual.** Os testes com bots conferem o dano e o bloqueio, mas não o visual. Vale olhar no jogo como a espada aparece bloqueando, em primeira e terceira pessoa.
