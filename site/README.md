# Site da BrasaMC

Site em Node, **sem dependências** (só o `http` do próprio Node), que roda separado do servidor do Minecraft, como o [bot do Discord](../discord-bot/README.md): no PC por enquanto, na VPS depois. Ele lê os dados pela API local do [DiscordPlugin](../discord-plugin/README.md), a mesma do bot.

## O que tem

| Página | O que mostra |
|---|---|
| `/` | O IP pra copiar, o status ao vivo (quem está online, o próximo boss e o pote da Mine-sena, atualizado a cada 30 s), o Discord, o modpack, os cards do que tem no servidor, a prévia dos rankings (top 3), como entrar e os sites de voto (os do `sites:` do VotoPlugin) |
| `/servidor/<id>` | Uma página por card (rankup, bosses, fazendas, clãs, eventos, skills, caixas, pesca): o texto, os destaques, os comandos (clicou, copiou) e o botão pro ranking ligado. Os textos ficam em `src/conteudo.js` |
| `/rankings` | Abas: Mais ricos, Rank (prestígio primeiro), Skills (o total ou uma skill), Clãs e Votos do mês. Pódio pros 3 primeiros e a lista até o 50º (20 por skill). A troca de aba não recarrega e o endereço acompanha (`?aba=`, `&skill=`), então dá pra mandar o link de uma aba. Busca por nick |
| `/jogador/<nick>` | O perfil: a skin, rank, VIP, apoiador, clã, dinheiro, votos, a posição em cada ranking e a barra de cada skill. Nick errado mostra os parecidos |
| `/loja` | A loja de créditos (os mesmos 4 pacotes da loja do Discord) em 3 passos: o pacote, o nick e o código que chega no chat do jogo, e o Mercado Pago (Pix ou cartão). Veja "Loja", abaixo |
| `/loja/pedido/<id>` | O pedido: aguardando, em análise, pago, entregue, vencido ou com valor diferente. Se atualiza sozinho a cada 5 s. O id é aleatório (ninguém adivinha o pedido dos outros) |
| `/votar` | Os sites de votação (os do `sites:` do VotoPlugin), o que cada voto dá, os 7 dias da sequência, a barra da Festa do Voto, o top 10 do mês e a sequência de um nick (`?nick=`) |
| `/novidades` | As atualizações, da mais nova pra mais antiga. Pra anunciar uma, ponha uma entrada no começo de `NOVIDADES` em `src/conteudo.js` |
| `/tutoriais` | Os tutoriais do fórum do Discord (`discord-bot/conteudo/tutoriais/*.md`), por grupo: Skills, Começando, Rankup/dinheiro/terrenos, PvP e eventos, Mundos e tesouros. Cada um em `/tutoriais/<nome>` (o nome do arquivo sem o número), com as seções em cards, as tabelas de nível e os outros do grupo do lado. Mudou o arquivo, muda o site e o Discord. O `/celular` leva pro de celular, e as páginas dos cards (`/servidor/<id>`) listam os tutoriais ligados (`tutoriais:` em `src/conteudo.js`) |
| `/regras` | As regras, lidas do mesmo arquivo do canal do Discord (`discord-bot/conteudo/regras.md`): mudou lá, muda aqui |

**Efeitos:** o brilho que segue o mouse nos cards, os cards que sobem e o ícone que pula, a entrada dos elementos ao rolar a página, os números contando, as faíscas no topo da inicial e o ponto verde pulsando no status. Quem pede menos movimento no sistema (`prefers-reduced-motion`) vê tudo parado.

**Prévia dos links:** toda página tem as tags `og:` com a imagem `publico/previa.png` (1200x630), que aparece quando alguém manda o link no Discord ou no WhatsApp. A arte é a página `/previa`; mudou? Com o site ligado, `node gerar-previa.js` tira a foto de novo com o Edge ou o Chrome. O endereço da imagem usa o `SITE_URL`.

## Loja

1. O comprador escolhe o pacote e digita o nick. O site pede um código ao DiscordPlugin (`POST /loja/codigo`), que manda no chat do jogo: o jogador precisa estar online. O mesmo jogador só recebe outro depois de 60 s, e o site limita a 5 pedidos de código por IP a cada 10 min.
2. Ele digita o código. O site confere (`POST /loja/confirmar`: vale 10 min, 5 tentativas, uma vez só), cria o pedido e o link do Mercado Pago (vale 1 hora) e manda o comprador pra lá.
3. A cada 20 s o site procura os pagamentos dos pedidos abertos. Aprovado e com o valor certo, entrega pelo `POST /entregar`, o mesmo da loja do Discord, que anota o ID do pagamento: nunca entrega duas vezes, nem se o site e o bot pedirem. Servidor desligado: tenta de novo até dar. Valor menor: nada é entregue e o pedido mostra o problema.

Os pedidos ficam em `<DISCORD_PASTA>/site-<servidor>.json` (a referência no Mercado Pago é `site-<servidor>-<número>`, diferente das do bot). O Mercado Pago só volta sozinho pra página do pedido com `SITE_URL` em https. As vendas do site aparecem no log do site e no `entregas.yml` do DiscordPlugin (pedido "site #N"); o `#log-vendas` do Discord é só da loja do bot.

**Mostrar pra alguém de fora, sem VPS:** `cloudflared tunnel --url http://127.0.0.1:8080` dá um endereço `https://....trycloudflare.com` temporário (muda a cada vez). Ligue o site com `SITE_URL` igual a ele. O `site.bat` (ou `node publicar.js oficial`) faz tudo isso sozinho: abre o túnel, sobe o site com o link certo e mostra o link na janela; fechar a janela tira os dois do ar.

## De onde vêm os dados

| Rota da API (DiscordPlugin) | Pra quê | Guardado no site |
|---|---|---|
| `GET /saude` | O status ao vivo | 15 s |
| `GET /site` | A foto de todos os jogadores e clãs. O plugin refaz a cada `site.intervalo-minutos` (5) | 1 min; com o servidor desligado, fica a última |

Cada plugin entrega a parte dele da foto por um método `dadosDoSite(UUID)`, chamado por reflexão: RankupPlugin (rank e prestígio), SkillsPlugin (nível, prestígio e XP de cada skill), ClasPlugin (clã e cargo, e `clasDoSite()` com todos os clãs) e VotoPlugin (votos do mês, total e sequência, e `resumoDoSite()` com a festa e os sites). O dinheiro vem do Vault, o VIP do VipPlugin e a tag de apoiador do CreditosPlugin.

## Rodar

```bash
cd site
npm start          # fala com o servidor de teste
npm run oficial    # fala com o servidor dos jogadores
```

Abre em `http://127.0.0.1:8080`. A configuração fica no mesmo `.env` do bot (`C:\mc-teste\discord\.env`, ou a pasta de `DISCORD_PASTA`):

```
PLUGIN_API_URL / PLUGIN_API_TOKEN   a API do DiscordPlugin (já existem pro bot; com _TESTE ou _OFICIAL, valem só naquele)
SERVIDOR_IP                         o IP mostrado na página (vazio: "em breve")
SITE_PORTA=8080                     a porta do site
SITE_ENDERECO=127.0.0.1             onde escutar (na VPS continua assim: o Caddy fica na frente)
SITE_URL=https://brasamc.com.br     o endereço público (volta do Mercado Pago e imagem de prévia)
MP_ACCESS_TOKEN                     o mesmo da loja do Discord (com _TESTE ou _OFICIAL); vazio: loja do site fechada
DISCORD_CONVITE=https://discord.gg/...   o botão do Discord (vazio: sem botão)
MODPACK_URL                         a página do modpack (padrão: a release modpack-1.0.0 do GitHub)
```

## Arquivos

```
src/servidor.js      as rotas
src/config.js        lê o .env
src/dados.js         a API do DiscordPlugin, com o que fica guardado
src/rankings.js      os rankings, refeitos só quando chega uma foto nova
src/loja.js          a loja: pacotes, código, pedidos, Mercado Pago e entrega
src/conteudo.js      os textos das páginas de cada parte do servidor e as novidades
gerar-previa.js      tira a foto da /previa pra publico/previa.png
src/paginas/         o HTML de cada página (util.js tem o esc(): tudo que vem do jogo passa por ele)
publico/             CSS, o JS do navegador (abas, efeitos, status) e o ícone
```

As cabeças dos jogadores vêm do mc-heads.net, pelo nick. A página só carrega script dela mesma (Content-Security-Policy), e as fontes vêm do Google Fonts.

## Testar

`tools/bots/teste-loja-site.js` testa a loja com um Mercado Pago falso (nada é cobrado): o código no chat, os limites, o código errado, o pedido, a entrega sem repetir, o valor menor, e as páginas de regras, novidades, votar e a prévia.

`tools/bots/teste-site.js` (com o servidor de teste ligado) cria um jogador com dinheiro, rank, skill, clã e voto, liga o site numa porta própria e confere a foto, a página inicial, o status, as páginas dos cards, as abas dos rankings, o perfil, a busca e os 404.
