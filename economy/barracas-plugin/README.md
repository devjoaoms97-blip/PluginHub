# BarracasPlugin

Mercado entre jogadores em **barracas alugadas no spawn**. O jogador paga um aluguel ao servidor por um período (padrão: **6h**) e, dentro da barraca, vende os próprios itens com **placas em baús**.

- **Barracas normais**: alugadas por ordem de chegada, pelo preço de tabela, e podem ser renovadas.
- **Barracas especiais** (★, maiores e mais perto do spawn): vão a **leilão** toda vez que ficam livres, com lance mínimo igual ao preço das normais. **Não renovam**, senão o dono atual nunca soltaria e o leilão não aconteceria.

## Comandos

| Comando | Quem | Ação |
|---|---|---|
| `/barraca lista` | todos | Todas as barracas: livres, alugadas e em leilão |
| `/barraca info [id]` | todos | Detalhes de uma barraca (sem id: a barraca onde você está, ou a sua) |
| `/barraca alugar <id>` | todos | Aluga uma barraca livre |
| `/barraca renovar` | dono | Estende o aluguel por mais um período (só barracas normais) |
| `/barraca lance <id> <valor>` | todos | Dá um lance no leilão de uma barraca especial |
| `/barraca sair confirmar` | dono | Devolve a barraca antes da hora, sem reembolso |
| `/barraca retirar` | todos | Pega os itens que estavam nos baús quando o aluguel acabou |
| `/barraca pos1` / `pos2` | staff | Marca os cantos da área (o bloco para onde você está olhando) |
| `/barraca criar <id> [especial]` | staff | Cria a barraca na área marcada |
| `/barraca remover <id>` | staff | Apaga a barraca |
| `/barraca liberar <id>` | staff | Encerra o aluguel atual (os itens vão pra retirada do dono) |
| `/barraca reload` | staff | Recarrega o `config.yml` |

Aliases: `/barracas`, `/mercado`.

## Montando a loja (dono)

1. Coloque um **baú** (ou barril) dentro da barraca, com o item que quer vender.
2. Coloque uma **placa** presa no baú (na lateral) ou em cima dele e escreva:
   ```
   [Vender]
   64
   150
   ```
   A 1ª linha é `[Vender]`, a 2ª é a quantidade por venda e a 3ª (ou a 4ª) é o preço. O preço aceita `150`, `150,50` ou `R$ 1.500,00`.
3. O plugin reescreve a placa como `[Vender] / 64x / diamond / R$ 150,00` e a encera, pra ninguém editar o texto.

O **item à venda fica gravado na placa** no momento em que ela é criada, a partir do primeiro item do baú. Trocar o conteúdo do baú depois não muda o que a placa vende. Pra mudar o item ou o preço, quebre a placa e coloque outra.

**Quem compra:**
- **Botão direito** na placa compra: o dinheiro vai pro dono (menos a taxa) e os itens vão pro inventário de quem comprou.
- **Botão esquerdo** só mostra as informações e o estoque, em número de vendas disponíveis.
- A compra é recusada se faltar estoque, saldo ou espaço no inventário. Nada é cobrado nesses casos.
- O dono recebe aviso de cada venda e de quando o estoque acaba (se estiver online).

## Proteção da área

Dentro de uma barraca:
- A **staff** (`barracas.admin`) pode tudo. É quem constrói e mantém as barracas.
- O **dono** só coloca e quebra **baús, barris e placas** (até o máximo de baús) e é o único que abre os baús. A construção da staff fica intacta.
- **Mais ninguém** mexe em nada.
- Também ficam bloqueados: funil puxando item de baú, pistão, explosão e balde.

## Fim do aluguel

- Aviso pro dono 30 min antes de vencer (configurável).
- Quando vence, as placas param de vender. O conteúdo dos baús do dono vai pra **caixa de retirada**, e os baús e placas dele saem da barraca.
- A caixa de retirada guarda os itens por **7 dias** (configurável). Depois disso, eles são descartados. Ao entrar no servidor, o jogador é avisado se tem itens esperando.
- A barraca normal fica livre. A especial volta a leilão.

## Leilão (barracas especiais)

- Começa quando a barraca especial fica livre (ao ser criada, quando o aluguel vence ou quando o dono sai). Dura **10 min** (configurável) e é anunciado no chat.
- O primeiro lance precisa ser pelo menos o preço das normais. Cada lance seguinte precisa ser **5% maior** que o atual (configurável).
- **O valor do lance fica retido na hora.** Quem é superado recebe o valor de volta na mesma hora. Quem já lidera e aumenta o próprio lance paga só a diferença.
- **Anti-snipe**: um lance nos últimos 30s estende o leilão por mais 30s a partir daquele lance.
- Quem vence fica com a barraca por 6h. O valor do lance vai pro servidor, ou seja, some da economia.
- Se ninguém der lance, a barraca fica livre pra alugar pelo preço mínimo, por ordem de chegada, até a próxima vez que ficar livre.
- Lances e leilões são salvos em disco. Reiniciar o servidor no meio de um leilão não perde dinheiro de ninguém.

## Regras de uma barraca por jogador

- Cada jogador só pode ter **uma** barraca alugada.
- Quem tem uma barraca não pode dar lance. Quem está liderando um leilão não pode alugar outra barraca.

## Configuração

```yaml
aluguel:
  preco: 500.0                 # normais; também é o lance mínimo das especiais
  duracao-horas: 6
  renovacao-maxima-horas: 12   # até quanto tempo dá pra acumular renovando
  aviso-minutos-antes: 30
barracas:
  normal:   { max-baus: 4 }
  especial: { max-baus: 8 }
leilao:
  duracao-minutos: 10
  incremento-minimo-percentual: 5
  anti-snipe-segundos: 30
vendas:
  taxa-percentual: 5.0         # descontado do dono a cada venda; some da economia
retirada:
  dias-guardado: 7
vip-descontos: ...             # desconto no aluguel (não no lance), mesmo formato da EconomyShop
integracao:
  pix: true
```

## Integração com o Pix

Com o PixPlugin instalado, aparecem no `/pix historico`:
- aluguel e renovação, como "Loja do Servidor";
- leilão vencido;
- compras nas barracas, no extrato de quem comprou e de quem vendeu.

O registro no Pix é por reflexão, igual ao da EconomyShop. O plugin funciona normalmente sem o Pix.

## Configuração inicial (passo a passo)

1. Construa as barracas no spawn.
2. Pra cada uma, olhe pra um canto e rode `/barraca pos1`. Depois olhe pro canto oposto, do piso até o teto, e rode `/barraca pos2`.
3. Crie com `/barraca criar banca1` (normal) ou `/barraca criar central especial` (a especial já entra em leilão).
4. Ajuste o preço e as durações no `config.yml` e rode `/barraca reload`.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + um plugin de economia (sem isso, ninguém aluga, dá lance ou compra)
- PixPlugin (opcional, pro histórico)

## Como compilar

```bash
cd economy/barracas-plugin
mvn clean package
```

O `.jar` sai em `target/BarracasPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **Só placa de venda (`[Vender]`).** A placa `[Comprar]`, em que o dono paga quem traz o item, ainda não existe.
2. **As compras nas barracas não contam pro limite diário do Pix.** Elas não passam pelo `/pix pagar`.
3. **O aviso de vencimento não é salvo em disco.** Se o servidor reiniciar nos últimos 30 min do aluguel, o dono pode receber o aviso duas vezes.
4. **Uma área por barraca, em forma de caixa.** Barraca em formato de L precisa ser feita como duas barracas.
5. **Placa de loja só funciona presa no baú** (na lateral) ou logo em cima ou embaixo dele. Placa solta perto do baú não vale.
