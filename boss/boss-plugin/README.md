# BossPlugin

Boss mundial agendado, com fases dinâmicas, loot configurável em jogo e tag de campeão via LuckPerms.

## O que ele faz

1. **Agenda recorrente + comando manual**: você configura dias/horários fixos no `config.yml`, e também pode forçar com `/boss start` a qualquer momento
2. **Boss gigante de verdade**: escala aumentada via atributo do próprio jogo (não é só cosmético), vida/dano configuráveis, resistente a knockback
3. **Barra de vida nativa** (igual Wither/Ender Dragon), visível pra todo mundo, atualizada em tempo real
4. **Fases por % de vida e por tempo**: invoca mobs, aplica efeitos em área nos jogadores, se buffa, ou manda mensagens dramáticas — tudo configurável sem recompilar
5. **Loot do chão**: itens com chance independente cada, cadastrados em jogo (`/boss additem`)
6. **Prêmio do campeão**: sorteio ponderado entre itens cadastrados (`/boss addchampionitem`) — vai direto pro inventário de quem der o último hit
7. **Tag de campeão via LuckPerms de verdade**: um grupo com prefixo (ex: `[Herói]`) passa automaticamente de quem tinha antes pra quem acabou de matar o boss

## Requisitos

- Paper 26.2 (Java 25)
- **LuckPerms** (pra tag de campeão funcionar — sem ele, o resto do plugin funciona normal, só a tag fica desativada)
- **SkillsPlugin** (opcional, mas recomendado) — se instalado, o boss fica automaticamente imune ao Atordoamento do Marreteiro e ao Empurrão do Tridente

## Comandos

| Comando | Ação |
|---|---|
| `/boss start` | Inicia o boss manualmente |
| `/boss stop` | Remove o boss ativo |
| `/boss setlocal` | Define a arena na sua posição atual |
| `/boss additem <chance%>` | Adiciona o item na mão ao loot do chão |
| `/boss addchampionitem <peso%>` | Adiciona o item na mão ao sorteio de prêmio do campeão |
| `/boss reload` | Recarrega o `config.yml` |

Todos exigem a permissão `boss.admin` (padrão: op).

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
