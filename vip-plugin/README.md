# VipPlugin

Sistema de VIPs por tempo — Bronze, Prata, Ouro e Diamante — com renovação/expiração automática, grupos via LuckPerms, perks (voo, cura, fome, kit, partículas, warp exclusivo), desconto na loja, **resgate por código** (pra vender fora do Minecraft — Discord hoje, site depois) e **revenda de código entre jogadores por coins** (nunca de um VIP já ativo, só de um código ainda não resgatado).

## Tudo pelo `/vip`

Todos os comandos, de jogador e de staff, entram por `/vip <ação>` — não tem mais comando solto tipo `/vipfly`. Exemplos: `/vip fly`, `/vip heal`, `/vip particula flame`, `/vip check`.

| Comando | Quem usa | Ação |
|---|---|---|
| `/vip check` | todos | Mostra seu próprio VIP e quanto tempo falta |
| `/vip tiers` | todos | Lista os 4 tiers e um resumo dos benefícios |
| `/vip resgatar <codigo>` | todos | **Ativa um código de VIP** — comprado na lojinha, ou comprado de outro jogador — veja a seção abaixo |
| `/vip vender <preco> <codigo>` | todos | Revende um código de VIP que você ainda não resgatou, por coins |
| `/vip cancelarvenda` | todos | Cancela seu código à venda (ele volta a ser um código normal, gratuito pra quem souber) |
| `/vip fly` | quem tem VIP | Liga/desliga voo |
| `/vip heal` | quem tem VIP | Cura instantânea (cooldown por tier) |
| `/vip feed` | quem tem VIP | Restaura a fome (cooldown por tier) |
| `/vip kit` | quem tem VIP | Resgata o kit do seu tier (cooldown por tier) |
| `/vip kitmensal` | quem tem VIP | Resgata o equipamento encantado do seu tier (armadura, armas e ferramentas), uma vez a cada 30 dias |
| `/vip particula <tipo\|off>` | quem tem VIP | Escolhe o efeito de partícula que te segue |
| `/vip warp` | quem tem VIP | Teleporta pra área exclusiva de VIP |
| `/vip add <jogador> <tier> <dias>` | staff | Concede/renova VIP direto (sem passar por código) |
| `/vip remove <jogador>` | staff | Remove o VIP na hora |
| `/vip check <jogador>` | staff | Vê o VIP de outro jogador |
| `/vip list` | staff | Lista todos os VIPs ativos |
| `/vip gerarcodigo <tier> <dias> [usos] [validade-dias]` | staff | Gera um código pra lojinha entregar ao comprador (validade opcional, veja abaixo) |
| `/vip codigos` | staff | Lista códigos ativos ainda não resgatados |
| `/vip revogarcodigo <codigo>` | staff | Invalida um código antes de ser usado |
| `/vip reload` | staff | Recarrega o `config.yml` |

Os comandos de perk (`fly`, `heal`, `feed`, `kit`, `particula`, `warp`) exigem **VIP ativo E** a permissão `vip.perk.<nome>` — as duas coisas. Isso permite, por exemplo, dar `/vip fly` só a partir do Prata pra cima, mesmo com o Bronze já sendo VIP.

## Como a venda funciona: código de resgate

A ideia é a compra acontecer **fora do Minecraft** — hoje no Discord, no futuro num site — e o jogador ativar o VIP comprado sozinho, sem staff precisar saber o nick dele na hora da venda:

```
jogador compra no Discord/site
        │
        ▼
staff confirma o pagamento e roda:
   /vip gerarcodigo ouro 30
        │
        ▼
plugin responde com um código:  VIP-4K7XQ2MN
        │
        ▼
staff copia e manda o código pro comprador (DM no Discord, e-mail, etc.)
        │
        ▼
jogador entra no servidor e roda:
   /vip resgatar VIP-4K7XQ2MN
        │
        ▼
VIP Ouro por 30 dias ativado na conta dele
```

**Por que código, e não vincular a conta direto?** Na hora da compra (principalmente no Discord) você geralmente não sabe com certeza qual é o nick/UUID exato do comprador no Minecraft — e pedir pra ele digitar errado é um risco. Com código, quem resgata é quem fica com o VIP: zero chance de mandar VIP pra conta errada.

### Hoje (Discord): 100% manual, já funciona

Sem precisar de nenhuma integração nova — depois que confirmar o Pix/pagamento no Discord, a staff só roda `/vip gerarcodigo <tier> <dias>` no servidor (ou via console) e cola o código na DM do comprador.

### No futuro (site): mesma base, só automatiza quem aperta o botão

Quando o site da loja existir, ele não precisa de nenhuma API nova no plugin — a forma mais simples e mais usada por lojas de Minecraft é o próprio site rodar o mesmo comando via **RCON** (protocolo de comando remoto que praticamente todo servidor Minecraft já expõe) assim que o pagamento for aprovado:

```
rcon> vip gerarcodigo ouro 30
```

E aí devolve o código gerado pro comprador na tela de confirmação da compra (ou por e-mail). Isso significa que **o plugin já está pronto pro site sem precisar mexer em mais nada aqui** — o trabalho todo do "futuro site" fica do lado do site (processar pagamento + falar RCON), não do plugin.

Se um dia vocês quiserem os dois lados conversando sem depender de RCON (ex: o site consultar status de código, listar produtos dinamicamente, etc.), aí sim faria sentido expor uma API HTTP própria — mas isso é trabalho extra que só vale a pena se RCON não for suficiente. Recomendo começar com RCON e só migrar se sentir necessidade real.

### Detalhes do código
- 8 caracteres (`VIP-XXXXXXXX`), sem `0/O/1/I/L` pra evitar erro de leitura/digitação ao repassar pro comprador.
- Por padrão cada código vale **1 uso**; `/vip gerarcodigo ouro 30 5` gera um código reutilizável 5 vezes (ex: promoção "5 primeiros a resgatar ganham Ouro").
- `/vip resgatar` aceita o código com ou sem o prefixo `VIP-`.
- Códigos ficam salvos em `codigos.yml` (sobrevivem a reinício do servidor) até serem totalmente usados, revogados com `/vip revogarcodigo` ou **vencerem o prazo de resgate**.

### Validade do código (prazo pra resgatar)
Um código pode ter prazo pra ser resgatado. Se ninguém resgatar até lá, ele some, **mesmo que esteja à venda**:

```yaml
codigos:
  validade-padrao-dias: 0   # 0 = não expira (padrão)
```

- `/vip gerarcodigo ouro 30` usa o prazo padrão do `config.yml`.
- `/vip gerarcodigo ouro 30 1 7` dá 7 dias pra resgatar (o 5º argumento vale mais que o padrão). Com `0` nesse argumento, o código não expira.
- O prazo é só pra **resgatar**. Depois de resgatado, o VIP dura os `<dias>` normais.
- `/vip codigos` e a mensagem do `gerarcodigo` mostram até quando cada código vale.
- Códigos gerados antes dessa opção existir continuam **sem prazo**, então nenhum código já vendido vence de surpresa.
- Quando um código à venda vence, ele sai da revenda sem avisar o vendedor.

## Revenda entre jogadores: código de VIP por coins

**Importante: só dá pra revender um código que ainda não foi ativado.** Um VIP já em uso (rodando na conta de alguém) não pode ser colocado à venda — nunca tira o VIP de ninguém que já está aproveitando ele. O que pode ser revendido é um **código ainda não resgatado**, tipo o que a staff gera depois de uma compra no Discord (`/vip gerarcodigo`) — se o jogador que recebeu esse código preferir revendê-lo em vez de ativar pra si, ele pode. Isso dá um caminho pra quem não pode/quer gastar dinheiro real chegar no VIP se esforçando no jogo (minerando, fazendo missão, etc — o que já render coins no seu servidor) e comprando de alguém que topa vender.

```
/vip vender 500 VIP-XXXXXXXX
```
- Confere se o código `VIP-XXXXXXXX` existe, ainda não foi resgatado, e ainda não está à venda por ninguém.
- Anexa o preço (500 coins) e você como vendedor — o código continua sendo o **mesmo texto**, só passa a exigir pagamento de quem for resgatar (menos de você mesmo, que não pode comprar da própria venda).
- Mudou de ideia e ninguém comprou ainda? `/vip cancelarvenda` tira o preço do código, que volta a valer de graça pra quem souber ele (você inclusive).

```
/vip resgatar VIP-XXXXXXXX
```
- Se o código tiver preço, cobra do comprador via Vault **antes** de consumir o código (se não tiver saldo suficiente, nada é gasto — o código continua válido pra tentar de novo).
- O vendedor recebe o pagamento automaticamente, descontada a **taxa de revenda** (`revenda.taxa-percentual`, padrão 10% — funciona como sink, igual o imposto da loja e a taxa do Pix: sem isso, pegar um código grátis da staff e revender pro servidor inteiro viraria uma forma de imprimir coins do nada).
- Depois de resgatado, o código some do sistema — não tem como revender de novo, nem tem VIP "duplicado": alguém sempre acaba com o tempo, nunca os dois.

Isso usa exatamente a mesma peça de código de resgate da seção anterior — só muda quem anexa o preço (o próprio jogador que tem o código em mãos, não a staff). `revenda.ativado: false` no `config.yml` desliga essa feature inteira se não quiser esse tipo de comércio no seu servidor. Precisa de Vault instalado — sem ele, `/vip vender` avisa e não deixa continuar.

## Setup — depois de instalar

### 1. Copiar o `.jar` e deixar gerar o `config.yml`
Suba o servidor uma vez com o plugin instalado (e o LuckPerms já rodando) pra ele criar `plugins/VipPlugin/config.yml`. Ajuste os 4 tiers como quiser (nome, kit, cooldowns, desconto).

### 2. Criar os 4 grupos no LuckPerms e dar os perks certos

Os nomes dos grupos abaixo precisam bater com `grupo-luckperms` de cada tier no `config.yml` (já vêm como `vip_bronze`, `vip_prata`, `vip_ouro`, `vip_diamante` por padrão).

```
/lp creategroup vip_bronze
/lp group vip_bronze meta setprefix "&7[Bronze] "
/lp group vip_bronze permission set vip.perk.heal true
/lp group vip_bronze permission set vip.perk.feed true
/lp group vip_bronze permission set vip.perk.kit true
/lp group vip_bronze permission set vip.perk.particulas true
/lp group vip_bronze permission set vip.desconto.bronze true
/lp group vip_bronze permission set vip.xp.bronze true

/lp creategroup vip_prata
/lp group vip_prata meta setprefix "&f[&bPrata&f] "
/lp group vip_prata permission set vip.perk.heal true
/lp group vip_prata permission set vip.perk.feed true
/lp group vip_prata permission set vip.perk.kit true
/lp group vip_prata permission set vip.perk.particulas true
/lp group vip_prata permission set vip.perk.fly true
/lp group vip_prata permission set vip.desconto.prata true
/lp group vip_prata permission set vip.xp.prata true
/lp group vip_prata permission set vip.perk.autovenda true

/lp creategroup vip_ouro
/lp group vip_ouro meta setprefix "&6[Ouro] "
/lp group vip_ouro permission set vip.perk.heal true
/lp group vip_ouro permission set vip.perk.feed true
/lp group vip_ouro permission set vip.perk.kit true
/lp group vip_ouro permission set vip.perk.particulas true
/lp group vip_ouro permission set vip.perk.fly true
/lp group vip_ouro permission set vip.perk.warp true
/lp group vip_ouro permission set vip.desconto.ouro true
/lp group vip_ouro permission set vip.xp.ouro true
/lp group vip_ouro permission set vip.perk.autovenda true
/lp group vip_ouro permission set vip.missao.extra true

/lp creategroup vip_diamante
/lp group vip_diamante meta setprefix "&b[Diamante] "
/lp group vip_diamante permission set vip.perk.heal true
/lp group vip_diamante permission set vip.perk.feed true
/lp group vip_diamante permission set vip.perk.kit true
/lp group vip_diamante permission set vip.perk.particulas true
/lp group vip_diamante permission set vip.perk.fly true
/lp group vip_diamante permission set vip.perk.warp true
/lp group vip_diamante permission set vip.desconto.diamante true
/lp group vip_diamante permission set vip.xp.diamante true
/lp group vip_diamante permission set vip.perk.autovenda true
/lp group vip_diamante permission set vip.missao.extra true
```

Essa distribuição de perks por tier é só sugestão — dá pra dar `/vip fly` já no Bronze, por exemplo, é só adicionar a permissão no grupo. Essa mudança não precisa de reload nem de reiniciar nada, é instantânea via LuckPerms.

### 3. Definir o warp exclusivo
Vá até o local desejado e anote as coordenadas (`F3`), depois edite `warp-vip.mundo/x/y/z` no `config.yml` e rode `/vip reload`.

### 4. Testar o fluxo completo
```
/vip gerarcodigo diamante 30
/vip resgatar VIP-XXXXXXXX   (logado com outro jogador, ou o mesmo mesmo)
/vip check
/vip fly
/vip kit
```

## Benefícios por tier

| Benefício | Bronze | Prata | Ouro | Diamante |
|---|---|---|---|---|
| Kit mensal (`/vip kitmensal`) | Diamante, nível IV | Netherite, nível V | Netherite, nível VI | Netherite, nível VII |
| Fortuna da picareta | III | III | IV | V |
| Bônus de ativação: dinheiro | 10% do próximo rank | 20% | 35% | 50% |
| Bônus de ativação: XP em cada skill | 500 | 1.500 | 3.000 | 5.000 |
| XP a mais nas skills (enquanto ativo) | +10% | +20% | +30% | +50% |
| Desconto na taxa do Pix | 20% | 40% | 60% | 80% |
| Desconto na loja e no aluguel de barraca | 5% | 10% | 15% | 20% |
| Venda automática ao minerar (`/autovenda`) | — | ✔ | ✔ | ✔ |
| Missão diária extra | — | — | ✔ | ✔ |

Os benefícios que moram em outros plugins (XP, Pix, missões, venda automática, descontos) funcionam por permissão, dada pelos grupos do LuckPerms (veja "Setup"). Cada plugin tem a sua lista de permissões no próprio `config.yml`.

### Kit mensal

O `/vip kitmensal` entrega, uma vez a cada `cooldown-kit-mensal-dias` (30), um equipamento completo. O Bronze recebe itens de diamante, e os outros recebem netherite. O **nível** é o dos encantamentos de força, e passa do máximo normal do Minecraft: Proteção, Afiação, Saque, Força, e Densidade e Empalamento nos itens do Diamante. Os de utilidade ficam no máximo normal: Inquebrável III, Remendo (Ouro e Diamante), Infinidade, Lealdade III e Peso-pena IV.

| | Bronze | Prata | Ouro | Diamante |
|---|---|---|---|---|
| Picareta | Fortuna III, Eficiência III | Fortuna III, Eficiência IV | Fortuna IV, Eficiência V | Fortuna V, Eficiência V |
| Machado, armadura completa, espada, arco, escudo | ✔ | ✔ | ✔ | ✔ |
| Besta e lança (skill Lanceiro) | — | — | ✔ | ✔ |
| Maça (skill Marreteiro), tridente e elytra | — | — | — | ✔ |
| Maçãs douradas | 8 | 16 | 16 + 1 encantada | 32 + 3 encantadas |
| Outros | — | 16 pérolas | 16 pérolas, 2 totens | 16 pérolas, 4 totens, 64 foguetes |

A espada do Diamante também tem Aspecto Flamejante II, e o arco tem Chama.

**Sobre o equilíbrio:**
- **Proteção** acima de V não faz diferença. Quatro peças com Proteção V já dão o limite do Minecraft, que é 80% menos dano. Do Prata para cima, a armadura está no máximo.
- **Afiação VII** dá +4 de dano por golpe (a V dá +3).
- O VIP de tier alto fica bem mais forte em PvP, no bounty e no boss. Essa foi a escolha, para incentivar a compra.

Pela simulação do rankup (`tools/economia`), quem não é VIP e encanta Fortuna III na mesa chega ao Rei em cerca de 150 h. O Bronze leva o mesmo tempo, sem precisar juntar XP para encantar. O Prata leva cerca de 131 h, o Ouro 112 h e o Diamante 107 h, sem contar o bônus de ativação.

**Formato dos itens:** `MATERIAL:quantidade:nome=nivel,...`, com os nomes do Minecraft (`fortune`, `sharpness`, `protection`...). Vale para os dois kits. Exemplo:
```yaml
kit-mensal:
  - "NETHERITE_SWORD:1:sharpness=7,looting=7,unbreaking=3,mending=1"
cooldown-kit-mensal-dias: 30
```
Um item com nome ou encantamento errado é pulado e avisado no console. Um tier sem `kit-mensal` responde "Seu tier de VIP não tem kit mensal" e não gasta o cooldown.

### Bônus de ativação

Quando um VIP é ativado, com `/vip add` ou `/vip resgatar`, o jogador recebe:
- **Dinheiro:** uma porcentagem do preço do **próximo rank** dele no RankupPlugin. Quem está no Rei recebe a porcentagem do preço do próprio Rei. Por ser proporcional, o bônus pesa o mesmo no começo e no fim do jogo: o Diamante ganha sempre meio rank.
- **XP em todas as 13 skills.** Os níveis sobem normalmente, mas **sem o dinheiro por nível** das skills, e o chat mostra um resumo só ("Suas skills subiram 40 níveis no total"). Sem isso, um jogador novo subiria muitos níveis baratos de uma vez: nos testes, 10.000 XP no Diamante pagavam R$ 25 mil, quinze vezes o bônus em dinheiro. O bônus contínuo de XP do VIP não se soma a esse XP. Se o jogador estiver offline (num `/vip add`, por exemplo), recebe quando entrar.

Tudo é **proporcional aos dias**: um código de 7 dias dá 7/30 do bônus, e um de 60 dias dá o dobro. Assim, comprar vários códigos curtos não rende mais que um longo.

```yaml
bonus-ativacao:
  dinheiro-percentual-proximo-rank: 50
  xp-por-skill: 5000
```
Sem o RankupPlugin, o bônus em dinheiro é zero. Sem o SkillsPlugin, não há XP. No painel da economia, o dinheiro aparece como "Bônus de ativação do VIP".

## Integração com a loja (EconomyShopPlugin)

Se o `economy-shop-plugin` estiver instalado, `/buy` já aplica automaticamente o desconto do maior tier do jogador — não precisa configurar nada a mais além do passo 2 acima (a permissão `vip.desconto.<tier>` já cobre isso). Essa integração é **desacoplada**: o VipPlugin não sabe que o EconomyShopPlugin existe, e vice-versa — os dois só concordam no nome das permissões `vip.desconto.*`. Ver `economy-shop-plugin/README.md`, seção "Desconto de VIP na compra".

## Integração com os clãs (ClasPlugin)

Com o ClasPlugin instalado, a tab list mostra as duas tags: `[ABC] [VIP Ouro] Nome`. Quem monta o nome na tab é sempre o VipPlugin: ele pergunta ao ClasPlugin (por reflexão, `prefixoTab`) qual é a tag do clã, e o ClasPlugin pede pra ele refazer a tab (`refrescarTagJogador`) quando alguém entra ou sai de um clã. No chat, as duas tags já conviviam. Sem o ClasPlugin, nada muda.

## Permissões

| Permissão | Padrão | Descrição |
|---|---|---|
| `vip.admin` | op | Gerenciar VIPs de outros jogadores e gerar/revogar códigos |
| `vip.perk.fly` | false | Perk de voo — dar no grupo LuckPerms do tier |
| `vip.perk.heal` | false | Perk de cura instantânea |
| `vip.perk.feed` | false | Perk de restaurar fome |
| `vip.perk.kit` | false | Perk de kit |
| `vip.perk.particulas` | false | Perk de partículas |
| `vip.perk.warp` | false | Perk de warp exclusivo |
| `vip.desconto.bronze` / `.prata` / `.ouro` / `.diamante` | false | Usada pelo EconomyShopPlugin (se instalado) |

`/vip resgatar` **não exige nenhuma permissão** — de propósito, já que é assim que um comprador ativa o que pagou. Não faria sentido gatear atrás de uma permissão que só quem já é VIP teria.

## Requisitos

- Paper 26.2 (Java 25)
- **LuckPerms** — sem ele, o plugin ainda controla prazos e códigos normalmente, mas não consegue aplicar nenhum grupo (fica um aviso no console)
- **Vault + algum plugin de economia** (compatível com o `economy-shop-plugin` deste repo) — só necessário pra `/vip vender`/`/vip resgatar` de código pago. Sem Vault, tudo o resto (staff, perks, códigos grátis) continua funcionando normal — só a revenda entre jogadores fica indisponível, com aviso claro pro jogador.
- EconomyShopPlugin é **opcional** — sem ele, o desconto na loja simplesmente não tem onde ser aplicado

## Como compilar

```bash
cd vip-plugin
mvn clean package
```

O `.jar` sai em `target/VipPlugin.jar`.

## ⚠️ Simplificações técnicas

1. **A tag de chat/tablist é implementada aqui, não pelo LuckPerms** — o LuckPerms guarda `meta setprefix` pra você usar com outros plugins (placeholders, scoreboard, etc.), mas o VipPlugin não lê esse prefixo; ele usa o próprio campo `nome-exibicao` do `config.yml`. Isso evita uma dependência a mais (não precisa de PlaceholderAPI), mas significa que você define a tag em dois lugares. Se preferir unificar lendo o prefixo do LuckPerms direto, é só pedir.
2. **`/vip warp` é um único ponto fixo pra todos os tiers** — não tem um warp por tier.
3. **Cooldowns de heal/feed/kit/kit mensal ficam salvos em `cooldowns.yml`**, então reiniciar o servidor não dá um uso grátis pra ninguém. Registros que já passaram do maior cooldown configurado são descartados ao salvar, pro arquivo não crescer pra sempre.
4. **`/vip kit` e `/vip kitmensal` sempre entregam o kit inteiro do tier atual** — não acumulam nem somam kits de tiers anteriores. O cooldown do kit mensal é por jogador, não por tier: quem resgata no Bronze e sobe pro Diamante no mesmo mês espera o mês acabar.
5. **A validade do código vem desligada** (`codigos.validade-padrao-dias: 0`). Por padrão, um código só some quando é totalmente usado ou revogado. Ligue no `config.yml` se quiser um prazo pra resgatar (veja "Validade do código").
6. **A integração com o site é via RCON, ainda não construída** — quando o site existir de verdade, ele precisa só rodar `vip gerarcodigo <tier> <dias>` via RCON depois de confirmar o pagamento. Nenhuma mudança no plugin é necessária pra isso funcionar; se quiser, mais pra frente, uma API HTTP própria em vez de RCON, é um projeto à parte.
7. **Um jogador só pode ter um código à venda por vez** — checado explicitamente (`buscarCodigoDeVenda`), não uma consequência de outra coisa. Se quiser permitir múltiplas vendas simultâneas, é só tirar essa checagem.
8. **Não tem preço mínimo/máximo pra `/vip vender`** — um jogador pode colocar por 1 coin ou 1 milhão, a critério dele. Se quiser limites (ex: não deixar vender por menos que X% do preço de tabela do tier), dá pra adicionar uma checagem usando `desconto-loja-percentual` ou um novo campo de "preço sugerido" por tier.
