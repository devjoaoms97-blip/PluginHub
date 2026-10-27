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
   - **Borda que fecha.** Depois de 5 minutos de luta, uma borda começa a fechar do tamanho da arena até 8 blocos, em 5 minutos. Cada participante vê a parede, e quem fica fora toma dano a cada segundo: 1 coração no primeiro, e 0,5 coração a mais a cada segundo seguido lá fora (2, 3, 4... de dano, até 20), para nem a regeneração alta segurar. Voltar pra dentro zera a contagem. O dano sai direto da vida (primeiro dos corações de absorção), e armadura e Proteção não reduzem. Como o mundo das arenas é dividido com o X1 e o boss, a borda é só dos participantes, e não do mundo.
6. **Fim.**
   - O pote é a soma das inscrições mais o prêmio do servidor (R$ 10.000).
   - No individual, vai todo para o vencedor. No modo por clã, é dividido igualmente entre todos que lutaram pelo clã vencedor, vivos ou não.
   - Depois de 20 minutos de luta, quem estiver de pé divide o pote.
   - Os vencedores têm 30 s para pegar os itens e depois voltam.
   - **Destaques.** Também saem no chat e no Discord os três destaques da luta, que ganham uma tag no chat até o próximo Gladiador:
     - ☠ **[Ceifador]**: quem mais matou;
     - ⚡ **[Fujão]**: quem mais andou na arena, a pé, correndo ou pulando;
     - ✸ **[Top Damage]**: quem mais tirou vida dos outros participantes. Conta só a vida tirada de verdade: um golpe de 20 em quem tinha 3 de vida vale 3.

     No empate, fica quem se inscreveu primeiro. As tags vão antes de tudo no chat, e quem tem mais de uma mostra todas. O `/gladiador top` mostra quem tem cada uma.
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
| `borda.comeca-depois-segundos` / `borda.fecha-em-segundos` | 300 / 300 | Quando a borda começa a fechar e quanto tempo leva |
| `borda.tamanho-final` | 8 | Lado final da borda, em blocos |
| `borda.dano-inicial` / `borda.aumento-por-segundo` / `borda.dano-maximo` | 2 / 1 / 20 | Dano no 1º segundo fora (2 = 1 coração), quanto cresce por segundo seguido fora e o teto (0 = sem teto) |
| `destaques.ativado` / `destaques.tags.*` | true / `[Ceifador]`, `[Fujão]`, `[Top Damage]` | Destaques no fim da luta e o texto de cada tag (com cores `&`) |
| `agenda` | vazia | Horários automáticos: `"SABADO 20:00 cla"`, `"DOMINGO 18:00 individual"` |

### Prêmio do clã no banco

Com `premio.cla-no-banco: true`, o modo por clã manda o pote inteiro pro banco do clã vencedor (`/cla banco`), em vez de dividir entre os membros que sobraram de pé. O anúncio avisa "direto no banco do clã". Sem o ClasPlugin, divide como sempre.

## Integrações

- **Vault:** inscrição e prêmio.
- **ClasPlugin:** o modo por clã usa a tag do clã de cada jogador (`tagDoCla`). Sem o ClasPlugin, o modo por clã não abre.
- **PixPlugin:** inscrição, devolução e prêmio aparecem no extrato do Bancário.
- **Painel da economia:** `/economia tipos GladiadorPlugin`.
- **DiscordPlugin:** a abertura e o resultado vão para a categoria `gladiador`.

## Dados

`arena.yml` guarda a área e os pontos de entrada, `estatisticas.yml` guarda as vitórias por jogador e por clã, e `destaques.yml`, quem tem cada tag. O evento em andamento fica só na memória: ao desligar, ele é cancelado e as inscrições voltam.
