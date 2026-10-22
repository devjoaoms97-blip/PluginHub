# MissoesPlugin

Missões diárias: todo dia cada jogador recebe **3 missões sorteadas** de um pool configurável e ganha **coins** (e opcionalmente **XP de skill**) ao completar. Completar as três dá um bônus.

É uma das fontes de dinheiro novo da economia, junto com a venda pra loja e o level up de skill, e equilibra as formas de dinheiro sair de circulação (taxa do Pix, imposto da loja, aluguel das barracas). Acompanhe quanto as missões injetam com o `/economia` do PainelEconomiaPlugin.

## Comandos

| Comando | Quem | Ação |
|---|---|---|
| `/missoes` | todos | Suas missões de hoje, com barra de progresso, recompensa e tempo até as próximas |
| `/missoes admin resortear <jogador>` | staff | Sorteia de novo as missões de hoje do jogador (zera o progresso) |
| `/missoes admin reload` | staff | Recarrega o `config.yml` e o pool |

Aliases: `/missao`, `/quests`. Permissões: `missoes.usar` (padrão: todos) e `missoes.admin` (padrão: op).

## Tipos de missão

| Tipo | Conta quando | Alvos |
|---|---|---|
| `minerar` | Quebra um bloco que **não** foi colocado por jogador | Materiais (`STONE`, `IRON_ORE`, `OAK_LOG`...) |
| `colher` | Quebra uma plantação **madura** | Materiais da planta (`WHEAT`, `CARROTS`, `POTATOES`...) |
| `matar` | Mata um mob (mob de **spawner** não conta, por padrão) | Tipos de entidade (`ZOMBIE`, `CREEPER`...) |
| `pescar` | Pesca alguma coisa (1 por pescaria) | Materiais do que veio no anzol |

Sem `alvos`, qualquer um conta. Duas missões do mesmo tipo podem contar ao mesmo tempo: matar um zumbi conta pra "matar 20 zumbis" e pra "matar 50 mobs".

O progresso aparece na barra de ação enquanto o jogador faz a missão. Ao completar, o pagamento é na hora, com mensagem e som.

## Configuração

```yaml
missoes-por-dia: 3

bonus-todas:
  coins: 300
  skill: AGILIDADE   # opcional
  xp: 150

anti-farm:
  ignorar-mobs-de-spawner: true

pool:
  - id: minerar_ferro
    tipo: minerar
    alvos: [IRON_ORE, DEEPSLATE_IRON_ORE]
    quantidade: 24
    descricao: "Minerar 24 minérios de ferro"
    coins: 300

  - id: matar_zumbis
    tipo: matar
    alvos: [ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER]
    quantidade: 20
    descricao: "Matar 20 zumbis"
    coins: 250
    skill: ESPADAS   # opcional: XP de skill do SkillsPlugin
    xp: 200
```

O `config.yml` já vem com 12 missões de exemplo. Os valores de coins são **provisórios**: ajuste conforme o que o `/economia` mostrar.

Mudar o pool e rodar `/missoes admin reload` vale pros próximos sorteios. Quem já sorteou hoje mantém as missões até a meia-noite.

## Integrações

- **SkillsPlugin** (opcional): a recompensa `skill` + `xp` vai pro XP da skill, por reflexão, e pode até fazer subir de nível. Sem o SkillsPlugin, o XP é ignorado e só os coins são pagos.
- **PixPlugin** (opcional): a recompensa aparece no `/pix historico` como "Missão diária".
- **PainelEconomiaPlugin**: os coins pagos aparecem como `MissoesPlugin` no fluxo por origem.

## Persistência

O progresso do dia fica em `plugins/MissoesPlugin/progresso.yml`. É gravado a cada 5 min e no desligamento, e o de dias anteriores é descartado. Se o servidor **cair** (não desligar normalmente), perde-se no máximo 5 min de progresso. Recompensa já paga nunca é paga de novo.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia (sem ele, as missões contam, mas não pagam coins)
- SkillsPlugin e PixPlugin (opcionais)

## Como compilar

```bash
cd missoes-plugin
mvn clean package
```

O `.jar` sai em `target/MissoesPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **A lista de blocos colocados por jogador fica em memória** e guarda os 100 mil mais recentes. Depois de um restart, um bloco colocado antes volta a contar como mineração. Isso é aceitável porque o farm de colocar e quebrar exige ficar recolocando os blocos.
2. **Cana, cacto e bambu** usam a mesma "idade" das plantações, mas raramente estão no máximo quando são quebrados. Por isso não contam bem nem como `colher` nem como `minerar`. Prefira trigo, cenoura, batata e beterraba nas missões de colher.
3. **Não tem missão de vender na loja ou de bater no boss.** Isso precisaria de integração com esses plugins e fica pra uma próxima versão.
