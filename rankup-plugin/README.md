# RankupPlugin

O coração de um servidor **rankup**: o jogador minera nas **minas**, vende o minério na loja (`/sell`) e paga coins pra **subir de rank**. Cada rank libera minas melhores e um bônus na venda.

Os ranks são medievais: **Camponês → Fazendeiro → Lenhador → Ferreiro → Mercador → Escudeiro → Clérigo → Cavaleiro → Barão → Conde → Duque → Rei**. Nomes, cores, preços e bônus ficam no `config.yml`.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/rankup` | todos | Paga e sobe pro próximo rank |
| `/rankup max` | todos | Sobe quantos ranks o saldo pagar |
| `/ranks` | todos | Lista os ranks: preço, bônus de venda e minas de cada um. Mostra onde você está (➤) |
| `/ranks <jogador>` | todos | Rank de outro jogador |
| `/mina` | todos | Lista as minas (✔ = você tem acesso) |
| `/mina <id>` | todos | Teleporta pra mina, se você tiver o rank |
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

**Ranks.** Todo jogador começa no primeiro rank. O `/rankup` cobra o preço do próximo e sobe. Quem sobe de rank é anunciado no chat. O pagamento aparece no `/pix historico` e no `/economia tipos RankupPlugin` como "Rankup".

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

**Chat.** O rank aparece antes do nome e convive com as tags do clã e do VIP: `[Fazendeiro] [ABC] [VIP Ouro] Nome: mensagem`.

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

**Sugestão de minas por rank:**

| Rank | Mina | Blocos |
|---|---|---|
| Camponês | Pedreira | pedra, carvão |
| Fazendeiro | Cobre | pedra, carvão, cobre |
| Lenhador | Bosque | troncos de carvalho e de pinheiro |
| Ferreiro | Ferro | pedra, ferro, carvão |
| Mercador | Ouro | pedra, ouro, ferro |
| Escudeiro | Redstone | deepslate, redstone, lápis |
| Clérigo | Ametista | ametista, lápis |
| Cavaleiro | Diamante | deepslate, diamante |
| Barão | Esmeralda | deepslate, esmeralda, diamante |
| Conde | Nether | netherrack, quartzo, ouro do Nether |
| Duque | Blocos | bloco de ferro, bloco de ouro |
| Rei | Tesouro | bloco de diamante, bloco de esmeralda, detrito ancestral |

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
    preco: 5000
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

Os preços são um ponto de partida. Calibre olhando o `/economia` depois de alguns dias: quanto dinheiro entra por hora com o `/sell` e quanto sai com o rankup.

## Integrações

- **EconomyShopPlugin**: aplica o bônus de venda do rank no `/sell`, no `/sell all` e no menu. A loja chama `RankupPlugin#multiplicadorVenda(UUID)` por reflexão.
- **PixPlugin** (opcional): o pagamento do rankup aparece no `/pix historico` como "Loja do Servidor".
- **PainelEconomiaPlugin**: o rankup aparece como o tipo "Rankup".
- **MissoesPlugin**: minerar na mina conta pra missão de minerar, como qualquer bloco natural. O bloco que o reset coloca não conta como "colocado pelo jogador".
- **LuckPerms** (opcional): use `comandos-ao-subir` pra dar um grupo por rank.

A API pra outros plugins, por reflexão: `multiplicadorVenda(UUID)`, `rankDe(UUID)` e `tagDoRank(UUID)`.

## Persistência

- `plugins/RankupPlugin/jogadores.yml`: o rank de cada jogador, gravado a cada mudança.
- `plugins/RankupPlugin/minas.yml`: as minas, gravado a cada mudança.

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
