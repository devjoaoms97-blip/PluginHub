# OficinasPlugin

NPCs de serviço nas barraquinhas da mina. Cada um fica atrás do balcão de uma barraca temática na Avenida das Minas (estrutura `oficinas` do EstruturasPlugin): quatro na praça de entrada e duas no fundo, com o Banco no meio do fundo. Clique direito no NPC:

| NPC | Barraca | O que faz |
|---|---|---|
| ⚒ **Ferreiro** | Deepslate, bigorna, alto-forno, lava e chaminé fumegando (leste, x 10..18) | Menu com os itens gastos do seu inventário e o preço do reparo de cada um. Clique pra reparar um, ou "Reparar tudo" |
| ⛏ **Picareteiro** | Pinho, mostruário de minérios, barris e picaretas nos quadros (leste, x 21..29) | Vende picaretas e machados (pra mina do bosque) da lista do config, encantados ou não |
| ✦ **Bruxa** | Carvalho escuro, estantes, vitral roxo, caldeirão, velas e ametista (oeste, x -18..-10) | Abre uma mesa de encantamento com o poder máximo (nível 30). O lápis é por conta dela: o espaço vem cheio e travado, e o jogador só gasta o XP |
| 📖 **Sábio** | Estantes, quartzo e lápis (oeste, x -29..-21) | Vende níveis de XP |
| ✦ **Cambista** | Purpur, ametista e vitral magenta, no **fundo** da avenida (leste, x 10..18) | Troca **Fragmentos Arcanos** e coins por encantamentos acima do máximo no item da mão (veja abaixo) |
| ▣ **Compactador** | Tufo, cobre e blocos de minério, no **fundo** da avenida (oeste, x -18..-10) | Transforma minérios e lingotes em blocos (9 → 1) e desfaz os blocos, de graça. Só mexe em item comum (sem nome, encantamento ou marca) |
| **Bancário** | O **Banco** no fim da avenida, entre o Compactador e o Cambista (estrutura `banco`: quartzo, ouro, colunas, frontão e o cofre) | Vende a folha de cheque em branco, deposita os cheques do inventário, dá o extrato e vende a Mine-sena (o balcão é do ChequePlugin) |
| **Guia de mina** | Fora do mapa desde 2026-10-27 (o `/mina` e o holograma de cada mina já mostram tudo). O tipo continua no plugin: `/oficina criar <id> mina:<id da mina>` | Mostra o rank exigido, os blocos com a %, o tempo até o reset, quanto já foi minerado e a chance de fragmento, e o botão de entrar pra quem tem o rank |
| ⚔ **Armeiro** | Balcão PvP da **Loja Oficial** (não fica na mina) | Vende todas as armaduras (couro, cota de malha, ouro, ferro, diamante e netherita) e armas (espadas e machados de madeira a netherita, arco, besta e tridente), **sem encantamento** (encanta-se na Bruxa), mais escudo, flechas e poções (`armeiro.itens`, com `pocao:` pro tipo da poção). Só compra: nada disso volta pra loja |

## Preços

- **Reparo**: `preco-cheio` do material (a primeira chave que aparece no nome do item, ex: `DIAMOND`) × fração gasta × (1 + 12% por nível de encantamento). Uma picareta de diamante Eficiência V, Inquebrável III e Fortuna III pela metade custa 250 × 0,5 × 2,32 = R$ 290. Item inquebrável não aparece.
- **XP**: o custo em pontos de XP do vanilla vezes `sabio.preco-por-ponto` (do 0 ao 30 são 1.395 pontos). Ninguém compra além de `sabio.nivel-maximo`.
- **Picareteiro** e **Armeiro**: preço fixo por item em `picareteiro.itens` e `armeiro.itens` (material, nome, quantidade, encantamentos e, nas poções, `pocao` com o tipo do Minecraft, como `strong_healing`). Com `preco: 0` o item é de graça e não aparece no extrato: a picareta e o machado de pedra do Picareteiro são assim, pra quem está começando.
- **Bruxa**: `bruxa.taxa` por abrir (0 = de graça).

Todos têm o desconto de VIP (`vip-descontos`, as mesmas permissões da loja) e aparecem no extrato do Bancário.

## Cambista

O Cambista encanta o item da **mão** com níveis acima do máximo do jogo, pagos com **Fragmentos Arcanos** e coins. Os fragmentos caem raramente ao minerar nas minas (RankupPlugin, cerca de 1 por hora). Os encantamentos, os níveis e os custos ficam em `cambista.encantamentos`:

| Encantamento | Em quê | Níveis | Fragmentos | Coins |
|---|---|---|---|---|
| Eficiência | Picareta, machado, pá e enxada | VI, VII, VIII | 15, 30, 60 | R$ 250 mil, 750 mil, 2 mi |
| Fortuna | Picareta, pá e enxada | VI, VII, VIII | 25, 50, 100 | R$ 500 mil, 1,5 mi, 4 mi |
| Afiação | Espada e machado | VI, VII, VIII | 20, 40, 80 | R$ 300 mil, 900 mil, 2,5 mi |
| Proteção | Capacete, peitoral, calça e bota | VI, VII, VIII | 12, 25, 50 | R$ 200 mil, 600 mil, 1,6 mi |
| Poder | Arco | VI, VII, VIII | 15, 30, 60 | R$ 250 mil, 750 mil, 2 mi |
| Saque | Espada | IV, V, VI | 20, 40, 80 | R$ 300 mil, 900 mil, 2,5 mi |
| Inquebrável | Ferramentas, armas e armaduras | IV, V, VI | 8, 16, 32 | R$ 100 mil, 300 mil, 800 mil |

- Sobe **um nível por vez**: do que o item tem pro primeiro nível da lista (uma picareta com Fortuna III ou sem Fortuna vai pra VI) e depois um a um.
- Cada encantamento só vai nos itens da lista `itens` dele no config (Afiação nunca em picareta ou pá). `_PICKAXE` vale pra todo material que termina assim; `BOW`, só pro arco. Sem a lista, vale o que o jogo aceita. Também não aparece o que conflita com um encantamento do item (Fortuna e Toque Suave, as proteções entre si).
- O desconto de VIP vale **só nas coins**. A compra aparece no extrato do Bancário, conta pra missão de encantar e, no último nível, é anunciada no chat.
- **É caro de propósito.** Pela simulação do rankup, só Eficiência VIII leva a meta de 150 h até o Rei pra ~90 h, e Eficiência VIII com Fortuna VIII pra ~75 h. Os custos somam ~105 h de mina em fragmentos pra Eficiência VIII e ~175 h pra Fortuna VIII. O Saque vai só até VI, um nível acima do que dá pra ter no jogo, porque mexe nos drops dos mobs (inclusive dos spawners).
- Sem item na mão, o menu mostra a vitrine com todos os custos.

## Missões

O Ferreiro e a Bruxa contam pras missões do MissoesPlugin: `reparar` (cada item reparado) e `encantar` (cada item encantado na mesa da Bruxa ou no Cambista), com o material do item como alvo.

## A mesa da bruxa

A mesa fica numa câmara fechada embaixo da barraca da bruxa (centro em -14, 61, -25), com o ar em volta e 32 estantes a 2 blocos (o jogo conta 15: poder 30). O NPC abre essa mesa de longe. Por isso ninguém usa a mesa direto, e a barraca não precisa ter as estantes do jeito do vanilla. A estrutura monta a câmara e marca o lugar no config (`bruxa.mesa`); a staff muda com `/oficina mesa`.

## Comandos (staff, `oficinas.admin`)

| Comando | Ação |
|---|---|
| `/oficina criar <id> <tipo> [mundo x y z yaw]` | Põe o NPC (onde você está, ou nas coordenadas, pelo console). Tipos: `ferreiro`, `bruxa`, `picareteiro`, `sabio`, `armeiro`, `compactador`, `cambista`, `bancario` e `mina:<id da mina>` (o guia). Com um id que já existe, move |
| `/oficina remover <id>` | Tira o NPC |
| `/oficina lista` | Lista as oficinas |
| `/oficina mesa [mundo x y z]` | Onde fica a mesa da bruxa |
| `/oficina abrir <tipo>` | Abre o serviço sem o NPC (pra testar) |
| `/oficina reload` | Recarrega o config (nomes e skins) |

## NPCs

Mesmo esquema dos de dicas e das barracas: Mannequin que não fica salvo no mundo (nasce quando o chunk carrega), não toma dano e volta sozinho se for removido. Ficam atrás do balcão, olhando pro passeio. Nome, descrição e skin de cada tipo ficam em `tipos.<tipo>` no config. A skin aceita `textura:<hash>` (a textura direto do textures.minecraft.net, por exemplo de uma skin do mineskin.org) ou o nick de um jogador. As temáticas que vêm no config: ferreiro de avental, minerador de capacete, bruxa de cabelo roxo, sábio de barba branca, cavaleiro (armeiro), operário de capacete de obra (compactador), mercador de casaco azul (cambista) e banqueiro de cartola (bancário). O perfil da textura vai com UUID e nome fixos, tirados do hash: sem eles o cliente pode tratar como perfil incompleto e mostrar a skin padrão. Nome, descrição ou skin que faltar no config do servidor (um config antigo não tem os tipos novos) cai no do jar. Não use emoji fora do Unicode básico no nome (🏦, 📖...): o Minecraft não desenha. Os lugares ficam em `oficinas.yml`.

## Montagem

`node tools/bots/montar-mapa.js construir oficinas` (ou junto com `minas`) constrói as 6 barracas, cria os NPCs delas e os 12 guias de mina (`mina-<id>`), marca a mesa e põe os quadros com o que cada um vende. Teste: `node tools/bots/teste-oficinas.js`.

## Como compilar

```bash
mvn package
```

O `.jar` sai em `target/OficinasPlugin.jar`. Requisitos: Paper 26.2 e Vault (PixPlugin, RankupPlugin e MissoesPlugin são opcionais: o histórico, os guias de mina e o Cambista, e as missões).
