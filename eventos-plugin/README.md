# EventosPlugin

Eventos de arena do servidor: o **Vietnã** (`/vietna`) e o **Spleef** (`/spleef`). Só um fica aberto por vez (a agenda pula o horário se outro estiver aberto, e a staff não abre um segundo).

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

## /eventos

A agenda de todos os eventos do servidor: o que está rolando agora (com o comando pra entrar ou assistir), o próximo (dia, hora e quanto falta) e os horários de cada um. Os horários vêm do config de cada plugin, então mudou lá, muda aqui: Vietnã e Spleef (`<evento>.agenda`), Gladiador (`agenda` do GladiadorPlugin), Boss (`agenda` do BossPlugin, com o boss do dia, ex.: Wither no domingo), Dragão do End (`end.dragao.hora` do RecursosPlugin) e Mine-sena (`minesena.horario` do ChequePlugin). Plugin desligado, a linha some.

## Comandos

Iguais nos dois eventos (`/vietna ...` e `/spleef ...`):

| Comando | O que faz |
|---|---|
| `/<evento>` ou `/<evento> info` | Como funciona, a agenda e o que está acontecendo agora |
| `/<evento> entrar` / `/<evento> sair` | Inscrição (sair só antes de começar) |
| `/<evento> assistir` | Vai pra arquibancada |
| `/<evento> top` | Quem mais venceu |

Staff (`eventos.admin`): `abrir`, `iniciar` (fecha as inscrições e começa agora), `cancelar`, `construir` (refaz a arena e mostra a caixa pro AreasPlugin) e `reload` (recarrega o config dos dois).

## Config

Uma seção por evento no `config.yml` (`vietna:` e `spleef:`), com as mesmas chaves comuns (agenda, inscrição, mínimo de jogadores, prêmio, tempos da luta, comandos liberados) e as de cada um: no Vietnã, os pilares que somem, o kit e o reabastecimento, a força dos empurrões e a arena; no Spleef, o derretimento, a Eficiência da pá e a arena (centro, raio, andares e distância entre eles). Mudou uma arena? Atualize a caixa dela no config do AreasPlugin com o que o `/<evento> construir` mostra.

## Teste

`node tools/bots/teste-vietna.js` e `node tools/bots/teste-spleef.js`
