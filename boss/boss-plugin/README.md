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
9. **Contagem regressiva** no chat antes do spawn agendado (padrão: 10, 5 e 1 min antes)
10. **Ranking de dano**: quando o boss morre, o chat mostra o top 3 de quem mais bateu nele
11. **Discord**: com o DiscordPlugin, o aparecimento e a morte (campeão e ranking de dano) vão para a categoria `boss`
12. **Recompensas**: coins pro top 3 do ranking de dano e pro campeão, XP de skill pra todo mundo que bateu (proporcional ao dano, na skill da arma usada: espada, machado, arco, tridente, lança, maça ou soco) e, opcional, um código de VIP pro campeão
13. **`/boss proximo`**: qualquer jogador vê quando o próximo boss da agenda aparece

## Requisitos

- Paper 26.2 (Java 25)
- **LuckPerms** (pra tag de campeão funcionar)
- **SkillsPlugin** (opcional, recomendado) — se instalado, o boss fica imune ao Atordoamento/Empurrão

## Comandos

| Comando | Ação |
|---|---|
| `/boss proximo` | Quando o próximo boss da agenda aparece (**qualquer jogador**) |
| `/boss start` | Inicia um boss **aleatório** entre os normais (não especiais) |
| `/boss start <id>` | Inicia um perfil específico (ex: `/boss start wither`) |
| `/boss list` | Lista os perfis configurados e seus ids |
| `/boss stop` | Remove o boss ativo |
| `/boss setlocal [mundo x y z]` | Define a arena na sua posição atual (ou, pelo console, no lugar informado) |
| `/boss additem <chance%>` | Adiciona o item na mão ao loot do chão |
| `/boss addchampionitem <peso%>` | Adiciona o item na mão ao sorteio de prêmio do campeão |
| `/boss reload` | Recarrega o `config.yml` |

## Recompensas

```yaml
recompensas:
  ranking-dano: [15000, 10000, 5000]   # coins pro 1º, 2º e 3º (lista vazia desativa)
  campeao-dinheiro: 20000               # coins pro campeão, além do item (0 desativa)
  xp-skill-maximo: 3000                 # XP de skill de quem tirou 100% da vida do boss (0 desativa)
  xp-skill-minimo: 100                  # mínimo pra quem bateu pelo menos um pouco
  codigo-vip:                           # código de VIP de evento, 1 uso, pro campeão (VipPlugin)
    ativado: true
    bosses: [wither]                    # só esses bosses dão (vazio = todos)
    tier: bronze
    dias: 7                             # 3, 7 ou 15 (sem bônus de ativação nem kit mensal; não dá pra vender)
    validade-dias: 7                    # prazo pra resgatar (0 = não expira)
```

- O dinheiro vem do servidor, então entra dinheiro novo na economia. Sem o Vault, só o dinheiro fica de fora.
- O XP é dividido entre as skills das armas que o jogador usou, na proporção do dano de cada uma. Só ganha quem está online quando o boss morre.

## Tempo limite e maldição

O boss fica vivo por `tempo-limite-minutos` (60). O chat avisa antes (`avisos-fuga-minutos`: 10 e 1 min). Se ninguém derrotar, ele vai embora e sai a frase global de `maldicao.mensagem` (também no Discord): **o mal reina sobre o servidor**. Por `maldicao.horas` (24), todo XP de skill vale `maldicao.xp-skill-multiplicador` (0,9, ou seja, −10%), o mesmo XP que o XP em dobro mexe (combate, missões e boss; o bônus de ativação do VIP fica de fora). Derrotar o próximo boss quebra a maldição na hora. Ela fica em `maldicao.yml` (sobrevive a reinício), o `/boss proximo` mostra quanto falta e quem entra durante ela é avisado. O `/boss stop` da staff não amaldiçoa. Vale igual pro Wither.

Enquanto o boss vive, os chunks da arena ficam carregados. Antes, sem ninguém perto, o chunk descarregava e o boss sumia sem morrer nem ir embora.

## Boss elite

A cada boss aleatório (agenda ou `/boss start` sem id), há `elite.chance` (10%) de ele vir **elite**: `elite.multiplicador-vida` (2×) de vida, `elite.multiplicador-dano` (2×) em tudo o que ele causa (golpe, flechas da chuva, onda de choque e explosão), brilho, o prefixo `elite.prefixo-nome` no nome, barra roxa e anúncio próprio (`elite.mensagem-spawn`, também no Discord). As coins do ranking e do campeão e o XP de skill por participar saem multiplicados por `elite.multiplicador-recompensas` (2×), e o loot do chão é sorteado `elite.rolagens-loot` (2) vezes. A staff força com `/boss start [id] elite`.

## Arena pronta

A arena do boss pode ser construída pelo EstruturasPlugin: `/estrutura construir toxica`. Quando termina, ele já roda o `/boss setlocal` no centro dela.

O boss não sai da arena: se passar de `arena-raio` (46) blocos do centro, na horizontal, ou de `arena-altura` (55) acima dele, volta pro centro com partículas de portal. Isso segura o Wither, que voa e escapava pela escadaria da entrada (o degrau da chegada só segura quem anda). O teleporte de ataque também só mira quem está dentro da arena. `arena-raio: 0` desliga.

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

Todos, menos o `/boss proximo`, exigem a permissão `boss.admin` (padrão: op).

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
  - "SEGUNDA 20:00"         # sorteia um boss normal
  # ... TERCA a SABADO
  - "DOMINGO 20:00 wither"  # boss fixo: o Wither, especial
```
`DIA HH:mm` sorteia entre os bosses normais; `DIA HH:mm <boss>` sobe aquele boss (é assim que o Wither entra na agenda). O `/boss proximo` mostra o nome quando o próximo é fixo. Deixe a lista vazia se quiser controlar só na mão com `/boss start`.

### 6. Contagem regressiva e ranking de dano (opcional)
```yaml
aviso-agenda:
  minutos-antes: [10, 5, 1]   # [] desativa
  mensagem: "&e&l⚠ &eUm boss vai aparecer em &f%minutos% min&e! Preparem-se."

ranking-dano:
  ativado: true
  top: 3
```
- A contagem só vale pros horários da `agenda`. O `/boss start` manual continua instantâneo.
- O ranking soma o dano que entrou de verdade: golpe direto ou projétil (flecha, tridente), já com os bônus do SkillsPlugin. O golpe final conta só a vida que faltava. Cada linha mostra o dano total e o % da vida máxima do boss.
- O **campeão continua sendo quem deu o último hit**. O ranking é só reconhecimento e não muda prêmio nem tag.
- Dano sem atacante, como o Sangramento do SkillsPlugin, não entra no ranking.

### 7. Ajustar as fases (opcional)
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

5. **Reiniciar no meio da luta perde o boss**: ao desligar, o boss vivo sai do mundo (a luta não continua depois). E se o servidor cair com o boss vivo, ele é removido quando o chunk da arena carrega: um boss "órfão", que o plugin não conhece, ficaria sem barra e sem ranking, matando quem chegasse.

6. **Loot persiste em `plugins/BossPlugin/loot.yml`**: sobrevive a reinícios do servidor, então você só precisa cadastrar os itens uma vez (a menos que queira mudar depois).

## Ideias para expandir

- Integração com o Pix/economia: recompensa em dinheiro além do item
