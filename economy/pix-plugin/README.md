# PixPlugin

Sistema de pagamentos entre jogadores, com pagamento direto e cobranças aceitáveis/recusáveis, inspirado no Pix.

## Comandos

| Comando | Descrição |
|---|---|
| `/pix pagar <jogador> <valor> [motivo]` | Paga alguém diretamente |
| `/pix receber <jogador> <valor> [motivo]` | Envia uma cobrança (o jogador precisa aceitar). Só é possível ter **uma cobrança ativa por vez** para o mesmo jogador. Expira sozinha em **5 minutos**. |
| `/pix aceitar <jogador>` | Aceita e paga a cobrança enviada por esse jogador |
| `/pix recusar <jogador>` | Recusa a cobrança enviada por esse jogador |
| `/pix cancelar <jogador>` | Cancela a cobrança que você enviou para esse jogador |
| `/pix pendentes` | Lista cobranças enviadas e recebidas em aberto |
| `/pix qrcode <valor> [motivo]` | Anuncia publicamente um pagamento — qualquer um pode clicar para te pagar |
| `/pix limite` | Mostra seu limite diário, quanto já enviou hoje e quanto ainda pode enviar |
| `/pix agendar <jogador> <valor> <dias> [motivo]` | Paga agora e repete a cada N dias, sozinho (até 5 agendamentos por jogador, de 1 a 30 dias) |
| `/pix agendamentos [cancelar <nº>]` | Seus Pix agendados, pagando ou recebendo. Os dois lados podem cancelar |
| `/pix admin reload` | (staff) Recarrega o `config.yml` (ex: depois de mudar a taxa ou o limite) |
| `/pix admin resetlimite <jogador>` | (staff) Zera o que um jogador já enviou hoje |
| `/pix admin vencer <nº>` | (staff) Faz um Pix agendado vencer agora (para testar) |

### Extrato

O histórico de transações não é mais um comando: o jogador pega o **extrato com o Bancário** (ChequePlugin), um livro com as últimas movimentações. Ele vem daqui (`PixPlugin#extrato`), porque quase todo movimento de dinheiro do servidor é registrado neste histórico: Pix, loja, oficinas, rankup, terrenos, barracas, Bounty, Duelo, Gladiador, clã, missões, diária, máquinas, spawners, cheques, Mine-sena, boss, VIP e achados e perdidos. A venda automática nas minas fica de fora (seria uma linha por bloco). `/pix historico` só avisa pra procurar o Bancário, e a staff vê o de qualquer um com `/pix admin log <jogador>`. Ficam as últimas 50 transações de cada jogador.

### Chave Pix

`/pix chave <apelido>` cadastra um apelido (3 a 20 letras, números, `_ . -`) que vale no lugar do nome no `/pix pagar`, `receber`, `agendar` e `admin log`. O apelido é único e não pode ser o nome de outro jogador que já entrou. `/pix chave` mostra a sua e `/pix chave remover` apaga. Ficam em `chaves.yml`.

### Aviso ao receber

Quem recebe um Pix (pagamento, cobrança aceita ou agendado) ganha um título na tela com o valor e um som, além da mensagem. Desliga em `notificacao.titulo` e `notificacao.som`.

### Extrato com a taxa, e o da staff

O extrato mostra a taxa de cada transação: quem pagou vê o valor cheio, e quem recebeu vê o que entrou de fato, os dois com `[taxa R$ x]`. A staff vê o extrato de qualquer um com `/pix admin log <jogador|chave> [qtd]` (permissão `pix.admin`).

### Alerta de Pix suspeito

Depois de cada Pix entre jogadores (pagamento, cobrança aceita e agendado), a staff é avisada quando:

- **Pix alto:** o valor é de `discord.pix-suspeito-valor` pra cima (padrão R$ 100.000; 0 desliga);
- **Perto do limite:** o envio deixou o pagador com `alertas.perto-do-limite-percentual` (90%) do limite diário ou mais. É o jeito de esvaziar uma conta roubada sem bater no limite. Só vale com `limite.diario` ligado, e quem tem `pix.limite.ignorar` fica de fora;
- **Conta nova recebendo de várias:** uma conta que entrou pela primeira vez há menos de `alertas.conta-nova-horas` (48 h) recebeu de `alertas.remetentes-distintos` (3) contas diferentes nas últimas 24 h. É o padrão de contas fantasma juntando dinheiro numa só.

O alerta aparece no chat de quem tem `pix.alertas` (padrão: op), vai pra categoria `pix` do DiscordPlugin e fica em `plugins/PixPlugin/alertas.log`. O mesmo alerta sobre o mesmo jogador não repete por 10 minutos. Quem recebeu de quem fica só na memória: um reinício zera a contagem das 24 h.

## Taxa de transação (sinkhole)

Toda transação entre jogadores (`/pix pagar` e cobranças aceitas via `/pix aceitar`) agora desconta uma pequena taxa, configurável em `config.yml`:

```yaml
taxa:
  percentual-transacao: 1.0   # % descontado de cada transação
  minimo: 0.0                 # taxa mínima em R$, opcional
```

- Quem paga (`/pix pagar`) sempre perde o valor cheio da conta.
- Quem recebe fica com `valor − taxa`. O dinheiro da taxa **desaparece** da economia — não vai pro servidor nem pra ninguém — funcionando como mais um sink, igual ao imposto sobre venda do `EconomyShopPlugin`.
- `0` em `percentual-transacao` desativa a taxa completamente.
- `/pix qrcode` usa o mesmo caminho de código de `/pix pagar` (é um botão clicável que roda `/pix pagar`), então já sai taxado automaticamente sem nenhuma mudança extra.

No extrato, quem pagou vê o valor cheio e quem recebeu vê o que entrou de fato, com a taxa ao lado.

## Desconto de VIP na taxa

Quem **paga** (no `/pix pagar` ou ao aceitar uma cobrança) e tem VIP paga menos taxa: 20% de desconto no Bronze, 40% no Prata, 60% no Ouro e 80% no Diamante. O desconto vem por permissão (`vip.desconto.<tier>`, as mesmas do desconto da loja), configurado em `taxa.vip-descontos`. Vale o maior que o jogador tiver.

## Limite diário de transferência

Teto de quanto cada jogador pode **enviar** por dia, contra fraude entre contas (por exemplo, uma conta roubada sendo esvaziada de uma vez):

```yaml
limite:
  diario: 0   # 0 = desativado (padrão)
```

- Conta o que sai em `/pix pagar` e `/pix aceitar`, inclusive pelos botões do `/pix qrcode`, sempre pelo **valor bruto**, antes da taxa. Receber dinheiro não conta, e compra na loja também não.
- Um envio que passaria do teto é recusado inteiro, e a mensagem mostra quanto ainda dá pra mandar. Se for uma cobrança, ela **continua pendente** (até expirar nos 5 min).
- Zera à meia-noite, no horário do servidor. O total do dia fica salvo em `plugins/PixPlugin/limites.yml`, então reiniciar o servidor não zera o limite.
- Quem tem `pix.limite.ignorar` não tem limite (padrão: op). A staff pode zerar o dia de alguém com `/pix admin resetlimite <jogador>`, por exemplo pra liberar uma transação grande e legítima.
- **Vem desativado** porque o valor certo depende da escala da economia do servidor. Ligue em `config.yml` e rode `/pix admin reload`.

## Requisitos

- Paper 26.2 (Java 25)
- Vault
- Um plugin de economia compatível (ex: EssentialsX)

## Como compilar

```bash
cd pix-plugin
mvn clean package
```

O `.jar` sai em `target/PixPlugin.jar`.

## Persistência

Cobranças pendentes e o histórico (o extrato do Bancário) ficam salvos em `plugins/PixPlugin/dados.yml`, gravado a cada mudança e também no desligamento. Por isso sobrevivem a reinício ou queda do servidor:

- A cobrança volta com o **tempo de expiração que faltava**. Se ela foi criada 2 min antes do restart, expira 3 min depois que o servidor subir.
- Uma cobrança que venceu com o servidor desligado é descartada ao carregar, sem notificação.
- O histórico guarda as últimas 50 transações de cada jogador.

## Transações de outros plugins no histórico

Outros plugins do PluginHub registram as próprias transações no histórico (o extrato do Bancário), chamando o `ChargeManager` por reflexão:

| Método | Aparece como | Quem usa |
|---|---|---|
| `registrarTransacao(de, para, valor, tipo)` | o nome do outro jogador | Barracas (compras entre jogadores) |
| `registrarTransacaoComLoja(jogador, valor, recebeu, tipo)` | "Loja do Servidor" | Loja, Barracas, Missões, Bounty, Clãs (criação) |
| `registrarTransacaoComEntidade(jogador, nome, valor, recebeu, tipo)` | o `nome` passado, ex: "Clã [ABC]" | Clãs (depósito e saque do banco) |

A entidade não é uma conta de verdade: a transação só entra no extrato do jogador. No `dados.yml`, o nome fica no campo `entidade` de cada registro.

## Pix agendado

O primeiro pagamento sai na hora, com as mesmas regras do `/pix pagar`: saldo, limite diário e taxa. Os próximos saem sozinhos no vencimento, conferido a cada 20 s, mesmo com os dois offline. Com quem paga offline, não dá para ver as permissões, então a taxa sai sem o desconto de VIP.

Sem saldo ou sem limite, o pagamento não sai, quem paga é avisado e o plugin tenta de novo em 1 h. Depois de 3 falhas seguidas (`agendamento.falhas-maximas`), o agendamento é cancelado e os dois são avisados. Os agendamentos ficam em `agendamentos.yml`.

O imposto do terreno e o aluguel das barracas já têm o próprio pagamento automático (`/terreno autopagar` e `/barraca autorenovar`). O Pix agendado é para pagamentos combinados entre jogadores.

## Ideias para expandir (ainda não implementadas)

- **`/pix chave <apelido>`** — jogador registra um "apelido" (tipo chave Pix) pra receber pagamentos sem precisar do nome exato
- **Notificação por som/título** — tocar um som e mostrar um título na tela quando alguém recebe um pagamento ou cobrança
- **Log administrativo** — comando `/pix admin log <jogador>` pra staff investigar transações suspeitas
