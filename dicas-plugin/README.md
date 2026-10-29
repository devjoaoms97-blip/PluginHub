# DicasPlugin

NPCs de dicas pelo spawn. O jogador clica com o botão direito num NPC e ele vira pra quem clicou e fala: um balão aparece em cima da cabeça dele, cresce, escreve a fala letra por letra com um somzinho, fica um tempo (proporcional ao tamanho do texto) e some encolhendo. Cada clique mostra a próxima fala daquele NPC, na ordem.

- Clicar enquanto ele escreve completa a fala na hora; depois de completa, o clique passa pra próxima.
- Um NPC fala uma coisa por vez, e todo mundo em volta vê o balão.
- O NPC (um Mannequin) não toma dano de nada, e se sumir (um `/kill`) volta sozinho. Ele não fica salvo no mundo: nasce quando o chunk carrega.

## Comandos

Todos exigem `dicas.admin` (padrão: op). Nome e falas aceitam cores com `&`.

| Comando | Ação |
|---|---|
| `/dica criar <id> <nome>` | Cria o NPC onde você está, olhando pra onde você olha |
| `/dica criar <id> em <mundo> <x> <y> <z> <yaw> <nome>` | O mesmo, num lugar informado (funciona pelo console) |
| `/dica fala <id> <texto>` | Adiciona uma fala |
| `/dica falas <id>` | Lista as falas, numeradas |
| `/dica tirarfala <id> <número>` | Remove uma fala |
| `/dica nome <id> <nome>` | Troca o nome |
| `/dica skin <id> <nick\|textura:<hash>\|padrao>` | Usa a skin de um jogador, uma textura direto do textures.minecraft.net (ex: do mineskin.org) ou a padrão |
| `/dica mover <id>` | Leva o NPC pra onde você está |
| `/dica remover <id>` | Apaga o NPC |
| `/dica lista` | Todos os NPCs de dicas |
| `/dica reload` | Recarrega o `config.yml` |

## Configuração

```yaml
descricao: "&7Clique pra conversar"   # texto embaixo do nome
balao:
  altura: 2.6                # acima dos pés do NPC
  largura: 180               # largura máxima da linha (px)
  fundo-opacidade: 170       # 0 a 255
  letras-por-tick: 1         # velocidade da escrita (20 ticks = 1 s)
  ticks-por-letra-lida: 1.2  # depois de escrito, fica 2 s + isso por letra (até 12 s)
```

Os NPCs e as falas ficam no `npcs.yml`.

## No mapa do servidor

O `tools/bots/montar-mapa.js construir dicas` cria 13 NPCs pelo spawn, com 4 a 7 falas cada:

| Onde | NPC | Assunto |
|---|---|---|
| Em volta da fonte | Velho Minerador, Dona Cida (a Mercadora), Veterano Bartolomeu, Zé (o Guia) | minas, rank, Fragmento Arcano e Compactador; barracas e Pix; boss, Wither de domingo, boss elite e o Dragão do End às 21h; diária, X1, Gladiador, terreno, `/achados` e `/montaria` |
| Portão das minas | Guarda do Portão, Dona Neusa (a Ranzinza) | `/mina`, a venda automática e o guia de cada mina; `/rankup max`, `/prestigio`, `/missoes` e o Ferreiro |
| Portão do boss | Sentinela do Portão, Caçador de Tesouros | agenda do boss, tempo limite e maldição, campeão, perda de itens e PvP na arena; mapas do tesouro (`/tesouro`), câmara, clã e convidados, e quem morre perde tudo |
| Fim das avenidas leste e oeste | Leiloeiro Matias, Mestre de Armas Duda | leilão das barracas ★ e renovação; combate, Escudo Perfeito, prestígio de skill, XP em dobro e `/bounty` |
| Jardins | Banqueira Rosa, Escriba dos Clãs, Seu Juca do Terreno | `/pix`, `/cheque`, `/shop`, extrato e Mine-sena; `/cla` e `/cc`; `/terreno`, `/recursos`, `/nether`, `/end` (e os dias dos resets) e `/mapa` |

Pra mudar as falas de vez, mude a lista `DICAS` no script e rode a parte de novo (ela recria todos); pra um ajuste rápido, use o `/dica`.

## Requisitos

- Paper 26.2 (Java 25)

## Como compilar

```bash
cd dicas-plugin
mvn clean package
```

O `.jar` sai em `target/DicasPlugin.jar`.
