# BarracasPlugin

Mercado entre jogadores em **barracas alugadas no spawn**. O jogador paga um aluguel ao servidor por um período (padrão: **6h**) e a barraca ganha um **NPC vendedor com a skin dele**. Clicando no NPC, os outros jogadores compram os itens que ele anunciou (ou vendem pra ele). O estoque fica guardado no plugin, sem baús.

- **Barracas normais**: alugadas por ordem de chegada, pelo preço delas, e podem ser renovadas, inclusive automaticamente.
- **Barracas especiais** (★, maiores e mais perto do spawn): vão a **leilão** toda vez que ficam livres, com lance mínimo igual ao preço da barraca. **Não renovam**, senão o dono atual nunca soltaria e o leilão não aconteceria.

## Comandos

| Comando | Quem | Ação |
|---|---|---|
| `/barraca lista` | todos | Todas as barracas: livres, alugadas e em leilão |
| `/barraca info [id]` | todos | Detalhes de uma barraca (sem id: a barraca onde você está, ou a sua) |
| `/barraca alugar <id>` | todos | Aluga uma barraca livre |
| `/barraca renovar` | dono | Estende o aluguel por mais um período (só barracas normais) |
| `/barraca autorenovar` | dono | Liga ou desliga a renovação automática (só barracas normais) |
| `/barraca lance <id> <valor>` | todos | Dá um lance no leilão de uma barraca especial |
| `/barraca sair confirmar` | dono | Devolve a barraca antes da hora, sem reembolso |
| `/barraca vender <quantidade> <preço>` | dono | Anuncia o item da mão na loja (já leva o que tiver dele no inventário pro estoque) |
| `/barraca comprar <quantidade> <preço>` | dono | A loja passa a comprar o item da mão dos outros jogadores |
| `/barraca loja` | dono | Abre o estoque da loja (o mesmo que clicar no próprio NPC) |
| `/barraca retirar` | todos | Pega o estoque da loja quando o aluguel acabou |
| `/barraca pos1` / `pos2` | staff | Marca os cantos da área (o bloco para onde você está olhando) |
| `/barraca criar <id> [especial]` | staff | Cria a barraca na área marcada |
| `/barraca remover <id>` | staff | Apaga a barraca |
| `/barraca liberar <id>` | staff | Encerra o aluguel atual (os itens vão pra retirada do dono) |
| `/barraca preco <id> <valor\|padrao>` | staff | Preço próprio da barraca (`padrao` volta pro preço do config) |
| `/barraca holograma <id> [remover]` | staff | Põe o holograma de entrada da barraca no bloco onde você está |
| `/barraca npc <id> [remover]` | staff | Marca onde fica o NPC vendedor: no bloco onde você está, olhando pra onde você olha |
| `/barraca reload` | staff | Recarrega o `config.yml` |

Aliases: `/barracas`, `/mercado`.

## A loja (NPC)

Enquanto a barraca está alugada, um NPC com a **skin do dono** e o nome "Loja de Fulano" fica parado no ponto que a staff marcou (`/barraca npc`). Barraca livre não tem NPC: quem anuncia é o holograma de entrada.

**Dono:**
1. Segure o item e rode `/barraca vender 64 150`: cada venda é de 64 unidades por R$ 150,00. O preço aceita `150`, `150,50` ou `1.500,00`. O que você tiver desse item no inventário já vai pro estoque.
2. Pra a loja **comprar** dos outros, segure uma unidade do item e rode `/barraca comprar 64 150`. O pagamento sai da sua conta.
3. Clique no seu NPC (ou use `/barraca loja`) pra ver o estoque:
   - **Esquerdo** num anúncio de venda coloca mais itens do inventário;
   - **Direito** retira o estoque (ou os itens que a loja comprou);
   - **Shift + clique** remove o anúncio. O estoque volta pro inventário, e o que não couber vai pra `/barraca retirar`.

O item anunciado fica fixo desde a criação (com encantamentos, nome etc.). Pra mudar o preço, remova o anúncio e crie de novo. Cada barraca normal tem até **5 anúncios** e a especial até **10**, com até **1.728 unidades** (um baú cheio) de estoque em cada.

**Quem compra:** clique no NPC e no item.
- Num anúncio de venda, compra um lote: o dinheiro vai pro dono (menos a taxa) e os itens vão pro seu inventário.
- Num anúncio de compra, vende um lote: os itens saem do seu inventário e o dinheiro sai da conta do dono (com a mesma taxa, descontada de quem vendeu).
- É recusado se faltar estoque, saldo, espaço no inventário, ou se a loja já comprou o máximo. Nada é cobrado nesses casos.
- O dono recebe aviso de cada venda e de quando o estoque acaba (se estiver online).

## Proteção da área

Dentro de uma barraca:
- A **staff** (`barracas.admin`) pode tudo. É quem constrói e mantém as barracas.
- **Mais ninguém** coloca ou quebra blocos, nem o dono: a loja é o NPC.
- Também ficam bloqueados: funil puxando item de baú, pistão, explosão e balde.

## Holograma de entrada ("Aluga-se")

A staff pode marcar a entrada de cada barraca (dentro ou fora da área): fique no lugar e rode `/barraca holograma <id>`. Ali aparece um holograma que se atualiza sozinho:

```
[Barraca] banca1     [Barraca] banca1     [Barraca ★] central
ALUGA-SE             Dono: Fulano         EM LEILÃO
R$ 500,00            Vence em 5h 20min    R$ 1.250,00
/barraca alugar ...                       /barraca lance ...
```

Clicar nele mostra o `/barraca info`. O tempo restante é atualizado a cada 30s, e a altura do texto fica em `holograma.altura`. O holograma não fica salvo no mundo: aparece quando o chunk carrega. Se a entrada era uma placa (versões antigas), a placa sai sozinha.

## Preço por barraca

Cada barraca pode ter o próprio preço, com `/barraca preco <id> <valor>`: as maiores ou mais bem localizadas podem custar mais. Nas especiais, esse preço é o lance mínimo do leilão. Sem preço próprio, vale o `aluguel.preco` do config. O `/barraca lista` e o `/barraca info` mostram o preço de cada uma.

## Renovação automática

Com `/barraca autorenovar`, o aluguel se renova sozinho no último minuto, mesmo com o dono offline: o preço da barraca sai da conta dele (com o desconto de VIP que ele tinha na última vez que alugou, renovou ou ligou a renovação automática) e o aluguel ganha mais um período. Se faltar saldo, a renovação automática é desligada, o dono é avisado e o aluguel vence normalmente. O aviso de vencimento lembra o valor que vai ser cobrado. Só vale pras barracas normais, e ela é desligada quando a barraca troca de dono.

## Fim do aluguel

- Aviso pro dono 30 min antes de vencer (configurável).
- Quando vence, o NPC some e o estoque de todos os anúncios vai pra **caixa de retirada**.
- A caixa de retirada guarda os itens por **7 dias** (configurável). Depois disso, eles são descartados. Ao entrar no servidor, o jogador é avisado se tem itens esperando.
- A barraca normal fica livre. A especial volta a leilão.

## Leilão (barracas especiais)

- Começa quando a barraca especial fica livre (ao ser criada, quando o aluguel vence ou quando o dono sai). Dura **10 min** (configurável) e é anunciado no chat.
- O primeiro lance precisa ser pelo menos o preço da barraca. Cada lance seguinte precisa ser **5% maior** que o atual (configurável).
- **O valor do lance fica retido na hora.** Quem é superado recebe o valor de volta na mesma hora. Quem já lidera e aumenta o próprio lance paga só a diferença.
- **Anti-snipe**: um lance nos últimos 30s estende o leilão por mais 30s a partir daquele lance.
- Quem vence fica com a barraca por 6h. O valor do lance vai pro servidor, ou seja, some da economia.
- Se ninguém der lance, a barraca fica livre pra alugar pelo preço mínimo, por ordem de chegada, até a próxima vez que ficar livre.
- Lances e leilões são salvos em disco. Reiniciar o servidor no meio de um leilão não perde dinheiro de ninguém.
- Com o DiscordPlugin, a abertura e o resultado do leilão vão para a categoria `leiloes`.

## Regras de uma barraca por jogador

- Cada jogador só pode ter **uma** barraca alugada.
- Quem tem uma barraca não pode dar lance. Quem está liderando um leilão não pode alugar outra barraca.

## Configuração

```yaml
aluguel:
  preco: 500.0                 # preço padrão (/barraca preco muda por barraca); nas especiais, é o lance mínimo
  duracao-horas: 6
  renovacao-maxima-horas: 12   # até quanto tempo dá pra acumular renovando
  aviso-minutos-antes: 30
barracas:
  normal:   { max-anuncios: 5 }
  especial: { max-anuncios: 10 }
  estoque-por-anuncio: 1728    # unidades do item em cada anúncio
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
- aluguel, renovação e renovação automática, como "Loja do Servidor";
- leilão vencido;
- compras e vendas nas lojas das barracas, no extrato de quem comprou e de quem vendeu.

O registro no Pix é por reflexão, igual ao da EconomyShop. O plugin funciona normalmente sem o Pix.

## Configuração inicial (passo a passo)

1. Construa as barracas no spawn.
2. Pra cada uma, olhe pra um canto e rode `/barraca pos1`. Depois olhe pro canto oposto, do piso até o teto, e rode `/barraca pos2`.
3. Crie com `/barraca criar banca1` (normal) ou `/barraca criar central especial` (a especial já entra em leilão).
4. Ajuste o preço e as durações no `config.yml` e rode `/barraca reload`. Pra dar um preço diferente a uma barraca, use `/barraca preco <id> <valor>`.
5. Fique atrás do balcão de cada barraca, olhando pro caminho, e rode `/barraca npc <id>`.
6. (Opcional) Fique na entrada de cada barraca e rode `/barraca holograma <id>`.

O spawn do EstruturasPlugin (`/estrutura construir spring`) faz esses passos sozinho para as barracas dele. Pelo console, os comandos de staff que pedem posição aceitam o lugar depois de `em`:
```
barraca criar <id> [especial] em <mundo> <x1> <y1> <z1> <x2> <y2> <z2>
barraca npc <id> em <mundo> <x> <y> <z> <yaw>
barraca holograma <id> em <mundo> <x> <y> <z>
```

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

1. **A renovação automática é do próprio plugin**, não do `/pix agendar` (que ainda não existe). Quando o Pix tiver pagamento agendado, dá pra migrar.
2. **As compras nas barracas não contam pro limite diário do Pix.** Elas não passam pelo `/pix pagar`.
3. **O aviso de vencimento não é salvo em disco.** Se o servidor reiniciar nos últimos 30 min do aluguel, o dono pode receber o aviso duas vezes.
4. **Uma área por barraca, em forma de caixa.** Barraca em formato de L precisa ser feita como duas barracas.
5. **Atualizando da versão com baús:** as placas `[Vender]`/`[Comprar]` param de funcionar. Os baús que o inquilino já tinha colocado continuam abrindo pra ele, e quando o aluguel acaba o conteúdo deles vai pra retirada, como antes.
