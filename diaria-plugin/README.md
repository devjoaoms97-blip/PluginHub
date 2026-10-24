# DiariaPlugin

Recompensa diária de login. O `/diaria` paga uma vez por dia, numa sequência de 7 dias que cresce até o 7º. Perder um dia volta a sequência para o dia 1, e depois do 7º ela recomeça. Quem entra no servidor com a recompensa disponível recebe um aviso.

## Quanto vale

Cada dia vale uma **% do preço do próximo rank** do jogador (RankupPlugin), para pesar parecido em qualquer fase do jogo:

| Dia | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| % do próximo rank | 0,5% | 0,5% | 0,75% | 0,75% | 1% | 1% | 2,5% |

- A semana inteira dá **7% de um rank**, perto de 2 a 3 horas de mineração.
- O mínimo é R$ 200 por dia. No 7º dia, o mínimo é três vezes isso (R$ 600).
- Sem o RankupPlugin, cada dia vale `dias.sem-rankup` (R$ 1.000).

É dinheiro novo na economia: aparece no painel como "Recompensa diária" (`/economia tipos DiariaPlugin`) e no `/pix historico`.

## Bônus do VIP

`vip-bonus.permissoes` dá uma % a mais na diária pela maior permissão do jogador (por padrão as mesmas do bônus de XP das skills: bronze +20%, prata +40%, ouro +60%, diamante +100%). A mensagem mostra o bônus.

## Comandos

| Comando | O que faz |
|---|---|
| `/diaria` (ou `/daily`) | Resgata a recompensa do dia |
| `/diaria admin <ontem\|anteontem> <jogador>` | (staff, `diaria.admin`) Volta a data do último resgate, para testar a sequência |

## Config

```yaml
dias:
  percentual-do-proximo-rank: [0.5, 0.5, 0.75, 0.75, 1.0, 1.0, 2.5]   # o tamanho da lista é o da sequência
  minimo: 200.0
  minimo-multiplicador-ultimo-dia: 3
  sem-rankup: 1000.0
integracao:
  pix: true
```

## Dados

`diaria.yml` guarda, por jogador, o dia do último resgate e em que dia da sequência ele está.
