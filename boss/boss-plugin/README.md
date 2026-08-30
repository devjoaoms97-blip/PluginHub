# BossPlugin

Boss mundial agendado, com **múltiplos perfis de boss** (um por mob), fases dinâmicas, arsenal de ataques aleatórios, loot configurável em jogo e tag de campeão via LuckPerms.

## O que ele faz

1. **7 perfis de boss**: 6 normais (Zumbi, Esqueleto, Aranha, Zumbi Porco, Creeper, Bruxa) que entram na aleatoriedade da agenda/comando, + 1 especial (Wither) que só sobe forçado (`/boss start wither`)
2. **Cada perfil tem nome, fases e arsenal de ataques próprios** — definidos no `config.yml`, sem precisar recompilar
3. **Agenda recorrente + comando manual**: dias/horários fixos, e também `/boss start [id]` a qualquer momento
4. **Boss gigante e mais perigoso**: escala aumentada de verdade, velocidade 50% acima do padrão, alcance de perseguição maior, resistente a knockback
5. **Barra de vida nativa** (igual Wither/Ender Dragon), visível pra todo mundo
6. **Fases fixas por % de vida e por tempo** (marcos da luta) **+ arsenal de ataques aleatórios** (dispara a cada X segundos, sorteando um ataque do próprio arsenal do mob) — isso é o que dá o "perigo constante" durante a luta toda, não só em marcos pontuais
7. **Loot do chão + prêmio exclusivo do campeão** (sorteio ponderado)
8. **Tag de campeão via LuckPerms**

## Requisitos

- Paper 26.2 (Java 25)
- **LuckPerms** (pra tag de campeão funcionar)
- **SkillsPlugin** (opcional, recomendado) — se instalado, o boss fica imune ao Atordoamento/Empurrão

## Comandos

| Comando | Ação |
|---|---|
| `/boss start` | Inicia um boss **aleatório** entre os normais (não especiais) |
| `/boss start <id>` | Inicia um perfil específico (ex: `/boss start wither`) |
| `/boss list` | Lista os perfis configurados e seus ids |
| `/boss stop` | Remove o boss ativo |
| `/boss setlocal` | Define a arena na sua posição atual |
| `/boss additem <chance%>` | Adiciona o item na mão ao loot do chão |
| `/boss addchampionitem <peso%>` | Adiciona o item na mão ao sorteio de prêmio do campeão |
| `/boss reload` | Recarrega o `config.yml` |

## Tipos de ação disponíveis (fases e arsenal aleatório usam os mesmos)

| Ação | O que faz |
|---|---|
| `invocar_mobs` | Spawna reforços ao redor do boss |
| `efeito_area` | Aplica um efeito de poção nos jogadores num raio |
| `buff_boss` | Aplica um efeito de poção no próprio boss |
| `mensagem` | Manda uma mensagem dramática no chat |
| `investida` *(novo)* | Avança rápido na direção do jogador mais próximo, empurrando no impacto |
| `chuva_projeteis` *(novo)* | Flechas caem do céu ao redor de jogadores próximos |
| `onda_de_choque` *(novo)* | Empurra e causa dano em todo mundo num raio ao redor do boss |
| `curar` *(novo)* | O boss recupera uma % da vida máxima (mecânica de fúria/desespero) |
| `teleporte_ataque` *(novo)* | Teleporta pra perto de um jogador aleatório e desfere um golpe surpresa |

Todos exigem a permissão `boss.admin` (padrão: op).

## ⚠️ Atualizando de uma versão anterior

O `config.yml` mudou de estrutura (agora tem uma seção `bosses:` com os 7 perfis, em vez de um único `nome`/`fases`/`mobs-possiveis` no topo). **Substitua o `config.yml` inteiro pelo novo.** Como consequência:

- **A arena precisa ser redefinida** (`/boss setlocal`) — o arquivo novo não tem a seção `arena:` que foi salva automaticamente antes
- **O campeão atual "reseta"** (`campeao-atual-uuid` também não vem no arquivo novo) — não é grave, o próximo boss morto já define um campeão de novo normalmente

## Configuração inicial (passo a passo)

### 1. Definir a arena
Vá até o local onde quer que o boss apareça e rode:
```
/boss setlocal
```

### 2. Criar o grupo de campeão no LuckPerms
```
/lp creategroup boss_campeao
/lp group boss_campeao meta setprefix "&6[Herói] "
/lp group boss_campeao setweight 10
```
(o nome do grupo precisa bater com `campeao.grupo-luckperms` no `config.yml`, que já vem como `boss_campeao` por padrão)

### 3. Cadastrar o loot do chão
Segure o item na mão e rode, por exemplo:
```
/boss additem 25
```
(25% de chance desse item específico cair, independente dos outros — pode adicionar quantos itens quiser)

### 4. Cadastrar os prêmios do campeão
Segure o item na mão e rode, por exemplo:
```
/boss addchampionitem 60
/boss addchampionitem 30
/boss addchampionitem 10
```
Ao morrer, o boss sorteia **exatamente 1** desses itens pra dar ao campeão — as porcentagens funcionam como peso relativo (não precisam somar 100%, são normalizadas automaticamente).

### 5. Ajustar a agenda (opcional)
No `config.yml`:
```yaml
agenda:
  - "SEXTA 20:00"
  - "SABADO 21:00"
```
Deixe a lista vazia se quiser controlar só na mão com `/boss start`.

### 6. Ajustar as fases (opcional)
Já vem um exemplo funcional no `config.yml` — veja os comentários lá pra editar quais ações disparam em qual % de vida ou intervalo de tempo.

## Como compilar

```bash
cd boss-plugin
mvn clean package
```

O `.jar` sai em `target/BossPlugin.jar`.

## ⚠️ Simplificações e observações técnicas

1. **Imunidade a controle (Atordoamento/Empurrão)**: implementada via uma marca compartilhada (`PersistentDataContainer` com chave `pluginhub:boss_mob`) que o SkillsPlugin também reconhece. Os dois plugins **não têm dependência direta** entre si — só compartilham essa convenção de chave. Se você editar o SkillsPlugin no futuro e quiser que outro efeito também ignore o boss, é só checar essa mesma chave.

2. **Um boss ativo por vez**: `/boss start` (manual ou pela agenda) é recusado se já tiver um boss vivo. Isso evita spawns duplicados acidentais.

3. **Fases por % de vida disparam uma única vez**: ao cruzar o percentual configurado pela primeira vez. Se a vida "balançar" pra cima e pra baixo (curou, por exemplo), a fase não dispara de novo.

4. **`getKiller()` do Bukkit**: uso o método nativo do jogo pra saber quem deu o último hit — é o mesmo mecanismo confiável usado pra qualquer mob morrer "por" um jogador no Minecraft vanilla.

5. **Loot persiste em `plugins/BossPlugin/loot.yml`**: sobrevive a reinícios do servidor, então você só precisa cadastrar os itens uma vez (a menos que queira mudar depois).

## Ideias para expandir

- Boss "elite" com raridade maior de vez em quando (dobro de vida/dano, loot melhor)
- Múltiplos perfis de boss configuráveis (não só um), sorteados ou escolhidos por comando
- Aviso de contagem regressiva antes do spawn agendado (10 min, 5 min, 1 min)
- Ranking dos jogadores que mais causaram dano no boss (não só o último hit)
- Integração com o Pix/economia: recompensa em dinheiro além do item
