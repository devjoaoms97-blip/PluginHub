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

## Antihack (GrimAC)

- [ ] O mesmo jar do `test-server/montar.ps1` (`grimac-bukkit-2.3.75-1299a2a.jar`, ou um mais novo que diga 26.2 no Modrinth).
- [ ] **`grim.exempt` só nos bots de teste, nunca num grupo**: quem tem fica fora de todas as checagens.
- [ ] `grim.alerts` e `grim.alerts.enable-on-join` no grupo da staff (sem isso, só os ops veem os alertas).
- [ ] O `punishments.yml` padrão **não expulsa ninguém**: só avisa a staff, grava no banco do Grim e puxa de volta quem se move de um jeito impossível. Proposta (2026-11-05): começar assim e olhar os alertas com jogadores de verdade (o bloqueio com a espada e a segunda mão do CombatePlugin, a montaria e o pula-pula da Corrida) antes de colocar `[kick]` ou banimento.
- [ ] Na rede Velocity (ROADMAP), `alerts.proxy.send` e `receive` em `true` pra staff ver os alertas de todos os servidores.
