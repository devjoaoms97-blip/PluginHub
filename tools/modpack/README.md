# Modpack (opcional)

A "versão bonita" do servidor: Fabric com desempenho melhor e shader. **É opcional.** Quem entra com o Minecraft normal continua jogando igual, e o antihack (GrimAC) vale para todos, com ou sem o modpack.

A textura do servidor **não** vem no modpack, porque o PacotePlugin manda ela a quem entra. O `servers.dat` do modpack já vem com o servidor na lista e aceitando o pacote de texturas.

## O que vem

| Mod | Para quê |
|---|---|
| [Sodium](https://modrinth.com/mod/sodium) | Desempenho do render (FPS) |
| [Iris](https://modrinth.com/mod/iris) | Shaders |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) | O shader, ligado na primeira vez |
| [Lithium](https://modrinth.com/mod/lithium) | Desempenho do jogo, sem mudar como ele funciona |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | Menos memória |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | Desempenho da interface, do texto e dos mapas |
| [Sodium Extra](https://modrinth.com/mod/sodium-extra) e [Reese's Sodium Options](https://modrinth.com/mod/reeses-sodium-options) | Mais opções de vídeo e a tela de opções do Sodium |
| [Mod Menu](https://modrinth.com/mod/modmenu), com o [Text Placeholder API](https://modrinth.com/mod/placeholder-api) | A lista de mods no menu |
| [Fabric API](https://modrinth.com/mod/fabric-api) | A base que os outros usam |

Minecraft 26.2 com o Fabric Loader 0.20.1. As versões ficam fixas em `MODS`, no `gerar.py`.

Fica de fora o que dá vantagem no PvP ou na mineração (minimapa com radar, x-ray, zoom e afins). O Grim não enxerga mod do cliente; fora do pacote oficial, a regra do servidor é que vale.

## Gerar

```bash
python tools/modpack/gerar.py                                          # endereço de produção
python tools/modpack/gerar.py --ip stammer-fix.tun.ply.gg --saida teste # BrasaMC-1.0.0-teste-*
```

Saem três arquivos, um para cada tipo de jogador:

| Arquivo | Para quem | Como joga |
|---|---|---|
| `BrasaMC-<versão>-jogar.zip` (~20 MB) | Quem tem **conta original** e quer o mais fácil | Extrai e dá dois cliques em **Jogar BrasaMC**. É o Prism Launcher portátil com o atalho: na primeira vez ele importa o modpack e pede o login da Microsoft; depois, abre o jogo já entrando no servidor |
| `BrasaMC-<versão>-instalador.zip` (~7 KB) | **Qualquer launcher**, inclusive os de conta não original (SKLauncher, TLauncher) | Extrai e dá dois cliques em **Instalar BrasaMC** (uma vez). Depois abre o launcher de sempre e escolhe a versão `BrasaMC-fabric-26.2` |
| `BrasaMC-<versão>.mrpack` (~3 KB) | Quem já usa o Modrinth App ou o Prism | Importa no launcher |

**Por que o atalho de dois cliques pede conta original:** um atalho que abrisse o jogo sem conta teria de baixar e rodar o Minecraft sem a licença da Mojang, que é o que um launcher pirata faz. Isso fica de fora. O instalador não baixa o jogo: só põe o Fabric, os mods e o shader na `.minecraft`, como o instalador oficial do Fabric, e o launcher de cada um cuida do resto.

**O instalador** (`instalador/instalar.ps1`), na `%APPDATA%\.minecraft` (ou `-Pasta`):
- grava a versão `BrasaMC-fabric-26.2` em `versions\` (o perfil do Fabric, que herda o 26.2) e o perfil "BrasaMC" no `launcher_profiles.json`, sem mexer nos outros;
- baixa os mods e o shader do CDN do Modrinth, conferindo o SHA-1, e anota o que instalou em `brasamc-instalado.json` (rodar de novo atualiza e tira o que saiu da lista);
- move os mods que já estavam em `mods\` para `mods-antes-do-brasamc\`, porque são de outras versões e derrubariam o jogo;
- liga o shader e põe o servidor na lista só se o jogador ainda não tiver `config\iris.properties` e `servers.dat`.

Foi testado numa `.minecraft` de mentira (instalar duas vezes, com um mod e um perfil que já existiam). **Falta testar abrindo o jogo** pelo launcher oficial, pelo SKLauncher e pelo TLauncher, e a pasta "jogar" (o Prism ainda não rodou aqui).

O `.mrpack` tem só uns 3 KB: a lista dos arquivos com o link do CDN do Modrinth e as hashes, mais o `servers.dat` e o `config/iris.properties`. Nenhum jar vai junto, e o launcher baixa tudo direto do Modrinth. Antes de gravar, o script confere no Modrinth que cada versão é do 26.2 e do Fabric e que as dependências obrigatórias estão na lista. Os arquivos gerados e o `.cache` (o zip do Prism, baixado uma vez e conferido pelo sha256 de `PRISM_SHA256`) não entram no git.

**Atualizar um mod:** troque o id da versão em `MODS` (o id está na página da versão no Modrinth, ou em `https://api.modrinth.com/v2/project/<slug>/version`), suba o `VERSAO` e gere de novo. Numa versão nova do jogo, troque também o `MINECRAFT` e o `FABRIC_LOADER`.

## Instalar (jogador)

- **Pasta "jogar" ou instalador:** veja a tabela acima.
- **Modrinth App:** *Adicionar instância* → *Importar* → escolha o `.mrpack`. Ou, depois de publicado, procure "BrasaMC" na aba de modpacks.
- **Prism Launcher:** *Adicionar instância* → *Importar* → o `.mrpack`.

Na primeira vez, o shader já vem ligado. Em PC fraco, desligue em *Opções → Vídeo → Shaders*.

## Publicar no GitHub e no Discord

Os três arquivos ficam numa release do repositório (o `jogar.zip` passa do limite de anexo do bot no Discord), com a tag `modpack-<versão>`:

```
python tools/modpack/gerar.py --ip <endereço do servidor>
gh release create modpack-1.0.0 tools/modpack/BrasaMC-1.0.0-jogar.zip tools/modpack/BrasaMC-1.0.0-instalador.zip tools/modpack/BrasaMC-1.0.0.mrpack --title "Modpack BrasaMC 1.0.0" --notes "..."
```

O canal `#modpack` do Discord (`discord-bot/conteudo/modpack.md`, publicado e editado pelo bot) tem os links dessa release, e o tutorial `20-modpack` explica a instalação. A cada versão nova: trocar `VERSAO` aqui, gerar, criar a release e trocar a versão nos links do `modpack.md`. O crédito do Complementary vai no fim do `modpack.md`.

## Publicar no Modrinth

O jeito oficial de distribuir é a página do modpack no Modrinth. É também o que a licença do Complementary pede (veja abaixo). A publicação é feita pela conta do servidor no site, subindo o `BrasaMC-<versão>.mrpack`, e não por este script.

Texto para a página (o crédito é obrigatório):

> Modpack oficial e opcional do BrasaMC (Minecraft 26.2, Fabric): mais FPS com o Sodium e shaders com o Iris. Quem joga sem ele continua jogando igual.
>
> **Shader:** [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined), de EminGT / Complementary Development ([complementary.dev](https://www.complementary.dev/)), ligado por padrão.
> Mods: Sodium, Iris, Lithium, FerriteCore, ImmediatelyFast, Sodium Extra, Reese's Sodium Options, Mod Menu e Fabric API, cada um dos seus autores.

## Licenças

- **Complementary Reimagined** ([licença](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/main/License.txt), item 1.2): pode entrar em modpack desde que (1) seja adicionado pelo sistema do Modrinth ou do CurseForge, nunca enviando o zip direto, e (2), por vir ligado por padrão, tenha crédito visível na página do modpack. O `.mrpack` e a pasta "jogar" cumprem o (1), porque o launcher baixa do Modrinth. O instalador também baixa do CDN do Modrinth e não leva o zip, mas não é o "sistema do Modrinth" de um launcher: **vale perguntar à Complementary Development** antes de divulgar o instalador com o shader (ou tirar o shader dele). O (2) é o texto acima, que precisa ir também onde o instalador for divulgado.
- **Prism Launcher** (GPL-3.0): vai inteiro na pasta "jogar", sem modificação; o código fica em [github.com/PrismLauncher/PrismLauncher](https://github.com/PrismLauncher/PrismLauncher).
- Os mods são baixados dos próprios autores pelo Modrinth, nas licenças de cada um: LGPL-3.0 (Iris, Lithium, Sodium Extra, ImmediatelyFast, Text Placeholder API), MIT (FerriteCore, Reese's Sodium Options, Mod Menu), Apache-2.0 (Fabric API) e PolyForm Shield (Sodium, que só proíbe fazer um produto concorrente). Nada é redistribuído daqui.
- **Entity Culling ficou de fora:** a licença dele (tr7zw Protective License) proíbe usar o mod para "vantagem comercial ou compensação monetária", e o servidor vende VIP. Antes de pôr um mod novo, ler a licença.
