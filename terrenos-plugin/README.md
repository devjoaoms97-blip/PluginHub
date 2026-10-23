# TerrenosPlugin

Terrenos de 32x32 comprados com coins, num mundo próprio em grade. Cada terreno paga um **imposto semanal**. Quem deixa o imposto atrasar demais perde o terreno, que vai a **leilão de imóvel** com tudo o que tem dentro. O dinheiro da compra, do imposto e do lance vencedor vai para o servidor e some da economia.

## Como funciona

- **Grade.** O mundo é gerado pelo plugin: terrenos de 32x32 com chão de grama, separados por ruas de pedra de 5 blocos, com borda de laje em volta de cada terreno. Não tem cavernas, estruturas, árvores nem mobs naturais. Cada terreno é chamado pela posição na grade, como `0;0` ou `-2;5`.
- **Compra.** R$ 50.000, preço fixo. A primeira semana de imposto vem incluída.
- **Limite.** 1 terreno por jogador. VIP Prata e Ouro podem ter 2, e o Diamante, 3. Leilão em que o jogador está ganhando conta no limite.
- **Imposto.** Por semana e por terreno: **3% do preço do rank atual** do dono no RankupPlugin, com mínimo de R$ 1.000. Como a renda cresce com o rank, o imposto pesa parecido em qualquer fase, uns 15 a 20 minutos de jogo por semana. Exemplos: Mercador R$ 1.680, Barão R$ 60 mil, Rei R$ 960 mil.
  - O **pagamento automático** vem ligado: no vencimento, o imposto sai do saldo se houver dinheiro. `/terreno autopagar` desliga.
  - Dá para pagar até 4 semanas adiantadas com `/terreno imposto pagar`.
- **Atraso.** Depois do vencimento, o dono tem **7 dias de tolerância**, com lembrete no login e a cada hora. Passado esse prazo, o terreno é confiscado.
- **Leilão de imóvel.** O terreno confiscado vai a leilão por 48 h, com tudo o que tem dentro (blocos, baús, itens). O anúncio sai no chat para todos.
  - Lance mínimo de R$ 50.000. Cada lance novo precisa ser 5% maior que o anterior. Lance nos últimos 5 minutos estende o leilão em 5 minutos.
  - O lance fica retido. Quem é superado recebe o valor de volta na hora.
  - O **dono antigo** só dá lance depois de pagar a dívida de imposto (`/terreno divida pagar <x;z>`). A dívida é a semana que venceu mais uma por semana inteira de atraso, então normalmente 2 semanas.
  - O lance vencedor some da economia. O dono antigo não recebe nada.
  - **Sem lances**, o terreno é limpo e volta à venda.
- **Amigos.** O dono libera outros jogadores, cada um com o que pode fazer:
  - **construir:** quebrar e colocar blocos, baldes, bater em animais, molduras e suportes de armadura;
  - **baus:** baús, barris, fornalhas, funis e outros recipientes;
  - **usar:** portas, alçapões, portões, botões, alavancas, placas de pressão, camas, e montar ou negociar com mobs.

  `/terreno amigo add <jogador>` dá tudo; `/terreno amigo add <jogador> baus usar`, só essas.
- **Placa na entrada.** Na rua, em cima da borda, no meio do lado norte de cada terreno, de frente para quem chega pelo `/terreno home` ou pelo `/terreno ir`. Mostra "À venda" com o preço, o dono ou o leilão com o lance atual. É reescrita quando o terreno muda de situação e quando o chunk carrega. Clicar nela mostra o `/terreno info`.
- **Abandono.** `/terreno abandonar confirmar` devolve o terreno sem reembolso, e tudo o que tem dentro é apagado.

## Proteção

No mundo de terrenos, só o dono e os amigos mexem num terreno, e cada amigo só no que recebeu (construir, baús ou usar). A rua, os terrenos à venda e os que estão em leilão são só da staff. Quem pode visitar um terreno é qualquer um, com `/terreno ir <x;z>`.

O plugin também barra o que atravessa a divisa: água e lava escorrendo, pistão, árvore crescendo, fogo se espalhando e explosão. Uma explosão só quebra blocos do terreno em que aconteceu.

## Comandos

| Comando | O que faz |
|---|---|
| `/terreno auto` | Leva até o terreno à venda mais perto do centro |
| `/terreno comprar` | Compra o terreno em que você está |
| `/terreno home [n]` | Vai até o seu terreno (o n-ésimo, se tiver mais de um) |
| `/terreno info [x;z]` | Dono, imposto e amigos do terreno (o imposto e os amigos só para o dono) |
| `/terreno lista` | Seus terrenos e até quando o imposto está pago |
| `/terreno imposto [pagar]` | Mostra o imposto, ou paga uma semana de todos os seus terrenos |
| `/terreno autopagar` | Liga ou desliga o pagamento automático |
| `/terreno amigo add <jogador> [construir] [baus] [usar]` | Amigo no terreno em que você está, com essas permissões (sem nenhuma: todas) |
| `/terreno amigo remover <jogador>` | Tira o amigo |
| `/terreno abandonar confirmar` | Devolve o terreno em que você está |
| `/terreno leiloes` | Leilões de imóvel abertos |
| `/terreno lance <x;z> <valor>` | Dá um lance |
| `/terreno divida [pagar <x;z>]` | Dívida de imposto de um terreno seu que foi a leilão |
| `/terreno ir <x;z>` | Visita um terreno |

Staff (`terrenos.admin`), que também constrói em qualquer lugar do mundo de terrenos:

| Comando | O que faz |
|---|---|
| `/terreno admin dono <x;z> <jogador>` | Passa o terreno para o jogador, com uma semana de imposto paga |
| `/terreno admin remover <x;z>` | Libera o terreno sem limpar |
| `/terreno admin limpar <x;z>` | Limpa um terreno sem dono |
| `/terreno admin leiloar <x;z>` | Manda o terreno a leilão na hora |
| `/terreno admin encerrar <x;z>` | Encerra o leilão agora |
| `/terreno admin vencer <x;z>` | Vence o imposto além da tolerância. Sem pagamento, o terreno vai a leilão em até 30 s (para testar) |
| `/terreno admin reload` | Recarrega o config |

Sem `<x;z>`, os comandos de staff usam o terreno em que você está.

## Instalação

1. Coloque o jar em `plugins/`, junto com o Vault e o Multiverse-Core. O RankupPlugin (imposto pelo rank) e o PixPlugin (histórico) são opcionais.
2. Crie o mundo com o gerador do plugin e desligue o PvP e os mobs:
   ```
   mv create terrenos normal --generator TerrenosPlugin --no-structures
   mv modify terrenos set pvp false
   execute in minecraft:terrenos run gamerule spawn_mobs false
   ```
3. Dê as permissões de limite aos grupos de VIP:
   ```
   lp group vip_prata permission set vip.terrenos.prata true
   lp group vip_ouro permission set vip.terrenos.ouro true
   lp group vip_diamante permission set vip.terrenos.diamante true
   ```

As medidas e os blocos da grade ficam em `grade` no `config.yml`. Mudar depois de o mundo existir não altera o que já foi gerado.

## Integrações

- **RankupPlugin:** o imposto é uma % do preço do rank atual (`precoRankAtual`). Sem ele, o imposto é fixo (`imposto.sem-rankup`).
- **PixPlugin:** compra, imposto, dívida e leilão aparecem no `/pix historico` como pagamento à "Loja do Servidor".
- **Painel da economia:** cada fluxo aparece separado em `/economia tipos TerrenosPlugin`.
- **DiscordPlugin:** os leilões de imóvel (abertos, arrematados e sem lances) vão para a categoria `leiloes`.

## Dados

`terrenos.yml` guarda só os terrenos com dono ou em leilão, incluindo o lance retido. É gravado a cada mudança, então um restart no meio do leilão não perde o dinheiro de ninguém.
