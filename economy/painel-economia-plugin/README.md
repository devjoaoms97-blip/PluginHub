# PainelEconomiaPlugin

Painel da economia pra staff: quanto dinheiro existe no servidor, quanto **entrou e saiu por dia** (separado pelo plugin que movimentou) e quem tem as maiores fortunas. É o que dá números pra calibrar taxas, preços e recompensas: imposto da loja, taxa do Pix, aluguel das barracas, recompensa das missões.

## Comandos

Todos exigem `painel.economia` (padrão: op). Aliases: `/ecostats`, `/painel`.

| Comando | O que mostra |
|---|---|
| `/economia` | Total em circulação (e a variação desde ontem), contas com saldo, média e mediana por conta, concentração (10 maiores e Gini), o fluxo de hoje por origem e o líquido por hora jogada |
| `/economia dias [n]` | Entrou, saiu e líquido de cada um dos últimos `n` dias (padrão 7, máximo 30), com a circulação, os jogadores e as horas jogadas daquele dia |
| `/economia tipos <plugin> [n]` | O fluxo de um plugin separado por tipo (na loja: compra e venda; nas barracas: aluguel, leilão, compras...), de hoje ou dos últimos `n` dias |
| `/economia top [n]` | Maiores saldos e quanto cada um representa do total |
| `/economia recalcular` | Recalcula a circulação agora, ignorando o cache |

O comando é `/economia`, e não `/eco`, porque `/eco` já é um comando do EssentialsX.

Exemplo do resumo:
```
--- Economia ---
Em circulação: R$ 1.250.000,00 (+R$ 17.855,00 desde ontem)
Contas com saldo: 312 | Média: R$ 4.006,41
Mediana: R$ 850,00 | 10 maiores: 41% do total | Gini: 0.72
Hoje: +R$ 52.355,00 -R$ 34.500,00 = +R$ 17.855,00
  48 jogadores, 96.5 h jogadas | líquido por hora jogada: +R$ 185,03
  EconomyShopPlugin: +R$ 40.000,00 -R$ 25.000,00 = +R$ 15.000,00
  SkillsPlugin: +R$ 6.000,00 -R$ 0,00 = +R$ 6.000,00
  BarracasPlugin: +R$ 1.900,00 -R$ 5.000,00 = -R$ 3.100,00
  PixPlugin: +R$ 4.455,00 -R$ 4.500,00 = -R$ 45,00
```
O Pix sai negativo porque o líquido dele é a taxa: quem recebe ganha o valor menos a taxa, e quem paga perde o valor cheio.

## Como funciona

**Fluxo por origem.** O painel se coloca na frente da economia do Vault (EssentialsX, por exemplo) como um intermediário. Ele repassa tudo pra economia real e anota cada depósito e cada saque que deu certo, junto com o plugin que fez a chamada. Esse plugin é descoberto pelo stack de execução. **Nenhum outro plugin precisou ser alterado.**

- Uma **entrada** é dinheiro depositado na conta de alguém. Uma **saída** é dinheiro tirado.
- Uma transferência entre jogadores (Pix, compra na barraca) aparece nas duas colunas. O **líquido** mostra o efeito real: no Pix, o líquido é só a taxa (negativo).
- Um líquido positivo é dinheiro novo entrando na economia (venda pra loja, level up de skill, missão). Um líquido negativo é dinheiro saindo de circulação (imposto, taxa, aluguel, compra da loja).

**Tipos.** O mesmo stack de execução mostra *de onde* dentro do plugin veio a chamada (a classe e o método). O `config.yml` traduz isso pra um nome legível: `BuyCommand#onCommand` vira "Compra (/buy)". Exemplo:
```
--- EconomyShopPlugin hoje ---
  Venda (menu): +R$ 31.000,00 -R$ 0,00 = +R$ 31.000,00
  Compra (menu): +R$ 0,00 -R$ 18.000,00 = -R$ 18.000,00
  Venda (/sell all): +R$ 9.000,00 -R$ 0,00 = +R$ 9.000,00
```
Os tipos dos plugins do PluginHub já vêm com nome. Um tipo sem nome aparece como `Classe#metodo`. Pra dar nome a ele, copie o que aparecer e adicione em `tipos.<Plugin>` no config.

**Alerta de crescimento.** A cada 5 min, o painel compara o dinheiro novo do dia (entrou − saiu) com a circulação de ontem. Se passar de `alerta.percentual` (10%) **e** de `alerta.minimo` (R$ 50.000), a staff online (`painel.economia`) e o console recebem um aviso, com os plugins que mais colocaram dinheiro. O alerta sai uma vez por dia e não aparece no primeiro dia, porque ainda não existe a circulação de ontem.

**Circulação.** É a soma do saldo de todo jogador que já entrou no servidor. Fica em cache por 5 min (configurável), porque com muitas contas pode ser lento. Uma "foto" do total é guardada por dia, pra comparar.

**Distribuição.** Junto com a circulação, o painel guarda como o dinheiro está dividido, contando só as contas com saldo:
- a **mediana**, o saldo de quem está no meio. Ela não é puxada pelos ricos como a média;
- a fatia das **10 maiores** contas;
- o **Gini**, de 0 (todos têm o mesmo) a 1 (uma conta tem tudo).

**Atividade.** Por dia, quantos jogadores diferentes entraram e quantas horas jogaram somando todos (contadas de 5 em 5 min). Dividindo o dinheiro novo pelas horas, sai o **líquido por hora jogada**: é o número que diz se a economia está inflando por causa de mais gente jogando (normal) ou porque cada hora de jogo rende mais (sinal de exploit ou de preço mal calibrado).

Os dados ficam em `plugins/PainelEconomiaPlugin/historico.yml`, com os últimos 90 dias (configurável). Ele também é a entrada da análise periódica da economia (veja `tools/economia`). O fluxo é gravado a cada 5 min e quando o servidor desliga.

## Requisitos e ordem de carregamento

- Paper 26.2 (Java 25)
- **Vault** + um plugin de economia

Pra um plugin aparecer no fluxo por origem, ele precisa pegar a economia **depois** que o painel se colocou na frente dela. Por isso o `plugin.yml` tem um `loadbefore` com os plugins do PluginHub: Pix, Loja, Cheque, VIP, Barracas, Skills, Boss, Missões, Bounty, Clãs e Rankup. **Um plugin novo que mexe com dinheiro precisa entrar nessa lista.**

Um plugin de fora da lista, ou um comando que não passa pelo Vault (como o `/pay` e o `/eco give` do EssentialsX), não aparece no fluxo. Mesmo assim, o efeito dele entra no total em circulação.

## Configuração

```yaml
circulacao:
  cache-minutos: 5
  top-maximo: 20
historico:
  dias-guardados: 90
alerta:
  ativado: true
  percentual: 10.0
  minimo: 50000.0
tipos:
  EconomyShopPlugin:
    "BuyCommand#onCommand": "Compra (/buy)"
    # ... (todos os plugins do PluginHub já vêm configurados)
```

## Como compilar

```bash
cd economy/painel-economia-plugin
mvn clean package
```

O `.jar` sai em `target/PainelEconomiaPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **Se a economia já estiver registrada no Vault com prioridade máxima**, o painel não consegue se colocar na frente dela. Ele avisa no console e mostra só a circulação.
2. **O fluxo é gravado a cada 5 min.** Se o servidor cair (não desligar normalmente), perde-se no máximo esses 5 min de registro. O dinheiro dos jogadores não é afetado, só a estatística.
3. **A origem é o primeiro plugin no stack de execução.** Se um plugin chamar outro que mexe no dinheiro, a movimentação conta pro plugin que fez a chamada de fato.
4. **O tipo depende do nome da classe e do método.** Se um plugin for reescrito e o método mudar de nome, o tipo passa a aparecer como `Classe#metodo` até o config ser atualizado. Os dias anteriores ao painel ter tipos só mostram o total por plugin.
5. **O alerta usa o fluxo registrado**, não a circulação recalculada. Dinheiro que entra por fora do Vault (`/eco give`) não dispara o alerta.
