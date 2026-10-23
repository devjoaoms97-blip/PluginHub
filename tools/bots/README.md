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
node montar-arenas.js       # monta arenas simples de X1 e do Gladiador no mundo arenas
node teste-duelo.js         # X1 com aposta (precisa das arenas)
node teste-gladiador.js     # Gladiador individual e por clã (precisa das arenas)
```
Os testes de mina usam a mina `pedreira`. Para usar outra, passe o nome dela (`node teste-coleta.js cobre`). O bot precisa ter o rank da mina; um jogador novo só minera nas minas do primeiro rank.

| Script | O que confere |
|---|---|
| `teste-coleta.js` | O minerado vai pro inventário, sem itens nem orbes no chão. Com o inventário cheio, só a sobra cai, com o aviso "Inventário cheio" |
| `teste-duelo.js` | Desafio e aceite com apostas retidas, contagem sem dano, ninguém de fora bate, comandos bloqueados, morte com perda de itens e prêmio menos a taxa, volta para onde estava, ranking, recusa e cancelamento com devolução |
| `teste-gladiador.js` | Individual (inscrição, preparação sem dano, desconexão elimina, último de pé leva o pote) e por clã (sem clã não entra, sem dano no mesmo clã, prêmio dividido no clã vencedor), e cancelamento com devolução |
| `teste-terrenos.js` | Compra, borda da grade, proteção (vizinho, rua, amigo), imposto, confisco por imposto atrasado, leilão de imóvel (dívida do dono antigo, lance superado e devolvido, vencedor leva o que tem dentro) e leilão sem lances |
| `teste-vip.js` | Pix com 80% de desconto na taxa, missão diária extra, bônus de ativação sem o dinheiro por nível das skills, kit mensal encantado e com cooldown, venda automática e `/autovenda` |

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
