# Bot do Discord da BrasaMC

Bot em Node ([discord.js](https://discord.js.org) 14) que roda **separado do servidor do Minecraft**: no PC por enquanto, na VPS depois. Ele fala com o servidor pela API local do [DiscordPlugin](../discord-plugin/README.md). Os avisos do jogo (boss, leilões...) continuam saindo pelos webhooks do plugin, mesmo com o bot desligado.

## O que faz

| Recurso | Como |
|---|---|
| **Vínculo de conta** | No jogo, `/vincular` dá um código de 6 letras (10 min). No Discord, `/vincular <código>` ou o botão do painel em `#vincular-conta`. Uma conta do Discord por conta do Minecraft |
| **Presente do vínculo** | 5 bilhetes grátis da Mine-sena, uma vez por conta do Minecraft e uma por conta do Discord, e só pra conta do Discord com 30 dias ou mais (`IDADE_MINIMA_DIAS`) |
| **Cargos sincronizados** | 🔗 Vinculado e o cargo do VIP (Bronze a Diamante) seguem o servidor a cada minuto e na entrada no Discord. Dar ou tirar esses cargos à mão não adianta: a sincronia desfaz |
| **Apelido** | Vira o nick do jogo no vínculo e quando o nick muda. Depois disso o membro pode trocar (o dono do servidor o bot não consegue renomear) |
| **`/conta [membro]`** | Mostra a conta vinculada e o VIP |
| **`/desvincular <membro>`** | Staff (quem pode gerenciar cargos). Tudo vai pro `#log-vinculos` |
| **Atividade** | "Jogando BrasaMC · N online", ou "servidor desligado" |

Com o servidor do Minecraft desligado, o vínculo avisa que ele está fora do ar, e a sincronia não mexe em nenhum cargo (nunca tira cargo por falta de resposta).

## Rodar

```bash
cd discord-bot
npm install
npm start
```

A configuração fica fora do repositório, em `C:\mc-teste\discord\` (ou na pasta de `DISCORD_PASTA`):

```
.env
  DISCORD_TOKEN=...            token do bot (Developer Portal)
  GUILD_TESTE=...              ID do servidor Discord de teste
  GUILD_OFICIAL=...            ID do oficial
  BOT_SERVIDOR=teste           em qual dos dois o bot trabalha
  PLUGIN_API_URL=http://127.0.0.1:8155
  PLUGIN_API_TOKEN=...         o api.token do config.yml do DiscordPlugin (gerado na 1ª vez)
  IDADE_MINIMA_DIAS=30
estado-<servidor>.json         IDs dos cargos e canais, do tools/discord/estrutura.js
bot-<servidor>.json            o que o bot guarda (mensagem do painel, apelidos aplicados)
```

O bot precisa dos intents **Server Members** e do cargo dele acima dos cargos que controla (o `estrutura.js` já deixa assim).

## Testar

`tools/bots/teste-vinculo.js` testa o vínculo de ponta a ponta sem o Discord: um bot entra no jogo, gera o código e o teste chama a API como o bot faria.

## Próximas etapas

Ponte de chat (`#chat-do-jogo`), tickets, `#status` e tutoriais, e a loja de créditos com Mercado Pago.
