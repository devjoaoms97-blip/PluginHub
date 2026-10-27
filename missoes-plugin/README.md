# MissoesPlugin

Missões diárias e semanais: todo dia cada jogador recebe **3 missões sorteadas** de um pool configurável, e toda semana **2 missões semanais** maiores. Ao completar, ganha **coins** (e opcionalmente **XP de skill**). Completar todas do dia (ou da semana) dá um bônus.

É uma das fontes de dinheiro novo da economia, junto com a venda pra loja e o level up de skill, e equilibra as formas de dinheiro sair de circulação (taxa do Pix, imposto da loja, aluguel das barracas). Acompanhe quanto as missões injetam com o `/economia` do PainelEconomiaPlugin.

## Comandos

| Comando | Quem | Ação |
|---|---|---|
| `/missoes` | todos | Abre o menu com as missões de hoje e da semana: progresso, recompensa e tempo até as próximas. Missão concluída brilha |
| `/missoes lista` | todos | O mesmo, no chat |
| `/missoes admin resortear <jogador> [semanal]` | staff | Sorteia de novo as missões de hoje (ou da semana) do jogador, zerando o progresso |
| `/missoes admin reload` | staff | Recarrega o `config.yml` e o pool |

Aliases: `/missao`, `/quests`. Permissões: `missoes.usar` (padrão: todos) e `missoes.admin` (padrão: op).

## Tipos de missão

| Tipo | Conta quando | Alvos |
|---|---|---|
| `minerar` | Quebra um bloco que **não** foi colocado por jogador | Materiais (`STONE`, `IRON_ORE`, `OAK_LOG`...) |
| `colher` | Quebra uma plantação **madura** | Materiais da planta (`WHEAT`, `CARROTS`, `POTATOES`...) |
| `matar` | Mata um mob (mob de **spawner** não conta, por padrão) | Tipos de entidade (`ZOMBIE`, `CREEPER`...) |
| `pescar` | Pesca alguma coisa (1 por pescaria) | Materiais do que veio no anzol |
| `vender` | Vende itens pra loja do servidor (EconomyShopPlugin), contando os itens | Materiais |
| `dano_boss` | Causa dano no boss mundial (BossPlugin), contando os pontos de dano | Tipo do mob do boss (`ZOMBIE`...) |
| `vender_barraca` | Vende pela barraca (BarracasPlugin): conta o dono quando a loja dele vende e o jogador quando vende pra uma loja, contando os itens | Materiais |
| `subir_rank` | Sobe de rank (RankupPlugin). Só conta quando o rank sobe | — |
| `minerar_mina` | Quebra um bloco numa mina do RankupPlugin | IDs das minas (`pedreira`, `ferro`...) |
| `pagar_imposto` | Paga o imposto de um terreno (TerrenosPlugin) | — |
| `reparar` | Repara itens no Ferreiro (OficinasPlugin), contando os itens | Materiais do item (`DIAMOND_PICKAXE`...) |
| `encantar` | Encanta itens na mesa da Bruxa ou compra um encantamento no Cambista (OficinasPlugin), contando os itens | Materiais do item |

Sem `alvos`, qualquer um conta. Com `rank-minimo`, a missão só é sorteada pra quem já tem acesso àquele rank (ex: a mina de diamante só pra quem chegou nela). Duas missões do mesmo tipo podem contar ao mesmo tempo: matar um zumbi conta pra "matar 20 zumbis" e pra "matar 50 mobs". Uma diária e uma semanal também contam juntas.

**Anti-farm da missão de vender:** o que o jogador **comprou** da loja no mesmo dia não conta quando ele vende de volta. Senão bastava comprar e revender.

**Dano no boss** usa o mesmo critério do ranking de dano do BossPlugin: só o dano que entrou, sem contar o excesso do golpe final. Como o boss só aparece em alguns horários, essa missão fica melhor no pool semanal.

O progresso aparece na barra de ação enquanto o jogador faz a missão. Ao completar, o pagamento é na hora, com mensagem e som.

## Missão extra de VIP

Quem tem a permissão `vip.missao.extra` (VIP Ouro e Diamante, pelos grupos do LuckPerms) recebe uma missão diária a mais. Para ganhar o bônus de completar todas, precisa completar as 4. Quem vira VIP no meio do dia ganha a extra na hora, sem perder o progresso das outras. Configurado em `vip-missoes-extras`.

## Missões semanais

Funcionam igual às diárias, com um pool próprio (`semanais.pool`) e um bônus próprio. Elas trocam na virada de domingo pra segunda, no horário do servidor. O `config.yml` já vem com 5 semanais de exemplo: diamante, mobs, dano no boss, venda e pesca. Pra desligar, use `semanais.ativado: false`.

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

gui:
  ativado: true      # false: /missoes mostra no chat

semanais:
  ativado: true
  quantidade: 2
  bonus-todas:
    coins: 2000
  pool:
    - id: semanal_boss
      tipo: dano_boss
      quantidade: 500
      descricao: "Causar 500 de dano no boss mundial"
      coins: 3000
```

O `config.yml` já vem com 13 missões diárias e 5 semanais de exemplo. Os valores de coins são **provisórios**: ajuste conforme o que o `/economia` mostrar.

Mudar o pool e rodar `/missoes admin reload` vale pros próximos sorteios. Quem já sorteou mantém as missões até o dia (ou a semana) virar.

Num servidor que já tinha o `config.yml` da versão anterior, as semanais aparecem mesmo assim, com os valores padrão. Pra mudar, copie a seção `semanais` pro seu config.

## Integrações

- **SkillsPlugin** (opcional): a recompensa `skill` + `xp` vai pro XP da skill, por reflexão, e pode até fazer subir de nível. Sem o SkillsPlugin, o XP é ignorado e só os coins são pagos.
- **EconomyShopPlugin** (opcional): avisa as vendas e compras pra missão de vender, chamando o `MissoesPlugin` por reflexão (`registrarProgresso` e `registrarCompraNaLoja`).
- **BossPlugin** (opcional): o boss é reconhecido pela chave compartilhada `pluginhub:boss_mob`. O BossPlugin não precisou mudar.
- **PixPlugin** (opcional): a recompensa aparece no extrato do Bancário como "Missão diária" ou "Missão semanal".
- **PainelEconomiaPlugin**: os coins pagos aparecem como `MissoesPlugin` no fluxo por origem.

## Persistência

O progresso fica em `plugins/MissoesPlugin/progresso.yml` (diárias em `jogadores`, semanais em `semanais`). É gravado a cada 5 min e no desligamento, e o de períodos anteriores é descartado. Se o servidor **cair** (não desligar normalmente), perde-se no máximo 5 min de progresso. Recompensa já paga nunca é paga de novo.

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
3. **O controle de "comprou e revendeu" fica em memória e vale só pro mesmo dia.** Comprar hoje e vender amanhã conta, e um restart zera o controle. Quem compra pra revender perde o spread da loja e o imposto, então o ganho é pequeno.
4. **A missão de vender só conta vendas pra loja do servidor**, não as vendas nas barracas entre jogadores.
