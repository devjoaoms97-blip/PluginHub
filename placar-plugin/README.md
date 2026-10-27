# PlacarPlugin

Scoreboard lateral (a "telinha" no canto da tela) de cada jogador, atualizada a cada 2 segundos:

```
⛏ RANKUP
Saldo: R$ 12.345
Rank: [Fazendeiro]
Próximo: Lenhador
 ■■■■■■■■■■ 64%
Clã: [ABC]
Missões hoje: 2/3
Skill: Espadas nv 23
 ■■■■■■■■■■ 45%
Próximo boss: sáb 21:00
Online: 12/100
```

## Comandos

| Comando | Ação |
|---|---|
| `/placar` | Liga e desliga o placar pra você (fica salvo) |
| `/placar ver <jogador>` | (staff) Mostra no chat as linhas como esse jogador vê agora |
| `/placar reload` | (staff) Recarrega o config |

Permissão da staff: `placar.admin` (padrão: op).

## Config

`titulo` e `linhas` (no máximo 15, de cima pra baixo, com `&` pras cores). Placeholders:

| Placeholder | O que mostra | De onde vem |
|---|---|---|
| `{saldo}` | Saldo (resumido acima de 100 mil: "R$ 1,2 mi") | Vault |
| `{rank}` | Tag do rank, com o prestígio | RankupPlugin (`tagDoRank`) |
| `{proximo-rank}` | Nome do próximo rank ("máximo" no último) | RankupPlugin (`nomeProximoRank`) |
| `{progresso}` | Barra de 10 blocos e a %: saldo / preço do próximo rank | RankupPlugin (`precoProximoRank`) |
| `{cla}` | Tag do clã, ou `textos.sem-cla` | ClasPlugin (`tagDoCla`) |
| `{missoes}` | Diárias concluídas / total (verde com ✔ quando terminou) | MissoesPlugin (`missoesDoDia`) |
| `{skill}` | Skill treinada no último minuto (ou a da arma na mão), com o prestígio e o nível, ou `textos.sem-skill` | SkillsPlugin (`textoSkillEmUso`) |
| `{skill-progresso}` | Barra de 10 blocos e a % até o próximo nível dessa skill | SkillsPlugin (`progressoSkillEmUso`) |
| `{boss}` | "AGORA!" com o boss vivo, ou o próximo horário da agenda | BossPlugin (`textoProximoBoss`) |
| `{online}` / `{max}` | Jogadores online / máximo | servidor |
| `{jogador}` | Nome do jogador | servidor |

Tudo é lido por reflexão: sem o plugin, a linha mostra "—". `intervalo-ticks` muda a frequência e `mundos-sem-placar` esconde o placar em alguns mundos (ex: arena com placar próprio).

## Como funciona

Cada jogador tem a própria scoreboard (as linhas são diferentes pra cada um), com os números da direita escondidos (`NumberFormat.blank()`). Ao sair do placar (`/placar` ou mundo sem placar), o jogador volta pra scoreboard principal do servidor.

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/PlacarPlugin.jar`. Requisitos: Paper 26.2 e Vault (os outros plugins são opcionais).
