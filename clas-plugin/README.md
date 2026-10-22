# ClasPlugin

Clãs com **tag no chat**, **cargos**, **banco compartilhado**, **fogo amigo desligado** e **ranking de abates**.

## Comandos

Aliases de `/cla`: `/clan`, `/clas`.

| Comando | Quem | Ação |
|---|---|---|
| `/cla criar <tag> <nome>` | sem clã | Funda um clã (custa R$ 5.000, configurável). Tag: 2 a 5 letras ou números |
| `/cla convidar <jogador>` | líder, oficial | Convida um jogador online (o convite vale 5 min e tem botões de aceitar e recusar) |
| `/cla aceitar [tag]` / `/cla recusar [tag]` | convidado | Responde o convite (sem tag, se só tiver um convite) |
| `/cla info [tag]` | todos | Líder, membros, abates, mortes, banco e fogo amigo (sem tag: o seu clã) |
| `/cla membros` | membro | Membros por cargo, com indicador de quem está online |
| `/cla top` | todos | Ranking de clãs por abates (desempate: banco) |
| `/cla banco` | membro | Saldo e as últimas movimentações do banco |
| `/cla depositar <valor>` | membro | Coloca dinheiro no banco do clã |
| `/cla sacar <valor>` | líder | Tira dinheiro do banco (oficiais também, se `banco.oficial-pode-sacar: true`) |
| `/cc <mensagem>` (ou `/cla c`) | membro | Chat só do clã |
| `/cla sair` | membro, oficial | Sai do clã (o líder precisa transferir antes ou desfazer) |
| `/cla expulsar <jogador>` | líder, oficial | O líder expulsa qualquer um; o oficial só expulsa membros comuns |
| `/cla promover <jogador>` / `/cla rebaixar <jogador>` | líder | Membro ↔ oficial |
| `/cla transferir <jogador>` | líder | Passa a liderança (o líder antigo vira oficial) |
| `/cla fogoamigo` | líder | Liga e desliga o fogo amigo (padrão: desligado) |
| `/cla desfazer confirmar` | líder | Apaga o clã. O banco volta pro líder |
| `/cla admin desfazer <tag>` | staff | Apaga o clã. O banco volta pro líder |
| `/cla admin reload` | staff | Recarrega o `config.yml` |

Permissões: `clas.usar` (padrão: todos) e `clas.admin` (padrão: op).

## Como funciona

- **Tag no chat**: `[TAG]` aparece antes do nome em toda mensagem. Ela convive com a tag do VIP: o VIP monta o formato do chat e o clã coloca a tag na frente, ficando `[TAG] [VIP Ouro] Nome: mensagem`. O formato e as cores ficam em `tag.formato`.
- **Fogo amigo desligado**: membro do mesmo clã não machuca o outro, nem com golpe direto nem com projétil. O líder pode ligar.
- **Abates e mortes**: matar um jogador de fora do clã soma um abate pro seu clã e uma morte pro clã da vítima. Não conta entre colegas nem entre contas do mesmo IP.
- **Banco**: qualquer membro deposita, e só o líder saca (ou também os oficiais, se configurado). O extrato guarda as últimas 30 movimentações, e todo membro online é avisado de cada uma.
- **Nome e chat do clã** não aceitam códigos de cor digitados por jogador.

## Integrações

- **BountyPlugin**: não paga recompensa entre colegas de clã, e não deixa colocar recompensa num colega. O Bounty consulta o `ClasPlugin#mesmoCla(UUID, UUID)` por reflexão.
- **VipPlugin**: as duas tags convivem no chat (ver acima).
- **PainelEconomiaPlugin**: o custo de criação aparece como saída do `ClasPlugin`. Um depósito no banco também aparece como saída, porque o dinheiro sai da conta do jogador, e o saque volta como entrada.

## Configuração

```yaml
criacao:
  custo: 5000.0
membros-maximo: 10
convite-expira-minutos: 5
banco:
  oficial-pode-sacar: false
tag:
  formato: "&8[&b%tag%&8]"
```

## Persistência

Os clãs ficam em `plugins/ClasPlugin/clas.yml`, gravado a cada mudança, porque o banco é dinheiro de jogador. Convites ficam só em memória: um restart cancela os convites pendentes.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia (pro custo de criação e o banco)

## Como compilar

```bash
cd clas-plugin
mvn clean package
```

O `.jar` sai em `target/ClasPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **A tag aparece só no chat, não na tab list.** O VipPlugin já define o nome na tab, e os dois iriam se sobrescrever. Juntar as duas tags na tab precisa de uma integração entre os plugins, que fica pra próxima versão.
2. **O banco do clã não aparece no `/pix historico`.** O Pix só registra transações entre jogadores ou com a "Loja do Servidor", então um depósito no clã apareceria com o nome errado. O extrato fica no `/cla banco`.
3. **O ranking é só por abates.** A integração com o Boss (clã do campeão) e a recompensa contra um clã inteiro no Bounty ficam pra próxima versão.
4. **Fogo amigo** cobre golpe direto e projétil. Poção de dano arremessada e explosão (TNT, cristal do End) ainda acertam colegas.
