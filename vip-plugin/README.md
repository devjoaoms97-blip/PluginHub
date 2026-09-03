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
| `/vip particula <tipo\|off>` | quem tem VIP | Escolhe o efeito de partícula que te segue |
| `/vip warp` | quem tem VIP | Teleporta pra área exclusiva de VIP |
| `/vip add <jogador> <tier> <dias>` | staff | Concede/renova VIP direto (sem passar por código) |
| `/vip remove <jogador>` | staff | Remove o VIP na hora |
| `/vip check <jogador>` | staff | Vê o VIP de outro jogador |
| `/vip list` | staff | Lista todos os VIPs ativos |
| `/vip gerarcodigo <tier> <dias> [usos]` | staff | Gera um código pra lojinha entregar ao comprador |
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
- Códigos ficam salvos em `codigos.yml` (sobrevivem a reinício do servidor) até serem totalmente usados ou revogados com `/vip revogarcodigo`.

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

/lp creategroup vip_prata
/lp group vip_prata meta setprefix "&f[&bPrata&f] "
/lp group vip_prata permission set vip.perk.heal true
/lp group vip_prata permission set vip.perk.feed true
/lp group vip_prata permission set vip.perk.kit true
/lp group vip_prata permission set vip.perk.particulas true
/lp group vip_prata permission set vip.perk.fly true
/lp group vip_prata permission set vip.desconto.prata true

/lp creategroup vip_ouro
/lp group vip_ouro meta setprefix "&6[Ouro] "
/lp group vip_ouro permission set vip.perk.heal true
/lp group vip_ouro permission set vip.perk.feed true
/lp group vip_ouro permission set vip.perk.kit true
/lp group vip_ouro permission set vip.perk.particulas true
/lp group vip_ouro permission set vip.perk.fly true
/lp group vip_ouro permission set vip.perk.warp true
/lp group vip_ouro permission set vip.desconto.ouro true

/lp creategroup vip_diamante
/lp group vip_diamante meta setprefix "&b[Diamante] "
/lp group vip_diamante permission set vip.perk.heal true
/lp group vip_diamante permission set vip.perk.feed true
/lp group vip_diamante permission set vip.perk.kit true
/lp group vip_diamante permission set vip.perk.particulas true
/lp group vip_diamante permission set vip.perk.fly true
/lp group vip_diamante permission set vip.perk.warp true
/lp group vip_diamante permission set vip.desconto.diamante true
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

## Integração com a loja (EconomyShopPlugin)

Se o `economy-shop-plugin` estiver instalado, `/buy` já aplica automaticamente o desconto do maior tier do jogador — não precisa configurar nada a mais além do passo 2 acima (a permissão `vip.desconto.<tier>` já cobre isso). Essa integração é **desacoplada**: o VipPlugin não sabe que o EconomyShopPlugin existe, e vice-versa — os dois só concordam no nome das permissões `vip.desconto.*`. Ver `economy-shop-plugin/README.md`, seção "Desconto de VIP na compra".

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
3. **Cooldowns de heal/feed/kit ficam em memória**, não persistem se o servidor reiniciar (o jogador ganha um "uso grátis" depois de um restart).
4. **`/vip kit` sempre entrega o kit inteiro do tier atual** — não acumula nem soma kits de tiers anteriores.
5. **Códigos não expiram sozinhos** — só somem quando totalmente usados (`usos-restantes` chega a 0) ou revogados manualmente com `/vip revogarcodigo`. Se quiser um prazo de validade pro código em si (ex: "expira em 7 dias se não for resgatado"), é rápido de adicionar.
6. **A integração com o site é via RCON, ainda não construída** — quando o site existir de verdade, ele precisa só rodar `vip gerarcodigo <tier> <dias>` via RCON depois de confirmar o pagamento. Nenhuma mudança no plugin é necessária pra isso funcionar; se quiser, mais pra frente, uma API HTTP própria em vez de RCON, é um projeto à parte.
7. **Um jogador só pode ter um código à venda por vez** — checado explicitamente (`buscarCodigoDeVenda`), não uma consequência de outra coisa. Se quiser permitir múltiplas vendas simultâneas, é só tirar essa checagem.
8. **Não tem preço mínimo/máximo pra `/vip vender`** — um jogador pode colocar por 1 coin ou 1 milhão, a critério dele. Se quiser limites (ex: não deixar vender por menos que X% do preço de tabela do tier), dá pra adicionar uma checagem usando `desconto-loja-percentual` ou um novo campo de "preço sugerido" por tier.
