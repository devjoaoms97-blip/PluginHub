# SpawnersPlugin

Spawners empilhados nos terrenos: outra fonte de renda além da mina.

## Como funciona

- **Compra:** no balcão **Spawners** da Loja Oficial (o vendedor "Domador" abre o `/spawners loja`). Cada tipo libera num rank e tem preço fixo; o dinheiro sai de circulação.
- **Onde:** só no mundo dos terrenos (`mundos` no config). A proteção dos terrenos cuida de quem pode colocar e quebrar; fora dele, o spawner não entra.
- **Empilhar:** com outro spawner igual na mão, clique direito no spawner (shift empilha a mão inteira), até `limite-por-bloco` (64). Só o dono empilha.
- **A pilha de mobs:** a cada `ciclo-segundos` (20), cada spawner com algum jogador a até `raio-ativacao` (16) blocos soma tantos mobs quanto spawners empilhados, numa entidade só ("x37 Zumbi"), até `limite-pilha` (300). Ela nasce num lugar livre em cima ou em volta do spawner (nunca dentro de bloco) e é um mob normal: anda, a água arrasta, ataca, queima no sol e toma dano de tudo, então dá pra montar farm. Só não some de longe, não pega item, não deixa cair o equipamento e o creeper não explode (levaria a pilha toda sem drop).
- **Abate:** a morte, por jogador ou não, vale pela pilha inteira: derruba os drops (com Pilhagem) e o XP de cada mob da pilha de uma vez, e o spawner começa uma pilha nova no ciclo seguinte. Os drops vendem no **Caçador**, e o preço dinâmico regula: o que muita gente farma vale menos.
- **Pilha que volta com o chunk:** se o spawner já começou outra enquanto ela estava descarregada, as duas viram uma só.
- **Quebrar:** devolve todos os spawners do bloco (em itens de até 64), sem precisar de Toque Suave.
- **Explosão** não quebra spawner.

O bloco mostra o mob girando, mas o spawn do vanilla fica desligado: quem gera é o ciclo do plugin.

## Integrações

- **Missões:** o mob da pilha nasce com o motivo "spawner", e as missões de matar não contam mob de spawner.
- **Skills:** bater em mob de pilha dá `xp.multiplicador-spawner` (25%) do XP de PvE, e só pelo dano do golpe: matar a pilha toda não dá o XP de cada mob dela. Senão a pilha virava fábrica de level up (cada nível paga dinheiro). A marca é a chave `spawnersplugin:pilha` na entidade.
- **Rankup:** o rank que libera cada tipo (por reflexão).
- **Pix:** a compra aparece no `/pix historico`.

## Tipos e preços

Com 1 spawner são 180 mobs por hora (se o jogador matar todos); o preço se paga em umas 20 h no preço de âncora dos drops. Com muitos spawners a pilha enche, e o que limita a renda passa a ser o quanto o jogador consegue matar.

| Tipo | Rank | Preço |
|---|---|---|
| Galinha, Porco | Camponês | R$ 5.000, R$ 8.000 |
| Ovelha, Vaca | Fazendeiro | R$ 10.000, R$ 14.000 |
| Zumbi, Esqueleto | Lenhador | R$ 3.000, R$ 5.000 |
| Aranha, Creeper | Ferreiro | R$ 5.000, R$ 7.000 |
| Slime | Mercador | R$ 10.000 |
| Enderman | Escudeiro | R$ 15.000 |
| Blaze | Clérigo | R$ 18.000 |
| Golem de Ferro | Barão | R$ 120.000 |

## Comandos

| Comando | Ação |
|---|---|
| `/spawners loja` | A loja de spawners (o Domador abre esse comando) |
| `/spawners dar <jogador> <tipo> [qtd]` | (staff) Dá spawners |
| `/spawners lista` | (staff) Os spawners colocados, com tipo, quantidade e dono |
| `/spawners reload` | (staff) Recarrega o config |

Permissão da staff: `spawners.admin`.

## Dados

`spawners.yml`: cada spawner colocado (mundo, posição, tipo, quantidade, dono, a pilha e o holograma).

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/SpawnersPlugin.jar`. Requisitos: Paper 26.2 e Vault.
