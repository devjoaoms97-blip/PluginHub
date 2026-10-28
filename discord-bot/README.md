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
| **`#status`** | Uma mensagem só, editada a cada minuto: online ou offline (e desde quando), o IP (`SERVIDOR_IP`), quem está jogando, o próximo boss, o pote da Mine-sena e o TPS quando cai. Se outra mensagem cair no canal, o painel é reenviado pra ficar por último |
| **Textos fixos** | Boas-vindas, regras e um post por tutorial no fórum `#tutoriais`, escritos em `conteudo/*.md` (veja abaixo). Publicados uma vez e editados quando o arquivo muda |
| **Cargos de aviso** | Botões na mensagem de boas-vindas: 🐉 Boss, ⚔️ Gladiador, ⚖️ Leilões e 📢 Novidades. Clicou, ganha o cargo; de novo, tira. Os avisos do DiscordPlugin marcam esses cargos |
| **Tickets** | Painel em `#abrir-ticket` com um menu: Dúvida, Bug, Denúncia, Pagamento, Apelação e Outro. Cada tipo abre um formulário (o nick vem do vínculo; sem vínculo, o formulário pergunta) e cria um canal privado em "📂 TICKETS ABERTOS", que só o dono e a staff veem. Os de **Pagamento**, só a direção. Até 2 abertos por pessoa |
| **Loja de créditos** | Painel em `#loja-vip` com 4 pacotes (100 créditos por R$ 10, 260 por R$ 25, 550 por R$ 50 e 1.150 por R$ 100). O botão dá um link do Mercado Pago (Checkout Pro: **Pix ou cartão**) que vale 1 hora; precisa da conta vinculada. Até 3 links abertos por pessoa |
| **Entrega** | A cada 20 s o bot procura os pagamentos dos pedidos abertos. Aprovado (e com o valor certo), pede a entrega ao servidor (`POST /entregar`), que roda `creditos dar` na conta vinculada e anota o ID do pagamento: nunca entrega duas vezes. Servidor desligado: tenta de novo até dar. A venda vai pro `#log-vendas` e o comprador recebe uma DM. Valor diferente ou entrega com erro viram alerta no `#log-vendas` |
| **Atender** | A staff marca que assumiu com **Assumir**. O dono ou a staff fecha com **Fechar** e uma resolução opcional: a conversa vai em `.txt` pro `#log-tickets` (com quem abriu, assumiu, fechou e quanto durou), o dono recebe na DM só o aviso com a resolução, e o canal some em 5 s |

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
  SERVIDOR_IP=jogar.brasamc.com.br   mostrado no #status e nas boas-vindas (vazio: "em breve")
  MP_ACCESS_TOKEN=...          Access Token do Mercado Pago (o de teste no servidor de teste; vazio: loja fechada)
estado-<servidor>.json         IDs dos cargos e canais, do tools/discord/estrutura.js
bot-<servidor>.json            o que o bot guarda (mensagens dos painéis, apelidos aplicados, tickets abertos, pedidos da loja)
```

O bot precisa dos intents **Server Members** e **Message Content** (a transcrição dos tickets lê o texto das mensagens) e do cargo dele acima dos cargos que controla (o `estrutura.js` já deixa assim).

## Textos (`conteudo/`)

| Arquivo | Onde aparece |
|---|---|
| `boas-vindas.md` | `#boas-vindas`, com os botões dos cargos de aviso |
| `regras.md` | `#regras` |
| `tutoriais/NN-nome.md` | Um post no fórum `#tutoriais` por arquivo, na ordem do número (o `01` fica fixado no topo) |

A primeira linha (`# ...`) é o título; o resto é o texto, em markdown do Discord, com até 4096 caracteres. `{ip}` vira o `SERVIDOR_IP` (ou "em breve") e `{#chave}` vira o link do canal (as chaves do `estrutura-brasamc.js`, como `{#abrir-ticket}`). Editou? Reinicie o bot: ele edita só o que mudou. Tutorial novo vira post novo.

## Testar

`tools/bots/teste-vinculo.js` testa o vínculo de ponta a ponta sem o Discord: um bot entra no jogo, gera o código e o teste chama a API como o bot faria.

## Testar a loja

Com o Access Token de **teste**, nada é cobrado. O pagamento precisa ser feito logado numa **conta de comprador de teste** (painel do Mercado Pago → Contas de teste) numa janela anônima, com um [cartão de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/additional-content/your-integrations/test/cards): titular `APRO` aprova, `OTHE` recusa.

## Próximas etapas

Avisos por DM.
