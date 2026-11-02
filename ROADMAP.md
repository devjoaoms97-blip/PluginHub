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
| `recursos` (normal) | Mobs naturais, pesca, árvores e PvP do Bounty e dos clãs. Resetado todo dia 30 pelo `recursos-plugin`, com borda de 6.000 blocos e `/warp recursos` para um ponto aleatório seguro | Sim |
| `recursos_nether` | Nether de recursos: `/warp nether` para um ponto seguro, borda de 3.000, resetado todo dia 20 | Sim |
| `recursos_end` | End de recursos: `/warp end` para o quadrado verde do centro (sem PvP em cima dele), centro protegido num raio de 100, dragão todo dia às 21h, borda de 5.000, resetado todo dia 10 | Sim |
| `tesouros` (superplano) | Mapas do tesouro (`tesouros-plugin`, `/warp tesouros`, com o templo da chegada): cavar no X abre uma câmara de guardiões com o baú. Ninguém quebra nem constrói | Não |
| `arenas` (vazio) | Arena de X1 e arena do Gladiador, a 1.200 blocos uma da outra. Só se entra por comando ou warp (a plateia por `/warp plateia-x1` e `/warp plateia-gladiador`), com volta ao spawn no fim | Sim |

- **Visual do spawn** ✅ (2026-10-24): em vez de um mapa pronto, as construções saem de código, pelo EstruturasPlugin (`/estrutura construir <id>`), inspiradas em mapas da CurseForge e da BuiltByBit e ajustadas jogando.
- **Mapa funcional** ✅: `tools/bots/montar-mapa.js` monta tudo por partes (`minas`, `spawn`, `barracas`, `boss`, `dicas`, `arenas`, `marcar`), pra dar pra refazer uma parte sem mexer no resto, e cadastra tudo nos plugins:
  - **Spawn** (`spring`): fechado, com fonte, jardins, 32 barracas com NPC e só dois portões. O norte encosta na **Avenida das Minas** (`minas`), com os 12 poços; o sul desce por uma escadaria fechada até a **arena do boss** (`toxica`), enterrada 32 blocos pra não aparecer por cima do spawn, com um degrau de 4 blocos na chegada pros mobs do boss não subirem.
  - **Arenas**: X1 `axepvp` (nichos fechados pros lutadores, teto de vidro e galeria da plateia) e Gladiador `coliseu` (3x maior, teto de vidro e arquibancada fechada), longe uma da outra.
  - As 12 minas entram prontas com `cadastrar --minas`. Navegação a pé, mais `/spawn` e `/warp` (o `/spawn` vem do EssentialsXSpawn).
- **Terrenos:** plugin nosso, e não o PlotSquared, para integrar com a economia (imposto, Pix e painel).
- **Servidor de teste:** o `montar.bat` baixa o Multiverse e o WorldEdit, e o `configurar-mundos.bat` cria os mundos (spawn, arenas, terrenos, recursos, recursos_nether, recursos_end e tesouros).
- **Mundo de recursos:** as missões de matar mobs, pescar, colher e cortar árvores precisam dele, porque mob de spawner não conta e o rankup protege o mundo fora das minas.
- **NPCs**: plugin nosso, com o Mannequin do 26.2 (sem Citizens). Já existem os vendedores das barracas (skin do dono) e 13 NPCs de dicas pelo spawn (DicasPlugin), com balão de fala animado. Na entrada de cada mina, um guia (OficinasPlugin, `mina:<id>`) mostra as informações dela e leva até ela quem tem o rank ✅ (2026-10-27). O NPC da loja saiu do roadmap em 2026-10-27: a Loja Oficial já tem os vendedores.
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
- [x] **Leiloeiro** (2026-11-01): a contagem dos itens guardados existia, mas nunca entrou no holograma do leilão. Agora quem conta é um NPC, o Leiloeiro, na rua, colado na entrada de cada terreno em leilão, atrás de um atril: a cada clique, uma fala num balão, como os NPCs do spawn ("Dou-lhe uma!..."), com o lance, de quem era, quantos itens estão guardados (nunca quais), quantas máquinas e quantos spawners tem, o lance mínimo e o tempo que falta. O terreno em leilão ficou sem holograma.
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
NPCs pelo spawn que falam ao receber um clique direito: o balão cresce, escreve letra por letra e some. Cada clique mostra a próxima fala. 13 NPCs com 4 a 7 falas cada (`/dica` pra criar e editar).

### Oficinas da mina ✅ v1 (`oficinas-plugin`)
Quatro barraquinhas temáticas na praça de entrada da Avenida das Minas (estrutura `oficinas`), cada uma com um NPC de serviço: **Ferreiro** (repara os itens por uma taxa pelo desgaste, material e encantamentos), **Bruxa** (mesa de encantamento sempre no nível 30, com a câmara de estantes escondida embaixo da barraca), **Picareteiro** (picaretas prontas, encantadas ou não) e **Sábio** (níveis de XP). A bruxa põe o lápis; cada NPC tem skin temática. Desconto de VIP e registro no extrato do Bancário.
- [x] Mais oficinas no fundo da avenida (2026-10-27): **Compactador** (minérios e lingotes em blocos e de volta, de graça) e **Cambista**, que troca **Fragmentos Arcanos** (caem nas minas, 1 em 4.000 blocos, ~1 por hora) e coins por Eficiência, Fortuna, Afiação, Proteção e Poder VI a VIII e Saque e Inquebrável IV a VI (o Saque vai até um acima do que dá pra ter no jogo), cada um só nos itens que fazem sentido (Afiação em espada e machado, nunca em picareta ou pá). Caro de propósito: ~105 h de mina pra Eficiência VIII, ~175 h pra Fortuna VIII e ~140 h pra Saque VI, mais milhões em coins.
- [ ] Calibrar a chance do fragmento e os custos do Cambista com jogadores de verdade (`analisar.py`). 🟡
- [x] Missões nas oficinas (2026-10-27): tipos `reparar` (Ferreiro) e `encantar` (Bruxa e Cambista), com uma diária e uma semanal de cada.
- [x] Machados no Picareteiro, pra mina do bosque (2026-10-25).
- [x] **Cambista começa no nível I** (2026-11-01): uma espada sem Saque ia direto pra Saque IV. Agora os níveis normais do jogo (de I ao máximo da mesa) saem um a um, só por coins (`niveis-comuns.preco-por-nivel`, R$ 25 mil × o nível), e do máximo normal o item segue pra tabela de fragmentos.

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
Cinco máquinas nos terrenos (Tear, Forja, Destilaria, Lapidadora e Reator Arcano), cada uma liberada num rank, que com **Células de Combustível** compradas na loja fabricam **produtos** (Tecido Fino, Barra de Aço, Essência Destilada, Gema Lapidada e Cristal de Energia). Produzem pelo relógio, mesmo offline, até encher o estoque (uma a duas horas). Melhorias de velocidade (5% por nível) e estoque, e até 10 máquinas por terreno, somando todos os tipos (era 4 por jogador, VIP até 7; mudou em 2026-10-31). O Maquinista (balcão Máquinas) vende as máquinas e o combustível e compra os produtos por preço dinâmico, com o bônus do rank e do VIP. Cada máquina rende ~10% da mina do rank dela (`tools/economia/maquinas.py`); com 10 no máximo num terreno, ~134% da mina.
- [x] **Textura própria do servidor**: resource pack servido pelo PacotePlugin (HTTP embutido, mandado ao entrar), com as texturas geradas pelo `tools/pacote/gerar.py` no estilo do spawn.
- [x] Produtos com ID próprio marcado no item (não dá pra falsificar, não entram em receita).
- [ ] Cadeias de produção (a saída de uma máquina é entrada de outra). 🟡
- [ ] `url-publica` do pacote no servidor de verdade (endereço e porta liberada). 🟢
- [x] Balcão **PvP** da loja: o Armeiro (OficinasPlugin) vende todas as armaduras e armas sem encantamento (couro a netherita), escudo, flechas e poções. Só compra.

### Montaria no spawn ✅ v1 (`montaria-plugin`)
`/montaria` (ou `/cavalo`) chama um cavalo pra andar mais rápido pelo mundo spawn e pelo mundo dos tesouros (decisões de 2026-10-27; tesouros em 2026-10-30):
- **Só VIP** (permissão `vip.perk.montaria`, em todos os tiers).
- **3× a corrida a pé** (era 1,6×; mudado em 2026-10-27), com pulo baixo (não pula balcão nem barraca).
- **No mundo spawn todo, menos os poços das minas e a arena do boss, e no mundo `tesouros`**: quem entra montado num lugar barrado desce e segue a pé.
- Some ao descer, sair, se teleportar, trocar de mundo ou tomar dano. Ninguém mais monta nela.
- [ ] Montaria diferente por tier (`tipos` no config já aceita), se quiser variar.

### Mine-sena ✅ v1 (no Bancário, `economy/cheque-plugin`)
A loteria do Bancário (2026-10-27). O jogador escolhe um número de 1 a 100 e compra o bilhete no balcão (R$ 500). Todo dia às 18:00 o servidor sorteia o número premiado, e o pote inteiro (todas as apostas acumuladas) vai pra quem acertou. Mais de um ganhador divide; sem ganhador, acumula pro próximo sorteio. Resultado no chat e no Discord, e aposta e prêmio no extrato do Bancário.

As outras ideias de banco (empréstimo, poupança, câmbio de fragmentos, ações e negócios dos jogadores) foram descartadas em 2026-10-27.

### Extrato no Bancário ✅ (2026-10-27)
O `/pix historico` saiu: o extrato agora é um livro que o Bancário entrega, com as últimas 30 movimentações. Entraram nele o que faltava: os prêmios em dinheiro do boss, a revenda de código VIP e o bônus da ativação, a taxa dos achados e perdidos, e a folha, a emissão e o cancelamento de cheque. Só a venda automática das minas fica de fora (seria uma linha por bloco). O histórico guarda as últimas 50 de cada jogador.

### PvP na arena do boss ✅ (2026-10-27)
A arena do boss (enterrada ao sul do spawn, com a escadaria) tem PvP sempre ligado. O mundo `spawn` passou a ter o PvP do jogo ligado, e o AreasPlugin barra o PvP fora da caixa da arena (`pvp-so-nas-caixas`). **No servidor de verdade:** `mv modify spawn set pvp true`.

### Loja de créditos ✅ v1 (`creditos-plugin`)
Decisões de 2026-10-27: a loja do Discord vende **créditos** (R$ 1 = 10); no jogo, `/creditos` troca por **VIP** (ativar ou código de presente pra vender no jogo) e **cosméticos** (rastros, cor do nome e efeitos de abate), por preço fixo. **Itens do jogo não são vendidos**, e nada pago tem sorteio (caixas pagas foram descartadas: ECA Digital e regras da Mojang). Quem contribui sem levar nada ganha a **tag de apoiador** por 30 dias a cada contribuição.
- [ ] O bot do Discord (loja com Mercado Pago) entregar os créditos com `creditos dar` e a tag com `apoiador dar`, pelo RCON. 🔴
- [ ] Ajustar os preços (VIP e cosméticos) antes de abrir a loja. 🟢

### Áreas fechadas ✅ v1 (`areas-plugin`)
Ninguém sai do spawn, das minas, da arena do boss, da loja e das arenas (X1, Gladiador e plateias) por fora do caminho, nem quem joga, nem quem assiste: lá dentro não dá pra voar (`/fly` do VIP), planar de elytra, jogar pérola do fim, usar tridente com Correnteza, fruta do coro nem carga de vento. Quem entra voando ou planando cai, sem dano. Os admins e o criativo passam. As áreas ficam no config (mundo inteiro ou caixa).
- [x] **Sem construção** (2026-10-25): ninguém quebra nem coloca bloco no spawn, na loja, na arena do boss e nas arenas de X1 e Gladiador (balde, fogo, molduras e explosão também). As minas ficam de fora.
- [x] Comandos de teleporte (`/tpa`, `/tpaccept`, `/back`, `/home`, `/sethome`...) barrados no mundo `arenas` (2026-10-27), e o teleporte que um deles pediu não entra lá (o `/back` pro lugar da morte, uma casa antiga). `/spawn` e `/warp` continuam.

### Login e regras de combate (pedidos de 2026-10-28)
- [x] **Login no servidor** (2026-10-28): AuthMe Reloaded 6.0.1 (`/register` e `/login`), baixado pelo `montar.ps1`, com mensagens em português, senha em BCRYPT (as antigas em SHA256 continuam valendo e viram BCRYPT no login) e sessão de 30 min no mesmo IP. No servidor de teste, sem limite de contas por IP e sem a janela de login durante a conexão, por causa dos bots (que agora registram e logam sozinhos). **No servidor de verdade:** `maxRegPerIp: 2` e pode ligar a janela (`dialog.preJoin`). Pular o login de quem tem conta original fica pra depois. 🟡
- [x] **Espera de 3 s no teleporte pra quem não é VIP** (2026-10-28): `teleport-delay: 3` no Essentials, com `essentials.teleport.timer.move` no grupo `default` (pode andar) e `essentials.teleport.timer.bypass` nos grupos VIP (teleporta na hora). Contagem a cada segundo ("Teleporte em 3 segundos." → 2 → 1 → "Teleportando...") e qualquer dano cancela, até queda e fogo (CombatePlugin, `ContagemTeleporte`; sozinho, o Essentials só cancelava no ataque de uma entidade).
- [x] **Sem teleporte depois de bater ou apanhar de jogador** (2026-10-28): 5 s pra todo mundo, VIP ou não (CombatePlugin, `EmCombate`). Os comandos de teleporte da lista são barrados e nenhum teleporte por comando leva quem está em combate.
- [x] **Deslogar em combate mata** (2026-10-28): morre na hora, com os itens no chão e aviso no chat. Expulsão e desligamento do servidor não contam, e no mundo `arenas` não vale (o Gladiador e o duelo já tratam a desconexão).
- [x] **Arena do boss com PvP só depois do pulo** (2026-10-28): a escadaria e o túnel ficaram sem PvP, e um holograma na borda do pulo avisa "PvP LIGADO o tempo todo, daqui pra frente" (AreasPlugin, `paineis`).

---

## 🔧 Features nos plugins atuais

### Pedidos novos para avaliar (2026-10-30)

#### Discord e orientação de jogadores
- [x] **Permissões dos botões de ticket** (2026-10-31): Assumir e Fechar são só da staff, e quem abriu o ticket não usa nenhum dos dois, nem se for da staff (`discord-bot/src/tickets.js`).
- [x] **Tutorial inicial com recompensa** (2026-10-31, `ajuda-plugin`): `/tutorial` com 5 etapas detectadas sozinhas (minerar 64 blocos na mina, `/warp loja`, `/ranks`, `/terreno auto`, `/diaria`) e um Mapa do Tesouro Comum no fim, uma vez por jogador (guardado se o inventário estiver cheio). Convite com botão ao entrar.
- [x] **Ajuda para iniciantes** (2026-10-31, `ajuda-plugin`): `/comandos` com o índice clicável e 11 páginas (`/comandos amigo`, `/comandos terreno`...); clicar num comando o digita no chat.
- [x] **Aceitar `/tpa` com clique** (2026-10-31, `ajuda-plugin`): botões [ACEITAR] e [RECUSAR] pra quem recebe o pedido; o Essentials continua validando o pedido e a expiração. O grupo `default` tem `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/home`, `/sethome` e `/delhome` (no `default.yml` do LuckPerms e no `montar-mapa.js`); o `teste-ajuda.js` confere sem dar permissão ao bot.

#### Mundos, arenas e eventos
- [x] **Painel "Como começar" removido** (2026-10-31): o holograma do fim da avenida oeste saiu (o `/tutorial` e o `/comandos` fazem esse papel). `node montar-mapa.js construir tutorial` apaga o painel de um mapa que ainda o tem.
- [x] **Montarias no mundo `tesouros`** (2026-10-30): `/montaria` liberado no `/warp tesouros`; as restrições das minas, da arena do boss e dos demais mundos foram mantidas.
- [x] **Recompensa do dragão do End** (2026-10-31): quem dá o último golpe ganha a tag `[Matador de Dragão]` no chat até o próximo dragão morrer (RecursosPlugin); numa morte indireta, conta o último jogador que acertou nos 30 s anteriores (flecha conta pro atirador, cristal pra quem quebrou). Sem dinheiro. O dragão sempre dá 1 Mapa do Tesouro direto no inventário de quem levou o crédito (sem ninguém com o crédito, ele cai do lado do portal), com as chances do boss (comum 45, raro 30, arcano 18, lendário 7; TesourosPlugin `drops.dragao`).
- [x] **Acesso às estruturas do `/boss`** (2026-10-31): cada torre ganhou escada de mão (lado do centro) e espiral de parkour em volta; cada contêiner, escada de mão na ponta (e até o empilhado). Vale ao reconstruir a `toxica`.
- [x] **Referências visuais no Gladiador** (2026-10-31): o piso tem quatro setores de cor (leste vermelho, sul verde, oeste amarelo, norte azul), com a faixa da parede, um obelisco com estandartes e o chão em volta na cor do setor. Vale ao reconstruir o `coliseu`.
- [x] **`/sethome` nas arenas** (2026-10-31): `/sethome` e `/createhome` barrados dentro da arena do boss, do piso do X1, do piso do Gladiador e da câmara ativa do tesouro (limites reais de cada uma); logo fora, liberado. `teste-sethome-arenas.js`.
- [x] **Toque Suave em spawners** (2026-10-31): spawner natural quebrado com picareta com Toque Suave vira o spawner da loja do mesmo mob, onde o jogador pode quebrar. A loja ganhou Aranha das Cavernas, Zumbi do Deserto, Afogado, Errante, Esqueleto Pantanoso, Piglin Zumbificado, Esqueleto Wither e Breeze (calibrados pelo `spawners.py`, ~20 h pra se pagar). Traça e cubo de magma ficaram de fora.

#### Terrenos, lojas e economia
- [x] **Carnes cozidas no Fazendeiro** (2026-10-31): o Fazendeiro compra boi, porco, frango, carneiro e coelho assados. A carne crua a loja deixou de vender (o Caçador continua comprando): comprar crua, assar e vender cozida dava lucro (`loja.py`).
- [x] **Creepers que não explodem** (2026-10-31): os da pilha de spawner e os da `/warp mobs` não miram no jogador nem explodem. `teste-creeper.js`.
- [x] **Recalibrar os spawners** (análise de 2026-10-31, `tools/economia/spawners.py`): o preço foi feito pra se pagar em ~20 h sem Pilhagem; com Pilhagem III a renda dobra ou triplica (se paga em 5 a 10 h), e isso fica como está (decisão de 2026-10-31). A ovelha, que só rendia o carneiro e se pagava em 45 h, agora vende a lã branca pro Fazendeiro e se paga em ~19 h, como os outros animais.
- [x] **Limite compartilhado de máquinas por terreno** (2026-10-31): até 10 máquinas em cada terreno, somando todos os tipos e donos (`limite-por-terreno`); saíram o limite por jogador (4) e o do VIP (5 a 7).
- [x] **Limite compartilhado de spawners por terreno** (2026-10-31): até 100 spawners em cada terreno, somando todos os tipos e donos (os empilhados contam um por um).
- [x] **Destino público para barraca alugada** (2026-10-31, BarracasPlugin): `/loja` lista as lojas abertas, `/loja <jogador>` leva à frente do vendedor dele (só enquanto a barraca está alugada; barrado em combate) e `/loja anunciar [mensagem]` anuncia no chat, com os itens no mouse e um [VISITAR], uma vez a cada 10 min.
- [x] **Nome das lojas** (2026-10-31): o nome bugado era o holograma de cada balcão da Loja Oficial, atravessado no telhado e com emoji que o Minecraft não desenha. Os hologramas saíram (o NPC já diz de que é a loja); reconstruir a `loja` apaga os que existem.
- [x] **Compras com o inventário cheio** (bug corrigido em 2026-10-31): a loja cobrava e jogava no chão o que não cabia, e a Limpeza levava (aconteceu com o Tear). Agora, sem espaço, a compra é recusada antes de cobrar, com o aviso "Seu inventário está cheio". Vale pro Maquinista (máquina e combustível), o Domador (spawners), a Loja Oficial (menu e `/buy`), o Armeiro e o Picareteiro, as folhas do Bancário, a emissão de cheque e o resgate do `/achados`. `teste-inventario-cheio.js`.
- [x] **Cobrança Pix entre jogadores com QR Code** (2026-10-31): `/pix qrcode <jogador> <valor> [motivo]` é a cobrança do `/pix receber` (saldo do jogo) com o botão [PAGAR]: uma por par, paga pelo `/pix aceitar` (saldo e limite conferidos antes), e pagar a encerra, então clicar de novo não paga duas vezes. Saiu o `/pix qrcode <valor>` público, que qualquer um pagava quantas vezes clicasse. Corrigido também: a cobrança mostrava o nome de quem recebia no lugar de quem cobrava.

- [x] **Máquinas sem prejuízo** (2026-10-31): o Tecido vendia a ~R$ 7 com R$ 6,25 de combustível por unidade. O preço passou a ser de cada jogador (as vendas de um não derrubam o dos outros), o mínimo de cada produto cobre o combustível com 25% de folga (Tecido 8, Barra 16, Essência 32, Gema 64) e o `passo` caiu pra quem enche o terreno de uma máquina vender a ~75% da âncora. O balcão mostra o gasto com duas casas ("0,25", antes "0,3") e o custo do combustível em reais. O `maquinas.py` ganhou a tabela do mercado.
- [x] **Valor total nas mensagens de compra** (2026-10-31): a compra de células de combustível (16 ou 64) não dizia quanto custou. Agora dizem o valor: o Maquinista (máquina, células e melhoria), a loja de créditos (cosmético, skin, chapéu e código de VIP), o `/rankup` de um rank só (antes só o de vários ranks mostrava o total) e o `/vip resgatar` de código comprado de outro jogador.

- [x] **Chat local e global** (2026-10-31, `chat-plugin`): a mensagem normal só chega a quem está no mesmo mundo (`[L]`) e `/g <mensagem>` chega a todos (`[G]`); a staff (`chat.espiar`) vê o local dos outros mundos. Antes o chat era um só.
- [x] **Anti-flood no chat** (2026-10-31, `chat-plugin`, novo): o chat e os comandos de mensagem (`/msg`, `/r`, `/cc`...) têm intervalo de 2 s, a mesma mensagem não repete por 30 s e mais de 5 em 15 s silencia por 30 s; `/vip vender`, `/pix qrcode`, `/pix receber`, `/tpa`, `/cla convidar` e `/bounty colocar` têm intervalo próprio. Vale pra staff também (só quem tem `chat.antiflood.ignorar` passa). O `/loja anunciar` deixou de isentar a staff, que era o que inundava o chat.

- [x] **Alquimista com todos os ingredientes de poção** (2026-10-31): o vendedor ganhou `extras` (itens de outra categoria, com o mesmo preço e mercado do vendedor de origem): verruga do Nether, redstone, pólvora, açúcar, pé de coelho, olho de aranha, creme de magma, cenoura dourada, vara de blaze, lágrima de ghast, escama de tartaruga, membrana de phantom, vara de breeze e pedra. Entraram na loja os que faltavam de vez: frasco de vidro, baiacu, bafo de dragão, bloco de slime, teia e casco de tartaruga (`loja.py`, sem brecha).

- [x] **Sem `/buy` e `/sell`** (2026-11-01): os comandos saíram do EconomyShopPlugin (eram só da staff). Comprar e vender é na `/warp loja` (vendedores da Loja Oficial) ou nas barracas dos jogadores; o minério continua vendido na hora, na mina. O `/shop` fica pra staff.
- [x] **`/vip pocoes`** (2026-11-01, perk `vip.perk.pocoes`, todos os tiers): junta as poções iguais do inventário (bebíveis, arremessáveis e persistentes) em pilhas de até 10 (`pocoes.max-pilha`), pelo tamanho máximo de pilha do item; beber ou arremessar gasta uma.
- [x] **"$1" no `/vip vender`** (2026-11-01): o VipPlugin formatava o dinheiro pelo Essentials ("$1"); agora mostra "R$ 1,00", como os outros plugins.

#### Limpeza e nomes
- [x] **Limpa dos comandos** (2026-11-01, AjudaPlugin): o jogador via 322 comandos no Tab (prefixo de plugin, apelidos do Essentials, WorldEdit, LuckPerms, AuthMe, `pl`...). Agora vê e usa só a lista `comandos-permitidos` (~60, os do servidor); o resto é "comando desconhecido". A staff vê tudo. O grupo `default` ganhou `/msg` e `/r`.
- [x] **Uma tag acima da cabeça** (2026-11-01, VipPlugin + PlacarPlugin): `[Fazendeiro] [DoD] [Diamante] Nome` ficava enorme. Acima da cabeça vai só a tag que o jogador escolhe no `/tag` (VIP, apoiador, clã, rank ou nenhuma); sem escolha, a do VIP ou, sem VIP, o rank. Na tab e no chat continuam todas.
- [x] **Corrigido** (2026-11-01): o PlacarPlugin era desativado a cada reinício (os times dos nomes ficam salvos no scoreboard principal, e apagá-los dava erro), sem placar nem tags.
- [x] **Limpeza do chão nas minas** (2026-10-31): a limpeza só alcançava chunks carregados, e as minas do fundo da avenida (até z -175) ficavam descarregadas sem ninguém por perto: os itens voltavam quando alguém chegava. Agora o item que nasceu antes da última limpeza sai quando o chunk dele carrega (com dono, vai pros achados). Os nomeados continuam protegidos (`ignorar-itens-nomeados`).
- [x] **Achados e perdidos** (2026-10-31): cada pilha fica 1 hora (`achados.horas`) e volta por R$ 10.000; as vencidas saem a cada minuto, mesmo de quem não abre o `/achados`, e o menu mostra os minutos que faltam. O que já estava guardado ganhou 1 hora a partir da atualização.
- [x] **Tags no nome acima da cabeça** (2026-10-31, PlacarPlugin): não havia time de scoreboard, e é só por ele que o Minecraft decora esse nome. Agora cada scoreboard em uso (o placar de cada jogador e o principal) tem um time por jogador com o mesmo prefixo da tab (rank, clã, VIP, apoiador) e a cor do nome comprada. `nome-na-cabeca.ativado` desliga.

### 🐉 BossPlugin
- [x] **Ranking de dano com prêmio**: o top 3 recebe coins proporcionais ao dano, e não só o último hit.
- [x] **XP de skill por participar**, conforme o dano causado.
- [x] **`/boss proximo`**: mostra quando é o próximo spawn da agenda.
- [x] **Corrigido** (2026-10-27): o código de VIP do campeão não era gerado desde o VIP de 30 dias fixos (o método mudou). Agora ele é um **código de evento** de 7 dias, e só o Wither dá (`recompensas.codigo-vip.bosses`).
- [x] **Agenda do boss** (2026-10-27): um boss normal sorteado de segunda a sábado às 20:00 e o **Wither, o especial, no domingo às 20:00**. A agenda aceita boss fixo por horário (`"DOMINGO 20:00 wither"`), e o `/boss proximo` mostra o nome dele.
- [x] **Tempo limite e maldição** (2026-10-27): o boss fica vivo 1 hora (avisos aos 10 e 1 min). Se ninguém derrotar, ele vai embora com uma frase global e o servidor fica amaldiçoado: XP de skill −10% (sem mexer no bônus de ativação do VIP) por 24 h ou até o próximo boss morrer. Os chunks da arena ficam carregados enquanto o boss vive (antes ele sumia sem ninguém perto).
- [x] **Corrigido** (2026-10-27): o Gladiador dava erro a cada dano entre entidades quando não havia evento.
- [x] **Código de VIP pro campeão**: entrega opcional de um código de VIP de curta duração. Funciona como vitrine do VIP.
- [x] **Corrigido** (2026-10-27): reiniciar com o boss vivo deixava um boss órfão na arena (sem barra nem ranking, matando quem chegasse). Agora o boss sai do mundo ao desligar, e qualquer boss órfão some quando a arena carrega.
- [x] **Boss "elite"** (2026-10-27): 10% de chance a cada boss aleatório (ou `/boss start [id] elite`), com 2× de vida e de dano, recompensas em dobro e o loot do chão sorteado duas vezes.
- [x] **Recompensa em dinheiro** pro campeão, além do item.
- [x] **Corrigido (2026-10-28): o Wither fugia da arena.** Ele voa, e o degrau de 4 blocos da chegada só segura quem anda: subiu a escadaria e foi parar no spawn. Agora todo boss que passa de `arena-raio` (46) blocos do centro, ou de `arena-altura` acima dele, volta pro centro, e o teleporte de ataque só mira quem está dentro da arena (antes, qualquer jogador do mundo).
- [x] **Corrigido (2026-10-28): efeitos continuavam depois da morte.** Quem morreu pro Wither renascia tomando o dano dele: o efeito em área do boss pegava quem já estava na tela de morte, e isso atravessava o renascimento. E o Sangramento e o Atordoamento só conferiam a morte uma vez por segundo. Agora os dois param na morte, o efeito em área pula os mortos e, um tique depois de renascer, qualquer poção que sobrou sai (SkillsPlugin, `MorteListener`).

### ⚔️ SkillsPlugin
- [x] **`/skills top <skill>`**: ranking global por skill.
- [x] **Bônus sazonal**: XP em dobro configurável (por exemplo, no fim de semana), com aviso no chat. Fim de semana pelo config (`xp-dobro.dias`) ou na hora com `/skills admin xpdobro <mult> <horas>`.
- [x] **Efeito visual no level up**, com partícula própria de cada skill.
- [x] **Prestígio** (2026-10-27): `/skills prestigio <skill>` no nível 50 volta pro 1, e cada prestígio (até 5) deixa tudo o que a skill dá 10% mais forte.
- [x] **Scoreboard lateral** com o XP da skill em uso (2026-10-27, no PlacarPlugin).
- [x] **Escudo Perfeito (parry)** (2026-10-27): escudo levantado até 0,3 s antes de um golpe corpo a corpo anula o dano e atordoa quem bateu por 1 s (sem andar e sem bater), com recarga de 3 s.
- [x] **Corrigido** (2026-10-27): quem morre só de Sangramento agora dá o abate a quem aplicou (Bounty, clãs, missões, Gladiador).
- [x] **Limite diário de XP no PvP** (2026-10-30): cada par atacante/vítima pode gerar até 1.000 XP efetivos por dia; o ganho diminui após 250 e 500 XP. O total é persistido e reinicia diariamente.
- [x] **Skills de profissão, fora do PvP** (ideias de 2026-11-01; números decididos e implementados em 2026-11-02 com o `simular.py`; `teste-profissoes.js`). As 13 skills de hoje são de combate. Todas com prestígio (+10% por prestígio, como as outras) e na curva de XP atual (515.850 XP até o nível 50). Ordem: Minerador, Agricultor, Pescador e Lenhador. 🔴
  - **Minerador** (1 XP por bloco quebrado à mão numa mina; chega ao nível ~45 quando vira Rei): chance de quebrar 3x3 com uma picaretada, 0,1% por nível (**5% no nível 50**, 7,5% no prestígio 5). Substitui o encantamento de "explosão" do Rankup. Encurta a meta de 150 h pra ~133 h (−11%), e **os preços dos ranks ficam como estão** (decisão de 2026-11-02: aceitar o encurtamento; `python tools/economia/simular.py --minerador 0.05`). Os blocos extras do 3x3 **só valem pra venda automática**: não dão Fragmento Arcano, mapa do tesouro, progresso de missão nem XP (a quebra extra dispara o evento de quebra de novo, então eles precisam de marca). Só dentro das minas e só com picareta. (Com 8% no nível 50 seriam −16%.)
  - **Pescador** (8 XP por pesca; ~161 h de pesca até o nível 50 com Isca III): **pesca AFK vale**. No nível 50, em média **1 mapa do tesouro por hora** (Comum 45% · Raro 30% · Arcano 18% · Lendário 7%) e **1 Fragmento Arcano por hora**, com a vara de referência (400 pescas/h, Isca III: 1 em 400 pescas pra cada). Abaixo do 50, a taxa cai junto com o nível (nível/50) e os pesos puxam pro Comum: 1–9: 85/15/0/0 · 10–19: 75/20/5/0 · 20–29: 65/24/9/2 · 30–39: 55/28/13/4 · 40–49: 50/29/16/5 · 50: 45/30/18/7. Tabela completa: `simular.py --pescador`. No nível 50 o mapa vale em média R$ 24.250 em coins (só entram quando alguém faz a câmara). Além disso, livro e armadura encantados e peixes raros pra vender.
  - **Agricultor** (XP por colher planta madura à mão): **+20% de venda no Fazendeiro** no nível 50 (0,4% por nível) e **chance de colheita dupla** de 15% no 50 (0,3% por nível), só na colheita à mão. Não mexe na meta: um terreno cheio de trigo rende ~R$ 560/h (o preço cai até o mínimo com o volume), ~R$ 750/h com o bônus, contra R$ 3.400/h da Pedreira. Toda plantação é colocada por jogador, então a regra contra farm é outra: só planta madura quebrada à mão dá XP e colheita dupla; farm automática não. O bônus fica abaixo da trava da venda.
  - **Lenhador**: **derruba a árvore inteira só no mundo de recursos, em árvore natural** (troncos ligados a folhas naturais), **até 64 troncos**, com 20% de chance no nível 50 e machado. **Na mina do bosque, só a tora a mais** (lá é tudo tronco sem folha: derrubar "a árvore" levaria a mina inteira). Tora a mais com 20% no nível 50 muda −0,2% na meta (o bosque só é a melhor mina por um rank).
- [ ] **Skills de profissão, segunda leva**: **Caçador** (drop a mais e cabeça de mob, fora das pilhas de spawner), **Escavador** (achados na terra, areia e cascalho), **Alquimista** (poção mais longa e extra no suporte) e **Ferreiro** (desconto no Ferreiro, na Bruxa e nas coins do Cambista).

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
- [x] **O Banco no fim da Avenida das Minas** (2026-10-27): o Bancário saiu do spawn (o `spring-banco` devolve aquele pedaço) e ganhou um banco clássico entre o Compactador e o Cambista, no lugar da cerejeira do fundo: quartzo, ouro, colunas, frontão com medalhão de esmeralda e o cofre.
- [x] **Skins dos NPCs** (2026-10-27): os de dicas ganharam skin temática (eram todos padrão), e o Bancário, o Cambista e o Compactador, as próprias. Corrigido: o perfil da textura ia sem UUID e nome (os NPCs das oficinas e da loja podiam aparecer com a skin padrão), e o config antigo do servidor sem os tipos novos deixava o Bancário com o nome "bancario" e sem skin. Nomes sem emoji fora do Unicode básico (🏦, 📖, 🪓...), que o Minecraft não desenha.
- [x] **Guias das minas removidos** (2026-10-27): o `/mina` e o holograma já mostram tudo.
- [x] **Log** de cheques emitidos e resgatados.
- [x] **Limite de valor** por cheque, ou cooldown. Valor máximo e cooldown no config; `cheque.limite.ignorar` pula os dois. O ID resgatado não resgata de novo (segura cópia por dupe).

### 👑 VipPlugin
- [ ] **Integração com o site via RCON**: o plugin já está pronto, falta construir o site. 🔴 (fora do plugin)
- [x] ~~**Preço mínimo e máximo** no `/vip vender`.~~ Saiu em 2026-10-31: o preço da revenda é livre (economia entre jogadores, 100% orgânica).
- [x] **Um warp diferente para cada tier.** `/vip setwarp <tier>`; o tier sem área própria vai pra área geral.
- [x] **Saque fora do kit mensal do Ouro e do Diamante** (2026-10-27): o Cambista vende até Saque VI. Os kits ainda vão ser revistos.
- [x] **Códigos de evento** (2026-10-27): `/vip gerarevento <tier> <3|7|15>` gera VIP de 3, 7 ou 15 dias só pra premiação de eventos. Não é vendido nem revendido, e o VIP que dá tem só os benefícios passivos (desconto, venda, XP, montaria...), e o kit diário, sem bônus de ativação (coins e XP) e sem o kit mensal. Quem já tem VIP resgata depois; comprar o mesmo tier por cima soma os dias. O campeão do boss recebe esse tipo.
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
- [x] **A limpeza leva todos os itens do chão** (2026-10-28). Antes, qualquer jogador a menos de 8 blocos segurava os itens (depois do Gladiador, quem assistia deixou os itens dos eliminados na arena). Agora não tem proteção nenhuma pros itens: o que tem dono vai pros achados e perdidos. Só os orbes de XP perto de alguém ficam.

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
- **Mapas do tesouro** (2026-10-29, `tesouros-plugin`): o boss sempre deixa um mapa e as minas dão um de vez em quando, nas raridades comum, raro, arcano e lendário. Cavar no X do mundo `tesouros` abre uma câmara de guardiões em ondas, com baú trancado até o último cair. O clã e os convidados ajudam, mas o tesouro é só do dono do mapa. Quem morre perde tudo. Novo NPC Caçador de Tesouros no portão do boss. Coins fixas por raridade (5, 20, 50 e 100 mil) e itens sorteados sem repetir, com chance de spawner, máquina e Células de Combustível. A máquina passou a exigir o rank dela (ou prestígio) pra ser colocada e usada; vender, pode.
- **Nether e End de recursos** (2026-10-29, `recursos-plugin` 1.1): `/nether` e `/end` (o Nether e o End padrão ficam desligados, sem portais), resets mensais escalonados (End no dia 10, Nether no 20, recursos no 30), `/mapa` de cada mundo (com o terreno explorado, apagado no reset), centro do End protegido num raio de 100 com o quadrado verde sem PvP, gaiolas dos cristais removidas, dragão renascendo todo dia às 21h e ovo só de enfeite. **Correção:** o reset não acontecia mais com o Multiverse 5.8 (o `/mv confirm` não chegava); agora o regen roda pelo console com o `confirm-mode: disable_console`.
- **Warps e `/luz`** (2026-10-29): os mundos passam a ser `/warp recursos`, `/warp nether`, `/warp end` e `/warp tesouros` (saem o `/nether`, o `/end`, o `/tesouro ir` e o teleporte do `/recursos`, que agora só mostra as datas dos resets). Templo da chegada no mundo dos tesouros, com hologramas das 4 direções; holograma do PvP no quadrado verde do End; `/luz` (visão noturna) pra todos. **Correção:** o resgate de quem saiu num mundo resetado agora espera o login no AuthMe.
- **Tesouro paga em cheque** (2026-10-29): o dinheiro do baú vem num cheque emitido pelo servidor (`ChequePlugin#chequeDoServidor`), que só vira dinheiro quando é depositado no Bancário. A chegada do `/warp tesouros` ficou no meio do bloco central do templo.
