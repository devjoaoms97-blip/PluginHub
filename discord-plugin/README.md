# DiscordPlugin

Ponte com o Discord. Os outros plugins mandam os anúncios para cá, e cada **categoria** vai para o seu webhook (ou para o padrão) como um **embed**, com título, cor e, quando configurado, a menção ao cargo 🔔 da categoria.

| Categoria | Quem manda | O quê | Canal na BrasaMC |
|---|---|---|---|
| `boss` | BossPlugin | Boss apareceu (marca 🔔 Boss); derrotado, com o campeão e o ranking; maldição | `#eventos` |
| `gladiador` | GladiadorPlugin | Inscrições abertas (marca 🔔 Gladiador), resultado e destaques | `#eventos` |
| `minesena` | ChequePlugin | Resultado da Mine-sena | `#eventos` |
| `skills` | encaminhado do chat | XP em dobro nas skills | `#eventos` |
| `loja` | encaminhado do chat | Alta de um item na loja | `#eventos` |
| `recursos` | encaminhado do chat | Mundo de recursos resetado | `#eventos` |
| `leiloes` | TerrenosPlugin e BarracasPlugin | Leilão aberto (marca 🔔 Leilões), arrematado ou sem lances | `#mercado` |
| `barracas` | BarracasPlugin | Barraca alugada e liberada | `#mercado` |
| `vip` | VipPlugin | VIP ativado | `#conquistas` |
| `conquistas` | encaminhado do chat | Novo Rei e prestígio | `#conquistas` |
| `bounty` | BountyPlugin | Recompensa grande colocada ou cobrada, contrato contra clã | `#bounty` |
| `status` | este plugin | Servidor ligou e desligou | `#status` |
| `pix` | PixPlugin | Pix alto ou suspeito (staff) | `#alertas-pix` |
| `economia` | PainelEconomiaPlugin | Circulação crescendo demais (staff) | `#alertas-economia` |

## Como funciona

- Cada plugin chama `DiscordPlugin#enviar(categoria, mensagem)` por reflexão. Sem o DiscordPlugin, nada é enviado e nada quebra. O Bounty usa o DiscordPlugin quando ele está ligado e o webhook próprio quando não está.
- **Encaminhamento do chat:** os anúncios do chat (`BroadcastMessageEvent`) que batem com um padrão de `encaminhar` vão para a categoria indicada. Assim, rankup, prestígio, XP em dobro, alta na loja e reset do mundo de recursos chegam ao Discord sem mudar esses plugins.
- O envio é feito numa fila própria, uma mensagem por vez, fora da thread do servidor. Se o Discord pedir para esperar (HTTP 429), a fila espera o tempo pedido e tenta de novo. Ao desligar, o aviso de "servidor desligado" sai direto, com prazo de 3 s.
- Com o Discord fora do ar ou o webhook errado, sai no máximo um aviso no console a cada 10 minutos.
- As cores do Minecraft (`§`) são tiradas. Só o cargo de `mencionar` pode ser marcado, e só quando o texto bate com `mencionar-se`; `@everyone` e `@here` nunca marcam ninguém.

## Configuração

```yaml
webhook-url: ""          # webhook padrão (vazio: só as categorias com webhook próprio)
nome: "BrasaMC"          # autor das mensagens
cor: "#f26b1d"           # cor padrão dos embeds
rodape: "BrasaMC"
categorias:
  boss:
    ativado: true
    webhook-url: ""      # vazio: usa o padrão
    nome: "Boss"
    titulo: "🐉 Boss"
    cor: "#c0392b"
    mencionar: ""        # ID do cargo 🔔 Boss
    mencionar-se: "apareceu"   # só marca no aparecimento, não na morte
encaminhar:
  - categoria: conquistas
    padrao: "subiu pro rank Rei!"
status:
  ligou: "🟢 **Servidor online!** Bora jogar."
  desligou: "🔴 **Servidor desligado.** Já já a gente volta."
```

As URLs dos webhooks e os IDs dos cargos não precisam ser colados à mão: o `tools/discord/estrutura.js` cria os webhooks no Discord e grava tudo no config (veja o [README de lá](../tools/discord/README.md)). Para pegar uma URL manualmente: no Discord, **Editar canal > Integrações > Webhooks > Novo webhook > Copiar URL do webhook**.

## Vínculo de conta e API do bot

O [bot do Discord](../discord-bot/README.md) roda separado e fala com o servidor por uma **API HTTP local**: só escuta em `api.endereco` (127.0.0.1) na `api.porta` (8155) e exige `Authorization: Bearer <api.token>`. Sem token no config, o plugin gera um na primeira vez e grava lá; ele vai pro `.env` do bot.

| Rota | O quê |
|---|---|
| `GET /saude` | Jogadores online e máximo |
| `GET /vinculos` | Todos os vínculos, com o tier do VIP ativo (do VipPlugin) |
| `POST /vinculo` | Confirma um código do `/vincular` (`codigo`, `discordId`, `discordNome`, `recompensa`) |
| `POST /desvincular` | Desfaz pelo `discordId` ou pelo `nick` |
| `POST /entregar` | Entrega uma compra da loja do Discord (`pagamento`, `pedido`, `uuid`, `creditos`): roda `loja.comandos` (por padrão `creditos dar`) e anota o pagamento em `entregas.yml`. O mesmo pagamento nunca é entregue duas vezes |

- **`/vincular`** (todos) mostra um código de 6 letras, clicável pra copiar, que vale por `vinculo.codigo-minutos` (10). Quem já vinculou vê com qual Discord.
- Uma conta do Discord por conta do Minecraft. Trocar só pela staff.
- A **recompensa** (`vinculo.recompensa`, comandos de console; por padrão 5 bilhetes grátis da Mine-sena) sai uma vez por conta do Minecraft e uma vez por conta do Discord, e só quando o bot diz que a conta do Discord tem a idade mínima. Desvincular e vincular de novo não dá outra.
- O nick é atualizado quando o jogador entra (se ele trocou de nome). Tudo fica em `vinculos.yml`.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/vincular` | todos | Código pra vincular a conta ao Discord |
| `/discord teste [categoria]` | staff | Manda uma mensagem de teste para a categoria (com o texto de `teste`, que dispara a menção) |
| `/discord teste todas` | staff | Manda um teste em cada categoria do config |
| `/discord reload` | staff | Recarrega o config e os encaminhamentos |
| `/discord vinculo <jogador>` | staff | Mostra o Discord vinculado ao jogador |
| `/discord desvincular <jogador>` | staff | Desfaz o vínculo |

Staff é `discord.admin` (op).

## Para outros plugins

```java
Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordPlugin");
if (discord != null && discord.isEnabled()) {
    discord.getClass().getMethod("enviar", String.class, String.class).invoke(discord, "minha-categoria", "texto");
}
```

Uma categoria nova não precisa de mudança neste plugin: sem entrada em `categorias`, ela fica ligada, usa o webhook padrão e a cor padrão. Para um anúncio que já vai pro chat, basta uma linha em `encaminhar`.
