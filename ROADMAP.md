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
| `spawn` (vazio) | Spawn, warps, placas do rankup e do ranking, área VIP, mercado das Barracas, loja e as 12 minas | Não |
| `terrenos` (grade) | Terrenos comprados com coins, com imposto semanal. Servem também para fazendas | Não |
| `recursos` (normal) | Mobs naturais, pesca, árvores e PvP do Bounty e dos clãs. Resetado de tempos em tempos | Sim |
| `arenas` (vazio) | Arena do boss, arenas de X1 e arena do Gladiador. Só se entra por comando ou warp, com volta ao spawn no fim | Sim |

- **Visual do spawn:** primeiro, tentar um mapa pronto de spawn medieval (mundo pronto ou `.schem` da 1.13 ou mais nova, com licença para servidor público). Se não ficar bom, plano B: montar a parte funcional (plataformas, caminhos, minas, mercado) e decorar depois.
- **Terrenos:** plugin nosso, e não o PlotSquared, para integrar com a economia (imposto, Pix e painel).
- **Mundo de recursos:** as missões de matar mobs, pescar, colher e cortar árvores precisam dele, porque mob de spawner não conta e o rankup protege o mundo fora das minas.
- **Arenas:** o boss já tem o `/boss setlocal`. O X1 é o Duelo com aposta (`duelo-plugin`), e o Gladiador, o `gladiador-plugin`. No servidor de teste, `tools/bots/montar-arenas.js` monta arenas simples de pedra, até o mapa chegar.
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
- [ ] Missão de vender nas barracas (entre jogadores). 🟢

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
- [ ] Espectadores (`/duelo assistir`). 🟢
- [ ] Os abates em duelo não contarem para o placar de abates do clã nem para o Bounty, para ninguém combinar duelo com um amigo para farmar. 🟢

### Mercado de Barracas ✅ v1 (`economy/barracas-plugin`)
Comércio entre jogadores em barracas alugadas no spawn, com lojas por placa em baús. A v1 está implementada (veja o README do plugin), com estas decisões:
- A área é definida por **dois pontos** (`/barraca pos1`, `/barraca pos2`).
- O dono **só coloca baús e placas**. A construção da staff fica intacta.
- O aluguel dura **6h**.
- **Barracas normais**: por ordem de chegada, com renovação.
- **Barracas especiais** (maiores, perto do spawn): vão a **leilão** quando ficam livres, com lance mínimo igual ao preço das normais. Não renovam.
- A caixa de retirada guarda os itens por **7 dias**.

**Próximos passos**
- [x] Placa `[Comprar]`: o dono paga quem traz o item.
- [x] Placa na entrada, com o nome do dono ou "Aluga-se" quando estiver livre (`/barraca placa <id>`).
- [x] Preço de aluguel por barraca (`/barraca preco <id> <valor>`).
- [x] Aluguel pago automaticamente: `/barraca autorenovar`, do próprio plugin. Migrar pro `/pix agendar` quando ele existir. 🟢

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
- [ ] Prestígio: ao chegar no Rei, voltar pro Camponês com um bônus permanente (venda mais cara, mina exclusiva). 🟡
- [ ] Rank na tab, junto com o clã e o VIP. 🟢
- [ ] Encantamentos de picareta comprados com coins ou com uma moeda própria (explosão, mais fortuna, venda automática). 🔴
- [x] Coleta direta nas minas pra todos: drops e XP vão pro jogador, sem itens no chão.
- [x] Venda automática ao minerar como benefício de VIP (`/autovenda`, Prata pra cima).
- [ ] Menu (GUI) no `/ranks` e no `/mina`. 🟢
- [ ] Missão de subir de rank e missões por mina. 🟢
- [ ] Mina privada do clã ou do VIP. 🟡
- [ ] Holograma ou placa com o tempo até o próximo reset da mina. 🟢
- [x] Fortuna nas minas: **vale** (decisão de 2026-10-22). A meta de 150 h é pra quem não é VIP e tem Fortuna III, da mesa de encantamento. Os VIPs Ouro e Diamante ganham Fortuna IV e V no kit mensal (`/vip kitmensal`) e chegam em cerca de 112 h e 107 h.

**Balanceamento (2026-10-22).** Meta: um jogador ativo chega ao Rei em **150 h**. A simulação (`tools/economia/simular.py`) mostrou que a sugestão inicial de minas não progredia: a de ferro rendia menos que a de cobre, a de redstone menos que a de ouro, e as de ametista e do Nether davam itens que a loja não comprava. As minas foram recalibradas pra cada uma render cerca de 1,6× a anterior, a loja do servidor de teste ganhou `NETHERRACK`, `GOLD_NUGGET` e `AMETHYST_BLOCK`, e os preços dos ranks foram recalculados (Fazendeiro: de R$ 5.000 pra R$ 3.400; Rei: de R$ 25 mi pra R$ 32 mi), com cada rank levando cerca de 1,5× o tempo do anterior. Tudo isso com Fortuna III como referência.

### Terrenos ✅ v1 (`terrenos-plugin`)
Terrenos de 32x32 num mundo em grade gerado pelo plugin, comprados por R$ 50.000. Decisões de 2026-10-23:
- 1 terreno por jogador; VIP Prata e Ouro, 2; Diamante, 3.
- Compra com preço fixo e **imposto semanal**, em vez de aluguel: 3% do preço do rank atual do dono, no mínimo R$ 1.000, com pagamento automático.
- Com 7 dias de atraso, o terreno passa para o servidor e vai a **leilão de imóvel** com tudo o que tem dentro. O lance vencedor some da economia. O dono antigo só dá lance depois de pagar a dívida.

**Próximos passos**
- [ ] Placa ou holograma na entrada do terreno, com o dono ou "À venda". 🟢
- [ ] Juntar terrenos vizinhos do mesmo dono. 🟡
- [ ] Permissões separadas para os amigos (só abrir baú, só construir). 🟢
- [ ] Missão de pagar o imposto ou de construir no terreno. 🟢

### Gladiador ✅ v1 (`gladiador-plugin`)
Evento de PvP na arena do mundo `arenas`, aberto pela staff ou pela agenda: inscrição paga, contagem regressiva, todos entram na arena e vence o último de pé.
- Dois modos, escolhidos ao abrir o evento: **individual** (todos contra todos) e **por clã** (vence o último clã com alguém vivo; usa o ClasPlugin, sem dano entre membros do mesmo clã).
- Cada um luta com o próprio equipamento, e quem morre perde os itens (decisão de 2026-10-23).
- Pote: inscrições (R$ 2.000 cada) mais R$ 10.000 do servidor. No modo por clã, é dividido entre quem lutou pelo clã vencedor.
- Quem desconecta ou sai da arena é eliminado.
- Ranking de vitórias de jogadores e de clãs.

**Próximos passos**
- [ ] Anúncio no Discord, quando a ponte existir. 🟢
- [ ] Borda que fecha com o tempo, para a luta não se arrastar. 🟡
- [ ] Prêmio do modo por clã direto no banco do clã, como opção. 🟢

### Ponte com o Discord 🟢
Webhook com anúncios do servidor:
- Spawn e ranking do boss.
- VIP resgatado e código colocado à venda.
- Transação suspeita no Pix (ver alerta em Pix, mais abaixo).
- Barraca alugada ou liberada.

### Recompensa diária de login 🟢
- Sequência de dias seguidos com recompensa crescente (por exemplo, dia 7 vale mais).
- Faltou um dia, a sequência zera (ou diminui, configurável).

---

## 🔧 Features nos plugins atuais

### 🐉 BossPlugin
- [ ] **Ranking de dano com prêmio**: o top 3 recebe coins proporcionais ao dano, e não só o último hit. 🟢
- [ ] **XP de skill por participar**, conforme o dano causado. 🟢
- [ ] **`/boss proximo`**: mostra quando é o próximo spawn da agenda. 🟢
- [ ] **Código de VIP pro campeão**: entrega opcional de um código de VIP de curta duração. Funciona como vitrine do VIP. 🟢
- [ ] **Boss "elite"**: aparece raramente, com o dobro de vida e de dano e loot melhor. 🟡
- [ ] **Recompensa em dinheiro** pro campeão, além do item. 🟢

### ⚔️ SkillsPlugin
- [ ] **`/skills top <skill>`**: ranking global por skill. 🟢
- [ ] **Bônus sazonal**: XP em dobro configurável (por exemplo, no fim de semana), com aviso no chat. 🟢
- [ ] **Efeito visual no level up**, com partícula própria de cada skill. 🟢
- [ ] **Prestígio**: no nível 50, resetar a skill em troca de um bônus permanente. 🟡
- [ ] **Scoreboard lateral** com o XP da skill em uso. 🟡
- [ ] **Escudo Perfeito (parry)**: bloquear no tempo exato anula o dano e atordoa o atacante. 🟡
- [ ] **Corrigir**: morrer só por Sangramento não credita quem aplicou o efeito. 🟡

### 🛒 EconomyShopPlugin
- [ ] **Eventos de mercado**: um item sorteado tem o preço multiplicado por algumas horas ("alta do diamante"), com anúncio no chat. 🟢
- [ ] **Venda em massa**: `/sell all` e `/sell mao`. 🟢
- [ ] **Categorias fixas**: uma lista definida no config em vez de texto livre, pra evitar "Minerio" e "Minerios" separados. 🟢

### 💸 PixPlugin
- [ ] **Alerta de transação suspeita**: um envio grande perto do limite, ou uma conta nova recebendo de várias contas, gera aviso pra staff. Aproveita o `LimiteManager`. 🟡
- [ ] **`/pix chave <apelido>`**: um apelido para receber pagamentos. 🟢
- [ ] **`/pix agendar`**: pagamentos recorrentes. Combina com Terrenos e com o aluguel das barracas. 🟡
- [ ] **Som e título na tela** ao receber um pagamento ou uma cobrança. 🟢
- [ ] **`/pix admin log <jogador>`**: a staff investiga as transações de alguém. 🟢
- [ ] **Taxa no histórico**: o extrato passa a mostrar o valor líquido e a taxa de cada transação. 🟢

### 🧾 ChequePlugin
- [ ] **Cheque nominal**: só a pessoa indicada pode resgatar. 🟢
- [ ] **Cancelar ou estornar** um cheque emitido por engano. 🟡
- [ ] **Log** de cheques emitidos e resgatados. 🟢
- [ ] **Limite de valor** por cheque, ou cooldown. 🟢

### 👑 VipPlugin
- [ ] **Integração com o site via RCON**: o plugin já está pronto, falta construir o site. 🔴 (fora do plugin)
- [ ] **Preço mínimo e máximo** no `/vip vender`. 🟢
- [ ] **Um warp diferente para cada tier.** 🟢
- [ ] **Ler a tag do prefixo do LuckPerms**, pra configurar num lugar só. 🟡
- [ ] **Avisar o vendedor** quando o código à venda dele vence. 🟢
- [x] **Kit mensal** (`/vip kitmensal`): armadura, armas e ferramentas, de diamante no Bronze e de netherite nos outros, com encantamentos acima do máximo normal (nível IV no Bronze até VII no Diamante; Fortuna IV e V no Ouro e no Diamante).
- [x] **Bônus de ativação:** dinheiro proporcional ao próximo rank (10% a 50%) e XP em todas as skills, os dois proporcionais aos dias do VIP.
- [x] **Perks em outros plugins, por permissão:** XP a mais nas skills, desconto na taxa do Pix, missão diária extra e venda automática.

### 🧹 ItemCleaner
- [ ] **Achados e perdidos**: os itens limpos vão pra uma caixa que dá pra recuperar pagando uma taxa. Também tira dinheiro de circulação e evita reclamação de quem perdeu item. 🟡
- [ ] **Limpar orbes de XP** também. 🟢
- [ ] **Aviso da limpeza manual configurável** (hoje é fixo em 10s). 🟢
- [ ] **Não limpar itens perto de jogadores.** 🟢

---

## 🔗 Integrações entre plugins
- O Boss paga em coins o ranking de dano.
- ~~Missões dão XP de skill.~~ ✅
- ~~Bounty, Barracas, Duelo e Gladiador aparecem no `/pix historico`.~~ ✅
- ~~Missões de vender na loja e de dano no boss.~~ ✅
- ~~Clãs ganham pontos no boss e mostram a tag na tab junto com o VIP.~~ ✅
- ~~O rank dá bônus no `/sell` da loja.~~ ✅
- O Discord recebe eventos do Boss, do VIP, do Pix e das Barracas (o Bounty já manda os dele).
- O aluguel de barraca e de terreno pode ser pago pelo `/pix agendar`.

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
