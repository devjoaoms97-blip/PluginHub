# AjudaPlugin

Ajuda pra quem está começando: o `/tutorial` (etapas com um prêmio único no fim), o `/comandos` (os comandos do servidor por tema) e os botões de aceitar e recusar o `/tpa` com um clique.

## `/tutorial`

Etapas em ordem, cada uma detectada sozinha. Só conta depois de começar (`/tutorial`), e não dá pra pular: a ação de uma etapa futura não conta.

| Etapa | Como conclui |
|---|---|
| 1. Minere na mina | Quebrar 64 blocos dentro de uma mina do RankupPlugin (`/mina`) |
| 2. Conheça a Loja Oficial | `/warp loja` |
| 3. Conheça os ranks | `/ranks` (ou `/rank`, `/rankup`) |
| 4. Ache um terreno | `/terreno auto` |
| 5. Pegue a recompensa diária | `/diaria` |

- **Prêmio:** um Mapa do Tesouro Comum (TesourosPlugin), **uma vez por jogador** (UUID). Sem espaço no inventário, ele fica guardado: o jogador libera um espaço e usa `/tutorial` pra receber. Nada cai no chão.
- **Convite:** quem nunca começou recebe, uns 8 s depois de entrar (tempo do login do AuthMe), a mensagem com o botão **[COMEÇAR]**. Quem está no meio recebe o botão **[VER ETAPA]**.
- **Na tela:** cada etapa mostra o título, a explicação e um botão que roda o comando da dica (ex.: **[▶ /mina]**). Na mineração, a barra mostra o progresso (12/64).
- Os tipos de etapa (`minerar-mina`, `vender-loja` e `comando`), os textos e o prêmio ficam no config. O `vender-loja` conta só a venda do inventário pros vendedores (o EconomyShopPlugin avisa). A venda automática das minas não conta, então ele não serve pra quem acabou de chegar.

## `/comandos`

`/comandos` mostra os temas, clicáveis. `/comandos <tema>` (ou `/comando <tema>`) abre a página: uma explicação curta e os comandos, cada um com o que faz. Clicar num comando já o digita no chat, até o primeiro argumento, pra completar e mandar.

Temas: começo, rankup, dinheiro, terreno, amigo (o `/terreno amigo`), teleporte, combate, clãs, máquinas, mundos e outros. As páginas ficam em `comandos.paginas` no config.

## `/tpa` com clique

Quem recebe um `/tpa` ou `/tpahere` (EssentialsX) ganha, logo abaixo das mensagens do Essentials, os botões **[✔ ACEITAR]** e **[✖ RECUSAR]**, que rodam `/tpaccept <quem pediu>` e `/tpdeny <quem pediu>`. O Essentials continua cuidando de tudo: se o pedido existe, se expirou e quem pode aceitar. O clique só digita o comando. O plugin ouve o `TPARequestEvent` do Essentials por reflexão, sem dependência de compilação. Sem o Essentials, essa parte fica desligada.

O jogador precisa das permissões do Essentials (`essentials.tpa`, `essentials.tpahere`, `essentials.tpaccept`, `essentials.tpdeny`). O grupo `default` do servidor de teste já as dá, junto com `essentials.home`, `essentials.sethome`, `essentials.delhome`, `essentials.msg` e `essentials.r`.

## Comandos permitidos

O jogador vê no Tab e usa só os comandos de `comandos-permitidos.lista` (os do servidor, pelo nome principal). Antes eram 322 no Tab de quem não é staff: cada comando repetido com o prefixo do plugin (`essentials:tpa`), os apelidos do Essentials (`etpa`, `tpyes`, `ehome`...), o WorldEdit, o LuckPerms, o FreeMinecraftModels, os extras do AuthMe e `pl`, `version`, `me`... O que não está na lista responde "Comando desconhecido", com a dica do `/comandos`. A staff (`ajuda.comandos.todos`, op) vê e usa tudo. Os botões clicáveis do chat passam por esse filtro, então o comando deles precisa estar na lista; o que um plugin roda pelo jogador, não. `comandos-permitidos.ativado: false` desliga.

Os bots de teste ganham `ajuda.comandos.todos` (`criarBot(nome, { comandos: true })` deixa o bot só com a lista, como no `teste-ajuda.js`).

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/tutorial` | todos | Começa o tutorial, mostra a etapa atual ou entrega o prêmio guardado |
| `/comandos [tema]` | todos | Índice ou página de um tema (apelidos: `/comando`, `/cmds`) |
| `/comandos reload` | staff | Recarrega o config |
| `/tutorial admin ver <jogador>` | staff | Em que etapa o jogador está |
| `/tutorial admin resetar <jogador>` | staff | Zera o tutorial (pode fazer e ganhar o prêmio de novo) |
| `/tutorial admin etapa <jogador> <n>` | staff | Põe o jogador na etapa n |

## Permissões

| Permissão | Padrão | O que libera |
|---|---|---|
| `ajuda.usar` | todos | `/tutorial` e `/comandos` |
| `ajuda.comandos.todos` | op | Vê e usa todos os comandos, não só os da lista |
| `ajuda.admin` | op | Os comandos de staff |

## Arquivos

`config.yml`: etapas, prêmio, `tpa-clique.ativado` e as páginas do `/comandos`. `progresso.yml`: a etapa, o contador e se já ganhou o prêmio, por jogador.

## Teste

`node tools/bots/teste-ajuda.js`: convite, `/comandos`, o tutorial inteiro (inclusive o prêmio guardado com o inventário cheio, e uma vez só) e o `/tpa` com clique.
