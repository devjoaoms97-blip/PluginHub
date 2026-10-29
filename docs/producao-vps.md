# Ida pra VPS (produção)

O que precisa ser feito ou conferido quando o servidor sair do PC (`C:\mc-teste`) e for pra VPS. No servidor de teste, os scripts de `test-server/` já fazem quase tudo. Na VPS, conferir item por item.

## Multiverse

- [ ] **`confirm-mode: disable_console`** no `plugins/Multiverse-Core/config.yml` (ou `mv config confirm-mode disable_console` no console). **Sem isso, nenhum mundo de recursos é resetado:** o RecursosPlugin roda o `mv regen` pelo console, e o Multiverse ficaria esperando um `/mv confirm` que ninguém dá. O console mostra o erro "O regen de ... não aconteceu em 30 s".
- [ ] Os mundos criados como no `test-server/scripts/configurar-mundos.ps1`: `spawn`, `arenas`, `terrenos`, `recursos`, `recursos_nether` (nether), `recursos_end` (`the_end`) e `tesouros` (superplano com as camadas do TesourosPlugin), com as regras de cada um (PvP, dificuldade, mobs).

## Servidor

- [ ] `allow-nether=false` no `server.properties` e `allow-end: false` no `bukkit.yml`: o Nether e o End são só os de recursos (`/nether` e `/end`).
