# MontariaPlugin

Montaria do VIP no spawn. No mundo `spawn` não dá pra voar, planar nem usar pérola (AreasPlugin), e a Avenida das Minas tem uns 230 blocos: a montaria deixa quem é VIP andar mais rápido entre o spawn, as minas, a loja e a warp mobs.

## Como funciona

- `/montaria` (ou `/cavalo`) chama um cavalo e já monta nele. De novo, ele vai embora.
- **Só VIP:** precisa da permissão `vip.perk.montaria`, que os grupos de VIP do LuckPerms dão (todos os tiers).
- **Velocidade:** `velocidade-vezes-corrida` (1,6) vezes a corrida a pé, mais ou menos um cavalo médio do jogo. O fim da avenida das minas fica a uns 25 s do spawn, em vez de 40 s.
- **Pulo baixo** (`pulo`: 0.5): sobe um degrau, mas não pula balcão nem barraca.
- **Onde:** só nos `mundos` do config (o `spawn`). Nos poços das minas do RankupPlugin e nas caixas de `proibido` (a arena do boss, que tem PvP) ela não é chamada, e quem entra montado desce na hora e segue a pé.
- **É descartável:** some quando o jogador desce, sai do servidor, se teleporta, troca de mundo ou toma dano. Ninguém mais monta nela, ela não tem baú nem inventário, não toma dano e não fica salva no mundo.
- **Recarga:** `recarga-segundos` (5) entre chamar uma e outra.
- **Tipo:** cavalo pra todos. `tipos` no config permite trocar por tier (permissão → `HORSE`, `SKELETON_HORSE`, `ZOMBIE_HORSE`, `DONKEY`, `MULE`, `CAMEL`).

## Comandos e permissões

| Comando | Ação |
|---|---|
| `/montaria` | Chama a montaria, ou manda ela embora |
| `/montaria reload` | (staff, `montaria.admin`) Recarrega o config |

| Permissão | Padrão | Pra quê |
|---|---|---|
| `vip.perk.montaria` | op | Usar a montaria (os grupos de VIP dão) |
| `montaria.admin` | op | Recarregar o config |

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/MontariaPlugin.jar`. Requisitos: Paper 26.2 (o RankupPlugin é opcional: sem ele, nenhuma mina é proibida).
