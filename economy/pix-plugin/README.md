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
| `/pix historico [quantidade]` | Mostra suas últimas transações (padrão: 10, máx: 20) |
| `/pix qrcode <valor> [motivo]` | Anuncia publicamente um pagamento — qualquer um pode clicar para te pagar |
| `/pix admin reload` | (staff) Recarrega o `config.yml` (ex: depois de mudar a taxa) |

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

**Simplificação atual:** o histórico (`/pix historico`) ainda registra o valor bruto da transação, não o valor líquido recebido — a taxa aparece só na mensagem de chat no momento da transação, não fica gravada por transação individual no extrato. Se quiser ver a taxa no histórico também, dá pra estender `TransacaoRegistro` pra guardar os dois valores; é só pedir.

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

Cobranças pendentes e histórico (`/pix historico`) ficam salvos em `plugins/PixPlugin/dados.yml`, gravado a cada mudança e também no desligamento. Por isso sobrevivem a reinício ou queda do servidor:

- A cobrança volta com o **tempo de expiração que faltava**. Se ela foi criada 2 min antes do restart, expira 3 min depois que o servidor subir.
- Uma cobrança que venceu com o servidor desligado é descartada ao carregar, sem notificação.
- O histórico mantém o mesmo limite de 20 transações por jogador.

## Ideias para expandir (ainda não implementadas)

- **`/pix chave <apelido>`** — jogador registra um "apelido" (tipo chave Pix) pra receber pagamentos sem precisar do nome exato
- **`/pix agendar <jogador> <valor> <intervalo>`** — pagamentos recorrentes automáticos (ex: aluguel de terreno toda semana)
- **`/pix limite`** — limite diário de transferência, pra evitar abuso/fraude entre contas
- **Notificação por som/título** — tocar um som e mostrar um título na tela quando alguém recebe um pagamento ou cobrança
- **Log administrativo** — comando `/pix admin log <jogador>` pra staff investigar transações suspeitas
