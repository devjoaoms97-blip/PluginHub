# Bots de teste

Bots ([Mineflayer](https://github.com/PrismarineJS/mineflayer)) que entram no servidor de teste e testam os plugins de dentro do jogo. O bot minera, usa comandos e lê o chat e a barra acima do inventário. As tarefas de staff (dar VIP, conferir saldo, ver encantamentos) vão pelo console, via RCON. Cada verificação aparece como ✔ ou ✘, e o script sai com erro se alguma falhar.

## Preparação (uma vez)

1. Monte o servidor de teste (`test-server/montar.bat`), com `online-mode=false` e o RCON ligado, como o `montar.ps1` já deixa.
2. Instale o **ViaVersion** e o **ViaBackwards** na pasta `plugins` do servidor de teste (baixe no [Hangar](https://hangar.papermc.io/ViaVersion)). O Mineflayer costuma demorar a suportar a versão mais nova do Minecraft, e com eles o bot entra por uma versão anterior (`26.1`, que pode ser trocada em `MC_VERSAO_BOT`). O cliente normal continua entrando como sempre.
3. Instale as dependências, com o Node.js 22 ou mais novo:
   ```
   cd tools/bots
   npm ci
   ```

A pasta do servidor é `C:\mc-teste`; para outra, defina `MC_TESTE`. A porta e a senha do RCON são lidas do `server.properties` dela.

## Uso

Ligue o servidor. Pode ser pelo `iniciar.bat` ou, para deixar sem console, com o `servidor.py`:
```
python servidor.py          # cria PRONTO quando terminar de ligar; para com o arquivo PARAR
```
Depois, em outro terminal:
```
node ping.js                # o bot entra e fala no chat: confere a conexão
node teste-coleta.js        # coleta direta na mina, sem VIP, e inventário cheio
node teste-vip.js           # benefícios do VIP Diamante
node teste-terrenos.js      # terrenos e leilão de imóvel (precisa do mundo terrenos)
node montar-mapa.js construir # monta o mapa funcional (servidor ligado; veja abaixo)
node teste-mapa.js          # o mapa: chegada na fonte, /spawn e warps, minas, proteção, portões colados e cadastros
node teste-warp-mobs.js     # warp mobs: as estátuas geram mobs, limite, nada sai do salão e a reposição
node teste-pvp-ciclo.js     # PvP em ciclos na warp mobs (salão sim, vestíbulo não), painel, boss sempre e spawn nunca
node teste-dicas.js         # NPCs de dicas: balão animado, próxima fala, some sozinho, não toma dano
node teste-duelo.js         # X1 com aposta (precisa das arenas)
node teste-gladiador.js     # Gladiador individual e por clã (precisa das arenas)
node teste-vietna.js        # evento Vietnã: inscrição, pilares, kit, regras, eliminação, cheque e pilares sumindo
node teste-spleef.js        # evento Spleef: um evento por vez, pisos, pá, quebrar e cair de andar, eliminação, cheque e piso derretendo
node teste-correoucai.js    # evento Corre ou Cai: sem kit, graça do começo, o chão fica vermelho e some, cair de andar, eliminação e cheque
node teste-sobrevivencia.js # evento Sobrevivência: kit de ferro, sem fogo amigo, ondas, mobs machucam, morte, encostar na grade, 10 ondas e o prêmio dividido
node teste-saguao.js       # Saguão dos eventos: portais, painel, inscrição pelo portal e pelo [ENTRAR], a espera, sair cancela, volta pro spawn
node teste-labirinto.js    # Labirinto do Fauno: borda, escuro, baús, barra do tempo, PvP, o Fauno (com o modelo), morte, vencedor e o tempo sem vencedor
node teste-corrida.js       # Corrida Maluca (3 fases, 5 mapas): contagem regressiva, barra do tempo, portão, portas, checkpoint, cair volta, trampolim, pula-pula, barra, martelo, carrossel, parkour, chegada (e a arquibancada dela), os 3 prêmios e a largada de cada mapa
node teste-ranking.js       # ranking geral: vitórias na Corrida e no Vietnã, /eventos top (ordem, posição, por jogador, recordes) e o holograma
node teste-quiz.js          # Quiz: perguntas no chat, resposta errada, espera entre tentativas, acento e maiúscula, sem acerto e o vencedor
node teste-farm-arenas.js   # abate nas arenas não paga Bounty nem conta no placar do clã
node teste-combate.js       # combate 1.8: sem recarga, tempo por mão, bloqueio com a espada e duas mãos
node teste-skills-maos.js   # o golpe da segunda mão treina a skill da arma dela
node teste-profissoes.js    # Minerador (3x3), Lenhador, Agricultor e Pescador (põe chances de 100% e volta o config)
node teste-recursos.js      # mundo de recursos: /warp recursos, borda e reset (uns 2 minutos)
node teste-nether-end.js    # /warp nether e /warp end, /mapa (com os outros jogadores), /luz, centro do End (proteção, PvP, gaiolas, dragão, ovo) e reset do Nether (uns 3 minutos)
node teste-rankup-extras.js # menus do /ranks e do /mina, holograma, rank na tab e prestígio
node teste-pix-agendado.js  # Pix agendado: primeiro pagamento, vencimento, falta de saldo e cancelar
node teste-discord.js       # ponte com o Discord (um servidor HTTP local faz o papel do Discord)
node teste-vinculo.js       # vínculo com o Discord pela API local (o teste faz o papel do bot)
node teste-diaria.js        # recompensa diária e a sequência de dias
node teste-gladiador-borda.js # borda do Gladiador fechando (muda o config por alguns segundos)
node teste-boss-maldicao.js # boss que escapa amaldiçoa (XP -10%) e o próximo morto quebra (o teste põe o limite de 2 min no config e devolve os 60 no fim)
node teste-boss-vip.js      # só o campeão do Wither ganha o código de VIP de evento de 7 dias; o boss normal não
node teste-vip-evento.js    # código de VIP de evento: só 3/7/15 dias, sem bônus nem kit mensal, não vende, quem tem VIP resgata depois
node teste-creditos.js      # loja de créditos: créditos pelo console, cosmético com confirmação, cor na tab, VIP e código, apoiador
node teste-montaria.js      # montaria do VIP (velocidade, some ao descer, não entra na arena do boss nem na warp mobs da linha vermelha pra dentro) e PvP só na arena do boss
node teste-sethome-arenas.js # /sethome bloqueado dentro das arenas e câmaras do tesouro, liberado logo fora dos limites
node teste-inventario-cheio.js # inventário cheio: Maquinista, Domador e Fazendeiro recusam a compra sem cobrar nem jogar no chão
node teste-ajuda.js         # /tutorial (etapas e prêmio uma vez), /comandos e /tpa com clique
node teste-creeper.js       # creepers da pilha de spawner e da /warp mobs não explodem (o comum, de controle, explode)
node teste-tesouros.js      # mapas do tesouro: /warp tesouros e o templo, cavar no X, câmara, convidado, ondas, baú do dono, spawner/máquina/células no loot, sair, morte, mapa do boss e da mina (precisa do mundo tesouros)
node teste-foco.js          # Bancário e cheque, Cambista, Compactador, achados, /tpa nas arenas, prestígio, parry e Boss elite
node teste-toque-suave.js   # spawner natural com Toque Suave e os tipos novos da loja de spawners
node teste-nomes-limpeza.js # tags no nome acima da cabeça e limpeza de item em chunk descarregado (minas)
node teste-chat.js          # chat local e global (/g), espiar da staff e anti-flood (intervalo, repetida, rajada, /pix receber, /loja anunciar da staff)
node teste-loja-mercado.js  # /loja das barracas, /pix qrcode só pra um jogador e mercado das máquinas por jogador
```
### Mapa funcional (`montar-mapa.js`)

Monta o spawn (a estrutura `spring` do EstruturasPlugin, que já cadastra as barracas) e o resto do que faz o servidor funcionar (minas, arenas, hologramas e warps). Pode rodar de novo: refaz igual. Não precisa rodar a cada vez que o servidor liga: o mundo fica salvo. Só rode de novo pra refazer o mapa.

1. `node montar-mapa.js construir [partes]`, com o servidor **ligado** (partes: `minas`, `spawn`, `mobs`, `barracas`, `boss`, `dicas`, `arenas` (com o `vietna`, o `spleef`, o `correoucai`, a `sobrevivencia`, a `corrida`, o `labirinto` e o `saguao`), `vietna`, `spleef`, `correoucai`, `sobrevivencia`, `corrida` e `labirinto` (só a arena do evento), `saguao` (o saguão dos eventos: portais, painel da agenda e ranking), `marcar` e, só na troca do mapa antigo, `limpar-antigo`; sem partes, faz `minas spawn boss dicas arenas marcar`): limpa o spawn antigo (praça, mercado, loja e portais), constrói o spawn novo pelo console (`/estrutura construir spring spawn 0 65 50`), que cadastra as 32 barracas, e monta as minas, as arenas e os hologramas. Também define o spawn dos jogadores na fonte (`/setspawn`), os warps (`minas`, `mercado`, `arenas`, `eventos`, `terrenos`, `boss`), o local do boss e o `/spawn` e o `/warp` liberados pro grupo `default` no LuckPerms.
2. `node montar-mapa.js cadastrar`, com o servidor **desligado**: grava as 3 arenas de X1 e a do Gladiador, e ajusta os configs (proteção do mundo `spawn` no Rankup, saída do mundo de recursos e warp VIP).
3. Os 12 poços das minas ficam cheios de pedra e sem mina cadastrada: a staff cria cada uma com `/mina admin pos1`, `pos2` e `criar`. Com `cadastrar --minas`, as 12 já entram prontas (de `tools/economia/minas-planejadas.yml`) e se enchem ao ligar. Os testes de mina (`teste-coleta`, `teste-vip`, `teste-rankup-extras` e `teste-mapa`) precisam da `pedreira`.

`node montar-mapa.js marcar` refaz só o passo do bot (spawn, warps e boss), sem reconstruir os blocos.

| Onde | O que tem |
|---|---|
| `spawn`, spawn (centro em 0, 43; z -20..106) | Spawn fechado: o jogador nasce na fonte (0, 65, 43), com 13 NPCs de dicas. 32 barracas pelas avenidas (28 normais e 4 especiais), com NPC de quem aluga. Só dois portões: "MINAS" ao norte e "BOSS" ao sul, colados nas vizinhas |
| `spawn`, sul (z 107..253) | Arena do boss (estrutura `toxica`), enterrada: escadaria fechada a partir do portão sul, piso em y 32, centro em (0, 33, 195). `/warp boss` leva pra dentro dela |
| `spawn`, norte (z -21..-185) | Avenida das Minas (estrutura `minas`): recinto fechado com os 12 poços de mina de 21 x 21 (15 de profundidade, cheios de pedra), em ordem de rank, alternando oeste e leste. O portão dela fica colado no caminho que sai do spawn |
| `arenas` | Saguão com portal de volta, X1 `axepvp` (centro -600, 0, cadastrada como `axe`) e Gladiador `coliseu` (centro 600, 0, com 16 pontos de entrada), longe um do outro. A plateia entra por `/warp plateia-x1` e `/warp plateia-gladiador` e sai com `/spawn` |

Os testes de mina usam a mina `pedreira`. Para usar outra, passe o nome dela (`node teste-coleta.js cobre`). O bot precisa ter o rank da mina; um jogador novo só minera nas minas do primeiro rank.

| Script | O que confere |
|---|---|
| `teste-mapa.js` | Jogador novo nasce na fonte do spawn, `/warp` e `/spawn`, sem o painel "Como começar" nem hologramas espalhados, poços das minas cheios (e a pedreira minerável, se já foi criada), chão do spawn protegido, caminho do portão colado na avenida das minas, portal de volta do saguão das arenas, 32 barracas com holograma e o NPC de quem aluga, 4 arenas de X1 (3 simples e a axepvp) e a do Gladiador prontas |
| `teste-coleta.js` | Venda automática na mina pra quem não é VIP: o que se quebra vira dinheiro na hora, com o "+R$" na barra, sem nada no inventário nem no chão |
| `teste-combate.js` | Velocidade de ataque, dois golpes seguidos com o dano inteiro, tempo entre golpes (no padrão o 2º golpe em 0,15 s não conta; com 0 conta), bloqueio com a espada tirando metade do dano, sem bloqueio da espada com escudo na outra mão golpe com a espada da segunda mão, espada e machado alternados contando os dois golpes em 0,15 s, a mesma mão não, e soco com a segunda mão vazia; carregando a besta (ou puxando o arco) a espada da segunda mão não golpeia, logo depois de disparar também não, e meio segundo depois volta a golpear |
| `teste-rankup-extras.js` | Menu do `/ranks` (subir clicando), menu do `/mina` (ir clicando), holograma em cima da mina, rank na tab, prestígio só no último rank, volta pro primeiro com ✦ na tab e no chat |
| `teste-pix-agendado.js` | Primeiro pagamento na hora com a taxa, quem recebe vê o agendamento, o próximo sai sozinho com quem recebe offline, sem saldo avisa e tenta de novo, cancelar tira da lista |
| `teste-discord.js` | Teste por categoria com o nome certo, embed e sem @everyone, abertura do Gladiador sem cores e leilão de terreno na categoria leilões |
| `teste-vinculo.js` | API recusa sem token, /vincular dá o código, código errado e reusado recusados, vínculo com 5 bilhetes grátis da Mine-sena só na primeira vez, conta já vinculada não gera código, desvincular pelo bot e pela staff |
| `teste-diaria.js` | Aviso ao entrar, dia 1 com o mínimo, sem repetir no mesmo dia, sequência avançando no dia seguinte e voltando pro 1 depois de perder um dia, e o painel |
| `teste-gladiador-borda.js` | Aviso da borda fechando, dano em quem ficou fora e o fim da luta pela borda |
| `teste-nether-end.js` | `/warp nether` em chão firme abaixo do teto e `/warp end` no quadrado verde do centro (com o holograma do PvP), `/luz` (liga, volta depois de morrer, desliga), bordas de 3.000 e 5.000, `/mapa` (um por mundo, com o terreno, que o reset apaga, e o outro bot como marca azul), centro do End sem quebrar, PvP barrado no quadrado e valendo fora dele, gaiolas dos cristais removidas, dragão renascendo pela staff, ovo que não teleporta nem quebra, reset do Nether e resgate de quem saiu lá dentro |
| `teste-recursos.js` | `/warp recursos` em chão firme dentro do raio, borda, espera entre usos, reset marcado com aviso no chat, quem estava lá tirado antes, bloco do mundo antigo sumindo, e quem saiu do servidor lá dentro voltando num lugar seguro do mundo novo |
| `teste-skills-maos.js` | Machado na mão esquerda treina a skill Machado, soco na esquerda treina o Porradeiro, e a espada da direita (que não bateu) não treina |
| `teste-profissoes.js` | Com chances de 100% no config (e o original de volta no fim): o 3x3 do Minerador quebra os vizinhos, vende tudo e só os blocos à mão dão XP; o Lenhador derruba a árvore natural inteira (com a tora a mais) e não a de folhas colocadas; colheita dupla do trigo; a pesca dá 8 XP, mapa do tesouro e Fragmento Arcano; e a venda no Fazendeiro mostra o bônus do Agricultor (+20% no 50) |
| `teste-duelo.js` | Desafio e aceite com apostas retidas, espectador (modo espectador, sem comandos, volta no fim), contagem sem dano, ninguém de fora bate, comandos bloqueados, morte com perda de itens e prêmio menos a taxa, volta para onde estava, ranking, recusa e cancelamento com devolução |
| `teste-quiz.js` | O `/eventos` mostra o Quiz às 12h e 15h; sem rodada, o `/quiz` não aceita resposta; a pergunta sai no chat; resposta errada ("Não é isso"), tentar de novo rápido demais ("Calma"), resposta certa com acento e maiúscula trocados; pergunta sem acerto mostra a resposta; e quem fez mais pontos leva os R$ 5.000 (o tempo das perguntas é encurtado no config e o original volta no fim; a resposta vem do `/quiz resposta` da staff) |
| `teste-ranking.js` | Ranking geral de vencedores: antes de vencer, "você ainda não venceu"; o B vence uma Corrida (o A em 2º não conta) e o A vence dois Vietnãs; o `/eventos top` mostra a posição de cada um (o A na frente, com 2 vitórias) e os recordes de cada evento (o Gladiador também); o `/eventos top <jogador>` mostra as vitórias por evento; e o holograma do saguão dos eventos aparece com o ranking (em até 1 minuto, com alguém perto) |
| `teste-corrida.js` | O `/eventos` mostra a Corrida às 9h e 14h e o `/corrida` os 3 prêmios; os 3 na largada, sem kit, com o portão fechado na preparação (e as 19 peças que se mexem lá: barras, martelos e os raios do carrossel), a contagem regressiva na tela (5, 4, 3, 2, 1 e "JÁ!", com som) e o portão aberto no "JÁ!"; de 3 portas só uma abre ao encostar, e a falsa avisa; o slime joga por cima do vão; o checkpoint avisa ("1 de 6"), e quem cai na água volta pra ele sem sair; as plataformas somem e voltam (as da fase 2 também); o tapete empurra pra trás; o pula-pula joga de um slime pro outro até o outro lado; a barra empurra quem está parado; o martelo derruba da ponte; o carrossel leva junto; o parkour está lá; os canhões soltam vento; o mapa é anunciado e a barra no alto mostra o tempo que falta; quem cruza a chegada vai pra arquibancada dela; a chegada em ordem fecha o pódio, paga os 3 cheques e devolve cada um; a pista volta inteira; e cada um dos outros 4 mapas tem a largada no seu lugar, com o nome anunciado. O teste prende o mapa no parque (`mapa-fixo`, e devolve o config no fim) e constrói os 5 mapas se faltar algum. Os bots vão de obstáculo em obstáculo por teleporte (com `eventos.admin`); constrói a pista se não houver |
| `teste-saguao.js` | O saguão dos eventos: um portal por evento, na cor dele, e o de volta; os textos em cima dos portais e o painel da agenda de hoje (na ordem do horário); o portal com o evento fechado diz quando abre; o anúncio da abertura tem o botão [ENTRAR]; quem se inscreve de fora vem pro saguão, e pelo portal fica nele; a barra do alto mostra quanto falta e quantos inscritos; o `/corrida sair` devolve pra onde estava; sair do saguão cancela a inscrição; no cancelamento, quem veio de fora volta; com a luta rolando, o portal leva pra arquibancada; no fim, cada um volta pra onde estava; e o portal da frente volta pro spawn. Constrói o saguão antes |
| `teste-labirinto.js` | O `/eventos` mostra o Labirinto às 8h e 23h e o `/labirinto` explica que, no tempo, ninguém vence; os 3 nascem na borda, sem nada, e quem assiste fica em cima do teto; lá dentro a luz é 0 (nem do céu); os baús estão lá, com itens; a barra no alto mostra o tempo e os vivos; o PvP vale entre quem luta; o Fauno acorda, com o modelo junto, e mata quem está perto ("foi pego pelo Fauno"); quem morre vai pro teto sem deixar nada; quem mata aparece no anúncio; o último vivo leva R$ 10.000, perde o que achou e volta; o Fauno some no fim. Na 2ª rodada, o labirinto é outro e, no tempo, com 2 vivos, ninguém vence (sem cheque, sem o que achou). O teste encurta o sono do Fauno e o tempo da 2ª rodada (e devolve o config) e constrói o labirinto antes |
| `teste-sobrevivencia.js` | O `/eventos` mostra às 22h; os 3 no pátio com o kit de ferro; sem fogo amigo; a onda 1 chega com 50% a mais de invasores por jogador a mais e eles machucam de verdade; vencida a onda, +16 flechas; encostado na grade de um portão, o bot continua no evento; quem morre está fora, renasce na galeria sem o kit e nada cai no chão; as 10 ondas (os invasores morrem pelo console, com a pausa encurtada no config e o original de volta no fim), com chefe na 5 e na 10; e quem chegou vivo divide o cheque |
| `teste-correoucai.js` | Os 3 no piso de cima e sem kit nenhum na luta; na graça do começo o chão não some; depois, o bloco debaixo de quem está parado fica vermelho e some, e ele cai pro piso de baixo sem sair; do último piso, cai na água e vai pra galeria; o último de pé leva o cheque e volta; a arena volta inteira; e o `/eventos` mostra o Corre ou Cai na agenda |
| `teste-spleef.js` | Com o Spleef aberto, o Vietnã não abre; com um item no inventário não entra; os 3 no piso de cima, sem a pá na preparação e com ela na luta; `/spawn` barrado lutando e `/sethome` na arquibancada; quebrar a neve debaixo dos pés (sem drop) faz cair pro piso do meio sem sair; cair do último piso na água elimina e leva pra arquibancada; desconectar elimina; o último de pé perde a pá, ganha o cheque e volta; a arena se refaz; e, num segundo evento com o tempo encurtado no config (e o original de volta no fim), a neve derrete |
| `teste-vietna.js` | Inscrição só com o inventário vazio (nem armadura) e uma por IP, os 3 nos pilares sem kit na preparação e com o kit na luta, golpe sem dano, `/spawn` e `/tpa` barrados lutando, teleporte de fora pra arena barrado, `/vietna assistir` e `/sethome`/`/tpa` barrados na arquibancada, cair na água elimina (arquibancada, sem kit), desconectar elimina, o último de pé leva o cheque e volta, a arena se refaz, `/spawn` da arquibancada e, num segundo evento com o tempo encurtado no config (e o original de volta no fim), os pilares sumindo |
| `teste-gladiador.js` | Individual (inscrição, preparação sem dano, desconexão elimina, último de pé leva o pote) e por clã (sem clã não entra, sem dano no mesmo clã, prêmio dividido no clã vencedor), e cancelamento com devolução. No fim, devolve os 16 pontos de entrada oficiais do coliseu |
| `teste-farm-arenas.js` | Num duelo entre clãs diferentes, com recompensa na cabeça de quem perde: o Bounty não paga (e a recompensa continua), e o placar de abates do clã não muda |
| `teste-terrenos.js` | Compra, borda da grade, holograma da entrada (à venda, dono, leilão), amigo só com baús, proteção (vizinho, rua, amigo), imposto, confisco por imposto atrasado, leilão de imóvel (dívida do dono antigo, lance superado e devolvido, vencedor leva o que tem dentro) e leilão sem lances |
| `teste-toque-suave.js` | No mundo de recursos: spawner natural de zumbi quebrado com Toque Suave vira o Spawner de Zumbi da loja, sem Toque Suave não dá nada, o de traça não sai; e os 8 tipos novos existem na loja |
| `teste-nomes-limpeza.js` | Um bot vê o rank no nome acima da cabeça do outro (time do PlacarPlugin com o prefixo da tab) e um item na mina do fim da avenida, com o chunk descarregado na hora da limpeza, sai quando o chunk carrega |
| `teste-chat.js` | Canais do ChatPlugin (o local só no mesmo mundo, com [L]; o `/g` em outro mundo, com [G]; a staff vê o local dos outros mundos; quem fala sozinho é lembrado do `/g`) e o anti-flood com um op: segunda mensagem em menos de 2 s, a mesma mensagem com maiúsculas e pontuação diferentes, o `/msg` contando junto, a rajada que silencia, o intervalo do `/pix receber` e o do `/loja anunciar` valendo pra staff. Os outros testes não passam pelo anti-flood (o `criarBot` dá `chat.antiflood.ignorar`) |
| `teste-loja-mercado.js` | `/loja` (lista as barracas alugadas com os itens, leva à frente do vendedor, o anúncio chega a todos com [VISITAR] e espera 10 min, sem aluguel o destino some), `/pix qrcode` (o público saiu; o direcionado chega com [PAGAR] e clicar duas vezes paga uma) e o mercado das máquinas (vender 1.600 tecidos não passa do mínimo, que paga o combustível, e só baixa o preço de quem vendeu) |
| `teste-foco.js` | Bancário (folha de cheque, cheque só com a folha e saindo do saldo, clique direito manda ao Bancário, depósito com 1% de taxa, cancelar devolve), Cambista (só os encantamentos que servem no item, Eficiência VI por 15 fragmentos, Saque a partir do IV na espada), Compactador, guia da pedreira, achados e perdidos (jogado, limpo, recuperado por R$ 10.000), `/tpa` barrado nas arenas, prestígio de skill com a skill da mão no placar, parry com escudo (no mundo de recursos, que tem PvP) e Boss elite. Precisa do `montar-mapa.js construir oficinas banco` |
| `teste-vip.js` | Pix com 80% de desconto na taxa, missão diária extra, bônus de ativação sem o dinheiro por nível das skills, kit mensal encantado e com cooldown, venda automática e `/autovenda` |
| `teste-novidades.js` | `/skills top`, XP em dobro pela staff, evento de mercado (anúncio, `/shop evento`, venda), chave Pix (cadastro, nome de outro jogador recusado, pagar pela chave), título ao receber, taxa no extrato, `/pix admin log`, cheque nominal e cooldown, `/vip setwarp <tier>`, preço mínimo do `/vip vender`, `/boss proximo` e ItemCleaner (item perto do jogador fica, orbe longe some) |
| `teste-oficinas.js` | As 4 oficinas da mina: Ferreiro repara e cobra, Picareteiro vende, Sábio vende XP, Bruxa abre a mesa no nível 30 com o lápis dela (encanta sem lápis, e o lápis não vai pro inventário), e as linhas da scoreboard lateral (`/placar ver`) |
| `teste-loja.js` | Loja Oficial: os 7 vendedores colocados, o Alquimista com todos os ingredientes de poção (extras e frasco), `/shop` fechado pros jogadores e `/sell` fora do servidor, `/warp loja`, vender e comprar no menu do fazendeiro, 12% de desconto do VIP Diamante a trava da venda (último rank + VIP: o diamante não passa de 90% do menor preço de compra) e o Armeiro (balcão PvP) vendendo a Poção de Força II |
| `teste-warp-mobs.js` | Warp mobs: com o jogador no salão, as estátuas perto dele ganham mobs (as do fundo só com ele lá), nenhum mob no vestíbulo nem no túnel, o zumbi não pega fogo no sol, não some de longe, morto a estátua repõe, e ninguém passa de 4 vivos |
| `teste-spawners.js` | Spawners: o Domador da loja vende, no spawn não entra, no terreno entra, empilha clicando (x2), a pilha de mobs aparece em cima (x2), matar tira um (x1) e derruba o drop, e quebrar devolve os spawners; 100 spawners por terreno somando os tipos (x64 de zumbi + x36 de esqueleto), sem empilhar nem colocar mais |
| `teste-maquinas.js` | Máquinas: o pacote de texturas é servido, o Maquinista vende o Tear e as Células, a máquina entra no terreno desenhada pelo modelo do pacote, abastece, faz 30 Tecidos em 30 minutos (`/maquinas acelerar`), coleta, vende no balcão e recolhe; abaixo do rank, a máquina não entra e a que está no chão fica bloqueada; 10 máquinas por terreno (5 teares e 5 forjas), a 11ª barrada |
| `teste-inventario-cheio.js` | Inventário cheio nas lojas: o Maquinista (máquina e combustível), o Domador (spawner) e o Fazendeiro da Loja Oficial recusam a compra com aviso, sem cobrar e sem jogar nada no chão; com um espaço livre, compram |
| `teste-ajuda.js` | AjudaPlugin: convite do `/tutorial` ao entrar, `/comandos` (índice, página, apelido e tema inexistente), o tutorial inteiro (sem pular etapa, 64 blocos na mina, `/warp loja`, `/ranks`, `/terreno auto`, `/diaria`), o mapa guardado com o inventário cheio e entregue uma vez só, e os botões do `/tpa` |
| `teste-creeper.js` | Creepers marcados como da pilha de spawner e da `/warp mobs` ficam do lado do jogador sem explodir; o creeper comum, de controle, explode |
| `teste-areas.js` | Áreas fechadas: no spawn a pérola não sai nem é gasta e o `/fly` não liga; num terreno (fora das áreas) a pérola funciona |

O `teste-vip.js` e o `teste-terrenos.js` usam jogadores novos a cada execução (`Vip`, `Ter` ou `Viz` + um código): o kit mensal e o bônus de ativação só valem uma vez por jogador, e cada um só compra um terreno. Esses jogadores ficam salvos no servidor e entram na contagem de jogadores do painel da economia. O `BotDois` só existe para receber o Pix.

## Escrevendo testes

O `lib.js` tem o necessário:
- `criarBot(nome)` conecta e espera o bot aparecer no mundo;
- `console(cmd)` roda um comando no console e devolve a resposta sem cores;
- `capturar(bot, fn)` devolve o chat e a barra recebidos durante `fn`;
- `mina(nome)`, `irParaMina` e `minerar(bot, mina, n)` cuidam das minas do RankupPlugin;
- `inventario` e `itensNoChao` mostram o que o bot tem e o que caiu;
- `lutar(bot, alvo)` anda até o outro e bate até ele morrer;
- `verificar(condicao, descricao)` e `terminar(...bots)` registram e encerram.

Dicas:
- Use `minecraft:give`, `minecraft:clear` e `minecraft:xp` no console. As versões da Essentials se comportam diferente; o `give` dela, por exemplo, dá 64 de qualquer item.
- Nessa versão, o Mineflayer não lê os encantamentos dos itens. Para conferir, use o `minecraft:data get entity` no console, como o `teste-vip.js` faz.
- Para PvP, use o `lutar` em vez do `minecraft:damage ... by <jogador>`: nos testes, esse comando às vezes dizia "Applied" sem tirar vida.
- Use `minecraft:tp` para levar os bots: o `tp` da Essentials recusa lugares que acha perigosos ("Há um buraco no chão!").
- No 26.2, o `setworldspawn` vale para o servidor todo, não só para o mundo em que roda. Rodar ele em outro mundo muda onde os jogadores novos entram.
