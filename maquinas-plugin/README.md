# MaquinasPlugin

Máquinas nos terrenos que fabricam **produtos** (itens que não existem no vanilla, com textura própria do servidor) gastando **Células de Combustível**. Os produtos vendem no balcão **Máquinas** da Loja Oficial, com preço dinâmico. Outra fonte de renda além da mina e dos spawners.

## Como funciona

- **Compra:** no balcão Máquinas da Loja Oficial (o vendedor "Maquinista" abre o `/maquinas loja`). Cada máquina libera num rank. Lá também se compra a Célula de Combustível e se vendem os produtos.
- **No terreno:** só no mundo dos terrenos (`mundos`), até `limite-por-jogador` máquinas (4; VIP de 5 a 7). A máquina é um bloco de barreira (colisão e clique) com um item display por cima desenhando o modelo dela, do resource pack, de frente pra quem colocou. Em cima, um holograma mostra o estoque, o combustível e se está produzindo, cheia ou sem combustível.
- **Produção:** pelo relógio, mesmo com o dono offline ou o terreno descarregado. A cada intervalo sai um produto, gastando uma fração de Célula, até acabar o combustível (tanque de 64) ou encher o estoque (96, mais 64 por nível de melhoria). Como o estoque enche em uma a duas horas, a máquina recompensa quem passa pra coletar, não quem some por dias.
- **Menu** (clique direito, só o dono): **Coletar**, **Abastecer** (põe as Células do inventário no tanque), **Melhorar velocidade** (-5% de tempo por nível: no nível 5, 1,33× mais produtos), **Melhorar estoque** (+64 por nível), até o nível 5, e **Recolher** (coleta, devolve a máquina com os níveis e o combustível que sobrou).
- **Venda dos produtos:** clique no produto no balcão vende tudo o que você tem dele. Igual à loja: cada unidade vendida baixa o preço (`passo`), que volta pra âncora com o tempo. Valem o bônus do rank e o do VIP (+3/6/9/12%), menos o imposto.
- **Proteção:** o produto, a Célula e a máquina são marcados por dentro: não dá pra falsificar, e não entram em receita nenhuma.

## Máquinas

Calibradas pelo `tools/economia/maquinas.py`: cada máquina rende ~10% da mina no rank em que libera (no preço de âncora, já tirando o combustível, no nível 0) e se paga em umas 20 h de produção. Com a velocidade no máximo, ~13%; com o limite todo usado no máximo, ~54% da mina sem VIP e ~94% no VIP Diamante. A mina continua sendo a renda principal.

| Máquina | Produto | Rank | Preço | Por hora | Lucro/h |
|---|---|---|---|---|---|
| Tear | Tecido Fino | Fazendeiro | R$ 12 mil | 60 | ~R$ 585 |
| Forja | Barra de Aço | Ferreiro | R$ 35 mil | 60 | ~R$ 1.770 |
| Destilaria | Essência Destilada | Mercador | R$ 60 mil | 40 | ~R$ 3.000 |
| Lapidadora | Gema Lapidada | Clérigo | R$ 140 mil | 30 | ~R$ 6.900 |
| Reator Arcano | Cristal de Energia | Conde | R$ 700 mil | 20 | ~R$ 35.000 |

A Célula de Combustível custa R$ 25. Melhoria custa 30% do preço da máquina × (nível + 1).

## Texturas

Os modelos e as texturas vêm do resource pack do **PacotePlugin** (namespace `servidor`): o produto é `servidor:<id>` e a máquina, `servidor:maquina_<id>`. Sem o pacote aceito, aparecem com a textura de "faltando". As texturas são geradas pelo `tools/pacote/gerar.py`.

## Comandos

| Comando | Ação |
|---|---|
| `/maquinas loja` | O balcão das máquinas |
| `/maquinas dar <jogador> <maquina\|combustivel\|produto> [id] [qtd]` | (staff) Dá itens |
| `/maquinas lista` | (staff) As máquinas colocadas |
| `/maquinas acelerar <min>` | (staff, teste) Faz o relógio de todas as máquinas andar N minutos |
| `/maquinas reload` | (staff) Recarrega o config |

Permissão da staff: `maquinas.admin`.

## Dados

`maquinas.yml` (as máquinas colocadas: tipo, dono, níveis, combustível, estoque e o relógio) e `precos.yml` (o preço atual de cada produto).

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/MaquinasPlugin.jar`. Requisitos: Paper 26.2, Vault e o PacotePlugin.
