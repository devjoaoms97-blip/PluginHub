# RankupPlugin

O coração de um servidor **rankup**: o jogador minera nas **minas**, vende o minério na loja (`/sell`) e paga coins pra **subir de rank**. Cada rank libera minas melhores e um bônus na venda.

Os ranks são medievais: **Camponês → Fazendeiro → Lenhador → Ferreiro → Mercador → Escudeiro → Clérigo → Cavaleiro → Barão → Conde → Duque → Rei**. Nomes, cores, preços e bônus ficam no `config.yml`.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/rankup` | todos | Paga e sobe pro próximo rank |
| `/rankup max` | todos | Sobe quantos ranks o saldo pagar |
| `/ranks` | todos | Abre o menu dos ranks: preço, bônus de venda, minas, o seu rank e o próximo (clicar nele faz o `/rankup`). `/ranks lista` mostra no chat |
| `/ranks <jogador>` | todos | Rank de outro jogador |
| `/mina` | todos | Abre o menu das minas: rank, blocos, tempo até o reset e quanto já foi minerado (clicar teleporta). `/mina lista` mostra no chat |
| `/mina <id>` | todos | Teleporta pra mina, se você tiver o rank |
| `/prestigio [confirmar]` | todos | No último rank, volta pro primeiro com um nível de prestígio |
| `/ranks admin set <jogador> <rank>` | staff | Muda o rank de alguém (roda os `comandos-ao-subir`) |
| `/ranks admin reload` | staff | Recarrega o config (ranks e preços) |
| `/mina admin pos1` / `pos2` | staff | Marca os cantos da mina (o bloco pra onde você olha) |
| `/mina admin criar <id> <rank>` | staff | Cria a mina na área marcada, a partir desse rank |
| `/mina admin bloco <id> <bloco> <peso>` | staff | Põe um bloco na composição (peso 0 tira) |
| `/mina admin spawn <id>` | staff | Marca onde você está como o ponto de teleporte da mina |
| `/mina admin rank <id> <rank>` | staff | Muda o rank mínimo |
| `/mina admin intervalo <id> <minutos>` | staff | Reset por tempo (0 = só por %) |
| `/mina admin percentual <id> <0-100>` | staff | Reset quando essa % for minerada (0 = só por tempo) |
| `/mina admin resetar <id>` | staff | Reseta agora |
| `/mina admin info <id>` | staff | Área, composição, próximo reset e quanto já foi minerado |
| `/mina admin deletar <id>` | staff | Apaga a mina (os blocos ficam) |

## Como funciona

**Ranks.** Todo jogador começa no primeiro rank. O `/rankup` cobra o preço do próximo e sobe. Quem sobe de rank é anunciado no chat. O pagamento aparece no extrato do Bancário e no `/economia tipos RankupPlugin` como "Rankup".

**Bônus de venda.** Cada rank tem um `multiplicador-venda`: com 1.25, o `/sell` (e a venda pelo menu da loja) paga 25% a mais. O bônus entra antes do imposto da loja, e a mensagem de venda mostra quanto foi.

**Minas.** Uma mina é um cubo marcado com dois cantos. A composição é uma lista de blocos com peso: `stone 70` e `coal_ore 30` dão 70% de pedra e 30% de carvão. Os pesos não precisam somar 100. Ela é preenchida de novo:
- a cada X minutos (padrão 15), com aviso pra quem está dentro aos 30s e aos 10s;
- quando X% dela foi minerada (padrão 80%);
- quando o servidor liga;
- com `/mina admin resetar`.

No reset, quem está dentro (ou em cima) vai pro ponto de teleporte da mina, e ninguém minera até acabar. O reset troca os blocos aos poucos (`blocos-por-tick`), então uma mina grande não trava o servidor.

**Regras das minas.**
- Só minera quem tem o rank mínimo. Quem não tem pode entrar, mas não quebra nada.
- Ninguém coloca bloco dentro da mina.
- Explosão não abre buraco na mina.
- A staff (`rankup.admin`) ignora essas regras.

**Proteção do mundo.** Em `protecao.mundos`, liste os mundos em que, **fora das minas**, ninguém quebra nem coloca blocos. É o normal num mundo de rankup, em que só as minas são mineráveis.

**Venda automática (pra todos).** Dentro de uma mina, o que você quebra é vendido na hora pela loja (preço dinâmico, bônus do rank e do VIP, imposto), e o dinheiro aparece na barra acima do inventário. O que a loja não compra vai direto pro inventário, e a experiência vai direto pra você (com o Remendo consertando a ferramenta, como o orbe faria). Não sobram itens nem orbes no chão, o que também reduz o lag. Só cai no chão o que não couber no inventário. No painel, aparece como "Venda automática (minas)".

**Chat.** O rank aparece antes do nome e convive com as tags do clã e do VIP: `[Fazendeiro] [ABC] [VIP Ouro] Nome: mensagem`.

**Prestígio.** No último rank (Rei), o `/prestigio confirmar` volta o jogador pro primeiro rank e dá um nível de prestígio, até 10. Cada nível soma **+10% de venda na loja** pra sempre, multiplicando o bônus do rank, e aparece como **✦N** antes do rank no chat e na tab. O dinheiro fica com o jogador. Os níveis ficam em `prestigio.yml`.

**Rank na tab.** O rank aparece antes do nome na tab. Com o VipPlugin, a ordem é rank, clã e VIP, como no chat. O VIP monta o nome, e o rankup pede pra refazer quando o rank muda.

**Fragmento Arcano.** Cada bloco quebrado numa mina tem `fragmentos.chance-por-bloco` (1 em 4.000) de dar um Fragmento Arcano, que vai direto pro inventário, com mensagem e som. Dá cerca de 1 por hora de mina. É a moeda do **Cambista** (OficinasPlugin), que troca fragmentos por Eficiência e Fortuna acima do máximo. O item é papel com o modelo `servidor:fragmento_arcano` do resource pack e a marca `rankupplugin:fragmento`: renomear outro papel não falsifica, e a loja não compra.

**Holograma das minas.** Em cima do ponto de teleporte de cada mina, um texto mostra o nome, o rank, o tempo até o reset e quanto já foi minerado, atualizado a cada segundo. Ele não fica salvo no mundo: some ao desligar e volta sozinho.

## Montando as minas (passo a passo)

1. Construa (ou escolha) o buraco da mina e olhe pra um canto: `/mina admin pos1`. Olhe pro canto oposto, no outro extremo da altura: `/mina admin pos2`.
2. `/mina admin criar carvao campones`
3. Defina os blocos:
   ```
   /mina admin bloco carvao stone 70
   /mina admin bloco carvao coal_ore 30
   ```
   (a mina nasce com `stone 100`; pra tirar a pedra, `/mina admin bloco carvao stone 0`)
4. Fique num lugar fora da mina, perto da entrada: `/mina admin spawn carvao`.
5. `/mina admin resetar carvao` pra preencher.
6. Cadastre na loja os itens que saem da mina (`/shop admin add`), senão o `/sell` não compra.

**Minas por rank.** As composições com peso estão em [`tools/economia/minas-planejadas.yml`](../tools/economia/minas-planejadas.yml). Elas foram calibradas pra cada mina render cerca de 1,6× a anterior com uma picareta de Fortuna III, então cada rank novo vale a pena:

| Rank | Mina | Blocos (peso) |
|---|---|---|
| Camponês | Pedreira | stone 80, coal_ore 20 |
| Fazendeiro | Cobre | stone 81, coal_ore 15, copper_ore 4 |
| Lenhador | Bosque | oak_log 50, spruce_log 50 |
| Ferreiro | Ferro | stone 55, iron_ore 30, coal_ore 15 |
| Mercador | Ouro | stone 63, gold_ore 12, iron_ore 25 |
| Escudeiro | Redstone | deepslate 30, deepslate_redstone_ore 25, deepslate_lapis_ore 45 |
| Clérigo | Ametista | amethyst_block 60, deepslate_lapis_ore 40 |
| Cavaleiro | Diamante | deepslate 62, deepslate_diamond_ore 38 |
| Barão | Esmeralda | deepslate 60, deepslate_emerald_ore 18, deepslate_diamond_ore 22 |
| Conde | Nether | netherrack 26, nether_quartz_ore 30, nether_gold_ore 20, ancient_debris 24 |
| Duque | Blocos | iron_block 37, gold_block 55, diamond_block 8 |
| Rei | Tesouro | iron_block 62, diamond_block 25, emerald_block 8, ancient_debris 5 |

Tudo o que essas minas dropam precisa estar na loja, inclusive `NETHERRACK`, `GOLD_NUGGET` e `AMETHYST_BLOCK`. O `itens.yml` do servidor de teste já tem tudo.

## Configuração

```yaml
ranks:
  - id: campones          # interno; não troque depois de ter jogadores nele
    nome: "Camponês"
    cor: "&7"
    preco: 0              # o do primeiro rank é ignorado
    multiplicador-venda: 1.0
  - id: fazendeiro
    nome: "Fazendeiro"
    cor: "&a"
    preco: 3400
    multiplicador-venda: 1.05
  # ... até o Rei

comandos-ao-subir: []     # ex: "lp user %jogador% parent add rank_%rank%"
anunciar-rankup: true

chat:
  tag: true
  formato: "&8[%rank%&8]"

minas:
  intervalo-minutos-padrao: 15
  reset-percentual-padrao: 80
  aviso-segundos: [30, 10]
  blocos-por-tick: 20000
  resetar-ao-ligar: true

protecao:
  mundos: []              # ex: [world]

integracao:
  pix: true
```

## Balanceamento

Os preços dos ranks foram calibrados pra um jogador ativo **sem VIP, com picareta de Fortuna III** (encantada na mesa), chegar ao **Rei em cerca de 150 h** de jogo. Os VIPs Ouro e Diamante ganham Fortuna IV e V no kit mensal e chegam em cerca de 112 h e 107 h. Sem Fortuna, são cerca de 200 h. A conta usa as minas acima, os preços da loja e uma velocidade de mineração estimada. O script que faz a conta está em `tools/economia/` (veja o README de lá). Rode de novo sempre que mudar um preço de rank, uma mina ou um preço da loja.

Com jogadores de verdade, o `rankups.csv` mostra quanto tempo cada rank está levando de fato, e o `/economia` mostra o dinheiro novo por hora jogada.

## Integrações

- **EconomyShopPlugin**: aplica o bônus de venda do rank no `/sell`, no `/sell all` e no menu. A loja chama `RankupPlugin#multiplicadorVenda(UUID)` por reflexão.
- **PixPlugin** (opcional): o pagamento do rankup aparece no extrato do Bancário como "Loja do Servidor".
- **PainelEconomiaPlugin**: o rankup aparece como o tipo "Rankup".
- **MissoesPlugin**: minerar na mina conta pra missão de minerar, como qualquer bloco natural. O bloco que o reset coloca não conta como "colocado pelo jogador".
- **LuckPerms** (opcional): use `comandos-ao-subir` pra dar um grupo por rank.

- **OficinasPlugin**: o guia de mina (fora do mapa desde 2026-10-27, mas o tipo continua) mostra `infoMina(String, UUID)` (rank, blocos, reset, % minerada e a chance de fragmento) e, pra quem `podeEntrarNaMina(String, Player)`, o botão que roda `/mina <id>`. O Cambista reconhece o Fragmento Arcano pela marca.

A API pra outros plugins, por reflexão: `multiplicadorVenda(UUID)`, `rankDe(UUID)`, `tagDoRank(UUID)`, `infoMina(String, UUID)`, `podeEntrarNaMina(String, Player)` e `fragmento(int)`.

## Persistência

- `plugins/RankupPlugin/jogadores.yml`: o rank de cada jogador, gravado a cada mudança.
- `plugins/RankupPlugin/minas.yml`: as minas, gravado a cada mudança.
- `plugins/RankupPlugin/rankups.csv`: uma linha por rankup pago, com data, jogador, de qual rank pra qual, preço e as **horas de jogo** do jogador naquele momento. É o que mostra quanto tempo cada rank leva de verdade, pra calibrar os preços (veja `tools/economia`). O `/ranks admin set` não entra.

## Requisitos

- Paper 26.2 (Java 25)
- Vault + plugin de economia
- EconomyShopPlugin, pra vender o que sai das minas

## Como compilar

```bash
cd rankup-plugin
mvn clean package
```

O `.jar` sai em `target/RankupPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **Não tem prestígio.** Quem chega no Rei não tem mais rank pra subir. O prestígio está no ROADMAP.
2. **O quanto foi minerado fica em memória.** Por isso as minas resetam quando o servidor liga (`resetar-ao-ligar`).
3. **O rank não aparece na tab**, só no chat. O VipPlugin monta o nome na tab, e o rank ainda não entrou nessa montagem.
4. **O `/mina <id>` teleporta na hora**, sem espera nem bloqueio em combate.
5. **Uma mina é sempre um cubo.** Formatos irregulares precisam de várias minas lado a lado.
