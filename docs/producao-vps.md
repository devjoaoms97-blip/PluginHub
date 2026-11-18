# Ida pra VPS (produção)

O que precisa ser feito ou conferido quando o servidor sair do PC (`C:\mc-teste`) e for pra VPS. No servidor de teste, os scripts de `test-server/` já fazem quase tudo. Na VPS, conferir item por item.

## Antes da VPS: o servidor dos jogadores no PC (2026-11-06)

Pra jogar com os amigos antes de abrir ao público, o PC roda dois servidores separados ao mesmo tempo:

| | Desenvolvimento (`C:\mc-teste`) | Jogadores (`D:\BrasaMC`) |
|---|---|---|
| Quem entra | os bots de teste | só gente (nunca rodar bot aqui) |
| Porta / RCON | 25566 / 25576, só no PC | 25565 (a do túnel do playit) / 25575 |
| API do DiscordPlugin / pacote | 8156 / 8766 | 8155 / a release `pacote` do GitHub (`url-publica`) |
| Avisos e bot do Discord | Discord de teste (`npm start`) | Discord oficial (`npm run oficial`) |
| Ligar | `tools/bots/servidor.py` | `D:\BrasaMC\iniciar.bat` (console, backup ao desligar) |

O `D:\BrasaMC` é uma cópia do de desenvolvimento com o mapa e os configs, sem nenhum dado de jogador (contas do AuthMe, dinheiro, ranks, skills, clãs, terrenos, VIPs, vínculos...), com o mundo dos terrenos recriado, op só na conta Admin e o mínimo de 2 jogadores nos eventos. No `.env` do bot, `PLUGIN_API_URL_TESTE`/`_OFICIAL` e `PLUGIN_API_TOKEN_OFICIAL` separam os dois. Mudou o pacote de texturas: troque o arquivo da release (o sha1 tem de ser o do log do PacotePlugin).

## Multiverse

- [ ] **`confirm-mode: disable_console`** no `plugins/Multiverse-Core/config.yml` (ou `mv config confirm-mode disable_console` no console). **Sem isso, nenhum mundo de recursos é resetado:** o RecursosPlugin roda o `mv regen` pelo console, e o Multiverse ficaria esperando um `/mv confirm` que ninguém dá. O console mostra o erro "O regen de ... não aconteceu em 30 s".
- [ ] **`enforce-gamemode: false`** no `plugins/Multiverse-Core/config.yml` (ou `mv config enforce-gamemode false`). Ligado, o Multiverse força o modo de jogo do mundo em quem troca de mundo: a staff no criativo volta pro sobrevivência a cada `/warp`. Desligado, o modo de cada um vale em todos os mundos (os plugins que mudam o modo, como o espectador do duelo, cuidam de voltar).
- [ ] Os mundos criados como no `test-server/scripts/configurar-mundos.ps1`: `spawn`, `arenas`, `terrenos`, `recursos`, `recursos_nether` (nether), `recursos_end` (`the_end`) e `tesouros` (superplano com as camadas do TesourosPlugin), com as regras de cada um (PvP, dificuldade, mobs).

## Servidor

- [ ] `allow-nether=false` no `server.properties` e `allow-end: false` no `bukkit.yml`: o Nether e o End são só os de recursos (`/warp nether` e `/warp end`).

## Montaria (MontariaPlugin)

- [ ] A caixa da **warp mobs** (decisão de 2026-11-03: sem montaria da linha vermelha pra dentro) em `proibido:` no `plugins/MontariaPlugin/config.yml`, junto da arena do boss. A lista que já existe no servidor não pega o padrão novo do jar:
  ```yaml
    - nome: "warp mobs"
      mundo: spawn
      min: [-209, 40, 3]
      max: [-93, 90, 83]
  ```
  É a mesma caixa do PvP da warp mobs no AreasPlugin (`pvp-so-nas-caixas.spawn`). Se a estrutura "mobs" mudar de lugar, as duas mudam juntas.

## Eventos (EventosPlugin)

- [ ] **`minimo-jogadores` de 2** (decisão de 2026-11-06; antes a ideia era 3 ou 4) em `vietna:`, `spleef:`, `correoucai:`, `sobrevivencia:`, `corrida:` e `labirinto:` no `plugins/EventosPlugin/config.yml`. O padrão do jar é 3; **no servidor de teste está 1** (decisão de 2026-11-02, pra testar sozinho), e não pode ir assim pra produção: com 1, quem entra sozinho joga até o tempo acabar e leva o cheque de R$ 10.000.
- [ ] No `plugins/AreasPlugin/config.yml`, as caixas "arena do vietnã" ([-16, 60, 584] a [16, 81, 616]), "arena do spleef" ([-16, 60, 884] a [16, 93, 916]) e "labirinto do fauno" ([-262, 63, 1738] a [-139, 69, 1861]) em `pvp-so-nas-caixas.arenas` e em `sethome.caixas-pvp`, `carga-de-vento-liberada: ["arena do vietnã"]` e `quebrar-liberado: ["arena do spleef"]` (as listas que já existem no servidor não pegam o padrão novo do jar).
- [ ] `/vietna construir`, `/spleef construir`, `/correoucai construir`, `/sobrevivencia construir`, `/corrida construir`, `/labirinto construir` e `/eventos construir` (o saguão dos eventos) uma vez (ou `node tools/bots/montar-mapa.js construir vietna spleef correoucai sobrevivencia corrida labirinto saguao`, que põe também os hologramas do saguão).
- [ ] O mundo `arenas` numa dificuldade que não seja Pacífico (a Sobrevivência solta mobs hostis lá; o `spawn_mobs false` não atrapalha).
- [ ] As permissões `eventos.ip.ignorar` só nos bots de teste, nunca num grupo.

## Fazendas (FazendasPlugin, 2026-11-17)

Vale também pro `D:\BrasaMC`, que é anterior às fazendas.

- [ ] Os jars novos: `FazendasPlugin`, e os que mudaram junto: `PacotePlugin` (texturas da Escritura, da Ração e do Adubo), `TerrenosPlugin` (o Leiloeiro conta as fazendas), `EconomyShopPlugin` e `EstruturasPlugin` (o balcão do Corretor Rural).
- [ ] **Pacote de texturas (não esquecer!):** junto com o jar do PacotePlugin, troque o arquivo `BrasaMC-texturas.zip` da release `pacote` do GitHub pelo pacote novo, com o sha1 que o log do PacotePlugin do `D:\BrasaMC` mostrar. Sem isso, a Escritura, a Ração e o Adubo aparecem com o xadrez roxo e preto. Não troque antes do jar, porque o sha1 não bateria e os jogadores cairiam, já que o pacote é obrigatório.
- [ ] Depois de trocar a release, no `C:\mc-teste\plugins\PacotePlugin\config.yml`, volte o `url-publica` para `"https://github.com/devjoaoms97-blip/PluginHub/releases/download/pacote/BrasaMC-texturas.zip"`. Em 2026-11-17 ele ficou vazio (`localhost:8766`) para testar as fazendas com o pacote novo.
- [ ] O vendedor `fazendas` (Corretor Rural) em `vendedores:` no `plugins/EconomyShopPlugin/config.yml`, antes do `alquimista` (a lista que já existe no servidor não pega o padrão novo do jar):
  ```yaml
    fazendas:
      comando: "fazendas loja"
      nome: "&a&l🌾 Corretor Rural"
      descricao: "&7Escrituras de fazenda, Ração e Adubo"
      skin: "textura:2d60f9159fb24fbdcc2229bb8ddc01aab31cc8ccb7c8984d0e5e7dccfdbc0a51"
  ```
- [ ] No `plugins/AjudaPlugin/config.yml` (o que já existe no servidor não pega o padrão novo do jar): **`- fazendas` em `comandos-permitidos.lista`** (sem ele, o Corretor Rural não abre pra jogador nenhum, só pra staff), e as páginas `terreno` ("cabe até 10 máquinas, 100 spawners e 8 fazendas") e `producao` (título "Produção", com as fazendas e o `/fazendas loja`) e o texto da etapa do terreno no `tutorial`, como no config do repositório. Depois, `comandos reload`.
- [ ] As duas falas novas do Seu Juca do Terreno: `node tools/bots/montar-mapa.js construir dicas` (refaz os NPCs de dicas do spawn) ou, na mão, `dica fala juca <texto>` com as duas falas das fazendas do `montar-mapa.js`.
- [ ] Reconstruir a Loja Oficial, que cresceu 13 blocos pro fundo (o x 136 virou 149, com a fonte no fundo novo): no console, `execute in minecraft:spawn run forceload add 60 10 152 76`, `estrutura construir loja spawn 0 65 43` (espere o `estrutura status` dizer "Nenhuma") e `execute in minecraft:spawn run forceload remove 60 10 152 76`. Os outros balcões não mudam de lugar.

## Combate sem a segunda mão (CombatePlugin, 2026-11-17)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars novos: `CombatePlugin` (sem o golpe da segunda mão) e `BossPlugin` (só um comentário a menos).
- [ ] No `plugins/CombatePlugin/config.yml`: `ticks-entre-golpes: 5` em `invulnerabilidade:` (ou `/combate intervalo 5`), e apagar o `separar-maos` e o bloco `duas-maos:`, que não valem mais. O config que já existe no servidor não pega o padrão novo do jar.
- [ ] As falas novas do Mestre de Armas Duda: `node tools/bots/montar-mapa.js construir dicas` ou, na mão, `dica fala mestre-armas <texto>` com as duas falas trocadas do `montar-mapa.js`.
- [ ] O tutorial `05-skills-e-combate` do Discord republicado (sem o "Combate com duas mãos").

## Mina PvP e Banco na Loja Oficial (2026-11-17)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars novos: `EstruturasPlugin` (portão do fundo da avenida, `minapvp` e o Banco no fundo da loja), `RankupPlugin` (hora de bônus), `AreasPlugin`, `MontariaPlugin` e `AjudaPlugin` (padrões novos nos configs).
- [ ] Com o servidor **desligado**: `node tools/bots/montar-mapa.js cadastrar --mina-pvp` põe a mina `pvp` no `minas.yml` sem mexer nas outras. (Ele também regrava as arenas do Duelo e do Gladiador e os configs que o `cadastrar` sempre mexe, iguais aos de antes.)
- [ ] No `plugins/AreasPlugin/config.yml`, a caixa `mina pvp` em `pvp-so-nas-caixas.spawn` (min `[-28, 40, -249]`, max `[28, 100, -199]`, `paineis: [[0.5, 66.8, -196.5]]`) e `"mina pvp"` em `sethome.caixas-pvp`. No `plugins/MontariaPlugin/config.yml`, a caixa `mina pvp` em `proibido:` (mundo `spawn`, `[-31, 40, -252]` a `[31, 100, -186]`). Os configs que já existem no servidor não pegam os padrões novos dos jars.
- [ ] No `plugins/AjudaPlugin/config.yml`, as linhas novas da página `rankup` (`/warp minapvp` e `/mina bonus`), o `/warp banco` da página `dinheiro` ("no fundo da Loja Oficial") e o `minapvp` na lista de warps; depois, `comandos reload`.
- [ ] Ligado: `node tools/bots/montar-mapa.js construir minas loja dicas` (a avenida com o portão, as oficinas, a mina PvP, a loja com o Banco e as falas novas do Guarda do Portão e da Banqueira) e o `marcar` (as warps `banco` e `minapvp`, com o bot da staff; **não rode bot no `D:\BrasaMC`**: lá, `/setwarp minapvp` em 0.5 65 -188.5 olhando pro norte e `/setwarp banco` em 150.5 65 43.5 olhando pro leste, na mão). O `construir minas` enche os 12 poços de pedra de novo: depois, `/mina admin resetar` em cada mina.
- [ ] O tutorial `18-ranks-minas-e-novidades` (a Mina PvP) e o `03-dinheiro-e-loja` (o Banco na loja) do Discord, republicados.

## Antihack (GrimAC)

- [ ] O mesmo jar do `test-server/montar.ps1` (`grimac-bukkit-2.3.75-1299a2a.jar`, ou um mais novo que diga 26.2 no Modrinth).
- [ ] **`grim.exempt` só nos bots de teste, nunca num grupo**: quem tem fica fora de todas as checagens.
- [ ] `grim.alerts` e `grim.alerts.enable-on-join` no grupo da staff (sem isso, só os ops veem os alertas).
- [ ] O `punishments.yml` padrão **não expulsa ninguém**: só avisa a staff, grava no banco do Grim e puxa de volta quem se move de um jeito impossível. Proposta (2026-11-05): começar assim e olhar os alertas com jogadores de verdade (o bloqueio com a espada do CombatePlugin, a montaria e o pula-pula da Corrida) antes de colocar `[kick]` ou banimento.
- [ ] Na rede Velocity (ROADMAP), `alerts.proxy.send` e `receive` em `true` pra staff ver os alertas de todos os servidores.
