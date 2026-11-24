# Roteiro de gravação dos Reels

Grava-se **em pé**: a janela do Minecraft com a área do jogo em **576x1024** (9:16) e o OBS com
tela base e saída em **1080x1920**, 60 fps, MP4, em `tools/divulgacao/public/brutos/` (o
`Reel.jsx` aceita deitado também, cortando o meio, mas em pé você vê o enquadramento na hora).

## Como gravar (servidor de teste)

- **Figurantes** (`tools/bots/figurantes.js <nick>`): bots com nick de jogador que andam à toa.
  No chat: `!aqui`, `!raio N`, `!parem`, `!andem` e `!lutar` (vão pra arena e lutam com o boss
  do `/boss start <id>`).
- **Câmera em trilho** (`tools/bots/camera.js`, pelo mesmo chat): `!cena t1`, `!ponto` em cada
  ponto, `!gravar 15`. O servidor move a câmera tique a tique (um datapack gerado na hora), então
  sai liso. Os pontos de cada cena ficam em `tools/bots/cenas-camera.json`.
- Antes de montar os pontos de um lugar novo, `tools/bots/mapa-altura.js` mapeia o terreno, pra a
  câmera não atravessar árvore, telhado ou teto.
- **Nada de câmera balançando** de um lado pro outro (dá enjoo): retas, curvas suaves e no máximo
  um giro lento no fim.
- **F1** esconde a interface, mas também os nomes dos NPCs. Onde os nomes importam (loja), deixe
  sem F1 e use `/placar`, `/missoes barra` e o chat oculto.
- Distância de visão: o servidor de teste está em 24 chunks; o jogo também precisa estar em 24.
- O Remotion sai em `yuvj420p`, que muito celular não toca. Pra postar, converta com o ffmpeg do
  `node_modules/@remotion/compositor-win32-x64-msvc`: `-c:v libx264 -pix_fmt yuv420p -crf 18
  -c:a copy -movflags +faststart` (o `saida/tour-celular.mp4` foi feito assim).
- Gameplay em pé: grave em **F5** (terceira pessoa). Na janela estreita o jogo empurra a mão pra
  fora da tela, então em primeira pessoa a espada não aparece.
- Antes de gravar: chat oculto, rastro de cosmético desligado (os corações poluem a luta) e escala
  da interface 3 ou 4 se for mostrar um baú ou menu. Se o chat escapar, `"ofuscarChat": true` no
  reel borra o canto dele.
- A capa vai à parte (1080x1920, título na faixa do meio, que é o que o perfil mostra).

## Feito

- **Tour** (2026-11-21, postado): `t1` a `t6` em `public/brutos`, reel `Tour` no `reels.json`.
- **Tesouros** (2026-11-21, postado): `x.mp4` (mapa lendário, câmara com os figurantes no `!brigar`,
  Guardião Ancestral), reel `Tesouros`. Faltou a abertura do baú (escura e pequena na gravação).
- **Recursos** (2026-11-21, pronto): `r1` a `r6` (cerejeiras, picos, caverna, Nether, bastião, End
  com o dragão), reel `Recursos`. Com shader, o Nether fica preto na câmera em trilho (o shader só
  aplica a visão noturna quando a câmera é o próprio jogador): grave o Nether voando no criativo ou
  sem shader (K). O bot da câmera tem que estar no mesmo mundo (o chat é local); ao trocar de mundo,
  teleporte antes e ponha em espectador depois (o Multiverse volta o modo de jogo).

- **Eventos** (2026-11-23, pronto): `e1` Corre ou Cai, `e2`/`e2b` Spleef, `e3`/`e3b` Vietnã (jogando e
  da arquibancada), `e4` Sobrevivência, `e5` Corrida Maluca, `e6` Labirinto (escuro de propósito, dá
  suspense), reel `Eventos`. Figurantes: `!evento <id>`, `!comecar`, `!fim`; com 9 (o máximo, o servidor
  é de 10) rodam numa janela separada (`node figurantes.js Viagrinha 9 8`), senão o Claude Code mata o
  processo quando falta memória. Feche os navegadores antes: Minecraft com shader, servidor, OBS e bots
  passam de 12 GB. Pra gravar, no teste: Spleef derretendo desde o começo e pilares do Vietnã sumindo
  aos 30 s (um a cada 6 s).

## Prioridade 1: os 3 primeiros Reels

### ReelTour (refazer) "Bem-vindo ao BrasaMC"
| Arquivo | O que gravar |
|---|---|
| `t1.mp4` | Voando por cima do spawn, devagar, até a fonte/centro |
| `t2.mp4` | Entrando na Avenida das Minas pelo portão norte, câmera na altura dos olhos |
| `t3.mp4` | Praça das Caixas: girando devagar em volta das 4 caixas |
| `t4.mp4` | Porto da pesca (`/warp pesca`): voando por cima das barracas e da água |
| `t5.mp4` | Loja Oficial: andando pelo corredor dos balcões |
| `t6.mp4` | Descendo o túnel da arena do boss, terminando de frente pra arena vazia |

### ReelBoss (refazer: os bosses agora são em 3D)
| Arquivo | O que gravar |
|---|---|
| `b1.mp4` | O boss nascendo (às 20h, ou chamado no teste), de frente, a uns 10 blocos |
| `b2.mp4` | Um ataque especial dele, com o jogador desviando |
| `b3.mp4` | Gente batendo nele (no servidor dos amigos, no horário de verdade, fica melhor) |
| `b4.mp4` | O boss caindo e o chão cheio de drop |
| `b5.mp4` | O Wither de domingo (pode ser no teste) |

### ReelCaixas "Abri 10 caixas"
| Arquivo | O que gravar |
|---|---|
| `c1.mp4` | Chegando na Praça das Caixas com a chave na mão (com interface) |
| `c2.mp4` | A roleta girando e parando (com interface, câmera parada de frente) |
| `c3.mp4` | Abrir várias de uma vez agachado |
| `c4.mp4` | Um prêmio bom saindo (spawner, máquina). Vale gravar várias e eu uso a melhor |

## Prioridade 2

### ReelMinaPvP "A mina que paga em dobro... se você sobreviver"
- `m1.mp4`: entrando pelo arco do saguão da Mina PvP
- `m2.mp4`: minerando rápido, com a picareta na mão
- `m3.mp4`: briga lá dentro (precisa de 2 ou mais pessoas)
- `m4.mp4`: os itens no chão depois da morte

### ReelTesouro "Achei um mapa..."
- `x1.mp4`: o Mapa do Tesouro na mão (com interface)
- `x2.mp4`: cavando até a câmara
- `x3.mp4`: os guardiões aparecendo
- `x4.mp4`: abrindo o baú (lendário, se der)

### ReelPesca
- `p1.mp4`: jogando a vara no porto, de lado
- `p2.mp4`: um peixe raro saindo (com interface pra mostrar o nome)
- `p3.mp4`: vendendo com `/peixes vender`

### ReelArenas (precisa de gente)
- `a1.mp4`: Spleef ou Corre ou Cai, de cima (o `/<evento> assistir` dá a vista da galeria)
- `a2.mp4`: alguém caindo na água
- `a3.mp4`: o vencedor

## Depois da gravação

Ponha os arquivos em `public/brutos/` e me avise. Eu escolho os trechos, monto as cenas no
`reels.json`, gero a trilha (`audio/sintetizar.py`, sem direito autoral) e renderizo em
`saida/`. Cada Reel fica com 15 a 25 s.
