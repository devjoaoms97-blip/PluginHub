# MaquinasPlugin

Máquinas nos terrenos que fabricam **produtos** (itens que não existem no vanilla, com textura própria do servidor) gastando **Células de Combustível**. Os produtos vendem no balcão **Máquinas** da Loja Oficial, com preço dinâmico. Outra fonte de renda além da mina e dos spawners.

## Como funciona

- **Compra:** no balcão Máquinas da Loja Oficial (o vendedor "Maquinista" abre o `/maquinas loja`). Cada máquina libera num rank. Lá também se compra a Célula de Combustível e se vendem os produtos.
- **No terreno:** só no mundo dos terrenos (`mundos`), até `limite-por-terreno` máquinas **em cada terreno** (10), somando todos os tipos e todos os donos: 10 forjas, ou 5 forjas e 5 teares. Quem tem dois terrenos põe 10 em cada. Fora de um terreno, a máquina não entra. Sem o TerrenosPlugin, o limite vale por jogador. A máquina é um bloco de barreira (colisão e clique) com um item display por cima desenhando o modelo dela, do resource pack, de frente pra quem colocou. Em cima, um holograma mostra o estoque, o combustível e se está produzindo, cheia ou sem combustível.
- **Produção:** pelo relógio, mesmo com o dono offline ou o terreno descarregado. A cada intervalo sai um produto, gastando uma fração de Célula, até acabar o combustível ou encher o estoque (96, mais 64 por nível de melhoria). O tanque cabe combustível pra 96 produtos, mais 64 por nível da melhoria de Tanque, no consumo de cada máquina, e nunca menos de 64 Células (desde 2026-11-07; antes era 64 pra todas, e da Destilaria pra cima o combustível acabava antes de encher o estoque: o Reator parava em 16 produtos). Como o estoque enche em uma a duas horas, a máquina recompensa quem passa pra coletar, não quem some por dias.
- **Menu** (clique direito, só o dono): **Coletar**, **Abastecer** (põe as Células do inventário no tanque), **Melhorar velocidade** (-5% de tempo por nível: no nível 5, 1,33× mais produtos), **Melhorar estoque** (+64 por nível), **Melhorar tanque** (+64 produtos de combustível por nível: o Reator vai de 384 a 1.664 Células), até o nível 5, e **Recolher** (coleta, devolve a máquina com os níveis e o combustível que sobrou).
- **Venda dos produtos:** clique no produto no balcão vende tudo o que você tem dele. Desde 2026-11-07, **sempre na âncora** (`preco-dinamico: false`, como a Loja Oficial). Com o dinâmico ligado, **o preço é de cada jogador:** cada unidade que você vende baixa o seu preço (`passo`), que volta pra âncora com o tempo (2% da diferença por minuto), e as suas vendas não mexem no preço dos outros. O `minimo` de cada produto fica 25% acima do combustível de uma unidade, então vender nunca dá prejuízo, e o `passo` faz quem tem o terreno cheio de uma máquina, na velocidade máxima, vender a ~75% da âncora (`tools/economia/maquinas.py`, segunda tabela). Valem o bônus do rank e o do VIP (+3/6/9/12%), menos o imposto. O balcão mostra o gasto de combustível com duas casas ("0,25 Célula") e quanto ele custa em reais por unidade.

  Até 2026-10-31 o preço era um só pro servidor, com mínimo abaixo do combustível no Tecido, na Barra e na Essência: com uns 15 teares vendendo, o Tecido ficava abaixo do custo pra todo mundo.
- **Rank:** só coloca e mexe numa máquina quem tem o rank dela, ou quem já fez prestígio (o prestígio volta o rank pro começo, mas o jogador já passou pelo Rei). A máquina que chega por outro caminho (tesouro, barraca) pode ser guardada e vendida, mas fica toda bloqueada até lá: não se coloca, e a que já estiver no chão não abre o menu (coletar, abastecer, melhorar, recolher).
- **Proteção:** o produto, a Célula e a máquina são marcados por dentro: não dá pra falsificar, e não entram em receita nenhuma.

## Máquinas

Calibradas pelo `tools/economia/maquinas.py`: cada máquina rende ~10% da mina no rank em que libera (no preço de âncora, já tirando o combustível, no nível 0) e se paga em umas 20 h de produção. Com a velocidade no máximo, ~13%; com as 10 máquinas de um terreno no máximo, ~134% da mina (~268% com dois terrenos). Com o limite por terreno, as máquinas passam a render mais que a mina: a recalibração está pendente.

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

`maquinas.yml` (as máquinas colocadas: tipo, dono, níveis, combustível, estoque e o relógio) e `precos.yml` (o preço atual de cada produto pra cada jogador que vendeu; quem volta à âncora sai do arquivo. O formato antigo, um preço pro servidor todo, é ignorado).

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/MaquinasPlugin.jar`. Requisitos: Paper 26.2, Vault e o PacotePlugin.
