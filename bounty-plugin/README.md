# BountyPlugin

Recompensa pela cabeça de jogadores. Alguém oferece um valor, que **fica retido** na hora, e **quem matar o alvo leva**. Isso dá um objetivo pro PvP e combina com as skills de combate.

## Comandos

| Comando | Quem | Ação |
|---|---|---|
| `/bounty colocar <jogador> <valor>` | todos | Oferece uma recompensa pela cabeça de alguém |
| `/bounty lista` | todos | As 10 maiores recompensas ativas (● verde = alvo online) |
| `/bounty ver <jogador>` | todos | A recompensa de um jogador e quando ela expira (a staff vê quem contribuiu) |
| `/bounty admin remover <jogador>` | staff | Remove a recompensa e devolve aos contribuintes |
| `/bounty admin reload` | staff | Recarrega o `config.yml` |

Aliases: `/recompensa`, `/cabeca`. Permissões: `bounty.usar` (padrão: todos) e `bounty.admin` (padrão: op).

## Como funciona

- **Oferecer**: o valor sai da sua conta na hora. Uma **taxa** (10%) é descontada e some da economia, e o resto vai pra recompensa. Valor mínimo: R$ 100.
- **Recompensas se somam**: várias pessoas podem oferecer pela mesma cabeça, e quem matar leva o total.
- **O alvo é avisado** quando colocam recompensa nele e ao entrar no servidor. Uma recompensa a partir de R$ 1.000 é anunciada pro servidor inteiro.
- **Cobrar**: matar o alvo em PvP paga o total pro matador, com anúncio no chat. Morte sem matador (queda, lava, mob) **não paga**, e a recompensa continua valendo.
- **Expiração**: se ninguém cobrar em **7 dias** desde a última contribuição, cada contribuinte recebe a sua parte de volta (já sem a taxa).
- Não dá pra colocar recompensa na própria cabeça.

## Anti-farm

A recompensa **não é paga**, e continua valendo, quando:
- o matador e o alvo estão conectados do **mesmo IP** (conta alternativa);
- o matador e o alvo são do **mesmo clã** (com o ClasPlugin instalado). Também não dá pra colocar recompensa em colega de clã;
- o mesmo matador **já cobrou esse alvo** nas últimas 6h.

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
integracao:
  pix: true
```

## Integrações

- **PixPlugin** (opcional): oferecer, cobrar e receber devolução aparecem no `/pix historico`.
- **ClasPlugin** (opcional): regra do mesmo clã no anti-farm.
- **PainelEconomiaPlugin**: a taxa aparece como saída do `BountyPlugin` no fluxo por origem.

## Persistência

As recompensas ficam em `plugins/BountyPlugin/bounties.yml`, gravado a cada mudança, porque é dinheiro retido de jogador. O cooldown de cobrança fica em memória e zera num restart.

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
3. **Não tem recompensa contra um clã inteiro.** Isso fica pra quando os clãs tiverem mais integrações.
