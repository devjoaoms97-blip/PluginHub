# EconomyShopPlugin

Loja do servidor com preço de venda dinâmico (oferta e demanda), categorias, histórico de preços, imposto sobre vendas, e integração com o extrato do Pix.

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
| `/shop` | Abre a loja (agora com tela de categorias) |
| `/shop historico <item>` | Mostra o gráfico de tendência (sparkline) das últimas 24h |
| `/buy <item> <quantidade>` | Compra pelo preço fixo |
| `/sell` | Vende o item na mão |
| `/sell all` | Vende tudo do inventário que a loja compra |
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
