# ChatPlugin

O chat do servidor: canal **local** e **global**, e o anti-flood. As tags (VIP, clã, rank, Gladiador) continuam nos plugins delas.

## Canais

| Canal | Como | Quem vê | Prefixo |
|---|---|---|---|
| Local | mensagem normal | só quem está no mesmo mundo | `[L]` |
| Global | `/g <mensagem>` (ou `/global`) | o servidor todo | `[G]` |

- Quem fala no local sem ninguém no mundo pra ouvir é lembrado do `/g` (`canais.avisar-sozinho`).
- A staff (`chat.espiar`, padrão op) vê o local dos outros mundos, com o nome do mundo na frente (`[terrenos] [L] ...`).
- O `/g` passa pelo chat de verdade, então as tags entram do mesmo jeito. O prefixo do canal fica na frente de tudo (o plugin carrega depois dos que põem tags).
- Os anúncios do servidor (boss, loja, rankup, `/loja anunciar`...) não são chat de jogador: continuam chegando a todos. O `/msg` e o `/cc` também não mudam.
- Desliga em `canais.ativado` (aí o chat volta a ser um só).

## Anti-flood

Vale pra todo mundo, **staff também**. Só passa quem tem `chat.antiflood.ignorar` (ninguém tem por padrão, nem op).

**Chat e comandos de mensagem**, contados juntos (quem fala no chat e no `/msg` usa o mesmo limite):

| Regra | Padrão | Config |
|---|---|---|
| Tempo mínimo entre duas mensagens | 2 s | `chat.intervalo-segundos` |
| A mesma mensagem não repete (sem diferença de maiúsculas, acentos, espaços e pontuação: "Olá!!" = "ola") | 30 s | `chat.repetida-segundos` |
| Rajada: mais de 5 mensagens em 15 s deixa o jogador sem falar | 30 s | `chat.rajada` |

Os comandos de mensagem ficam em `comandos-de-mensagem`: `/g`, `/msg`, `/tell`, `/w`, `/r`, `/me`, `/cc` e `/cla c`, com quantas palavras pular antes do texto (o destinatário do `/msg`).

**Comandos que anunciam ou cobram**, um a cada tantos segundos por jogador (`comandos-com-intervalo`): `/vip vender` (60 s), `/pix qrcode`, `/pix receber`, `/pix cobrar`, `/pix agendar`, `/tpa`, `/tpahere`, `/cla convidar` (10 s) e `/bounty colocar` (30 s). O `/loja anunciar` tem o intervalo próprio no BarracasPlugin (10 min, staff também).

Os comandos são reconhecidos pelo nome, então os aliases contam (`/clan c` = `/cla c`, `/tell` = `/msg`). Uma chave de duas palavras (`"vip vender"`) vale só pra aquele subcomando.

## Comandos

| Comando | Ação |
|---|---|
| `/g <mensagem>` | Fala no chat global |
| `/antiflood reload` | (staff) Recarrega o `config.yml` |

## Como compilar

```bash
cd chat-plugin
mvn clean package
```

O `.jar` sai em `target/ChatPlugin.jar`.
