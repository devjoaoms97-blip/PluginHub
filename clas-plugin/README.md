# ClasPlugin

Clãs com **tag no chat e na tab**, **cargos**, **banco compartilhado**, **fogo amigo desligado** e **ranking de abates e bosses**.

## Comandos

Aliases de `/cla`: `/clan`, `/clas`.

| Comando | Quem | Ação |
|---|---|---|
| `/cla criar <tag> <nome>` | sem clã | Funda um clã (custa R$ 5.000, configurável). Tag: 2 a 5 letras ou números |
| `/cla convidar <jogador>` | líder, oficial | Convida um jogador online (o convite vale 5 min e tem botões de aceitar e recusar) |
| `/cla aceitar [tag]` / `/cla recusar [tag]` | convidado | Responde o convite (sem tag, se só tiver um convite) |
| `/cla info [tag]` | todos | Líder, membros, pontos, abates, mortes, bosses, banco e fogo amigo (sem tag: o seu clã) |
| `/cla membros` | membro | Membros por cargo, com indicador de quem está online |
| `/cla top` | todos | Ranking de clãs por pontos: abates + pontos de boss (desempate: banco) |
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
- **Tag na tab**: a tab list também mostra `[TAG] [VIP Ouro] Nome`. Ela é atualizada na hora quando alguém entra ou sai do clã. Formato em `tag.formato-tab`; pra desligar, use `tag.na-tab: false`.
- **Fogo amigo desligado**: membro do mesmo clã não machuca o outro com golpe direto, projétil, poção de dano, TNT acesa por ele ou cristal do End em que ele bateu. Poção arremessada ou persistente com efeito ruim (veneno, lentidão, fraqueza...) não pega no colega, e as boas (cura, força) continuam pegando. O líder pode ligar o fogo amigo.
- **Boss mundial**: o clã de quem dá o último golpe no boss vira o **clã do campeão** (★ no `/cla top` e no `/cla info`) e ganha 10 pontos. Os outros clãs cujos membros, somados, tiraram pelo menos 10% da vida do boss ganham 3 pontos. O boss é reconhecido sozinho, sem configurar nada no BossPlugin.
- **Abates e mortes**: matar um jogador de fora do clã soma um abate pro seu clã e uma morte pro clã da vítima. Não conta entre colegas, entre contas do mesmo IP nem no mundo das arenas (duelo X1 e Gladiador, onde dá pra combinar a luta), configurado em `placar.mundos-ignorados`.
- **Banco**: qualquer membro deposita, e só o líder saca (ou também os oficiais, se configurado). O extrato guarda as últimas 30 movimentações, e todo membro online é avisado de cada uma. Depósitos e saques também aparecem no `/pix historico` de quem fez, como "Clã [TAG]".
- **Nome e chat do clã** não aceitam códigos de cor digitados por jogador.

## Integrações

- **BountyPlugin**: não paga recompensa entre colegas de clã, e não deixa colocar recompensa num colega. O Bounty consulta o `ClasPlugin#mesmoCla(UUID, UUID)` por reflexão.
- **VipPlugin**: as duas tags convivem no chat e na tab. Na tab, o VipPlugin monta o nome perguntando a tag do clã ao `ClasPlugin#prefixoTab(UUID)`.
- **BossPlugin**: pontos e clã do campeão (ver acima). O boss é reconhecido pela chave compartilhada `pluginhub:boss_mob`.
- **PixPlugin**: criação do clã ("Loja do Servidor"), depósitos e saques ("Clã [TAG]") e a devolução do banco quando o clã é desfeito aparecem no `/pix historico`.
- **BountyPlugin**: também consulta `tagDoCla(UUID)` e `tagExistente(String)`, pras recompensas contra um clã inteiro.
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
  na-tab: true
  formato-tab: "&8[&b%tag%&8]"
boss:
  ativado: true
  pontos-campeao: 10
  pontos-participacao: 3
  participacao-minima-percentual: 10
integracao:
  pix: true
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

1. **A tag na tab e no chat precisa do VipPlugin atualizado** pra aparecer junto com a do VIP. Com um VipPlugin antigo, o VIP sobrescreve a tab sem a tag do clã.
2. **O banco no `/pix historico` precisa do PixPlugin atualizado.** Com um Pix antigo, só a criação do clã aparece, e um aviso sai uma vez no console.
3. **Fogo amigo não cobre explosão de cama ou de âncora de renascimento** (o jogo não diz quem causou) nem TNT ativada por redstone ou dispensador. Lava e fogo colocados por um colega também machucam.
4. **O dano do boss por clã fica em memória.** Um restart no meio da luta zera a conta de participação daquele boss.
