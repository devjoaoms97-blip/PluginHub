# MontariaPlugin

Montaria do VIP no spawn. No mundo `spawn` não dá pra voar, planar nem usar pérola (AreasPlugin), e a Avenida das Minas tem uns 230 blocos: a montaria deixa quem é VIP andar mais rápido entre o spawn, as minas, a loja e a warp mobs.

## Como funciona

- `/montaria` (ou `/cavalo`) chama um cavalo e já monta nele. De novo, ele vai embora.
- **Só VIP:** precisa da permissão `vip.perk.montaria`, que os grupos de VIP do LuckPerms dão (todos os tiers).
- **Velocidade:** `velocidade-vezes-corrida` (3) vezes a corrida a pé, 16,8 blocos por segundo, bem mais que qualquer cavalo do jogo. O fim da avenida das minas fica a uns 13 s do spawn, em vez de 40 s.
- **Pulo baixo** (`pulo`: 0.5): sobe um degrau, mas não pula balcão nem barraca.
- **Onde:** só nos `mundos` do config (o `spawn`). Nos poços das minas do RankupPlugin e nas caixas de `proibido` (a arena do boss, que tem PvP) ela não é chamada, e quem entra montado desce na hora e segue a pé.
- **É descartável:** some quando o jogador desce, sai do servidor, se teleporta, troca de mundo ou toma dano. Ninguém mais monta nela, ela não tem baú nem inventário, não toma dano e não fica salva no mundo.
- **Recarga:** `recarga-segundos` (5) entre chamar uma e outra.
- **Tipo:** cavalo pra todos. `tipos` no config permite trocar por tier (permissão → `HORSE`, `SKELETON_HORSE`, `ZOMBIE_HORSE`, `DONKEY`, `MULE`, `CAMEL`).

## Skins de montaria

Compradas com créditos no CreditosPlugin (seção Montarias da loja). Com uma skin em uso, o `/montaria` chama sempre um cavalo, **invisível** e com a sela escondida (equipamento `servidor:invisivel` do resource pack, sem camada), e põe o bicho em cima. Velocidade, pulo e todas as regras de cima continuam iguais.

- **Modelos animados (`fmm-modelo`):** o FreeMinecraftModels desenha um modelo do Blockbench, com as animações dele, no cavalo (`DynamicEntity.createWithInvisibility`, por reflexão: sem o FMM, a skin sai sem modelo), com o dano por contato desligado. Hoje: rena, bode, caranguejo, peixe do Estige e biplano (FrenchKrab, CC BY 4.0; os `.bbmodel` ficam em `tools/pacote/terceiros/frenchkrab` e vão em `plugins/FreeMinecraftModels/models`).
- **Modelos de caixas (sem `fmm-modelo`):** os bichos do `tools/pacote/montarias.py`, num item display que vira com o cavalo e troca de quadro andando. Ficaram fora da loja (não ficaram bons); o código fica.
- `skins.<id>` no config: `fmm-modelo`, `escala-do-cavalo` (o tamanho do cavalo invisível, que muda a altura do assento; o FMM encolhe o modelo junto: o caranguejo usa 0.5), e, nos de caixas, `ajuste-altura`, `anima-parado`, `ticks-por-quadro` e `particula`. `skins-giro` gira os de caixas (180 por padrão).
- O CreditosPlugin e o FreeMinecraftModels são opcionais: sem o primeiro, ninguém tem skin.

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

O `.jar` sai em `target/MontariaPlugin.jar`. Requisitos: Paper 26.2 (o RankupPlugin é opcional: sem ele, nenhuma mina é proibida; o CreditosPlugin, o PacotePlugin e o FreeMinecraftModels, pras skins).
