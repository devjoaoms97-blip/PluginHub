# Roadmap do PluginHub

Tudo o que está planejado para os plugins, num só lugar. Os READMEs de cada plugin continuam descrevendo **o que já existe**; este arquivo descreve **o que vem**.

**Esforço:** 🟢 baixo · 🟡 médio · 🔴 alto

## Diagnóstico (por que essas features)

1. **A economia tem mais saída que entrada de dinheiro.** O dinheiro sai pela taxa do Pix, pelo imposto da loja e pela taxa da revenda de VIP. Entra só pela venda de itens pra loja e pelo level up de skill. Faltam fontes de renda regulares que não dependam de grind.
2. **O PvP não tem objetivo.** As skills deixam o jogador mais forte, mas não há aposta, recompensa nem ranking.
3. **Falta conteúdo social.** Não existe clã, comércio entre jogadores nem terreno.

## Ordem sugerida

| # | Item | Esforço | Motivo |
|---|---|---|---|
| 1 | Painel da economia | 🟢 | Dá os números pra calibrar taxas, limites e recompensas de tudo o que vem depois |
| 2 | Missões diárias | 🟡 | Coloca dinheiro novo na economia e dá algo pra fazer todo dia |
| 3 | Recompensa por cabeça (Bounty) | 🟢 | Dá objetivo pro PvP usando o que já existe |
| 4 | ~~Mercado de Barracas~~ ✅ v1 | 🟡/🔴 | Comércio entre jogadores com aluguel, que também tira dinheiro de circulação |
| 5 | Clãs ou Terrenos | 🔴 | Projetos grandes. Escolher conforme o perfil do servidor |

O resto não tem ordem definida e pode ser puxado quando fizer sentido.

---

## 🆕 Plugins novos

### Painel da economia 🟢
Comando de staff (`/eco stats`) pra acompanhar a saúde da economia.
- Total em circulação (soma dos saldos no Vault).
- Entrada e saída por dia, separadas por origem: loja, Pix, revenda de VIP, missões etc.
- Maiores fortunas (top N saldos).
- Histórico diário salvo em disco, pra comparar semanas.

### Missões diárias 🟡
Três missões sorteadas por dia pra cada jogador, com recompensa em coins e XP de skill.
- Tipos: minerar X blocos, matar Y mobs, vender Z itens na loja, pescar, causar dano no boss etc.
- Pool de missões e recompensas configurável. Reinicia à meia-noite.
- Integra com o SkillsPlugin (XP de skill como recompensa) e com a Loja (missão de venda).

### Recompensa por cabeça (Bounty) 🟢
`/bounty <jogador> <valor>`: o valor sai da conta de quem oferece e fica retido até alguém matar o alvo. Quem mata leva.
- Recompensas pelo mesmo alvo se somam. `/bounty lista` mostra as maiores.
- Taxa configurável (tira dinheiro de circulação) e valor mínimo.
- Proteção contra farm: matar a própria conta alternativa ou um aliado combinado. Por exemplo, cooldown por par de jogadores ou não pagar se os dois tiverem o mesmo IP.
- Aparece no `/pix historico`, do mesmo jeito que a loja já aparece.

### Duelo com aposta 🟡
`/duelo <jogador> <valor>`: os dois apostam, o valor fica retido e a luta é numa arena 1v1. O vencedor leva tudo, menos uma taxa.
- Arena configurável (`/duelo setarena`), com inventário próprio ou com o do jogador (configurável).
- Desconectar no meio da luta conta como derrota.
- Ranking de vitórias.

### Mercado de Barracas ✅ v1 (`economy/barracas-plugin`)
Comércio entre jogadores em barracas alugadas no spawn, com lojas por placa em baús. A v1 está implementada (veja o README do plugin), com estas decisões:
- A área é definida por **dois pontos** (`/barraca pos1`, `/barraca pos2`).
- O dono **só coloca baús e placas**. A construção da staff fica intacta.
- O aluguel dura **6h**.
- **Barracas normais**: por ordem de chegada, com renovação.
- **Barracas especiais** (maiores, perto do spawn): vão a **leilão** quando ficam livres, com lance mínimo igual ao preço das normais. Não renovam.
- A caixa de retirada guarda os itens por **7 dias**.

**Próximos passos**
- [ ] Placa `[Comprar]`: o dono paga quem traz o item. 🟡
- [ ] Placa ou holograma na entrada, com o nome do dono ou "Aluga-se" quando estiver livre. 🟢
- [ ] Preço de aluguel por barraca, em vez de um preço único. 🟢
- [ ] Aluguel pago automaticamente pelo `/pix agendar`, quando ele existir. 🟡

### Clãs 🔴
- Clã com nome, tag no chat e na tab, cargos (líder, oficial, membro) e convites.
- Banco do clã integrado ao Pix (depósito, saque com permissão por cargo e extrato).
- Fogo amigo desligado entre membros.
- Ranking de clãs (somando kills, boss derrotado e saldo).
- Integra com o Boss (clã do campeão) e com o Bounty (recompensa contra o clã inteiro).

### Terrenos (claims) 🔴
- Proteger uma área comprada com coins, com tamanho ligado ao valor pago.
- Permissões por jogador (construir, abrir baú, usar porta).
- Aluguel ou imposto recorrente sobre o terreno, que é uma das maiores formas de tirar dinheiro de circulação em servidor de economia.
- Destrava o `/pix agendar` (pagar aluguel de terreno automaticamente).

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

### 🧹 ItemCleaner
- [ ] **Achados e perdidos**: os itens limpos vão pra uma caixa que dá pra recuperar pagando uma taxa. Também tira dinheiro de circulação e evita reclamação de quem perdeu item. 🟡
- [ ] **Limpar orbes de XP** também. 🟢
- [ ] **Aviso da limpeza manual configurável** (hoje é fixo em 10s). 🟢
- [ ] **Não limpar itens perto de jogadores.** 🟢

---

## 🔗 Integrações entre plugins
- O Boss paga em coins o ranking de dano.
- Missões dão XP de skill.
- Bounty, Duelo e Barracas aparecem no `/pix historico`.
- O Discord recebe eventos do Boss, do VIP, do Pix e das Barracas.
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
