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

## Foco atual (2026-10-27) — implementado e testado

1. ~~Skills: correção do Sangramento, parry com escudo e prestígio no nível 50~~ ✅
2. ~~Placar: linha com a skill em uso e o XP dela~~ ✅
3. ~~NPCs: um na entrada de cada mina, com informações e teleporte~~ ✅
4. ~~Oficinas: Compactador e Cambista (fragmentos das minas por encantamentos VI a VIII) no fundo da avenida, e missões de reparar e encantar~~ ✅
5. ~~Boss elite, alerta de Pix suspeito, cancelar cheque, tag do VIP pelo LuckPerms, achados e perdidos do ItemCleaner e `/tpa`, `/back` e `/home` barrados nas arenas~~ ✅

Testado no servidor de teste pelos bots (`tools/bots/teste-foco.js` e os testes antigos). Os custos do Cambista e a chance do fragmento (1 em 4.000 blocos) são a primeira calibragem: conferir no `analisar.py` com jogadores de verdade.

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
- **NPCs**: plugin nosso, com o Mannequin do 26.2 (sem Citizens). Já existem os vendedores das barracas (skin do dono) e 12 NPCs de dicas pelo spawn (DicasPlugin), com balão de fala animado. Na entrada de cada mina, um guia (OficinasPlugin, `mina:<id>`) mostra as informações dela e leva até ela quem tem o rank ✅ (2026-10-27). O NPC da loja saiu do roadmap em 2026-10-27: a Loja Oficial já tem os vendedores.
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
Tag no chat e na tab (convive com a do VIP), cargos (líder, oficial, membro), convites com botão, banco compartilhado com extrato (também no extrato do Bancário), fogo amigo desligado (inclusive poção e explosão), chat do clã (`/cc`) e ranking por pontos (abates + boss).

**Próximos passos**
- [x] Tag na tab list, junto com a do VIP.
- [x] Integração com o Boss: clã do campeão e pontos no ranking.
- [x] Fogo amigo também pra poção arremessada e explosão (TNT e cristal do End).
- [x] Banco do clã no extrato do Bancário (o Pix ganhou transação com "entidade").
- [ ] Guerra de clãs: declarar guerra, placar de abates entre os dois e prêmio pro vencedor. 🟡

### Rankup ✅ v1 (`rankup-plugin`)
12 ranks medievais pagos com coins (`/rankup`, `/rankup max`), com bônus de venda no `/sell` por rank. Minas próprias (`/mina`), com composição por peso, reset por tempo e por % minerada e acesso pelo rank. Tag do rank no chat e proteção opcional do mundo fora das minas.

**Próximos passos**
- [x] Prestígio: no Rei, `/prestigio` volta pro Camponês com +10% de venda por nível (até 10) e a marca ✦ no chat e na tab.
- [ ] Mina exclusiva de prestígio. 🟡
- [x] Rank na tab, junto com o clã e o VIP.
- [ ] Encantamentos de picareta comprados com coins ou com uma moeda própria (explosão, mais fortuna, venda automática). 🔴 (Eficiência e Fortuna acima do máximo já saem no Cambista, com o Fragmento Arcano das minas; falta a explosão.)
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
- [x] Fundo de bedrock em y = 0 (2026-10-25), também nos chunks que já existiam.
- [x] Holograma do terreno em leilão mostra quantos itens estão guardados lá dentro (2026-10-25).
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
- [x] NPC em cada mina (2026-10-27): o guia do OficinasPlugin, posto pelo `montar-mapa.js`.
- [x] Duas barraquinhas novas no fundo da avenida (Cambista e Compactador), na estrutura `oficinas` (2026-10-27).
- [ ] Cópias da arena de X1, pra mais de um duelo ao mesmo tempo. 🟢

### NPCs de dicas ✅ v1 (`dicas-plugin`)
NPCs pelo spawn que falam ao receber um clique direito: o balão cresce, escreve letra por letra e some. Cada clique mostra a próxima fala. 12 NPCs com 4 falas cada (`/dica` pra criar e editar).

### Oficinas da mina ✅ v1 (`oficinas-plugin`)
Quatro barraquinhas temáticas na praça de entrada da Avenida das Minas (estrutura `oficinas`), cada uma com um NPC de serviço: **Ferreiro** (repara os itens por uma taxa pelo desgaste, material e encantamentos), **Bruxa** (mesa de encantamento sempre no nível 30, com a câmara de estantes escondida embaixo da barraca), **Picareteiro** (picaretas prontas, encantadas ou não) e **Sábio** (níveis de XP). A bruxa põe o lápis; cada NPC tem skin temática. Desconto de VIP e registro no extrato do Bancário.
- [x] Mais oficinas no fundo da avenida (2026-10-27): **Compactador** (minérios e lingotes em blocos e de volta, de graça) e **Cambista**, que troca **Fragmentos Arcanos** (caem nas minas, 1 em 4.000 blocos, ~1 por hora) e coins por Eficiência, Fortuna, Afiação, Proteção e Poder VI a VIII e Saque e Inquebrável IV a VI (o Saque vai até um acima do que dá pra ter no jogo), cada um só nos itens que fazem sentido (Afiação em espada e machado, nunca em picareta ou pá). Caro de propósito: ~105 h de mina pra Eficiência VIII, ~175 h pra Fortuna VIII e ~140 h pra Saque VI, mais milhões em coins.
- [ ] Calibrar a chance do fragmento e os custos do Cambista com jogadores de verdade (`analisar.py`). 🟡
- [x] Missões nas oficinas (2026-10-27): tipos `reparar` (Ferreiro) e `encantar` (Bruxa e Cambista), com uma diária e uma semanal de cada.
- [x] Machados no Picareteiro, pra mina do bosque (2026-10-25).

### Placar lateral ✅ v1 (`placar-plugin`)
Scoreboard no canto da tela com saldo, rank, barra até o próximo rank, clã, missões do dia, próximo boss e jogadores online. Linhas no config, com placeholders; `/placar` liga e desliga.
- [x] Linha com a skill em uso e o XP dela (2026-10-27): `{skill}` e `{skill-progresso}`, a skill da arma do último golpe (ou a da mão).

### Loja Oficial ✅ v1 (`economy-shop-plugin` + estrutura `loja`)
Prédio fechado colado no portão LESTE do spawn (`/warp loja`), com dez balcões, cinco de cada lado: sete vendedores temáticos, um por categoria (minerador, construtor, lenhador, fazendeiro, caçador, jardineiro, alquimista), o Domador (spawners), o Maquinista (máquinas) e o Armeiro (PvP). Comprar e vender é só com eles; `/shop`, `/buy` e `/sell` ficaram pra staff. Nas minas, tudo o que se quebra vira dinheiro na hora, pra todos. VIP compra com 3/6/9/12% de desconto e vende com 3/6/9/12% a mais. Sem limite de quantidade: o preço dinâmico regula, e farm AFK é bem-vinda. Uma trava (90% do menor preço de compra) impede comprar e revender com lucro. Catálogo de 356 itens gerado e conferido pelo `tools/economia/loja.py` (nenhuma brecha com craft, fundição ou quebra de bloco).
- [ ] Ajustar preços com o `analisar.py` depois de alguns dias com jogadores. 🟡

### Spawners ✅ v1 (`spawners-plugin`)
Spawners empilhados nos terrenos, comprados no balcão Spawners da Loja Oficial (12 tipos, cada um liberado num rank). Iguais no mesmo bloco se somam, e os mobs se juntam numa pilha só em cima do spawner, que o jogador mata na espada. Os drops vendem no Caçador. As skills dão 25% do XP em mob de pilha, e as missões não contam.
- [x] A pilha é um mob normal (anda, a água arrasta, ataca) e nasce em lugar livre, e um abate mata a pilha toda, com os drops e o XP de todos (2026-10-25). O XP de skill continua só pelo dano do golpe.
- [x] **Warp mobs** (2026-10-25): salão de estátuas a oeste do spawn (portão "MOBS", `/warp mobs`), inspirado na Craftlandia. Cada estátua gera mobs normais em volta do spawner dela (até 4), com drops, XP e XP de skill cheios; as missões não contam.
- [ ] Ver no `analisar.py` quanto os spawners rendem de verdade e calibrar os preços (agora com a pilha morrendo de uma vez e farms possíveis). 🟡

### Máquinas e produtos ✅ v1 (`maquinas-plugin` + `pacote-plugin`)
Cinco máquinas nos terrenos (Tear, Forja, Destilaria, Lapidadora e Reator Arcano), cada uma liberada num rank, que com **Células de Combustível** compradas na loja fabricam **produtos** (Tecido Fino, Barra de Aço, Essência Destilada, Gema Lapidada e Cristal de Energia). Produzem pelo relógio, mesmo offline, até encher o estoque (uma a duas horas). Melhorias de velocidade (5% por nível) e estoque, e até 4 máquinas por jogador (VIP até 7). O Maquinista (balcão Máquinas) vende as máquinas e o combustível e compra os produtos por preço dinâmico, com o bônus do rank e do VIP. Cada máquina rende ~10% da mina do rank dela (`tools/economia/maquinas.py`).
- [x] **Textura própria do servidor**: resource pack servido pelo PacotePlugin (HTTP embutido, mandado ao entrar), com as texturas geradas pelo `tools/pacote/gerar.py` no estilo do spawn.
- [x] Produtos com ID próprio marcado no item (não dá pra falsificar, não entram em receita).
- [ ] Cadeias de produção (a saída de uma máquina é entrada de outra). 🟡
- [ ] `url-publica` do pacote no servidor de verdade (endereço e porta liberada). 🟢
- [x] Balcão **PvP** da loja: o Armeiro (OficinasPlugin) vende todas as armaduras e armas sem encantamento (couro a netherita), escudo, flechas e poções. Só compra.

### Montaria no spawn (`/montaria`) 🟡
Pedido de 2026-10-27: um comando (`/montaria` ou `/cavalo`) que dá uma montaria nas áreas sem PvP do spawn, para o jogador ir mais rápido das minas até a loja, por exemplo. A montaria some ao descer, ao sair da área ou ao entrar em combate, e não pode ser levada para as áreas de PvP.
- [ ] Definir: montaria única para todos ou por rank/VIP (cavalo, camelo...), velocidade e se dá para usar dentro das minas.

### Mine-sena ✅ v1 (no Bancário, `economy/cheque-plugin`)
A loteria do Bancário (2026-10-27). O jogador escolhe um número de 1 a 100 e compra o bilhete no balcão (R$ 500). Todo dia às 18:00 o servidor sorteia o número premiado, e o pote inteiro (todas as apostas acumuladas) vai pra quem acertou. Mais de um ganhador divide; sem ganhador, acumula pro próximo sorteio. Resultado no chat e no Discord, e aposta e prêmio no extrato do Bancário.

As outras ideias de banco (empréstimo, poupança, câmbio de fragmentos, ações e negócios dos jogadores) foram descartadas em 2026-10-27.

### Extrato no Bancário ✅ (2026-10-27)
O `/pix historico` saiu: o extrato agora é um livro que o Bancário entrega, com as últimas 30 movimentações. Entraram nele o que faltava: os prêmios em dinheiro do boss, a revenda de código VIP e o bônus da ativação, a taxa dos achados e perdidos, e a folha, a emissão e o cancelamento de cheque. Só a venda automática das minas fica de fora (seria uma linha por bloco). O histórico guarda as últimas 50 de cada jogador.

### Áreas fechadas ✅ v1 (`areas-plugin`)
Ninguém sai do spawn, das minas, da arena do boss, da loja e das arenas (X1, Gladiador e plateias) por fora do caminho, nem quem joga, nem quem assiste: lá dentro não dá pra voar (`/fly` do VIP), planar de elytra, jogar pérola do fim, usar tridente com Correnteza, fruta do coro nem carga de vento. Quem entra voando ou planando cai, sem dano. Os admins e o criativo passam. As áreas ficam no config (mundo inteiro ou caixa).
- [x] **Sem construção** (2026-10-25): ninguém quebra nem coloca bloco no spawn, na loja, na arena do boss e nas arenas de X1 e Gladiador (balde, fogo, molduras e explosão também). As minas ficam de fora.
- [x] Comandos de teleporte (`/tpa`, `/tpaccept`, `/back`, `/home`, `/sethome`...) barrados no mundo `arenas` (2026-10-27), e o teleporte que um deles pediu não entra lá (o `/back` pro lugar da morte, uma casa antiga). `/spawn` e `/warp` continuam.

---

## 🔧 Features nos plugins atuais

### 🐉 BossPlugin
- [x] **Ranking de dano com prêmio**: o top 3 recebe coins proporcionais ao dano, e não só o último hit.
- [x] **XP de skill por participar**, conforme o dano causado.
- [x] **`/boss proximo`**: mostra quando é o próximo spawn da agenda.
- [x] **Código de VIP pro campeão**: entrega opcional de um código de VIP de curta duração. Funciona como vitrine do VIP.
- [x] **Boss "elite"** (2026-10-27): 10% de chance a cada boss aleatório (ou `/boss start [id] elite`), com 2× de vida e de dano, recompensas em dobro e o loot do chão sorteado duas vezes.
- [x] **Recompensa em dinheiro** pro campeão, além do item.

### ⚔️ SkillsPlugin
- [x] **`/skills top <skill>`**: ranking global por skill.
- [x] **Bônus sazonal**: XP em dobro configurável (por exemplo, no fim de semana), com aviso no chat. Fim de semana pelo config (`xp-dobro.dias`) ou na hora com `/skills admin xpdobro <mult> <horas>`.
- [x] **Efeito visual no level up**, com partícula própria de cada skill.
- [x] **Prestígio** (2026-10-27): `/skills prestigio <skill>` no nível 50 volta pro 1, e cada prestígio (até 5) deixa tudo o que a skill dá 10% mais forte.
- [x] **Scoreboard lateral** com o XP da skill em uso (2026-10-27, no PlacarPlugin).
- [x] **Escudo Perfeito (parry)** (2026-10-27): escudo levantado até 0,3 s antes de um golpe corpo a corpo anula o dano e atordoa quem bateu por 1 s (sem andar e sem bater), com recarga de 3 s.
- [x] **Corrigido** (2026-10-27): quem morre só de Sangramento agora dá o abate a quem aplicou (Bounty, clãs, missões, Gladiador).

### 🛒 EconomyShopPlugin
- [x] **Eventos de mercado**: um item sorteado tem o preço multiplicado por algumas horas ("alta do diamante"), com anúncio no chat. A cada `intervalo-horas`, com uma chance, por `duracao-horas`; a staff liga com `/shop admin evento <item> <mult> <horas>`, e `/shop evento` mostra o item em alta.
- [x] **Venda em massa**: `/sell all` (e `/sell` vende o item da mão).
- [x] **Sem troca com villager** (2026-10-25), no servidor todo: esmeralda passaria por fora da loja.
- [x] **Categorias fixas**: uma lista definida no config em vez de texto livre, pra evitar "Minerio" e "Minerios" separados. `categorias` no config ("Nome:ÍCONE"), na ordem do menu.

### 💸 PixPlugin
- [x] **Alerta de transação suspeita** (2026-10-27): Pix alto, envio que deixa o pagador com 90% do limite diário e conta nova (menos de 48 h) recebendo de 3 contas em 24 h avisam a staff no chat (`pix.alertas`), no Discord e no `alertas.log`.
- [x] **`/pix chave <apelido>`**: um apelido para receber pagamentos.
- [x] **`/pix agendar`**: pagamentos recorrentes entre jogadores. (O imposto do terreno e o aluguel das barracas já se pagam sozinhos.)
- [x] **Som e título na tela** ao receber um pagamento ou uma cobrança.
- [x] **`/pix admin log <jogador>`**: a staff investiga as transações de alguém.
- [x] **Taxa no histórico**: o extrato passa a mostrar o valor líquido e a taxa de cada transação.

### 🧾 ChequePlugin
- [x] **Cheque nominal**: só a pessoa indicada pode resgatar.
- [x] **Cancelar e estornar** um cheque emitido por engano (2026-10-27): `/cheque cancelar` com ele na mão, ou `/cheque cancelar <id>` (staff) com ele no inventário de outro. O valor volta pra quem emitiu.
- [x] **O cheque sai do saldo de quem emite** (2026-10-27). Antes, emitir não custava nada.
- [x] **Folha de cheque e Bancário** (2026-10-27): o cheque só nasce numa folha comprada no Bancário (NPC do OficinasPlugin) e só vira dinheiro depositado lá, com taxa de compensação igual à do Pix. Liberado pra todos os jogadores.
- [x] Barraquinha do Bancário no spawn (2026-10-27), estrutura `banco` no quadrante nordeste, com a folha de cheque, o depósito e a Mine-sena (ver "Mine-sena", acima).
- [x] **Log** de cheques emitidos e resgatados.
- [x] **Limite de valor** por cheque, ou cooldown. Valor máximo e cooldown no config; `cheque.limite.ignorar` pula os dois. O ID resgatado não resgata de novo (segura cópia por dupe).

### 👑 VipPlugin
- [ ] **Integração com o site via RCON**: o plugin já está pronto, falta construir o site. 🔴 (fora do plugin)
- [x] **Preço mínimo e máximo** no `/vip vender`.
- [x] **Um warp diferente para cada tier.** `/vip setwarp <tier>`; o tier sem área própria vai pra área geral.
- [x] **Saque fora do kit mensal do Ouro e do Diamante** (2026-10-27): o Cambista vende até Saque VI. Os kits ainda vão ser revistos.
- [x] **Ler a tag do prefixo do LuckPerms** (2026-10-27): `tag-do-luckperms: true`; o grupo sem prefixo usa o `nome-exibicao`.
- [x] **Avisar o vendedor** quando o código à venda dele vence.
- [x] **Kit mensal** (`/vip kitmensal`): armadura, armas e ferramentas, de diamante no Bronze e de netherite nos outros, com encantamentos acima do máximo normal (nível IV no Bronze até VII no Diamante; Fortuna IV e V no Ouro e no Diamante).
- [x] **Bônus de ativação:** dinheiro proporcional ao próximo rank (10% a 50%) e XP em todas as skills, os dois proporcionais aos dias do VIP.
- [x] **Perks em outros plugins, por permissão:** XP a mais nas skills, desconto na taxa do Pix, missão diária extra e venda automática.

### 🧹 ItemCleaner
- [x] **Achados e perdidos** (2026-10-27): o item com dono (jogado por ele ou do corpo dele) que a limpeza tiraria vai pro `/achados`, e cada pilha volta por R$ 100. Guarda 45 pilhas por 3 dias; nas arenas, nada vai pra caixa. Com isso, a limpeza deixou de esperar o item ter 10 s no chão.
- [x] **Limpar orbes de XP** também.
- [x] **Aviso da limpeza manual configurável** (hoje é fixo em 10s).
- [x] **Não limpar itens perto de jogadores.** `protecao-perto-de-jogador` (8 blocos).

---

## 🔗 Integrações entre plugins
- O Boss paga em coins o ranking de dano.
- ~~Missões dão XP de skill.~~ ✅
- ~~Bounty, Barracas, Duelo e Gladiador aparecem no extrato do Bancário.~~ ✅
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
- **Clãs**: tag na tab junto com o VIP, pontos e clã do campeão no boss, fogo amigo com poção e explosão, e banco no extrato do Bancário.
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
- **Foco de 2026-10-27**: Sangramento credita o abate, parry com escudo, prestígio de skill, skill em uso no placar, guia em cada mina, Fragmento Arcano nas minas, Cambista e Compactador, missões de reparar e encantar, Boss elite, alerta de Pix suspeito, cancelar cheque, tag do VIP pelo LuckPerms, achados e perdidos e teleporte por comando barrado nas arenas.
- **Correção**: o menu da loja vendia junto qualquer item do mesmo material (o produto de máquina, que é papel, saía junto com papel comum). Agora só vende item comum, sem nome, encantamento ou marca.
