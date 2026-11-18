# CaixasPlugin

Caixas com chave (2026-11-18). Ficam na **Praça das Caixas**, no quadrante nordeste do spawn, entre o portão das Minas e o da Loja, no lugar da cerejeira. São 8 pedestais: 4 com caixa e 4 vazios, pras caixas que vierem.

- **Clique esquerdo** na caixa: a vitrine com os prêmios, da raridade mais alta pra mais baixa, com a chance de cada um.
- **Clique direito com a chave na mão:** a roleta. A linha do meio corre e vai freando até parar no prêmio. Fechar antes entrega na hora.
- **Agachado + clique direito:** abre todas as chaves da mão de uma vez, sem roleta, com um resumo.

## As caixas

| Caixa | Como se ganha a chave | Prêmios |
|---|---|---|
| **Voto** | Cada voto em site de lista (VotoPlugin) | Coins, combustível, livros, fragmento, mapas, Baú do Mar e, raro, spawner de zumbi, tear e coins grandes |
| **Mina** | Cai minerando à mão numa mina do RankupPlugin (`mina.chance-por-bloco`, 1 em 12.000: ~1 a cada 3 h) | Coins, livros, fragmentos, mapas e, raro, forja e mapa arcano |
| **Boss** | Os 3 primeiros do ranking de dano (BossPlugin, `recompensas.ranking-chaves`) | Coins, fragmentos, livros de PvP, mapas, Baú do Mar, spawners e lapidadora |
| **Tesouro** | Nos baús das câmaras (TesourosPlugin, `chave:tesouro` no `loot.yml` do raro, arcano e lendário) | Coins, fragmentos, mapas, Baú do Mar, spawner de slime, reator e mapa lendário |

As coins são uma % do preço do **próximo rank** do jogador (como a `/diaria`), com um piso: pesam parecido em qualquer fase. Aparecem no extrato do Bancário. Prêmio lendário é anunciado no chat e no Discord (categoria `caixas`).

## Chaves

Gancho de armadilha com nome, brilho, textura própria (`servidor:chave_<caixa>`, desenhada no `tools/pacote/texturas.py`) e a marca `caixasplugin:chave`. Não se coloca, não entra em receita, e a loja não compra (item com marca).

Quem dá: `/caixas dar` (console também) e a API `darChave(UUID, caixa, qtd, motivo)`. Online, vai pro inventário. O que não couber, ou se o jogador estiver offline, fica **guardado** (`pendentes.yml`): chega sozinho quando ele entra, ou com `/caixas resgatar`.

## Caixa nova

1. Um bloco novo em `caixas:` no config: `nome`, `chave` (`nome`, `modelo`), `como-conseguir` e `premios`.
2. Cada prêmio: `raridade` (comum, raro, epico, lendario), `peso` e um destes:
   - `coins: X` (X% do próximo rank) com `minimo`;
   - `item:` `fragmento`, `combustivel`, `mapa:<raridade>`, `spawner:<tipo>`, `maquina:<tipo>`, `livro:<encantamento>:<nível>`, `chave:<caixa>` ou um MATERIAL, com `qtd: [mín, máx]`;
   - `comando:` do console (`%jogador%`), com `nome` e `icone`.
3. O lugar: `/caixas local <caixa>` olhando pro bloco (ou `mundo x y z`). Os pedestais vazios da praça estão em 22, 66, 15; 26, 66, 17; 28, 66, 21 e 26, 66, 25 (no mundo `spawn`).
4. A textura da chave: uma função nova no `texturas.py` e `python tools/pacote/gerar.py`.

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/caixas` (`/caixa`, `/chaves`) | todos | As caixas e como conseguir cada chave |
| `/caixas resgatar` | todos | Pega as chaves guardadas |
| `/caixas dar <jogador> <caixa> [qtd]` | staff, console | Dá chaves (offline fica guardada) |
| `/caixas local <caixa> [mundo x y z]` | staff | Onde fica a caixa |
| `/caixas simular <caixa> [n]` | staff | Sorteia n prêmios (sem entregar) e conta as raridades |
| `/caixas lista` / `reload` | staff | Lista as caixas e os lugares / recarrega |

## Integrações

Por reflexão, todas opcionais: RankupPlugin (preço do rank, minas, fragmento), TesourosPlugin (mapas), SpawnersPlugin e MaquinasPlugin (itens), SkillsPlugin (o Baú do Mar, pelo `/peixes dar`), PixPlugin (extrato) e DiscordPlugin (anúncio). A praça é a estrutura `spring-caixas` do EstruturasPlugin, que cadastra as caixas (`node tools/bots/montar-mapa.js construir caixas`).

Teste: `node tools/bots/teste-caixas.js`.
