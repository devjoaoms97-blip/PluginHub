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
node teste-dicas.js         # NPCs de dicas: balão animado, próxima fala, some sozinho, não toma dano
node teste-duelo.js         # X1 com aposta (precisa das arenas)
node teste-gladiador.js     # Gladiador individual e por clã (precisa das arenas)
node teste-farm-arenas.js   # abate nas arenas não paga Bounty nem conta no placar do clã
node teste-combate.js       # combate 1.8: sem recarga, tempo por mão, bloqueio com a espada e duas mãos
node teste-skills-maos.js   # o golpe da segunda mão treina a skill da arma dela
node teste-recursos.js      # mundo de recursos: /recursos, borda e reset (uns 2 minutos)
node teste-rankup-extras.js # menus do /ranks e do /mina, holograma, rank na tab e prestígio
node teste-pix-agendado.js  # Pix agendado: primeiro pagamento, vencimento, falta de saldo e cancelar
node teste-discord.js       # ponte com o Discord (um servidor HTTP local faz o papel do Discord)
node teste-diaria.js        # recompensa diária e a sequência de dias
node teste-gladiador-borda.js # borda do Gladiador fechando (muda o config por alguns segundos)
```
### Mapa funcional (`montar-mapa.js`)

Monta o spawn (a estrutura `spring` do EstruturasPlugin, que já cadastra as barracas) e o resto do que faz o servidor funcionar (minas, arenas, hologramas e warps). Pode rodar de novo: refaz igual. Não precisa rodar a cada vez que o servidor liga: o mundo fica salvo. Só rode de novo pra refazer o mapa.

1. `node montar-mapa.js construir [partes]`, com o servidor **ligado** (partes: `minas`, `spawn`, `barracas`, `boss`, `dicas`, `arenas`, `marcar` e, só na troca do mapa antigo, `limpar-antigo`; sem partes, faz `minas spawn boss dicas arenas marcar`): limpa o spawn antigo (praça, mercado, loja e portais), constrói o spawn novo pelo console (`/estrutura construir spring spawn 0 65 50`), que cadastra as 32 barracas, e monta as minas, as arenas e os hologramas. Também define o spawn dos jogadores na fonte (`/setspawn`), os warps (`minas`, `mercado`, `arenas`, `terrenos`, `boss`), o local do boss e o `/spawn` e o `/warp` liberados pro grupo `default` no LuckPerms.
2. `node montar-mapa.js cadastrar`, com o servidor **desligado**: grava as 3 arenas de X1 e a do Gladiador, e ajusta os configs (proteção do mundo `spawn` no Rankup, saída do mundo de recursos e warp VIP).
3. Os 12 poços das minas ficam cheios de pedra e sem mina cadastrada: a staff cria cada uma com `/mina admin pos1`, `pos2` e `criar`. Com `cadastrar --minas`, as 12 já entram prontas (de `tools/economia/minas-planejadas.yml`) e se enchem ao ligar. Os testes de mina (`teste-coleta`, `teste-vip`, `teste-rankup-extras` e `teste-mapa`) precisam da `pedreira`.

`node montar-mapa.js marcar` refaz só o passo do bot (spawn, warps e boss), sem reconstruir os blocos.

| Onde | O que tem |
|---|---|
| `spawn`, spawn (centro em 0, 43; z -20..106) | Spawn fechado: o jogador nasce na fonte (0, 65, 43), com o painel "Como começar" (um holograma grande no fim da avenida oeste, sobre os arbustos) e 12 NPCs de dicas. 32 barracas pelas avenidas (28 normais e 4 especiais), com NPC de quem aluga. Só dois portões: "MINAS" ao norte e "BOSS" ao sul, colados nas vizinhas |
| `spawn`, sul (z 107..253) | Arena do boss (estrutura `toxica`), enterrada: escadaria fechada a partir do portão sul, piso em y 32, centro em (0, 33, 195). `/warp boss` leva pra dentro dela |
| `spawn`, norte (z -21..-185) | Avenida das Minas (estrutura `minas`): recinto fechado com os 12 poços de mina de 21 x 21 (15 de profundidade, cheios de pedra), em ordem de rank, alternando oeste e leste. O portão dela fica colado no caminho que sai do spawn |
| `arenas` | Saguão com portal de volta, X1 `axepvp` (centro -600, 0, cadastrada como `axe`) e Gladiador `coliseu` (centro 600, 0, com 16 pontos de entrada), longe um do outro. A plateia entra por `/warp plateia-x1` e `/warp plateia-gladiador` e sai com `/spawn` |

Os testes de mina usam a mina `pedreira`. Para usar outra, passe o nome dela (`node teste-coleta.js cobre`). O bot precisa ter o rank da mina; um jogador novo só minera nas minas do primeiro rank.

| Script | O que confere |
|---|---|
| `teste-mapa.js` | Jogador novo nasce na fonte do spawn, `/warp` e `/spawn`, painel "Como começar" (um só, sem hologramas espalhados), poços das minas cheios (e a pedreira minerável, se já foi criada), chão do spawn protegido, caminho do portão colado na avenida das minas, portal de volta do saguão das arenas, 32 barracas com holograma e o NPC de quem aluga, 4 arenas de X1 (3 simples e a axepvp) e a do Gladiador prontas |
| `teste-coleta.js` | Venda automática na mina pra quem não é VIP: o que se quebra vira dinheiro na hora, com o "+R$" na barra, sem nada no inventário nem no chão |
| `teste-combate.js` | Velocidade de ataque, dois golpes seguidos com o dano inteiro, tempo entre golpes (no padrão o 2º golpe em 0,15 s não conta; com 0 conta), bloqueio com a espada tirando metade do dano, sem bloqueio da espada com escudo na outra mão golpe com a espada da segunda mão, espada e machado alternados contando os dois golpes em 0,15 s, a mesma mão não, e soco com a segunda mão vazia; carregando a besta (ou puxando o arco) a espada da segunda mão não golpeia, logo depois de disparar também não, e meio segundo depois volta a golpear |
| `teste-rankup-extras.js` | Menu do `/ranks` (subir clicando), menu do `/mina` (ir clicando), holograma em cima da mina, rank na tab, prestígio só no último rank, volta pro primeiro com ✦ na tab e no chat |
| `teste-pix-agendado.js` | Primeiro pagamento na hora com a taxa, quem recebe vê o agendamento, o próximo sai sozinho com quem recebe offline, sem saldo avisa e tenta de novo, cancelar tira da lista |
| `teste-discord.js` | Teste por categoria com o nome certo e sem menções, abertura do Gladiador sem cores e leilão de terreno na categoria leilões |
| `teste-diaria.js` | Aviso ao entrar, dia 1 com o mínimo, sem repetir no mesmo dia, sequência avançando no dia seguinte e voltando pro 1 depois de perder um dia, e o painel |
| `teste-gladiador-borda.js` | Aviso da borda fechando, dano em quem ficou fora e o fim da luta pela borda |
| `teste-recursos.js` | `/recursos` em chão firme dentro do raio, borda, espera entre usos, reset marcado com aviso no chat, quem estava lá tirado antes, bloco do mundo antigo sumindo, e quem saiu do servidor lá dentro voltando num lugar seguro do mundo novo |
| `teste-skills-maos.js` | Machado na mão esquerda treina a skill Machado, soco na esquerda treina o Porradeiro, e a espada da direita (que não bateu) não treina |
| `teste-duelo.js` | Desafio e aceite com apostas retidas, espectador (modo espectador, sem comandos, volta no fim), contagem sem dano, ninguém de fora bate, comandos bloqueados, morte com perda de itens e prêmio menos a taxa, volta para onde estava, ranking, recusa e cancelamento com devolução |
| `teste-gladiador.js` | Individual (inscrição, preparação sem dano, desconexão elimina, último de pé leva o pote) e por clã (sem clã não entra, sem dano no mesmo clã, prêmio dividido no clã vencedor), e cancelamento com devolução. No fim, devolve os 16 pontos de entrada oficiais do coliseu |
| `teste-farm-arenas.js` | Num duelo entre clãs diferentes, com recompensa na cabeça de quem perde: o Bounty não paga (e a recompensa continua), e o placar de abates do clã não muda |
| `teste-terrenos.js` | Compra, borda da grade, holograma da entrada (à venda, dono, leilão), amigo só com baús, proteção (vizinho, rua, amigo), imposto, confisco por imposto atrasado, leilão de imóvel (dívida do dono antigo, lance superado e devolvido, vencedor leva o que tem dentro) e leilão sem lances |
| `teste-vip.js` | Pix com 80% de desconto na taxa, missão diária extra, bônus de ativação sem o dinheiro por nível das skills, kit mensal encantado e com cooldown, venda automática e `/autovenda` |
| `teste-novidades.js` | `/skills top`, XP em dobro pela staff, evento de mercado (anúncio, `/shop evento`, venda), chave Pix (cadastro, nome de outro jogador recusado, pagar pela chave), título ao receber, taxa no extrato, `/pix admin log`, cheque nominal e cooldown, `/vip setwarp <tier>`, preço mínimo do `/vip vender`, `/boss proximo` e ItemCleaner (item perto do jogador fica, orbe longe some) |
| `teste-oficinas.js` | As 4 oficinas da mina: Ferreiro repara e cobra, Picareteiro vende, Sábio vende XP, Bruxa abre a mesa no nível 30 com o lápis dela (encanta sem lápis, e o lápis não vai pro inventário), e as linhas da scoreboard lateral (`/placar ver`) |
| `teste-loja.js` | Loja Oficial: os 7 vendedores colocados, `/shop` e `/sell` fechados pros jogadores, `/warp loja`, vender e comprar no menu do fazendeiro, 12% de desconto do VIP Diamante a trava da venda (último rank + VIP: o diamante não passa de 90% do menor preço de compra) e o Armeiro (balcão PvP) vendendo a Poção de Força II |
| `teste-spawners.js` | Spawners: o Domador da loja vende, no spawn não entra, no terreno entra, empilha clicando (x2), a pilha de mobs aparece em cima (x2), matar tira um (x1) e derruba o drop, e quebrar devolve os spawners |
| `teste-maquinas.js` | Máquinas: o pacote de texturas é servido, o Maquinista vende o Tear e as Células, a máquina entra no terreno desenhada pelo modelo do pacote, abastece, faz 30 Tecidos em 30 minutos (`/maquinas acelerar`), coleta, vende no balcão e recolhe |
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
