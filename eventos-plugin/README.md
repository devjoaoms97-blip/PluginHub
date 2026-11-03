# EventosPlugin

Eventos de arena do servidor: o **Vietnã** (`/vietna`), o **Spleef** (`/spleef`), o **Corre ou Cai** (`/correoucai`), a **Sobrevivência** (`/sobrevivencia`) e a **Corrida Maluca** (`/corrida`). Só um fica aberto por vez (a agenda pula o horário se outro estiver aberto, e a staff não abre um segundo).

## O que todos têm em comum

O código comum fica em `base/` (`Evento`, `EventosListener` e `EventoCommand`); cada evento só define a arena, o kit e o que acontece durante a luta.

- **Inscrição grátis** (3 minutos, com lembretes), com o **inventário totalmente vazio** (mochila, barra, armadura, outra mão e cursor) e **uma por IP** (`eventos.ip.ignorar` pula a trava: só pros bots de teste). Quem está no mundo das arenas, ou já inscrito no outro evento, não entra.
- **Mínimo de 3** inscritos (`minimo-jogadores`); sem isso, o evento é cancelado. Com 1 (só no servidor de teste, pra testar sozinho), quem entra sozinho não vence na hora: joga até cair ou o tempo acabar. Em produção, 3 ou 4 (`docs/producao-vps.md`).
- **Preparação** de 10 s sem kit, depois a **luta** com o mesmo kit pra todos.
- **Ninguém perde vida**: todo dano em quem luta é cancelado.
- **Caiu na água**: perde o kit e vai pra **arquibancada** (sai com `/spawn`). **Desconectou**: perde o kit e volta pra onde estava. Se o servidor cair no meio, quem estava lutando volta pra onde estava ao entrar de novo (`<evento>-pendentes.yml`).
- **Fim**: o último de pé perde o kit, volta pra onde estava e recebe um **cheque de R$ 10.000** (ChequePlugin; sem ele, o valor cai na conta). Depois de 8 minutos de luta, quem estiver de pé divide. As vitórias ficam em `<evento>-vitorias.yml` (`/<evento> top`).
- **Comandos e teleporte**: lutando, só o comando do evento; na arena e na arquibancada, só ele e o `/spawn` (`comandos-liberados`). Nada de `/tpa`, `/sethome`, `/home`, `/back`... Nenhum teleporte de fora leva alguém pra dentro da arena ou da arquibancada: pra assistir, `/<evento> assistir`. Da arquibancada ninguém atira. Lutando, não se joga nem se pega item e não tem fome. A staff (`eventos.admin`) passa.
- **Anúncios** curtos no chat e no Discord (categoria `eventos`, no canal #eventos): quando começa, o prêmio e o comando. Como funciona fica no `/<evento>` e no tutorial de eventos do Discord (`discord-bot/conteudo/tutoriais/07-eventos.md`).

## Vietnã

Pilares de 2x2 sobre a água. Cada um nasce num pilar com o mesmo kit e tenta derrubar os outros. **Ninguém perde vida**: os golpes só empurram. Caiu na água, está fora e vai pra arquibancada. O último de pé leva um **cheque de R$ 10.000** (ChequePlugin; sem ele, o valor cai na conta).

### Como funciona

1. **Abertura.** Todo dia às 10:00 e às 16:00 (`vietna.agenda`; o Gladiador e o dragão ficam à noite), ou pela staff com `/vietna abrir`. O anúncio sai no chat e no Discord (categoria `eventos` do DiscordPlugin, no canal #eventos, junto com o Gladiador e o boss). No saguão das arenas, um holograma mostra o comando e os horários, e o Zé, o Guia, avisa no spawn.
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

Um prédio fechado de tema de selva, construído pelo plugin (`/vietna construir`, uma vez) no mundo `arenas`, em volta de (0, 600). A cada evento, só os pilares são erguidos de novo.
- 6 x 6 pilares de 2x2 de tronco da selva com topo de musgo, 3 blocos de vão (dá pra pular correndo) e 6 de altura acima da água (3 de fundo, com lanternas do mar no fundo);
- paredes de pedra com musgo e colunas de tronco, teto de madeira escura com claraboias e lanternas penduradas;
- a **galeria** de quem assiste em volta dos 4 lados, 5 blocos acima do topo dos pilares, separada da arena por um vidro até o teto (quem luta não chega lá nem com a carga de vento), com bancos e lanternas.

A caixa da piscina até o teto (de [-16, 60, 584] a [16, 81, 616]) está no AreasPlugin como "arena do vietnã": lá a carga de vento vale (`carga-de-vento-liberada`) e ninguém marca home. O `tools/bots/montar-mapa.js construir vietna` chama o `/vietna construir` e põe o holograma no saguão.

## Spleef

Uma torre redonda e fechada de tema de inverno no mundo `arenas`, em volta de (0, 900): três pisos de neve (raio 15) sobre a água, um a cada 6 blocos de altura, dentro de uma parede de gelo compactado com uma faixa de gelo azul e lanternas do mar em cada andar, teto de gelo com luzes e lanternas penduradas, e por fora pinheiro e neve. A galeria de quem assiste é um anel em volta, 5 blocos acima do piso de cima, atrás de um vidro até o teto. A torre é construída uma vez (`/spleef construir`); a cada evento, só a neve volta. Todo dia às **13:00** e às **18:00** (`spleef.agenda`).

- **Kit**: uma pá de diamante com Eficiência V (quebra a neve na hora), inquebrável. Sem bola de neve, e soco não faz nada (decisões de 2026-11-02).
- **Quebrar**: quem está lutando quebra só a neve dos pisos, e ela não cai como item. Quebrou debaixo dos pés, cai pro piso de baixo e continua; do último, cai na água e está fora.
- **O piso derrete**: depois de 2 minutos de luta, a cada 2 s, 10 blocos de neve viram gelo e somem 1 s depois, só nos andares em que ainda tem alguém de pé (`spleef.derreter`).
- Todos começam no piso de cima, em roda, olhando pro centro. A arena é construída de novo a cada evento.
- A caixa por dentro da parede (de [-16, 60, 884] a [16, 93, 916]) está no AreasPlugin como "arena do spleef": lá quebrar bloco vale (`quebrar-liberado`, e o EventosPlugin decide o que quebra) e ninguém marca home.

## Corre ou Cai

Um prédio quadrado e fechado de festa colorida no mundo `arenas`, em volta de (0, 1200): três pisos de concreto de 25 x 25 (verde-limão, azul-claro e magenta, de baixo pra cima) sobre a água, de parede a parede, um a cada 6 blocos de altura. Paredes de quartzo com faixas de vidro colorido e luzes em cada andar, teto com claraboias coloridas e lanternas penduradas, e a galeria em volta, 5 blocos acima do piso de cima, atrás de um vidro até o teto. Todo dia às **11:00** e às **17:00** (`correoucai.agenda`).

- **Sem kit**: só correr (decisões de 2026-11-02).
- **O chão some**: o bloco em que alguém pisa (os até 4 debaixo do pé) fica **vermelho** e some meio segundo depois (`chao.some-em-ticks: 10`), mesmo pra quem está parado: o plugin confere o chão de cada um a cada 2 ticks. Nos primeiros 3 segundos da luta, nada some (`chao.graca-segundos`). Quem cai de um piso vai pro de baixo; do último, cai na água e está fora.
- Todos começam no piso de cima, espalhados. A cada evento, só os pisos voltam.

## Sobrevivência

O único de todos juntos: com o mesmo kit de ferro, contra 10 ondas de invasores no pátio de um castelo sitiado, com chefe na 5 e na 10. Todo dia às **22:00** (`sobrevivencia.agenda`). Decisões de 2026-11-02.

- **Arena**: o pátio de 31 x 31 de um castelo fechado no mundo `arenas`, em volta de (0, 1500): chão de pedra com musgo e rachaduras, muralhas de tijolo de pedra com colunas de carvalho escuro, um portão com grade de ferro no meio de cada muralha (de onde os invasores saem), fardos de feno e pilhas de tronco pra se esconder, teto de carvalho escuro com claraboias e lanternas, ameias por fora e a galeria em cima das muralhas, atrás de vidro.
- **Kit**: armadura, espada e arco de ferro (inquebráveis), 32 flechas e 4 maçãs douradas. Quando uma onda cai, mais 16 flechas. Sem fome (a vida volta sozinha).
- **Ondas** (`ondas.lista`, pra 1 jogador; cada jogador a mais põe 50% a mais de mobs e de vida nos chefes, `ondas.a-mais-por-jogador`): saqueadores e vingadores, bruxas a partir da 3 e evocadores a partir da 7. Chefes (`ondas.chefes`): **Capitão Saqueador** (vingador com 120 de vida e 1,5x de dano) na 5 e **Devastador** (300 de vida) na 10. Os invasores saem dos 4 portões em rodízio e vão atrás do jogador mais perto; as vexes do evocador entram na onda. Eles não deixam cair nada nem experiência. Entre as ondas, 10 s de pausa (`ondas.pausa-segundos`), com aviso no chat de quem está na arena ou na galeria. O `/sobrevivencia` mostra a onda e quantos invasores faltam.
- **Regras**: os mobs machucam de verdade; um jogador não machuca outro (nem com flecha). **Morreu, está fora**: não deixa nada no chão (só o kit estava com ele) e renasce na galeria. Quem estiver vivo quando a última onda cair divide o cheque; se todos morrerem, ninguém ganha; depois de 20 minutos, quem estiver vivo divide.

## Corrida Maluca

Uma pista de obstáculos num pavilhão comprido e fechado de parque de diversões no mundo `arenas`, de (0, 1800) pro norte, uns 145 blocos: a pista tem 13 de largura sobre uma piscina, paredes de listras vermelhas e brancas com uma faixa amarela, pisos coloridos, bandeirinhas atravessando a pista, teto com claraboias e as passarelas de quem assiste dos dois lados, em cima das paredes, atrás de um vidro até o teto. Todo dia às **9:00** e às **14:00** (`corrida.agenda`). Decisões de 2026-11-02.

- **Sem kit**: só correr e pular. **Ninguém sai por cair**: quem cai na água volta pro último checkpoint (são 2, entre os trechos).
- **Prêmio por colocação** (`premios: [5000, 3000, 2000]`): os 3 primeiros a cruzar a chegada levam os cheques, na ordem (só o 1º conta vitória no `/corrida top`). Quem chega vai pra passarela. Acaba quando o 3º chega, quando todos chegam ou em 5 minutos (`luta.tempo-maximo-minutos`); o prêmio de quem não chegou não é pago. Quem sai do servidor antes do pagamento recebe na conta.
- **Trecho 1**: o portão de vidro da largada abre no "valendo"; duas fileiras de 3 portas iguais em que só uma abre (sorteada a cada corrida, `portas.abertas-por-fileira`: some pra todos quando alguém encosta; a falsa só faz barulho); o trampolim de slime, que joga por cima de um vão (`trampolim`); e a pista de gelo com buracos.
- **Trecho 2**: as plataformas que somem, em dois grupos em xadrez que se alternam (firme 3 s, vermelha 1 s, sumida 2 s: `plataformas`); e o tapete rolante estreito, com as setas pra trás, que empurra quem está em cima (`tapete`).
- **Trecho 3**: dois discos com barras giratórias na altura do pé (uma barra e depois uma cruz: `barras`), feitas de blocos de exibição; quem não pula leva um empurrão pra fora. Depois, a ponte estreita do vento, com 4 canhões nas paredes que soltam carga de vento revezando (`vento`); e a chegada quadriculada, com um arco.
- Todos os obstáculos são do plugin (sem redstone). A cada corrida, só o portão, as portas e as plataformas voltam.

## Quiz

O único sem arena: uma rodada de perguntas no chat, todo dia às **12:00** e às **15:00** (`quiz.agenda`). Sem inscrição e sem mínimo de jogadores: responde quem estiver online, de onde estiver, com **`/quiz <resposta>`**. Roda junto com os outros eventos. Decisões de 2026-11-02.

- **10 perguntas** (`perguntas-por-rodada`), **20 s** cada (`segundos-por-pergunta`) e 5 s entre elas. Ninguém acertou a tempo, a resposta aparece e vem a próxima.
- **Os tipos** são sorteados pelos pesos (`pesos`): sobre o **servidor** e sobre o **Minecraft** (as listas `servidor` e `minecraft` do config, com uma ou mais respostas aceitas), **contas** geradas na hora (soma, subtração e multiplicação) e **palavras embaralhadas** (`palavras`). Uma pergunta não se repete na mesma rodada.
- **A resposta** não liga pra acento, maiúscula, pontuação de número ("10.000" = "10000") nem pra "o"/"a"/"um" na frente. Errou, só quem errou vê "Não é isso"; acertou, todos veem quem acertou e a resposta. Entre uma tentativa e outra, 2 s (`espera-entre-tentativas-segundos`), contra chutar as contas no automático.
- **Prêmio**: cada pergunta vale 1 ponto pra quem acertar primeiro; quem fizer mais pontos leva o cheque de **R$ 5.000** (`premio`), dividido no empate (quem saiu antes do fim recebe na conta). Vitórias em `quiz-vitorias.yml` (`/quiz top`).
- Staff (`eventos.admin`): `/quiz abrir`, `/quiz cancelar` e `/quiz resposta` (a resposta da pergunta aberta).

## Ranking geral (/eventos top)

Os vencedores de todos os eventos juntos: as vitórias de cada evento deste plugin (os de arena e o Quiz) e do **Gladiador individual** (o por clã não conta), **1 por vitória**, desde sempre. Vitória é o que cada evento já conta no `/<evento> top`: o último de pé (ou quem divide no tempo esgotado), todo sobrevivente da Sobrevivência, só o 1º da Corrida e quem fez mais pontos no Quiz. Decisões de 2026-11-03.

- **`/eventos top`**: o top 10, a sua posição (mesmo fora do top) e os recordes (quem mais venceu cada evento).
- **`/eventos top <jogador>`**: as vitórias dele em cada evento e a posição no geral.
- **Holograma** no saguão das arenas, na parede sul (`ranking.holograma`): o top 10, atualizado a cada vitória e a cada minuto, quando tem alguém perto (é recriado sozinho depois de um reinício).
- Não guarda nada à parte: lê os `<evento>-vitorias.yml`, o `quiz-vitorias.yml` e, do GladiadorPlugin (por reflexão), as vitórias individuais.

## /eventos

A agenda de todos os eventos do servidor (o Quiz também): o que está rolando agora (com o comando pra entrar ou assistir), o próximo (dia, hora e quanto falta) e os horários de cada um. Os horários vêm do config de cada plugin, então mudou lá, muda aqui: Vietnã e Spleef (`<evento>.agenda`), Gladiador (`agenda` do GladiadorPlugin), Boss (`agenda` do BossPlugin, com o boss do dia, ex.: Wither no domingo), Dragão do End (`end.dragao.hora` do RecursosPlugin) e Mine-sena (`minesena.horario` do ChequePlugin). Plugin desligado, a linha some.

## Comandos

Iguais em todos os eventos (`/vietna ...`, `/spleef ...`, `/correoucai ...`, `/sobrevivencia ...` e `/corrida ...`):

| Comando | O que faz |
|---|---|
| `/<evento>` ou `/<evento> info` | Como funciona, a agenda e o que está acontecendo agora |
| `/<evento> entrar` / `/<evento> sair` | Inscrição (sair só antes de começar) |
| `/<evento> assistir` | Vai pra arquibancada |
| `/<evento> top` | Quem mais venceu |

Staff (`eventos.admin`): `abrir`, `iniciar` (fecha as inscrições e começa agora), `cancelar`, `construir` (refaz a arena e mostra a caixa pro AreasPlugin) e `reload` (recarrega o config de todos).

## Config

Uma seção por evento no `config.yml` (`vietna:`, `spleef:`, `correoucai:`, `sobrevivencia:` e `corrida:`), com as mesmas chaves comuns (agenda, inscrição, mínimo de jogadores, prêmio, tempos da luta, comandos liberados) e as de cada um: no Vietnã, os pilares que somem, o kit e o reabastecimento, a força dos empurrões e a arena; no Spleef, o derretimento, a Eficiência da pá e a arena (centro, raio, andares e distância entre eles); no Corre ou Cai, o tempo até o bloco sumir, a graça do começo e a arena; na Sobrevivência, o kit, as ondas, os chefes e a arena; na Corrida, os prêmios por colocação, as portas, o trampolim, o tapete, as plataformas, as barras, o vento e a arena. Mudou uma arena? Atualize a caixa dela no config do AreasPlugin com o que o `/<evento> construir` mostra.

## Teste

`node tools/bots/teste-vietna.js`, `node tools/bots/teste-spleef.js` e `node tools/bots/teste-correoucai.js`, `node tools/bots/teste-sobrevivencia.js`, `node tools/bots/teste-corrida.js` e `node tools/bots/teste-quiz.js`
