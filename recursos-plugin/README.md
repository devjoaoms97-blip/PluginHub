# RecursosPlugin

Mundos de recursos: o **normal** (minérios, árvores, mobs, pesca), o **Nether** e o **End**, todos com PvP. Cada um tem uma warp que leva até ele e é **resetado uma vez por mês**, num dia diferente, pra não crescer sem fim. No reset, tudo o que estiver no mundo some, então quem quer guardar algo leva pro terreno.

| Mundo | Warp | Chegada | Borda | Reset |
|---|---|---|---|---|
| `recursos` | `/warp recursos` | ponto aleatório seguro até 2.500 blocos do centro | 6.000 | dia 30 |
| `recursos_nether` | `/warp nether` | ponto aleatório seguro até 1.400 blocos do centro, abaixo do teto de bedrock | 3.000 | dia 20 |
| `recursos_end` | `/warp end` | o quadrado verde do centro do End | 5.000 | dia 10 |

O Nether e o End padrão do jogo ficam desligados (`allow-nether=false` no `server.properties` e `allow-end: false` no `bukkit.yml`): **só se entra pela warp**. Nos três mundos, os portais não funcionam (nem pra jogador, nem pra mob ou item), e o portal de saída do End, depois do dragão, leva pro spawn.

## Como funciona

- **Teleporte:** o ponto precisa ter chão firme e dois blocos livres em cima, sem água, lava, cacto, fogo, magma ou neve fofa. No Nether, o chão é procurado de baixo pra cima, entre as alturas 32 e 115. Os chunks carregam sem travar o servidor. Há 30 s de espera entre um teleporte e outro, contando as três warps juntas.
- **As warps:** o `/warp` é do Essentials, mas `recursos`, `nether` e `end` são do plugin, porque o ponto é sorteado a cada uso. O plugin pega o `/warp <nome>` depois de quem barra teleporte (combate, duelo, Gladiador): quem não pode se teleportar continua sem poder. Elas aparecem no Tab do `/warp`, mas não na lista do `/warp` sem nome, que é do Essentials.
- **Reset:** no dia do mundo, às 05:00 (num mês mais curto que o dia, no último dia do mês):
  1. avisos no chat 60, 30, 10, 5 e 1 minuto antes;
  2. quem está no mundo é levado pra fora (`saida.mundo`, ou o mundo principal);
  3. o Multiverse refaz o mundo com uma seed nova, mantendo as regras (`mv regen <mundo> --seed`, pelo console). O plugin espera o mundo novo aparecer, até 30 s, e então reaplica a borda, refaz o centro do End e anuncia ("O Nether foi resetado!", que vai pro Discord).
- **Quem saiu do servidor dentro de um desses mundos** e volta depois do reset é levado a um ponto seguro do mundo novo, com um aviso, logo depois do login no AuthMe (senão o `ForceSpawnLocOnJoin` dele desfaria o resgate). Sem isso, apareceria no mundo principal nas coordenadas antigas.
- **`/mapa [recursos|nether|end]`** (sem argumento, o mundo em que você está) dá o mapa de localização daquele mundo, uma vez só. O mapa mostra o mundo inteiro, com a borda como moldura, uma grade a cada 500 blocos e o centro. No End, mostra também a área protegida e o quadrado verde. Com ele na mão, dentro daquele mundo, a seta é você, e embaixo aparecem as coordenadas.
- **Terreno do mapa:** cada pixel é a cor (a do mapa do jogo) de um ponto do mundo, a cada 47 blocos no de recursos, 23 no Nether e 39 no End. No Nether, é o chão debaixo do teto; no End, o vazio fica escuro. O mapa só lê chunk que já existe, sem gerar nada: ele se preenche conforme os jogadores exploram, e o que ninguém visitou fica em branco. Cada chunk que carrega pinta o pedaço dele. Quando o plugin liga sem o desenho salvo, ele lê aos poucos os chunks já gerados. O desenho fica em `mapa-<mundo>.dat` (salvo a cada 5 min e ao desligar). **No reset, o desenho é apagado**, e o mapa do mundo novo começa em branco.

## O centro do End

- **Área protegida:** até 100 blocos do 0, 0 (em qualquer altura), ninguém quebra, constrói nem despeja balde, e explosão (de cama, de cristal ou do dragão) não quebra bloco. A staff passa com `recursos.admin` no criativo.
- **Chegada:** o `/warp end` leva pro quadrado 3x3 de concreto verde em 0, 25, na ilha principal, olhando pro portal. O plugin põe o quadrado quando o mundo carrega e depois de cada reset. **Em cima dele não tem PvP:** quem está nele não apanha nem bate. Fora dele, PvP normal, e um holograma em cima do quadrado avisa isso (`end.aviso-pvp`).
- **Gaiolas dos cristais:** os dois pilares mais baixos vêm com uma gaiola de grades em volta do cristal, e dentro da área protegida não dá pra construir até eles. Por isso o plugin tira as grades (a cada 30 s, com gente no End, porque o ritual do dragão refaz os pilares): todos os cristais caem com flecha.
- **Dragão:** renasce todo dia às 21:00 (`end.dragao.hora`), se não estiver vivo, pelo ritual do jogo (4 cristais em volta do portal), com aviso no chat. Só vale nos 10 minutos do horário: um reinício mais tarde no mesmo dia não renasce de novo o dragão já morto. A staff força com `/recursos admin dragao`.
- **Ovo do dragão:** só enfeite. Não quebra e não teleporta quando alguém clica. Como no jogo, ele só aparece na primeira morte do dragão de cada End, e o reset traz um End novo.
- As portas pras ilhas de fora (end gateways) ficam dentro da área protegida, no alto: se chega nelas com pérola do End.

## Comandos

| Comando | O que faz |
|---|---|
| `/warp recursos`, `/warp nether`, `/warp end` | Vai pro mundo |
| `/recursos [info] [mundo]` | Datas dos próximos resets (dos três, ou de um) |
| `/luz` (ou `/visao`) | Liga e desliga a visão noturna (pra todos). Fica até desligar: volta depois de morrer, de beber leite e de sair do servidor |
| `/mapa [recursos\|nether\|end]` | O mapa de localização do mundo |
| `/recursos admin resetar [mundo] [minutos\|agora]` | Marca um reset (padrão: o mundo de recursos, em 5 min, com os avisos) ou reseta na hora. O mundo pode ser o nome ou o comando (`nether`, `end`) |
| `/recursos admin cancelar [mundo]` | Cancela o reset marcado (o automático continua) |
| `/recursos admin levar [mundo] <jogador\|seletor>` | Leva o jogador (ou `@p[distance=..2]`, num bloco de comando) pro mundo. É o que o portal do spawn usa |
| `/recursos admin dragao` | Renasce o dragão do End agora (se ele não estiver vivo) |
| `/recursos admin mapa` | Quanto do mapa de cada mundo já tem terreno, e salva os desenhos |
| `/recursos admin reload` | Recarrega o config e prepara os mundos de novo (borda e centro do End) |

`recursos.admin` (op) também ignora a espera do teleporte e constrói no centro do End, no criativo.

## Config

```yaml
reset:
  ativado: true
  hora: "05:00"
  avisos-minutos: [60, 30, 10, 5, 1]
saida:
  mundo: ""          # vazio = mundo principal; com o mapa funcional, "spawn"
teleporte:
  tentativas: 20
  cooldown-segundos: 30
mundos:              # nome do mundo no Multiverse; warp = o nome no /warp
  recursos:        {nome: "mundo de recursos", warp: recursos, dia-do-mes: 30, borda: 6000, raio: 2500}
  recursos_nether: {nome: "Nether", warp: nether, dia-do-mes: 20, borda: 3000, raio: 1400}
  recursos_end:    {nome: "End", warp: end, dia-do-mes: 10, borda: 5000}
end:
  raio-protegido: 100
  plataforma: [0, 25]
  bloco-plataforma: LIME_CONCRETE
  remover-gaiolas: true
  dragao: {renascer: true, hora: "21:00", mensagem: "..."}
```

Um config de antes do Nether e do End é atualizado sozinho quando o plugin liga. As seções novas entram com os valores padrão, e saem as chaves que não valem mais (`mundo`, `reset.a-cada-dias`, `borda` e `teleporte.raio`). O `saida.mundo` continua.

## Requisitos

Multiverse-Core: cria os mundos e faz o regen. O `configurar-mundos.bat` do servidor de teste cria os três e liga o `confirm-mode: disable_console` do Multiverse (`mv config confirm-mode disable_console`). **Sem essa opção o reset não acontece**, porque o regen pede um `/mv confirm` que ninguém dá. O último reset e o ciclo de cada mundo, e o id do mapa de cada um, ficam em `dados.yml`. Na ida pra VPS, ver [docs/producao-vps.md](../docs/producao-vps.md).

## Testes

`tools/bots/teste-recursos.js` (o mundo normal e o reset) e `tools/bots/teste-nether-end.js`:
- `/warp nether` abaixo do teto e `/warp end` no quadrado verde, com as bordas e o holograma do PvP;
- o `/luz` (e ele voltando depois de morrer);
- o `/mapa` e o terreno dele (apagado no reset);
- o centro protegido e o PvP só fora do quadrado;
- as gaiolas, o dragão renascendo e o ovo parado;
- o reset do Nether e o resgate de quem saiu lá dentro.
