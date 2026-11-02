# Ida pra VPS (produção)

O que precisa ser feito ou conferido quando o servidor sair do PC (`C:\mc-teste`) e for pra VPS. No servidor de teste, os scripts de `test-server/` já fazem quase tudo. Na VPS, conferir item por item.

## Multiverse

- [ ] **`confirm-mode: disable_console`** no `plugins/Multiverse-Core/config.yml` (ou `mv config confirm-mode disable_console` no console). **Sem isso, nenhum mundo de recursos é resetado:** o RecursosPlugin roda o `mv regen` pelo console, e o Multiverse ficaria esperando um `/mv confirm` que ninguém dá. O console mostra o erro "O regen de ... não aconteceu em 30 s".
- [ ] **`enforce-gamemode: false`** no `plugins/Multiverse-Core/config.yml` (ou `mv config enforce-gamemode false`). Ligado, o Multiverse força o modo de jogo do mundo em quem troca de mundo: a staff no criativo volta pro sobrevivência a cada `/warp`. Desligado, o modo de cada um vale em todos os mundos (os plugins que mudam o modo, como o espectador do duelo, cuidam de voltar).
- [ ] Os mundos criados como no `test-server/scripts/configurar-mundos.ps1`: `spawn`, `arenas`, `terrenos`, `recursos`, `recursos_nether` (nether), `recursos_end` (`the_end`) e `tesouros` (superplano com as camadas do TesourosPlugin), com as regras de cada um (PvP, dificuldade, mobs).

## Servidor

- [ ] `allow-nether=false` no `server.properties` e `allow-end: false` no `bukkit.yml`: o Nether e o End são só os de recursos (`/warp nether` e `/warp end`).

## Eventos (EventosPlugin: Vietnã e Spleef)

- [ ] **`minimo-jogadores` de 3 ou 4** em `vietna:`, `spleef:`, `correoucai:` e `sobrevivencia:` no `plugins/EventosPlugin/config.yml`. O padrão do jar é 3; **no servidor de teste está 1** (decisão de 2026-11-02, pra testar sozinho), e não pode ir assim pra produção: com 1, quem entra sozinho joga até o tempo acabar e leva o cheque de R$ 10.000.
- [ ] No `plugins/AreasPlugin/config.yml`, as caixas "arena do vietnã" ([-16, 60, 584] a [16, 81, 616]) e "arena do spleef" ([-16, 60, 884] a [16, 93, 916]) em `pvp-so-nas-caixas.arenas` e em `sethome.caixas-pvp`, `carga-de-vento-liberada: ["arena do vietnã"]` e `quebrar-liberado: ["arena do spleef"]` (as listas que já existem no servidor não pegam o padrão novo do jar).
- [ ] `/vietna construir`, `/spleef construir`, `/correoucai construir` e `/sobrevivencia construir` uma vez (ou `node tools/bots/montar-mapa.js construir vietna spleef correoucai sobrevivencia`, que põe também os hologramas do saguão).
- [ ] O mundo `arenas` numa dificuldade que não seja Pacífico (a Sobrevivência solta mobs hostis lá; o `spawn_mobs false` não atrapalha).
- [ ] As permissões `eventos.ip.ignorar` só nos bots de teste, nunca num grupo.
