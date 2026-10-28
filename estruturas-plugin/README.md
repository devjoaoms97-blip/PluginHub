# EstruturasPlugin

Constrói estruturas prontas (o spawn e as arenas) a partir de código, sem WorldEdit. Os blocos entram aos poucos (8000 por tick) pra não travar o servidor. Quando termina, a estrutura roda os comandos que a ligam aos outros plugins: a arena do boss marca o local do boss, e o spawn cadastra as barracas.

## Comandos

Todos exigem `estruturas.admin` (padrão: op).

| Comando | Ação |
|---|---|
| `/estrutura lista` | O que dá pra construir |
| `/estrutura construir <id>` | Constrói centrada em você, com o piso no nível dos seus pés |
| `/estrutura construir <id> <mundo> <x> <y> <z>` | O mesmo, com os pés nesse ponto (funciona pelo console) |
| `/estrutura desfazer` | Volta o terreno da última construção |
| `/estrutura status` | Se tem construção em andamento |

## Estruturas

| Id | Pra quê | O que é | Tamanho | Depois de construir |
|---|---|---|---|---|
| `spring` | **Spawn** | Spawn de primavera fechado: fonte com pavilhão de cerejeira no centro (onde o jogador nasce), avenidas, jardins e 32 barracas pequenas pro NPC do BarracasPlugin (28 normais e 4 especiais ★ perto do centro). Quatro saídas, uma no fim de cada avenida: o portão "MINAS" ao norte, o "BOSS" ao sul, o "LOJA" a leste e o "MOBS" a oeste, que acabam na face de fora (63 blocos do centro), onde encostam as vizinhas. Em volta, gramado até completar o quadrado | círculo de ~89, área de 115 x 127 | Cadastra as barracas que faltam (`b1`..`b28`, `e1`..`e4`): área, NPC e holograma |
| `spring-barracas` | **Spawn** | Só as 32 barracas do `spring`, pra refazer elas sem mexer no resto. Use a mesma origem do spawn | — | O mesmo cadastro (as que já existem ficam como estão, com aluguel e NPC) |
| `spring-oeste` | **Spawn** | Só o pedaço oeste do `spring` (o portão "MOBS", o túnel e o fim da avenida), pra abrir o portão num spawn que já existe sem mexer no resto. Use a mesma origem do spawn | x <= -35 e \|z\| <= 19 a partir do centro | Nada |
| `mobs` | **Warp mobs** | Salão enorme colado no portão "MOBS" do spawn, inspirado na /warp mobs da Craftlandia: fechado, teto de vidro com nervuras e nenhuma saída além do túnel. Onze estátuas gigantes de mobs direto na grama (galinha, porco, vaca, ovelha, zumbi, esqueleto, aranha, creeper, slime e blaze nos lados, virados pra avenida, e o enderman no fundo, de frente pra entrada), cada uma com um spawner no chão, na frente. Em volta de cada par, o cenário do tema: granja com galinheiro, lamaçal, pasto florido, cemitério com lápides e árvores secas, ossada gigante, floresta sombria cheia de teia, floresta com a cratera de uma explosão, pântano com laguinhos e mangues, Nether com poços de lava cercados, braseiros e portal em ruínas, e o Fim no fundo (pedra do Fim, pilares de obsidiana e coros). Um rio raso atravessa o salão de parede a parede, com cascatas e a ponte da avenida. Em cima da entrada, o painel do PvP em ciclos do AreasPlugin. Fica meio enterrado (piso em y 48, teto em y 84), então não aparece de dentro do spawn: do portão desce uma escadaria fechada, com o teto acompanhando os degraus. Logo depois da porta, o vestíbulo (onde a warp chega), separado do salão por uma linha vermelha no chão que os mobs não passam | escadaria em x -64..-82, salão em x -83..-209, 81 de largura, vidro em y 84 | Cadastra no SpawnersPlugin a área dos mobs e o spawner de cada estátua (`/spawners mobs`) |
| `minas` | **Minas** | Avenida das Minas: recinto retangular fechado em volta dos 12 poços, no estilo do spawn (muralha com arcos e torres, portão "MINAS" colado no caminho do spawn, praças, canteiros, calçadas e arcadas). Feita pro mapa do servidor: nunca escreve nos poços nem na borda deles, e deixa livres os pontos de teleporte | x -40..40, z -21..-185 (com a origem em 0, 65, -21) | Nada |
| `banco` | **Avenida das Minas** | O Banco do fim da avenida, entre o Compactador e o Cambista, no lugar da cerejeira grande da praça do fundo: plataforma e escadaria de quartzo com tapete vermelho, colunas com capitel de ouro, frontão com medalhão de esmeralda, balcão de pedra-negra e, no fundo, o cofre (porta de ferro e o bloco de cofre) com ouro e barris em volta. O fundo entra na muralha norte, pra a frente ficar na linha das barracas vizinhas e sobrar a passagem até o canteiro. Mesma origem da avenida (`0 65 -21`), depois dela | x -8..8, z -185..-179 (limpa x -9..9 até o z -172) | Cria o NPC Bancário (OficinasPlugin) |
| `spring-banco` | **Spawn** | Devolve o pedaço do spawn onde ficava a barraquinha antiga do Bancário (chão, poste e grama de antes). Mesma origem do spawn | x 26..34, z -18..-10 a partir do centro | Nada |
| `oficinas` | **Minas** | As barraquinhas de serviço da Avenida das Minas (OficinasPlugin): Ferreiro, Picareteiro, Bruxa e Sábio na praça de entrada, encostadas na muralha sul, e Cambista e Compactador no fundo, encostados na muralha norte. Embaixo da Bruxa, a câmara da mesa de encantamento. Construir depois da `minas`, com a mesma origem | x -29..29, z -24..-27 e z -182..-179 | Cria os 6 NPCs, marca a mesa da bruxa e põe os quadros |
| `axepvp` | **X1** | Pátio de areia rebaixado com paredes neoclássicas, no tamanho da AxePvP Arena da CurseForge. Teto de vidro na altura do topo das paredes, um nicho fechado em cada parede curta (onde cada lutador nasce) e, em cima, a galeria dos espectadores com parapeito, bancos e estandartes | piso de 43 x 27, paredes de 15 | Nada: registre com `/duelo arena criar` + `pos1`/`pos2` no fundo dos nichos, em (±23, 1, 0) a partir do centro |
| `coliseu` | **Gladiador** | Coliseu romano elíptico com piso de areia fechado e teto de vidro com nervuras. Por cima, o deck dos espectadores, 10 fileiras de arquibancada e uma galeria com toldos, todos cercados pela muralha. Ruínas como cobertura e o centro livre pra borda final | piso de 153 x 129, estrutura de 213 x 189 | Nada: veja abaixo |
| `toxica` | **Boss** | Arena sci-fi/apocalíptica enterrada: o piso fica 32 blocos abaixo do chão de fora (gramado), então a cúpula não aparece por cima do spawn. Muralha circular de deepslate com listras de perigo e cobre oxidado, cúpula de vidro lima com treliça, poças tóxicas, canos, torres, contêineres e tambores. Uma única entrada: a escadaria fechada ao norte, no mesmo estilo do túnel do spawn (só o túnel da muralha é escuro), que termina 88 blocos ao norte do centro, no nível do chão; na chegada à arena, um degrau de 4 blocos impede os mobs do boss de subir | piso de ~97, vidro a 42-52 de altura (a partir do piso) | `/boss setlocal` no centro |

As três arenas inspiradas na BuiltByBit (`spring`, `coliseu` e `toxica`) foram feitas pela descrição dos anúncios: o site não deixou baixar as imagens.

**No mapa do servidor** (`tools/bots/montar-mapa.js`, mundo `spawn`): a Avenida das Minas vai de z = -21 pro norte; o spawn tem o centro em (0, 65, 43), então o portão norte fica em z = -20, colado nela, e o sul em z = 106; a Loja Oficial encosta no portão leste (x = 64) e a warp mobs no oeste (x = -64), as duas com a mesma origem do spawn; a arena do boss tem o centro em (0, 33, 195), e a escadaria dela chega em z = 107, colada no portão sul. Cada estrutura só escreve na sua faixa, então dá pra refazer qualquer uma sem estragar as vizinhas. O X1 e o Gladiador ficam no mundo `arenas`, em (-600, 0) e (600, 0), longe um do outro.

**Gladiador no coliseu**, a partir do centro do piso: `pos1` em (-76, 1, -64), `pos2` em (76, 10, 64) e 16 pontos de entrada: (38, 0), (19, 28), (0, 32), (-27, 23), (-38, 0), (-27, -23), (0, -32), (27, -23), (46, 22), (18, 42), (-14, 43), (-46, 22), (-46, -22), (-27, -39), (14, -43) e (46, -22), todos em y = 1.

**Boss:** o BossPlugin impede o boss de quebrar blocos (o Wither e os crânios dele), então a cúpula e as paredes aguentam a luta.

## Cuidados

- A estrutura **apaga tudo** no volume dela (inclusive construções de jogadores) e fica alinhada aos eixos.
- O **desfazer** só vale pra última construção e some quando o servidor reinicia. Ele volta os blocos, mas o conteúdo de baús que estavam no caminho se perde, e os cadastros feitos depois (barracas, local do boss) continuam.
- Os comandos de integração de um plugin que não está instalado são pulados, com aviso.

## Nova estrutura

Crie uma classe em `com.example.estruturas.estrutura` implementando `Estrutura` (a `Planta` tem os atalhos de escada, laje, vidro conectado etc.) e registre no construtor do `Construtor`. Os blocos são colocados sem física: folhas precisam de `persistent`, vidros e cercas das conexões marcadas, e líquido solto escorre na primeira atualização.

## Requisitos

- Paper 26.2 (Java 25)
- BossPlugin e BarracasPlugin (opcionais, pros comandos de integração)

## Como compilar

```bash
cd estruturas-plugin
mvn clean package
```

O `.jar` sai em `target/EstruturasPlugin.jar`.
