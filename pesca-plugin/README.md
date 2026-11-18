# PescaPlugin

O porto da **/warp pesca** (2026-11-17): 16 barraquinhas alugadas na beira de um lago, num porto longe de tudo, onde só se chega pela warp. Quem aluga fica pescando nela, e pode ficar parado: a vara puxa e lança sozinha, e o pescado vai pro **estoque** da barraquinha (o barril), não pro inventário.

- **Aluguel:** `preco` (R$ 10.000) por `horas-aluguel` (24 h), uma por jogador, renovável até `periodos-maximos` (3) períodos pagos. Dá pra ligar a renovação automática, que cobra quando o aluguel acaba (se o dono tiver o dinheiro). O pagamento vai pro extrato do Bancário (PixPlugin).
- **Pesca:** com a vara na mão e dentro da própria barraquinha:
  - quando o peixe morde, o plugin puxa a vara;
  - depois de `relancar-ticks` (1 s), ela é lançada de novo, sem o jogador clicar;
  - a espera até morder é a do jogo (5 a 30 s, menos 5 s por nível de Isca) vezes `fator-espera` (2): rende uns **50% da pesca ativa**.
  - Conta como pesca normal: XP de Pescador, missões e o loot especial das Skills. Dentro da barraquinha, as Skills não aplicam o desconto da pesca parada (25%), porque a espera já é maior (`SkillsPlugin` pergunta `PescaPlugin#pescandoNaBarraca`).
- **Estoque:** até `estoque-pilhas` (45). O item pescado é guardado uns ticks depois da fisgada (as Skills trocam o item do loot especial no tick seguinte). Cheio, a vara para de puxar sozinha e a barra avisa.
- **Barril:** clique direito no barril da barraquinha:
  - livre: o menu de aluguel;
  - do dono: o estoque. Clicar num item pega só ele; também tem "Pegar tudo", renovar, renovar sozinho e devolver a barraquinha (dois cliques);
  - de outro jogador: só um aviso.
- **Fim do aluguel:** a barraquinha fica livre e o estoque vai pra retirada do dono (`/pesca retirar`) por `retirada-dias` (7).
- **Holograma:** em cima de cada barril, com o número, o dono e quanto falta (ou "LIVRE" e o preço). Não fica salvo no mundo.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/pesca` (`/porto`, `/barraquinha`) | todos | As barraquinhas: a sua (com [IR]) e as livres (clicar aluga) |
| `/pesca alugar <n>` | todos | Aluga a barraquinha `n` |
| `/pesca renovar` / `autorenovar` | dono | Mais um período / liga ou desliga a renovação automática |
| `/pesca ir` | dono | Vai pra sua barraquinha |
| `/pesca sair confirmar` | dono | Devolve a barraquinha (sem reembolso) |
| `/pesca retirar` | todos | Pega o estoque de um aluguel que acabou |
| `/pesca admin barraca <id> <mundo> <x1 y1 z1> <x2 y2 z2> <barril x y z>` | staff | Cadastra (ou move) uma barraquinha. A estrutura `porto` roda isso pras 16 |
| `/pesca admin liberar <id>` / `vencer <id>` | staff | Encerra o aluguel / vence agora (testa a renovação automática) |
| `/pesca admin lista` / `remover <id>` / `reload` | staff | |

## Mapa

A estrutura `porto` do EstruturasPlugin (`node tools/bots/montar-mapa.js construir porto`) monta o porto e cadastra as barraquinhas. Ela fica em x 480..560, z -40..14 (origem `400 65 0`), solta no vazio do mundo spawn, sem estrada nem porta: só se chega pela `/warp pesca`, no calçadão sul (550.5 65 10.5, `marcar`), e só se sai por comando. Até 2026-11-18 ficava ao norte da Loja Oficial, com uma porta no fundo dela. Os dados ficam em `barracas.yml`.

Teste: `node tools/bots/teste-pesca-porto.js`.
