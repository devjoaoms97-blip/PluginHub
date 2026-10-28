# Discord da BrasaMC

Scripts que montam e conferem o servidor do Discord pela API, sem clicar em nada. Sem dependências (Node 18+).

O token do bot e os dados de cada servidor ficam **fora do repositório**, em `C:\mc-teste\discord\` (ou na pasta de `DISCORD_PASTA`):

| Arquivo | O que é |
|---|---|
| `.env` | `DISCORD_TOKEN=...`, `GUILD_TESTE=...` e `GUILD_OFICIAL=...` |
| `export-<servidor>.json` | Último retrato do servidor, feito pelo `exportar.js` |
| `estado-<servidor>.json` | ID de cada cargo, canal e webhook criado pelo `estrutura.js` |
| `webhooks-<servidor>.json` | URLs dos webhooks. **Segredo:** quem tem a URL posta no canal |

## Scripts

| Comando | O que faz |
|---|---|
| `node tools/discord/exportar.js oficial` | Só lê: mostra cargos, categorias, canais, permissões e webhooks, e grava o `export-oficial.json` |
| `node tools/discord/estrutura.js teste` | Simula: mostra o que mudaria para o servidor ficar como o `estrutura-brasamc.js` descreve |
| `node tools/discord/estrutura.js teste --aplicar` | Aplica |
| `... --aplicar --plugin C:\mc-teste\plugins\DiscordPlugin\config.yml` | Também grava as URLs dos webhooks e os cargos 🔔 no config do DiscordPlugin |

O `estrutura.js` pode rodar quantas vezes for preciso: o que já está certo não é tocado. Cada item tem uma chave fixa no `estrutura-brasamc.js`, então renomear lá renomeia no Discord sem duplicar.

- **Cargos:** cria, ajusta e ordena abaixo do cargo do bot. O bot não mexe em cargo acima do dele: se precisar, o script pede pra arrastar o cargo do bot pra cima e para antes de mudar qualquer canal.
- **Canais:** cria as categorias e os canais com o acesso de cada um (`publico`, `leitura`, `staff`, `direcao` ou `manter`). `adotar` reaproveita um canal que já existe, com o histórico.
- **Comunidade:** liga o modo Comunidade (precisa dele para fórum e canal de anúncios) e define os canais de regras e de avisos do Discord.
- **Webhooks:** um por categoria do DiscordPlugin, no canal de `webhooks`.

Sempre rodar primeiro no servidor de teste (`teste`) e só depois no `oficial`.
