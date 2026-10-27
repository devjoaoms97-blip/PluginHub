# BountyPlugin

Recompensa pela cabeça de jogadores, ou de um **clã inteiro**. Alguém oferece um valor, que **fica retido** na hora, e **quem matar o alvo leva**. Isso dá um objetivo pro PvP e combina com as skills de combate. Recompensas grandes podem ser anunciadas num canal do **Discord**.

## Comandos

| Comando | Quem | Ação |
|---|---|---|
| `/bounty colocar <jogador> <valor>` | todos | Oferece uma recompensa pela cabeça de alguém |
| `/bounty cla <tag> <valor por abate> <abates>` | todos | Contrato contra um clã: paga o valor por cada membro morto, até o número de abates |
| `/bounty cla <tag>` | todos | Contratos ativos contra um clã |
| `/bounty lista` | todos | As 10 maiores recompensas ativas (● verde = alvo online) e os clãs caçados |
| `/bounty ver <jogador>` | todos | A recompensa de um jogador e quando ela expira (a staff vê quem contribuiu) |
| `/bounty admin remover <jogador>` | staff | Remove a recompensa e devolve aos contribuintes |
| `/bounty admin removercla <tag>` | staff | Encerra os contratos contra um clã e devolve o que sobrou |
| `/bounty admin reload` | staff | Recarrega o `config.yml` |

Aliases: `/recompensa`, `/cabeca`. Permissões: `bounty.usar` (padrão: todos) e `bounty.admin` (padrão: op).

## Como funciona

- **Oferecer**: o valor sai da sua conta na hora. Uma **taxa** (10%) é descontada e some da economia, e o resto vai pra recompensa. Valor mínimo: R$ 100.
- **Recompensas se somam**: várias pessoas podem oferecer pela mesma cabeça, e quem matar leva o total.
- **O alvo é avisado** quando colocam recompensa nele e ao entrar no servidor. Uma recompensa a partir de R$ 1.000 é anunciada pro servidor inteiro.
- **Cobrar**: matar o alvo em PvP paga o total pro matador, com anúncio no chat. Morte sem matador (queda, lava, mob) **não paga**, e a recompensa continua valendo.
- **Expiração**: se ninguém cobrar em **7 dias** desde a última contribuição, cada contribuinte recebe a sua parte de volta (já sem a taxa).
- Não dá pra colocar recompensa na própria cabeça.

## Recompensa contra um clã

`/bounty cla ABC 500 10` cria um contrato de R$ 500 por membro do clã ABC morto, até 10 abates. O total (R$ 5.000) sai da sua conta na hora, com a mesma taxa.

- Cada abate de qualquer membro do clã paga o valor de **um abate** pro matador. Com vários contratos contra o mesmo clã, o **mais antigo** paga primeiro.
- Valem as mesmas regras de anti-farm (mesmo IP, mesmo clã e cooldown por matador e vítima). O cooldown é separado do da recompensa individual. Um membro do próprio clã **nunca** cobra, mesmo com `anti-farm.mesmo-cla` desligado.
- Não dá pra contratar contra o próprio clã.
- O contrato vence em **7 dias** (contados da criação), e o que sobrou volta pra quem contratou. Se o clã for desfeito, ele é devolvido na próxima checagem (a cada 10 min).
- Precisa do ClasPlugin.

## Anúncio no Discord

Com `discord.webhook-url` preenchido, o plugin manda uma mensagem pro canal quando:
- uma recompensa chega a `discord.valor-minimo` (R$ 1.000) ou é cobrada;
- um contrato contra clã é criado, ou um abate dele é cobrado, a partir desse valor.

Pra pegar a URL, abra o canal no Discord e vá em Editar canal > Integrações > Webhooks > Novo webhook > Copiar URL do webhook. O envio não trava o servidor, e um Discord fora do ar só gera um aviso no console (no máximo um a cada 10 min). As menções (`@everyone`) ficam desligadas.

## Anti-farm

A recompensa **não é paga**, e continua valendo, quando:
- o matador e o alvo estão conectados do **mesmo IP** (conta alternativa);
- o matador e o alvo são do **mesmo clã** (com o ClasPlugin instalado). Também não dá pra colocar recompensa em colega de clã;
- o mesmo matador **já cobrou esse alvo** nas últimas 6h;
- o abate foi **no mundo das arenas** (duelo X1 e Gladiador), onde dá pra combinar a luta com um amigo. Os mundos ficam em `anti-farm.mundos-ignorados`.

A taxa de 10% também desestimula o "coloca recompensa no amigo e divide": o grupo sempre perde a taxa.

## Configuração

```yaml
valor-minimo: 100.0
taxa-percentual: 10.0
expira-dias: 7
anunciar-a-partir-de: 1000.0
anti-farm:
  mesmo-ip: true
  mesmo-cla: true
  cooldown-horas: 6
  mundos-ignorados: [arenas]
cla:
  valor-minimo-por-abate: 100.0
  abates-maximo: 20
discord:
  webhook-url: ""        # vazio desliga
  valor-minimo: 1000.0
  nome: "Bounty"
integracao:
  pix: true
```

## Integrações

- **PixPlugin** (opcional): oferecer, cobrar e receber devolução aparecem no extrato do Bancário.
- **ClasPlugin** (opcional): regra do mesmo clã no anti-farm e contratos contra clã. Consulta por reflexão os métodos `mesmoCla`, `tagDoCla` e `tagExistente`.
- **PainelEconomiaPlugin**: a taxa aparece como saída do `BountyPlugin` no fluxo por origem.

## Persistência

As recompensas e os contratos contra clã ficam em `plugins/BountyPlugin/bounties.yml`, gravado a cada mudança, porque é dinheiro retido de jogador. O cooldown de cobrança fica em memória e zera num restart.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia
- PixPlugin e ClasPlugin (opcionais)

## Como compilar

```bash
cd bounty-plugin
mvn clean package
```

O `.jar` sai em `target/BountyPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **A checagem de IP só compara os dois jogadores no momento da morte.** Quem usa VPN pra separar as contas passa. É o limite comum desse tipo de checagem.
2. **"Matador" é quem deu o último golpe** (`getKiller()` do Bukkit). Dano de Sangramento do SkillsPlugin não credita o atacante, então uma morte só por Sangramento não paga a recompensa.
3. **O contrato contra clã olha o clã da vítima na hora da morte.** Quem sai do clã deixa de valer pro contrato, e quem entra passa a valer.
