# DueloPlugin

X1 com aposta. Um jogador desafia outro com `/duelo <jogador> [valor]`. Se o desafiado aceitar, as duas apostas ficam retidas e os dois vão para uma arena livre. **Cada um luta com o próprio equipamento, e quem morre perde os itens.** O vencedor leva o pote menos uma taxa, que sai de circulação.

## Como funciona

1. **Desafio.** `/duelo <jogador> [valor]`. Sem valor, o duelo é amistoso, mas os itens continuam em jogo. O convite vale 60 s. Nada é cobrado até o aceite.
2. **Aceite.** `/duelo aceitar` (ou `/duelo recusar`). As duas apostas saem das contas e os dois vão para uma arena livre, com vida e fome cheias e sem efeitos de poção.
3. **Contagem.** 5 s com os dois parados e sem dano.
4. **Luta.** Só os dois se machucam. Quem é de fora não bate neles, e eles não batem em quem é de fora. Os comandos (fora o `/duelo`) e os teleportes ficam bloqueados.
5. **Fim.**
   - Quem morre deixa os itens na arena e renasce onde estava antes do duelo.
   - O vencedor recebe o pote menos 5% e tem 20 s para pegar os itens. Depois volta para onde estava.
   - Duelos com aposta a partir de R$ 10.000 são anunciados para o servidor todo.
6. **Desconectar** no meio do duelo conta como morte: os itens caem na arena e o outro vence.
7. **Empate.** Passados 5 minutos de luta, as apostas voltam e os dois saem. O mesmo acontece se a staff cancelar o duelo ou se o servidor desligar.

As arenas ficam no mundo `arenas`, que não guarda o inventário ao morrer (`keep_inventory false`). Quem está nesse mundo, num duelo ou em outro evento como o Gladiador, não começa um duelo novo.

## Comandos

| Comando | O que faz |
|---|---|
| `/duelo <jogador> [valor]` | Desafia, apostando o valor |
| `/duelo aceitar` / `/duelo recusar` | Responde ao desafio |
| `/duelo stats [jogador]` | Vitórias e derrotas |
| `/duelo top` | Os 10 com mais vitórias |
| `/duelo assistir <jogador>` | Assiste o duelo desse jogador, no modo espectador, de cima da arena |
| `/duelo sair` | Para de assistir e volta para onde estava |

Staff (`duelo.admin`):

| Comando | O que faz |
|---|---|
| `/duelo arena criar <nome>` | Cria uma arena |
| `/duelo arena pos1 <nome>` / `pos2 <nome>` | Marca o ponto de partida de cada lutador, onde você está e olhando para onde ele deve olhar |
| `/duelo arena remover <nome>` | Remove a arena (se não estiver em uso) |
| `/duelo arena lista` | Arenas e se estão livres |
| `/duelo cancelar <jogador>` | Cancela o duelo do jogador e devolve as apostas |

Com mais de uma arena, acontece mais de um duelo ao mesmo tempo. Cada arena recebe um duelo por vez.

**Espectadores.** Quem assiste fica no modo espectador, a até 25 blocos do centro da arena (`espectador.raio`), sem outros comandos nem teleportes. Quando o duelo acaba, todo mundo que estava assistindo volta para onde estava, no modo de jogo de antes. Sair do servidor também tira do modo espectador.

## Config

| Chave | Padrão | O que é |
|---|---|---|
| `aposta.minima` / `aposta.maxima` | 0 / 0 | Limites da aposta (0 na máxima = sem limite) |
| `convite.expira-segundos` | 60 | Validade do desafio |
| `duelo.taxa-percentual` | 5 | % do pote que fica com o servidor |
| `duelo.contagem-segundos` | 5 | Contagem antes da luta |
| `duelo.tempo-maximo-minutos` | 5 | Depois disso, empate |
| `duelo.tempo-pra-pegar-itens-segundos` | 20 | Tempo do vencedor na arena depois da luta |
| `anuncio.aposta-minima` | 10000 | A partir desse valor, o resultado vai para o chat de todos |

## Integrações

- **Vault:** as apostas. Sem ele, só há duelo amistoso.
- **PixPlugin:** aposta, prêmio e devolução aparecem no `/pix historico`.
- **Painel da economia:** `/economia tipos DueloPlugin` separa apostas, prêmios e devoluções.

## Dados

`arenas.yml` guarda as arenas, e `estatisticas.yml`, as vitórias e derrotas. Os duelos em andamento ficam só na memória: ao desligar o servidor, todos viram empate e as apostas voltam.
