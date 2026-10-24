# EconomyShopPlugin

Loja do servidor com preço de venda dinâmico (oferta e demanda), categorias, histórico de preços, imposto sobre vendas, e integração com o extrato do Pix.

## Loja Oficial (presencial)

A loja é um prédio: a **Loja Oficial**, do lado de fora do portão LESTE do spawn (`/warp loja`, estrutura `loja` do EstruturasPlugin). Dentro, sete vendedores (NPCs com skin temática), um por categoria: **Minerador** (minérios), **Construtor** (blocos), **Lenhador** (madeiras), **Fazendeiro** (farm e comida), **Caçador** (drops de mob), **Jardineiro** (natureza) e **Alquimista** (poções, redstone, nether e end). Clique direito abre o menu só daquela categoria, onde se compra e vende.

- **Na mina, tudo vira dinheiro na hora** (venda automática, pra todos): o que se quebra dentro de uma mina do RankupPlugin é vendido pela loja, com o bônus do rank e do VIP, e o dinheiro aparece na barra de ação.
- **Comandos:** `/shop` (menu completo), `/buy` e `/sell` de qualquer lugar são só da staff (`shop.comandos`). O jogador que usa `/shop` recebe o aviso pra ir à Loja Oficial. **Nunca dê `essentials.sell` nem `essentials.worth`** aos jogadores: o `/sell` do Essentials vende pela tabela `worth.yml` dele e passaria por cima de tudo isso.
- **VIP:** compra com 3/6/9/12% de desconto (`vip-descontos`) e vende com 3/6/9/12% a mais (`vip-bonus-venda`), pelas permissões `vip.desconto.<tier>`. Na simulação, o Diamante chega ao último rank em ~96 h e quem não é VIP em ~150 h.
- **Sem limite de quantidade:** o preço dinâmico regula. O que muita gente vende cai de preço sozinho, então farm AFK é bem-vinda.
- **Trava** (`trava-venda.percentual-da-compra`, 90%): vendendo do inventário, uma unidade nunca rende mais que 90% do menor preço de compra possível (com o maior desconto de VIP). Sem isso, um VIP no último rank, com prestígio e evento de mercado (até ×8,96 na venda), comprava e revendia com lucro. A venda automática da mina não tem trava: o minério nem vira item.
- **Itens:** cada item pode ter `compra: false` (a loja não vende) e `venda: false` (os vendedores não compram) ou `venda: so-mina` (só a venda automática da mina vende).

### O catálogo (`tools/economia/loja.py`)

O `itens.yml` sai do script, que confere se nenhum preço abre brecha antes de gravar: compra sempre acima da venda no pior caso, e nenhum lucro comprando ingredientes, craftando, fundindo, cortando ou quebrando o bloco e vendendo o resultado (receitas do Minecraft pelo minecraft-data). São 356 itens em 7 categorias; os vendedores compram 96 (minérios, toras, farm e drops), e o resto (blocos, tábuas, natureza, alquimia) só a loja vende, tirando dinheiro de circulação. Os itens da mina mantêm os preços que a simulação de 150 h usa.

```
python tools/economia/loja.py                          # confere
python tools/economia/loja.py --gravar --servidor C:\mc-teste   # confere e grava
```

### Vendedores

| Comando | Ação |
|---|---|
| `/shop admin npc <id> [mundo x y z yaw]` | Põe o vendedor (onde você está, sem coordenadas). Os ids e as categorias ficam em `vendedores` no config |
| `/shop admin npc remover <id>` | Tira o vendedor |
| `/shop admin npc lista` | Lista os vendedores colocados |

## Como o preço funciona

- **Compra** (jogador compra do servidor): sempre o **preço base fixo** — nunca muda.
- **Venda** (jogador vende pro servidor): **dinâmica**. Começa numa "âncora" (`preço base × (1 − margem%)`), e:
  - Cada unidade **vendida** empurra o preço de venda **pra baixo**
  - Com o tempo, o preço de venda **se recupera** sozinho de volta em direção à âncora
  - Sempre limitado entre um **mínimo** e um **máximo** por item
- Numa venda em lote, o preço se ajusta **unidade por unidade dentro da mesma transação**.

## Comandos

| Comando | Ação |
|---|---|
| `/shop` | (staff) Abre a loja completa, com as categorias; pros jogadores, avisa pra ir à Loja Oficial |
| `/shop historico <item>` | Mostra o gráfico de tendência (sparkline) das últimas 24h |
| `/buy <item> <quantidade>` | (staff) Compra pelo preço fixo |
| `/sell` | (staff) Vende o item na mão |
| `/sell all` | (staff) Vende tudo do inventário que a loja compra |
| `/shop admin additem <categoria> <base> <margem%> <min> <max> <passo%>` | (staff) Cadastra o item na mão |
| `/shop admin removeitem` | (staff) Remove o item na mão |
| `/shop admin resetprice` | (staff) Reseta o preço de venda do item na mão |
| `/shop admin list` | (staff) Lista tudo cadastrado, marcando itens travados no preço mínimo |
| `/shop admin list piso` | (staff) Lista só os itens travados no preço mínimo |
| `/shop admin reload` | (staff) Recarrega o config.yml |

### Na GUI
- **Tela de categorias** → clique numa categoria pra entrar
- Dentro da categoria: **clique esquerdo** vende 1, **shift+esquerdo** vende tudo, **clique direito** compra 1, **shift+direito** compra 64
- Botão de baú (canto inferior) volta pra tela de categorias

## As 5 novidades desta rodada

### 1. Histórico de preços (gráfico)
Como não dá pra desenhar um gráfico "de verdade" numa interface de Minecraft, uso uma **sparkline em texto** (barrinhas Unicode ▁▂▃▄▅▆▇█ que sobem e descem representando a variação). Uma tarefa tira uma "foto" do preço de cada item a cada `historico.intervalo-minutos` (padrão: 15 min), guardando os últimos pontos das últimas 24h, com persistência em `historico.yml`.

```
/shop historico pedra
```

**Vendas por dia.** Além do gráfico, a loja guarda em `vendas-diarias.yml` quanto de cada item foi vendido por dia, o valor pago e o preço médio. O valor é o do preço dinâmico, antes do bônus do rank e do imposto. Ficam os últimos 90 dias (`estatisticas.dias-guardados`). Não tem comando: é o dado bruto pra análise da economia (veja `tools/economia`).

### 2. Categorias na GUI
Cada item agora tem uma categoria (texto livre, definida por você no `/shop admin additem`). A GUI ganhou uma tela inicial listando as categorias como "pastas" — clicar numa abre a lista paginada só daquele grupo.

### 3. Integração com o histórico do Pix
Toda compra/venda na loja agora também aparece no `/pix historico` do jogador, junto com pagamentos e cobranças do Pix — um extrato só, unificado. Isso foi feito com uma pequena adição no `ChargeManager` do Pix (um método novo, `registrarTransacaoComLoja`) e uma pequena correção no `PixCommand` (pra mostrar "Loja do Servidor" em vez de tentar achar um jogador com esse nome).

**Importante:** isso exige atualizar o `pix-plugin` também — os arquivos alterados estão inclusos separadamente (veja a seção abaixo).

Pra desligar essa integração sem desinstalar o Pix: `integracao.pix: false` no `config.yml`.

### 4. Imposto sobre vendas (sinkhole)
Uma pequena porcentagem de toda venda **desaparece** (não vai pra ninguém, nem pro "caixa" da loja) — isso ajuda a controlar a inflação do servidor com o tempo, tirando dinheiro de circulação aos poucos. Configurável em `imposto.percentual-venda` (padrão: 2%). O jogador vê no chat quanto foi de imposto em cada venda.

### 5. Diagnóstico de itens travados no piso

`/shop admin list` agora marca com `[NO PISO]` todo item cujo preço de venda atual já bateu no `minimo` configurado, e mostra um resumo (quantos itens e qual %) no final. Isso é um sinal de que a oferta de venda daquele item está acima do que `passo` + regeneração conseguem absorver — ou seja, o preço está permanentemente "no chão" e o mínimo provavelmente está alto demais (ou o imposto está baixo demais) pra segurar aquele volume. Use `/shop admin list piso` pra ver só os itens nessa situação, sem precisar rolar a lista inteira.

## Eventos de mercado ("alta do diamante")

A cada `eventos-mercado.intervalo-horas`, com `chance`% de sorte, um item da lista `eventos-mercado.itens` (que esteja cadastrado na loja) fica em alta por `duracao-horas`: a loja paga um multiplicador entre `multiplicador-minimo` e `multiplicador-maximo` na venda dele (no `/sell`, no menu e na venda automática). O preço dinâmico continua caindo a cada unidade vendida. Começo e fim vão pro chat, o menu mostra "📈 em alta" no item e na categoria, e `/shop evento` mostra o item, o preço e quanto falta. O evento atual fica salvo no config e sobrevive a reinício.

A staff põe um item em alta na hora com `/shop admin evento <item> <multiplicador> <horas>` (`/shop admin evento parar` encerra).

## Categorias fixas

`categorias` no config ("Nome:ÍCONE") define a ordem e o ícone das categorias do menu, que aparecem mesmo vazias. O `/shop admin additem` sugere essas categorias no Tab, pra não surgir "Minerio" e "Minérios" separados. A categoria que só existe nos itens cadastrados vai depois, com o ícone do primeiro item.

## Bônus do rank (RankupPlugin)

Se o RankupPlugin estiver instalado, toda venda (os vendedores, a venda automática da mina e o `/sell` da staff) é multiplicada pelo bônus do rank do jogador (`multiplicador-venda` no config do Rankup). Com 1.25, ele recebe 25% a mais. O bônus entra antes do imposto, e a mensagem de venda mostra quanto foi. A integração é por reflexão: sem o RankupPlugin, nada muda.

## Integração com as missões (MissoesPlugin)

Se o MissoesPlugin estiver instalado, toda venda pra loja (os vendedores e a venda automática da mina) conta pra missão de vender. As compras também são avisadas, pra que revender no mesmo dia o que acabou de comprar não conte. A integração é por reflexão, como a do Pix: sem o MissoesPlugin, nada muda.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia
- LuckPerms não é necessário aqui (isso é do Skills/Boss)
- PixPlugin é **opcional** — sem ele, a loja funciona normal, só sem unificar o extrato

## Como compilar

```bash
cd economy-shop-plugin
mvn clean package
```

## ⚠️ Simplificações técnicas

1. **"Gráfico" é uma sparkline de texto**, não um gráfico visual de verdade — é a forma prática de mostrar tendência dentro do chat do Minecraft, sem depender de mods de cliente ou interfaces externas.

2. **Categoria é texto livre**: não existe uma lista fixa pré-definida (tipo enum). Isso dá flexibilidade total, mas também significa que "Minerios" e "minerios" (maiúscula diferente) contam como a mesma categoria na exibição (comparação sem diferenciar maiúsculas), mas duas pessoas digitando nomes ligeiramente diferentes (ex: "Minerio" vs "Minerios") criam categorias separadas sem querer — vale manter uma convenção de nomes ao cadastrar itens.

3. **Integração com o Pix via reflexão**: o EconomyShopPlugin não tem dependência de compilação com o PixPlugin — ele só tenta chamar o método por reflexão em tempo de execução, e se o Pix não estiver instalado (ou for uma versão muito diferente), simplesmente ignora sem quebrar nada.

4. **Imposto se aplica só em venda**, não em compra — decisão intencional, já que o objetivo é remover dinheiro de circulação (que entra no sistema quando o servidor "paga" pelo item vendido), não taxar quem tá gastando.

## Arquivos alterados no PixPlugin (aplicar separadamente)

- `ChargeManager.java` — novo método `registrarTransacaoComLoja` + constante `ENTIDADE_LOJA`
- `PixCommand.java` — método `historico()` agora reconhece a `ENTIDADE_LOJA` e mostra "Loja do Servidor"
