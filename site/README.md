# Site da BrasaMC

Site em Node, **sem dependências** (só o `http` do próprio Node), que roda separado do servidor do Minecraft, como o [bot do Discord](../discord-bot/README.md): no PC por enquanto, na VPS depois. Ele lê os dados pela API local do [DiscordPlugin](../discord-plugin/README.md), a mesma do bot.

## O que tem

| Página | O que mostra |
|---|---|
| `/` | O IP pra copiar, o status ao vivo (quem está online, o próximo boss e o pote da Mine-sena, atualizado a cada 30 s), o Discord, o modpack, os cards do que tem no servidor, a prévia dos rankings (top 3), como entrar e os sites de voto (os do `sites:` do VotoPlugin) |
| `/servidor/<id>` | Uma página por card (rankup, bosses, fazendas, clãs, eventos, skills, caixas, pesca): o texto, os destaques, os comandos (clicou, copiou) e o botão pro ranking ligado. Os textos ficam em `src/conteudo.js` |
| `/rankings` | Abas: Mais ricos, Rank (prestígio primeiro), Skills (o total ou uma skill), Clãs e Votos do mês. Pódio pros 3 primeiros e a lista até o 50º (20 por skill). A troca de aba não recarrega e o endereço acompanha (`?aba=`, `&skill=`), então dá pra mandar o link de uma aba. Busca por nick |
| `/jogador/<nick>` | O perfil: a skin, rank, VIP, apoiador, clã, dinheiro, votos, a posição em cada ranking e a barra de cada skill. Nick errado mostra os parecidos |

**Efeitos:** o brilho que segue o mouse nos cards, os cards que sobem e o ícone que pula, a entrada dos elementos ao rolar a página, os números contando, as faíscas no topo da inicial e o ponto verde pulsando no status. Quem pede menos movimento no sistema (`prefers-reduced-motion`) vê tudo parado.

Próxima etapa: a **loja de créditos** (o nick, um código que chega no jogo e o pagamento pelo Mercado Pago, com a mesma entrega da loja do Discord).

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
DISCORD_CONVITE=https://discord.gg/...   o botão do Discord (vazio: sem botão)
MODPACK_URL                         a página do modpack (padrão: a release modpack-1.0.0 do GitHub)
```

## Arquivos

```
src/servidor.js      as rotas
src/config.js        lê o .env
src/dados.js         a API do DiscordPlugin, com o que fica guardado
src/rankings.js      os rankings, refeitos só quando chega uma foto nova
src/conteudo.js      os textos das páginas de cada parte do servidor
src/paginas/         o HTML de cada página (util.js tem o esc(): tudo que vem do jogo passa por ele)
publico/             CSS, o JS do navegador (abas, efeitos, status) e o ícone
```

As cabeças dos jogadores vêm do mc-heads.net, pelo nick. A página só carrega script dela mesma (Content-Security-Policy), e as fontes vêm do Google Fonts.

## Testar

`tools/bots/teste-site.js` (com o servidor de teste ligado) cria um jogador com dinheiro, rank, skill, clã e voto, liga o site numa porta própria e confere a foto, a página inicial, o status, as páginas dos cards, as abas dos rankings, o perfil, a busca e os 404.
