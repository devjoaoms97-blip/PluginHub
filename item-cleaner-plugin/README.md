# ItemCleanerPlugin

Limpa periodicamente os itens dropados no chão (drops de mob, blocos minerados, PvP, farms automáticas, etc.) pra reduzir lag causado por muitas entidades acumuladas — sem pegar o jogador de surpresa: avisa no chat em contagem regressiva antes de limpar.

## Como funciona

- A cada `intervalo-segundos` (padrão: 300s / 5 min), o plugin remove todos os itens no chão.
- Antes disso, avisa no chat nos segundos definidos em `avisos-segundos` (padrão: 60, 30, 10, 5).
- Não há tempo mínimo no chão: o item com dono que sai vai pros achados e perdidos (veja abaixo), então nada se perde de vez.
- Por padrão, **não remove itens renomeados** (ex: na bigorna) — geralmente é sinal de que o jogador guardou aquilo de propósito.
- Dá pra restringir a mundos específicos (`mundos.modo` + `mundos.lista`), por exemplo pra nunca limpar num mundo de museu/spawn onde itens ficam expostos.

## Orbes de XP, perto de jogador e aviso manual

- `limpar-orbes-xp` (padrão true): os orbes de XP soltos também saem.
- `protecao-perto-de-jogador` (padrão 8 blocos): os **orbes de XP** num raio desses de um jogador ficam (ele está pegando). 0 desliga.
- **Os itens não têm proteção nenhuma perto de jogador** (decisão de 2026-10-28): a limpeza leva todos. O que tem dono vai pros achados e perdidos, então quem perdeu algo valioso resgata por lá. Antes, qualquer jogador perto segurava os itens no chão, e depois do Gladiador quem assistia deixava os itens dos eliminados na arena.
- `aviso-manual-segundos` e `mensagens.aviso-manual`: o tempo e a mensagem do `/limparchao` sem argumento (antes eram fixos em 10s, com a mensagem da automática).

## Achados e perdidos

O item que a limpeza tiraria do chão e que tem dono não some: vai pra caixa do dono, que abre com `/achados` (ou `/perdidos`). Dono é quem jogou o item ou, nos drops de morte, quem morreu (os drops que nascem perto do corpo no mesmo instante da morte ganham o morto como dono). Cada pilha volta pagando `achados.taxa-por-pilha` (R$ 100), e essas coins somem da economia. A caixa guarda até `achados.maximo-por-jogador` (45) pilhas por `achados.dias` (3) dias; o que passa disso some, como antes. Quem está online vê um aviso na barra de ação.

Nos mundos de `achados.mundos-sem-caixa` (as `arenas`, onde quem morre perde os itens pro vencedor) nada vai pra caixa. Drop de mob, de bloco minerado e de farm não tem dono e some normalmente. A caixa fica em `achados.yml`. Precisa do Vault pra cobrar (sem ele, recuperar é de graça).

## Comandos

| Comando | Ação |
|---|---|
| `/achados` | Abre os seus achados e perdidos |
| `/limparchao` | (staff) Avisa no chat e limpa em 10 segundos |
| `/limparchao agora` | (staff) Limpa na hora, sem aviso |
| `/limparchao status` | (staff) Mostra quantos segundos faltam pra próxima limpeza automática |
| `/limparchao reload` | (staff) Recarrega o `config.yml` e reinicia o temporizador |

## Permissões

| Permissão | Padrão | Descrição |
|---|---|---|
| `itemcleaner.admin` | op | Permite usar `/limparchao` e seus subcomandos |

## Requisitos

- Paper 26.2 (Java 25)
- Vault (opcional): sem ele, recuperar dos achados e perdidos é de graça

## Como compilar

```bash
cd item-cleaner-plugin
mvn clean package
```

O `.jar` sai em `target/ItemCleanerPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **Só remove entidades do tipo `Item`** (itens dropados no chão) — não mexe em orbes de experiência, minecarts, boats, item frames, armor stands ou qualquer outra entidade. Se quiser incluir orbes de XP também (outra fonte comum de lag), é só pedir.
2. **A contagem regressiva roda a cada segundo (1 tick a cada 20 ticks do servidor)**, então o timing dos avisos é preciso o suficiente pra esse uso, mas não é "tempo real" milissegundo a milissegundo.
3. **Limpeza manual (`/limparchao` sem argumento) sempre avisa 10s antes** — esse valor é fixo no código, não configurável via `config.yml` (só o intervalo automático é). Se quiser deixar configurável também, é rápido de adicionar.
4. **Sem idade mínima** — antes, o item precisava estar no chão há `idade-minima-segundos`; isso saiu com os achados e perdidos. O filtro que fica é o nome customizado (`ignorar-itens-nomeados`).
