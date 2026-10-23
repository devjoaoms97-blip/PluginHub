# DiscordPlugin

Ponte com o Discord. Os outros plugins mandam os anúncios para cá, e cada **categoria** vai para o seu webhook, ou para o webhook padrão.

| Categoria | Quem manda | O quê |
|---|---|---|
| `boss` | BossPlugin | Boss apareceu; boss derrotado, com o campeão e o ranking de dano |
| `gladiador` | GladiadorPlugin | Inscrições abertas e resultado |
| `leiloes` | TerrenosPlugin e BarracasPlugin | Leilão de imóvel e de barraca especial: aberto, arrematado ou sem lances |

O BountyPlugin continua com o webhook próprio (`discord.webhook-url` no config dele).

## Como funciona

- Cada plugin chama `DiscordPlugin#enviar(categoria, mensagem)` por reflexão. Sem o DiscordPlugin, nada é enviado e nada quebra.
- O envio é feito numa fila própria, uma mensagem por vez, fora da thread do servidor. Se o Discord pedir para esperar (HTTP 429), a fila espera o tempo pedido e tenta de novo.
- Com o Discord fora do ar ou o webhook errado, sai no máximo um aviso no console a cada 10 minutos.
- As cores do Minecraft (`§`) são tiradas, e as menções (`@everyone`, `@here`, cargos) ficam desligadas.

## Configuração

```yaml
webhook-url: ""          # webhook padrão (vazio: só as categorias com webhook próprio)
nome: "Servidor"         # autor das mensagens
categorias:
  boss:
    ativado: true
    webhook-url: ""      # vazio: usa o padrão
    nome: "Boss"
  gladiador:
    ativado: true
    webhook-url: ""
    nome: "Gladiador"
  leiloes:
    ativado: true
    webhook-url: ""
    nome: "Leilões"
```

Para pegar a URL: no Discord, **Editar canal > Integrações > Webhooks > Novo webhook > Copiar URL do webhook**.

## Comandos (staff, `discord.admin`)

| Comando | O que faz |
|---|---|
| `/discord teste [categoria]` | Manda uma mensagem de teste para a categoria |
| `/discord reload` | Recarrega o config |

## Para outros plugins

```java
Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordPlugin");
if (discord != null && discord.isEnabled()) {
    discord.getClass().getMethod("enviar", String.class, String.class).invoke(discord, "minha-categoria", "texto");
}
```

Uma categoria nova não precisa de mudança neste plugin: sem entrada em `categorias`, ela fica ligada e usa o webhook padrão.
