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

**Atualizado em 2026-11-18** com tudo de 17 e 18/11 (fazendas, combate, mina PvP, porto da pesca, bosses em 3D, caixas, voto, skills da segunda leva, texturas dos achados e a API do site), com backup antes (`backup-2026-11-18_16-32-01-antes-da-migracao-1118.zip`). As seções abaixo marcadas "vale também pro `D:\BrasaMC`" já foram feitas lá, menos: republicar os tutoriais do Discord (os revisados na varredura de 2026-11-18 e os novos 24 a 27, ao abrir o `bot-oficial.bat`), as falas novas dos NPCs de dicas (`MC_TESTE='D:\BrasaMC' node tools/bots/montar-mapa.js construir dicas`, só RCON), a porta 8192 do Votifier pública e o cadastro nos sites de voto, e o site oficial (sem domínio ainda). O jeito usado: `atualizar-plugins.ps1`, os configs com as mudanças copiados do `C:\mc-teste` (o `campeao-atual-uuid` do Boss mantido), as warps `minapvp`, `pesca` e `banco` direto nos arquivos do Essentials (sem bot), `MC_TESTE='D:\BrasaMC' node tools/bots/montar-mapa.js cadastrar --mina-pvp` desligado e `construir porto minas loja caixas dicas` ligado (só RCON), `mina admin resetar` nas 13 minas e o pacote novo na release (o PacotePlugin remonta sozinho quando o FMM gera os modelos, uns 15 s depois de ligar: espere o segundo sha1 no log).

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
- [ ] No `plugins/CombatePlugin/config.yml`: `ticks-entre-golpes: 2` em `invulnerabilidade:` (ou `/combate intervalo 2`), e apagar o `separar-maos` e o bloco `duas-maos:`, que não valem mais. O config que já existe no servidor não pega o padrão novo do jar.
- [ ] As falas novas do Mestre de Armas Duda: `node tools/bots/montar-mapa.js construir dicas` ou, na mão, `dica fala mestre-armas <texto>` com as duas falas trocadas do `montar-mapa.js`.
- [ ] O tutorial `05-skills-e-combate` do Discord republicado (sem o "Combate com duas mãos").

## Mina PvP e Banco na Loja Oficial (2026-11-17)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars novos: `EstruturasPlugin` (portão do fundo da avenida, `minapvp` e o Banco no fundo da loja), `RankupPlugin` (hora de bônus), `AreasPlugin`, `MontariaPlugin` e `AjudaPlugin` (padrões novos nos configs).
- [ ] Com o servidor **desligado**: `node tools/bots/montar-mapa.js cadastrar --mina-pvp` põe a mina `pvp` no `minas.yml` sem mexer nas outras. (Ele também regrava as arenas do Duelo e do Gladiador e os configs que o `cadastrar` sempre mexe, iguais aos de antes.)
- [ ] No `plugins/AreasPlugin/config.yml`, a caixa `mina pvp` em `pvp-so-nas-caixas.spawn` (min `[-28, 40, -249]`, max `[28, 100, -199]`, `paineis: [[0.5, 70.3, -197.5]]`) e `"mina pvp"` em `sethome.caixas-pvp`. No `plugins/MontariaPlugin/config.yml`, a caixa `mina pvp` em `proibido:` (mundo `spawn`, `[-31, 40, -252]` a `[31, 100, -186]`). Os configs que já existem no servidor não pegam os padrões novos dos jars.
- [ ] No `plugins/AjudaPlugin/config.yml`, as linhas novas da página `rankup` (`/warp minapvp` e `/mina bonus`), o `/warp banco` da página `dinheiro` ("no fundo da Loja Oficial") e o `minapvp` na lista de warps; depois, `comandos reload`.
- [ ] Ligado: `node tools/bots/montar-mapa.js construir minas loja dicas` (a avenida com o portão, as oficinas, a mina PvP, a loja com o Banco e as falas novas do Guarda do Portão e da Banqueira) e o `marcar` (as warps `banco` e `minapvp`, com o bot da staff; **não rode bot no `D:\BrasaMC`**: lá, `/setwarp minapvp` em 0.5 65 -188.5 olhando pro norte e `/setwarp banco` em 150.5 65 43.5 olhando pro leste, na mão). O `construir minas` enche os 12 poços de pedra de novo: depois, `/mina admin resetar` em cada mina.
- [ ] O tutorial `18-ranks-minas-e-novidades` (a Mina PvP) e o `03-dinheiro-e-loja` (o Banco na loja) do Discord, republicados.

## Warp pesca (PescaPlugin, 2026-11-17)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars: `PescaPlugin` (novo), `EstruturasPlugin` (a estrutura `porto`, longe de tudo, e o degrau da `minapvp`), `SkillsPlugin` (sem o desconto da pesca parada na barraquinha) e `AjudaPlugin`.
- [ ] No `plugins/AjudaPlugin/config.yml`: `pesca`, `porto` e `barraquinha` em `comandos-permitidos.lista` (sem eles, o `/pesca` não funciona pra jogador), as duas linhas da pesca na página `producao` e o `pesca` na lista de warps; depois, `comandos reload`.
- [ ] Ligado: `node tools/bots/montar-mapa.js construir porto dicas` (o porto em x 480..560, só pela warp, com as 16 barraquinhas cadastradas, e a fala nova do Caçador de Tesouros). **No `D:\BrasaMC` não rode o `marcar` (é bot)**: `/setwarp pesca` em 550.5 65 10.5, olhando pro norte, na mão.
- [ ] O tutorial `22-skills-de-profissao` do Discord, republicado.

## Bosses em 3D (2026-11-17)

Vale também pro `D:\BrasaMC`.

- [ ] Os 6 `.bbmodel` de `tools/pacote/bosses/` em `plugins/FreeMinecraftModels/models/`.
- [ ] O jar novo do `BossPlugin`.
- [ ] No `plugins/BossPlugin/config.yml`, `fmm-modelo:` em cada boss (`zombie: boss_tita`, `skeleton: boss_carrasco`, `spider: boss_rainha`, `zombified_piglin: boss_acougueiro`, `creeper: boss_estilhaco`, `witch: boss_feiticeira`). O config que já existe no servidor não pega o padrão novo do jar.
- [ ] Reiniciar. O FMM gera o pacote com os modelos, e o PacotePlugin junta: o sha1 do pacote muda, então troque também o arquivo da release `pacote` do GitHub pelo novo (com o sha1 do log do D:\BrasaMC).
- [ ] Conferir no jogo, com `/boss start zombie` (e os outros): o tamanho do modelo no boss, as animações e o nome em cima.

## Caixas (CaixasPlugin, 2026-11-18)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars: `CaixasPlugin` (novo), `EstruturasPlugin` (a Praça das Caixas no spawn, estrutura `spring-caixas`), `BossPlugin` (a chave pro ranking de dano), `TesourosPlugin` (`chave:<caixa>` no loot), `PacotePlugin` (as texturas das chaves) e `AjudaPlugin`.
- [ ] No `plugins/BossPlugin/config.yml`, em `recompensas`: `ranking-chaves: [1, 1, 1]` e `chave-caixa: boss`.
- [ ] No `plugins/TesourosPlugin/loot.yml`, a chave em `raro`, `arcano` e `lendario` (`chave: {item: "chave:tesouro", qtd: [1, 1], peso: 6}`; 10 no arcano; `qtd: [1, 2]` e peso 14 no lendário).
- [ ] No `plugins/AjudaPlugin/config.yml`: `caixas`, `caixa` e `chaves` em `comandos-permitidos.lista` e as duas linhas do `/caixas` na página `outros`; depois, `comandos reload`.
- [ ] Ligado: `node tools/bots/montar-mapa.js construir caixas dicas` (a praça no lugar da cerejeira do nordeste, só aquele pedaço, as 4 caixas cadastradas e a fala nova da Banqueira).
- [ ] O pacote de texturas mudou (as chaves): troque o arquivo da release `pacote` do GitHub pelo novo.

## Voto com recompensa (VotoPlugin, 2026-11-18)

Vale também pro `D:\BrasaMC`. Depois das Caixas (a chave é delas).

- [ ] O `Votifier` (azuvotifier 3.3.6: `nuvotifier-bukkit-3.3.6-dist.jar` do Modrinth, projeto `azuvotifier`, sha1 `daa4a90861ddf9773207d4617193a3ba93275c2a`) e o `VotoPlugin` (novo) em `plugins/`. Reinicie: o Votifier cria a chave dele em `plugins/Votifier/rsa/`.
- [ ] A porta **8192/TCP** liberada no firewall da VPS (é por ela que os sites avisam o voto).
- [ ] No `plugins/AjudaPlugin/config.yml`: `votar`, `vote` e `votos` em `comandos-permitidos.lista` e a linha do `/votar` na página `outros`; depois, `comandos reload`.
- [ ] Cadastrar o servidor em cada site de lista (IP, porta 8192 e a chave pública de `plugins/Votifier/rsa/public.key`) e pôr o link de votar de cada um em `sites:` no `plugins/VotoPlugin/config.yml`; depois, `/votar admin reload`.
- [ ] Testar com `/testvote username=<seu nick> serviceName=Teste` e votar de verdade num site.

## Skills de profissão, segunda leva (2026-11-18)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars: `SkillsPlugin` (Caçador, Escavador, Alquimista, Ferreiro e o `/trofeus`), `OficinasPlugin` (o desconto e o XP do Ferreiro) e `AjudaPlugin`.
- [ ] Nada a copiar no config do SkillsPlugin: as seções novas entram sozinhas ao ligar (confira no log: "Config atualizado com as skills de profissão novas").
- [ ] No `plugins/AjudaPlugin/config.yml`: `trofeus`, `cabecas` e `reliquias` em `comandos-permitidos.lista` e a linha do `/trofeus` na página de `dinheiro` (perto do `/peixes`); depois, `comandos reload`.
- [ ] O tutorial novo `23-skills-de-profissao-2` do Discord.

## Texturas dos achados raros das fazendas (2026-11-18)

Vale também pro `D:\BrasaMC`.

- [ ] Os jars `FazendasPlugin` e `PacotePlugin` (o pacote com as 12 texturas dos achados e as 4 chaves das caixas).
- [ ] No `plugins/FazendasPlugin/config.yml`, em cada fazenda, `raro.modelo: servidor:raro_<id da fazenda>` (horta, galinheiro, pomar, pocilga, trigal, curral, ovelhas, apiario, aboboras, cogumelos, verrugas e chorus). O config que já existe não pega o padrão novo. Achado que já estava com algum jogador fica com o visual antigo.
- [ ] O pacote mudou: troque o arquivo da release `pacote` do GitHub pelo novo.

## Site (2026-11-18, etapa 1)

- [ ] Os jars: `DiscordPlugin` 1.4 (a foto do site, `GET /site` e `/discord site`), `RankupPlugin`, `SkillsPlugin`, `ClasPlugin` e `VotoPlugin` (o `dadosDoSite` de cada um).
- [ ] No `.env` do bot (o site usa o mesmo): `DISCORD_CONVITE=https://discord.gg/qzKCNVa2NN` e, se quiser outra porta, `SITE_PORTA`.
- [ ] Rodar o site junto do bot: `cd site && npm run oficial` (fica em `127.0.0.1:8080`).
- [ ] **Loja do site** (2026-11-18): o jar novo do `DiscordPlugin` (`/loja/codigo` e `/loja/confirmar`) e do `VotoPlugin` (o resumo da sequência pra página de votar). No `.env`: `SITE_URL_OFICIAL=https://<domínio>` (sem ele, o Mercado Pago não volta sozinho pro pedido e a imagem de prévia não aparece) e o `MP_ACCESS_TOKEN_OFICIAL` de produção, o mesmo da loja do Discord. A loja do site abre junto com a do Discord.
- [ ] Gerar a imagem de prévia com o IP de verdade: com o site oficial ligado, `node site/gerar-previa.js` e subir o `previa.png`.
- [ ] Antes de abrir: revisar `NOVIDADES` em `site/src/conteudo.js` (só o que já está no servidor dos jogadores).
- [ ] **Domínio e HTTPS** (etapa 4): registrar o domínio (brasamc.com.br estava livre em 2026-10-25), apontar o registro A pra VPS e pôr o Caddy na frente (`brasamc.com.br { reverse_proxy 127.0.0.1:8080 }`), que tira o certificado sozinho. Liberar as portas 80 e 443.

## Antihack (GrimAC)

- [ ] O mesmo jar do `test-server/montar.ps1` (`grimac-bukkit-2.3.75-1299a2a.jar`, ou um mais novo que diga 26.2 no Modrinth).
- [ ] **`grim.exempt` só nos bots de teste, nunca num grupo**: quem tem fica fora de todas as checagens.
- [ ] `grim.alerts` e `grim.alerts.enable-on-join` no grupo da staff (sem isso, só os ops veem os alertas).
- [ ] O `punishments.yml` padrão **não expulsa ninguém**: só avisa a staff, grava no banco do Grim e puxa de volta quem se move de um jeito impossível. Proposta (2026-11-05): começar assim e olhar os alertas com jogadores de verdade (o bloqueio com a espada do CombatePlugin, a montaria e o pula-pula da Corrida) antes de colocar `[kick]` ou banimento.
- [ ] Na rede Velocity (ROADMAP), `alerts.proxy.send` e `receive` em `true` pra staff ver os alertas de todos os servidores.
