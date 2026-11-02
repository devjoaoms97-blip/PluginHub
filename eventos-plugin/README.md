# EventosPlugin

Eventos do servidor. O primeiro é o **Vietnã**.

## Vietnã

Pilares de 2x2 sobre a água. Cada um nasce num pilar com o mesmo kit e tenta derrubar os outros. **Ninguém perde vida**: os golpes só empurram. Caiu na água, está fora e vai pra arquibancada. O último de pé leva um **cheque de R$ 10.000** (ChequePlugin; sem ele, o valor cai na conta).

### Como funciona

1. **Abertura.** Todo dia às 10:00 e às 16:00 (`vietna.agenda`; o Gladiador e o dragão ficam à noite), ou pela staff com `/vietna abrir`. O anúncio sai no chat e no Discord.
2. **Inscrição** (3 minutos, com lembretes). `/vietna entrar` é grátis, mas:
   - o **inventário precisa estar totalmente vazio**: mochila, barra, armadura, a outra mão e o cursor;
   - **uma inscrição por IP** (a permissão `eventos.ip.ignorar` pula a trava; é só pros bots de teste, que entram todos pelo mesmo IP);
   - quem está no mundo das arenas (num duelo, por exemplo) não entra.
3. **Início.** Com pelo menos **3** inscritos (`vietna.minimo-jogadores`), cada um vai pra um pilar sorteado, com vida e fome cheias e sem efeitos. Quem saiu do servidor, pegou um item ou foi pras arenas durante a inscrição fica de fora. A arena é construída de novo a cada evento, então os pilares voltam.
4. **Preparação** (10 s): nos pilares, sem kit e sem atirar.
5. **Luta.** O kit chega:
   - arco com Impulso I e Infinidade (inquebrável), 1 flecha;
   - 16 bolas de neve, 3 cargas de vento e uma vara de pesca (inquebrável).

   A cada 30 s, mais 8 bolas de neve e 1 carga de vento voltam, até o que o kit dá.

   **Empurrão:** o plugin cancela o dano de tudo o que acerta e empurra na direção do projétil (`vietna.empurrao`). A flecha empurra conforme a força do tiro. A explosão da carga de vento empurra como no jogo, e ela também serve pra pular de um pilar pro outro. Soco não vale.

   **Pilares somem:** depois de 2 minutos de luta, a cada 12 s um pilar fica com o topo vermelho e some 3 s depois. Sempre sobram pelo menos 2.
6. **Eliminação.**
   - **Cair na água** ou **sair da arena** (passar por cima do vidro com a carga de vento, por exemplo): perde o kit e vai pra **arquibancada**, de onde sai com `/spawn`.
   - **Desconectar**: perde o kit e volta pra onde estava antes de se inscrever.
   - Se o servidor cair no meio, quem estava lutando fica em `vietna-pendentes.yml` e, ao entrar de novo, perde o kit e volta pra onde estava.
7. **Fim.** O último de pé perde o kit, volta pra onde estava e recebe o cheque. Depois de 8 minutos de luta, quem estiver de pé divide o prêmio. As vitórias ficam em `vietna-vitorias.yml` (`/vietna top`).

### Regras da arena

- **Comandos:** lutando, só o `/vietna`. Na arena e na arquibancada, só `/vietna` e `/spawn` (`vietna.comandos-liberados`). Nada de `/tpa`, `/sethome`, `/home`, `/back` etc. A staff (`eventos.admin`) passa.
- **Teleporte:** lutando, ninguém sai por teleporte, só pelos do evento. E nenhum teleporte de fora (`/tpa`, `/home`, warp, `/tp` da staff num jogador comum...) leva alguém pra dentro da arena ou da arquibancada: pra assistir, `/vietna assistir`.
- **Quem assiste não atira:** da arquibancada, nenhum projétil sai, e quem luta só toma empurrão de outro participante.
- Lutando, não se joga item fora, não se pega item, não se quebra nem coloca bloco e não tem fome.

### A arena

Construída pelo plugin (`/vietna construir`, e de novo a cada evento) no mundo `arenas`, em volta de (0, 600):
- 6 x 6 pilares de 2x2, de tronco da selva com topo de musgo, 3 blocos de vão entre eles (dá pra pular correndo) e 6 de altura acima da água;
- água com 3 de fundo, vidro em volta até 8 acima dos pilares;
- a arquibancada no lado norte, atrás do vidro e acima dos pilares, com dois degraus.

A caixa da piscina (de [-16, 60, 584] a [16, 78, 616]) está no AreasPlugin como "arena do vietnã": lá a carga de vento vale (`carga-de-vento-liberada`) e ninguém marca home. O `tools/bots/montar-mapa.js construir arenas` chama o `/vietna construir`.

## Comandos

| Comando | O que faz |
|---|---|
| `/vietna` ou `/vietna info` | Como funciona, a agenda e o que está acontecendo agora |
| `/vietna entrar` / `/vietna sair` | Inscrição (sair só antes de começar) |
| `/vietna assistir` | Vai pra arquibancada |
| `/vietna top` | Quem mais venceu |

Staff (`eventos.admin`): `/vietna abrir`, `/vietna iniciar` (fecha as inscrições e começa agora), `/vietna cancelar`, `/vietna construir` (refaz a arena e mostra a caixa pro AreasPlugin) e `/vietna reload`.

## Config

Tudo em `vietna:` no `config.yml`: agenda, inscrição, mínimo de jogadores, prêmio, tempos da luta, pilares que somem, kit e reabastecimento, força dos empurrões, posição e tamanho da arena e os comandos liberados. Mudou a arena (centro, número de pilares, vão ou altura)? Atualize a caixa "arena do vietnã" no config do AreasPlugin com o que o `/vietna construir` mostra.

## Teste

`node tools/bots/teste-vietna.js`
