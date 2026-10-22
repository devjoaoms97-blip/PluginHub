# Ferramentas da economia

Dois scripts em Python pra calibrar e acompanhar a economia do rankup. Precisam do PyYAML (`pip install pyyaml`).

| Arquivo | Pra quê |
|---|---|
| `simular.py` | Antes de ter jogadores: quantas horas leva cada rank, com os preços e as minas atuais |
| `minas-planejadas.yml` | As minas que a simulação usa. Monte as minas no jogo com essas composições |
| `analisar.py` | Com o servidor rodando: resumo da semana a partir dos dados que os plugins gravam |

## Meta

Um jogador ativo chega ao **Rei em cerca de 150 h** de jogo, e cada rank leva cerca de 1,5× o tempo do anterior: o primeiro leva 1 h, e o último cerca de 49 h.

## Simulação (`simular.py`)

```
python tools/economia/simular.py                    # cenários padrão
python tools/economia/simular.py --meta-horas 150   # e sugere preços de rank pra bater a meta
python tools/economia/simular.py --jogadores 10 --fortuna 3
```

Ela lê os ranks do `rankup-plugin/src/main/resources/config.yml`, os preços da loja do `test-server/configs/EconomyShopPlugin/itens.yml` e as minas do `minas-planejadas.yml`. Em cada rank, o jogador minera na mina que mais rende entre as que ele pode usar, vende tudo no `/sell` (com o bônus do rank e o imposto da loja) e sobe quando junta o preço.

**O que ela supõe:**
- **Picareta de diamante com Eficiência III.** O tempo de cada bloco segue a regra do Minecraft: a dureza do bloco e a espera de 5 ticks entre um bloco e outro.
- **50% do tempo quebrando bloco.** O resto vai em andar, vender e esperar o reset da mina. Mude com `--utilizacao`.
- **Preço da loja no equilíbrio.** O preço cai a cada unidade vendida e volta aos poucos. A simulação usa o ponto em que as duas forças empatam, com N jogadores vendendo o mesmo minério. A referência usa 3 por mina.
- **Sem Fortuna.** Com Fortuna III, os minérios dropam cerca de 2,2× mais.
- **Só mineração.** Missões, boss e bounty também dão dinheiro, mas pouco perto da mina a partir do meio do jogo. No começo, as missões aceleram os primeiros ranks.

**Resultado com os preços atuais (3 jogadores por mina, sem Fortuna):**

| Cenário | Horas até o Rei |
|---|---|
| Sozinho na mina | ~127 h |
| 3 por mina (referência) | ~150 h |
| 10 por mina | ~214 h |
| 3 por mina, com Fortuna III | ~116 h |

A diferença entre "sozinho" e "10 por mina" é a loja dinâmica: quanto mais gente vende o mesmo minério, mais o preço cai. A Fortuna é a maior alavanca que ainda não está decidida (veja o ROADMAP).

**Quando rodar de novo:** sempre que mudar o preço de um rank, a composição de uma mina ou o preço de um item da loja. Se a mina usar um bloco novo, ele precisa entrar na tabela `BLOCOS` do `simular.py` (dureza e o que ele dropa).

## Análise (`analisar.py`)

```
python tools/economia/analisar.py                          # C:\mc-teste, últimos 7 dias
python tools/economia/analisar.py --servidor D:\srv --dias 14
```

Ela lê o que os plugins gravam no servidor:

| Arquivo | Plugin | O que tem |
|---|---|---|
| `PainelEconomiaPlugin/historico.yml` | Painel | Fluxo por plugin e por tipo, circulação, mediana, Gini, jogadores e horas jogadas por dia |
| `EconomyShopPlugin/vendas-diarias.yml` | Loja | Quanto de cada item foi vendido por dia e o preço médio |
| `RankupPlugin/rankups.csv` | Rankup | Cada rankup com as horas de jogo do jogador |

E mostra:
1. **Por dia:** jogadores, horas, dinheiro que entrou e saiu, o líquido por hora jogada, a circulação, a mediana e o Gini.
2. **Fontes e ralos:** o líquido de cada tipo de movimentação. Mostra quem coloca dinheiro novo e quem tira, e quanto do que entra os ralos tiram.
3. **Loja:** quanto de cada item foi vendido e a que fração do preço de referência.
4. **Rankups:** a mediana de horas em cada rank, comparada com a simulação.
5. **Pontos de atenção:** o líquido por hora subindo, ralos fracos, item vendido abaixo de 60% da referência (sinal de farm) e rank levando 50% a mais ou a menos que o simulado.

## Rotina semanal

Uma vez por semana, com jogadores de verdade:
1. Rodar o `analisar.py` (ou pedir pro Claude rodar).
2. O Claude escreve um relatório: o que está fora da meta, a causa provável e a mudança sugerida no config, com o diff pronto.
3. **Nada muda sem aprovação.** Aprovada a mudança, roda a simulação de novo pra conferir o efeito antes de aplicar.

Com o servidor vazio, os números não querem dizer nada. Os primeiros relatórios só fazem sentido depois de alguns dias com jogadores.

Ajuste automático (o "Banco Central" do ROADMAP) ficou de fora por decisão: no começo, tudo passa por aprovação.
