# Roadmap do PluginHub

Tudo o que está planejado para os plugins, num só lugar. Os READMEs de cada plugin continuam descrevendo **o que já existe**; este arquivo descreve **o que vem**.

**Esforço:** 🟢 baixo · 🟡 médio · 🔴 alto

**Direção (2026-10-22): o servidor vai ser rankup.** O jogador minera nas minas, vende na loja e paga pra subir de rank, com ranks medievais (Camponês → Rei). Os itens abaixo são priorizados por quanto ajudam esse loop.

## Diagnóstico (por que essas features)

1. **A economia tem mais saída que entrada de dinheiro.** O dinheiro sai pela taxa do Pix, pelo imposto da loja e pela taxa da revenda de VIP. Entra só pela venda de itens pra loja e pelo level up de skill. Faltam fontes de renda regulares que não dependam de grind.
2. **O PvP não tem objetivo.** As skills deixam o jogador mais forte, mas não há aposta, recompensa nem ranking.
3. **Falta conteúdo social.** Não existe clã, comércio entre jogadores nem terreno.

## Ordem sugerida

| # | Item | Esforço | Motivo |
|---|---|---|---|
| 1 | ~~Painel da economia~~ ✅ v1 | 🟢 | Dá os números pra calibrar taxas, limites e recompensas de tudo o que vem depois |
| 2 | ~~Missões diárias~~ ✅ v1 | 🟡 | Coloca dinheiro novo na economia e dá algo pra fazer todo dia |
| 3 | ~~Recompensa por cabeça (Bounty)~~ ✅ v1 | 🟢 | Dá objetivo pro PvP usando o que já existe |
| 4 | ~~Mercado de Barracas~~ ✅ v1 | 🟡/🔴 | Comércio entre jogadores com aluguel, que também tira dinheiro de circulação |
| 5 | ~~Clãs~~ ✅ v1 · ~~Terrenos~~ ✅ v1 | 🔴 | Clãs veio primeiro, depois os Terrenos |

O resto não tem ordem definida e pode ser puxado quando fizer sentido.

## Mapa do rankup (decisões de 2026-10-23)

O servidor ainda usa o mundo padrão do Minecraft. O mapa do rankup fica dividido em mundos, cada um com as próprias regras (PvP, proteção, mobs). Para isso, entram o Multiverse-Core (vários mundos) e o WorldEdit ou o FAWE (colar schematics).

| Mundo | O que tem | PvP |
|---|---|---|
| `spawn` (vazio) | Spawn fechado com as barracas e os NPCs de dicas, a Avenida das 12 minas (colada no portão norte) e a arena do boss, enterrada (colada no portão sul) | Não |
| `terrenos` (grade) | Terrenos comprados com coins, com imposto semanal. Servem também para fazendas | Não |
| `recursos` (normal) | Mobs naturais, pesca, árvores e PvP do Bounty e dos clãs. Resetado a cada 30 dias pelo `recursos-plugin`, com borda de 6.000 blocos e `/recursos` para um ponto aleatório seguro | Sim |
| `arenas` (vazio) | Arena de X1 e arena do Gladiador, a 1.200 blocos uma da outra. Só se entra por comando ou warp (a plateia por `/warp plateia-x1` e `/warp plateia-gladiador`), com volta ao spawn no fim | Sim |

- **Visual do spawn** ✅ (2026-10-24): em vez de um mapa pronto, as construções saem de código, pelo EstruturasPlugin (`/estrutura construir <id>`), inspiradas em mapas da CurseForge e da BuiltByBit e ajustadas jogando.
- **Mapa funcional** ✅: `tools/bots/montar-mapa.js` monta tudo por partes (`minas`, `spawn`, `barracas`, `boss`, `dicas`, `arenas`, `marcar`), pra dar pra refazer uma parte sem mexer no resto, e cadastra tudo nos plugins:
  - **Spawn** (`spring`): fechado, com fonte, jardins, 32 barracas com NPC e só dois portões. O norte encosta na **Avenida das Minas** (`minas`), com os 12 poços; o sul desce por uma escadaria fechada até a **arena do boss** (`toxica`), enterrada 32 blocos pra não aparecer por cima do spawn, com um degrau de 4 blocos na chegada pros mobs do boss não subirem.
  - **Arenas**: X1 `axepvp` (nichos fechados pros lutadores, teto de vidro e galeria da plateia) e Gladiador `coliseu` (3x maior, teto de vidro e arquibancada fechada), longe uma da outra.
  - As 12 minas entram prontas com `cadastrar --minas`. Navegação a pé, mais `/spawn` e `/warp` (o `/spawn` vem do EssentialsXSpawn).
- **Terrenos:** plugin nosso, e não o PlotSquared, para integrar com a economia (imposto, Pix e painel).
- **Servidor de teste:** o `montar.bat` baixa o Multiverse e o WorldEdit, e o `configurar-mundos.bat` cria os 4 mundos.
- **Mundo de recursos:** as missões de matar mobs, pescar, colher e cortar árvores precisam dele, porque mob de spawner não conta e o rankup protege o mundo fora das minas.
- **NPCs**: plugin nosso, com o Mannequin do 26.2 (sem Citizens). Já existem os vendedores das barracas (skin do dono) e 12 NPCs de dicas pelo spawn (DicasPlugin), com balão de fala animado. Ainda faltam: NPC da loja (abre o `/sell` e mostra preços) e um na entrada de cada mina. 🟡
- **Arenas:** o X1 é o Duelo com aposta (`duelo-plugin`), e o Gladiador, o `gladiador-plugin`. A arena do boss marca o local dele sozinha ao ser construída. Hoje há uma arena de X1 só, então um duelo por vez; dá pra construir cópias da `axepvp` em outros lugares do mundo `arenas` quando precisar de mais.
- **Itens nas arenas de PvP (X1 e Gladiador):** cada um luta com o próprio equipamento, e **quem morre perde os itens**.

---

## 🆕 Plugins novos

### Painel da economia ✅ v1 (`economy/painel-economia-plugin`)
`/economia` (staff) mostra circulação, entrada/saída por dia separada por plugin e maiores fortunas. O painel se coloca na frente da economia do Vault e descobre o plugin de origem pelo stack de execução, então nenhum plugin precisou ser alterado. O comando é `/economia` porque `/eco` já é do EssentialsX.

**Próximos passos**
- [x] Separar o fluxo por "tipo" dentro de cada plugin (`/economia tipos <plugin>`). O tipo vem da classe e do método que mexeu no dinheiro, com nomes no config.
- [x] Alerta pra staff quando a circulação crescer mais que X% num dia (10% e R$ 50.000 por padrão).
- [x] Medir a economia ao longo do tempo: mediana dos saldos, concentração (10 maiores e Gini), jogadores e horas jogadas por dia e líquido por hora jogada. A loja guarda as vendas por item e por dia, e o Rankup guarda as horas de jogo de cada rankup.
- [ ] Relatório semanal: o Claude roda `tools/economia/analisar.py` com os dados do servidor, escreve o que está fora da meta e sugere mudanças de config com o diff pronto. Nada é aplicado sem aprovação. Só vale a pena com jogadores de verdade. 🟢
- [ ] "Banco Central": uma cotação diária do minério (0,8× a 1,2×) ajustada pela inflação dos últimos 7 dias, com teto por dia e trava da staff. **Decisão (2026-10-22): nada automático no começo.** Reavaliar depois de algumas semanas de relatório. 🟡

### Missões diárias ✅ v1 (`missoes-plugin`)
3 missões sorteadas por dia e 2 por semana (minerar, colher, matar, pescar, vender na loja, dano no boss), com recompensa em coins e XP de skill e bônus por completar todas. Menu no `/missoes`. Contra farm: bloco colocado pelo jogador, mob de spawner e revenda do que foi comprado na loja no mesmo dia não contam.

**Próximos passos**
- [x] Missão de vender na loja e de causar dano no boss.
- [x] Menu visual (GUI) no `/missoes`.
- [x] Missões semanais, maiores e com recompensa melhor.
- [x] Missão de vender nas barracas (entre jogadores).

### Recompensa por cabeça (Bounty) ✅ v1 (`bounty-plugin`)
`/bounty colocar <jogador> <valor>`: o valor fica retido (com taxa de 10%), recompensas se somam e quem matar leva. Expira em 7 dias, devolvendo aos contribuintes. Contra farm: mesmo IP, mesmo clã e cooldown por matador. `/bounty cla <tag> <valor por abate> <abates>` caça um clã inteiro. Anúncios grandes vão pro Discord por webhook.

**Próximos passos**
- [x] Recompensa contra um clã inteiro (contrato por abate).
- [x] Aparecer no Discord quando uma recompensa grande for colocada ou cobrada (webhook no próprio plugin; migrar pra Ponte com o Discord quando ela existir).

### Duelo com aposta ✅ v1 (`duelo-plugin`)
`/duelo <jogador> [valor]`: os dois apostam, o valor fica retido e a luta é numa arena 1v1 do mundo `arenas`. O vencedor leva o pote menos 5%. Sem valor, é amistoso.
- Várias arenas (`/duelo arena criar|pos1|pos2`), um duelo por arena.
- Luta com o equipamento do próprio jogador, e quem morre perde os itens (decisão de 2026-10-23). O vencedor tem 20 s para pegar os itens.
- Desconectar conta como derrota. Depois de 5 minutos de luta, empate com as apostas devolvidas.
- Ranking de vitórias e derrotas (`/duelo top`).

**Próximos passos**
- [x] Espectadores (`/duelo assistir`).
- [x] Os abates no mundo das arenas (duelo e Gladiador) não contam para o placar de abates do clã nem para o Bounty, para ninguém combinar luta com um amigo para farmar.

### Mercado de Barracas ✅ v1 (`economy/barracas-plugin`)
Comércio entre jogadores em barracas alugadas no spawn. A loja é um NPC com a skin do dono e estoque virtual (antes era por placa em baús). A v1 está implementada (veja o README do plugin), com estas decisões:
- A área é definida por **dois pontos** (`/barraca pos1`, `/barraca pos2`), ou cadastrada pelo console (o spawn do EstruturasPlugin cadastra as 32 sozinho).
- O dono **não constrói nada**: anuncia com `/barraca vender` e `/barraca comprar` e abastece pelo NPC. A construção da staff fica intacta.
- O aluguel dura **6h**.
- **Barracas normais**: por ordem de chegada, com renovação.
- **Barracas especiais** (maiores, perto do spawn): vão a **leilão** quando ficam livres, com lance mínimo igual ao preço das normais. Não renovam.
- A caixa de retirada guarda os itens por **7 dias**.

**Próximos passos**
- [x] Placa `[Comprar]`: o dono paga quem traz o item.
- [x] Holograma na entrada, com o nome do dono ou "Aluga-se" quando estiver livre (`/barraca holograma <id>`). Antes era uma placa.
- [x] Preço de aluguel por barraca (`/barraca preco <id> <valor>`).
- [x] Aluguel pago automaticamente: `/barraca autorenovar`, do próprio plugin. Migrar pro `/pix agendar` quando ele existir. 🟢
- [x] Loja por NPC com estoque virtual, no lugar dos baús e placas (2026-10-24).

### Clãs ✅ v1 (`clas-plugin`)
Tag no chat e na tab (convive com a do VIP), cargos (líder, oficial, membro), convites com botão, banco compartilhado com extrato (também no `/pix historico`), fogo amigo desligado (inclusive poção e explosão), chat do clã (`/cc`) e ranking por pontos (abates + boss).

**Próximos passos**
- [x] Tag na tab list, junto com a do VIP.
- [x] Integração com o Boss: clã do campeão e pontos no ranking.
- [x] Fogo amigo também pra poção arremessada e explosão (TNT e cristal do End).
- [x] Banco do clã no `/pix historico` (o Pix ganhou transação com "entidade").
- [ ] Guerra de clãs: declarar guerra, placar de abates entre os dois e prêmio pro vencedor. 🟡

### Rankup ✅ v1 (`rankup-plugin`)
12 ranks medievais pagos com coins (`/rankup`, `/rankup max`), com bônus de venda no `/sell` por rank. Minas próprias (`/mina`), com composição por peso, reset por tempo e por % minerada e acesso pelo rank. Tag do rank no chat e proteção opcional do mundo fora das minas.

**Próximos passos**
- [x] Prestígio: no Rei, `/prestigio` volta pro Camponês com +10% de venda por nível (até 10) e a marca ✦ no chat e na tab.
- [ ] Mina exclusiva de prestígio. 🟡
- [x] Rank na tab, junto com o clã e o VIP.
- [ ] Encantamentos de picareta comprados com coins ou com uma moeda própria (explosão, mais fortuna, venda automática). 🔴
- [x] Coleta direta nas minas pra todos: drops e XP vão pro jogador, sem itens no chão.
- [x] Venda automática ao minerar, pra todos (2026-10-24; antes era benefício de VIP com `/autovenda`).
- [x] Menu (GUI) no `/ranks` e no `/mina`.
- [x] Missão de subir de rank e missões por mina.
- [ ] Mina privada do clã ou do VIP. 🟡
- [x] Holograma com o tempo até o próximo reset da mina.
- [x] Fortuna nas minas: **vale** (decisão de 2026-10-22). A meta de 150 h é pra quem não é VIP e tem Fortuna III, da mesa de encantamento. Os VIPs Ouro e Diamante ganham Fortuna IV e V no kit mensal (`/vip kitmensal`) e chegam em cerca de 112 h e 107 h.

**Balanceamento (2026-10-22).** Meta: um jogador ativo chega ao Rei em **150 h**. A simulação (`tools/economia/simular.py`) mostrou que a sugestão inicial de minas não progredia: a de ferro rendia menos que a de cobre, a de redstone menos que a de ouro, e as de ametista e do Nether davam itens que a loja não comprava. As minas foram recalibradas pra cada uma render cerca de 1,6× a anterior, a loja do servidor de teste ganhou `NETHERRACK`, `GOLD_NUGGET` e `AMETHYST_BLOCK`, e os preços dos ranks foram recalculados (Fazendeiro: de R$ 5.000 pra R$ 3.400; Rei: de R$ 25 mi pra R$ 32 mi), com cada rank levando cerca de 1,5× o tempo do anterior. Tudo isso com Fortuna III como referência.

### Terrenos ✅ v1 (`terrenos-plugin`)
Terrenos de 32x32 num mundo em grade gerado pelo plugin, comprados por R$ 50.000. Decisões de 2026-10-23:
- 1 terreno por jogador; VIP Prata e Ouro, 2; Diamante, 3.
- Compra com preço fixo e **imposto semanal**, em vez de aluguel: 3% do preço do rank atual do dono, no mínimo R$ 1.000, com pagamento automático.
- Com 7 dias de atraso, o terreno passa para o servidor e vai a **leilão de imóvel** com tudo o que tem dentro. O lance vencedor some da economia. O dono antigo só dá lance depois de pagar a dívida.

**Próximos passos**
- [x] Holograma na entrada do terreno, com o dono, "À venda" ou o leilão. Antes era uma placa.
- [ ] Juntar terrenos vizinhos do mesmo dono. 🟡
- [x] Permissões separadas para os amigos (construir, baús, usar).
- [x] Missão de pagar o imposto ou de construir no terreno.

### Gladiador ✅ v1 (`gladiador-plugin`)
Evento de PvP na arena do mundo `arenas`, aberto pela staff ou pela agenda: inscrição paga, contagem regressiva, todos entram na arena e vence o último de pé.
- Dois modos, escolhidos ao abrir o evento: **individual** (todos contra todos) e **por clã** (vence o último clã com alguém vivo; usa o ClasPlugin, sem dano entre membros do mesmo clã).
- Cada um luta com o próprio equipamento, e quem morre perde os itens (decisão de 2026-10-23).
- Pote: inscrições (R$ 2.000 cada) mais R$ 10.000 do servidor. No modo por clã, é dividido entre quem lutou pelo clã vencedor.
- Quem desconecta ou sai da arena é eliminado.
- Ranking de vitórias de jogadores e de clãs.

**Próximos passos**
- [x] Anúncio no Discord (abertura e resultado).
- [x] Borda que fecha com o tempo, para a luta não se arrastar. O dano fora dela cresce a cada segundo (2, 3, 4... até 20) e ignora a armadura.
- [x] Destaques com tag no chat até o próximo Gladiador: [Ceifador] (mais abates), [Fujão] (mais blocos andados) e [Top Damage] (mais dano).
- [x] Prêmio do modo por clã direto no banco do clã, como opção.

### Combate 1.8 ✅ v1 (`combate-plugin`)
Decisões de 2026-10-23, no servidor todo: **sem recarga de ataque**, **tempo entre golpes configurável e separado por mão** (`/combate intervalo`, de 0 ao padrão de 0,5 s ou mais; alternar as duas mãos bate duas vezes mais), **bloqueio com a espada** (só sem escudo na segunda mão) e **duas mãos** (o botão direito bate com a espada, o machado ou o soco da segunda mão, cada um com a sua skill). Ataque em área, repulsão, regeneração, maçãs, ender pearl, escudo e machado continuam como no jogo atual.

**Próximos passos**
- [ ] Testar jogando e decidir o que fazer com a maça, a lança e o machado do kit mensal do VIP, que ficam fortes sem recarga. 🟢
- [ ] Conferir no jogo a animação da espada bloqueando. 🟢
- [x] **Corrigido (2026-10-24):** com espada na principal, o botão direito num inimigo bloqueava em vez de bater com a segunda mão (o cliente nunca manda o clique da segunda mão quando a principal "faz algo").
- [ ] Calibrar o tempo entre golpes jogando (o dano por segundo muda com ele). 🟢
- [x] **Besta numa mão e espada na outra** (2026-10-24): carregar trava a espada (besta e arco). Enquanto a besta carrega (ou o arco é puxado), e por 0,5 s depois de disparar, nenhum golpe corpo a corpo conta, de qualquer mão. Bater e depois carregar continua livre.

### Ponte com o Discord ✅ v1 (`discord-plugin`)
Os outros plugins mandam os anúncios para o DiscordPlugin, e cada categoria vai para o seu webhook ou para o padrão. Já ligados: Boss (aparecimento e morte com o ranking), Gladiador (abertura e resultado), leilões (terrenos e barracas especiais), VIP ativado (categoria `vip`), Pix alto (categoria `pix`, a partir de `discord.pix-suspeito-valor` no PixPlugin; de preferência num canal da staff) e barraca alugada ou liberada (categoria `barracas`). O Bounty continua com o webhook próprio.

### Recompensa diária de login ✅ v1 (`diaria-plugin`)
`/diaria` numa sequência de 7 dias, com uma % do preço do próximo rank (0,5% a 2,5%, mínimo de R$ 200): a semana toda dá 7% de um rank. Perder um dia volta pro 1.

- [x] Bônus de VIP na recompensa diária.

### Estruturas ✅ v1 (`estruturas-plugin`)
Constrói estruturas de código, sem WorldEdit (`/estrutura construir <id> [mundo x y z]`, também pelo console), aos poucos pra não travar e com `/estrutura desfazer`. No fim, roda comandos de integração (cadastra as barracas, marca o local do boss). Estruturas: `spring` (spawn), `spring-barracas`, `minas`, `axepvp` (X1), `coliseu` (Gladiador) e `toxica` (boss).

**Próximos passos**
- [ ] NPC da loja e um em cada mina (ver "NPCs", no mapa). 🟡
- [ ] Cópias da arena de X1, pra mais de um duelo ao mesmo tempo. 🟢

### NPCs de dicas ✅ v1 (`dicas-plugin`)
NPCs pelo spawn que falam ao receber um clique direito: o balão cresce, escreve letra por letra e some. Cada clique mostra a próxima fala. 12 NPCs com 4 falas cada (`/dica` pra criar e editar).

### Oficinas da mina ✅ v1 (`oficinas-plugin`)
Quatro barraquinhas temáticas na praça de entrada da Avenida das Minas (estrutura `oficinas`), cada uma com um NPC de serviço: **Ferreiro** (repara os itens por uma taxa pelo desgaste, material e encantamentos), **Bruxa** (mesa de encantamento sempre no nível 30, com a câmara de estantes escondida embaixo da barraca), **Picareteiro** (picaretas prontas, encantadas ou não) e **Sábio** (níveis de XP). A bruxa põe o lápis; cada NPC tem skin temática. Desconto de VIP e registro no `/pix historico`.
- [ ] Mais oficinas no fundo da avenida (ex: vendedor de comida, troca de minério por fragmento). 🟡
- [ ] Missões nas oficinas (reparar, encantar). 🟡

### Placar lateral ✅ v1 (`placar-plugin`)
Scoreboard no canto da tela com saldo, rank, barra até o próximo rank, clã, missões do dia, próximo boss e jogadores online. Linhas no config, com placeholders; `/placar` liga e desliga.
- [ ] Linha com a skill em uso e o XP dela (ver SkillsPlugin). 🟡

### Loja Oficial ✅ v1 (`economy-shop-plugin` + estrutura `loja`)
Prédio fechado colado no portão LESTE do spawn (`/warp loja`), com dez balcões, cinco de cada lado: sete vendedores temáticos, um por categoria (minerador, construtor, lenhador, fazendeiro, caçador, jardineiro, alquimista), o Domador (spawners), o Maquinista (máquinas) e o Armeiro (PvP). Comprar e vender é só com eles; `/shop`, `/buy` e `/sell` ficaram pra staff. Nas minas, tudo o que se quebra vira dinheiro na hora, pra todos. VIP compra com 3/6/9/12% de desconto e vende com 3/6/9/12% a mais. Sem limite de quantidade: o preço dinâmico regula, e farm AFK é bem-vinda. Uma trava (90% do menor preço de compra) impede comprar e revender com lucro. Catálogo de 356 itens gerado e conferido pelo `tools/economia/loja.py` (nenhuma brecha com craft, fundição ou quebra de bloco).
- [ ] Ajustar preços com o `analisar.py` depois de alguns dias com jogadores. 🟡

### Spawners ✅ v1 (`spawners-plugin`)
Spawners empilhados nos terrenos, comprados no balcão Spawners da Loja Oficial (12 tipos, cada um liberado num rank). Iguais no mesmo bloco se somam, e os mobs se juntam numa pilha só em cima do spawner, que o jogador mata na espada. Os drops vendem no Caçador. As skills dão 25% do XP em mob de pilha, e as missões não contam.
- [ ] Ver no `analisar.py` quanto os spawners rendem de verdade e calibrar os preços. 🟡

### Máquinas e produtos ✅ v1 (`maquinas-plugin` + `pacote-plugin`)
Cinco máquinas nos terrenos (Tear, Forja, Destilaria, Lapidadora e Reator Arcano), cada uma liberada num rank, que com **Células de Combustível** compradas na loja fabricam **produtos** (Tecido Fino, Barra de Aço, Essência Destilada, Gema Lapidada e Cristal de Energia). Produzem pelo relógio, mesmo offline, até encher o estoque (uma a duas horas). Melhorias de velocidade (5% por nível) e estoque, e até 4 máquinas por jogador (VIP até 7). O Maquinista (balcão Máquinas) vende as máquinas e o combustível e compra os produtos por preço dinâmico, com o bônus do rank e do VIP. Cada máquina rende ~10% da mina do rank dela (`tools/economia/maquinas.py`).
- [x] **Textura própria do servidor**: resource pack servido pelo PacotePlugin (HTTP embutido, mandado ao entrar), com as texturas geradas pelo `tools/pacote/gerar.py` no estilo do spawn.
- [x] Produtos com ID próprio marcado no item (não dá pra falsificar, não entram em receita).
- [ ] Cadeias de produção (a saída de uma máquina é entrada de outra). 🟡
- [ ] `url-publica` do pacote no servidor de verdade (endereço e porta liberada). 🟢
- [x] Balcão **PvP** da loja: o Armeiro (OficinasPlugin) vende todas as armaduras e armas sem encantamento (couro a netherita), escudo, flechas e poções. Só compra.

### Áreas fechadas ✅ v1 (`areas-plugin`)
Ninguém sai do spawn, das minas, da arena do boss, da loja e das arenas (X1, Gladiador e plateias) por fora do caminho, nem quem joga, nem quem assiste: lá dentro não dá pra voar (`/fly` do VIP), planar de elytra, jogar pérola do fim, usar tridente com Correnteza, fruta do coro nem carga de vento. Quem entra voando ou planando cai, sem dano. Os admins e o criativo passam. As áreas ficam no config (mundo inteiro ou caixa).
- [ ] Comandos de teleporte de jogador pra jogador (`/tpa`, `/back`, `/home`) dentro das arenas de evento, se aparecerem: hoje os plugins de evento já barram comandos de quem participa. 🟡

---

## 🔧 Features nos plugins atuais

### 🐉 BossPlugin
- [x] **Ranking de dano com prêmio**: o top 3 recebe coins proporcionais ao dano, e não só o último hit.
- [x] **XP de skill por participar**, conforme o dano causado.
- [x] **`/boss proximo`**: mostra quando é o próximo spawn da agenda.
- [x] **Código de VIP pro campeão**: entrega opcional de um código de VIP de curta duração. Funciona como vitrine do VIP.
- [ ] **Boss "elite"**: aparece raramente, com o dobro de vida e de dano e loot melhor. 🟡
- [x] **Recompensa em dinheiro** pro campeão, além do item.

### ⚔️ SkillsPlugin
- [x] **`/skills top <skill>`**: ranking global por skill.
- [x] **Bônus sazonal**: XP em dobro configurável (por exemplo, no fim de semana), com aviso no chat. Fim de semana pelo config (`xp-dobro.dias`) ou na hora com `/skills admin xpdobro <mult> <horas>`.
- [x] **Efeito visual no level up**, com partícula própria de cada skill.
- [ ] **Prestígio**: no nível 50, resetar a skill em troca de um bônus permanente. 🟡
- [ ] **Scoreboard lateral** com o XP da skill em uso. 🟡
- [ ] **Escudo Perfeito (parry)**: bloquear no tempo exato anula o dano e atordoa o atacante. 🟡
- [ ] **Corrigir**: morrer só por Sangramento não credita quem aplicou o efeito. 🟡

### 🛒 EconomyShopPlugin
- [x] **Eventos de mercado**: um item sorteado tem o preço multiplicado por algumas horas ("alta do diamante"), com anúncio no chat. A cada `intervalo-horas`, com uma chance, por `duracao-horas`; a staff liga com `/shop admin evento <item> <mult> <horas>`, e `/shop evento` mostra o item em alta.
- [x] **Venda em massa**: `/sell all` (e `/sell` vende o item da mão).
- [x] **Categorias fixas**: uma lista definida no config em vez de texto livre, pra evitar "Minerio" e "Minerios" separados. `categorias` no config ("Nome:ÍCONE"), na ordem do menu.

### 💸 PixPlugin
- [ ] **Alerta de transação suspeita**: um envio grande perto do limite, ou uma conta nova recebendo de várias contas, gera aviso pra staff. Aproveita o `LimiteManager`. 🟡
- [x] **`/pix chave <apelido>`**: um apelido para receber pagamentos.
- [x] **`/pix agendar`**: pagamentos recorrentes entre jogadores. (O imposto do terreno e o aluguel das barracas já se pagam sozinhos.)
- [x] **Som e título na tela** ao receber um pagamento ou uma cobrança.
- [x] **`/pix admin log <jogador>`**: a staff investiga as transações de alguém.
- [x] **Taxa no histórico**: o extrato passa a mostrar o valor líquido e a taxa de cada transação.

### 🧾 ChequePlugin
- [x] **Cheque nominal**: só a pessoa indicada pode resgatar.
- [ ] **Cancelar ou estornar** um cheque emitido por engano. 🟡
- [x] **Log** de cheques emitidos e resgatados.
- [x] **Limite de valor** por cheque, ou cooldown. Valor máximo e cooldown no config; `cheque.limite.ignorar` pula os dois. O ID resgatado não resgata de novo (segura cópia por dupe).

### 👑 VipPlugin
- [ ] **Integração com o site via RCON**: o plugin já está pronto, falta construir o site. 🔴 (fora do plugin)
- [x] **Preço mínimo e máximo** no `/vip vender`.
- [x] **Um warp diferente para cada tier.** `/vip setwarp <tier>`; o tier sem área própria vai pra área geral.
- [ ] **Ler a tag do prefixo do LuckPerms**, pra configurar num lugar só. 🟡
- [x] **Avisar o vendedor** quando o código à venda dele vence.
- [x] **Kit mensal** (`/vip kitmensal`): armadura, armas e ferramentas, de diamante no Bronze e de netherite nos outros, com encantamentos acima do máximo normal (nível IV no Bronze até VII no Diamante; Fortuna IV e V no Ouro e no Diamante).
- [x] **Bônus de ativação:** dinheiro proporcional ao próximo rank (10% a 50%) e XP em todas as skills, os dois proporcionais aos dias do VIP.
- [x] **Perks em outros plugins, por permissão:** XP a mais nas skills, desconto na taxa do Pix, missão diária extra e venda automática.

### 🧹 ItemCleaner
- [ ] **Achados e perdidos**: os itens limpos vão pra uma caixa que dá pra recuperar pagando uma taxa. Também tira dinheiro de circulação e evita reclamação de quem perdeu item. 🟡
- [x] **Limpar orbes de XP** também.
- [x] **Aviso da limpeza manual configurável** (hoje é fixo em 10s).
- [x] **Não limpar itens perto de jogadores.** `protecao-perto-de-jogador` (8 blocos).

---

## 🔗 Integrações entre plugins
- O Boss paga em coins o ranking de dano.
- ~~Missões dão XP de skill.~~ ✅
- ~~Bounty, Barracas, Duelo e Gladiador aparecem no `/pix historico`.~~ ✅
- ~~Missões de vender na loja e de dano no boss.~~ ✅
- ~~Clãs ganham pontos no boss e mostram a tag na tab junto com o VIP.~~ ✅
- ~~O rank dá bônus no `/sell` da loja.~~ ✅
- ~~O Discord recebe eventos do Boss, do Gladiador e dos leilões (terrenos e barracas).~~ ✅ Faltam o VIP e o Pix.
- ~~O aluguel de barraca e o imposto do terreno se pagam sozinhos.~~ ✅ (pagamento automático de cada plugin; o `/pix agendar` é pra pagamentos entre jogadores)

---

## ✅ Concluído recentemente
- **Pix**: cobranças e histórico salvos em `dados.yml`.
- **Pix**: limite diário de transferência (`/pix limite`).
- **Boss**: ranking de dano anunciado quando o boss morre.
- **Boss**: contagem regressiva antes do spawn agendado.
- **VIP**: cooldowns de heal/feed/kit salvos em disco.
- **VIP**: prazo opcional pra resgatar códigos.
- **Barracas**: mercado de barracas alugadas, com lojas por placa e leilão das especiais (v1).
- **Painel da economia** (v1): `/economia`, com circulação e fluxo por plugin.
- **Missões diárias** (v1): 3 missões por dia, com coins e XP de skill.
- **Bounty** (v1): recompensa pela cabeça de jogadores.
- **Clãs** (v1): tag, cargos, banco, fogo amigo e ranking.
- **Correção**: Pix, Cheque, venda de VIP e Barracas recusavam valores negativos, mas aceitavam `NaN` e `Infinity` digitados. Agora recusam também.
- **Painel**: fluxo por tipo (`/economia tipos`) e alerta de crescimento da circulação.
- **Missões**: missões de vender na loja e de dano no boss, menu visual e missões semanais.
- **Bounty**: contrato contra um clã inteiro e anúncio no Discord.
- **Barracas**: placa `[Comprar]`, placa de entrada ("Aluga-se"), preço por barraca e renovação automática.
- **Clãs**: tag na tab junto com o VIP, pontos e clã do campeão no boss, fogo amigo com poção e explosão, e banco no `/pix historico`.
- **Correção**: o `plugin.yml` do Pix tinha um `:` sem aspas, e o Paper recusava carregar o Pix.
- **Servidor de teste** (`test-server/`): montagem automática, configs de teste e backup automático do mundo.
- **Rankup** (v1): ranks medievais, minas que se regeneram e bônus de venda na loja.
- **Balanceamento do rankup**: simulação da progressão, minas e preços calibrados pra 150 h até o Rei, e medição da economia (painel, loja e rankups) com um script de análise.
- **VIP**: kit mensal com equipamento completo encantado acima do máximo, bônus de ativação (dinheiro e XP), XP a mais nas skills, desconto no Pix, missão extra e venda automática. O rankup foi recalibrado com Fortuna III como referência.
- **Correção**: o Rankup não estava no `loadbefore` do painel e podia pegar a economia antes dele, ficando fora do fluxo por plugin.
- **Mundo de recursos** (`recursos-plugin`): `/recursos` pra um ponto aleatório seguro, borda de 6.000 blocos e reset a cada 30 dias.
- **Servidor de teste**: o `montar.bat` baixa o Multiverse, o WorldEdit, o ViaVersion e o ViaBackwards, e o `configurar-mundos.bat` cria os 4 mundos do rankup.
- **Terrenos**: placa na entrada e permissões separadas pros amigos.
- **Duelo**: espectadores. **Gladiador**: borda que fecha.
- **Rankup**: prestígio, rank na tab, menus do `/ranks` e do `/mina` e holograma das minas.
- **Pix**: `/pix agendar`.
- **Ponte com o Discord** (`discord-plugin`) e **recompensa diária** (`diaria-plugin`).
- **Mapa** (2026-10-24): EstruturasPlugin (spawn, Avenida das Minas, X1, coliseu e arena do boss enterrada), barracas com loja por NPC, NPCs de dicas e `montar-mapa.js` por partes. O boss não quebra mais blocos, e o mundo `spawn` passou pra dificuldade normal (sem mobs naturais), pro boss existir lá.
- **Combate**: golpe com a segunda mão funcionando com o cliente de verdade.
