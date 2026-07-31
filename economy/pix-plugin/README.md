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

## ⚠️ Limitação importante

As cobranças e o histórico ficam **em memória** — se o servidor reiniciar, cobranças pendentes são perdidas. Se isso for um problema pra você (ex: jogador cria uma cobrança e o servidor cai antes dele aceitar), dá pra evoluir isso pra salvar num arquivo YAML ou banco de dados. É só pedir.

## Ideias para expandir (ainda não implementadas)

- **`/pix chave <apelido>`** — jogador registra um "apelido" (tipo chave Pix) pra receber pagamentos sem precisar do nome exato
- **`/pix agendar <jogador> <valor> <intervalo>`** — pagamentos recorrentes automáticos (ex: aluguel de terreno toda semana)
- **`/pix limite`** — limite diário de transferência, pra evitar abuso/fraude entre contas
- **Persistência** — salvar cobranças e histórico em arquivo, sobrevivendo a reinícios do servidor
- **Taxa de transação** — cobrar uma pequena porcentagem em cada pix, como uma sinkhole de economia (ajuda a controlar inflação no servidor)
- **Notificação por som/título** — tocar um som e mostrar um título na tela quando alguém recebe um pagamento ou cobrança
- **Log administrativo** — comando `/pix admin log <jogador>` pra staff investigar transações suspeitas
