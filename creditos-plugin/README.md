# CreditosPlugin

Créditos da loja do Discord, trocados no jogo por **VIP** e **cosméticos**, mais a **tag de apoiador**. Decisões de 2026-10-27:

- A loja do Discord vende **créditos** (R$ 1 = 10 créditos).
- Créditos viram **cosméticos** e **VIP**, por preço fixo e sem sorteio.
- **Itens do jogo não são vendidos**: só saem jogando. Os créditos não viram coins, e as coins não viram créditos.
- Quem quer contribuir sem levar nada ganha a **tag de apoiador** por 30 dias a cada contribuição.

Por quê: caixa com prêmio aleatório paga com dinheiro é proibida pro público menor de idade (ECA Digital, Lei 15.211/2025), e item que dá vantagem pra quem paga é o que as regras da Mojang mais barram. O VIP continua sendo o ponto de risco (ele dá vantagem); os cosméticos não.

## No jogo

`/creditos` (ou `/cosmeticos`) mostra o saldo e abre a loja:

| Seção | O que tem |
|---|---|
| **VIP** | Bronze, Prata, Ouro e Diamante, 30 dias. **Clique esquerdo** ativa pra você (pelo `/vip add`, com o bônus de ativação). **Clique direito** gera um código de presente (1 uso), que dá pra usar, dar ou vender no jogo (`/vip vender`) |
| **Rastros** | Partículas nos pés enquanto anda: corações, chamas, almas, notas, cerejeira, estrelas, encantado, neve |
| **Cor do nome** | O nome colorido no chat e na tab: aqua, verde, amarelo, rosa, vermelho, dourado, azul, roxo |
| **Efeitos de abate** | Ao derrotar outro jogador: raio, fogos, chuva de corações, almas, explosão, música. Só visual: não machuca ninguém. Botão direito mostra o efeito antes de comprar |

- **Toda compra pede um segundo clique** pra confirmar (em até 10 s): os créditos são dinheiro de verdade.
- O cosmético comprado é **pra sempre**. Em cada categoria usa-se um por vez; na loja mesmo dá pra trocar ou tirar.
- **VIP:** ativar o mesmo tier que já está ativo **soma** os 30 dias aos que faltam (por cima de um VIP de evento do mesmo tier, ele vira VIP normal, com o kit mensal). Quem já tem um VIP maior não ativa um menor (trocaria pelo menor): a loja sugere gerar o código.

## Pra loja do Discord (e a staff)

Pelo console (RCON), com `creditos.admin`:

| Comando | Ação |
|---|---|
| `creditos dar <jogador> <quantidade> [motivo]` | Entrega créditos (o jogador é avisado se estiver online) |
| `creditos tirar <jogador> <quantidade> [motivo]` | Tira créditos (estorno) |
| `creditos ver <jogador>` | Saldo e se é apoiador |
| `apoiador dar <jogador> [dias]` | Tag de apoiador por 30 dias (ou `dias`), somados ao que ainda faltava |
| `apoiador ver <jogador>` | Até quando é apoiador |

O jogador precisa ter entrado no servidor pelo menos uma vez. Toda entrada e saída de créditos fica em `creditos.log` (data, jogador, quanto, saldo e o motivo), pra conferir com os pagamentos. Os saldos ficam em `dados.yml`.

## Tag e cor no chat e na tab

Quem monta o chat e a tab é o VipPlugin: ele pede aqui a tag de apoiador (`tagApoiador`) e a cor do nome (`corDoNome`), por reflexão. Ordem: rank, clã, VIP, apoiador e o nome colorido. A tag vence sozinha; a tab de quem está online é refeita a cada 5 minutos.

## Config

Preços (em créditos) do VIP e de cada cosmético, a tag de apoiador, onde comprar (aparece na loja) e o catálogo de cosméticos (`cosmeticos.rastros|cores|abates`, com nome, ícone, partícula, cor ou efeito e preço). Os preços iniciais: VIP de 100 a 500 créditos (R$ 10 a R$ 50), rastro 50, cor 30 e efeito de abate 60. **São um ponto de partida: ajuste antes de abrir a loja.**

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/CreditosPlugin.jar`. Requisitos: Paper 26.2 e o VipPlugin (pro VIP, a tag e a cor no chat).
