# TesourosPlugin

Mapas do tesouro. O mapa traz as coordenadas de um ponto do mundo superplano `tesouros`. Quem cava o chão bem no X abre uma câmara enterrada, cheia de guardiões que vêm em ondas, com um baú no centro que só destrava quando o último guardião morre.

## De onde vêm os mapas

- **Boss:** sempre deixa 1 mapa no chão quando morre. A raridade é sorteada pelos pesos de `drops.boss.pesos`. O plugin reconhece o boss pela marca `pluginhub:boss_mob`, sem dependência do BossPlugin.
- **Minas:** cada bloco quebrado numa mina do RankupPlugin tem a chance `drops.mina.chance-por-bloco` (padrão 1 em 20.000, uns 5 h de mina) de dar um mapa, que vai direto pro inventário. O lendário é anunciado no chat.
- **Staff:** `/tesouro dar <jogador> <raridade> [x z]`.

As raridades são **comum**, **raro**, **arcano** e **lendário**. O item é um papel com a cara de mapa, marcado com id, raridade e coordenadas: renomear outro item não falsifica. O mapa pode ser vendido, trocado e posto em barraca, e quem estiver com ele cava. Cada id abre uma câmara só (`usados.yml`), então um mapa duplicado por bug não vale de novo.

## Como funciona

1. `/tesouro ir` leva ao mundo dos tesouros. Com o mapa na mão, a barra acima do inventário mostra quantos blocos faltam até o X.
2. No mundo dos tesouros ninguém quebra nem coloca bloco. O único bloco que se cava é o chão do X, por quem tem aquele mapa no inventário. O mapa é gasto na hora.
3. Embaixo do X, a câmara é montada: uma sala de 15×15 com 6 de altura, 18 blocos abaixo do chão. O dono do mapa cai nela, e quem pode entrar e está a até 6 blocos (`camara.raio-puxar`) cai junto.
4. **Quem pode entrar:** o dono, o clã dele (ClasPlugin) e quem ele convidou com `/tesouro convidar <jogador>` (o convite vale até o dono sair do servidor). Eles entram pisando no buraco do X enquanto a câmara está aberta. Os outros são barrados no buraco, e ninguém entra por teleporte.
5. **Guardiões:** as ondas de `raridades.<raridade>.ondas`, com vida e dano multiplicados, e um chefe no fim do arcano e do lendário. Uma barra de boss mostra a onda, quantos guardiões faltam e o tempo. Os guardiões não deixam itens, só XP.
6. **Baú:** fica trancado enquanto houver guardião. Depois, só o dono do mapa abre. Na primeira vez ele recebe as coins (registradas no extrato), e do baú só se tira. A câmara fica aberta por `camara.tempo-para-pegar-segundos` (90 s); no fim, o que sobrou no baú vai pro inventário do dono.
7. **Fim:** `/tesouro sair` (depois que o baú destrava) leva pra superfície, ao lado do X. Quando o dono sai, a câmara fecha. Ao fechar, todo mundo sobe, e cada bloco da sala volta pra camada do superplano daquela altura.

**Regras duras:**
- **Quem morre na câmara perde tudo:** os itens e o XP somem (nada cai no chão nem vai pros achados). Se for o dono, a câmara desaba e o tesouro se perde; quem está vivo sobe com os próprios itens.
- **Sair do servidor durante a luta é morrer.** Depois que o baú destrava, o dono que sai leva o resto do tesouro pro inventário.
- **Sem fuga:** com guardião vivo, nenhum teleporte tira o jogador da câmara (comando, pérola, `/tpa`...).
- **Tempo limite** (`camara.tempo-limite-segundos`, 10 min): acabou, a câmara desaba e o tesouro se perde, sem matar ninguém.
- Duas câmaras não se abrem coladas: quem cava perto de outra aberta espera ela fechar.
- Se o servidor cair com uma câmara aberta, ela fica no `camaras.yml` e é desfeita quando o plugin liga. Quem entrar depois dentro da terra volta pra superfície.

`anunciar` lista as raridades que vão pro chat e pro Discord (categoria `tesouros` do DiscordPlugin) quando o baú destrava.

## Loot (`loot.yml`)

Cada raridade tem coins (`[mín, máx]`; iguais = valor fixo: 5 mil, 20 mil, 50 mil e 100 mil) e `rolagens` itens sorteados pelo peso, sem repetir (cada item sai no máximo uma vez por baú). Um item pode ser um material, `fragmento` (Fragmento Arcano do RankupPlugin), `livro:<encantamento>:<nível>`, `spawner:<tipo>` (SpawnersPlugin), `maquina:<tipo>` (MaquinasPlugin, nova), `combustivel` (Células de Combustível), ou qualquer item posto pela staff com `/tesouro loot add`. Quantidade maior que uma pilha vem em várias pilhas.

Os spawners e as máquinas saem do comum (spawner de Zumbi e Esqueleto) ao lendário (Golem de Ferro, Lapidadora e Reator Arcano), mais raros quanto mais caros. A máquina que sai acima do rank do jogador pode ser guardada e vendida, mas só é colocada e usada por quem tem o rank dela (ou já fez prestígio): o MaquinasPlugin bloqueia. O spawner não tem trava. **Os itens ainda vão ser calibrados.**

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/tesouro` | todos | Ajuda |
| `/tesouro ir` | todos | Vai pro mundo dos tesouros (em combate, o CombatePlugin barra) |
| `/tesouro convidar <jogador>` / `desconvidar` | todos | Deixa (ou tira) um amigo entrar nas suas câmaras |
| `/tesouro sair` | todos | Sai da câmara depois que o baú destrava |
| `/tesouro dar <jogador> <raridade> [x z]` | staff | Dá um mapa (coordenadas sorteadas ou escolhidas) |
| `/tesouro ativas` | staff | Câmaras abertas |
| `/tesouro encerrar <id\|todas>` | staff | Fecha sem tesouro |
| `/tesouro loot <raridade>` | staff | Mostra o loot |
| `/tesouro loot add <raridade> <peso> [mín máx]` | staff | Acrescenta o item da mão ao loot |
| `/tesouro loot remover <raridade> <chave>` | staff | Tira um item do loot |
| `/tesouro reload` | staff | Recarrega o config e o loot |

Permissões: `tesouros.usar` (todos) e `tesouros.admin` (op). Com `tesouros.admin` no criativo, dá pra quebrar e construir no mundo.

## Mundo

O `configurar-mundos.ps1` cria o `tesouros`: superplano de bedrock, 40 de pedra, 3 de terra e grama (o chão fica em y -20), sem estruturas, PvP nem mobs naturais, e sempre de dia. As camadas de `camadas` no config precisam ser as mesmas do mundo. As coordenadas são sorteadas entre `coordenadas.raio-minimo` e `raio-maximo` em volta do 0, 0, e a borda fica 100 blocos depois do raio máximo.

## Integrações

Todas são por reflexão e opcionais: RankupPlugin (mina e fragmento), ClasPlugin (clã), Vault e PixPlugin (coins e extrato) e DiscordPlugin (anúncio). Sem uma delas, só aquela parte fica de fora.

## Teste

`tools/bots/teste-tesouros.js` cobre:
- cavar fora do X e no X;
- o convidado puxado junto e o estranho barrado;
- o baú trancado, a fuga por teleporte barrada e as ondas;
- o baú só do dono, as coins e a saída;
- a morte que perde tudo;
- o spawner, a máquina e as células no baú;
- o mapa do boss e o mapa minerando.

## Como compilar

```bash
cd tesouros-plugin
mvn clean package
```

O `.jar` sai em `target/TesourosPlugin.jar`. Requisitos: Paper 26.2 (Java 25).
