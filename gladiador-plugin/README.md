# GladiadorPlugin

Evento de PvP na arena. A staff, ou a agenda, abre as inscrições. Quando elas fecham, os inscritos vão para a arena, e vence o último jogador de pé (modo **individual**) ou o último clã com alguém vivo (modo **por clã**). **Cada um luta com o próprio equipamento, e quem morre perde os itens.**

## Como funciona

1. **Abertura.** `/gladiador abrir <individual|cla> [inscrição]`, ou a agenda. O anúncio sai no chat com a inscrição e o prêmio.
2. **Inscrições.** Ficam abertas por 5 minutos, com lembretes no chat. `/gladiador entrar` cobra a inscrição, e `/gladiador sair` devolve. No modo por clã, só entra quem tem clã. Quem está no mundo das arenas, num duelo por exemplo, não entra.
3. **Início.** Se houver pelo menos 4 jogadores (individual) ou 2 clãs (por clã), todos vão para a arena com vida e fome cheias. No individual, cada um vai para um ponto de entrada diferente, em rodízio. No modo por clã, cada clã sai de um mesmo ponto. Se faltar gente, o evento é cancelado e as inscrições voltam.
4. **Preparação.** 15 s sem dano.
5. **Luta.**
   - Só os participantes se machucam entre si. No modo por clã, membros do mesmo clã nunca se ferem.
   - Os comandos (fora o `/gladiador`) e os teleportes ficam bloqueados.
   - Morrer, desconectar ou sair da área da arena elimina. Os itens caem na arena, e o eliminado renasce onde estava antes de se inscrever.
6. **Fim.**
   - O pote é a soma das inscrições mais o prêmio do servidor (R$ 10.000).
   - No individual, vai todo para o vencedor. No modo por clã, é dividido igualmente entre todos que lutaram pelo clã vencedor, vivos ou não.
   - Depois de 20 minutos de luta, quem estiver de pé divide o pote.
   - Os vencedores têm 30 s para pegar os itens e depois voltam.
7. **Cancelamento** pela staff, ou no desligamento do servidor: as inscrições voltam e quem estava na arena sai.

## Comandos

| Comando | O que faz |
|---|---|
| `/gladiador` ou `/gladiador info` | Evento aberto, inscritos, pote e fase |
| `/gladiador entrar` / `/gladiador sair` | Inscrição (sair só antes da luta) |
| `/gladiador top` | Jogadores e clãs com mais vitórias |

Staff (`gladiador.admin`):

| Comando | O que faz |
|---|---|
| `/gladiador abrir <individual\|cla> [inscrição]` | Abre as inscrições |
| `/gladiador iniciar` | Fecha as inscrições e começa agora |
| `/gladiador cancelar` | Cancela e devolve as inscrições |
| `/gladiador arena pos1` / `pos2` | Cantos da área da arena (sair dela elimina) |
| `/gladiador arena spawn add` / `spawn limpar` | Pontos de entrada, onde você está |
| `/gladiador arena info` | O que falta para a arena ficar pronta |
| `/gladiador reload` | Recarrega o config e a agenda |

## Config

| Chave | Padrão | O que é |
|---|---|---|
| `inscricao.valor` | 2000 | Inscrição padrão (vai para o pote) |
| `inscricao.duracao-segundos` | 300 | Tempo de inscrição |
| `inscricao.avisos-segundos` | 240, 120, 60, 30, 10 | Lembretes antes de fechar |
| `premio.servidor` | 10000 | Somado às inscrições. É dinheiro novo na economia |
| `minimo.jogadores` / `minimo.clas` | 4 / 2 | Mínimo para começar |
| `luta.preparacao-segundos` | 15 | Tempo sem dano no começo |
| `luta.tempo-maximo-minutos` | 20 | Depois disso, quem está de pé divide o pote |
| `luta.tempo-pra-pegar-itens-segundos` | 30 | Tempo dos vencedores na arena depois da luta |
| `agenda` | vazia | Horários automáticos: `"SABADO 20:00 cla"`, `"DOMINGO 18:00 individual"` |

## Integrações

- **Vault:** inscrição e prêmio.
- **ClasPlugin:** o modo por clã usa a tag do clã de cada jogador (`tagDoCla`). Sem o ClasPlugin, o modo por clã não abre.
- **PixPlugin:** inscrição, devolução e prêmio aparecem no `/pix historico`.
- **Painel da economia:** `/economia tipos GladiadorPlugin`.

## Dados

`arena.yml` guarda a área e os pontos de entrada, e `estatisticas.yml`, as vitórias por jogador e por clã. O evento em andamento fica só na memória: ao desligar, ele é cancelado e as inscrições voltam.
