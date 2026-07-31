# SkillsPlugin

Sistema de skills de combate estilo RPG, com 11 skills, progressão por nível, GUI visual e recompensas em dinheiro.

## Skills incluídas

| Skill | Ganha XP com | Bônus no nível 50 |
|---|---|---|
| Espadas | Dano com espada | +25% dano |
| Machado | Dano com machado | +20% dano |
| Arqueiro | Dano com arco/besta | +25% dano |
| Porradeiro | Dano com mão vazia | +30% dano, 10% chance de atordoar |
| Tridente | Dano com tridente | +25% dano |
| Esquiva | Tomar dano se movendo | 20% chance de esquiva total |
| Defesa | Tomar dano e sobreviver | -15% dano recebido |
| Bloqueio | Bloquear com escudo levantado | -20% a mais no bloqueio |
| Crítico | Acertar golpe crítico | +15% no dano crítico |
| Regeneração | Tempo em combate recente | +50% velocidade de regen natural |
| Agilidade | Se mover durante combate | +10% velocidade de movimento |

- **PvP conta XP cheio; PvE (mobs) conta metade** — configurável em `xp.multiplicador-pve`
- **Nível máximo: 50** por skill, com curva de XP progressiva — configurável em `config.yml`
- **Persistência real**: os dados ficam salvos em `plugins/SkillsPlugin/playerdata/<uuid>.yml`, sobrevivem a reinício do servidor

## Comandos

| Comando | Ação |
|---|---|
| `/skills` | Abre o menu visual (GUI) com todas as skills e barras de progresso |
| `/skills ver` | Mostra um resumo rápido no chat |
| `/skills admin setlevel <jogador> <skill> <nível>` | (staff) Define manualmente o nível de uma skill de um jogador. Requer permissão `skills.admin` (padrão: op) |

Nomes de skill aceitos no comando admin: `espadas`, `machado`, `arqueiro`, `porradeiro`, `tridente`, `esquiva`, `defesa`, `bloqueio`, `critico`, `regeneracao`, `agilidade`.

Ao usar `setlevel`, o XP acumulado daquela skill é zerado (já que o "degrau" mudou manualmente, não faria sentido manter XP de um nível diferente).

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia (opcional — sem ele, as skills funcionam, só não dão dinheiro ao subir de nível)

## Como compilar

```bash
cd skills-plugin
mvn clean package
```

O `.jar` sai em `target/SkillsPlugin.jar`.

## ⚠️ Simplificações técnicas (importante entender)

1. **Detecção de golpe crítico**: o Bukkit não expõe diretamente se um hit foi "crítico" (esse cálculo acontece internamente no jogo antes do evento disparar). Uso uma aproximação baseada nas condições clássicas do crítico vanilla (jogador caindo, fora do chão, sem sprint). Na grande maioria dos casos bate certinho com o crítico real, mas pode ocasionalmente divergir em situações bem específicas (ex: lag de rede).

2. **Ordem de aplicação de dano**: aplico os modificadores (bônus de arma, crítico, esquiva, bloqueio, defesa) diretamente em `event.setDamage()`, sem usar o sistema interno de `DamageModifier` do Bukkit (que separa dano base/armadura/encantamento/etc). Isso é mais simples de manter e funciona bem na prática, mas significa que os bônus das skills se aplicam **depois** da armadura/encantamentos vanilla já terem sido calculados pelo jogo.

3. **Tridente**: só dá bônus de dano por enquanto. Reduzir o cooldown do "riptide" exigiria acessar APIs mais internas do NMS, então deixei de fora por ora — dá pra adicionar depois se quiser.

## Ideias para expandir

- Efeito visual diferente pra cada tipo de level up (ex: partícula própria por skill)
- Prestígio: ao chegar no nível 50, permitir "resetar" a skill em troca de um bônus permanente extra
- Scoreboard/placar lateral mostrando XP da skill que está sendo usada no momento
- Skill de "Escudo Perfeito" (parry): se o bloqueio acontecer no exato milissegundo do golpe, anula 100% do dano e atordoa o atacante
